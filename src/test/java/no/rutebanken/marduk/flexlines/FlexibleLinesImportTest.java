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
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_IMPORT_TIMETABLE_FLEX;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_FLEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FlexibleLinesImportTest {

    private static final String HANDLE = "inbound/atb/flexible-lines.zip";
    private static final String INTERNAL_CONTAINER = "marduk-internal";
    private static final String ANTU_CONTAINER = "antu-exchange";

    private InMemoryMardukBlobStoreRepository internalRepository;
    private RecordingPubSubPublisher publisher;
    private FlexibleLinesImport flexibleLinesImport;

    @BeforeEach
    void setUp() {
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        publisher = new RecordingPubSubPublisher();

        Provider provider = new Provider();
        provider.setId(1L);
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential("atb");
        provider.setChouetteInfo(chouetteInfo);
        ProviderRepository providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(1L)).thenReturn(provider);

        flexibleLinesImport = new FlexibleLinesImport(
                providerRepository,
                new MardukInternalBlobStoreService(INTERNAL_CONTAINER, internalRepository),
                new JobEventPublisher(publisher),
                publisher,
                ANTU_CONTAINER);
    }

    private static MardukMessage flexImport() {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 1L)
                .setHeader(FILE_HANDLE, HANDLE)
                .setHeader(CORRELATION_ID, "corr-id-1");
    }

    private void storeTheArchive() {
        internalRepository.uploadBlob(
                HANDLE, new ByteArrayInputStream("zip bytes".getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> validationRequest() {
        return publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst().attributes();
    }

    @Test
    void theArchiveIsCopiedFromTheInternalBucketIntoTheBucketAntuReads() {
        storeTheArchive();
        MardukMessage message = flexImport();

        flexibleLinesImport.start(message);

        internalRepository.setContainerName(ANTU_CONTAINER);
        assertNotNull(internalRepository.getBlob(HANDLE), "the archive was not copied into antu's bucket");
        assertEquals(ANTU_CONTAINER, message.getHeader(TARGET_CONTAINER, String.class));
        assertEquals(HANDLE, message.getHeader(TARGET_FILE_HANDLE, String.class));
    }

    @Test
    void theValidationRequestNamesTheFlexImportProfileAndStage() {
        storeTheArchive();

        flexibleLinesImport.start(flexImport());

        Map<String, String> request = validationRequest();
        assertEquals("atb", request.get(DATASET_REFERENTIAL));
        assertEquals(VALIDATION_STAGE_FLEX_POSTVALIDATION, request.get(VALIDATION_STAGE_HEADER));
        assertEquals(VALIDATION_CLIENT_MARDUK, request.get(VALIDATION_CLIENT_HEADER));
        assertEquals(VALIDATION_PROFILE_IMPORT_TIMETABLE_FLEX, request.get(VALIDATION_PROFILE_HEADER));
        assertEquals(HANDLE, request.get(VALIDATION_DATASET_FILE_HANDLE_HEADER));
        assertEquals("corr-id-1", request.get(VALIDATION_CORRELATION_ID_HEADER));
        assertEquals(IMPORT_TYPE_NETEX_FLEX, request.get(VALIDATION_IMPORT_TYPE));
    }

    @Test
    void thePostValidationIsReportedAsPending() {
        storeTheArchive();

        flexibleLinesImport.start(flexImport());

        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name(), reported.getAction());
        assertEquals(JobEvent.State.PENDING, reported.getState());
    }
}
