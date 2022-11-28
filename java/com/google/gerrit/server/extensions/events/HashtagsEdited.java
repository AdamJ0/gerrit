// Copyright (C) 2015 The Android Open Source Project
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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.events.HashtagsEditedListener;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Set;
import java.util.StringJoiner;

@Singleton
public class HashtagsEdited {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<HashtagsEditedListener> listeners;
  private final EventUtil util;
  private final ReplicatedEventsCoordinator replicatedEventsCoordinator;

  @Inject
  public HashtagsEdited(PluginSetContext<HashtagsEditedListener> listeners, EventUtil util, ReplicatedEventsCoordinator replicatedEventsCoordinator) {
    this.listeners = listeners;
    this.util = util;
    this.replicatedEventsCoordinator = replicatedEventsCoordinator;
  }

  public void fire(
      Change change,
      AccountState editor,
      ImmutableSortedSet<String> hashtags,
      Set<String> added,
      Set<String> removed,
      Timestamp when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change), util.accountInfo(editor), hashtags, added, removed, when,
                  replicatedEventsCoordinator.getThisNodeIdentity());
      listeners.runEach(l -> l.onHashtagsEdited(event));
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log("Couldn't fire event");
    }
  }

  /**
   * fire stream event off for its respective listeners to pick up.
   * @param streamEvent HashtagsEditedListener.Event
   */
  public void fire(HashtagsEditedListener.Event streamEvent) {
    if (listeners.isEmpty()) {
      return;
    }
    listeners.runEach(l -> l.onHashtagsEdited(streamEvent));
  }


  @isReplicatedStreamEvent
  private static class Event extends AbstractChangeEvent implements HashtagsEditedListener.Event {
    private Collection<String> updatedHashtags;
    private Collection<String> addedHashtags;
    private Collection<String> removedHashtags;

    Event(
        ChangeInfo change,
        AccountInfo editor,
        Collection<String> updated,
        Collection<String> added,
        Collection<String> removed,
        Timestamp when,
        final String nodeIdentity) {
      super(change, editor, when, NotifyHandling.ALL, nodeIdentity);
      this.updatedHashtags = updated;
      this.addedHashtags = added;
      this.removedHashtags = removed;
    }

    @Override
    public Collection<String> getHashtags() {
      return updatedHashtags;
    }

    @Override
    public Collection<String> getAddedHashtags() {
      return addedHashtags;
    }

    @Override
    public Collection<String> getRemovedHashtags() {
      return removedHashtags;
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
              .add("updatedHashtags=" + getHashtags())
              .add("addedHashtags=" + getAddedHashtags())
              .add("removedHashtags=" + getRemovedHashtags())
              .add("hasBeenReplicated=" + super.hasBeenReplicated)
              .add("eventTimestamp=" + getEventTimestamp())
              .add("eventNanoTime=" + getEventNanoTime())
              .add("nodeIdentity='" + super.getNodeIdentity() + "'")
              .toString();
    }
  }
}
