// Copyright (C) 2016 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.extensions.events;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.AssigneeChangedListener;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.StringJoiner;

@Singleton
public class AssigneeChanged {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<AssigneeChangedListener> listeners;
  private final EventUtil util;
  private final ReplicatedEventsCoordinator replicatedEventsCoordinator;


  @Inject
  AssigneeChanged(PluginSetContext<AssigneeChangedListener> listeners, EventUtil util, ReplicatedEventsCoordinator replicatedEventsCoordinator) {
    this.listeners = listeners;
    this.util = util;
    this.replicatedEventsCoordinator = replicatedEventsCoordinator;
  }

  public void fire(
      Change change, AccountState accountState, AccountState oldAssignee, Timestamp when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change),
              util.accountInfo(accountState),
              util.accountInfo(oldAssignee),
              when,
              replicatedEventsCoordinator.getThisNodeIdentity());
      listeners.runEach(l -> l.onAssigneeChanged(event));
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
    }
  }

  /**
   * fire stream event off for its respective listeners to pick up.
   * @param streamEvent AssigneeChangedListener.Event
   */
  public void fire(AssigneeChangedListener.Event streamEvent) {
    if (listeners.isEmpty()) {
      return;
    }
    listeners.runEach(l -> l.onAssigneeChanged(streamEvent));
  }

  @isReplicatedStreamEvent
  private static class Event extends AbstractChangeEvent implements AssigneeChangedListener.Event {
    private final AccountInfo oldAssignee;

    Event(ChangeInfo change, AccountInfo editor, AccountInfo oldAssignee, Timestamp when, final String nodeIdentity) {
      super(change, editor, when, NotifyHandling.ALL, nodeIdentity);
      this.oldAssignee = oldAssignee;
    }

    @Override
    public AccountInfo getOldAssignee() {
      return oldAssignee;
    }

    @Override
    public String nodeIdentity() {
      return super.getNodeIdentity();
    }

    @Override
    public String className() {
      return this.getClass().getName();
    }

    @Override
    public String projectName() {
      return getChange().project;
    }

    @Override
    public void setStreamEventReplicated(boolean replicated) {
      hasBeenReplicated = replicated;
    }

    @Override
    public boolean replicationSuccessful() {
      return hasBeenReplicated;
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", Event.class.getSimpleName() + "[", "]")
              .add("oldAssignee=" + oldAssignee)
              .add("hasBeenReplicated=" + super.hasBeenReplicated)
              .add("eventTimestamp=" + getEventTimestamp())
              .add("eventNanoTime=" + getEventNanoTime())
              .add("nodeIdentity='" + super.getNodeIdentity() + "'")
              .toString();
    }
  }
}
