package com.google.gerrit.server.replication;

import java.util.Optional;
import java.util.Properties;

import static com.google.gerrit.server.replication.configuration.ReplicationConstants.GERRIT_REPLICATED_EVENT_WORKER_POOL_SIZE;
import static com.google.gerrit.server.replication.configuration.ReplicationConstants.REPLICATION_DISABLED;

public abstract class AbstractReplicationSetup {

  protected static TestingReplicatedEventsCoordinator dummyTestCoordinator;

  /**
   * The properties set in this method are the bare minimum required for testing
   * There is the option off passing in extra properties which will be added to
   * the set of overall properties added passed to the TestingReplicatedEventsCoordinator
   * constructor. If no extra properties are required for the test pass null.
   * @param extraProperties
   * @throws Exception
   */
  public static void setupReplicatedEventsCoordinatorProps(boolean replicationDisabled, Properties extraProperties) throws Exception {
    // make sure to clear - really we want to call disable in before class and only enable for one test.
    SingletonEnforcement.clearAll();
    SingletonEnforcement.setDisableEnforcement(true);

    Properties testingProperties = new Properties();

    // SET our pool to 2 items, plus the 2 core projects.
    testingProperties.put(GERRIT_REPLICATED_EVENT_WORKER_POOL_SIZE, "2");
    testingProperties.put(REPLICATION_DISABLED, true);

    Optional<Properties> extra = Optional.ofNullable(extraProperties);
    extra.ifPresent(testingProperties::putAll);

    dummyTestCoordinator = new TestingReplicatedEventsCoordinator(testingProperties);

    // Some tests may require that replication is enabled. This will not be real replication but will set up the required
    // testing properties in the ReplicationConfiguration class.
    dummyTestCoordinator.getReplicatedConfiguration()
            .getConfigureReplication().setReplicationDisabledServerConfig(replicationDisabled);

    GerritEventFactory.setupEventWrapper();
  }
}
