package no.rutebanken.marduk.routes.flexlines;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

class NetexFlexibleLinesImportRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:flexibleLinesImport")
    protected ProducerTemplate startRoute;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @EndpointInject("mock:antuNetexValidationQueue")
    protected MockEndpoint antuNetexValidationQueue;

    @Test
    void correctHeadersAndStatusShouldBeSet() throws Exception {

        AdviceWith.adviceWith(context, "flexible-lines-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):AntuNetexValidationQueue")
                    .replace()
                    .to("mock:antuNetexValidationQueue");
        });

        when(providerRepository.getProvider(anyLong()))
                .thenReturn(provider("rb_atb", 1, null));

        context.start();

        startRoute.sendBodyAndHeaders(
                null,
                Map.of(PROVIDER_ID, 1L,
                        FILE_HANDLE, "test.xml",
                        CORRELATION_ID, "corr-id-" + 1L)
        );

        List<Exchange> messages = antuNetexValidationQueue.getExchanges();

        assertNotNull(messages);
        assertEquals(messages.size(), 1, "Expected 1 message in AntuNetexValidationQueue");

        Exchange message = messages.get(0);

        assertEquals(message.getIn().getHeader(DATASET_REFERENTIAL), "rb_atb");
        assertEquals(message.getIn().getHeader(VALIDATION_STAGE_HEADER), VALIDATION_STAGE_IMPORT_FLEX_POSTVALIDATION);
        assertEquals(message.getIn().getHeader(VALIDATION_CLIENT_HEADER), VALIDATION_CLIENT_MARDUK);
        assertEquals(message.getIn().getHeader(VALIDATION_PROFILE_HEADER), VALIDATION_PROFILE_IMPORT_TIMETABLE_FLEX);
        assertEquals(message.getIn().getHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER), "test.xml");
        assertEquals(message.getIn().getHeader(VALIDATION_CORRELATION_ID_HEADER), "corr-id-1");

        List<Exchange> jobEvents = updateStatus.getExchanges();

        assertNotNull(jobEvents);
        assertEquals(jobEvents.size(), 1, "Expected 1 jobEvent");

        String jobEvent = jobEvents.get(0).getIn().getBody(String.class);

        assertEquals(JobEvent.fromString(jobEvent).state, JobEvent.State.PENDING);
        assertEquals(JobEvent.fromString(jobEvent).action, JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.toString());
    }
}