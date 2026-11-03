package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.netex.NetexDsjConverterTest.NETEX_1_15_FIXTURE;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.NETEX_1_16_ENTRY;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.OLD_NETEX_ENTRY;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.README_ENTRY;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.assertUpgraded;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.mixedArchiveEntries;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.unzip;
import static no.rutebanken.marduk.netex.NetexDsjConverterTest.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NetexDsjUpgradeTest {

    private static final String NETEX_1_15_ENTRY = "VYG_netex-1.15.xml";

    @TempDir
    Path workingDirectory;

    private InMemoryMardukBlobStoreRepository internalRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private NetexDsjUpgrade upgrade;

    private byte[] datasetWithFilesToUpgrade;
    private byte[] datasetWithoutFilesToUpgrade;

    @BeforeEach
    void setUp() throws IOException {
        internalRepository = new InMemoryMardukBlobStoreRepository(new ConcurrentHashMap<>());
        internalRepository.setContainerName("internal");
        publisher = new RecordingPubSubPublisher();
        providerRepository = mock(ProviderRepository.class);
        upgrade = upgradeWith(NetexDsjTestConfig.create(workingDirectory));

        // mixedArchiveEntries() holds a NeTEx 1.16 file, a NeTEx file declaring an older version and a non-XML file
        Map<String, byte[]> entries = new LinkedHashMap<>(mixedArchiveEntries());
        Map<String, byte[]> upToDateEntries = new LinkedHashMap<>(entries);
        upToDateEntries.remove(OLD_NETEX_ENTRY);
        datasetWithoutFilesToUpgrade = zip(upToDateEntries);
        entries.put(NETEX_1_15_ENTRY, Files.readAllBytes(NETEX_1_15_FIXTURE));
        datasetWithFilesToUpgrade = zip(entries);
    }

    private NetexDsjUpgrade upgradeWith(NetexDsjExportConfig config) {
        return new NetexDsjUpgrade(config, new MardukInternalBlobStoreService("internal", internalRepository),
                providerRepository, new JobEventPublisher(publisher));
    }

    private static MardukMessage message(String referential, String fileHandle, FileType fileType) {
        return new MardukMessage()
                .setHeader(Constants.PROVIDER_ID, TestConstants.PROVIDER_ID_RB_RUT)
                .setHeader(Constants.CHOUETTE_REFERENTIAL, referential)
                .setHeader(Constants.CORRELATION_ID, "corr-upgrade")
                .setHeader(Constants.FILE_HANDLE, fileHandle)
                .setHeader(Constants.FILE_TYPE, fileType.name());
    }

    private void store(String path, byte[] bytes) {
        internalRepository.uploadBlob(path, new ByteArrayInputStream(bytes));
    }

    @Test
    void uploadedFilesOlderThanNetex116AreUpgradedInPlace() throws IOException {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        store(fileHandle, datasetWithFilesToUpgrade);
        MardukMessage message = message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE);

        upgrade.upgradeIfNeeded(message);

        assertThat(message.getHeader(Constants.FILE_HANDLE)).isEqualTo(fileHandle);
        Map<String, byte[]> upgraded = unzip(internalBlob(fileHandle));
        assertThat(upgraded.keySet()).containsExactly(NETEX_1_16_ENTRY, OLD_NETEX_ENTRY, README_ENTRY, NETEX_1_15_ENTRY);
        assertUpgraded(new String(upgraded.get(NETEX_1_15_ENTRY), StandardCharsets.UTF_8));
        // a file declaring a version older than 1.15 is upgraded as well
        assertThat(new String(upgraded.get(OLD_NETEX_ENTRY), StandardCharsets.UTF_8)).contains("version=\"1.16\"");
        assertThat(new String(upgraded.get(NETEX_1_16_ENTRY), StandardCharsets.UTF_8)).contains("version=\"1.16:");
        // the dataset as uploaded is kept next to the upgraded one
        assertThat(internalBlob("inbound/received/rb_rut/netex-original-v115.zip")).isEqualTo(datasetWithFilesToUpgrade);
    }

    /**
     * The generation of the stored file changes with the upgrade, and the import copies exactly that version to
     * Nisaba later.
     */
    @Test
    void theFileVersionOfTheReplacedDatasetIsRecordedOnTheMessage() {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        store(fileHandle, datasetWithFilesToUpgrade);
        MardukMessage message = message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE)
                .setHeader(Constants.FILE_VERSION, 1L);

        upgrade.upgradeIfNeeded(message);

        assertThat(message.getHeader(Constants.FILE_VERSION, Long.class)).isNotNull();
    }

    /**
     * The backup is the dataset currently stored, not the first one ever uploaded: a later upload replaces it.
     */
    @Test
    void aNewUploadReplacesTheBackupOfThePreviousOne() throws IOException {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        String backupHandle = "inbound/received/rb_rut/netex-original-v115.zip";
        store(fileHandle, datasetWithFilesToUpgrade);
        upgrade.upgradeIfNeeded(message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE));
        assertThat(internalBlob(backupHandle)).isEqualTo(datasetWithFilesToUpgrade);

        byte[] secondUpload = zip(Map.of(NETEX_1_15_ENTRY, Files.readAllBytes(NETEX_1_15_FIXTURE)));
        store(fileHandle, secondUpload);

        upgrade.upgradeIfNeeded(message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE));

        assertThat(internalBlob(backupHandle)).isEqualTo(secondUpload);
        assertUpgraded(new String(unzip(internalBlob(fileHandle)).get(NETEX_1_15_ENTRY), StandardCharsets.UTF_8));
    }

    /**
     * The nightly validation reuses a stored dataset without classifying it, so its message carries no file type.
     */
    @Test
    void unclassifiedDatasetsAreUpgraded() throws IOException {
        String fileHandle = "inbound/received/rb_rut/netex-unclassified.zip";
        store(fileHandle, datasetWithFilesToUpgrade);
        MardukMessage message = message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE)
                .removeHeader(Constants.FILE_TYPE);

        upgrade.upgradeIfNeeded(message);

        Map<String, byte[]> upgraded = unzip(internalBlob(fileHandle));
        assertUpgraded(new String(upgraded.get(NETEX_1_15_ENTRY), StandardCharsets.UTF_8));
    }

    @Test
    void theReferentialIsTakenFromTheProviderWhenTheMessageHasNone() throws IOException {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        store(fileHandle, datasetWithFilesToUpgrade);
        when(providerRepository.getReferential(TestConstants.PROVIDER_ID_RB_RUT)).thenReturn("rb_rut");
        MardukMessage message = message(null, fileHandle, FileType.NETEXPROFILE);
        message.removeHeader(Constants.CHOUETTE_REFERENTIAL);

        upgrade.upgradeIfNeeded(message);

        assertUpgraded(new String(unzip(internalBlob(fileHandle)).get(NETEX_1_15_ENTRY), StandardCharsets.UTF_8));
    }

    @Test
    void datasetsWithoutFilesOlderThanNetex116AreKeptAsIs() throws IOException {
        String fileHandle = "inbound/received/rb_rut/netex-1.16.zip";
        store(fileHandle, datasetWithoutFilesToUpgrade);
        MardukMessage message = message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE);

        upgrade.upgradeIfNeeded(message);

        assertThat(internalBlob(fileHandle)).isEqualTo(datasetWithoutFilesToUpgrade);
        // nothing was converted, so the stored dataset was not rewritten and keeps its generation
        assertThat(message.getHeader(Constants.FILE_VERSION)).isNull();
        assertNoBackup("inbound/received/rb_rut/netex-1.16-original-v115.zip");
    }

    @Test
    void datasetsOfOtherCodespacesAreKeptAsIs() throws IOException {
        String fileHandle = "inbound/received/rb_atb/netex.zip";
        store(fileHandle, datasetWithFilesToUpgrade);

        upgrade.upgradeIfNeeded(message("rb_atb", fileHandle, FileType.NETEXPROFILE));

        assertThat(internalBlob(fileHandle)).isEqualTo(datasetWithFilesToUpgrade);
        assertNoBackup("inbound/received/rb_atb/netex-original-v115.zip");
    }

    @Test
    void nonNetexFilesAreKeptAsIs() throws IOException {
        String fileHandle = "inbound/received/rb_rut/gtfs.zip";
        store(fileHandle, datasetWithFilesToUpgrade);

        upgrade.upgradeIfNeeded(message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.GTFS));

        assertThat(internalBlob(fileHandle)).isEqualTo(datasetWithFilesToUpgrade);
        assertNoBackup("inbound/received/rb_rut/gtfs-original-v115.zip");
    }

    @Test
    void nothingIsUpgradedWhenTheUpgradeIsDisabled() throws IOException {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        store(fileHandle, datasetWithFilesToUpgrade);
        upgrade = upgradeWith(NetexDsjTestConfig.create(workingDirectory, true, false));

        upgrade.upgradeIfNeeded(message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE));

        assertThat(internalBlob(fileHandle)).isEqualTo(datasetWithFilesToUpgrade);
    }

    @Test
    void theWorkingDirectoryIsRemoved() throws IOException {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        store(fileHandle, datasetWithFilesToUpgrade);

        upgrade.upgradeIfNeeded(message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE));

        try (var children = Files.list(workingDirectory)) {
            assertThat(children.toList()).isEmpty();
        }
    }

    /**
     * A dataset that cannot be upgraded must not be validated as it is: the nightly run of that provider fails and
     * reports a failed pre-validation instead.
     */
    @Test
    void aFailedUpgradeBeforeTheNightlyValidationIsReportedAndRethrown() {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        store(fileHandle, "not a zip archive".getBytes(StandardCharsets.UTF_8));
        MardukMessage message = message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE)
                .removeHeader(Constants.FILE_TYPE);

        assertThatThrownBy(() -> upgrade.upgradeForNightlyValidation(message)).isInstanceOf(RuntimeException.class);

        var reported = publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE);
        assertThat(reported).hasSize(1);
        assertThat(reported.getFirst().body()).contains("PREVALIDATION").contains("FAILED");
    }

    @Test
    void aSuccessfulUpgradeBeforeTheNightlyValidationReportsNothing() {
        String fileHandle = "inbound/received/rb_rut/netex.zip";
        store(fileHandle, datasetWithFilesToUpgrade);

        upgrade.upgradeForNightlyValidation(message(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, fileHandle, FileType.NETEXPROFILE)
                .removeHeader(Constants.FILE_TYPE));

        assertThat(publisher.published()).isEmpty();
    }

    /** A dataset that is not upgraded is not overwritten either, so there is nothing to keep a copy of. */
    private void assertNoBackup(String backupPath) {
        assertThat(internalRepository.getBlob(backupPath)).as("Unexpected backup %s", backupPath).isNull();
    }

    private byte[] internalBlob(String path) throws IOException {
        InputStream blob = internalRepository.getBlob(path);
        assertThat(blob).as("Expected internal blob %s", path).isNotNull();
        return blob.readAllBytes();
    }
}
