package no.rutebanken.marduk.batch;

import no.rutebanken.marduk.leader.LeaderElection;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.InFlightWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * Runs whatever has accumulated for one kind of batched request.
 *
 * <p>Replaces {@code IdleRouteAggregationMonitor} and the {@code quartz://marduk/checkAggregation} route that
 * poked it. The aggregator completed a group when the route had no exchange in flight, at a hundred requests,
 * or after a timeout; a tick that takes everything waiting covers all three, because a tick only runs when the
 * previous one has finished.
 *
 * <p>Leader-gated: the work is expensive and once per cluster is the point. A leadership handover can still
 * overlap two runners, which is why {@link BatchedRequests#claim} decides the winner in the database rather
 * than here.
 */
@Component
public class BatchRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchRunner.class);

    private final BatchedRequests requests;
    private final LeaderElection leaderElection;
    private final InFlightWork inFlightWork;
    private final ConcurrentMap<String, Semaphore> exclusionGroups = new ConcurrentHashMap<>();

    public BatchRunner(BatchedRequests requests, LeaderElection leaderElection, InFlightWork inFlightWork) {
        this.requests = requests;
        this.leaderElection = leaderElection;
        this.inFlightWork = inFlightWork;
    }

    /**
     * Claims everything waiting for {@code kind} and hands it to {@code job}.
     *
     * <p>The job is given the newest request, which is what the merged GTFS export - the only caller of this
     * form - ran on: its aggregation preserved five headers off the last message to arrive. The batch exists
     * to avoid running the job several times, not to merge the requests' contents. A job whose identity is
     * the batch's rather than any one request's, as both OTP2 builds' was, takes the whole batch instead and
     * uses {@link BatchedRequests.Batch#aggregate()}.
     *
     * <p>The requests are deleted when the job returns and released when it throws, so whether a failure is
     * retried is the job's decision, not this class's. The merged GTFS export throws and is retried. Both
     * graph builds catch their build failure, report FAILED and return, so those requests are consumed - a
     * failing Kubernetes job fails the same way next tick - as does {@code Otp2NetexGraphBuild} on its early
     * return when there is no stop place export to merge. Its later steps, the merged NeTEx export and
     * publishing the finished graph, do propagate and are retried.
     */
    public void run(String kind, Consumer<MardukMessage> job) {
        run(kind, kind, job);
    }

    /** The same, for a kind that must not run while another kind in {@code exclusionGroup} is running. */
    public void run(String exclusionGroup, String kind, Consumer<MardukMessage> job) {
        runBatch(exclusionGroup, kind, batch -> job.accept(batch.newest()));
    }

    /**
     * The same, for a job that needs every request rather than just the newest.
     *
     * <p>The OTP2 NeTEx graph build needs this: it reports a status event per provider that asked for a
     * build, so it has to see the whole batch.
     */
    public void runOverWholeBatch(String kind, Consumer<BatchedRequests.Batch> job) {
        runOverWholeBatch(kind, kind, job);
    }

    public void runOverWholeBatch(
            String exclusionGroup, String kind, Consumer<BatchedRequests.Batch> job) {
        runBatch(exclusionGroup, kind, job);
    }

    /**
     * Puts back claims left behind by a pod that died mid-batch, without which the requests in them are
     * never served and never seen again.
     */
    @Scheduled(fixedDelayString = "${marduk.batch.claim.sweep.interval.ms:600000}")
    void releaseStaleClaimsOnSchedule() {
        if (!leaderElection.isLeader()) {
            return;
        }
        requests.releaseStaleClaims();
    }

    /**
     * Serves one batch, unless another kind in the same exclusion group is being served.
     *
     * <p>The exclusion group replaces the back pressure the aggregate controllers gave for free. Both OTP2
     * base graph routes named {@code otp2-base-graph-build} in
     * {@code idleRouteAggregationMonitor.getAggregateControllerForRoute}, and both sent to the route with
     * that id; the check only force-completed a group when that route had no exchange in flight, so a
     * production build and a candidate build could not overlap. They share work directories and blob paths,
     * so that mattered. Failing to acquire leaves the requests waiting, which is what the aggregator did.
     */
    private void runBatch(String exclusionGroup, String kind, Consumer<BatchedRequests.Batch> job) {
        if (!leaderElection.isLeader()) {
            LOGGER.debug("Not the leader, leaving the {} batch alone", kind);
            return;
        }
        // A semaphore rather than a lock: the exclusion is per process, not per thread, and a reentrant
        // lock would let a build that triggers another one in its own group through.
        Semaphore exclusive = exclusionGroups.computeIfAbsent(exclusionGroup, group -> new Semaphore(1));
        if (!exclusive.tryAcquire()) {
            LOGGER.debug("Another {} batch is running, leaving the {} requests to accumulate",
                    exclusionGroup, kind);
            return;
        }
        try {
            serve(kind, job);
        } finally {
            exclusive.release();
        }
    }

    private void serve(String kind, Consumer<BatchedRequests.Batch> job) {
        BatchedRequests.Batch batch = requests.claim(kind);
        if (batch.isEmpty()) {
            return;
        }
        MardukMdc.with(batch.newest(), () -> {
            LOGGER.info("Serving {} batched {} requests", batch.size(), kind);
            // Counted as in-flight work so a rolling restart drains a running batch rather than killing it
            // the moment the context closes.
            try (InFlightWork.Tracked tracked = inFlightWork.start()) {
                serveClaimed(batch, job);
            }
        });
    }

    private void serveClaimed(BatchedRequests.Batch batch, Consumer<BatchedRequests.Batch> job) {
        try {
            job.accept(batch);
        } catch (RuntimeException e) {
            try {
                requests.release(batch);
            } catch (RuntimeException releaseFailure) {
                e.addSuppressed(releaseFailure);
            }
            throw e;
        }
        requests.complete(batch);
    }
}
