package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchRunner;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.otp.otp2.Otp2BaseGraphBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ_PREFIX;

/**
 * Builds the OTP2 street graph: OSM and elevation data, no transit.
 *
 * <p>Replaces {@code Otp2BaseGraphRouteBuilder}. The two queues it consumed are now
 * {@link Otp2BaseGraphBuildConsumer} and {@link Otp2BaseGraphCandidateBuildConsumer}, which record a request
 * and return; this serves whatever has accumulated. A candidate build differs only in where the graph is
 * written and which NeTEx build it triggers, which is what the {@code RutebankenOtpBuildCandidate} exchange
 * property decided.
 *
 * <p>A build that fails is reported as FAILED and its requests are consumed rather than retried, as the
 * route's {@code doCatch(Exception)} did - the graph builder is a Kubernetes job, and a failing one fails the
 * same way every time.
 */
@Component
public class Otp2BaseGraphBuild {

    /** Batch kinds. The two aggregations were separate, so they stay separate here. */
    public static final String KIND = "otp2-base-graph";
    public static final String CANDIDATE_KIND = "otp2-base-graph-candidate";

    /**
     * Both kinds, so a production build and a candidate build cannot overlap - they write the same
     * published street graph path. Named after the route both aggregate controllers were registered under,
     * which is what serialised them under Camel.
     */
    public static final String EXCLUSION_GROUP = "otp2-base-graph-build";

    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2BaseGraphBuild.class);

    private final BatchRunner batchRunner;
    private final Otp2BaseGraphBuilder graphBuilder;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final Otp2GraphWorkDirectory workDirectory;
    private final MardukPubSubPublisher publisher;
    private final JobEventPublisher jobEvents;
    private final boolean scheduleEnabled;

    public Otp2BaseGraphBuild(
            BatchRunner batchRunner,
            Otp2BaseGraphBuilder graphBuilder,
            MardukInternalBlobStoreService internalBlobStore,
            Otp2GraphWorkDirectory workDirectory,
            MardukPubSubPublisher publisher,
            JobEventPublisher jobEvents,
            @Value("${otp2.graph.build.autoStartup:true}") boolean scheduleEnabled) {
        this.batchRunner = batchRunner;
        this.graphBuilder = graphBuilder;
        this.internalBlobStore = internalBlobStore;
        this.workDirectory = workDirectory;
        this.publisher = publisher;
        this.jobEvents = jobEvents;
        this.scheduleEnabled = scheduleEnabled;
    }

    /**
     * Five seconds, matching how often {@code quartz://marduk/checkAggregation} poked the idle-route check
     * that completed the aggregation. {@code fixedDelay} rather than {@code fixedRate}, so a tick never
     * starts while a build from the previous one is still running.
     *
     * <p>{@code autoStartup} gates the schedule and nothing else. It used to decide whether the route
     * started, which also silenced the queue; now the admin endpoint that publishes a build request still
     * works, and the request waits until the schedule is switched back on.
     */
    @Scheduled(fixedDelayString = "${otp2.graph.build.batch.interval.ms:5000}", scheduler = "graphBuildScheduler")
    void buildOnSchedule() {
        if (!scheduleEnabled) {
            return;
        }
        batchRunner.runOverWholeBatch(EXCLUSION_GROUP, KIND, batch -> build(batch.aggregate(), false));
    }

    @Scheduled(fixedDelayString = "${otp2.graph.build.batch.interval.ms:5000}", scheduler = "graphBuildScheduler")
    void buildCandidateOnSchedule() {
        if (!scheduleEnabled) {
            return;
        }
        batchRunner.runOverWholeBatch(EXCLUSION_GROUP, CANDIDATE_KIND,
                batch -> build(batch.aggregate(), true));
    }

    /**
     * Builds one graph for the whole batch.
     *
     * @param request the batch's aggregate message: the newest request's headers under a correlation id of
     *                the batch's own, which is what {@code setNewCorrelationId} gave the aggregated exchange
     */
    void build(MardukMessage request, boolean candidate) {
        MardukMdc.set(request);

        String timestamp = Otp2GraphWorkDirectory.timestamp();
        reportGraphJob(request, timestamp, JobEvent.State.STARTED);
        String workDir = workDirectory.path(timestamp);
        LOGGER.info("Starting OTP2 base graph building in directory {}.", workDir);

        LOGGER.info("Preparing OTP2 graph with all non-transit data...");
        try {
            buildAndPublish(request, candidate, timestamp, workDir);
        } catch (RuntimeException e) {
            LOGGER.error("OTP2 Base Graph building failed: {}", e.getMessage(), e);
            reportGraphJob(request, timestamp, JobEvent.State.FAILED);
        }
        LOGGER.info("Done with OTP2 base graph building.");
    }

    private void buildAndPublish(MardukMessage request, boolean candidate, String timestamp, String workDir) {
        graphBuilder.build(workDir, timestamp, candidate);
        LOGGER.info("Done building new OTP2 base graph.");

        String filePrefix = workDir + "/" + OTP2_BASE_GRAPH_OBJ_PREFIX;
        BlobStoreFiles.File built = internalBlobStore.findBlob(filePrefix);
        if (built == null) {
            throw new IllegalStateException("No OTP2 base graph matching the file prefix " + filePrefix);
        }
        LOGGER.info("Found OTP2 base graph named {} matching file prefix {}", built.getFileNameOnly(), filePrefix);

        Otp2GraphPublishing.BaseGraph paths = Otp2GraphPublishing.baseGraph(
                workDir, built.getFileNameOnly(), workDirectory.blobStoreSubdirectory());
        internalBlobStore.copyBlobInBucket(paths.builtPath(), paths.publishedPath());

        triggerNetexGraphBuild(request, candidate);
        reportGraphJob(request, timestamp, JobEvent.State.OK);
        // Only on success, as the route did: a failed build leaves its output for inspection.
        workDirectory.delete(workDir);
    }

    private void triggerNetexGraphBuild(MardukMessage request, boolean candidate) {
        request.setBody("");
        if (candidate) {
            LOGGER.info("Copied new OTP2 candidate base graph, triggering candidate NeTEx graph build");
            publisher.publish(MardukQueues.OTP2_GRAPH_CANDIDATE_BUILD_QUEUE, request);
        } else {
            LOGGER.info("Copied new OTP2 base graph, triggering NeTex graph build");
            publisher.publish(MardukQueues.OTP2_GRAPH_BUILD_QUEUE, request);
        }
    }

    /** The graph job's correlation id is the build timestamp, not the request's. */
    private void reportGraphJob(MardukMessage request, String timestamp, JobEvent.State state) {
        jobEvents.reportSystemJob(request, builder -> builder
                .jobDomain(JobEvent.JobDomain.GRAPH)
                .action(JobEvent.TimetableAction.OTP2_BUILD_BASE)
                .state(state)
                .correlationId(timestamp));
    }
}
