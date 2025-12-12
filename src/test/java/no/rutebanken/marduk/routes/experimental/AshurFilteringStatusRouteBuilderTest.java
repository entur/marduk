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

import java.io.IOException;
import java.util.Map;

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

        updateStatusEndpoint.expectedMessageCount(1);
        copyInternalBlobToValidationBucketEndpoint.expectedMessageCount(1);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(1);
        antuNetexValidationQueueEndpoint.expectedMessageCount(1);

        sendBodyAndHeadersToPubSub(importTemplate, null, Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "/some-path/filtered-file.zip",
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyInternalBlobToValidationBucketEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();
    }

    @Test
    void verifyThatValidationIsNotRunWhenFilteringFails() throws Exception {
        interceptRoutes();

        context.start();

        updateStatusEndpoint.expectedMessageCount(0);
        copyInternalBlobToValidationBucketEndpoint.expectedMessageCount(0);
        copyBlobFromAnotherBucketToInternalEndpoint.expectedMessageCount(0);
        antuNetexValidationQueueEndpoint.expectedMessageCount(0);

        sendBodyAndHeadersToPubSub(importTemplate, null, Map.of(
            Constants.FILTER_NETEX_FILE_STATUS_HEADER, Constants.FILTER_NETEX_FILE_STATUS_FAILED,
            Constants.FILTERED_NETEX_FILE_PATH_HEADER, "/some-path/filtered-file.zip",
            Constants.DATASET_REFERENTIAL, "tst",
            Constants.PROVIDER_ID, "0",
            Constants.CORRELATION_ID, "someCorrelationId"
        ));

        updateStatusEndpoint.assertIsSatisfied();
        copyInternalBlobToValidationBucketEndpoint.assertIsSatisfied();
        copyBlobFromAnotherBucketToInternalEndpoint.assertIsSatisfied();
        antuNetexValidationQueueEndpoint.assertIsSatisfied();
    }
}