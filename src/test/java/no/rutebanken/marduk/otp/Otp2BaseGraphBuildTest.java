package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchRunner;
import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.InFlightWork;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.routes.otp.otp2.Otp2BaseGraphBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Otp2BaseGraphBuildTest {

    private static final String INTERNAL_CONTAINER = "marduk-internal";
    private static final String GRAPH_FILE_NAME = OTP2_BASE_GRAPH_OBJ_PREFIX + "-EN-0051.obj";
    private static final String PUBLISHED_GRAPH = "graphs/street/" + GRAPH_FILE_NAME;

    private InMemoryMardukBlobStoreRepository internalRepository;
    private MardukInternalBlobStoreService internalBlobStore;
    private RecordingPubSubPublisher publisher;
    private BatchedRequests requests;

    /** What the graph builder was asked to do, and what it writes into the work directory. */
    private final List<String> builtWorkDirs = new ArrayList<>();
    private final List<Boolean> builtAsCandidate = new ArrayList<>();
    /** The MDC the build ran under, captured while it runs rather than after. */
    private final List<String> builtUnderCodespace = new ArrayList<>();
    private boolean builderWritesAGraph = true;
    private RuntimeException builderFailure;

    @BeforeEach
    void setUp() {
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalBlobStore = new MardukInternalBlobStoreService(INTERNAL_CONTAINER, internalRepository);
        publisher = new RecordingPubSubPublisher();
        requests = mock(BatchedRequests.class);
    }

    private Otp2BaseGraphBuild build(boolean scheduleEnabled, boolean leader) {
        return build(new BatchRunner(requests, () -> leader, new InFlightWork()), scheduleEnabled);
    }

    private Otp2BaseGraphBuild build(BatchRunner batchRunner, boolean scheduleEnabled) {
        return new Otp2BaseGraphBuild(
                batchRunner,
                graphBuilder(),
                internalBlobStore,
                new Otp2GraphWorkDirectory(internalBlobStore, "graphs", true),
                publisher,
                new JobEventPublisher(publisher),
                scheduleEnabled);
    }

    private Otp2BaseGraphBuilder graphBuilder() {
        return new Otp2BaseGraphBuilder() {
            @Override
            public void build(String otpWorkDir, String timestamp, boolean candidate) {
                builtWorkDirs.add(otpWorkDir);
                builtAsCandidate.add(candidate);
                builtUnderCodespace.add(org.slf4j.MDC.get("codespace"));
                if (builderWritesAGraph) {
                    internalRepository.uploadBlob(otpWorkDir + '/' + GRAPH_FILE_NAME, dummy());
                }
                internalRepository.uploadBlob(otpWorkDir + "/partial-output", dummy());
                if (builderFailure != null) {
                    throw builderFailure;
                }
            }
        };
    }

    private static ByteArrayInputStream dummy() {
        return new ByteArrayInputStream("dummy".getBytes(StandardCharsets.UTF_8));
    }

    private static MardukMessage request(String correlationId) {
        return new MardukMessage()
                .setHeader(CORRELATION_ID, correlationId)
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CHOUETTE_REFERENTIAL, "rb_rut");
    }

    private BatchedRequests.Batch batchOf(String kind, String... correlationIds) {
        List<MardukMessage> messages = new ArrayList<>();
        for (String correlationId : correlationIds) {
            messages.add(request(correlationId));
        }
        BatchedRequests.Batch batch = new BatchedRequests.Batch(kind, UUID.randomUUID().toString(), messages);
        when(requests.claim(kind)).thenReturn(batch);
        return batch;
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(published -> JobEvent.fromString(published.body()))
                .toList();
    }

    @Test
    void aStreetGraphBuildPublishesTheGraphAndTriggersTheTransitGraphBuild() {
        build(true, true).build(request("corr-id"), false);

        assertTrue(internalRepository.exist(PUBLISHED_GRAPH),
                "the built street graph was not copied to the street graph directory");
        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_GRAPH_BUILD_QUEUE).size());
        assertEquals(0, publisher.publishedTo(MardukQueues.OTP2_GRAPH_CANDIDATE_BUILD_QUEUE).size());

        List<JobEvent> events = reportedEvents();
        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.OK),
                events.stream().map(JobEvent::getState).toList());
        assertTrue(events.stream().allMatch(event -> JobEvent.JobDomain.GRAPH.equals(event.getDomain())
                && JobEvent.TimetableAction.OTP2_BUILD_BASE.toString().equals(event.getAction())));
    }

    @Test
    void aCandidateBuildTriggersTheCandidateTransitGraphBuildInstead() {
        build(true, true).build(request("corr-id"), true);

        assertEquals(List.of(true), builtAsCandidate);
        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_GRAPH_CANDIDATE_BUILD_QUEUE).size());
        assertEquals(0, publisher.publishedTo(MardukQueues.OTP2_GRAPH_BUILD_QUEUE).size());
        assertTrue(internalRepository.exist(PUBLISHED_GRAPH),
                "a candidate street graph is published to the same place as the ordinary one");
    }

    @Test
    void theGraphJobIsReportedUnderTheBuildTimestampRatherThanTheRequestsCorrelationId() {
        build(true, true).build(request("corr-id"), false);

        List<String> correlationIds = reportedEvents().stream().map(JobEvent::getCorrelationId).distinct().toList();
        assertEquals(1, correlationIds.size(), "the started and finished events belong to different jobs");
        assertNotEquals("corr-id", correlationIds.getFirst());
        assertTrue(correlationIds.getFirst().matches("\\d{17}"),
                "expected a yyyyMMddHHmmssSSS timestamp but got " + correlationIds.getFirst());
    }

    @Test
    void theWorkDirectoryIsEmptiedAfterASuccessfulBuild() {
        build(true, true).build(request("corr-id"), false);

        String workDir = builtWorkDirs.getFirst();
        assertTrue(internalRepository.listBlobs(workDir).getFiles().isEmpty(),
                "the remote work directory was left behind");
    }

    @Test
    void aFailingBuilderIsReportedAsFailedWithoutTriggeringATransitGraphBuild() {
        builderFailure = new IllegalStateException("the graph builder job failed");

        build(true, true).build(request("corr-id"), false);

        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.FAILED),
                reportedEvents().stream().map(JobEvent::getState).toList());
        assertEquals(0, publisher.publishedTo(MardukQueues.OTP2_GRAPH_BUILD_QUEUE).size());
    }

    @Test
    void aFailedBuildLeavesItsWorkDirectoryForInspection() {
        // The route cleaned up on the success path only, and that is preserved: the graph builder's partial
        // output is the only evidence of why it failed.
        builderFailure = new IllegalStateException("the graph builder job failed");

        build(true, true).build(request("corr-id"), false);

        assertFalse(internalRepository.listBlobs(builtWorkDirs.getFirst()).getFiles().isEmpty());
    }

    @Test
    void aBuilderThatProducedNoGraphFailsTheBuild() {
        builderWritesAGraph = false;

        build(true, true).build(request("corr-id"), false);

        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.FAILED),
                reportedEvents().stream().map(JobEvent::getState).toList());
    }

    @Test
    void aFailedBuildConsumesItsRequestsRatherThanRetryingThem() {
        // doCatch(Exception) swallowed the failure, so the aggregated messages were acknowledged. A graph
        // builder job that fails fails the same way next tick, and retrying it forever would report FAILED
        // every five seconds.
        builderFailure = new IllegalStateException("the graph builder job failed");
        BatchedRequests.Batch batch = batchOf(Otp2BaseGraphBuild.KIND, "corr-id");

        build(true, true).buildOnSchedule();

        verify(requests).complete(batch);
        verify(requests, never()).release(any());
    }

    @Test
    void oneBuildServesEveryRequestInTheBatch() {
        batchOf(Otp2BaseGraphBuild.KIND, "first", "second", "third");

        build(true, true).buildOnSchedule();

        assertEquals(1, builtWorkDirs.size());
    }

    @Test
    void theScheduledBuildIsGatedByAutoStartupSoTheAdminEndpointStillWorks() {
        // The flag used to stop the route, which also stopped the queue being consumed. Now the request is
        // recorded either way and only the schedule is off, so nothing is lost while it is switched off.
        batchOf(Otp2BaseGraphBuild.KIND, "corr-id");

        build(false, true).buildOnSchedule();

        verify(requests, never()).claim(any());
        assertTrue(builtWorkDirs.isEmpty());
    }

    @Test
    void aFollowerDoesNotBuild() {
        batchOf(Otp2BaseGraphBuild.KIND, "corr-id");

        build(true, false).buildOnSchedule();

        verify(requests, never()).claim(any());
        assertTrue(builtWorkDirs.isEmpty());
    }

    @Test
    void theTriggeredTransitBuildCarriesTheBatchesOwnCorrelationIdRatherThanARequests() {
        // setNewCorrelationId gave the aggregated exchange an id of its own. Writing that id onto the
        // newest request instead would move one arbitrary provider's job under the batch's id.
        BatchedRequests.Batch batch = batchOf(Otp2BaseGraphBuild.KIND, "first", "newest");

        build(true, true).buildOnSchedule();

        assertEquals(batch.claimId(),
                publisher.publishedTo(MardukQueues.OTP2_GRAPH_BUILD_QUEUE).getFirst()
                        .attributes().get(CORRELATION_ID));
        assertEquals(List.of("first", "newest"),
                batch.requests().stream().map(m -> m.getHeader(CORRELATION_ID, String.class)).toList(),
                "a contributing request was stamped with the batch's correlation id");
    }

    @Test
    void theTriggeredTransitBuildCarriesNoProvidersIdentity() {
        // Every header on this message goes out as a PubSub attribute. Master aggregated into an empty
        // exchange, so the transit build request carried no referential and the transit build reported it
        // as nobody's job - which is the premise of aRequestWithNoReferentialIsNotAProvidersJob over in
        // Otp2NetexGraphBuildTest. Copying a request's headers here would report one arbitrary provider a
        // graph job it never asked for.
        batchOf(Otp2BaseGraphBuild.KIND, "first", "newest");

        build(true, true).buildOnSchedule();

        Map<String, String> attributes =
                publisher.publishedTo(MardukQueues.OTP2_GRAPH_BUILD_QUEUE).getFirst().attributes();
        assertNull(attributes.get(CHOUETTE_REFERENTIAL),
                "a provider's referential reached the transit build request: " + attributes);
        assertNull(attributes.get(PROVIDER_ID),
                "a provider id reached the transit build request: " + attributes);
    }

    @Test
    void theStreetGraphBuildLogsUnderNoCodespace() {
        // The aggregate has no referential, so MardukMdc.set leaves the codespace unset, as
        // updateMdcFromHeaders did on an exchange with no headers. Read during the build: MDC is
        // thread-local and the assertion would otherwise see whatever the previous test left behind.
        batchOf(Otp2BaseGraphBuild.KIND, "corr-id");

        build(true, true).buildOnSchedule();

        assertNull(builtUnderCodespace.getFirst(),
                "one arbitrary provider's codespace labelled the whole graph build");
    }

    @Test
    void bothStreetGraphBuildsShareOneExclusionGroupSoTheyCannotOverlap() {
        // They write the same published street graph path. Both routes named the same aggregate controller
        // route, whose inflight count is what stopped one starting while the other ran.
        BatchRunner runner = mock(BatchRunner.class);
        Otp2BaseGraphBuild build = build(runner, true);

        build.buildOnSchedule();
        build.buildCandidateOnSchedule();

        verify(runner).runOverWholeBatch(
                eq(Otp2BaseGraphBuild.EXCLUSION_GROUP), eq(Otp2BaseGraphBuild.KIND), any());
        verify(runner).runOverWholeBatch(
                eq(Otp2BaseGraphBuild.EXCLUSION_GROUP), eq(Otp2BaseGraphBuild.CANDIDATE_KIND), any());
    }

    @Test
    void theCandidateScheduleServesTheCandidateBatch() {
        batchOf(Otp2BaseGraphBuild.CANDIDATE_KIND, "corr-id");

        build(true, true).buildCandidateOnSchedule();

        assertEquals(List.of(true), builtAsCandidate);
    }
}
