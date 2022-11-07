package com.google.gerrit.pgm.wandisco;

import com.github.rholder.retry.Retryer;
import com.google.common.util.concurrent.Runnables;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gerrit.server.replication.AbstractReplicationSetup;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Properties;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.notedb.RepoSequence.SEQUENCE_TUPLE_DELIMITER;
import static java.nio.charset.StandardCharsets.UTF_8;


@RunWith(PowerMockRunner.class)
public class RevertRepoSequenceTest extends AbstractReplicationSetup{

    private static final int INITIAL_SEQUENCE = 345;

    private static final String NODE_ID = "6666";

    // Don't sleep in tests.
    private static final Retryer<Result> RETRYER =
            RepoSequence.retryerBuilder().withBlockStrategy(t -> {}).build();

    public AllProjectsName allProjectsName;

    private InMemoryRepositoryManager repoManager;

    private RevertRepoSequence revertRepoSequence;

    @Before
    public void setUp() throws Exception {
        repoManager = new InMemoryRepositoryManager();
        allProjectsName = new AllProjectsName("All-Projects");
        AllUsersName allUsersName = new AllUsersName("All-Users");
        repoManager.createRepository(allProjectsName);

        revertRepoSequence = new RevertRepoSequence(repoManager, allProjectsName, allUsersName);

        Properties extraProperties = new Properties();
        extraProperties.put("node.id", "TestNodeId");
        AbstractReplicationSetup.setupReplicatedEventsCoordinatorProps(false, extraProperties);
    }

    @Test
    public void sequenceShouldBeFirstChangeId() throws Exception {
      RepoSequence initSequence = newSequence(Sequences.NAME_CHANGES, INITIAL_SEQUENCE, 1);
      assertThat(initSequence.next()).isEqualTo(INITIAL_SEQUENCE);
    }

    @Test
    public void firstSequenceBlobShouldBeTuple() throws Exception {
        newSequence(Sequences.NAME_CHANGES, INITIAL_SEQUENCE, 1).next();
        assertThat(readBlob(Sequences.NAME_CHANGES)).isEqualTo(dummyTestCoordinator.getThisNodeIdentity() + SEQUENCE_TUPLE_DELIMITER + (INITIAL_SEQUENCE+1));
    }

    @Test
    public void shouldRevertTupleToIntAndBeRolledOn() throws Exception {
        RepoSequence sequence = newSequence(Sequences.NAME_CHANGES, INITIAL_SEQUENCE, 1);
        sequence.next();
        assertThat(readBlob(Sequences.NAME_CHANGES)).isEqualTo(dummyTestCoordinator.getThisNodeIdentity() + SEQUENCE_TUPLE_DELIMITER + (INITIAL_SEQUENCE+1));

        revertRepoSequence.updateSequence(allProjectsName, Sequences.NAME_CHANGES);

        // updating the sequence will also roll the sequence number forward
        assertThat(Integer.parseInt(readBlob(Sequences.NAME_CHANGES))).isEqualTo(INITIAL_SEQUENCE + 2);
    }

    /**
     * @see com.google.gerrit.server.notedb.RepoSequenceTest
     */

    private RepoSequence newSequence(String name, int start, int batchSize) {
        return newSequence(name, start, batchSize, Runnables.doNothing(), RETRYER);
    }

    private RepoSequence newSequence(
            String name,
            final int start,
            int batchSize,
            Runnable afterReadRef,
            Retryer<RefUpdate.Result> retryer) {
        return new RepoSequence(
                dummyTestCoordinator.getReplicatedConfiguration(),
                repoManager,
                GitReferenceUpdated.DISABLED,
                allProjectsName,
                name,
                () -> start,
                batchSize,
                afterReadRef,
                retryer);
    }

    private String readBlob(String sequenceName) throws Exception {
        String refName = RefNames.REFS_SEQUENCES + sequenceName;
        try (Repository repo = repoManager.openRepository(allProjectsName);
             RevWalk rw = new RevWalk(repo)) {
            ObjectId id = repo.exactRef(refName).getObjectId();
            return new String(rw.getObjectReader().open(id).getCachedBytes(), UTF_8);
        }
    }
}
