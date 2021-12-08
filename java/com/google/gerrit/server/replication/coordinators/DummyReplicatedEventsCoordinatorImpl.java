package com.google.gerrit.server.replication.coordinators;

import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.replication.configuration.ReplicatedConfiguration;
import com.google.gerrit.server.replication.ReplicatedScheduling;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingAccountBaseIndexEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingCacheEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingIndexEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingProjectEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingProjectIndexEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingServerEventsFeed;
import com.google.gerrit.server.replication.processors.ReplicatedEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingAccountGroupIndexEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingAccountUserIndexEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingCacheEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingIndexEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingProjectEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingProjectIndexEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingServerEventProcessor;
import com.google.gerrit.server.replication.workers.ReplicatedIncomingEventWorker;
import com.google.gerrit.server.replication.workers.ReplicatedOutgoingEventWorker;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gson.Gson;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.wandisco.gerrit.gitms.shared.events.EventWrapper;

import java.util.Map;
import java.util.Set;

@Singleton
public class DummyReplicatedEventsCoordinatorImpl implements ReplicatedEventsCoordinator {

  private final ReplicatedConfiguration replicatedConfiguration;

  @Inject
  DummyReplicatedEventsCoordinatorImpl(ReplicatedConfiguration replicatedConfiguration) {
    this.replicatedConfiguration = replicatedConfiguration;
  }

  @Override
  public Injector getSysInjector() {
    return null;
  }

  @Override
  public void setSysInjector(Injector sysInjector) { }

  @Override
  public void start() {

  }

  @Override
  public void stop() {

  }

  @Override
  public boolean isReplicationEnabled() {
    return false;
  }

  @Override
  public ChangeIndexer getChangeIndexer() {
    throw new UnsupportedOperationException("getChangeIndexer: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public AccountIndexer getAccountIndexer() {
    throw new UnsupportedOperationException("getAccountIndexer: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public GroupIndexer getGroupIndexer() {
    throw new UnsupportedOperationException("getGroupIndexer: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ProjectIndexer getProjectIndexer() {
    throw new UnsupportedOperationException("getProjectIndexer: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedConfiguration getReplicatedConfiguration() {
    return replicatedConfiguration;
  }

  @Override
  public Gson getGson() {
    throw new UnsupportedOperationException("getGson: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public GitRepositoryManager getGitRepositoryManager() {
    throw new UnsupportedOperationException("getGitRepositoryManager: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public Map<EventWrapper.Originator, ReplicatedEventProcessor> getReplicatedProcessors() {
    throw new UnsupportedOperationException("getReplicatedProcessors: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public SchemaFactory<ReviewDb> getSchemaFactory() {
    throw new UnsupportedOperationException("getSchemaFactory: Unable to get access to replicated objects when not using replicated entry points.");

  }

  @Override
  public ChangeNotes.Factory getChangeNotesFactory() {
    throw new UnsupportedOperationException("getChangeNotesFactory: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public void subscribeEvent(EventWrapper.Originator eventType, ReplicatedEventProcessor toCall) {
    throw new UnsupportedOperationException("subscribeEvent: Unable to get access to replicated objects when not using replicated entry points.");

  }

  @Override
  public void unsubscribeEvent(EventWrapper.Originator eventType, ReplicatedEventProcessor toCall) {
    throw new UnsupportedOperationException("unsubscribeEvent: Unable to get access to replicated objects when not using replicated entry points.");

  }

  @Override
  public boolean isCacheToBeEvicted(String cacheName) {
    return false;
  }

  @Override
  public ReplicatedIncomingIndexEventProcessor getReplicatedIncomingIndexEventProcessor() {
    throw new UnsupportedOperationException("getReplicatedIncomingIndexEventProcessor: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedIncomingAccountUserIndexEventProcessor getReplicatedIncomingAccountUserIndexEventProcessor() {
    throw new UnsupportedOperationException("getReplicatedIncomingAccountUserIndexEventProcessor: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedIncomingAccountGroupIndexEventProcessor getReplicatedIncomingAccountGroupIndexEventProcessor() {
    throw new UnsupportedOperationException("getReplicatedIncomingAccountGroupIndexEventProcessor: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedIncomingServerEventProcessor getReplicatedIncomingServerEventProcessor() {
    throw new UnsupportedOperationException("getReplicatedIncomingServerEventProcessor: Unable to get access to replicated objects when not using replicated entry points.");

  }

  @Override
  public ReplicatedIncomingCacheEventProcessor getReplicatedIncomingCacheEventProcessor() {
    throw new UnsupportedOperationException("getReplicatedIncomingCacheEventProcessor: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedIncomingProjectEventProcessor getReplicatedIncomingProjectEventProcessor() {
    throw new UnsupportedOperationException("getReplicatedIncomingProjectEventProcessor: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedIncomingProjectIndexEventProcessor getReplicatedIncomingProjectIndexEventProcessor() {
    throw new UnsupportedOperationException("getReplicatedIncomingProjectIndexEventProcessor: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedOutgoingIndexEventsFeed getReplicatedOutgoingIndexEventsFeed() {
    throw new UnsupportedOperationException("getReplicatedOutgoingIndexEventsFeed: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedOutgoingCacheEventsFeed getReplicatedOutgoingCacheEventsFeed() {
    throw new UnsupportedOperationException("getReplicatedOutgoingCacheEventsFeed: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedOutgoingProjectEventsFeed getReplicatedOutgoingProjectEventsFeed() {
    throw new UnsupportedOperationException("getReplicatedOutgoingProjectEventsFeed: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedOutgoingAccountBaseIndexEventsFeed getReplicatedOutgoingAccountBaseIndexEventsFeed() {
    throw new UnsupportedOperationException("getReplicatedOutgoingAccountBaseIndexEventsFeed: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedOutgoingProjectIndexEventsFeed getReplicatedOutgoingProjectIndexEventsFeed() {
    throw new UnsupportedOperationException("getReplicatedOutgoingProjectIndexEventsFeed: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedOutgoingServerEventsFeed getReplicatedOutgoingServerEventsFeed() {
    throw new UnsupportedOperationException("getReplicatedOutgoingServerEventsFeed: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedIncomingEventWorker getReplicatedIncomingEventWorker() {
    throw new UnsupportedOperationException("getReplicatedIncomingEventWorker: Unable to get access to replicated objects when not using replicated entry points.");
  }


  @Override
  public ReplicatedOutgoingEventWorker getReplicatedOutgoingEventWorker() {
    throw new UnsupportedOperationException("getReplicatedOutgoingEventWorker: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public ReplicatedScheduling getReplicatedScheduling() {
    throw new UnsupportedOperationException("getReplicatedScheduling: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public void queueEventForReplication(EventWrapper event) {
    throw new UnsupportedOperationException("queueEventForReplication: Unable to get access to replicated objects when not using replicated entry points.");
  }

  @Override
  public String getThisNodeIdentity() {
    return getReplicatedConfiguration().getThisNodeIdentity();
  }

}
