package com.google.gerrit.server.replication.coordinators;

import com.google.common.base.Verify;
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
import com.google.gerrit.server.replication.processors.ReplicatedIncomingStreamEventProcessor;
import com.google.gerrit.server.replication.workers.ReplicatedIncomingEventWorker;
import com.google.gerrit.server.replication.workers.ReplicatedOutgoingEventWorker;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.account.AccountIndexer;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gson.Gson;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.wandisco.gerrit.gitms.shared.events.EventWrapper;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is the coordinator responsible for the various threads in operation,
 * whether they be single processing threads like outgoing thread processor, or the new thread pool for incoming events processing.
 */
@Singleton
public class ReplicatedEventsCoordinatorImpl implements ReplicatedEventsCoordinator {
  private static final Logger log = LoggerFactory.getLogger(ReplicatedEventsCoordinatorImpl.class);
  private final ReplicatedConfiguration replicatedConfiguration;
  private ReplicatedEventRequestScope replicatedEventRequestScope;

  /*Processors*/
  private final ReplicatedIncomingIndexEventProcessor replicatedIncomingIndexEventProcessor;
  private final ReplicatedIncomingAccountUserIndexEventProcessor replicatedIncomingAccountUserIndexEventProcessor;
  private final ReplicatedIncomingAccountGroupIndexEventProcessor replicatedIncomingAccountGroupIndexEventProcessor;
  private final ReplicatedIncomingCacheEventProcessor replicatedIncomingCacheEventProcessor;
  private final ReplicatedIncomingProjectEventProcessor replicatedIncomingProjectEventProcessor;
  private final ReplicatedIncomingProjectIndexEventProcessor replicatedIncomingProjectIndexEventProcessor;

  private final ReplicatedIncomingStreamEventProcessor replicatedIncomingStreamEventProcessor;

  /*Feeds*/
  private final ReplicatedOutgoingIndexEventsFeed replicatedOutgoingIndexEventsFeed;
  private final ReplicatedOutgoingCacheEventsFeed replicatedOutgoingCacheEventsFeed;
  private final ReplicatedOutgoingProjectEventsFeed replicatedOutgoingProjectEventsFeed;
  private final ReplicatedOutgoingAccountBaseIndexEventsFeed replicatedOutgoingAccountBaseIndexEventsFeed;
  private final ReplicatedOutgoingProjectIndexEventsFeed replicatedOutgoingProjectIndexEventsFeed;
  private ReplicatedOutgoingStreamEventsFeed replicatedOutgoingStreamEventsFeed;

  private final ReplicatedIncomingEventWorker replicatedIncomingEventWorker;
  private final ReplicatedOutgoingEventWorker replicatedOutgoingEventWorker;
  private final ReplicatedScheduling replicatedScheduling;

  private final Provider<ReplicatedStreamEventsApiListener> replicatedStreamEventsApiListener;

  private final Provider<SchemaFactory<ReviewDb>> schemaFactory;
  private final Provider<ChangeNotes.Factory> notesFactory;

  private final Provider<ChangeIndexer> changeIndexer;
  private final Provider<AccountIndexer> accountIndexer;
  private final Provider<GroupIndexer> groupIndexer;
  private final Provider<ProjectIndexer> projectIndexer;

  private final GitRepositoryManager gitRepositoryManager;
  private final Gson gson;

  private EventBroker eventBroker;

  private Injector sysInjector;

  @Override
  public boolean isReplicationEnabled() {
    return replicatedConfiguration.getConfigureReplication().isReplicationEnabled();
  }

  @Inject
  public ReplicatedEventsCoordinatorImpl(@GerritServerConfig Config config,
                                         ReplicatedConfiguration configuration,
                                         ReplicatedEventRequestScope replicatedEventRequestScope,
                                         Provider<ReplicatedStreamEventsApiListener> replicatedStreamEventsApiListener,
                                         Provider<SchemaFactory<ReviewDb>> schemaFactory,
                                         Provider<ChangeNotes.Factory> notesFactory,
                                         Provider<ChangeIndexer> changeIndexer,
                                         Provider<AccountIndexer> accountIndexer,
                                         Provider<GroupIndexer> groupIndexer,
                                         Provider<ProjectIndexer> projectIndexer,
                                         GitRepositoryManager gitRepositoryManager,
                                         @Named("wdGson") Gson gson) throws Exception {
    Verify.verifyNotNull(configuration);
    this.replicatedConfiguration = configuration;
    this.replicatedEventRequestScope = replicatedEventRequestScope;
    this.replicatedStreamEventsApiListener = replicatedStreamEventsApiListener;
    this.gerritVanillaServerConfig = config;
    this.schemaFactory = schemaFactory;
    this.notesFactory = notesFactory;
    this.changeIndexer = changeIndexer;
    this.accountIndexer = accountIndexer;
    this.groupIndexer = groupIndexer;
    this.projectIndexer = projectIndexer;
    this.gitRepositoryManager = gitRepositoryManager;
    this.gson = gson;

    if(!isReplicationEnabled()){
      /* workers */
      replicatedIncomingEventWorker = null;
      replicatedOutgoingEventWorker = null;
      /* processors */
      replicatedIncomingIndexEventProcessor = null;
      replicatedIncomingAccountUserIndexEventProcessor = null;
      replicatedIncomingAccountGroupIndexEventProcessor = null;
      replicatedIncomingStreamEventProcessor = null;
      replicatedIncomingCacheEventProcessor = null;
      replicatedIncomingProjectEventProcessor = null;
      replicatedIncomingProjectIndexEventProcessor = null;
      /* feeds*/
      replicatedOutgoingIndexEventsFeed = null;
      replicatedOutgoingCacheEventsFeed = null;
      replicatedOutgoingProjectEventsFeed = null;
      replicatedOutgoingAccountBaseIndexEventsFeed = null;
      replicatedOutgoingProjectIndexEventsFeed = null;
      replicatedScheduling = null;
      return;
    }

    ensureEventsDirectoriesExist();

    /* Workers */
    replicatedIncomingEventWorker = new ReplicatedIncomingEventWorker(this);
    replicatedOutgoingEventWorker = new ReplicatedOutgoingEventWorker(this);

    /* Processors
    *  Responsible for processing incoming events and actioning them*/
    replicatedIncomingIndexEventProcessor = new ReplicatedIncomingIndexEventProcessor(this);
    replicatedIncomingAccountUserIndexEventProcessor = new ReplicatedIncomingAccountUserIndexEventProcessor(this);
    replicatedIncomingAccountGroupIndexEventProcessor = new ReplicatedIncomingAccountGroupIndexEventProcessor(this);
    replicatedIncomingCacheEventProcessor = new ReplicatedIncomingCacheEventProcessor(this);
    replicatedIncomingProjectEventProcessor = new ReplicatedIncomingProjectEventProcessor(this);
    replicatedIncomingProjectIndexEventProcessor = new ReplicatedIncomingProjectIndexEventProcessor(this);

    /* Stream Event Processor */
    replicatedIncomingStreamEventProcessor = new ReplicatedIncomingStreamEventProcessor(this, replicatedEventRequestScope);

    /*
     * Feeds to the queue.
     * Does not include ReplicatedOutgoingServerEventsFeed. It is a standalone feed
     * that does not require a member variable here as it is not accessed by any other class.
     */
    replicatedOutgoingIndexEventsFeed = new ReplicatedOutgoingIndexEventsFeed(this);
    replicatedOutgoingCacheEventsFeed = new ReplicatedOutgoingCacheEventsFeed(this);
    replicatedOutgoingProjectEventsFeed = new ReplicatedOutgoingProjectEventsFeed(this);
    replicatedOutgoingAccountBaseIndexEventsFeed = new ReplicatedOutgoingAccountBaseIndexEventsFeed(this);
    replicatedOutgoingProjectIndexEventsFeed = new ReplicatedOutgoingProjectIndexEventsFeed(this);


    /** Creation of the new Scheduler which manages the Thread Pool of Incoming Processor,
     * as well as how and when to schedule work items to this pool.
     */
    replicatedScheduling = new ReplicatedScheduling(this);
  }

  @Override
  public Injector getSysInjector() {
    return sysInjector;
  }

  /**
   * Using the sysInjector to get an instance of EventBroker to avoid using the coordinator
   * having to inject an EventBroker dependency in its constructor which would cause a dependency cycle.
   *
   * @return A singleton instance of the EventBroker.
   */
  public EventBroker getEventBroker() {
    if (eventBroker == null) {
      eventBroker = this.getSysInjector().getInstance(EventBroker.class);
    }
    return eventBroker;
  }

  @Override
  public void setSysInjector(Injector sysInjector) {
    this.sysInjector = sysInjector;
  }

  @Override
  public void start() {
    // we can delay start if need be / checking other items.... only if we can't create something early because
    // of cyclic dependencies would I normally use this, or something that needs to wait for other threads to start.
    if(!isReplicationEnabled()){
      return;
    }
    replicatedScheduling.startScheduledWorkers();
  }

  @Override
  public void stop() {
    if(!isReplicationEnabled()){
      return;
    }
    // lets kill our processors.
    replicatedIncomingCacheEventProcessor.stop();
    replicatedIncomingAccountUserIndexEventProcessor.stop();
    replicatedIncomingProjectEventProcessor.stop();
    replicatedIncomingStreamEventProcessor.stop();
    replicatedIncomingIndexEventProcessor.stop();

    // We could maybe start using shutdown / then awaitTermination but for now we don't care, pull the plug and we
    // will reschedule the work load correctly.
    replicatedScheduling.stop();
  }


  @Override
  public void queueEventForReplication(EventWrapper event) {
    getReplicatedOutgoingEventWorker().queueEventWithOutgoingWorker(event); // queue is unbound, no need to check for result
  }

  // Note this is not the same as the replicator configuration - this is a GerritServerConfig from vanilla gerrit.
  private static Config gerritVanillaServerConfig = null;

  // This can be set by our own testing, but mainly it is setup when gerrit has injected and created a ReplicatedIndex
  // or events manager with the GerritServerConfig member. It then updates this member, which is a lazy init.  When we
  // move over to guice binding remove this as its redundant and in fact can cause a race if left!
  static void setGerritVanillaServerConfig(Config config) {
    gerritVanillaServerConfig = config;
  }

  /**
   * If in the Gerrit Configuration file the cache value for memoryLimit is 0 then
   * it means that no cache is configured and we are not going to replicate this kind of events.
   * <p>
   * Example gerrit config file:
   * [cache "accounts"]
   * memorylimit = 0
   * disklimit = 0
   * [cache "accounts_byemail"]
   * memorylimit = 0
   * disklimit = 0
   * <p>
   * There is here a small probability of race condition due to the use of the static and the global
   * gerritConfig variable. But in the worst case, we can miss just one call (because once it's initialized
   * it's stable)
   *
   * @param cacheName
   * @return true if the cache is not disabled, i.e. the name does not show up in the gerrit config file with a value of 0 memoryLimit
   */
  public boolean isCacheToBeEvicted(String cacheName) {
    return !(gerritVanillaServerConfig != null && gerritVanillaServerConfig.getLong("cache", cacheName, "memoryLimit", 4096) == 0);
  }

  // Using a concurrent hash map to allow subscribers to add to the list, and not affect us while we are using that list
  // in other threads.  This is a dirty read YES, but it greatly reduces contention in an area where there should be.
  // The only case were a listener may register late, may be from a plugin or lazy context and there is nothing wrong with
  // us sending to the listeners that we started with and completing that list, and the next event to come in will use the new
  // list.
  private final static Map<EventWrapper.Originator, ReplicatedEventProcessor> replicatedEventProcessors =
      new ConcurrentHashMap<>();

  @Override
  public ChangeIndexer getChangeIndexer() {
    return changeIndexer.get();
  }

  @Override
  public AccountIndexer getAccountIndexer() {
    return accountIndexer.get();
  }

  @Override
  public GroupIndexer getGroupIndexer() {
    return groupIndexer.get();
  }

  @Override
  public ProjectIndexer getProjectIndexer() {
    return projectIndexer.get();
  }

  @Override
  public ReplicatedConfiguration getReplicatedConfiguration() {
    return replicatedConfiguration;
  }

  @Override
  public Gson getGson() {
    return gson;
  }

  @Override
  public Map<EventWrapper.Originator, ReplicatedEventProcessor> getReplicatedProcessors() {
    return replicatedEventProcessors;
  }

  @Override
  public SchemaFactory<ReviewDb> getSchemaFactory() {
    return schemaFactory.get();
  }


  @Override
  public ChangeNotes.Factory getChangeNotesFactory(){
    return notesFactory.get();
  }

  @Override
  public GitRepositoryManager getGitRepositoryManager() {
    return gitRepositoryManager;
  }

  @Override
  public String getThisNodeIdentity() {
    return replicatedConfiguration.getThisNodeIdentity();
  }

  @Override
  public void subscribeEvent(EventWrapper.Originator eventType,
                             ReplicatedEventProcessor processor) {
    synchronized (replicatedEventProcessors) {
      replicatedEventProcessors.putIfAbsent(eventType, processor);
      log.info("Subscriber added to {} and will be processed by {}", eventType, processor);
    }
  }

  @Override
  public void unsubscribeEvent(EventWrapper.Originator eventType,
                               ReplicatedEventProcessor processor) {
    synchronized (replicatedEventProcessors) {
      replicatedEventProcessors.remove(eventType);
      log.info("Subscriber removed of type {}", eventType);
    }
  }

  @Override
  public ReplicatedEventRequestScope getReplicatedEventRequestScope() {
    return replicatedEventRequestScope;
  }

  @Override
  public ReplicatedStreamEventsApiListener getReplicatedStreamEventsApiListener() {
    return replicatedStreamEventsApiListener.get();
  }


  /* Processors */

  @Override
  public ReplicatedIncomingIndexEventProcessor getReplicatedIncomingIndexEventProcessor() {
    return replicatedIncomingIndexEventProcessor;
  }

  @Override
  public ReplicatedIncomingAccountUserIndexEventProcessor getReplicatedIncomingAccountUserIndexEventProcessor() {
    return replicatedIncomingAccountUserIndexEventProcessor;
  }

  @Override
  public ReplicatedIncomingAccountGroupIndexEventProcessor getReplicatedIncomingAccountGroupIndexEventProcessor() {
    return replicatedIncomingAccountGroupIndexEventProcessor;
  }


  public ReplicatedIncomingStreamEventProcessor getReplicatedIncomingStreamEventProcessor() {
    return replicatedIncomingStreamEventProcessor;
  }

  @Override
  public ReplicatedIncomingCacheEventProcessor getReplicatedIncomingCacheEventProcessor() {
    return replicatedIncomingCacheEventProcessor;
  }

  @Override
  public ReplicatedIncomingProjectEventProcessor getReplicatedIncomingProjectEventProcessor() {
    return replicatedIncomingProjectEventProcessor;
  }

  @Override
  public ReplicatedIncomingProjectIndexEventProcessor getReplicatedIncomingProjectIndexEventProcessor() {
    return replicatedIncomingProjectIndexEventProcessor;
  }


  /* Feeds */

  @Override
  public ReplicatedOutgoingIndexEventsFeed getReplicatedOutgoingIndexEventsFeed() {
    return replicatedOutgoingIndexEventsFeed;
  }

  @Override
  public ReplicatedOutgoingCacheEventsFeed getReplicatedOutgoingCacheEventsFeed() {
    return replicatedOutgoingCacheEventsFeed;
  }

  @Override
  public ReplicatedOutgoingAccountBaseIndexEventsFeed getReplicatedOutgoingAccountBaseIndexEventsFeed() {
    return replicatedOutgoingAccountBaseIndexEventsFeed;
  }

  @Override
  public ReplicatedOutgoingProjectIndexEventsFeed getReplicatedOutgoingProjectIndexEventsFeed() {
    return replicatedOutgoingProjectIndexEventsFeed;
  }

  @Override
  public ReplicatedOutgoingStreamEventsFeed getReplicatedOutgoingStreamEventsFeed() {
    if (replicatedOutgoingStreamEventsFeed == null) {
      replicatedOutgoingStreamEventsFeed = getSysInjector().getInstance(ReplicatedOutgoingStreamEventsFeed.class);
    }
    return replicatedOutgoingStreamEventsFeed;
  }


  @Override
  public ReplicatedOutgoingProjectEventsFeed getReplicatedOutgoingProjectEventsFeed() {
    return replicatedOutgoingProjectEventsFeed;
  }


  /* Workers */

  @Override
  public ReplicatedIncomingEventWorker getReplicatedIncomingEventWorker() {
    return replicatedIncomingEventWorker;
  }

  @Override
  public ReplicatedOutgoingEventWorker getReplicatedOutgoingEventWorker() {
    return replicatedOutgoingEventWorker;
  }

  @Override
  public ReplicatedScheduling getReplicatedScheduling() {
    return replicatedScheduling;
  }

  private void ensureEventsDirectoriesExist() throws Exception {
    if (!replicatedConfiguration.getIncomingReplEventsDirectory().exists()) {
      if (!replicatedConfiguration.getIncomingReplEventsDirectory().mkdirs()) {
        String errMsg = String.format("RE %s path cannot be created! Replicated events will not work!",
                replicatedConfiguration.getIncomingReplEventsDirectory().getAbsolutePath());
        log.error(errMsg);
        throw new Exception(errMsg);
      }

      log.info("RE {} incoming events location created.",
          replicatedConfiguration.getIncomingReplEventsDirectory().getAbsolutePath());
    }
    if (!replicatedConfiguration.getOutgoingReplEventsDirectory().exists()) {
      if (!replicatedConfiguration.getOutgoingReplEventsDirectory().mkdirs()) {
        String errMsg = String.format("RE %s path cannot be created! Replicated outgoing events will not work!",
                replicatedConfiguration.getOutgoingReplEventsDirectory().getAbsolutePath());
        log.error(errMsg);
        throw new Exception(errMsg);
      }

      log.info("RE {} outgoing events location created.",
          replicatedConfiguration.getOutgoingReplEventsDirectory().getAbsolutePath());
    }
    if (!replicatedConfiguration.getIncomingFailedReplEventsDirectory().exists()) {
      if (!replicatedConfiguration.getIncomingFailedReplEventsDirectory().mkdirs()) {
        String errMsg = String.format("RE %s path cannot be created! Replicated failed events cannot be saved!",
                replicatedConfiguration.getIncomingFailedReplEventsDirectory().getAbsolutePath());
        log.error(errMsg);
        throw new Exception(errMsg);
      }

      log.info("RE {} incoming failed events location created.",
          replicatedConfiguration.getIncomingFailedReplEventsDirectory().getAbsolutePath());
    }
  }


}
