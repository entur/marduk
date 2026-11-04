package no.rutebanken.marduk.flexlines;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_UTTU_EXPORT;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_FLEX;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_FLEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlexibleLinesExportConsumerTest {

    private static final String UTTU_FILE_NAME = "rb_mal-flexible-lines-20260727141110.zip";
    private static final String INBOUND_HANDLE = "inbound/uttu/" + UTTU_FILE_NAME;
    private static final String EXCHANGE_CONTAINER = "marduk-exchange";
    private static final String ANTU_CONTAINER = "antu-exchange";

    private Map<String, Map<String, byte[]>> buckets;
    private InMemoryMardukBlobStoreRepository exchangeRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private FlexibleLinesExportConsumer consumer;

    @BeforeEach
    void setUp() {
        buckets = new ConcurrentHashMap<>();
        exchangeRepository = new InMemoryMardukBlobStoreRepository(buckets);
        publisher = new RecordingPubSubPublisher();

        Provider provider = new Provider();
        provider.setId(42L);
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential("rb_mal");
        provider.setChouetteInfo(chouetteInfo);
        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProviderId("rb_mal")).thenReturn(42L);
        when(providerRepository.getProvider(42L)).thenReturn(provider);

        consumer = new FlexibleLinesExportConsumer(
                providerRepository,
                new ExchangeBlobStoreService(EXCHANGE_CONTAINER, exchangeRepository),
                new JobEventPublisher(publisher),
                publisher,
                ANTU_CONTAINER);
    }

    private static MardukMessage uttuNotification() {
        return new MardukMessage()
                .setHeader(CHOUETTE_REFERENTIAL, "rb_mal")
                .setHeader(CORRELATION_ID, "test-correlation")
                .setBody(UTTU_FILE_NAME);
    }

    private void storeTheUttuExport() {
        exchangeRepository.uploadBlob(
                INBOUND_HANDLE, new ByteArrayInputStream("zip bytes".getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> validationRequest() {
        return publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst().attributes();
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(p -> JobEvent.fromString(p.body()))
                .toList();
    }

    @Test
    void theNotifiedFileIsResolvedToTheInboundUttuFolder() {
        storeTheUttuExport();
        MardukMessage message = uttuNotification();

        consumer.handle(message);

        assertEquals(INBOUND_HANDLE, message.getHeader(FILE_HANDLE, String.class));
    }

    @Test
    void theProviderAndItsReferentialAreResolvedFromTheChouetteReferential() {
        storeTheUttuExport();
        MardukMessage message = uttuNotification();

        consumer.handle(message);

        assertEquals(42L, message.getHeader(PROVIDER_ID, Long.class));
        assertEquals("rb_mal", message.getHeader(DATASET_REFERENTIAL, String.class));
    }

    @Test
    void theExportIsCopiedIntoTheBucketAntuReadsUnderTheSameName() {
        storeTheUttuExport();

        consumer.handle(uttuNotification());

        exchangeRepository.setContainerName(ANTU_CONTAINER);
        assertNotNull(exchangeRepository.getBlob(INBOUND_HANDLE),
                "the flexible lines export was not copied into antu's bucket");
        assertEquals(INBOUND_HANDLE, validationRequest().get(VALIDATION_DATASET_FILE_HANDLE_HEADER));
    }

    @Test
    void theValidationRequestNamesTheFlexProfileAndStage() {
        storeTheUttuExport();

        consumer.handle(uttuNotification());

        Map<String, String> request = validationRequest();
        assertEquals(VALIDATION_STAGE_FLEX_POSTVALIDATION, request.get(VALIDATION_STAGE_HEADER));
        assertEquals(VALIDATION_CLIENT_MARDUK, request.get(VALIDATION_CLIENT_HEADER));
        assertEquals(VALIDATION_PROFILE_TIMETABLE_FLEX, request.get(VALIDATION_PROFILE_HEADER));
        assertEquals(IMPORT_TYPE_UTTU_EXPORT, request.get(VALIDATION_IMPORT_TYPE));
        assertEquals("test-correlation", request.get(VALIDATION_CORRELATION_ID_HEADER));
    }

    @Test
    void theExportIsReportedOkBeforeThePendingPostValidation() {
        storeTheUttuExport();

        consumer.handle(uttuNotification());

        List<JobEvent> reported = reportedEvents();
        assertEquals(2, reported.size());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX.name(), reported.getFirst().getAction());
        assertEquals(JobEvent.State.OK, reported.getFirst().getState());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name(), reported.getLast().getAction());
        assertEquals(JobEvent.State.PENDING, reported.getLast().getState());
    }

    @Test
    void anUnknownReferentialIsAckedAndDroppedWithoutAskingAntu() {
        // Failing here would nack the message and Pub/Sub would redeliver it forever, since the
        // referential never becomes known.
        when(providerRepository.getProviderId("rb_mal")).thenReturn(null);

        assertDoesNotThrow(() -> consumer.handle(uttuNotification()));

        assertTrue(publisher.published().isEmpty(), "an unknown referential produced pipeline traffic");
    }

    @Test
    void theSubscriptionIsTheFlexibleLinesExportQueue() {
        assertEquals(MardukQueues.FLEXIBLE_LINES_EXPORT_QUEUE, consumer.destination());
    }
}
