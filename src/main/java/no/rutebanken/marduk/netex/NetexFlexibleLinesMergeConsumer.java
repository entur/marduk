package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.experimental.SetProviderIdBeforeFlexMergeProcessor;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.AbstractBlobStoreService;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_VERSION;
import static no.rutebanken.marduk.Constants.FOLDER_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_FLEX_MERGING;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;

/**
 * Merges the NeTEx dataset Chouette exported with the flexible-lines dataset Uttu exported.
 *
 * <p>Both archives are unpacked side by side into one scratch directory and re-zipped as a single dataset.
 * Where that dataset then goes depends on what was actually found: only when <em>both</em> sources
 * contributed does the merge need antu's blessing, and it is uploaded to the validation folder and
 * post-validated. In every other case - no flexible line data for the codespace, or the merge disabled - the
 * result is the Chouette export unchanged, so it goes straight to the outbound bucket and is published.
 *
 * <p>Replaces {@code NetexMergeChouetteWithFlexibleLineExportRouteBuilder} and the eight routes only it
 * called.
 */
@Component
public class NetexFlexibleLinesMergeConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetexFlexibleLinesMergeConsumer.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String BLOBSTORE_PATH_UTTU = "uttu/";

    private final ExperimentalImportHelpers experimentalImportHelpers;
    private final SetProviderIdBeforeFlexMergeProcessor setProviderIdBeforeFlexMerge;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final MardukPublicBlobStoreService publicBlobStore;
    private final ExchangeBlobStoreService exchangeBlobStore;
    private final MergedNetexPublication publication;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final String localWorkingDirectory;
    private final boolean mergeFlexibleLinesEnabled;
    private final String antuExchangeContainer;

    public NetexFlexibleLinesMergeConsumer(
            ExperimentalImportHelpers experimentalImportHelpers,
            SetProviderIdBeforeFlexMergeProcessor setProviderIdBeforeFlexMerge,
            MardukInternalBlobStoreService internalBlobStore,
            MardukPublicBlobStoreService publicBlobStore,
            ExchangeBlobStoreService exchangeBlobStore,
            MergedNetexPublication publication,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${netex.export.download.directory:files/netex/merged}") String localWorkingDirectory,
            @Value("${netex.export.merge.flexible.lines.enabled:false}") boolean mergeFlexibleLinesEnabled,
            @Value("${blobstore.gcs.antu.exchange.container.name}") String antuExchangeContainer) {
        this.experimentalImportHelpers = experimentalImportHelpers;
        this.setProviderIdBeforeFlexMerge = setProviderIdBeforeFlexMerge;
        this.internalBlobStore = internalBlobStore;
        this.publicBlobStore = publicBlobStore;
        this.exchangeBlobStore = exchangeBlobStore;
        this.publication = publication;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.localWorkingDirectory = localWorkingDirectory;
        this.mergeFlexibleLinesEnabled = mergeFlexibleLinesEnabled;
        this.antuExchangeContainer = antuExchangeContainer;
    }

    @Override
    protected String destination() {
        return MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        setCorrelationIdIfMissing(message);
        LOGGER.info("Merging chouette NeTEx export with FlexibleLines");
        if (message.getHeader(CHOUETTE_REFERENTIAL) == null) {
            throw new MardukException("Cannot merge with flexible lines without a Chouette referential");
        }

        setProviderIdBeforeFlexMerge.setProviderIdIfChouetteImport(message);
        if (message.getHeader(PROVIDER_ID) == null) {
            throw new MardukException("Cannot merge with flexible lines without a provider id");
        }

        String workingDirectory = localWorkingDirectory + "/"
                + message.getHeader(CORRELATION_ID, String.class) + "_" + LocalDateTime.now().format(TIMESTAMP);
        message.setProperty(FOLDER_NAME, workingDirectory);
        try {
            boolean hasChouetteData = unpackChouetteExport(message);
            boolean hasFlexibleData = unpackFlexibleLinesExport(message);

            if (hasChouetteData && hasFlexibleData) {
                uploadMergedFileToValidationFolder(message);
                requestMergedNetexPostValidation(message);
            } else {
                uploadMergedFileToOutboundBucket(message);
                publication.publishMergedDataset(message);
            }
        } finally {
            // In a finally so a failed merge does not leave the unpacked dataset behind on the pod's disk.
            deleteDirectoryRecursively(workingDirectory);
        }
    }

    /**
     * @return true if the Chouette export - or, for an experimental codespace, the Ashur output standing in
     *         for it - was found and unpacked
     */
    private boolean unpackChouetteExport(MardukMessage message) {
        String fileHandle = experimentalImportHelpers.pathToExportedNetexFileToMergeWithFlex(message);
        message.setHeader(FILE_HANDLE, fileHandle);
        if (unpackInternalBlobIfPresent(message, fileHandle)) {
            return true;
        }
        if (!experimentalImportHelpers.shouldRunExperimentalImport(message)) {
            LOGGER.info("{} was empty when trying to fetch it from blobstore.", fileHandle);
            return false;
        }
        // Only for experimental codespaces, where the merge can be triggered from a different correlation
        // (e.g. FLEX post-validation) and the correlation-keyed path therefore points to no file.
        String fallbackHandle = experimentalImportHelpers.pathToLatestNetexWithoutBlocksFromAshur(message);
        message.setHeader(FILE_HANDLE, fallbackHandle);
        if (unpackInternalBlobIfPresent(message, fallbackHandle)) {
            return true;
        }
        LOGGER.info("{} fallback was empty when trying to fetch latest Ashur output from blobstore.",
                fallbackHandle);
        return false;
    }

    /** @return true if the flexible lines export was found and unpacked */
    private boolean unpackFlexibleLinesExport(MardukMessage message) {
        if (!mergeFlexibleLinesEnabled) {
            LOGGER.info("Skipping merge with flexible lines as this is disabled.");
            return false;
        }
        String fileHandle = BLOBSTORE_PATH_OUTBOUND + "netex/" + message.getHeader(CHOUETTE_REFERENTIAL, String.class)
                + "-" + Constants.CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;
        message.setHeader(FILE_HANDLE, fileHandle);
        try (InputStream archive = exchangeBlobStore.getBlob(fileHandle)) {
            if (archive == null) {
                LOGGER.info("No flexible line data found: {} was empty when trying to fetch it from blobstore.",
                        fileHandle);
                return false;
            }
            ZipFileUtils.unzipFile(archive, experimentalImportHelpers.flexibleDataWorkingDirectory(message));
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not unpack " + fileHandle, e);
        }
    }

    private boolean unpackInternalBlobIfPresent(MardukMessage message, String fileHandle) {
        try (InputStream archive = internalBlobStore.getBlob(fileHandle)) {
            if (archive == null) {
                return false;
            }
            ZipFileUtils.unzipFile(archive, experimentalImportHelpers.flexibleDataWorkingDirectory(message));
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not unpack " + fileHandle, e);
        }
    }

    private void uploadMergedFileToOutboundBucket(MardukMessage message) {
        Path merged = zipTheWorkingFolder(message);
        String fileHandle = BLOBSTORE_PATH_OUTBOUND + "netex/"
                + message.getHeader(CHOUETTE_REFERENTIAL, String.class) + "-"
                + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        message.setHeader(FILE_HANDLE, fileHandle);
        store(message, merged, fileHandle, publicBlobStore);
    }

    private void uploadMergedFileToValidationFolder(MardukMessage message) {
        Path merged = zipTheWorkingFolder(message);
        String fileHandle = BLOBSTORE_PATH_UTTU + "netex/" + message.getHeader(CHOUETTE_REFERENTIAL, String.class)
                + "/" + message.getHeader(CORRELATION_ID, String.class) + "_"
                + LocalDateTime.now().format(TIMESTAMP) + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        message.setHeader(FILE_HANDLE, fileHandle);
        store(message, merged, fileHandle, internalBlobStore);
    }

    private Path zipTheWorkingFolder(MardukMessage message) {
        String resultDirectory = experimentalImportHelpers.directoryForMergedNetex(message);
        new File(resultDirectory).mkdir();
        return ZipFileUtils.zipFilesInFolder(
                experimentalImportHelpers.flexibleDataWorkingDirectory(message),
                resultDirectory + "/merged.zip").toPath();
    }

    /** Streamed off disk: a merged dataset is far larger than the pod's heap. */
    private static void store(
            MardukMessage message, Path merged, String fileHandle, AbstractBlobStoreService blobStore) {
        try (InputStream archive = Files.newInputStream(merged)) {
            message.setHeader(FILE_VERSION, blobStore.uploadBlob(fileHandle, archive));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the merged dataset as " + fileHandle, e);
        }
        message.setBody("");
    }

    private void requestMergedNetexPostValidation(MardukMessage message) {
        LOGGER.info("validating Merged NeTEx dataset");
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        message.setHeader(TARGET_CONTAINER, antuExchangeContainer);
        message.setHeader(TARGET_FILE_HANDLE, fileHandle);
        internalBlobStore.copyBlobToAnotherBucket(fileHandle, antuExchangeContainer, fileHandle);

        message.setHeader(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION);
        message.setHeader(VALIDATION_CLIENT_HEADER, VALIDATION_CLIENT_MARDUK);
        message.setHeader(VALIDATION_PROFILE_HEADER, VALIDATION_PROFILE_TIMETABLE_FLEX_MERGING);
        message.setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER, fileHandle);
        message.setHeader(VALIDATION_CORRELATION_ID_HEADER, message.getHeader(CORRELATION_ID, String.class));
        publisher.publish(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE, message);

        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION)
                .state(JobEvent.State.PENDING)
                .jobId(null));
    }

    private static void setCorrelationIdIfMissing(MardukMessage message) {
        if (message.getHeader(CORRELATION_ID, String.class) == null) {
            message.setHeader(CORRELATION_ID, UUID.randomUUID().toString());
            MardukMdc.set(message);
        }
    }

    private static void deleteDirectoryRecursively(String directory) {
        LOGGER.debug("Deleting local directory {} ...", directory);
        try {
            if (FileSystemUtils.deleteRecursively(Path.of(directory))) {
                LOGGER.debug("Local directory {} cleanup done.", directory);
            } else {
                LOGGER.debug("The directory {} did not exist, ignoring deletion request", directory);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete directory {}", directory, e);
        }
    }
}
