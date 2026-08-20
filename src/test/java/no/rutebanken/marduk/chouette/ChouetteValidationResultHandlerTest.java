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
import java.nio.charset.StandardCharsets;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChouetteValidationResultHandlerTest {

    private HttpServer server;
    private ChouetteClient client;
    private ChouetteJobs chouetteJobs;
    private ProviderRepository providerRepository;
    private RecordingPubSubPublisher publisher;
    private volatile String jobList = "[]";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());
        publisher = new RecordingPubSubPublisher();

        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider(2L, "rut", 4L));
        when(providerRepository.getProvider(4L)).thenReturn(provider(4L, "rb_rut", null));
        chouetteJobs = new ChouetteJobs(client, providerRepository);
    }

    @AfterEach
    void stopServer() throws IOException {
        chouetteJobs.stopFanOut();
        client.close();
        server.stop(0);
    }

    private static Provider provider(long id, String referential, Long migrateTo) {
        Provider provider = new Provider();
        provider.setId(id);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential(referential);
        info.setMigrateDataToProvider(migrateTo);
        provider.setChouetteInfo(info);
        return provider;
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }
        byte[] payload = jobList.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, payload.length);
        try (var out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private ChouetteValidationResultHandler handler() {
        return new ChouetteValidationResultHandler(chouetteJobs, providerRepository,
                new JobEventPublisher(publisher), publisher);
    }

    private static MardukMessage result(long providerId, String referential,
                                        String actionReport, String validationReport) {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, providerId)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(CHOUETTE_REFERENTIAL, referential)
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL,
                        JobEvent.TimetableAction.VALIDATION_LEVEL_1.name())
                .setHeader("action_report_result", actionReport)
                .setHeader("validation_report_result", validationReport);
    }

    private JobEvent reported() {
        return JobEvent.fromString(publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getLast().body());
    }

    @Test
    void aProviderThatMigratesItsDataOnwardsTransfersItToTheNextDataspace() {
        handler().handle(result(2L, "rut", "OK", "OK"));

        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_TRANSFER_EXPORT_QUEUE).size());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE).isEmpty());
        assertEquals(JobEvent.State.OK, reported().getState());
    }

    @Test
    void aProviderThatKeepsItsDataExportsNetex() {
        handler().handle(result(4L, "rb_rut", "OK", "OK"));

        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE).size());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_TRANSFER_EXPORT_QUEUE).isEmpty());
    }

    @Test
    void anotherQueuedImportPostponesBoth() {
        // The data is about to change again, so neither the transfer nor the export is worth starting.
        jobList = "[{\"id\":1,\"referential\":\"rut\",\"status\":\"SCHEDULED\"}]";

        handler().handle(result(2L, "rut", "OK", "OK"));

        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_TRANSFER_EXPORT_QUEUE).isEmpty());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE).isEmpty());
        assertEquals(JobEvent.State.OK, reported().getState(), "the validation itself still succeeded");
    }

    @Test
    void faultyTimetableDataFailsTheValidationAndMovesNothing() {
        handler().handle(result(4L, "rb_rut", "OK", "NOK"));

        assertEquals(JobEvent.State.FAILED, reported().getState());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE).isEmpty());
    }

    @Test
    void aValidationThatDidNotRunIsAlsoAFailure() {
        handler().handle(result(4L, "rb_rut", "NOK", "OK"));

        assertEquals(JobEvent.State.FAILED, reported().getState());
    }

    @Test
    void theFailureIsReportedUnderTheLevelThatWasRequested() {
        MardukMessage message = result(4L, "rb_rut", "NOK", "OK")
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL,
                        JobEvent.TimetableAction.VALIDATION_LEVEL_2.name());

        handler().handle(message);

        assertEquals("VALIDATION_LEVEL_2", reported().getAction());
    }

    @Test
    void theDestinationIsTheOneTheSubmittingStepNames() {
        assertEquals("direct:processValidationResult", handler().destination());
    }
}
