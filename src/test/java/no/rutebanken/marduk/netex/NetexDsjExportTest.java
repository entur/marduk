package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.netex.NetexDsjConverterTest.NETEX_1_16_ENTRY;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.OLD_NETEX_ENTRY;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.README_ENTRY;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.assertDowngraded;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.mixedArchiveEntries;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.unzip;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetexDsjExportTest {

    private static final String NISABA_CONTAINER = "nisaba";

    @TempDir
    Path workingDirectory;

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private InMemoryMardukBlobStoreRepository publicRepository;
    private InMemoryMardukBlobStoreRepository internalRepository;
    private InMemoryMardukBlobStoreRepository nisabaRepository;
    private RecordingPubSubPublisher publisher;
    private NetexDsjExportConfig config;
    private NetexDsjExport export;

    private Map<String, byte[]> sourceEntries;
    private byte[] sourceArchive;

    @BeforeEach
    void setUp() throws IOException {
        publicRepository = new InMemoryMardukBlobStoreRepository(buckets);
        publicRepository.setContainerName("public");
        internalRepository = new InMemoryMardukBlobStoreRepository(buckets);
        internalRepository.setContainerName("internal");
        nisabaRepository = new InMemoryMardukBlobStoreRepository(buckets);
        nisabaRepository.setContainerName(NISABA_CONTAINER);
        publisher = new RecordingPubSubPublisher();
        config = NetexDsjTestConfig.create(workingDirectory);
        export = new NetexDsjExport(config,
                new MardukPublicBlobStoreService("public", publicRepository),
                new MardukInternalBlobStoreService("internal", internalRepository),
                new JobEventPublisher(publisher));
        sourceEntries = mixedArchiveEntries();
        sourceArchive = zip(sourceEntries);
    }

    private static MardukMessage message(Long providerId, String referential, String correlationId) {
        return new MardukMessage()
                .setHeader(Constants.PROVIDER_ID, providerId)
                .setHeader(Constants.CHOUETTE_REFERENTIAL, referential)
                .setHeader(Constants.CORRELATION_ID, correlationId);
    }

    private static MardukMessage rut(String correlationId) {
        return message(TestConstants.PROVIDER_ID_RB_RUT, TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, correlationId);
    }

    private static Provider provider(Long id, String referential) {
        return new Provider().setId(id).setChouetteInfo(new ChouetteInfo().setReferential(referential));
    }

    private static InputStream stream(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static byte[] notAZip() {
        return "not a zip archive".getBytes(StandardCharsets.UTF_8);
    }

    private List<JobEvent> events() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(published -> JobEvent.fromString(published.body()))
                .toList();
    }

    private List<JobEvent.State> jobStates() {
        return events().stream().map(JobEvent::getState).toList();
    }

    private byte[] blob(InMemoryMardukBlobStoreRepository repository, String path) throws IOException {
        InputStream blob = repository.getBlob(path);
        assertThat(blob).as("Expected blob %s in %s", path, repository).isNotNull();
        return blob.readAllBytes();
    }

    private void assertThreeVariants(String referential) throws IOException {
        // the new variant is the export as published
        assertThat(blob(publicRepository, config.newExportPath(referential))).isEqualTo(sourceArchive);

        // the legacy variant contains the downgraded NeTEx 1.16 file and the other files unchanged
        byte[] legacyArchive = blob(publicRepository, config.legacyExportPath(referential));
        Map<String, byte[]> legacyEntries = unzip(legacyArchive);
        assertThat(legacyEntries.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY);
        assertDowngraded(new String(legacyEntries.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(legacyEntries.get(OLD_NETEX_ENTRY)).isEqualTo(sourceEntries.get(OLD_NETEX_ENTRY));
        assertThat(legacyEntries.get(README_ENTRY)).isEqualTo(sourceEntries.get(README_ENTRY));

        // the default folder receives the legacy variant (netex.export.dsj.default.variant=legacy in the test configuration)
        assertThat(config.getDefaultVariant()).isEqualTo(NetexDsjExportConfig.Variant.LEGACY);
        assertThat(blob(publicRepository, config.defaultExportPath(referential))).isEqualTo(legacyArchive);
    }

    @Test
    void distributeExportStoredInTheNewFolder() throws IOException {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        publicRepository.uploadBlob(config.newExportPath(referential), stream(sourceArchive));

        export.distributeExport(rut("corr-1"));

        assertThat(jobStates()).containsExactly(JobEvent.State.STARTED, JobEvent.State.OK);
        assertThat(events().getFirst().getAction()).isEqualTo(JobEvent.TimetableAction.EXPORT_NETEX_LEGACY.name());
        assertThreeVariants(referential);
    }

    @Test
    void distributeLeavesNoWorkingDirectoryBehind() throws IOException {
        publicRepository.uploadBlob(config.newExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT), stream(sourceArchive));

        export.distributeExport(rut("corr-clean"));

        try (var children = java.nio.file.Files.list(workingDirectory)) {
            assertThat(children.toList()).isEmpty();
        }
    }

    @Test
    void distributeReportsTheSystemAsTheUserWhenNoUserTriggeredIt() {
        publicRepository.uploadBlob(config.newExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT), stream(sourceArchive));

        export.distributeExport(rut("corr-system"));

        assertThat(events()).extracting(JobEvent::getUsername).containsExactly("System", "System");
    }

    @Test
    void distributeReportsTheUserThatTriggeredIt() {
        publicRepository.uploadBlob(config.newExportPath(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT), stream(sourceArchive));

        export.distributeExport(rut("corr-user").setHeader(Constants.USERNAME, "john.doe"));

        assertThat(events()).extracting(JobEvent::getUsername).containsExactly("john.doe", "john.doe");
    }

    @Test
    void distributeSeedsTheNewFolderFromTheDefaultFolder() throws IOException {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        publicRepository.uploadBlob(config.defaultExportPath(referential), stream(sourceArchive));

        export.distributeExport(rut("corr-2"));

        assertThreeVariants(referential);
    }

    @Test
    void distributeFailsWhenTheProviderHasNoExport() {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;

        assertThatThrownBy(() -> export.distributeExport(rut("corr-3")))
                .isInstanceOf(MardukException.class)
                .hasMessage("No NeTEx export found for the provider");

        assertThat(jobStates()).containsExactly(JobEvent.State.STARTED, JobEvent.State.FAILED);
        assertThat(publicRepository.getBlob(config.legacyExportPath(referential))).isNull();
        assertThat(publicRepository.getBlob(config.defaultExportPath(referential))).isNull();
    }

    @Test
    void distributeBlocksExportStoresALegacyCopy() throws IOException {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        internalRepository.uploadBlob(config.blocksExportPath(referential), stream(sourceArchive));
        // the antu consumer carries the serialised job event in the body
        MardukMessage message = rut("corr-4")
                .setHeader(Constants.FILE_HANDLE, "some/validated/file.zip")
                .setBody("the body of the caller");

        export.distributeBlocksExport(message);

        assertThat(message.getHeader(Constants.FILE_HANDLE)).as("FILE_HANDLE is untouched for the caller")
                .isEqualTo("some/validated/file.zip");
        assertThat(message.getBody(String.class)).as("the body is restored for the caller")
                .isEqualTo("the body of the caller");
        // the export produced by the pipeline is untouched
        assertThat(blob(internalRepository, config.blocksExportPath(referential))).isEqualTo(sourceArchive);
        Map<String, byte[]> legacyEntries = unzip(blob(internalRepository, config.legacyBlocksExportPath(referential)));
        assertThat(legacyEntries.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY);
        assertDowngraded(new String(legacyEntries.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(legacyEntries.get(OLD_NETEX_ENTRY)).isEqualTo(sourceEntries.get(OLD_NETEX_ENTRY));
    }

    @Test
    void distributeBlocksExportIsANoOpWhenTheDualExportIsDisabled() {
        config = NetexDsjTestConfig.create(workingDirectory, false, true);
        export = new NetexDsjExport(config,
                new MardukPublicBlobStoreService("public", publicRepository),
                new MardukInternalBlobStoreService("internal", internalRepository),
                new JobEventPublisher(publisher));
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        internalRepository.uploadBlob(config.blocksExportPath(referential), stream(sourceArchive));

        export.distributeBlocksExport(rut("corr-off"));

        assertThat(internalRepository.getBlob(config.legacyBlocksExportPath(referential))).isNull();
    }

    /**
     * The legacy copy is overwritten in place and the timetable API cannot tell a stale one from a current one, so a
     * failed downgrade must fail the publication instead of leaving the previous copy next to a freshly published
     * blocks export.
     */
    @Test
    void distributeBlocksExportFailsInsteadOfLeavingAStaleLegacyCopy() throws IOException {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        byte[] staleLegacyCopy = "the legacy copy of a previous run".getBytes(StandardCharsets.UTF_8);
        internalRepository.uploadBlob(config.legacyBlocksExportPath(referential), stream(staleLegacyCopy));
        internalRepository.uploadBlob(config.blocksExportPath(referential), stream(notAZip()));

        assertThatThrownBy(() -> export.distributeBlocksExport(rut("corr-blocks-fail")))
                .as("the failure is propagated so that the publication is retried")
                .isInstanceOf(RuntimeException.class);

        assertThat(blob(internalRepository, config.legacyBlocksExportPath(referential)))
                .as("the stale legacy copy is still there, which is why the publication must not be reported as successful")
                .isEqualTo(staleLegacyCopy);
    }

    /**
     * The manual backfill distributes every provider in one run, so it tolerates a provider whose export cannot be
     * downgraded instead of stopping at the first one.
     */
    @Test
    void theBackfillToleratesAProviderWhoseBlocksExportCannotBeDowngraded() {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        internalRepository.uploadBlob(config.blocksExportPath(referential), stream(notAZip()));

        export.distributeAllExports(rut("corr-blocks-backfill"),
                List.of(provider(TestConstants.PROVIDER_ID_RB_RUT, referential)));

        assertThat(events()).as("a batch reports no provider job events").isEmpty();
    }

    /**
     * The legacy folder and the default folder are written by the distribution itself; the caller stores the
     * dataset as uploaded in the new folder.
     */
    @Test
    void distributeOriginalDatasetToNisaba() throws IOException {
        String originalFileHandle = "inbound/received/rb_rut/original.zip";
        String nisabaFileHandle = "imported/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip";
        internalRepository.uploadBlob(originalFileHandle, stream(sourceArchive));
        MardukMessage message = rut("corr-5")
                .setHeader(Constants.FILE_HANDLE, originalFileHandle)
                .setHeader(Constants.TARGET_FILE_HANDLE, nisabaFileHandle)
                .setHeader(Constants.TARGET_CONTAINER, NISABA_CONTAINER)
                .setBody("the body of the caller");

        export.distributeOriginalDatasetToNisaba(message);

        assertThat(message.getHeader(Constants.FILE_HANDLE)).isEqualTo(originalFileHandle);
        assertThat(message.getHeader(Constants.TARGET_FILE_HANDLE)).isEqualTo(nisabaFileHandle);
        assertThat(message.getBody(String.class)).isEqualTo("the body of the caller");
        assertThat(config.originalDatasetPublicationPath(nisabaFileHandle))
                .isEqualTo("imported-dsj-new/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip");

        byte[] legacyBlob = blob(nisabaRepository, "imported-dsj-legacy/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip");
        Map<String, byte[]> legacyEntries = unzip(legacyBlob);
        assertDowngraded(new String(legacyEntries.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8));
        assertThat(legacyEntries.get(OLD_NETEX_ENTRY)).isEqualTo(sourceEntries.get(OLD_NETEX_ENTRY));

        // the default folder receives the legacy variant (netex.export.dsj.default.variant=legacy in the test configuration)
        assertThat(config.getDefaultVariant()).isEqualTo(NetexDsjExportConfig.Variant.LEGACY);
        assertThat(blob(nisabaRepository, nisabaFileHandle)).isEqualTo(legacyBlob);
    }

    /**
     * Datasets of codespaces without DatedServiceJourney replacement information are not downgraded: the legacy
     * variants are plain copies.
     */
    @Test
    void codespacesWithoutReplacementInformationAreCopiedInsteadOfDowngraded() throws IOException {
        assertThat(config.hasDsjReplacements("rb_vyg")).isTrue();
        assertThat(config.hasDsjReplacements("VYG")).isTrue();
        assertThat(config.hasDsjReplacements("rb_atb")).isFalse();

        String referential = "rb_atb";
        publicRepository.uploadBlob(config.newExportPath(referential), stream(sourceArchive));
        internalRepository.uploadBlob(config.blocksExportPath(referential), stream(sourceArchive));

        export.distributeExport(message(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-6"));
        export.distributeBlocksExport(message(TestConstants.PROVIDER_ID_RB_RUT, referential, "corr-6"));

        assertThat(blob(publicRepository, config.legacyExportPath(referential))).isEqualTo(sourceArchive);
        assertThat(blob(publicRepository, config.defaultExportPath(referential))).isEqualTo(sourceArchive);
        assertThat(blob(internalRepository, config.legacyBlocksExportPath(referential))).isEqualTo(sourceArchive);
    }

    /**
     * The default folder of Nisaba is written by this distribution only: a failure must fail the calling step so
     * that it is retried, instead of leaving the import without an original dataset in the default folder.
     */
    @Test
    void distributeOriginalDatasetToNisabaFailsWhenTheDatasetCannotBeDowngraded() {
        String originalFileHandle = "inbound/received/rb_rut/original.zip";
        String nisabaFileHandle = "imported/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip";
        internalRepository.uploadBlob(originalFileHandle, stream(notAZip()));
        MardukMessage message = rut("corr-9")
                .setHeader(Constants.FILE_HANDLE, originalFileHandle)
                .setHeader(Constants.TARGET_FILE_HANDLE, nisabaFileHandle)
                .setHeader(Constants.TARGET_CONTAINER, NISABA_CONTAINER);

        assertThatThrownBy(() -> export.distributeOriginalDatasetToNisaba(message))
                .isInstanceOf(RuntimeException.class);

        assertThat(nisabaRepository.getBlob(nisabaFileHandle)).isNull();
        assertThat(nisabaRepository.getBlob("imported-dsj-legacy/rb_rut/rb_rut_2026-09-10T10_00_00.000.zip")).isNull();
    }

    /**
     * A provider that has never published has no export in any folder: the backfill skips it instead of reporting a
     * failed export.
     */
    @Test
    void providersWithoutAnyExportAreSkippedByTheBackfill() {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;

        export.distributeAllExports(rut("corr-8"), List.of(provider(TestConstants.PROVIDER_ID_RB_RUT, referential)));

        assertThat(events()).isEmpty();
        assertThat(publicRepository.getBlob(config.legacyExportPath(referential))).isNull();
    }

    /**
     * A provider that has not published since the dual export was enabled still has its export in the default folder:
     * the backfill distributes it from there.
     */
    @Test
    void providersWithAnExportInTheDefaultFolderAreDistributedByTheBackfill() throws IOException {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        publicRepository.uploadBlob(config.defaultExportPath(referential), stream(sourceArchive));

        export.distributeAllExports(rut("corr-9"), List.of(provider(TestConstants.PROVIDER_ID_RB_RUT, referential)));

        assertThreeVariants(referential);
    }

    /**
     * A batch distributes every provider under the correlation id of the batch, so Nabu, which aggregates job events
     * by correlation id, would merge them into one job attributed to whichever provider was distributed first. The
     * batch reports nothing instead; the distribution itself is unaffected.
     */
    @Test
    void theBatchDistributesWithoutReportingProviderJobEvents() throws IOException {
        String referential = TestConstants.CHOUETTE_REFERENTIAL_RB_RUT;
        publicRepository.uploadBlob(config.newExportPath(referential), stream(sourceArchive));

        export.distributeAllExports(rut("corr-batch"), List.of(provider(TestConstants.PROVIDER_ID_RB_RUT, referential)));

        assertThat(events()).isEmpty();
        assertThreeVariants(referential);
    }

    @Test
    void anIgnoringBatchGoesOnAfterAProviderThatCannotBeDowngraded() throws IOException {
        // The first provider cannot be downgraded; the batch still distributes the second.
        publicRepository.uploadBlob(config.newExportPath("rb_rut"), stream(sourceArchive));
        publicRepository.uploadBlob(config.newExportPath("rb_vyg"), stream(notAZip()));

        // an ignoring batch swallows the failure of one provider and goes on
        export.distributeAllExports(rut("corr-batch-failure"),
                List.of(provider(1L, "rb_vyg"), provider(2L, "rb_rut")));

        assertThat(blob(publicRepository, config.legacyExportPath("rb_rut"))).isNotNull();
        assertThat(publicRepository.getBlob(config.legacyExportPath("rb_vyg"))).isNull();
    }

    /**
     * A failure the backfill cannot swallow still reaches the caller, after every provider was tried, so that the
     * aggregated export is not built from folders that are not filled. The batch reports no job event for it.
     */
    @Test
    void aFailureTheBackfillCannotSwallowIsRethrownAfterTheOtherProvidersAndReportsNoJobEvent() throws IOException {
        publicRepository.uploadBlob(config.newExportPath("rb_vyg"), stream(sourceArchive));
        publicRepository.uploadBlob(config.newExportPath("rb_vyg") + ".bak", stream(sourceArchive));
        publicRepository.uploadBlob(config.newExportPath("rb_rut"), stream(sourceArchive));

        assertThatThrownBy(() -> export.distributeAllExports(rut("corr-batch-rethrow"),
                List.of(provider(1L, "rb_vyg"), provider(2L, "rb_rut"))))
                .isInstanceOf(MardukException.class)
                .hasMessageContaining("Found multiple files matching the prefix");

        assertThat(blob(publicRepository, config.legacyExportPath("rb_rut"))).isNotNull();
        assertThat(events()).as("a failing batch reports no provider job events").isEmpty();
    }

    /**
     * Providers that have not published since the dual export was enabled are distributed before the aggregated
     * exports are built; the ones that are complete are left alone, and a failure fails the aggregated export
     * rather than losing the provider.
     */
    @Test
    void theMissingExportsAreDistributedAndAFailureIsNotSwallowed() throws IOException {
        publicRepository.uploadBlob(config.defaultExportPath("rb_rut"), stream(sourceArchive));
        // complete in both folders already
        publicRepository.uploadBlob(config.newExportPath("rb_akt"), stream(sourceArchive));
        publicRepository.uploadBlob(config.legacyExportPath("rb_akt"), stream(sourceArchive));

        export.distributeMissingExports(new MardukMessage(),
                List.of(provider(1L, "rb_rut"), provider(2L, "rb_akt")));

        assertThat(blob(publicRepository, config.newExportPath("rb_rut"))).isEqualTo(sourceArchive);
        assertThat(blob(publicRepository, config.legacyExportPath("rb_rut"))).isNotNull();
        assertThat(events()).as("the distribution of missing exports adds no job event of its own").isEmpty();

        publicRepository.uploadBlob(config.newExportPath("rb_vyg"), stream(notAZip()));
        assertThatThrownBy(() -> export.distributeMissingExports(new MardukMessage(), List.of(provider(3L, "rb_vyg"))))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void severalFilesMatchingTheExportOfAProviderIsAnError() {
        publicRepository.uploadBlob(config.newExportPath("rb_rut"), stream(sourceArchive));
        publicRepository.uploadBlob(config.newExportPath("rb_rut") + ".bak", stream(sourceArchive));

        assertThatThrownBy(() -> export.distributeMissingExports(new MardukMessage(), List.of(provider(1L, "rb_rut"))))
                .isInstanceOf(MardukException.class)
                .hasMessageContaining("Found multiple files matching the prefix");
    }
}
