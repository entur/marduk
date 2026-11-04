package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.leader.LeaderElection;
import no.rutebanken.marduk.pipeline.MardukMdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deletes Chouette's completed jobs, nightly and on request.
 *
 * <p>Replaces the quartz half of {@code ChouetteRemoveOldJobsRouteBuilder}. Three differences from what
 * camel-master plus quartz did:
 *
 * <ul>
 *   <li><b>No lenient-fire-time guard.</b> {@code shouldQuartzRouteTrigger} re-checked that the fire time
 *       was close to a scheduled time, because a {@code master:} route resumed on a leadership change and
 *       quartz re-fired a trigger it had missed. A Spring scheduled task has no such resume, so the guard
 *       has nothing to guard against and is gone rather than reimplemented.
 *   <li><b>Leadership is checked when the task fires</b>, not when a route starts. A pod that loses the
 *       lease between two firings simply skips the next one.
 *   <li><b>{@code autoStartup} gates the schedule, not the operation.</b> It used to decide whether the
 *       route started at all; here the task still fires and returns immediately, so the same flag cannot
 *       accidentally disable the admin endpoint that calls the same cleanup by hand.
 * </ul>
 */
@Component
public class ChouetteJobCleanup {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteJobCleanup.class);

    private final ChouetteClient chouette;
    private final LeaderElection leaderElection;
    private final int keepJobs;
    private final int keepDays;
    private final boolean scheduleEnabled;

    public ChouetteJobCleanup(
            ChouetteClient chouette,
            LeaderElection leaderElection,
            @Value("${chouette.remove.old.jobs.keep.jobs:100}") int keepJobs,
            @Value("${chouette.remove.old.jobs.keep.days:100}") int keepDays,
            @Value("${chouette.remove.old.jobs.autoStartup:true}") boolean scheduleEnabled) {
        this.chouette = chouette;
        this.leaderElection = leaderElection;
        this.keepJobs = keepJobs;
        this.keepDays = keepDays;
        this.scheduleEnabled = scheduleEnabled;
    }

    /**
     * The cron is space-separated, unlike the {@code +}-separated quartz trigger it replaces. The ConfigMap
     * renders both from the same helm value so they cannot drift.
     *
     * <p>{@code @Scheduled} parses its cron eagerly, so an unparseable value fails the context at startup
     * rather than silently never firing.
     */
    @Scheduled(cron = "${chouette.remove.old.jobs.cron:0 15 23 * * MON-FRI}", zone = "Europe/Oslo")
    void removeOldJobsOnSchedule() {
        if (!scheduleEnabled) {
            return;
        }
        if (!leaderElection.isLeader()) {
            LOGGER.debug("Not the leader, skipping the scheduled Chouette job cleanup");
            return;
        }
        MardukMdc.clear();
        MardukMdc.setCorrelationId(UUID.randomUUID().toString());
        try {
            LOGGER.info("Scheduled deletion of old jobs in Chouette");
            removeOldJobs(keepJobs, keepDays);
        } finally {
            MardukMdc.clear();
        }
    }

    /**
     * @param keepJobs how many completed jobs to keep, or null for the configured default
     * @param keepDays how many days of completed jobs to keep, or null for the configured default
     */
    public void removeOldJobs(Integer keepJobs, Integer keepDays) {
        int jobs = keepJobs != null ? keepJobs : this.keepJobs;
        int days = keepDays != null ? keepDays : this.keepDays;
        LOGGER.info("Starting Chouette remove old jobs, keeping {} jobs and {} days", jobs, days);
        chouette.delete("/chouette_iev/admin/completed_jobs?keepJobs=" + jobs + "&keepDays=" + days);
        LOGGER.info("Completed Chouette remove old jobs");
    }
}
