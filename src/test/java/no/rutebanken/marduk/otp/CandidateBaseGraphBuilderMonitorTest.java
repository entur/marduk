package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.leader.LeaderElection;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateBaseGraphBuilderMonitorTest {

    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();

    /** Stands in for the Kubernetes lookup, which needs a cluster. */
    private static class StubbedMonitor extends CandidateBaseGraphBuilderMonitor {
        String resourceVersion;

        StubbedMonitor(RecordingPubSubPublisher publisher, LeaderElection leaderElection, String resourceVersion) {
            super(publisher, leaderElection, "default", "graph-builder-otp2");
            this.resourceVersion = resourceVersion;
        }

        @Override
        String candidateBuilderResourceVersion() {
            return resourceVersion;
        }
    }

    private StubbedMonitor monitor(boolean leader) {
        return new StubbedMonitor(publisher, () -> leader, "1000");
    }

    @Test
    void theFirstCheckAlwaysCountsAsANewVersion() {
        // The last-seen version is held in memory, so a fresh process has nothing to compare against. Not new
        // behaviour: under camel-master, leadership moving to a pod that had not run the check did the same.
        monitor(true).checkForNewVersionOnSchedule();

        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_CANDIDATE_BUILD_QUEUE).size());
    }

    @Test
    void anUnchangedVersionTriggersNothingOnTheSecondCheck() {
        StubbedMonitor monitor = monitor(true);

        monitor.checkForNewVersionOnSchedule();
        publisher.clear();
        monitor.checkForNewVersionOnSchedule();

        assertTrue(publisher.published().isEmpty(), "an unchanged builder triggered a candidate build");
    }

    @Test
    void aChangedVersionTriggersAnotherBuild() {
        StubbedMonitor monitor = monitor(true);
        monitor.checkForNewVersionOnSchedule();
        publisher.clear();

        monitor.resourceVersion = "1001";
        monitor.checkForNewVersionOnSchedule();

        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_CANDIDATE_BUILD_QUEUE).size());
    }

    @Test
    void onlyTheLeaderChecks() {
        monitor(false).checkForNewVersionOnSchedule();

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void aNonLeaderDoesNotConsumeTheVersionItWouldHaveSeen() {
        // If the check ran far enough to record the version, the pod that later becomes leader would think it
        // had already seen it and skip the build.
        StubbedMonitor monitor = monitor(false);
        monitor.checkForNewVersionOnSchedule();

        assertTrue(monitor.isNewVersion(), "the version was recorded despite not being the leader");
    }
}
