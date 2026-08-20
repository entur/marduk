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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AshurFilteringStatusRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {
    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:" + Constants.FILTER_NETEX_FILE_STATUS_TOPIC)
    protected ProducerTemplate importTemplate;

    @EndpointInject("mock:antuNetexValidationQueue")
    protected MockEndpoint antuNetexValidationQueueEndpoint;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatusEndpoint;

    @EndpointInject("mock:copyInternalBlobToValidationBucket")
    protected MockEndpoint copyInternalBlobToValidationBucketEndpoint;

    @EndpointInject("mock:copyBlobFromAnotherBucketToInternalBucket")
    protected  MockEndpoint copyBlobFromAnotherBucketToInternalEndpoint;

    @BeforeEach
    @Override
    protected void setUp() throws IOException {
        super.setUp();
        antuNetexValidationQueueEndpoint.reset();
    }

    void interceptRoutes() throws Exception {
        AdviceWith.adviceWith(context, "antu-post-validation-preparation-route", a -> {
            a.interceptSendToEndpoint("direct:copyInternalBlobToValidationBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyInternalBlobToValidationBucket");
        });

        AdviceWith.adviceWith(context, "copy-filtered-dataset-to-internal-bucket-route", a -> {
            a.interceptSendToEndpoint("direct:copyBlobFromAnotherBucketToInternalBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyBlobFromAnotherBucketToInternalBucket");
        });

        AdviceWith.adviceWith(context, "copy-filtered-dataset-to-validation-bucket-route", a -> {
            a.interceptSendToEndpoint("direct:copyInternalBlobToValidationBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyInternalBlobToValidationBucket");
        });

        AdviceWith.adviceWith(context, "ashur-filtering-status-standard-import-route", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
        });

        AdviceWith.adviceWith(context, "trigger-antu-post-validation-after-filtering-route", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):AntuNetexValidationQueue")
                    .replace()
                    .to("mock:antuNetexValidationQueue");
        });
    }

    @Test
    void testValidationTriggersAfterSuccessfulFiltering() throws Exception {
        interceptRoutes();

        context.start();

        updateStatusEndpoint.expectedMessageCount(2);
        copyInternalBlobToValidationBucketEndpoint.expectedMessageCount(1);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(1);
        antuNetexValidationQueueEndpoint.expectedMessageCount(1);

        sendBodyAndHeadersToPubSub(importTemplate, standardImportReport(), Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "TST/someCorrelationId/filtered_netex.zip",
            Constants.FILTERING_PROFILE_HEADER, Constants.FILTERING_PROFILE_STANDARD_IMPORT,
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyInternalBlobToValidationBucketEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();

        // Verify we received both expected status updates
        var receivedMessages = updateStatusEndpoint.getReceivedExchanges().stream()
            .map(exchange -> exchange.getIn().getBody(String.class))
            .toList();

        boolean foundFilteringOkStatus = receivedMessages.stream()
            .anyMatch(body -> body.contains("\"action\":\"FILTERING\"") && body.contains("\"state\":\"OK\""));
        boolean foundValidationPendingStatus = receivedMessages.stream()
            .anyMatch(body -> body.contains("\"action\":\"EXPORT_NETEX_POSTVALIDATION\"") && body.contains("\"state\":\"PENDING\""));

        assert foundFilteringOkStatus : "Expected status update with action=FILTERING and state=OK";
        assert foundValidationPendingStatus : "Expected status update with action=EXPORT_NETEX_POSTVALIDATION and state=PENDING";
    }

    @Test
    void verifyThatValidationIsNotRunWhenFilteringFails() throws Exception {
        interceptRoutes();

        context.start();

        // Filtering failure should emit 1 FAILED status update but no copy or validation
        updateStatusEndpoint.expectedMessageCount(1);
        copyInternalBlobToValidationBucketEndpoint.expectedMessageCount(0);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(0);
        antuNetexValidationQueueEndpoint.expectedMessageCount(0);

        sendBodyAndHeadersToPubSub(importTemplate, null, Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_FAILED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "TST/someCorrelationId/filtered_netex.zip",
            Constants.FILTERING_PROFILE_HEADER, Constants.FILTERING_PROFILE_STANDARD_IMPORT,
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyInternalBlobToValidationBucketEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();

        // Verify the FAILED status event
        List<JobEvent> events = updateStatusEndpoint.getExchanges().stream()
            .map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je ->
            JobEvent.TimetableAction.FILTERING.name().equals(je.getAction())
            && JobEvent.State.FAILED.equals(je.getState())));
    }

    @Test
    void testCheckpoint2_StandardImportFailsIfReportProfileMismatch() throws Exception {
        interceptRoutes();

        context.start();

        // Security violation should trigger a FAILED status update and stop processing
        updateStatusEndpoint.expectedMessageCount(1);
        copyInternalBlobToValidationBucketEndpoint.expectedMessageCount(0);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(0);
        antuNetexValidationQueueEndpoint.expectedMessageCount(0);

        // Send a standard import success response with a report containing wrong filterProfile
        sendBodyAndHeadersToPubSub(importTemplate, reportWithProfile(Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS), Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "TST/someCorrelationId/filtered_netex.zip",
            Constants.FILTERING_PROFILE_HEADER, Constants.FILTERING_PROFILE_STANDARD_IMPORT,
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyInternalBlobToValidationBucketEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();

        // Verify the failure event has the correct error code
        List<JobEvent> events = updateStatusEndpoint.getExchanges().stream()
            .map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je ->
            JobEvent.TimetableAction.FILTERING.name().equals(je.getAction())
            && JobEvent.State.FAILED.equals(je.getState())
            && Constants.SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH.equals(je.getErrorCode())));
    }

    @Test
    void testCheckpoint2_StandardImportFailsIfReportContainsBlocks() throws Exception {
        interceptRoutes();

        context.start();

        updateStatusEndpoint.expectedMessageCount(1);
        copyInternalBlobToValidationBucketEndpoint.expectedMessageCount(0);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(0);
        antuNetexValidationQueueEndpoint.expectedMessageCount(0);

        // Send a standard import success response with a report containing blocks
        sendBodyAndHeadersToPubSub(importTemplate, standardImportReportWithBlocks(), Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "TST/someCorrelationId/filtered_netex.zip",
            Constants.FILTERING_PROFILE_HEADER, Constants.FILTERING_PROFILE_STANDARD_IMPORT,
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyInternalBlobToValidationBucketEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();

        List<JobEvent> events = updateStatusEndpoint.getExchanges().stream()
            .map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je ->
            JobEvent.TimetableAction.FILTERING.name().equals(je.getAction())
            && JobEvent.State.FAILED.equals(je.getState())
            && Constants.SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH.equals(je.getErrorCode())));
    }

    @Test
    void testCheckpoint2_BlocksExportFailsIfReportProfileMismatch() throws Exception {
        interceptBlocksRoutes();

        context.start();

        // Security violation should trigger a FAILED status update and stop processing
        updateStatusEndpoint.expectedMessageCount(1);
        antuNetexValidationQueueEndpoint.expectedMessageCount(0);

        // Send a blocks export success response with a report containing wrong filterProfile
        sendBodyAndHeadersToPubSub(importTemplate, reportWithProfile(Constants.FILTERING_PROFILE_STANDARD_IMPORT), Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "TST/someCorrelationId/filtered_netex.zip",
            Constants.FILTERING_PROFILE_HEADER, Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS,
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.CHOUETTE_REFERENTIAL, "rb_tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();

        // Verify the failure event has the correct error code
        List<JobEvent> events = updateStatusEndpoint.getExchanges().stream()
            .map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je ->
            JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS.name().equals(je.getAction())
            && JobEvent.State.FAILED.equals(je.getState())
            && Constants.SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH.equals(je.getErrorCode())));
    }

    @Test
    void testCheckpoint2_BlocksExportSucceedsWithCorrectReport() throws Exception {
        interceptBlocksRoutes();

        context.start();

        // Valid blocks export should succeed with OK status
        updateStatusEndpoint.expectedMessageCount(2);  // OK for EXPORT_NETEX_BLOCKS and PENDING for post-validation
        antuNetexValidationQueueEndpoint.expectedMessageCount(1);

        // Send a blocks export success response with correct report
        sendBodyAndHeadersToPubSub(importTemplate, blocksExportReport(), Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "TST/someCorrelationId/filtered_netex.zip",
            Constants.FILTERING_PROFILE_HEADER, Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS,
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.CHOUETTE_REFERENTIAL, "rb_tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();

        // Verify success event
        List<JobEvent> events = updateStatusEndpoint.getExchanges().stream()
            .map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je ->
            JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS.name().equals(je.getAction())
            && JobEvent.State.OK.equals(je.getState())));
    }

    void interceptBlocksRoutes() throws Exception {
        AdviceWith.adviceWith(context, "ashur-filtering-status-block-export-route", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
        });

        AdviceWith.adviceWith(context, "post-validate-filtered-dataset-with-blocks-route", a -> {
            a.interceptSendToEndpoint("direct:copyBlobFromAnotherBucketToInternalBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyBlobFromAnotherBucketToInternalBucket");
            a.interceptSendToEndpoint("direct:copyInternalBlobToValidationBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyInternalBlobToValidationBucket");
        });

        AdviceWith.adviceWith(context, "trigger-antu-post-blocks-validation-after-filtering-route", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):AntuNetexValidationQueue")
                    .replace()
                    .to("mock:antuNetexValidationQueue");
        });
    }

    private static String standardImportReport() {
        return """
            {
              "created": "2026-03-27T12:00:00.000000",
              "correlationId": "someCorrelationId",
              "codespace": "TST",
              "filterProfile": "StandardImportFilter",
              "status": "SUCCESS",
              "reason": null,
              "entityTypeCounts": {
                "ServiceJourney": 142,
                "Line": 3,
                "Block": 0
              }
            }
            """;
    }

    private static String standardImportReportWithBlocks() {
        return """
            {
              "created": "2026-03-27T12:00:00.000000",
              "correlationId": "someCorrelationId",
              "codespace": "TST",
              "filterProfile": "StandardImportFilter",
              "status": "SUCCESS",
              "reason": null,
              "entityTypeCounts": {
                "ServiceJourney": 142,
                "Line": 3,
                "Block": 50
              }
            }
            """;
    }

    private static String blocksExportReport() {
        return """
            {
              "created": "2026-03-27T12:00:00.000000",
              "correlationId": "someCorrelationId",
              "codespace": "TST",
              "filterProfile": "IncludeBlocksAndRestrictedJourneysFilter",
              "status": "SUCCESS",
              "reason": null,
              "entityTypeCounts": {
                "ServiceJourney": 142,
                "Block": 50
              }
            }
            """;
    }

    private static String reportWithProfile(String filterProfile) {
        return """
            {
              "created": "2026-03-27T12:00:00.000000",
              "correlationId": "someCorrelationId",
              "codespace": "TST",
              "filterProfile": "%s",
              "status": "SUCCESS",
              "reason": null,
              "entityTypeCounts": {
                "ServiceJourney": 142,
                "Line": 3
              }
            }
            """.formatted(filterProfile);
    }
}
