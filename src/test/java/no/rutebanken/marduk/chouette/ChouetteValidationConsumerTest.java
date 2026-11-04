package no.rutebanken.marduk.chouette;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_TYPE;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChouetteValidationConsumerTest {

    private HttpServer server;
    private ChouetteClient client;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private final List<String> requested = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());
        publisher = new RecordingPubSubPublisher();

        Provider provider = new Provider();
        provider.setId(2L);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential("rut");
        provider.setChouetteInfo(info);
        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider);
    }

    @AfterEach
    void stopServer() throws IOException {
        client.close();
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }
        requested.add(exchange.getRequestURI().getPath());
        exchange.getResponseHeaders().add("Location",
                "http://chouette/chouette_iev/referentials/rut/scheduled_jobs/5");
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    private ChouetteValidationConsumer consumer() {
        return new ChouetteValidationConsumer(client, providerRepository,
                new JobEventPublisher(publisher), new ChouetteJobSubmission(publisher));
    }

    private static MardukMessage validationRequest(JobEvent.TimetableAction level) {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, level.name());
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(p -> JobEvent.fromString(p.body()))
                .toList();
    }

    @Test
    void theValidatorIsCalledForTheProvidersDataspace() {
        consumer().handle(validationRequest(JobEvent.TimetableAction.VALIDATION_LEVEL_1));

        assertEquals(List.of("/chouette_iev/referentials/rut/validator"), requested);
    }

    @Test
    void theRequestedLevelIsWhatTheJobIsReportedUnder() {
        // A level 1 and a level 2 validation of the same dataspace have to be two jobs in nabu.
        consumer().handle(validationRequest(JobEvent.TimetableAction.VALIDATION_LEVEL_2));

        assertEquals("VALIDATION_LEVEL_2", reportedEvents().getFirst().getAction());
        assertEquals("VALIDATION_LEVEL_2", publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE)
                .getFirst().attributes().get(CHOUETTE_JOB_STATUS_JOB_TYPE));
    }

    @Test
    void theJobIsPutOnThePollQueueWithTheValidationResultHandlerAsItsDestination() {
        consumer().handle(validationRequest(JobEvent.TimetableAction.VALIDATION_LEVEL_1));

        var polled = publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst();
        assertEquals("direct:processValidationResult",
                polled.attributes().get(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION));
        assertEquals("5", polled.attributes().get(CHOUETTE_JOB_ID));
    }

    @Test
    void theValidationIsReportedAsPending() {
        consumer().handle(validationRequest(JobEvent.TimetableAction.VALIDATION_LEVEL_1));

        assertEquals(JobEvent.State.PENDING, reportedEvents().getFirst().getState());
    }

    @Test
    void aRequestForADataspaceThatDoesNotExistIsReportedAsFailedRatherThanDropped() {
        // The operator asked for something; nabu has to show that it did not happen.
        MardukMessage message = validationRequest(JobEvent.TimetableAction.VALIDATION_LEVEL_1)
                .setHeader(PROVIDER_ID, 999L);

        consumer().handle(message);

        assertTrue(requested.isEmpty(), "Chouette was asked to validate a dataspace with no provider");
        assertEquals(JobEvent.State.FAILED, reportedEvents().getLast().getState());
    }

    @Test
    void aRequestWithoutACorrelationIdGetsOne() {
        MardukMessage message = validationRequest(JobEvent.TimetableAction.VALIDATION_LEVEL_1)
                .removeHeader(CORRELATION_ID);

        consumer().handle(message);

        assertNotNull(message.getHeader(CORRELATION_ID), "the job would be untraceable in nabu");
    }

    @Test
    void theSubscriptionIsTheValidationQueue() {
        assertEquals(MardukQueues.CHOUETTE_VALIDATION_QUEUE, consumer().destination());
    }
}
