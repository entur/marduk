package no.rutebanken.marduk.validation;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_FINLAND;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_NIGHTLY_VALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_PREVALIDATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AntuValidationTest {

    private static final String HANDLE = "inbound/received/rb_tst/netex.zip";
    private static final String ANTU_CONTAINER = "antu-exchange";

    private InMemoryMardukBlobStoreRepository internalRepository;
    private RecordingPubSubPublisher publisher;
    private AntuValidation antuValidation;

    @BeforeEach
    void setUp() {
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalRepository.setContainerName("marduk-internal");
        publisher = new RecordingPubSubPublisher();

        Provider provider = new Provider();
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential("rb_tst");
        provider.setChouetteInfo(chouetteInfo);
        ProviderRepository providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider);

        antuValidation = new AntuValidation(
                new MardukInternalBlobStoreService("marduk-internal", internalRepository),
                providerRepository,
                new NetexValidationProfiles(List.of(), List.of("OYM")),
                new JobEventPublisher(publisher),
                publisher,
                ANTU_CONTAINER);
    }

    private static MardukMessage netexMessage() {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(FILE_HANDLE, HANDLE)
                .setHeader(FILE_TYPE, FileType.NETEXPROFILE.name());
    }

    private void storeTheFile() {
        internalRepository.uploadBlob(HANDLE, new ByteArrayInputStream("zip".getBytes(StandardCharsets.UTF_8)));
    }

    private Map<String, String> requestAttributes() {
        return publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst().attributes();
    }

    private JobEvent reported() {
        return JobEvent.fromString(publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
    }

    @Test
    void aPreValidationRequestDescribesTheDatasetAndTheStage() {
        storeTheFile();
        MardukMessage message = netexMessage();

        antuValidation.requestPreValidation(message);

        Map<String, String> attributes = requestAttributes();
        assertEquals(VALIDATION_STAGE_PREVALIDATION, attributes.get(VALIDATION_STAGE_HEADER));
        assertEquals(VALIDATION_CLIENT_MARDUK, attributes.get(VALIDATION_CLIENT_HEADER));
        assertEquals(HANDLE, attributes.get(VALIDATION_DATASET_FILE_HANDLE_HEADER));
        assertEquals("corr", attributes.get(VALIDATION_CORRELATION_ID_HEADER));
        assertEquals("rb_tst", attributes.get(DATASET_REFERENTIAL));
        assertEquals(VALIDATION_PROFILE_TIMETABLE, attributes.get(VALIDATION_PROFILE_HEADER));
        assertEquals("", publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst().body());
    }

    @Test
    void theFileIsCopiedIntoTheBucketAntuReadsUnderTheSameName() {
        storeTheFile();
        MardukMessage message = netexMessage();

        antuValidation.requestPreValidation(message);

        // The path has to match: the handle in the attribute is what antu opens.
        assertEquals(ANTU_CONTAINER, message.getHeader(TARGET_CONTAINER, String.class));
        assertEquals(HANDLE, message.getHeader(TARGET_FILE_HANDLE, String.class));
    }

    @Test
    void aPendingPreValidationIsReported() {
        storeTheFile();

        antuValidation.requestPreValidation(netexMessage());

        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), reported().getAction());
        assertEquals(JobEvent.State.PENDING, reported().getState());
    }

    @Test
    void aPendingNightlyValidationIsReportedAsAPrevalidation() {
        storeTheFile();

        antuValidation.requestNightlyValidationIfFilePresent(netexMessage().setHeader(DATASET_REFERENTIAL, "rb_tst"));

        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), reported().getAction());
        assertEquals(JobEvent.State.PENDING, reported().getState());
    }

    @Test
    void eachPostValidationStageIsReportedAgainstItsOwnJobWithoutTheChouetteJobId() {
        // The pipeline shows a separate job per post-validation, and the Chouette export job that produced
        // the file is not this validation's external id.
        storeTheFile();

        antuValidation.requestPostValidation(
                netexMessage().setHeader(CHOUETTE_JOB_ID, "4711"), VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION);

        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name(), reported().getAction());
        assertEquals(JobEvent.State.PENDING, reported().getState());
        assertNull(reported().getExternalId());
    }

    @Test
    void aBlocksPostValidationIsReportedAgainstTheBlocksJob() {
        storeTheFile();

        antuValidation.requestPostValidation(
                netexMessage().setHeader(CHOUETTE_JOB_ID, "4711"),
                VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION);

        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION.name(), reported().getAction());
        assertEquals(JobEvent.State.PENDING, reported().getState());
        assertNull(reported().getExternalId());
    }

    @Test
    void gtfsIsLeftToChouette() {
        storeTheFile();

        antuValidation.requestPreValidation(netexMessage().setHeader(FILE_TYPE, FileType.GTFS.name()));

        assertTrue(publisher.published().isEmpty(), "antu was asked to validate a GTFS file");
    }

    @Test
    void theProfileFollowsTheCodespace() {
        storeTheFile();
        Provider finnish = new Provider();
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential("rb_oym");
        finnish.setChouetteInfo(info);
        ProviderRepository providers = mock(ProviderRepository.class);
        when(providers.getProvider(2L)).thenReturn(finnish);
        AntuValidation withFinnishProvider = new AntuValidation(
                new MardukInternalBlobStoreService("marduk-internal", internalRepository),
                providers,
                new NetexValidationProfiles(List.of(), List.of("OYM")),
                new JobEventPublisher(publisher),
                publisher,
                ANTU_CONTAINER);

        withFinnishProvider.requestPreValidation(netexMessage());

        assertEquals(VALIDATION_PROFILE_TIMETABLE_FINLAND, requestAttributes().get(VALIDATION_PROFILE_HEADER));
    }

    @Test
    void aNightlyValidationIsRequestedForTheStoredFile() {
        storeTheFile();
        MardukMessage message = netexMessage().setHeader(DATASET_REFERENTIAL, "rb_tst");

        assertTrue(antuValidation.requestNightlyValidationIfFilePresent(message));

        assertEquals(VALIDATION_STAGE_NIGHTLY_VALIDATION, requestAttributes().get(VALIDATION_STAGE_HEADER));
    }

    @Test
    void aMissingFileFallsBackToTheCaller() {
        // Nothing stored: the caller triggers a Chouette level 1 validation instead, so nothing may be
        // published here - antu would otherwise wait for a file that does not exist.
        MardukMessage message = netexMessage().setHeader(DATASET_REFERENTIAL, "rb_tst");

        assertFalse(antuValidation.requestNightlyValidationIfFilePresent(message));

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void aMissingFileHandleFallsBackToTheCaller() {
        MardukMessage message = netexMessage().removeHeader(FILE_HANDLE).setHeader(DATASET_REFERENTIAL, "rb_tst");

        assertFalse(antuValidation.requestNightlyValidationIfFilePresent(message));

        assertTrue(publisher.published().isEmpty());
    }
}
