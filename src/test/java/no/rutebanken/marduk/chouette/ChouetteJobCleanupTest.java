package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.leader.LeaderElection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChouetteJobCleanupTest {

    /** Records the URLs that would have been called. */
    private static class RecordingClient extends ChouetteClient {
        final List<String> deleted = new ArrayList<>();

        RecordingClient() {
            super("http://chouette", noRetries());
        }

        @Override
        public void delete(String url) {
            deleted.add(url);
        }
    }

    private final RecordingClient chouette = new RecordingClient();

    private ChouetteJobCleanup cleanup(boolean leader, boolean scheduleEnabled) {
        LeaderElection leaderElection = () -> leader;
        return new ChouetteJobCleanup(chouette, leaderElection, 20, 20, scheduleEnabled);
    }

    @Test
    void theScheduledCleanupUsesTheConfiguredRetention() {
        cleanup(true, true).removeOldJobsOnSchedule();

        assertEquals(
                List.of("/chouette_iev/admin/completed_jobs?keepJobs=20&keepDays=20"),
                chouette.deleted);
    }

    @Test
    void onlyTheLeaderRunsTheScheduledCleanup() {
        // Two pods share the Chouette instance; both deleting is harmless but pointless, and leadership is
        // what kept it to one under camel-master.
        cleanup(false, true).removeOldJobsOnSchedule();

        assertTrue(chouette.deleted.isEmpty());
    }

    @Test
    void autoStartupFalseDisablesTheSchedule() {
        cleanup(true, false).removeOldJobsOnSchedule();

        assertTrue(chouette.deleted.isEmpty());
    }

    @Test
    void autoStartupFalseStillAllowsTheAdminEndpointToRunIt() {
        // The flag used to decide whether the quartz route started, which also made the admin path
        // unreachable. Gating only the schedule keeps the manual cleanup available.
        cleanup(true, false).removeOldJobs(null, null);

        assertEquals(
                List.of("/chouette_iev/admin/completed_jobs?keepJobs=20&keepDays=20"),
                chouette.deleted);
    }

    @Test
    void explicitRetentionOverridesTheConfiguredDefaults() {
        cleanup(true, true).removeOldJobs(5, 7);

        assertEquals(
                List.of("/chouette_iev/admin/completed_jobs?keepJobs=5&keepDays=7"),
                chouette.deleted);
    }

    @Test
    void eitherRetentionValueCanFallBackOnItsOwn() {
        cleanup(true, true).removeOldJobs(5, null);

        assertEquals(
                List.of("/chouette_iev/admin/completed_jobs?keepJobs=5&keepDays=20"),
                chouette.deleted);
    }

    @Test
    void aNonLeaderCanStillBeAskedDirectly() {
        // The admin endpoint is served by whichever pod the request lands on, so it must not check leadership.
        cleanup(false, true).removeOldJobs(null, null);

        assertEquals(1, chouette.deleted.size());
    }
}
