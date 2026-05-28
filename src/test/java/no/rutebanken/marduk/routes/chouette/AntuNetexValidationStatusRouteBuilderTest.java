package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.routes.chouette.AntuNetexValidationStatusRouteBuilder.STATUS_VALIDATION_FAILED;
import static no.rutebanken.marduk.routes.chouette.AntuNetexValidationStatusRouteBuilder.STATUS_VALIDATION_OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {"marduk.experimental-import.enabled=true"})
class AntuNetexValidationStatusRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:AntuNetexValidationStatusQueue")
    protected ProducerTemplate importTemplate;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @EndpointInject("mock:copyInternalBlobToAnotherBucket")
    protected MockEndpoint copyInternalBlobToAnotherBucket;

    @EndpointInject("mock:copyExternalBlobInBucket")
    protected MockEndpoint copyExternalBlobInBucket;

    @EndpointInject("mock:copyBlobInBucket")
    protected MockEndpoint copyBlobInBucketMock;

    @EndpointInject("mock:chouetteMergeWithFlexibleLinesQueue")
    protected MockEndpoint chouetteMergeWithFlexibleLinesQueueMock;

    @EndpointInject("mock:publishMergedNetexQueue")
    protected MockEndpoint publishMergedNetexQueueMock;

    @EndpointInject("mock:antuNetexValidationQueueMerged")
    protected MockEndpoint antuNetexValidationQueueMergedMock;

    @EndpointInject("mock:otp2GraphBuildQueue")
    protected MockEndpoint otp2GraphBuildQueueMock;

    @EndpointInject("mock:netexExportNotificationQueue")
    protected MockEndpoint netexExportNotificationQueueMock;

    @EndpointInject("mock:gtfsRouteDispatcherTopic")
    protected MockEndpoint gtfsRouteDispatcherTopicMock;

    @BeforeEach
    protected void setUp() throws IOException {
        super.setUp();
        updateStatus.reset();
        copyInternalBlobToAnotherBucket.reset();
        copyExternalBlobInBucket.reset();
        chouetteMergeWithFlexibleLinesQueueMock.reset();
    }

    @Test
    void testAntuStatusValidationOk() throws Exception {

        AdviceWith.adviceWith(context, "antu-netex-validation-complete", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.interceptSendToEndpoint("direct:copyExternalBlobInBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyExternalBlobInBucket");

            a.weaveByToUri("google-pubsub:(.*):ChouetteMergeWithFlexibleLinesQueue")
                    .replace()
                    .to("mock:chouetteMergeWithFlexibleLinesQueue");
        });


        // we must manually start when we are done with all the advice with
        context.start();

        updateStatus.expectedMessageCount(1);
        copyExternalBlobInBucket.expectedMessageCount(1);
        chouetteMergeWithFlexibleLinesQueueMock.expectedMessageCount(1);


        Map<String, String> headers = new HashMap<>();
        headers.put(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_FLEX_POSTVALIDATION);
        headers.put(VALIDATION_DATASET_FILE_HANDLE_HEADER, "testFileName");
        headers.put(VALIDATION_CORRELATION_ID_HEADER, "testCorrelationId");
        sendBodyAndHeadersToPubSub(importTemplate, STATUS_VALIDATION_OK, headers);

        updateStatus.assertIsSatisfied();
        copyExternalBlobInBucket.assertIsSatisfied();
        chouetteMergeWithFlexibleLinesQueueMock.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain())
                && JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name().equals(je.getAction())
                && JobEvent.State.OK.equals(je.getState())));
    }

    @Test
    void testAntuStatusValidationFailedShouldStopMergeWithFlexibleLine() throws Exception {

        AdviceWith.adviceWith(context, "antu-netex-validation-failed", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.interceptSendToEndpoint("direct:copyBlobToAnotherBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyBlobToAnotherBucket");
            a.interceptSendToEndpoint("google-pubsub:(.*):ChouetteMergeWithFlexibleLinesQueue")
                    .skipSendToOriginalEndpoint()
                    .to("mock:chouetteMergeWithFlexibleLinesQueue");
        });

        // we must manually start when we are done with all the advice with
        context.start();

        updateStatus.expectedMessageCount(1);
        copyInternalBlobToAnotherBucket.expectedMessageCount(0);
        chouetteMergeWithFlexibleLinesQueueMock.expectedMessageCount(0);

        Map<String, String> headers = new HashMap<>();
        headers.put(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_FLEX_POSTVALIDATION);
        headers.put(VALIDATION_DATASET_FILE_HANDLE_HEADER, "testFileName");
        headers.put(VALIDATION_CORRELATION_ID_HEADER, "testCorrelationId");
        sendBodyAndHeadersToPubSub(importTemplate, STATUS_VALIDATION_FAILED, headers);

        updateStatus.assertIsSatisfied();
        copyInternalBlobToAnotherBucket.assertIsSatisfied();
        chouetteMergeWithFlexibleLinesQueueMock.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain())
                && JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name().equals(je.getAction())
                && JobEvent.State.FAILED.equals(je.getState())));
    }

    @Test
    void testAntuFailedValidationShouldStopPublishMergedNetexQueue() throws Exception {

        // Mock update status calls
        AdviceWith.adviceWith(context, "antu-netex-validation-failed", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.interceptSendToEndpoint("direct:copyBlobInBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyBlobInBucket");
            a.interceptSendToEndpoint("google-pubsub:(.*):PublishMergedNetexQueue")
                    .skipSendToOriginalEndpoint()
                    .to("mock:publishMergedNetexQueue");
        });

        // we must manually start when we are done with all the advice with
        context.start();

        updateStatus.expectedMessageCount(1);
        copyBlobInBucketMock.expectedMessageCount(0);
        publishMergedNetexQueueMock.expectedMessageCount(0);

        Map<String, String> headers = new HashMap<>();
        headers.put(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION);
        headers.put(VALIDATION_DATASET_FILE_HANDLE_HEADER, "testFileName");
        headers.put(VALIDATION_CORRELATION_ID_HEADER, "testCorrelationId");
        sendBodyAndHeadersToPubSub(importTemplate, STATUS_VALIDATION_FAILED, headers);

        updateStatus.assertIsSatisfied();
        copyBlobInBucketMock.assertIsSatisfied();
        publishMergedNetexQueueMock.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain())
                && JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION.name().equals(je.getAction())
                && JobEvent.State.FAILED.equals(je.getState())));
    }

    @Test
    void testPublicationSucceedsWithPublishNetexOkEvent() throws Exception {
        // Mock the antu-netex-validation-complete route for EXPORT_MERGED_POSTVALIDATION
        AdviceWith.adviceWith(context, "antu-netex-validation-complete", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.interceptSendToEndpoint("direct:copyInternalBlobToAnotherBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyInternalBlobToAnotherBucket");
            a.weaveByToUri("google-pubsub:(.*):PublishMergedNetexQueue")
                    .replace()
                    .to("mock:publishMergedNetexQueue");
        });

        context.start();

        // Successful publication: 1 status update (validation OK, sent after the choice block)
        // Copy and publish should happen
        updateStatus.expectedMessageCount(1);
        copyInternalBlobToAnotherBucket.expectedMessageCount(1);
        publishMergedNetexQueueMock.expectedMessageCount(1);

        Map<String, String> headers = new HashMap<>();
        headers.put(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION);
        headers.put(VALIDATION_DATASET_FILE_HANDLE_HEADER, "/some-path/regular-netex/aggregated.zip");
        headers.put(VALIDATION_CORRELATION_ID_HEADER, "testCorrelationId");
        sendBodyAndHeadersToPubSub(importTemplate, STATUS_VALIDATION_OK, headers);

        updateStatus.assertIsSatisfied();
        copyInternalBlobToAnotherBucket.assertIsSatisfied();
        publishMergedNetexQueueMock.assertIsSatisfied();

        // Verify validation OK status
        List<JobEvent> events = updateStatus.getExchanges().stream()
            .map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();

        assertTrue(events.stream().anyMatch(je ->
            JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION.name().equals(je.getAction())
            && JobEvent.State.OK.equals(je.getState())));
    }

    /**
     * Regression guard: when a FLEX post-validation OK arrives for an experimental codespace and the latest
     * Ashur ordinary NeTEx exists at the stable per-referential path (but NOT at the FLEX correlation-keyed
     * path, since the flex import has a different correlation id than the most recent ordinary import),
     * the merge route must still produce both a chouette- and a flex-source and route through the merged
     * post-validation step instead of publishing straight to the OTP2 graph build queue.
     */
    @Test
    void testFlexPostValidationForExperimentalCodespaceTriggersMergedValidationViaLatestAshurFallback() throws Exception {
        // Override default provider mocks so rb_rut is an experimental codespace.
        Provider experimentalRutProvider = new Provider();
        experimentalRutProvider.setId(TestConstants.PROVIDER_ID_RUT);
        ChouetteInfo rutInfo = new ChouetteInfo();
        rutInfo.setId(TestConstants.PROVIDER_ID_RUT);
        rutInfo.setReferential(TestConstants.CHOUETTE_REFERENTIAL_RUT);
        rutInfo.setEnableExperimentalImport(true);
        experimentalRutProvider.setChouetteInfo(rutInfo);

        Provider experimentalRbRutProvider = new Provider();
        experimentalRbRutProvider.setId(TestConstants.PROVIDER_ID_RB_RUT);
        ChouetteInfo rbRutInfo = new ChouetteInfo();
        rbRutInfo.setId(TestConstants.PROVIDER_ID_RB_RUT);
        rbRutInfo.setReferential(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT);
        rbRutInfo.setEnableExperimentalImport(true);
        experimentalRbRutProvider.setChouetteInfo(rbRutInfo);

        when(providerRepository.getProvider(TestConstants.PROVIDER_ID_RUT)).thenReturn(experimentalRutProvider);
        when(providerRepository.getProvider(TestConstants.PROVIDER_ID_RB_RUT)).thenReturn(experimentalRbRutProvider);
        when(providerRepository.getProviders()).thenReturn(List.of(experimentalRutProvider, experimentalRbRutProvider));
        when(providerRepository.getProviderId(TestConstants.CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);
        when(providerRepository.getProviderId(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT)).thenReturn(TestConstants.PROVIDER_ID_RB_RUT);

        // Simulated state from a previous successful experimental ordinary import:
        // the latest Ashur StandardImportFilter output is at the stable per-referential path.
        // No file is placed at the FLEX correlation-keyed path - that's the whole point of the regression.
        String stableLatestAshurPath = "filtered-netex/rb_rut/latest-without-blocks/rb_rut-aggregated-netex.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(
                stableLatestAshurPath,
                new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip"));

        // Pre-populate the flex file at the destination path the merge route reads from.
        // The FLEX post-validation handler also copies it there, but we mock that copy to keep the test focused.
        String flexOutboundPath = BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-" + CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;
        exchangeInMemoryBlobStoreRepository.uploadBlob(
                flexOutboundPath,
                new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex_with_two_files.zip"));

        AdviceWith.adviceWith(context, "antu-netex-validation-complete", a -> {
            a.interceptSendToEndpoint("direct:copyExternalBlobInBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyExternalBlobInBucket");
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
        });

        AdviceWith.adviceWith(context, "antu-merged-netex-post-validation", a -> {
            a.interceptSendToEndpoint("direct:copyInternalBlobToValidationBucket")
                    .skipSendToOriginalEndpoint()
                    .to("mock:copyInternalBlobToValidationBucket");
            a.weaveByToUri("google-pubsub:(.*):AntuNetexValidationQueue")
                    .replace()
                    .to("mock:antuNetexValidationQueueMerged");
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
        });

        AdviceWith.adviceWith(context, "publish-merged-dataset", a -> {
            a.weaveByToUri("google-pubsub:(.*):Otp2GraphBuildQueue")
                    .replace()
                    .to("mock:otp2GraphBuildQueue");
        });

        AdviceWith.adviceWith(context, "netex-notify-export", a -> a
                .weaveByToUri("google-pubsub:(.*):NetexExportNotificationQueue")
                .replace()
                .to("mock:netexExportNotificationQueue"));

        AdviceWith.adviceWith(context, "start-damu-gtfs-export", a -> a
                .weaveByToUri("google-pubsub:(.*):GtfsRouteDispatcherTopic")
                .replace()
                .to("mock:gtfsRouteDispatcherTopic"));

        context.start();

        antuNetexValidationQueueMergedMock.expectedMessageCount(1);
        antuNetexValidationQueueMergedMock.setResultWaitTime(30000);
        otp2GraphBuildQueueMock.expectedMessageCount(0);
        otp2GraphBuildQueueMock.setResultWaitTime(5000);

        Map<String, String> headers = new HashMap<>();
        headers.put(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_FLEX_POSTVALIDATION);
        headers.put(VALIDATION_DATASET_FILE_HANDLE_HEADER, "any/source/file.zip");
        headers.put(VALIDATION_CORRELATION_ID_HEADER, "flex-correlation-id-distinct-from-ordinary");
        headers.put(DATASET_REFERENTIAL, TestConstants.CHOUETTE_REFERENTIAL_RB_RUT);
        sendBodyAndHeadersToPubSub(importTemplate, STATUS_VALIDATION_OK, headers);

        antuNetexValidationQueueMergedMock.assertIsSatisfied();
        otp2GraphBuildQueueMock.assertIsSatisfied();

        Exchange captured = antuNetexValidationQueueMergedMock.getReceivedExchanges().getFirst();
        assertEquals(VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION,
                captured.getIn().getHeader(VALIDATION_STAGE_HEADER));
    }
}