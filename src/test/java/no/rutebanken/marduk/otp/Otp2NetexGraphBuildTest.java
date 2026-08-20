package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchRunner;
import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.InFlightWork;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.routes.otp.otp2.Otp2NetexGraphBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_REPORT_INDEX_FILE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Otp2NetexGraphBuildTest {

    private static final String INTERNAL_CONTAINER = "marduk-internal";
    private static final String GRAPHS_CONTAINER = "otp-graphs";
    private static final String REPORT_CONTAINER = "otpreport";
    private static final String GRAPH_FILE_NAME = "Graph-otp2-EN-0051.obj";
    private static final String CURRENT_FILE = "current-otp2";

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();

    private InMemoryMardukBlobStoreRepository internalRepository;
    private InMemoryMardukBlobStoreRepository graphsRepository;
    private InMemoryMardukBlobStoreRepository reportRepository;
    private MardukInternalBlobStoreService internalBlobStore;
    private OtpGraphsBlobStoreService otpGraphsBlobStore;
    private OtpReportBlobStoreService otpReportBlobStore;
    private RecordingPubSubPublisher publisher;
    private BatchedRequests requests;
    private StubMergedNetexExport mergedNetexExport;

    private final List<String> builtWorkDirs = new ArrayList<>();
    private final List<Boolean> builtAsCandidate = new ArrayList<>();
    private boolean builderWritesAGraph = true;
    private RuntimeException builderFailure;

    @BeforeEach
    void setUp() {
        internalRepository = new InMemoryMardukBlobStoreRepository(buckets);
        graphsRepository = new InMemoryMardukBlobStoreRepository(buckets);
        reportRepository = new InMemoryMardukBlobStoreRepository(buckets);
        internalBlobStore = new MardukInternalBlobStoreService(INTERNAL_CONTAINER, internalRepository);
        otpGraphsBlobStore = new OtpGraphsBlobStoreService(GRAPHS_CONTAINER, graphsRepository);
        otpReportBlobStore = new OtpReportBlobStoreService(REPORT_CONTAINER, reportRepository);
        publisher = new RecordingPubSubPublisher();
        requests = mock(BatchedRequests.class);
        mergedNetexExport = new StubMergedNetexExport();
    }

    private Otp2NetexGraphBuild build(boolean scheduleEnabled, boolean leader) {
        return build(new BatchRunner(requests, () -> leader, new InFlightWork()), scheduleEnabled);
    }

    private Otp2NetexGraphBuild build(BatchRunner batchRunner, boolean scheduleEnabled) {
        return new Otp2NetexGraphBuild(
                batchRunner,
                graphBuilder(),
                mergedNetexExport,
                internalBlobStore,
                otpGraphsBlobStore,
                otpReportBlobStore,
                new Otp2GraphWorkDirectory(internalBlobStore, "graphs", true),
                new JobEventPublisher(publisher),
                GRAPHS_CONTAINER,
                REPORT_CONTAINER,
                CURRENT_FILE,
                scheduleEnabled);
    }

    private Otp2NetexGraphBuilder graphBuilder() {
        return new Otp2NetexGraphBuilder() {
            @Override
            public void build(String otpWorkDir, String timestamp, boolean candidate) {
                builtWorkDirs.add(otpWorkDir);
                builtAsCandidate.add(candidate);
                if (builderWritesAGraph) {
                    internalRepository.uploadBlob(otpWorkDir + '/' + GRAPH_FILE_NAME, dummy());
                }
                internalRepository.uploadBlob(otpWorkDir + "/report/report.html", dummy());
                if (builderFailure != null) {
                    throw builderFailure;
                }
            }
        };
    }

    /** The merged export is exercised by its own test; here only its verdict matters. */
    private static class StubMergedNetexExport extends Otp2MergedNetexExport {

        private boolean exported = true;
        private int exports;

        StubMergedNetexExport() {
            super(null, null, null, "", "", "", "");
        }

        @Override
        public boolean export(MardukMessage message) {
            exports++;
            return exported;
        }
    }

    private static ByteArrayInputStream dummy() {
        return new ByteArrayInputStream("dummy".getBytes(StandardCharsets.UTF_8));
    }

    private static MardukMessage request(String correlationId, String referential) {
        MardukMessage message = new MardukMessage()
                .setHeader(CORRELATION_ID, correlationId)
                .setHeader(PROVIDER_ID, 2L);
        return referential == null ? message : message.setHeader(CHOUETTE_REFERENTIAL, referential);
    }

    private BatchedRequests.Batch batch(MardukMessage... messages) {
        return new BatchedRequests.Batch(
                Otp2NetexGraphBuild.KIND, UUID.randomUUID().toString(), List.of(messages));
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(published -> JobEvent.fromString(published.body()))
                .toList();
    }

    private List<JobEvent> graphEvents() {
        return reportedEvents().stream()
                .filter(event -> JobEvent.JobDomain.GRAPH.equals(event.getDomain()))
                .toList();
    }

    private List<JobEvent> providerEvents() {
        return reportedEvents().stream()
                .filter(event -> JobEvent.JobDomain.TIMETABLE.equals(event.getDomain()))
                .toList();
    }

    private String publishedGraphPath() {
        return graphsRepository.listBlobs("netex-otp2/EN-0051/").getFiles().stream()
                .map(BlobStoreFiles.File::getName)
                .filter(name -> name.endsWith(GRAPH_FILE_NAME))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no published graph in " + buckets.get(GRAPHS_CONTAINER)));
    }

    private static String contentsOf(InMemoryMardukBlobStoreRepository repository, String name) throws IOException {
        try (InputStream contents = repository.getBlob(name)) {
            return contents == null ? null : new String(contents.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void aGraphBuildPublishesTheGraphAndPointsCurrentAtIt() throws IOException {
        build(true, true).build(batch(request("corr-id", "rb_rut")), false);

        String published = publishedGraphPath();
        assertTrue(published.matches("netex-otp2/EN-0051/\\d{17}-" + GRAPH_FILE_NAME),
                "the graph is published under its version and build timestamp, but was " + published);
        assertEquals(published, contentsOf(graphsRepository, "netex-otp2/EN-0051/" + CURRENT_FILE),
                "the versioned pointer file holds the path of the graph it points at");
        assertEquals(published, contentsOf(graphsRepository, CURRENT_FILE),
                "the current pointer file holds the path of the graph it points at");
    }

    @Test
    void aGraphBuildPublishesItsBuildReportAndTheCurrentReportPage() throws IOException {
        build(true, true).build(batch(request("corr-id", "rb_rut")), false);

        List<String> reportFiles = reportRepository.listBlobs("netex-otp2/").getFiles().stream()
                .map(BlobStoreFiles.File::getName)
                .toList();
        assertEquals(1, reportFiles.size(), "expected the copied build report but got " + reportFiles);
        assertTrue(reportFiles.getFirst().endsWith("-report/report.html"), reportFiles.getFirst());

        String indexPage = contentsOf(reportRepository, OTP2_GRAPH_REPORT_INDEX_FILE);
        assertTrue(indexPage.contains("http://" + REPORT_CONTAINER + "/netex-otp2/"), indexPage);
        assertTrue(indexPage.contains("-report/index.html"), indexPage);
    }

    @Test
    void aCandidateBuildLeavesCurrentAndTheReportsAlone() throws IOException {
        build(true, true).build(batch(request("corr-id", "rb_rut")), true);

        assertEquals(publishedGraphPath(), contentsOf(graphsRepository, "netex-otp2/EN-0051/" + CURRENT_FILE),
                "the versioned pointer is written for a candidate too, so the candidate can be loaded");
        assertFalse(graphsRepository.exist(CURRENT_FILE), "a candidate build moved the current pointer");
        assertTrue(reportRepository.listBlobs("").getFiles().isEmpty(),
                "a candidate build published a build report");
    }

    @Test
    void aCandidateBuildSkipsTheMergedNetexExport() {
        build(true, true).build(batch(request("corr-id", "rb_rut")), true);

        assertEquals(0, mergedNetexExport.exports);
        assertEquals(List.of(true), builtAsCandidate);
    }

    @Test
    void everyRequestFromAProviderIsReportedStartedAndThenOk() {
        build(true, true).build(
                batch(request("first", "rb_rut"), request("second", "rb_akt")), false);

        assertEquals(
                List.of("rb_rut", "rb_akt", "rb_rut", "rb_akt"),
                providerEvents().stream().map(JobEvent::getReferential).toList());
        assertEquals(
                List.of(JobEvent.State.STARTED, JobEvent.State.STARTED, JobEvent.State.OK, JobEvent.State.OK),
                providerEvents().stream().map(JobEvent::getState).toList());
        assertTrue(providerEvents().stream().allMatch(event ->
                JobEvent.TimetableAction.OTP2_BUILD_GRAPH.toString().equals(event.getAction())));
    }

    @Test
    void aRequestWithNoReferentialIsNotAProvidersJob() {
        // An admin-triggered build, or the one a finished street graph build publishes.
        build(true, true).build(batch(request("corr-id", null)), false);

        assertTrue(providerEvents().isEmpty());
        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.OK),
                graphEvents().stream().map(JobEvent::getState).toList());
    }

    @Test
    void theGraphJobIsReportedUnderTheBuildTimestampRatherThanTheRequestsCorrelationId() {
        build(true, true).build(batch(request("corr-id", "rb_rut")), false);

        List<String> correlationIds = graphEvents().stream().map(JobEvent::getCorrelationId).distinct().toList();
        assertEquals(1, correlationIds.size(), "the started and finished events belong to different jobs");
        assertTrue(correlationIds.getFirst().matches("\\d{17}"), correlationIds.getFirst());
    }

    @Test
    void aFailingBuilderReportsFailedForTheGraphAndForEveryProvider() {
        builderFailure = new IllegalStateException("the graph builder job failed");

        build(true, true).build(batch(request("first", "rb_rut"), request("second", "rb_akt")), false);

        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.FAILED),
                graphEvents().stream().map(JobEvent::getState).toList());
        assertEquals(
                List.of(JobEvent.State.STARTED, JobEvent.State.STARTED,
                        JobEvent.State.FAILED, JobEvent.State.FAILED),
                providerEvents().stream().map(JobEvent::getState).toList());
    }

    @Test
    void aFailedBuildCleansUpItsWorkDirectory() {
        builderFailure = new IllegalStateException("the graph builder job failed");

        build(true, true).build(batch(request("corr-id", "rb_rut")), false);

        assertTrue(internalRepository.listBlobs(builtWorkDirs.getFirst()).getFiles().isEmpty(),
                "the remote work directory was left behind");
    }

    @Test
    void aSuccessfulBuildCleansUpItsWorkDirectory() {
        build(true, true).build(batch(request("corr-id", "rb_rut")), false);

        assertTrue(internalRepository.listBlobs(builtWorkDirs.getFirst()).getFiles().isEmpty(),
                "the remote work directory was left behind");
    }

    @Test
    void aMissingStopPlaceExportAbortsTheBuildWithoutConcludingTheGraphJob() {
        // Preserved from the route's .stop(): nothing reports the graph job as failed, so it stays STARTED in
        // nabu until someone looks. Worth knowing about rather than a behaviour to rely on.
        mergedNetexExport.exported = false;

        build(true, true).build(batch(request("corr-id", "rb_rut")), false);

        assertEquals(List.of(JobEvent.State.STARTED), graphEvents().stream().map(JobEvent::getState).toList());
        assertEquals(List.of(JobEvent.State.STARTED), providerEvents().stream().map(JobEvent::getState).toList());
        assertTrue(builtWorkDirs.isEmpty(), "the graph was built without any transit data to build it from");
    }

    @Test
    void aMissingStopPlaceExportConsumesItsRequestsToo() {
        // The early return is a normal return, so the rows are deleted. Pinned because BatchRunner's javadoc
        // says so, and because the test above calls build() directly and would not notice either way.
        mergedNetexExport.exported = false;
        BatchedRequests.Batch claimed = batch(request("corr-id", "rb_rut"));
        when(requests.claim(Otp2NetexGraphBuild.KIND)).thenReturn(claimed);

        build(true, true).buildOnSchedule();

        verify(requests).complete(claimed);
        verify(requests, never()).release(any());
    }

    @Test
    void aFailedBuildConsumesItsRequestsRatherThanRetryingThem() {
        builderFailure = new IllegalStateException("the graph builder job failed");
        BatchedRequests.Batch claimed = batch(request("corr-id", "rb_rut"));
        when(requests.claim(Otp2NetexGraphBuild.KIND)).thenReturn(claimed);

        build(true, true).buildOnSchedule();

        verify(requests).complete(claimed);
        verify(requests, never()).release(any());
    }

    @Test
    void aFailureWhilePublishingReleasesTheBatchForTheNextTick() {
        // Publishing sat outside the route's doTry, so a failure there nacked the messages instead of
        // reporting the build as failed. Losing a finished graph would be worse than building it again.
        builderWritesAGraph = false;
        BatchedRequests.Batch claimed = batch(request("corr-id", "rb_rut"));
        when(requests.claim(Otp2NetexGraphBuild.KIND)).thenReturn(claimed);

        assertThrows(IllegalStateException.class, () -> build(true, true).buildOnSchedule());

        verify(requests).release(claimed);
        verify(requests, never()).complete(any());
    }

    @Test
    void oneBuildServesEveryRequestInTheBatch() {
        when(requests.claim(Otp2NetexGraphBuild.KIND))
                .thenReturn(batch(request("first", "rb_rut"), request("second", "rb_akt")));

        build(true, true).buildOnSchedule();

        assertEquals(1, builtWorkDirs.size());
        assertEquals(4, providerEvents().size(), "each request is still reported on its own");
    }

    @Test
    void theScheduledBuildIsGatedByAutoStartupSoTheAdminEndpointStillWorks() {
        when(requests.claim(any())).thenReturn(batch(request("corr-id", "rb_rut")));

        build(false, true).buildOnSchedule();

        verify(requests, never()).claim(any());
        assertTrue(builtWorkDirs.isEmpty());
    }

    @Test
    void aFollowerDoesNotBuild() {
        when(requests.claim(any())).thenReturn(batch(request("corr-id", "rb_rut")));

        build(true, false).buildOnSchedule();

        verify(requests, never()).claim(any());
        assertTrue(builtWorkDirs.isEmpty());
    }

    @Test
    void anEmptyBatchBuildsNothing() {
        when(requests.claim(Otp2NetexGraphBuild.KIND)).thenReturn(batch());

        build(true, true).buildOnSchedule();

        assertTrue(builtWorkDirs.isEmpty());
        verify(requests, never()).complete(any());
    }

    @Test
    void everyProviderKeepsItsOwnCorrelationIdWhileTheBatchGetsOneOfItsOwn() {
        // The route split the aggregated message list, so each provider's status event carried that
        // provider's correlation id. Writing the batch's id onto the newest request loses one of them.
        BatchedRequests.Batch claimed = batch(request("first", "rb_rut"), request("second", "rb_akt"));

        build(true, true).build(claimed, false);

        assertEquals(List.of("first", "second", "first", "second"),
                providerEvents().stream().map(JobEvent::getCorrelationId).toList(),
                "a provider's job event was reported under the batch's correlation id");
    }

    @Test
    void bothTransitGraphBuildsShareOneExclusionGroupSoTheyCannotOverlap() {
        // They share the merged NeTEx export and the versioned graph pointer. Both routes named the same
        // aggregate controller route, whose inflight count is what stopped one starting while the other ran.
        BatchRunner runner = mock(BatchRunner.class);
        Otp2NetexGraphBuild build = build(runner, true);

        build.buildOnSchedule();
        build.buildCandidateOnSchedule();

        verify(runner).runOverWholeBatch(
                eq(Otp2NetexGraphBuild.EXCLUSION_GROUP), eq(Otp2NetexGraphBuild.KIND), any());
        verify(runner).runOverWholeBatch(
                eq(Otp2NetexGraphBuild.EXCLUSION_GROUP), eq(Otp2NetexGraphBuild.CANDIDATE_KIND), any());
    }

    @Test
    void theCandidateScheduleServesTheCandidateBatch() {
        when(requests.claim(Otp2NetexGraphBuild.CANDIDATE_KIND))
                .thenReturn(batch(request("corr-id", "rb_rut")));

        build(true, true).buildCandidateOnSchedule();

        assertEquals(List.of(true), builtAsCandidate);
    }
}
