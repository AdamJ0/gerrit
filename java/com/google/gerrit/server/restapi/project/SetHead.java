// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.projects.HeadInput;
import com.google.gerrit.extensions.events.HeadUpdatedListener;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.extensions.events.AbstractNoNotifyEvent;
import com.google.gerrit.server.extensions.events.isReplicatedStreamEvent;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.replication.coordinators.ReplicatedEventsCoordinator;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.StringJoiner;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

@Singleton
public class SetHead implements RestModifyView<ProjectResource, HeadInput> {
  private final GitRepositoryManager repoManager;
  private final Provider<IdentifiedUser> identifiedUser;
  private final PluginSetContext<HeadUpdatedListener> headUpdatedListeners;
  private final PermissionBackend permissionBackend;
  private final Provider<ReplicatedEventsCoordinator> replicatedEventsCoordinator;


  @Inject
  SetHead(
      GitRepositoryManager repoManager,
      Provider<IdentifiedUser> identifiedUser,
      PluginSetContext<HeadUpdatedListener> headUpdatedListeners,
      PermissionBackend permissionBackend,
      Provider<ReplicatedEventsCoordinator> replicatedEventsCoordinator) {
    this.repoManager = repoManager;
    this.identifiedUser = identifiedUser;
    this.headUpdatedListeners = headUpdatedListeners;
    this.permissionBackend = permissionBackend;
    this.replicatedEventsCoordinator = replicatedEventsCoordinator;
  }

  @Override
  public String apply(ProjectResource rsrc, HeadInput input)
      throws AuthException, ResourceNotFoundException, BadRequestException,
          UnprocessableEntityException, IOException, PermissionBackendException {
    if (input == null || Strings.isNullOrEmpty(input.ref)) {
      throw new BadRequestException("ref required");
    }
    String ref = RefNames.fullName(input.ref);

    permissionBackend
        .user(rsrc.getUser())
        .project(rsrc.getNameKey())
        .ref(ref)
        .check(RefPermission.SET_HEAD);

    try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
      Map<String, Ref> cur = repo.getRefDatabase().exactRef(Constants.HEAD, ref);
      if (!cur.containsKey(ref)) {
        throw new UnprocessableEntityException(String.format("Ref Not Found: %s", ref));
      }

      final String oldHead = cur.get(Constants.HEAD).getTarget().getName();
      final String newHead = ref;
      if (!oldHead.equals(newHead)) {
        final RefUpdate u = repo.updateRef(Constants.HEAD, true);
        u.setRefLogIdent(identifiedUser.get().newRefLogIdent());
        RefUpdate.Result res = u.link(newHead);
        switch (res) {
          case NO_CHANGE:
          case RENAMED:
          case FORCED:
          case NEW:
            break;
          case FAST_FORWARD:
          case IO_FAILURE:
          case LOCK_FAILURE:
          case NOT_ATTEMPTED:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case REJECTED_MISSING_OBJECT:
          case REJECTED_OTHER_REASON:
          default:
            throw new IOException("Setting HEAD failed with " + res);
        }

        fire(rsrc.getNameKey(), oldHead, newHead);
      }
      return ref;
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(rsrc.getName(), e);
    }
  }

  private void fire(Project.NameKey nameKey, String oldHead, String newHead) {
    if (headUpdatedListeners.isEmpty()) {
      return;
    }

    Event event = new Event(nameKey, oldHead, newHead, replicatedEventsCoordinator.get().getThisNodeIdentity());
    headUpdatedListeners.runEach(l -> l.onHeadUpdated(event));
  }

  /**
   * fire stream event off for its respective listeners to pick up.
   * @param streamEvent HeadUpdatedListener.Event
   */
  public void fire(HeadUpdatedListener.Event streamEvent) {
    if (headUpdatedListeners.isEmpty()) {
      return;
    }
    headUpdatedListeners.runEach(l -> l.onHeadUpdated(streamEvent));
  }

  @isReplicatedStreamEvent
  private static class Event extends AbstractNoNotifyEvent implements HeadUpdatedListener.Event {

    private final Project.NameKey nameKey;
    private final String oldHead;
    private final String newHead;

    Event(Project.NameKey nameKey, String oldHead, String newHead, final String nodeIdentity) {
      super(nodeIdentity);
      this.nameKey = nameKey;
      this.oldHead = oldHead;
      this.newHead = newHead;
    }

    @Override
    public String getProjectName() {
      return nameKey.get();
    }

    @Override
    public String getOldHeadName() {
      return oldHead;
    }

    @Override
    public String getNewHeadName() {
      return newHead;
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
      return getProjectName();
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
              .add("nameKey=" + nameKey)
              .add("oldHead='" + oldHead + "'")
              .add("newHead='" + newHead + "'")
              .add("hasBeenReplicated=" + super.hasBeenReplicated)
              .add("eventTimestamp=" + getEventTimestamp())
              .add("eventNanoTime=" + getEventNanoTime())
              .add("nodeIdentity='" + super.getNodeIdentity() + "'")
              .toString();
    }
  }
}
