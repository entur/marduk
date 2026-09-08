package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchRunner;
import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.routes.otp.otp2.Otp2NetexGraphBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_REPORT_INDEX_FILE;
import static no.rutebanken.marduk.Constants.OTP2_NETEX_GRAPH_DIR;

/**
 * Builds the full OTP2 graph: the street graph loaded back in, plus every provider's transit data.
 *
 * <p>Replaces {@code Otp2NetexGraphRouteBuilder}. The two queues it consumed are now
 * {@link Otp2NetexGraphBuildConsumer} and {@link Otp2NetexGraphCandidateBuildConsumer}, which record a
 * request and return; this serves whatever has accumulated.
 *
 * <p>A candidate build skips the merged NeTEx export, does not move the {@code current-otp2} pointer and does
 * not publish a build report - it exists to find out whether a new graph builder can build at all. That is
 * what the {@code RutebankenOtpBuildCandidate} exchange property decided.
 */
@Component
public class Otp2NetexGraphBuild {

    /** Batch kinds. The two aggregations were separate, so they stay separate here. */
    public static final String KIND = "otp2-netex-graph";
    public static final String CANDIDATE_KIND = "otp2-netex-graph-candidate";

    /**
     * Both kinds, so a production build and a candidate build cannot overlap - they share the merged NeTEx
     * export and the versioned graph pointer. Named after the route both aggregate controllers were
     * registered under, which is what serialised them under Camel.
     */
    public static final String EXCLUSION_GROUP = "otp2-remote-netex-graph-build";

    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2NetexGraphBuild.class);

    private final BatchRunner batchRunner;
    private final Otp2NetexGraphBuilder graphBuilder;
    private final Otp2MergedNetexExport mergedNetexExport;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final OtpGraphsBlobStoreService otpGraphsBlobStore;
    private final OtpReportBlobStoreService otpReportBlobStore;
    private final Otp2GraphWorkDirectory workDirectory;
    private final JobEventPublisher jobEvents;
    private final String otpGraphsBucketName;
    private final String otpReportContainerName;
    private final String otpGraphCurrentFile;
    private final boolean scheduleEnabled;

    public Otp2NetexGraphBuild(
            BatchRunner batchRunner,
            Otp2NetexGraphBuilder graphBuilder,
            Otp2MergedNetexExport mergedNetexExport,
            MardukInternalBlobStoreService internalBlobStore,
            OtpGraphsBlobStoreService otpGraphsBlobStore,
            OtpReportBlobStoreService otpReportBlobStore,
            Otp2GraphWorkDirectory workDirectory,
            JobEventPublisher jobEvents,
            @Value("${blobstore.gcs.graphs.container.name:otp-graphs}") String otpGraphsBucketName,
            @Value("${blobstore.gcs.otpreport.container.name}") String otpReportContainerName,
            @Value("${otp2.graph.current.file:current-otp2}") String otpGraphCurrentFile,
            @Value("${otp2.graph.build.autoStartup:true}") boolean scheduleEnabled) {
        this.batchRunner = batchRunner;
        this.graphBuilder = graphBuilder;
        this.mergedNetexExport = mergedNetexExport;
        this.internalBlobStore = internalBlobStore;
        this.otpGraphsBlobStore = otpGraphsBlobStore;
        this.otpReportBlobStore = otpReportBlobStore;
        this.workDirectory = workDirectory;
        this.jobEvents = jobEvents;
        this.otpGraphsBucketName = otpGraphsBucketName;
        this.otpReportContainerName = otpReportContainerName;
        this.otpGraphCurrentFile = otpGraphCurrentFile;
        this.scheduleEnabled = scheduleEnabled;
    }

    /**
     * Five seconds, matching how often {@code quartz://marduk/checkAggregation} poked the idle-route check
     * that completed the aggregation. {@code fixedDelay} rather than {@code fixedRate}, so a tick never
     * starts while a build from the previous one is still running.
     *
     * <p>{@code autoStartup} gates the schedule and nothing else, so the admin endpoint that publishes a
     * build request still works and the request waits until the schedule is switched back on.
     */
    @Scheduled(fixedDelayString = "${otp2.graph.build.batch.interval.ms:5000}", scheduler = "graphBuildScheduler")
    void buildOnSchedule() {
        runBatch(KIND, false);
    }

    @Scheduled(fixedDelayString = "${otp2.graph.build.batch.interval.ms:5000}", scheduler = "graphBuildScheduler")
    void buildCandidateOnSchedule() {
        runBatch(CANDIDATE_KIND, true);
    }

    /**
     * Claims and serves one batch.
     *
     * <p>Over the whole batch, not just its newest request: the build reports status per provider for
     * <em>every</em> request in it, which is what {@code direct:sendStatusForOtp2NetexJobs} did by splitting
     * the aggregated message list.
     */
    private void runBatch(String kind, boolean candidate) {
        if (!scheduleEnabled) {
            return;
        }
        batchRunner.runOverWholeBatch(EXCLUSION_GROUP, kind, batch -> build(batch, candidate));
    }

    /**
     * Builds one graph for the whole batch.
     *
     * <p>A build that fails is reported as FAILED and returns, so its requests are consumed rather than
     * retried - the route's {@code doCatch(Exception)} followed by {@code stop()}. A failure anywhere else,
     * in the merged export or in publishing the finished graph, propagates and the batch is retried on the
     * next tick, which is also what the route did by leaving those steps outside the {@code doTry}.
     */
    void build(BatchedRequests.Batch batch, boolean candidate) {
        // The batch's own message, not one of its requests: the graph job and the log lines belong to the
        // batch, while every request in it keeps the correlation id its provider's job was reported under.
        MardukMessage request = batch.aggregate();
        MardukMdc.set(request);

        String timestamp = Otp2GraphWorkDirectory.timestamp();
        reportGraphJob(request, timestamp, JobEvent.State.STARTED);
        reportProviderJobs(batch, JobEvent.State.STARTED);

        String workDir = workDirectory.path(timestamp);
        LOGGER.info("Starting OTP2 graph building in remote directory {}.", workDir);

        if (!candidate && !mergedNetexExport.export(request)) {
            // No stop place export, so there is nothing to build a graph from. The route's .stop() aborted
            // here too, which leaves the graph job reported as STARTED and never concluded.
            return;
        }

        LOGGER.info("Building OTP2 graph...");
        try {
            graphBuilder.build(workDir, timestamp, candidate);
            LOGGER.info("Done building new OTP2 graph.");
            reportProviderJobs(batch, JobEvent.State.OK);
        } catch (RuntimeException e) {
            LOGGER.error("OTP2 Graph building failed: {}", e.getMessage(), e);
            reportGraphJob(request, timestamp, JobEvent.State.FAILED);
            reportProviderJobs(batch, JobEvent.State.FAILED);
            workDirectory.delete(workDir);
            return;
        }

        publish(request, candidate, timestamp, workDir);
        LOGGER.info("Done with OTP2 graph building.");
    }

    private void publish(MardukMessage request, boolean candidate, String timestamp, String workDir) {
        String filePrefix = workDir + "/" + OTP2_GRAPH_OBJ_PREFIX;
        BlobStoreFiles.File built = internalBlobStore.findBlob(filePrefix);
        if (built == null) {
            throw new IllegalStateException("No OTP2 graph matching the file prefix " + filePrefix);
        }
        LOGGER.info("Found OTP2 graph named {} matching file prefix {}", built.getFileNameOnly(), filePrefix);

        Otp2GraphPublishing.NetexGraph paths =
                Otp2GraphPublishing.netexGraph(workDir, timestamp, built.getFileNameOnly());
        internalBlobStore.copyBlobToAnotherBucket(paths.builtPath(), otpGraphsBucketName, paths.publishedPath());
        LOGGER.info("Done copying new OTP2 graph: {}", paths.builtPath());

        String versionedCurrentFile =
                OTP2_NETEX_GRAPH_DIR + "/" + paths.compatibilityVersion() + "/" + otpGraphCurrentFile;
        writeGraphPointer(versionedCurrentFile, paths.publishedPath());
        LOGGER.info("Done uploading reference to versioned current OTP2graph: {}", versionedCurrentFile);

        if (!candidate) {
            LOGGER.info("Uploading reference to current OTP2 graph: {}", otpGraphCurrentFile);
            writeGraphPointer(otpGraphCurrentFile, paths.publishedPath());
            LOGGER.info("Done uploading reference to current OTP2 graph: {}", otpGraphCurrentFile);

            copyBuildReport(workDir, paths.reportVersion());
            writeCurrentReportPage(paths.reportVersion());
            LOGGER.info("Done uploading OTP2 graph build reports.");
        }

        reportGraphJob(request, timestamp, JobEvent.State.OK);
        workDirectory.delete(workDir);
    }

    /** A pointer file whose whole content is the path of the graph it points at. */
    private void writeGraphPointer(String name, String graphPath) {
        otpGraphsBlobStore.uploadBlob(
                name, new ByteArrayInputStream(graphPath.getBytes(StandardCharsets.UTF_8)));
    }

    private void copyBuildReport(String workDir, String reportVersion) {
        LOGGER.info("Copying OTP2 graph build reports to gs://{}/{}", otpReportContainerName, reportVersion);
        internalBlobStore.copyAllBlobs(workDir + "/report", otpReportContainerName, reportVersion);
        LOGGER.info("Done copying OTP2 graph build reports.");
    }

    private void writeCurrentReportPage(String reportVersion) {
        LOGGER.info("Uploading OTP graph build reports current version.");
        otpReportBlobStore.uploadHtmlBlob(OTP2_GRAPH_REPORT_INDEX_FILE, redirectPage(reportVersion));
    }

    private InputStream redirectPage(String version) {
        String url = "http://" + otpReportContainerName + "/" + version + "/index.html";
        String html = """
                <html>
                <head>
                    <meta http-equiv="refresh" content="0; url=%s" />
                </head>
                </html>""".formatted(url);
        return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
    }

    /** The graph job's correlation id is the build timestamp, not the request's. */
    private void reportGraphJob(MardukMessage request, String timestamp, JobEvent.State state) {
        jobEvents.reportSystemJob(request, builder -> builder
                .jobDomain(JobEvent.JobDomain.GRAPH)
                .action(JobEvent.TimetableAction.OTP2_BUILD_GRAPH)
                .state(state)
                .correlationId(timestamp));
    }

    /**
     * Reports the build against every request that came from a provider's import, so an operator sees their
     * dataset reach the graph. A request with no referential - an admin-triggered build, or one following a
     * base graph build - is not a provider's job and is skipped, as the route's filter did.
     */
    private void reportProviderJobs(BatchedRequests.Batch batch, JobEvent.State state) {
        for (MardukMessage request : batch.requests()) {
            if (request.getHeader(CHOUETTE_REFERENTIAL) == null) {
                continue;
            }
            jobEvents.reportProviderJob(request, builder -> builder
                    .timetableAction(JobEvent.TimetableAction.OTP2_BUILD_GRAPH)
                    .state(state));
        }
    }
}
