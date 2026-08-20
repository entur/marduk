package no.rutebanken.marduk.chouette;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_VERSION;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChouetteImportResultHandlerTest {

    private static final String HANDLE = "inbound/received/rut/netex.zip";
    private static final String NISABA = "nisaba-exchange";

    private HttpServer server;
    private ChouetteClient client;
    private ChouetteJobs chouetteJobs;
    private InMemoryMardukBlobStoreRepository internalRepository;
    private InMemoryMardukBlobStoreRepository nisabaRepository;
    private RecordingPubSubPublisher publisher;
    private final List<String> requested = new CopyOnWriteArrayList<>();
    private final Map<String, String> responses = new HashMap<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());

        // One backing store, two views of it, so a copy between buckets is visible from both.
        Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
        internalRepository = new InMemoryMardukBlobStoreRepository(buckets);
        internalRepository.setContainerName("marduk-internal");
        nisabaRepository = new InMemoryMardukBlobStoreRepository(buckets);
        nisabaRepository.setContainerName(NISABA);
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream("zip".getBytes(StandardCharsets.UTF_8)));
        publisher = new RecordingPubSubPublisher();

        Provider provider = new Provider();
        provider.setId(2L);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential("rut");
        provider.setChouetteInfo(info);
        ProviderRepository providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider);
        chouetteJobs = new ChouetteJobs(client, providerRepository);

        responses.put("last_update_date", "2026-08-18T10:00:00");
        responses.put("jobs", "[]");
    }

    @AfterEach
    void stopServer() throws IOException {
        chouetteJobs.stopFanOut();
        client.close();
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            body.readAllBytes();
        }
        String path = exchange.getRequestURI().getPath();
        requested.add(path + (exchange.getRequestURI().getQuery() == null ? ""
                : "?" + exchange.getRequestURI().getQuery()));
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

    private ChouetteImportResultHandler handler() {
        return new ChouetteImportResultHandler(client, chouetteJobs,
                new MardukInternalBlobStoreService("marduk-internal", internalRepository),
                new JobEventPublisher(publisher), publisher, noRetries(), NISABA);
    }

    private static MardukMessage result(String actionReport, String validationReport) {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(CHOUETTE_REFERENTIAL, "rut")
                .setHeader(FILE_HANDLE, HANDLE)
                .setHeader(FILE_VERSION, 3L)
                .setHeader("action_report_result", actionReport)
                .setHeader("validation_report_result", validationReport);
    }

    private JobEvent reported() {
        return JobEvent.fromString(publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getLast().body());
    }

    @Test
    void aCleanImportTriggersValidation() {
        handler().handle(result("OK", "OK"));

        var validation = publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE);
        assertEquals(1, validation.size());
        assertEquals("VALIDATION_LEVEL_1",
                validation.getFirst().attributes().get(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL));
        assertEquals(JobEvent.State.OK, reported().getState());
    }

    @Test
    void aCleanImportKeepsTheDatasetForNisabaUnderItsUpdateTime() {
        handler().handle(result("OK", "OK"));

        assertTrue(nisabaRepository.exist("imported/rut/rut_2026-08-18T10_00_00.zip"),
                "the dataset was not archived under the key Chouette's update time gives it");
    }

    @Test
    void anotherQueuedImportPostponesValidation() {
        // Validating a half-imported dataspace wastes a Chouette job and reports on the wrong data.
        responses.put("jobs", "[{\"id\":1,\"referential\":\"rut\",\"status\":\"SCHEDULED\"}]");

        handler().handle(result("OK", "OK"));

        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE).isEmpty());
        assertEquals(JobEvent.State.OK, reported().getState(), "the import itself still succeeded");
    }

    @Test
    void aFailedValidationFailsTheImport() {
        handler().handle(result("OK", "NOK"));

        assertEquals(JobEvent.State.FAILED, reported().getState());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE).isEmpty());
    }

    @Test
    void aFailedImportIsReportedAsFailed() {
        handler().handle(result("NOK", "OK"));

        assertEquals(JobEvent.State.FAILED, reported().getState());
    }

    @Test
    void anUnrecognisedActionReportResultIsAlsoAFailure() {
        handler().handle(result("SOMETHING_ELSE", "OK"));

        assertEquals(JobEvent.State.FAILED, reported().getState());
    }

    @Test
    void aFailedImportArchivesNothing() {
        handler().handle(result("NOK", "OK"));

        assertTrue(requested.stream().noneMatch(r -> r.contains("last_update_date")),
                "a failed import must not be archived as a dataset nisaba can use");
    }

    @Test
    void theDestinationIsTheOneTheSubmittingStepNames() {
        assertEquals("direct:processImportResult", handler().destination());
    }
}
