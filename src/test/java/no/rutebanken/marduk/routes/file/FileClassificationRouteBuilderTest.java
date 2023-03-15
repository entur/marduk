package no.rutebanken.marduk.routes.file;

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
import java.util.HashMap;
import java.util.Map;

import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;

public class FileClassificationRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:processValidFile")
    protected ProducerTemplate processValidFileTemplate;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatusMock;

    @EndpointInject("mock:antuNetexPreValidation")
    protected MockEndpoint antuNetexPreValidationMock;

    @EndpointInject("mock:flexibleLinesImportQueue")
    protected MockEndpoint flexibleLinesImportQueueMock;

    @EndpointInject("mock:chouetteImportQueue")
    protected MockEndpoint chouetteImportQueueMock;

    @BeforeEach
    protected void setUp() throws IOException {
        super.setUp();
        updateStatusMock.reset();
        flexibleLinesImportQueueMock.reset();
        antuNetexPreValidationMock.reset();
        chouetteImportQueueMock.reset();
    }

    @Test
    public void testMessagesWithImportTypeFlexShouldBeSendToFlexibleLinesImportQueue() throws Exception {

        // Mock initial call to Chouette to validation job
        AdviceWith.adviceWith(
                context, "process-valid-file",
                a -> {
                    a.interceptSendToEndpoint("direct:updateStatus")
                            .skipSendToOriginalEndpoint()
                            .to("mock:updateStatus");
                    a.interceptSendToEndpoint("direct:antuNetexPreValidation")
                            .skipSendToOriginalEndpoint()
                            .to("mock:antuNetexPreValidation");
                    a.weaveByToUri("google-pubsub:(.*):FlexibleLinesImportQueue")
                            .replace()
                            .to("mock:flexibleLinesImportQueue");
                    a.weaveByToUri("google-pubsub:(.*):ChouetteImportQueue")
                            .replace()
                            .to("mock:chouetteImportQueue");
                });

        // we must manually start when we are done with all the advice with
        context.start();

        updateStatusMock.expectedMessageCount(1);
        antuNetexPreValidationMock.expectedMessageCount(0);
        flexibleLinesImportQueueMock.expectedMessageCount(1);
        chouetteImportQueueMock.expectedMessageCount(0);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.CORRELATION_ID, "corr_id");
        headers.put(Constants.IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX);
        processValidFileTemplate.sendBodyAndHeaders("", headers);

        updateStatusMock.assertIsSatisfied();
        antuNetexPreValidationMock.assertIsSatisfied();
        flexibleLinesImportQueueMock.assertIsSatisfied();
        chouetteImportQueueMock.assertIsSatisfied();
    }

    @Test
    public void testMessagesWithoutImportTypeFlexShouldBeSendToChouetteImportQueue() throws Exception {

        // Mock initial call to Chouette to validation job
        AdviceWith.adviceWith(
                context, "process-valid-file",
                a -> {
                    a.interceptSendToEndpoint("direct:updateStatus")
                            .skipSendToOriginalEndpoint()
                            .to("mock:updateStatus");
                    a.interceptSendToEndpoint("direct:antuNetexPreValidation")
                            .skipSendToOriginalEndpoint()
                            .to("mock:antuNetexPreValidation");
                    a.weaveByToUri("google-pubsub:(.*):FlexibleLinesImportQueue")
                            .replace()
                            .to("mock:flexibleLinesImportQueue");
                    a.weaveByToUri("google-pubsub:(.*):ChouetteImportQueue")
                            .replace()
                            .to("mock:chouetteImportQueue");

                });

        // we must manually start when we are done with all the advice with
        context.start();

        updateStatusMock.expectedMessageCount(1);
        antuNetexPreValidationMock.expectedMessageCount(1);
        flexibleLinesImportQueueMock.expectedMessageCount(0);
        chouetteImportQueueMock.expectedMessageCount(1);

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, "2");
        headers.put(Constants.CORRELATION_ID, "corr_id");
        processValidFileTemplate.sendBodyAndHeaders("", headers);

        updateStatusMock.assertIsSatisfied();
        antuNetexPreValidationMock.assertIsSatisfied();
        flexibleLinesImportQueueMock.assertIsSatisfied();
        chouetteImportQueueMock.assertIsSatisfied();
    }
}