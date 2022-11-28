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
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.extensions.events.VoteDeletedListener;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.GpgException;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.PatchListObjectTooLargeException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.StringJoiner;

@Singleton
public class VoteDeleted {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<VoteDeletedListener> listeners;
  private final EventUtil util;
  private final ReplicatedEventsCoordinator replicatedEventsCoordinator;

  @Inject
  VoteDeleted(PluginSetContext<VoteDeletedListener> listeners, EventUtil util, ReplicatedEventsCoordinator replicatedEventsCoordinator) {
    this.listeners = listeners;
    this.util = util;
    this.replicatedEventsCoordinator = replicatedEventsCoordinator;
  }

  public void fire(
      Change change,
      PatchSet ps,
      AccountState reviewer,
      Map<String, Short> approvals,
      Map<String, Short> oldApprovals,
      NotifyHandling notify,
      String message,
      AccountState remover,
      Timestamp when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change),
              util.revisionInfo(change.getProject(), ps),
              util.accountInfo(reviewer),
              util.approvals(remover, approvals, when),
              util.approvals(remover, oldApprovals, when),
              notify,
              message,
              util.accountInfo(remover),
              when,
              replicatedEventsCoordinator.getThisNodeIdentity());
      listeners.runEach(l -> l.onVoteDeleted(event));
    } catch (PatchListObjectTooLargeException e) {
      logger.atWarning().log("Couldn't fire event: %s", e.getMessage());
    } catch (PatchListNotAvailableException
        | GpgException
        | IOException
        | OrmException
        | PermissionBackendException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
    }
  }

  /**
   * fire stream event off for its respective listeners to pick up.
   * @param streamEvent VoteDeletedListener.Event
   */
  public void fire(VoteDeletedListener.Event streamEvent) {
    if (listeners.isEmpty()) {
      return;
    }
    listeners.runEach(l -> l.onVoteDeleted(streamEvent));
  }

  @isReplicatedStreamEvent
  private static class Event extends AbstractRevisionEvent implements VoteDeletedListener.Event {
    private final AccountInfo reviewer;
    private final Map<String, ApprovalInfo> approvals;
    private final Map<String, ApprovalInfo> oldApprovals;
    private final String message;

    Event(
        ChangeInfo change,
        RevisionInfo revision,
        AccountInfo reviewer,
        Map<String, ApprovalInfo> approvals,
        Map<String, ApprovalInfo> oldApprovals,
        NotifyHandling notify,
        String message,
        AccountInfo remover,
        Timestamp when,
        final String nodeIdentity) {
      super(change, revision, remover, when, notify, nodeIdentity);
      this.reviewer = reviewer;
      this.approvals = approvals;
      this.oldApprovals = oldApprovals;
      this.message = message;
    }

    @Override
    public Map<String, ApprovalInfo> getApprovals() {
      return approvals;
    }

    @Override
    public Map<String, ApprovalInfo> getOldApprovals() {
      return oldApprovals;
    }

    @Override
    public String getMessage() {
      return message;
    }

    @Override
    public AccountInfo getReviewer() {
      return reviewer;
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
              .add("reviewer=" + reviewer)
              .add("approvals=" + approvals)
              .add("oldApprovals=" + oldApprovals)
              .add("message='" + message + "'")
              .add("hasBeenReplicated=" + super.hasBeenReplicated)
              .add("eventTimestamp=" + getEventTimestamp())
              .add("eventNanoTime=" + getEventNanoTime())
              .add("nodeIdentity='" + super.getNodeIdentity() + "'")
              .toString();
    }
  }
}
