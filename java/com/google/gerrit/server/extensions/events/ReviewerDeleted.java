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
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
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
public class ReviewerDeleted {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<ReviewerDeletedListener> listeners;
  private final EventUtil util;
  private final ReplicatedEventsCoordinator replicatedEventsCoordinator;


  @Inject
  ReviewerDeleted(PluginSetContext<ReviewerDeletedListener> listeners, EventUtil util, ReplicatedEventsCoordinator replicatedEventsCoordinator) {
    this.listeners = listeners;
    this.util = util;
    this.replicatedEventsCoordinator = replicatedEventsCoordinator;
  }

  public void fire(
      Change change,
      PatchSet patchSet,
      AccountState reviewer,
      AccountState remover,
      String message,
      Map<String, Short> newApprovals,
      Map<String, Short> oldApprovals,
      NotifyHandling notify,
      Timestamp when) {
    if (listeners.isEmpty()) {
      return;
    }
    try {
      Event event =
          new Event(
              util.changeInfo(change),
              util.revisionInfo(change.getProject(), patchSet),
              util.accountInfo(reviewer),
              util.accountInfo(remover),
              message,
              util.approvals(reviewer, newApprovals, when),
              util.approvals(reviewer, oldApprovals, when),
              notify,
              when,
              replicatedEventsCoordinator.getThisNodeIdentity());
      listeners.runEach(l -> l.onReviewerDeleted(event));
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
   * @param streamEvent ReviewerDeletedListener.Event
   */
  public void fire(ReviewerDeletedListener.Event streamEvent) {
    if (listeners.isEmpty()) {
      return;
    }
    listeners.runEach(l -> l.onReviewerDeleted(streamEvent));
  }

  @isReplicatedStreamEvent
  private static class Event extends AbstractRevisionEvent
      implements ReviewerDeletedListener.Event {
    private final AccountInfo reviewer;
    private final String comment;
    private final Map<String, ApprovalInfo> newApprovals;
    private final Map<String, ApprovalInfo> oldApprovals;

    Event(
        ChangeInfo change,
        RevisionInfo revision,
        AccountInfo reviewer,
        AccountInfo remover,
        String comment,
        Map<String, ApprovalInfo> newApprovals,
        Map<String, ApprovalInfo> oldApprovals,
        NotifyHandling notify,
        Timestamp when,
        final String nodeIdentity) {
      super(change, revision, remover, when, notify, nodeIdentity);
      this.reviewer = reviewer;
      this.comment = comment;
      this.newApprovals = newApprovals;
      this.oldApprovals = oldApprovals;
    }

    @Override
    public AccountInfo getReviewer() {
      return reviewer;
    }

    @Override
    public String getComment() {
      return comment;
    }

    @Override
    public Map<String, ApprovalInfo> getNewApprovals() {
      return newApprovals;
    }

    @Override
    public Map<String, ApprovalInfo> getOldApprovals() {
      return oldApprovals;
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
              .add("comment='" + comment + "'")
              .add("newApprovals=" + newApprovals)
              .add("oldApprovals=" + oldApprovals)
              .add("hasBeenReplicated=" + super.hasBeenReplicated)
              .add("eventTimestamp=" + getEventTimestamp())
              .add("eventNanoTime=" + getEventNanoTime())
              .add("nodeIdentity='" + super.getNodeIdentity() + "'")
              .toString();
    }
  }
}
