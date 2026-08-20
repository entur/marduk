package no.rutebanken.marduk.chouette;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import no.rutebanken.marduk.pubsub.InFlightWork;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_TYPE;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static no.rutebanken.marduk.pipeline.RetryPolicies.retriesWithoutWaiting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Driven against a real HTTP server standing in for Chouette, so the status and report payloads are parsed
 * the way they arrive on the wire.
 */
class ChouetteJobPollerTest {

    private static final String DESTINATION = "direct:processImportResult";

    private HttpServer server;
    private ChouetteClient client;
    private RecordingPubSubPublisher publisher;
    private ThreadPoolTaskScheduler scheduler;
    private InFlightWork inFlightWork;
    private final List<String> dispatched = new CopyOnWriteArrayList<>();
    private final List<MardukMessage> dispatchedMessages = new CopyOnWriteArrayList<>();
    private final List<String> requested = new CopyOnWriteArrayList<>();

    /** Path prefix to response body. First match wins. */
    private final Map<String, String> responses = new HashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());
        publisher = new RecordingPubSubPublisher();
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        inFlightWork = new InFlightWork();
    }

    @AfterEach
    void stopServer() throws IOException {
        scheduler.shutdown();
        client.close();
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        requested.add(path);
        String payload = responses.entrySet().stream()
                .filter(entry -> path.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("{}");
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private ChouetteJobPoller poller(int maxRetries) {
        return poller(maxRetries, publisher, noRetries());
    }

    private ChouetteJobPoller poller(int maxRetries, MardukPubSubPublisher target, RetryPolicy retryPolicy) {
        return new ChouetteJobPoller(client, target, new JobEventPublisher(publisher),
                List.of(recordingHandler(DESTINATION)), scheduler, inFlightWork, retryPolicy, maxRetries, 1);
    }

    private ChouetteJobResultHandler recordingHandler(String destination) {
        return new ChouetteJobResultHandler() {
            @Override
            public String destination() {
                return destination;
            }

            @Override
            public void handle(MardukMessage message) {
                dispatched.add(destination);
                dispatchedMessages.add(message.copy());
            }
        };
    }

    private MardukMessage pollRequest() {
        return new MardukMessage()
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CHOUETTE_JOB_ID, "1")
                .setHeader(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, DESTINATION)
                .setHeader(CHOUETTE_JOB_STATUS_URL, "/chouette_iev/referentials/rut/scheduled_jobs/1")
                .setHeader(CHOUETTE_JOB_STATUS_JOB_TYPE, JobEvent.TimetableAction.IMPORT.name());
    }

    private void status(String state) {
        responses.put("scheduled_jobs", """
                {"id":1,"status":"%s","links":[
                  {"rel":"action_report","href":"/chouette_iev/referentials/rut/data/1/action_report.json"},
                  {"rel":"validation_report","href":"/chouette_iev/referentials/rut/data/1/validation_report.json"},
                  {"rel":"data","href":"/chouette_iev/referentials/rut/data/1/exported.zip"}]}
                """.formatted(state));
    }

    private void actionReport(String result) {
        responses.put("action_report", """
                {"action_report":{"result":"%s","progression":{"steps":[{"step":"FINALISATION","total":1,"realized":1}]}}}
                """.formatted(result));
    }

    private void validationReport(String verdict) {
        responses.put("validation_report", "NOK".equals(verdict)
                ? "{\"validation_report\":{\"check_points\":[{\"severity\":\"ERROR\",\"result\":\"NOK\"}]}}"
                : "{\"validation_report\":{\"check_points\":[{\"severity\":\"WARNING\",\"result\":\"NOK\"}]}}");
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(p -> JobEvent.fromString(p.body()))
                .toList();
    }

    @Test
    void aDestinationWithNoHandlerIsRejectedRatherThanDropped() {
        // The destination is on the wire, so the only way to see an unknown one is a message written by a
        // version that knows a handler this one does not. Nacking lets a pod that knows it take the message.
        status("TERMINATED");
        actionReport("OK");
        validationReport("OK");

        MardukMessage message = pollRequest()
                .setHeader(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, "direct:processSomethingUnknown");

        assertThrows(IllegalArgumentException.class, () -> poller(3000).handle(message));
        assertTrue(dispatched.isEmpty());
    }

    // ----------------------------------------------------------------------------------- the happy path

    @Test
    void aTerminatedJobHandsBothReportResultsToTheDestination() {
        status("TERMINATED");
        actionReport("OK");
        validationReport("OK");

        poller(3000).handle(pollRequest());

        assertEquals(List.of(DESTINATION), dispatched);
        MardukMessage result = dispatchedMessages.getFirst();
        assertEquals("OK", result.getHeader("action_report_result", String.class));
        assertEquals("OK", result.getHeader("validation_report_result", String.class));
        assertEquals("/chouette_iev/referentials/rut/data/1/exported.zip",
                result.getHeader("data_url", String.class));
    }

    @Test
    void aFailedValidationReportReachesTheDestinationAsNok() {
        status("TERMINATED");
        actionReport("OK");
        validationReport("NOK");

        poller(3000).handle(pollRequest());

        assertEquals("NOK", dispatchedMessages.getFirst().getHeader("validation_report_result", String.class));
    }

    @Test
    void aJobWithoutAValidationReportIsMarkedAsSuch() {
        responses.put("scheduled_jobs", """
                {"id":1,"status":"TERMINATED","links":[
                  {"rel":"action_report","href":"/chouette_iev/referentials/rut/data/1/action_report.json"}]}
                """);
        actionReport("OK");

        poller(3000).handle(pollRequest());

        assertEquals("NOT_PRESENT",
                dispatchedMessages.getFirst().getHeader("validation_report_result", String.class));
        assertNull(dispatchedMessages.getFirst().getHeader("data_url"));
    }

    @Test
    void aNoDataFailureBecomesAnErrorCodeTheOperatorCanRead() {
        status("TERMINATED");
        responses.put("action_report", """
                {"action_report":{"result":"NOK","failure":{"code":"NO_DATA_FOUND"},
                  "progression":{"steps":[{"step":"FINALISATION","total":1,"realized":1}]}}}
                """);
        validationReport("OK");

        poller(3000).handle(pollRequest());

        assertEquals(JobEvent.JOB_ERROR_VALIDATION_NO_DATA,
                dispatchedMessages.getFirst().getHeader(Constants.JOB_ERROR_CODE, String.class));
    }

    // ------------------------------------------------------------------------------------- rescheduling

    @Test
    void anUnfinishedJobIsPutBackOnTheQueue() throws Exception {
        status("STARTED");

        poller(3000).handle(pollRequest());

        assertTrue(dispatched.isEmpty(), "an unfinished job must not be reported as done");
        assertEquals("1", awaitRequeue().getHeader("loopCounter", String.class));
    }

    @Test
    void thePollCounterSurvivesTheRoundTrip() throws Exception {
        // Without it the retry cap never bites and a stuck job is polled for ever.
        status("STARTED");

        poller(3000).handle(pollRequest().setHeader("loopCounter", 7));

        assertEquals("8", awaitRequeue().getHeader("loopCounter", String.class));
    }

    @Test
    void theFirstPollOfAStartedJobReportsItAsStarted() throws Exception {
        status("STARTED");

        poller(3000).handle(pollRequest());
        awaitRequeue();

        JobEvent reported = reportedEvents().getFirst();
        assertEquals("IMPORT", reported.getAction());
        assertEquals(JobEvent.State.STARTED, reported.getState());
        assertEquals("1", reported.getExternalId());
    }

    @Test
    void aLaterPollOfTheSameJobReportsNothing() throws Exception {
        status("STARTED");

        poller(3000).handle(pollRequest().setHeader("loopCounter", 7));
        awaitRequeue();

        assertTrue(reportedEvents().isEmpty(), "a status event per poll would flood nabu");
    }

    @Test
    void aJobStillRunningAtTheRetryCapIsReportedAsTimedOut() {
        status("STARTED");

        poller(2).handle(pollRequest().setHeader("loopCounter", 2));

        assertEquals(JobEvent.State.TIMEOUT, reportedEvents().getFirst().getState());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).isEmpty());
    }

    @Test
    void aNonFinalisedActionReportIsPolledAgain() throws Exception {
        status("TERMINATED");
        responses.put("action_report", "{\"action_report\":{\"result\":\"OK\",\"progression\":{\"steps\":[{\"step\":\"FINALISATION\",\"total\":2,\"realized\":1}]}}}");

        poller(3000).handle(pollRequest());

        assertTrue(dispatched.isEmpty());
        awaitRequeue();
    }

    @Test
    void aNonFinalisedActionReportAtTheRetryCapFails() {
        status("TERMINATED");
        responses.put("action_report", "{\"action_report\":{\"result\":\"OK\",\"progression\":{\"steps\":[{\"step\":\"FINALISATION\",\"total\":2,\"realized\":1}]}}}");

        poller(2).handle(pollRequest().setHeader("loopCounter", 5));

        assertEquals(JobEvent.State.FAILED, reportedEvents().getFirst().getState());
        assertTrue(dispatched.isEmpty());
    }

    @Test
    void aRefusedScheduleStillPutsThePollBackOnTheQueue() throws Exception {
        // The task is refused from the moment shutdown begins. Losing it would leave the Chouette job
        // running with nothing following it, and no terminal status would ever be reported.
        status("STARTED");
        scheduler.shutdown();

        poller(3000).handle(pollRequest());

        assertEquals("1", awaitRequeue().getHeader("loopCounter", String.class));
        assertEquals(0, inFlightWork.count(), "the shutdown drain will now wait out its whole timeout");
    }

    @Test
    void aRepublishThatKeepsFailingReleasesTheWorkItTracked() throws Exception {
        // The exception a scheduled task throws lands in a Future nobody reads, so the counter is the only
        // thing that would show it - and a counter that never comes back down blocks every later shutdown.
        status("STARTED");
        FailingPublisher failing = new FailingPublisher();

        poller(3000, failing, retriesWithoutWaiting()).handle(pollRequest());

        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (inFlightWork.count() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(0, inFlightWork.count(), "the republish leaked its in-flight work");
        assertEquals(4, failing.attempts.get(), "the republish was not retried");
    }

    /** Refuses everything, as a publisher whose topic has just been taken away would. */
    private static class FailingPublisher implements MardukPubSubPublisher {
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public void publish(String destination, MardukMessage message) {
            attempts.incrementAndGet();
            throw new IllegalStateException("no such topic");
        }
    }

    // ------------------------------------------------------------------------------------ failed states

    @Test
    void anAbortedJobIsReportedAsFailed() {
        status("ABORTED");

        poller(3000).handle(pollRequest());

        assertEquals(JobEvent.State.FAILED, reportedEvents().getFirst().getState());
        assertTrue(dispatched.isEmpty(), "a failed job must not look like a result");
    }

    @Test
    void aCancelledJobIsReportedAsCancelled() {
        status("CANCELED");

        poller(3000).handle(pollRequest());

        assertEquals(JobEvent.State.CANCELLED, reportedEvents().getFirst().getState());
        assertTrue(dispatched.isEmpty());
    }

    @Test
    void anEmptyActionReportFailsTheJobRatherThanTheMessage() {
        // A terminated job whose report will not parse cannot be retried into existence.
        status("TERMINATED");
        responses.put("action_report", "");

        poller(3000).handle(pollRequest());

        assertEquals(JobEvent.State.FAILED, reportedEvents().getFirst().getState());
        assertTrue(dispatched.isEmpty());
    }

    @Test
    void aJobWithoutAnActionReportUrlIsAnError() {
        responses.put("scheduled_jobs", "{\"id\":1,\"status\":\"TERMINATED\",\"links\":[]}");

        assertThrows(IllegalArgumentException.class, () -> poller(3000).handle(pollRequest()));
    }

    // -------------------------------------------------------------------------------- request validation

    @Test
    void aPollRequestMissingAnythingItNeedsIsRejected() {
        for (String header : List.of(CORRELATION_ID, PROVIDER_ID, CHOUETTE_JOB_STATUS_ROUTING_DESTINATION,
                CHOUETTE_JOB_STATUS_URL, CHOUETTE_JOB_STATUS_JOB_TYPE)) {
            MardukMessage incomplete = pollRequest().removeHeader(header);
            assertThrows(IllegalArgumentException.class, () -> poller(3000).handle(incomplete),
                    "a request without " + header + " was accepted");
            assertTrue(requested.isEmpty(), "an incomplete request must not reach Chouette");
        }
    }

    @Test
    void theSubscriptionIsThePollStatusQueue() {
        assertEquals(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE, poller(3000).destination());
    }

    /** The republish is scheduled, so give the scheduler a moment. */
    private MardukMessage awaitRequeue() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            List<RecordingPubSubPublisher.Published> queued =
                    publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE);
            if (!queued.isEmpty()) {
                MardukMessage requeued = new MardukMessage(new HashMap<>(queued.getFirst().attributes()),
                        queued.getFirst().body());
                return requeued;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("nothing was put back on " + MardukQueues.CHOUETTE_POLL_STATUS_QUEUE);
    }
}
