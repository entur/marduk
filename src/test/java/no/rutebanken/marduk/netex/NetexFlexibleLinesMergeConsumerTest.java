package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.experimental.SetProviderIdBeforeFlexMergeProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_FLEX_MERGING;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetexFlexibleLinesMergeConsumerTest {

    private static final String REFERENTIAL = "rb_rut";
    private static final long PROVIDER = 1002L;

    private static final String CHOUETTE_EXPORT = "chouette/netex/rb_rut-" + CURRENT_AGGREGATED_NETEX_FILENAME;
    private static final String FLEX_EXPORT =
            BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-" + CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;
    private static final String LATEST_ASHUR_OUTPUT =
            "filtered-netex/rb_rut/latest-without-blocks/rb_rut-" + CURRENT_AGGREGATED_NETEX_FILENAME;
    private static final String MERGED_EXPORT =
            BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-" + CURRENT_AGGREGATED_NETEX_FILENAME;

    private static final String INTERNAL_CONTAINER = "marduk-internal";
    private static final String PUBLIC_CONTAINER = "marduk";
    private static final String EXCHANGE_CONTAINER = "marduk-exchange";
    private static final String ANTU_CONTAINER = "antu-exchange";

    @TempDir
    Path workingDirectory;

    private InMemoryMardukBlobStoreRepository internalRepository;
    private InMemoryMardukBlobStoreRepository publicRepository;
    private InMemoryMardukBlobStoreRepository exchangeRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private MergedNetexPublication publication;
    private List<Provider> providers;

    @BeforeEach
    void setUp() {
        Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
        internalRepository = repository(buckets, INTERNAL_CONTAINER);
        publicRepository = repository(buckets, PUBLIC_CONTAINER);
        exchangeRepository = repository(buckets, EXCHANGE_CONTAINER);
        publisher = new RecordingPubSubPublisher();
        publication = mock(MergedNetexPublication.class);

        // The experimental-import lookup matches on the codespace, so a codespace has both an rb_ provider
        // and a bare one and the flag has to be set on whichever the lookup finds.
        Provider rbProvider = provider(PROVIDER, REFERENTIAL);
        Provider bareProvider = provider(2L, "rut");
        providers = List.of(rbProvider, bareProvider);

        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(PROVIDER)).thenReturn(rbProvider);
        when(providerRepository.getProvider(2L)).thenReturn(bareProvider);
        when(providerRepository.getProviderId(REFERENTIAL)).thenReturn(PROVIDER);
        when(providerRepository.getProviders()).thenReturn(providers);
    }

    private static InMemoryMardukBlobStoreRepository repository(
            Map<String, Map<String, byte[]>> buckets, String container) {
        InMemoryMardukBlobStoreRepository repository = new InMemoryMardukBlobStoreRepository(buckets);
        repository.setContainerName(container);
        return repository;
    }

    private static Provider provider(long id, String referential) {
        Provider provider = new Provider();
        provider.setId(id);
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential(referential);
        provider.setChouetteInfo(chouetteInfo);
        return provider;
    }

    private void enableExperimentalImport() {
        providers.forEach(provider -> provider.getChouetteInfo().setEnableExperimentalImport(true));
    }

    private NetexFlexibleLinesMergeConsumer consumer(
            boolean experimentalImportEnabled, boolean mergeFlexibleLinesEnabled) {
        ExperimentalImportHelpers helpers =
                new ExperimentalImportHelpers(experimentalImportEnabled, providerRepository);
        return new NetexFlexibleLinesMergeConsumer(
                helpers,
                new SetProviderIdBeforeFlexMergeProcessor(helpers, providerRepository),
                new MardukInternalBlobStoreService(INTERNAL_CONTAINER, internalRepository),
                new MardukPublicBlobStoreService(PUBLIC_CONTAINER, publicRepository),
                new ExchangeBlobStoreService(EXCHANGE_CONTAINER, exchangeRepository),
                publication,
                new JobEventPublisher(publisher),
                publisher,
                workingDirectory.toString(),
                mergeFlexibleLinesEnabled,
                ANTU_CONTAINER);
    }

    private static MardukMessage mergeRequest() {
        return new MardukMessage()
                .setHeader(CHOUETTE_REFERENTIAL, REFERENTIAL)
                .setHeader(CORRELATION_ID, "corr");
    }

    private void store(InMemoryMardukBlobStoreRepository repository, String handle, String archive) {
        try (InputStream zip = Files.newInputStream(
                Path.of("src/test/resources/no/rutebanken/marduk/routes/file/beans/" + archive))) {
            repository.uploadBlob(handle, zip);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> validationRequest() {
        return publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst().attributes();
    }

    @Test
    void bothSourcesPresentMeansTheMergeHasToBePostValidatedBeforeItIsPublished() {
        store(internalRepository, CHOUETTE_EXPORT, "netex.zip");
        store(exchangeRepository, FLEX_EXPORT, "netex_with_two_files.zip");

        consumer(false, true).handle(mergeRequest());

        Map<String, String> request = validationRequest();
        assertEquals(VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION, request.get(VALIDATION_STAGE_HEADER));
        assertEquals(VALIDATION_CLIENT_MARDUK, request.get(VALIDATION_CLIENT_HEADER));
        assertEquals(VALIDATION_PROFILE_TIMETABLE_FLEX_MERGING, request.get(VALIDATION_PROFILE_HEADER));
        assertEquals("corr", request.get(VALIDATION_CORRELATION_ID_HEADER));

        String merged = request.get(VALIDATION_DATASET_FILE_HANDLE_HEADER);
        assertTrue(merged.matches("uttu/netex/rb_rut/corr_\\d{17}-" + CURRENT_AGGREGATED_NETEX_FILENAME),
                "unexpected merged file handle " + merged);
        internalRepository.setContainerName(INTERNAL_CONTAINER);
        assertNotNull(internalRepository.getBlob(merged), "the merged dataset was not stored");
        internalRepository.setContainerName(ANTU_CONTAINER);
        assertNotNull(internalRepository.getBlob(merged), "the merged dataset was not handed to antu");

        verify(publication, never()).publishMergedDataset(any());
    }

    @Test
    void thePendingMergedPostValidationIsReported() {
        store(internalRepository, CHOUETTE_EXPORT, "netex.zip");
        store(exchangeRepository, FLEX_EXPORT, "netex_with_two_files.zip");

        consumer(false, true).handle(mergeRequest());

        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION.name(), reported.getAction());
        assertEquals(JobEvent.State.PENDING, reported.getState());
    }

    @Test
    void aCodespaceWithoutFlexibleLineDataStillPublishesTheChouetteExport() {
        store(internalRepository, CHOUETTE_EXPORT, "netex.zip");
        MardukMessage message = mergeRequest();

        consumer(false, true).handle(message);

        publicRepository.setContainerName(PUBLIC_CONTAINER);
        assertNotNull(publicRepository.getBlob(MERGED_EXPORT),
                "the Chouette export was not published to the outbound bucket");
        assertEquals(MERGED_EXPORT, message.getHeader(FILE_HANDLE, String.class));
        verify(publication).publishMergedDataset(message);
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty(),
                "antu was asked to validate a dataset nothing was merged into");
    }

    @Test
    void theFlexibleLinesExportIsIgnoredWhenTheMergeIsDisabled() {
        store(internalRepository, CHOUETTE_EXPORT, "netex.zip");
        store(exchangeRepository, FLEX_EXPORT, "netex_with_two_files.zip");
        MardukMessage message = mergeRequest();

        consumer(false, false).handle(message);

        verify(publication).publishMergedDataset(message);
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
    }

    /**
     * A FLEX post-validation carries the flex import's correlation id, so the Ashur output of the most recent
     * ordinary import is not at the correlation-keyed path. The stable per-referential path is the fallback,
     * and without it the merge would find no Chouette data and publish flex-only data as the whole dataset.
     */
    @Test
    void anExperimentalCodespaceFallsBackToTheLatestAshurOutputWhenTheCorrelationKeyedPathIsEmpty() {
        enableExperimentalImport();
        store(internalRepository, LATEST_ASHUR_OUTPUT, "netex.zip");
        store(exchangeRepository, FLEX_EXPORT, "netex_with_two_files.zip");
        MardukMessage message = mergeRequest()
                .setHeader(DATASET_REFERENTIAL, REFERENTIAL)
                .setHeader(PROVIDER_ID, PROVIDER);

        consumer(true, true).handle(message);

        assertEquals(VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION,
                validationRequest().get(VALIDATION_STAGE_HEADER));
        verify(publication, never()).publishMergedDataset(any());
    }

    @Test
    void anExperimentalCodespaceWithNothingFromAshurPublishesTheFlexDataAlone() {
        enableExperimentalImport();
        store(exchangeRepository, FLEX_EXPORT, "netex_with_two_files.zip");
        MardukMessage message = mergeRequest()
                .setHeader(DATASET_REFERENTIAL, REFERENTIAL)
                .setHeader(PROVIDER_ID, PROVIDER);

        consumer(true, true).handle(message);

        verify(publication).publishMergedDataset(message);
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
    }

    @Test
    void theScratchDirectoryIsRemovedWhenTheMergeSucceeds() {
        store(internalRepository, CHOUETTE_EXPORT, "netex.zip");

        consumer(false, true).handle(mergeRequest());

        assertNoScratchDirectoryIsLeftBehind();
    }

    @Test
    void theScratchDirectoryIsRemovedWhenTheMergeFails() {
        store(internalRepository, CHOUETTE_EXPORT, "netex.zip");
        doThrow(new MardukException("nabu is down")).when(publication).publishMergedDataset(any());

        assertThrows(MardukException.class, () -> consumer(false, true).handle(mergeRequest()));

        assertNoScratchDirectoryIsLeftBehind();
    }

    @Test
    void aMergeWithoutAReferentialFails() {
        MardukException failure = assertThrows(MardukException.class,
                () -> consumer(false, true).handle(new MardukMessage().setHeader(CORRELATION_ID, "corr")));

        assertTrue(failure.getMessage().contains("Chouette referential"), failure.getMessage());
    }

    @Test
    void aMergeForAnUnknownReferentialFails() {
        when(providerRepository.getProviderId(REFERENTIAL)).thenReturn(null);

        MardukException failure =
                assertThrows(MardukException.class, () -> consumer(false, true).handle(mergeRequest()));

        assertTrue(failure.getMessage().contains("provider id"), failure.getMessage());
    }

    @Test
    void theSubscriptionIsTheMergeQueue() {
        assertEquals(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE, consumer(false, true).destination());
    }

    private void assertNoScratchDirectoryIsLeftBehind() {
        try (var entries = Files.list(workingDirectory)) {
            assertFalse(entries.findAny().isPresent(), "the unpacked dataset was left on disk");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
