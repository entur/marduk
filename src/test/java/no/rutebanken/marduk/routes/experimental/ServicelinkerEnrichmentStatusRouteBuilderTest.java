package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestPropertySource(properties = {"servicelinker.linkingEnabled=true"})
class ServicelinkerEnrichmentStatusRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("google-pubsub:{{servicelinker.pubsub.project.id}}:" + Constants.SERVICELINKER_STATUS_TOPIC)
    protected ProducerTemplate statusTemplate;

    @EndpointInject("mock:ashurNetexFilterAfterPreValidation")
    protected MockEndpoint ashurFilterEndpoint;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatusEndpoint;

    @EndpointInject("mock:copyBlobFromAnotherBucketToInternalBucket")
    protected MockEndpoint copyBlobFromAnotherBucketToInternalEndpoint;

    @BeforeEach
    @Override
    protected void setUp() throws IOException {
        super.setUp();
        ashurFilterEndpoint.reset();
    }

    void interceptRoutes() throws Exception {
        AdviceWith.adviceWith(context, "servicelinker-enrichment-status-route", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.interceptSendToEndpoint("direct:ashurNetexFilterAfterPreValidation")
                    .skipSendToOriginalEndpoint()
                    .to("mock:ashurNetexFilterAfterPreValidation");
        });

        AdviceWith.adviceWith(context, "copy-enriched-dataset-to-internal-bucket-route", a -> {
            a.interceptSendToEndpoint("direct:copyBlobFromAnotherBucketToInternalBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyBlobFromAnotherBucketToInternalBucket");
        });
    }

    @Test
    void testAshurTriggeredAfterSuccessfulLinking() throws Exception {
        interceptRoutes();

        context.start();

        updateStatusEndpoint.expectedMessageCount(1);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(1);
        ashurFilterEndpoint.expectedMessageCount(1);

        sendBodyAndHeadersToPubSub(statusTemplate, null, Map.of(
            Constants.LINKING_NETEX_FILE_STATUS_HEADER, Constants.LINKING_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.LINKED_NETEX_FILE_PATH_HEADER, "servicelinker/tst/some-uuid/tst-aggregated-netex.zip",
            Constants.FILE_HANDLE, "chouette/netex-before-validation/tst-export.zip",
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        ashurFilterEndpoint.assertIsSatisfied();

        var receivedMessages = updateStatusEndpoint.getReceivedExchanges().stream()
            .map(exchange -> exchange.getIn().getBody(String.class))
            .toList();

        assertTrue(
            receivedMessages.stream().anyMatch(body -> body.contains("\"action\":\"LINKING\"") && body.contains("\"state\":\"OK\"")),
            "Expected status update with action=LINKING and state=OK"
        );

        // The enriched file must be written to the dedicated servicelinker path, not the original FILE_HANDLE
        var copyExchange = copyBlobFromAnotherBucketToInternalEndpoint.getReceivedExchanges().getFirst();
        String enrichedPath = "servicelinker/tst/some-uuid/tst-aggregated-netex.zip";
        assertEquals(enrichedPath, copyExchange.getIn().getHeader(Constants.TARGET_FILE_HANDLE, String.class),
            "Expected TARGET_FILE_HANDLE to be enriched path, not original");

        // Ashur must receive the enriched path as FILE_HANDLE, not the original
        var ashurExchange = ashurFilterEndpoint.getReceivedExchanges().getFirst();
        assertEquals(enrichedPath, ashurExchange.getIn().getHeader(Constants.FILE_HANDLE, String.class),
            "Expected FILE_HANDLE forwarded to Ashur to be enriched path, not original");
    }

    @Test
    void testAshurTriggeredAfterFailedLinking() throws Exception {
        interceptRoutes();

        context.start();

        updateStatusEndpoint.expectedMessageCount(1);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(0);
        ashurFilterEndpoint.expectedMessageCount(1);

        sendBodyAndHeadersToPubSub(statusTemplate, null, Map.of(
            Constants.LINKING_NETEX_FILE_STATUS_HEADER, Constants.LINKING_NETEX_FILE_STATUS_FAILED,
            Constants.LINKED_NETEX_FILE_PATH_HEADER, "",
            Constants.FILE_HANDLE, "chouette/netex-before-validation/tst-export.zip",
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId",
            Constants.LINKING_ERROR_CODE_HEADER, "OSRM timeout"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        ashurFilterEndpoint.assertIsSatisfied();

        var receivedMessages = updateStatusEndpoint.getReceivedExchanges().stream()
            .map(exchange -> exchange.getIn().getBody(String.class))
            .toList();

        assertTrue(
            receivedMessages.stream().anyMatch(body -> body.contains("\"action\":\"LINKING\"") && body.contains("\"state\":\"FAILED\"")),
            "Expected status update with action=LINKING and state=FAILED"
        );
    }

    @Test
    void testStartedStatusStopsAndPropagatesEmitTime() throws Exception {
        interceptRoutes();

        context.start();

        // STARTED branch updates status then stops — it must not copy the file or continue to Ashur
        updateStatusEndpoint.expectedMessageCount(1);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(0);
        ashurFilterEndpoint.expectedMessageCount(0);

        Instant emitTime = Instant.parse("2026-06-03T10:15:30Z");

        sendBodyAndHeadersToPubSub(statusTemplate, null, Map.of(
            Constants.LINKING_NETEX_FILE_STATUS_HEADER, Constants.LINKING_NETEX_FILE_STATUS_STARTED,
            Constants.LINKING_STATUS_EVENT_TIME_HEADER, emitTime.toString(),
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        ashurFilterEndpoint.assertIsSatisfied();

        JobEvent event = relayedJobEvent();
        assertEquals("LINKING", event.getAction());
        assertEquals(JobEvent.State.STARTED, event.getState());
        assertEquals(emitTime, event.getEventTime(), "Servicelinker emit time must be propagated to the JobEvent");
    }

    @Test
    void testEmitTimePropagatedOnSuccess() throws Exception {
        interceptRoutes();

        context.start();

        updateStatusEndpoint.expectedMessageCount(1);

        Instant emitTime = Instant.parse("2026-06-03T10:20:45Z");

        sendBodyAndHeadersToPubSub(statusTemplate, null, Map.of(
            Constants.LINKING_NETEX_FILE_STATUS_HEADER, Constants.LINKING_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.LINKED_NETEX_FILE_PATH_HEADER, "servicelinker/tst/some-uuid/tst-aggregated-netex.zip",
            Constants.LINKING_STATUS_EVENT_TIME_HEADER, emitTime.toString(),
            Constants.FILE_HANDLE, "chouette/netex-before-validation/tst-export.zip",
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();

        JobEvent event = relayedJobEvent();
        assertEquals(JobEvent.State.OK, event.getState());
        assertEquals(emitTime, event.getEventTime(), "Servicelinker emit time must be propagated to the JobEvent");
    }

    @Test
    void testMissingEmitTimeFallsBackToProcessingTime() throws Exception {
        assertFallbackToProcessingTime(null);
    }

    @Test
    void testUnparseableEmitTimeFallsBackToProcessingTime() throws Exception {
        assertFallbackToProcessingTime("not-a-timestamp");
    }

    private void assertFallbackToProcessingTime(String emitTimeHeader) throws Exception {
        interceptRoutes();

        context.start();

        updateStatusEndpoint.expectedMessageCount(1);

        java.util.Map<String, String> headers = new java.util.HashMap<>(Map.of(
            Constants.LINKING_NETEX_FILE_STATUS_HEADER, Constants.LINKING_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.LINKED_NETEX_FILE_PATH_HEADER, "servicelinker/tst/some-uuid/tst-aggregated-netex.zip",
            Constants.FILE_HANDLE, "chouette/netex-before-validation/tst-export.zip",
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));
        if (emitTimeHeader != null) {
            headers.put(Constants.LINKING_STATUS_EVENT_TIME_HEADER, emitTimeHeader);
        }

        Instant before = Instant.now();
        sendBodyAndHeadersToPubSub(statusTemplate, null, headers);
        updateStatusEndpoint.assertIsSatisfied();
        Instant after = Instant.now();

        JobEvent event = relayedJobEvent();
        assertEquals(JobEvent.State.OK, event.getState());
        assertTrue(!event.getEventTime().isBefore(before) && !event.getEventTime().isAfter(after),
            "Expected event time to fall back to processing time (~now), but was " + event.getEventTime());
    }

    private JobEvent relayedJobEvent() {
        String body = updateStatusEndpoint.getReceivedExchanges().getFirst().getIn().getBody(String.class);
        return JobEvent.fromString(body);
    }
}
