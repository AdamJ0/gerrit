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

package com.google.gerrit.server.events;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.events.ReplicatedStreamEvent;
import com.google.gerrit.extensions.events.ReplicatedStreamEventListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.plugincontext.PluginSetEntryContext;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.util.Providers;
import org.eclipse.jgit.lib.Config;

/**
 * Distributes Events to listeners if they are allowed to see them
 */
@Singleton
public class EventBroker implements EventDispatcher {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), EventDispatcher.class);
      DynamicItem.bind(binder(), EventDispatcher.class).to(EventBroker.class);
    }
  }

  /**
   * Listeners to receive changes as they happen (limited by visibility of user).
   */
  protected final PluginSetContext<UserScopedEventListener> listeners;

  /**
   * Listeners to receive all changes as they happen.
   */
  protected final PluginSetContext<EventListener> unrestrictedListeners;
  protected final PluginSetContext<ReplicatedStreamEventListener> replicatedStreamEventListeners;

  protected final PluginSetContext<NewProjectCreatedListener> newProjectCreatedListener;


  private final PermissionBackend permissionBackend;
  protected final ProjectCache projectCache;
  protected final ChangeNotes.Factory notesFactory;
  protected final Provider<ReviewDb> dbProvider;

  // Use schemaFactory directly as we can't use Request Scoped Providers for items that can cause writes / migration
  // to happen - or if the change it is passed came from request scope.
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  public EventBroker(
      PluginSetContext<UserScopedEventListener> listeners,
      PluginSetContext<EventListener> unrestrictedListeners,
      PluginSetContext<ReplicatedStreamEventListener> replicatedStreamEventListeners,
      PluginSetContext<NewProjectCreatedListener> newProjectCreatedListener,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory,
      Provider<ReviewDb> dbProvider,
      SchemaFactory<ReviewDb> schemaFactory,
      @GerritServerConfig Config config
  ) {
    this.listeners = listeners;
    this.unrestrictedListeners = unrestrictedListeners;
    this.replicatedStreamEventListeners = replicatedStreamEventListeners;
    this.newProjectCreatedListener = newProjectCreatedListener;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.notesFactory = notesFactory;
    this.dbProvider = dbProvider;
    this.schemaFactory = schemaFactory;
  }

  /**
   * Please note as this returns a Provider of a ReviewDB.  As such the instance of the DB isn't really open until
   * the provider.get() is used.  Allowing tidy try( ReviewDb db = provider.get() ) blocks to be used.
   *
   * @return Provider<ReviewDB> instance
   * @throws OrmException
   */
  public Provider<ReviewDb> getReviewDbProvider() throws OrmException {
    return Providers.of(schemaFactory.open());
  }


  @Override
  public void postEvent(Change change, ChangeEvent event)
      throws OrmException, PermissionBackendException {
    fireEvent(change, event);
  }

  @Override
  public void postEvent(Branch.NameKey branchName, RefEvent event)
      throws PermissionBackendException {
    fireEvent(branchName, event);
  }

  @Override
  public void postEvent(Project.NameKey projectName, ProjectEvent event) {
    fireEvent(projectName, event);
  }

  @Override
  public void postEvent(Event event) throws OrmException, PermissionBackendException {
    fireEvent(event);
  }

  @Override
  public void postEvent(ReplicatedStreamEvent event) {
    fireEventForReplicatedStreamEventListener(event);
  }

  public void postNewProjectEvent(NewProjectCreatedListener.Event event){
    fireEventForNewProjectEventListener(event);
  }

  /**
   * Fires the replicated NewProjectEventListener.Events off to each registered replicated NewProjectCreatedListener listener
   * @param event : NewProjectCreatedListener.Event type
   */
  protected void fireEventForNewProjectEventListener(NewProjectCreatedListener.Event event){
    newProjectCreatedListener.runEach(l -> l.onNewProjectCreated(event));
  }

  /**
   * Fires the replicated stream event off to each registered replicated stream event listener
   * @param streamEvent : ReplicatedStreamEvent event type
   */
  protected void fireEventForReplicatedStreamEventListener(ReplicatedStreamEvent streamEvent){
    replicatedStreamEventListeners.runEach(l -> l.onReplicatedStreamEvent(streamEvent));
  }

  protected void fireEventForUnrestrictedListeners(Event event) {
    unrestrictedListeners.runEach(l -> l.onEvent(event));
  }

  protected void fireEvent(Change change, ChangeEvent event)
      throws OrmException, PermissionBackendException {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(l -> l.getUser());
      if (isVisibleTo(change, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Project.NameKey project, ProjectEvent event) {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(l -> l.getUser());
      if (isVisibleTo(project, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Branch.NameKey branchName, RefEvent event)
      throws PermissionBackendException {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(l -> l.getUser());
      if (isVisibleTo(branchName, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected void fireEvent(Event event) throws OrmException, PermissionBackendException {
    for (PluginSetEntryContext<UserScopedEventListener> c : listeners) {
      CurrentUser user = c.call(l -> l.getUser());
      if (isVisibleTo(event, user)) {
        c.run(l -> l.onEvent(event));
      }
    }
    fireEventForUnrestrictedListeners(event);
  }

  protected boolean isVisibleTo(Project.NameKey project, CurrentUser user) {
    try {
      ProjectState state = projectCache.get(project);
      if (state == null || !state.statePermitsRead()) {
        return false;
      }

      permissionBackend.user(user).project(project).check(ProjectPermission.ACCESS);
      return true;
    } catch (AuthException | PermissionBackendException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Change change, CurrentUser user)
      throws OrmException, PermissionBackendException {
    if (change == null) {
      return false;
    }
    ProjectState pe = projectCache.get(change.getProject());
    if (pe == null || !pe.statePermitsRead()) {
      return false;
    }

    // Use Thread Request scope, as the createChecked below may cause a read/write during migration from
    // review to note DB.
    // N.B. This change should be reverted when we move over to 3.0+ -> see 3.x branch for the
    // new code which should work again
    try ( ReviewDb db = getReviewDbProvider().get() ) {
      permissionBackend
          .user(user)
          .change(notesFactory.createChecked(db, change))
          .database(db)
          .check(ChangePermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Branch.NameKey branchName, CurrentUser user)
      throws PermissionBackendException {
    ProjectState pe = projectCache.get(branchName.getParentKey());
    if (pe == null || !pe.statePermitsRead()) {
      return false;
    }

    try {
      permissionBackend.user(user).ref(branchName).check(RefPermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  protected boolean isVisibleTo(Event event, CurrentUser user)
      throws OrmException, PermissionBackendException {
    if (event instanceof RefEvent) {
      RefEvent refEvent = (RefEvent) event;
      String ref = refEvent.getRefName();
      if (PatchSet.isChangeRef(ref)) {
        Change.Id cid = PatchSet.Id.fromRef(ref).getParentKey();
        try {
          Change change =
              notesFactory
                  .createChecked(dbProvider.get(), refEvent.getProjectNameKey(), cid)
                  .getChange();
          return isVisibleTo(change, user);
        } catch (NoSuchChangeException e) {
          logger.atFine().log(
              "Change %s cannot be found, falling back on ref visibility check", cid.id);
        }
      }
      return isVisibleTo(refEvent.getBranchNameKey(), user);
    } else if (event instanceof ProjectEvent) {
      return isVisibleTo(((ProjectEvent) event).getProjectNameKey(), user);
    }
    return true;
  }


  /**
   * This method updates the unrestricted set of listeners.
   *
   * We use this method, to add listeners without having them really be plugin contexts....
   * TODO: (trevorg) Move ReplicationEventManager listener to be a plugin context and this can disappear.
   * @param name
   * @param unrestrictedListener
   */
  public void registerUnrestrictedEventListener(String name, EventListener unrestrictedListener) {
    this.unrestrictedListeners.registerImplementation(name, unrestrictedListener);
  }


  /**
   * Registers a replicated stream event listener for a given thread or class.
   * @param name : The name of the thread or class registering the listener
   * @param replicationListener : The instance of the replicated stream event listener
   */
  public void registerReplicatedStreamEventListener(String name, ReplicatedStreamEventListener replicationListener) {
    this.replicatedStreamEventListeners.registerImplementation(name, replicationListener);
  }

}
