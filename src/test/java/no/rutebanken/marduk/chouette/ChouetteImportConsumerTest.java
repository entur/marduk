package no.rutebanken.marduk.chouette;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.FileType;
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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_TYPE;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_URL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChouetteImportConsumerTest {

    private static final String HANDLE = "inbound/received/rut/netex.zip";

    private record Received(String method, String path, byte[] body) {
    }

    private HttpServer server;
    private ChouetteClient client;
    private InMemoryMardukBlobStoreRepository internalRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private final List<Received> received = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());

        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalRepository.setContainerName("marduk-internal");
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
            received.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    body.readAllBytes()));
        }
        exchange.getResponseHeaders().add("Location",
                "http://chouette/chouette_iev/referentials/rut/scheduled_jobs/17");
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }

    private ChouetteImportConsumer consumer(boolean enablePreValidation) {
        return new ChouetteImportConsumer(
                client,
                new MardukInternalBlobStoreService("marduk-internal", internalRepository),
                providerRepository,
                new JobEventPublisher(publisher),
                new ChouetteJobSubmission(publisher),
                noRetries(),
                enablePreValidation,
                List.of());
    }

    private static MardukMessage importRequest() {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(FILE_NAME, "netex.zip")
                .setHeader(FILE_HANDLE, HANDLE)
                .setHeader(FILE_TYPE, FileType.NETEXPROFILE.name());
    }

    private void storeTheDataset() {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream("zip bytes".getBytes(StandardCharsets.UTF_8)));
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(p -> JobEvent.fromString(p.body()))
                .toList();
    }

    @Test
    void theDatasetIsPostedToTheProvidersImporter() {
        storeTheDataset();

        consumer(true).handle(importRequest());

        assertEquals("POST", received.getFirst().method());
        assertEquals("/chouette_iev/referentials/rut/importer/netexprofile", received.getFirst().path());
    }

    @Test
    void aGtfsFileGoesToTheGtfsImporter() {
        storeTheDataset();

        consumer(true).handle(importRequest().setHeader(FILE_TYPE, FileType.GTFS.name()));

        assertEquals("/chouette_iev/referentials/rut/importer/gtfs", received.getFirst().path());
    }

    @Test
    void theDatasetIsSentAsTheMultipartFeed() {
        storeTheDataset();

        consumer(true).handle(importRequest());

        String body = new String(received.getFirst().body(), StandardCharsets.UTF_8);
        assertTrue(body.contains("zip bytes"), "the dataset was not in the multipart body");
        assertTrue(body.contains("netex.zip"), "the file name was not in the multipart body");
    }

    @Test
    void gtfsIsPreValidatedByChouetteEvenWhenPreValidationIsOff() {
        // antu does not validate GTFS, so Chouette has to.
        storeTheDataset();

        consumer(false).handle(importRequest().setHeader(FILE_TYPE, FileType.GTFS.name()));

        assertTrue(new String(received.getFirst().body(), StandardCharsets.UTF_8).contains("gtfs-import"),
                "the GTFS import parameters were not sent");
    }

    @Test
    void theJobIsPutOnThePollQueueWithEverythingThePollerNeeds() {
        storeTheDataset();
        MardukMessage message = importRequest();

        consumer(true).handle(message);

        var polled = publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst();
        assertEquals("http://chouette/chouette_iev/referentials/rut/scheduled_jobs/17",
                polled.attributes().get(CHOUETTE_JOB_STATUS_URL));
        assertEquals("17", polled.attributes().get(CHOUETTE_JOB_ID));
        assertEquals("direct:processImportResult", polled.attributes().get(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION));
        assertEquals("IMPORT", polled.attributes().get(CHOUETTE_JOB_STATUS_JOB_TYPE));
        assertEquals("", polled.body());
    }

    @Test
    void aRetriedImportDoesNotInheritTheEarlierJobsPollCount() {
        // Otherwise the new job starts near the retry cap and is declared timed out early.
        storeTheDataset();

        consumer(true).handle(importRequest().setHeader("loopCounter", 2999));

        assertNull(publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst()
                .attributes().get("loopCounter"));
    }

    @Test
    void aRetriedImportDoesNotInheritTheEarlierJobsId() {
        storeTheDataset();

        consumer(true).handle(importRequest().setHeader(CHOUETTE_JOB_ID, "9"));

        assertEquals("17", publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst()
                .attributes().get(CHOUETTE_JOB_ID));
    }

    @Test
    void theImportIsReportedAsPending() {
        storeTheDataset();

        consumer(true).handle(importRequest());

        JobEvent reported = reportedEvents().getFirst();
        assertEquals("IMPORT", reported.getAction());
        assertEquals(JobEvent.State.PENDING, reported.getState());
    }

    @Test
    void aMissingDatasetFailsTheImportWithoutCallingChouette() {
        consumer(true).handle(importRequest());

        assertTrue(received.isEmpty(), "Chouette was asked to import a file that is not there");
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).isEmpty());
        assertEquals(JobEvent.State.FAILED, reportedEvents().getLast().getState());
    }

    @Test
    void theReferentialIsRecordedOnTheMessage() {
        storeTheDataset();
        MardukMessage message = importRequest();

        consumer(true).handle(message);

        assertEquals("rut", message.getHeader(Constants.CHOUETTE_REFERENTIAL, String.class));
    }

    @Test
    void theSubscriptionIsTheImportQueue() {
        assertEquals(MardukQueues.CHOUETTE_IMPORT_QUEUE, consumer(true).destination());
    }
}
