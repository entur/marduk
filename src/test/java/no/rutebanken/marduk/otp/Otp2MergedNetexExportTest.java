package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.netex.NetexDsjConverter;
import no.rutebanken.marduk.netex.NetexDsjConverterTest;
import no.rutebanken.marduk.netex.NetexDsjExport;
import no.rutebanken.marduk.netex.NetexDsjExportConfig;
import no.rutebanken.marduk.netex.NetexDsjTestConfig;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        return exportWith(NetexDsjTestConfig.disabled());
    }

    /** As the test configuration has it: both variants built from the exports of the DSJ folders. */
    private Otp2MergedNetexExport dsjExport() {
        return exportWith(NetexDsjTestConfig.create(workingDirectory.resolve("dsj")));
    }

    private Otp2MergedNetexExport exportWith(NetexDsjExportConfig dsjConfig) {
        JobEventPublisher jobEvents = new JobEventPublisher(publisher);
        return new Otp2MergedNetexExport(
                blobStore,
                providerRepository,
                jobEvents,
                new NetexDsjExport(dsjConfig, blobStore,
                        new MardukInternalBlobStoreService("internal", new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>())),
                        jobEvents),
                dsjConfig,
                workingDirectory.resolve("work").toString(),
                STOPS_BLOB,
                "netex/rb_norway-aggregated-netex.zip",
                "_stops",
                false);
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

        assertEquals(List.of("rb_rut"), export().publishedProviders().stream()
                .map(provider -> provider.getChouetteInfo().getReferential()).toList());
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

        assertTrue(childrenOf(workingDirectory.resolve("work")).isEmpty(),
                "the local working directory was left behind: " + childrenOf(workingDirectory.resolve("work")));
    }

    @Test
    void theWorkingDirectoryIsRemovedAfterASuccessfulExport() throws IOException {
        uploadProviderExport(RUT_EXPORT);
        uploadStopPlaceExport();

        export().export(request());

        assertTrue(childrenOf(workingDirectory.resolve("work")).isEmpty(),
                "the local working directory was left behind: " + childrenOf(workingDirectory.resolve("work")));
    }

    // ------------------------------------------------------------ dual DatedServiceJourney export

    private static final String NORWAY = "rb_norway-aggregated-netex.zip";

    private byte[] blobBytes(String name) throws IOException {
        try (InputStream blob = repository.getBlob(name)) {
            assertNotNull(blob, "Expected blob " + name);
            return blob.readAllBytes();
        }
    }

    private Map<String, byte[]> blobEntries(String name) throws IOException {
        return NetexDsjConverterTest.unzip(blobBytes(name));
    }

    private static Provider rut() {
        return provider("rb_rut", null);
    }

    /**
     * With the dual DatedServiceJourney export enabled, the aggregated export is built from the new and legacy
     * per-provider exports and the default folder receives the default variant.
     */
    @Test
    void bothVariantsAreBuiltAndTheDefaultFolderReceivesTheDefaultVariant() throws IOException {
        when(providerRepository.getProviders()).thenReturn(List.of(rut()));
        uploadStopPlaceExport();
        // the new variant of the provider export is a NeTEx 1.16 dataset, the legacy variant its downgraded copy
        Map<String, byte[]> newEntries = NetexDsjConverterTest.mixedArchiveEntries();
        ByteArrayOutputStream legacyNetex = new ByteArrayOutputStream();
        NetexDsjConverter.downgradeXml(NetexDsjConverterTest.NETEX_1_16_FIXTURE.toFile(), legacyNetex, Long.MAX_VALUE);
        Map<String, byte[]> legacyEntries = new LinkedHashMap<>(newEntries);
        legacyEntries.put(NetexDsjConverterTest.NETEX_1_16_ENTRY, legacyNetex.toByteArray());
        repository.uploadBlob("outbound/netex-dsj-new/rb_rut-aggregated-netex.zip",
                new ByteArrayInputStream(NetexDsjConverterTest.zip(newEntries)));
        repository.uploadBlob("outbound/netex-dsj-legacy/rb_rut-aggregated-netex.zip",
                new ByteArrayInputStream(NetexDsjConverterTest.zip(legacyEntries)));

        assertTrue(dsjExport().export(request()));

        Map<String, byte[]> newMerged = blobEntries("outbound/netex-dsj-new/" + NORWAY);
        Map<String, byte[]> legacyMerged = blobEntries("outbound/netex-dsj-legacy/" + NORWAY);
        assertTrue(newMerged.keySet().containsAll(Set.of(NetexDsjConverterTest.NETEX_1_16_ENTRY, "_stops.xml")));
        assertTrue(new String(newMerged.get(NetexDsjConverterTest.NETEX_1_16_ENTRY), java.nio.charset.StandardCharsets.UTF_8)
                .contains("version=\"1.16:"));
        assertTrue(legacyMerged.keySet().containsAll(Set.of(NetexDsjConverterTest.NETEX_1_16_ENTRY, "_stops.xml")));
        NetexDsjConverterTest.assertDowngraded(
                new String(legacyMerged.get(NetexDsjConverterTest.NETEX_1_16_ENTRY), java.nio.charset.StandardCharsets.UTF_8));
        // the stop place export contains no DatedServiceJourney and is used as is in both variants
        assertArrayEquals(newMerged.get("_stops.xml"), legacyMerged.get("_stops.xml"));
        // the default folder receives the legacy variant (netex.export.dsj.default.variant=legacy in the test configuration)
        assertArrayEquals(blobBytes("outbound/netex-dsj-legacy/" + NORWAY), blobBytes(MERGED_BLOB));
    }

    /**
     * A provider that has not published since the dual export was enabled exists only in the default folder: its
     * export is distributed to the new and legacy folders before the aggregated exports are built.
     */
    @Test
    void aProviderThatOnlyHasADefaultFolderExportIsDistributedBeforeTheVariantsAreBuilt() throws IOException {
        when(providerRepository.getProviders()).thenReturn(List.of(rut()));
        uploadStopPlaceExport();
        repository.uploadBlob(RUT_EXPORT,
                new ByteArrayInputStream(NetexDsjConverterTest.zip(NetexDsjConverterTest.mixedArchiveEntries())));

        assertTrue(dsjExport().export(request()));

        assertNotNull(repository.getBlob("outbound/netex-dsj-new/rb_rut-aggregated-netex.zip"));
        assertNotNull(repository.getBlob("outbound/netex-dsj-legacy/rb_rut-aggregated-netex.zip"));
        assertTrue(blobEntries("outbound/netex-dsj-new/" + NORWAY).containsKey(NetexDsjConverterTest.NETEX_1_16_ENTRY));
        assertTrue(blobEntries("outbound/netex-dsj-legacy/" + NORWAY).containsKey(NetexDsjConverterTest.NETEX_1_16_ENTRY));
        assertEquals(List.of(JobEvent.State.STARTED, JobEvent.State.OK),
                reportedEvents().stream().map(JobEvent::getState).toList(),
                "the distribution of the missing exports adds no job event of its own");
    }

    /**
     * The distribution of the missing exports and the aggregation must work on the same providers. The provider
     * cache is refreshed every few minutes: a provider that drops out of it after the aggregated export has read
     * the provider list would otherwise be neither distributed nor recorded as missing, and both variants would be
     * built without it while agreeing with each other.
     */
    @Test
    void theProvidersReadAtTheStartAreTheOnesDistributedAndAggregated() throws IOException {
        // the provider is in the cache when the aggregated export starts, and gone from every later reading
        when(providerRepository.getProviders()).thenReturn(List.of(rut()), List.of());
        uploadStopPlaceExport();
        repository.uploadBlob(RUT_EXPORT,
                new ByteArrayInputStream(NetexDsjConverterTest.zip(NetexDsjConverterTest.mixedArchiveEntries())));

        assertTrue(dsjExport().export(request()));

        assertTrue(blobEntries("outbound/netex-dsj-new/" + NORWAY).containsKey(NetexDsjConverterTest.NETEX_1_16_ENTRY));
        assertTrue(blobEntries("outbound/netex-dsj-legacy/" + NORWAY).containsKey(NetexDsjConverterTest.NETEX_1_16_ENTRY));
    }

    /**
     * A per-provider export that cannot be distributed to the legacy and new folders must fail the aggregated
     * export: building it anyway would publish a Norway dataset silently missing that provider, and feed it to the
     * OTP graph build.
     */
    @Test
    void aProviderExportThatCannotBeDistributedFailsTheAggregatedExport() {
        when(providerRepository.getProviders()).thenReturn(List.of(rut()));
        uploadStopPlaceExportQuietly();
        // missing from the legacy folder and not a zip archive, so it cannot be downgraded
        repository.uploadBlob("outbound/netex-dsj-new/rb_rut-aggregated-netex.zip",
                new ByteArrayInputStream("not a zip archive".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertThrows(RuntimeException.class, () -> dsjExport().export(request()));

        assertFalse(repository.exist("outbound/netex-dsj-new/" + NORWAY));
        assertFalse(repository.exist("outbound/netex-dsj-legacy/" + NORWAY));
        assertFalse(repository.exist(MERGED_BLOB));
    }

    /**
     * A per-provider export that is not a zip archive (a truncated upload, a blob with the wrong content) must
     * fail the aggregated export and name the offending export: skipping it would publish a Norway dataset
     * silently missing that provider and feed it to the OTP graph build.
     */
    @Test
    void aCorruptProviderExportFailsTheAggregatedExportAndIsNamed() {
        when(providerRepository.getProviders()).thenReturn(List.of(rut()));
        uploadStopPlaceExportQuietly();
        repository.uploadBlob(RUT_EXPORT,
                new ByteArrayInputStream("not a zip archive".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        MardukException failure = assertThrows(MardukException.class, () -> export().export(request()));

        assertTrue(failure.getMessage().contains("rb_rut-aggregated-netex.zip"), failure.getMessage());
        assertFalse(repository.exist(MERGED_BLOB), "No aggregated export must be published");
    }

    /**
     * Every variant must be built from the exports of the same providers: a provider missing from one folder only
     * would silently disappear from one of the aggregated exports.
     */
    @Test
    void aProviderMissingFromOneVariantFolderOnlyFailsTheAggregatedExport() throws IOException {
        when(providerRepository.getProviders()).thenReturn(List.of(rut()));
        uploadStopPlaceExport();
        // present in the new folder, and the legacy folder is left without it by a distribution that lost it
        repository.uploadBlob("outbound/netex-dsj-new/rb_rut-aggregated-netex.zip",
                new ByteArrayInputStream(NetexDsjConverterTest.zip(NetexDsjConverterTest.mixedArchiveEntries())));
        NetexDsjExportConfig config = NetexDsjTestConfig.create(workingDirectory.resolve("dsj"));
        JobEventPublisher jobEvents = new JobEventPublisher(publisher);
        // a distribution that does nothing, so the legacy folder stays empty
        NetexDsjExport inert = new NetexDsjExport(config, blobStore, null, jobEvents) {
            @Override
            public void distributeMissingExports(MardukMessage message, List<Provider> providers) {
            }
        };
        Otp2MergedNetexExport export = new Otp2MergedNetexExport(blobStore, providerRepository, jobEvents, inert, config,
                workingDirectory.resolve("work").toString(), STOPS_BLOB, "netex/rb_norway-aggregated-netex.zip", "_stops", false);

        MardukException failure = assertThrows(MardukException.class, () -> export.export(request()));

        assertTrue(failure.getMessage().contains("do not cover the same providers"), failure.getMessage());
        assertFalse(repository.exist(MERGED_BLOB));
    }

    private void uploadStopPlaceExportQuietly() {
        try {
            uploadStopPlaceExport();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static List<Path> childrenOf(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(directory)) {
            return children.toList();
        }
    }
}
