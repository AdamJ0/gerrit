package com.google.gerrit.server.replication.coordinators;

import com.google.gerrit.index.project.ProjectIndexer;
import com.google.gerrit.server.events.EventBroker;
import com.google.gerrit.server.replication.streamlistener.ReplicatedStreamEventsApiListener;
import com.google.gerrit.server.index.group.GroupIndexer;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.replication.ReplicatedEventRequestScope;
import com.google.gerrit.server.replication.configuration.ReplicatedConfiguration;
import com.google.gerrit.server.replication.ReplicatedScheduling;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingAccountBaseIndexEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingCacheEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingIndexEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingProjectEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingProjectIndexEventsFeed;
import com.google.gerrit.server.replication.feeds.ReplicatedOutgoingStreamEventsFeed;
import com.google.gerrit.server.replication.processors.ReplicatedEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingAccountGroupIndexEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingAccountUserIndexEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingCacheEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingIndexEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingProjectEventProcessor;
import com.google.gerrit.server.replication.processors.ReplicatedIncomingProjectIndexEventProcessor;
import com.google.gerrit.server.replication.workers.ReplicatedIncomingEventWorker;
import com.google.gerrit.server.replication.workers.ReplicatedOutgoingEventWorker;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gson.Gson;
import com.google.gwtorm.server.SchemaFactory;
import com.wandisco.gerrit.gitms.shared.events.EventWrapper;

import java.util.Map;

public interface ReplicatedEventsCoordinator extends SysInjectable, LifecycleListener {

  boolean isReplicationEnabled();

  ChangeIndexer getChangeIndexer();

  AccountIndexer getAccountIndexer();

  GroupIndexer getGroupIndexer();

  ProjectIndexer getProjectIndexer();

  String getThisNodeIdentity();

  ReplicatedConfiguration getReplicatedConfiguration();

  Gson getGson();

  EventBroker getEventBroker();

  GitRepositoryManager getGitRepositoryManager();

  Map<EventWrapper.Originator, ReplicatedEventProcessor> getReplicatedProcessors();

  SchemaFactory<ReviewDb> getSchemaFactory();

  ChangeNotes.Factory getChangeNotesFactory();

  void subscribeEvent(EventWrapper.Originator eventType,
                      ReplicatedEventProcessor toCall);

  void unsubscribeEvent(EventWrapper.Originator eventType,
                        ReplicatedEventProcessor toCall);

  boolean isCacheToBeEvicted(String cacheName);

  void queueEventForReplication(EventWrapper event);

  ReplicatedEventRequestScope getReplicatedEventRequestScope();

  ReplicatedStreamEventsApiListener getReplicatedStreamEventsApiListener();

  /* Processors */
  ReplicatedIncomingIndexEventProcessor getReplicatedIncomingIndexEventProcessor();

  ReplicatedIncomingAccountUserIndexEventProcessor getReplicatedIncomingAccountUserIndexEventProcessor();

  ReplicatedIncomingAccountGroupIndexEventProcessor getReplicatedIncomingAccountGroupIndexEventProcessor();

  ReplicatedIncomingCacheEventProcessor getReplicatedIncomingCacheEventProcessor();

  ReplicatedIncomingProjectEventProcessor getReplicatedIncomingProjectEventProcessor();

  ReplicatedIncomingProjectIndexEventProcessor getReplicatedIncomingProjectIndexEventProcessor();



  /* Feeds */
  ReplicatedOutgoingIndexEventsFeed getReplicatedOutgoingIndexEventsFeed();

  ReplicatedOutgoingCacheEventsFeed getReplicatedOutgoingCacheEventsFeed();

  ReplicatedOutgoingProjectEventsFeed getReplicatedOutgoingProjectEventsFeed();

  ReplicatedOutgoingAccountBaseIndexEventsFeed getReplicatedOutgoingAccountBaseIndexEventsFeed();

  ReplicatedOutgoingProjectIndexEventsFeed getReplicatedOutgoingProjectIndexEventsFeed();

  ReplicatedOutgoingStreamEventsFeed getReplicatedOutgoingStreamEventsFeed();

  /* Workers */
  ReplicatedIncomingEventWorker getReplicatedIncomingEventWorker();

  ReplicatedOutgoingEventWorker getReplicatedOutgoingEventWorker();

  ReplicatedScheduling getReplicatedScheduling();

}
