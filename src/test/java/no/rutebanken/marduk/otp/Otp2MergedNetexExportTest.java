package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Otp2MergedNetexExportTest {

    private static final String CONTAINER = "marduk";
    private static final String STOPS_BLOB = "tiamat/Full_latest.zip";
    private static final String MERGED_BLOB = "outbound/netex/rb_norway-aggregated-netex.zip";
    private static final String RUT_EXPORT = "outbound/netex/rb_rut-aggregated-netex.zip";
    private static final String AKT_EXPORT = "outbound/netex/rb_akt-aggregated-netex.zip";

    @TempDir
    Path workingDirectory;

    private InMemoryMardukBlobStoreRepository repository;
    private MardukPublicBlobStoreService blobStore;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        blobStore = new MardukPublicBlobStoreService(CONTAINER, repository);
        publisher = new RecordingPubSubPublisher();
        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProviders()).thenReturn(List.of(provider("rb_rut", null), provider("rb_akt", null)));
    }

    private static Provider provider(String referential, Long migrateDataToProvider) {
        Provider provider = new Provider();
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential(referential);
        info.setMigrateDataToProvider(migrateDataToProvider);
        provider.setChouetteInfo(info);
        return provider;
    }

    private Otp2MergedNetexExport export() {
        return new Otp2MergedNetexExport(
                blobStore,
                providerRepository,
                new JobEventPublisher(publisher),
                workingDirectory.toString(),
                STOPS_BLOB,
                "netex/rb_norway-aggregated-netex.zip",
                "_stops");
    }

    private void upload(String name, String file) throws IOException {
        try (InputStream contents = Files.newInputStream(Path.of(file))) {
            repository.uploadBlob(name, contents);
        }
    }

    private void uploadStopPlaceExport() throws IOException {
        upload(STOPS_BLOB, "src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip");
    }

    private void uploadProviderExport(String name) throws IOException {
        upload(name, "src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip");
    }

    private static MardukMessage request() {
        return new MardukMessage().setHeader(CORRELATION_ID, "corr-id");
    }

    private Set<String> mergedArchiveEntries() throws IOException {
        try (InputStream merged = repository.getBlob(MERGED_BLOB)) {
            return ZipFileUtils.listFilesInZip(merged.readAllBytes()).stream()
                    .map(ZipEntry::getName)
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(published -> JobEvent.fromString(published.body()))
                .toList();
    }

    @Test
    void everyProvidersExportAndTheStopPlacesAreMergedIntoOneArchive() throws IOException {
        uploadProviderExport(RUT_EXPORT);
        uploadProviderExport(AKT_EXPORT);
        uploadStopPlaceExport();

        assertTrue(export().export(request()));

        assertEquals(Set.of("WF739.xml", "_stops.xml"), mergedArchiveEntries(),
                "the stop place file is renamed to the name the profile expects");
        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.OK),
                reportedEvents().stream().map(JobEvent::getState).toList());
        assertEquals(JobEvent.JobDomain.TIMETABLE_PUBLISH, reportedEvents().getFirst().getDomain());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_MERGED.toString(),
                reportedEvents().getFirst().getAction());
    }

    @Test
    void aProviderWhoseDataMigratesOnwardsIsNotIncluded() {
        // Its data is exported by the dataspace it migrates into, so including it would duplicate every line.
        when(providerRepository.getProviders())
                .thenReturn(List.of(provider("rut", 2L), provider("rb_rut", null)));

        assertEquals(List.of("rb_rut-aggregated-netex.zip"), export().aggregatedNetexFiles());
    }

    @Test
    void aProviderWithNoExportYetIsSkippedRatherThanFailingTheMerge() throws IOException {
        uploadProviderExport(RUT_EXPORT);
        uploadStopPlaceExport();

        assertTrue(export().export(request()));

        assertEquals(Set.of("WF739.xml", "_stops.xml"), mergedArchiveEntries());
    }

    @Test
    void aMissingStopPlaceExportFailsTheExportAndProducesNoArchive() throws IOException {
        uploadProviderExport(RUT_EXPORT);

        assertFalse(export().export(request()), "the caller must abort the graph build");

        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.FAILED),
                reportedEvents().stream().map(JobEvent::getState).toList());
        assertFalse(repository.exist(MERGED_BLOB));
    }

    @Test
    void theWorkingDirectoryIsRemovedAfterAFailedExport() throws IOException {
        // Unpacked, the providers' exports and the stop place registry come to more than the pod's disk.
        uploadProviderExport(RUT_EXPORT);

        export().export(request());

        assertTrue(childrenOf(workingDirectory).isEmpty(),
                "the local working directory was left behind: " + childrenOf(workingDirectory));
    }

    @Test
    void theWorkingDirectoryIsRemovedAfterASuccessfulExport() throws IOException {
        uploadProviderExport(RUT_EXPORT);
        uploadStopPlaceExport();

        export().export(request());

        assertTrue(childrenOf(workingDirectory).isEmpty(),
                "the local working directory was left behind: " + childrenOf(workingDirectory));
    }

    private static List<Path> childrenOf(Path directory) throws IOException {
        try (Stream<Path> children = Files.list(directory)) {
            return children.toList();
        }
    }
}
