package no.rutebanken.marduk.routes.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
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
}
