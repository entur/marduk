package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.apache.commons.io.FileUtils;
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
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_NETEX_FILENAME;

/**
 * Builds the one NeTEx archive for Norway that the OTP2 graph is built from.
 *
 * <p>Replaces {@code Otp2NetexExportMergedRouteBuilder}. Every provider's latest export is unpacked into one
 * directory together with the stop place registry's export, whose files are renamed to the names the profile
 * expects, and the result is zipped back up and published.
 *
 * <p>Everything goes through the working directory on disk rather than the heap: the providers' exports and
 * the stop place export together are well over a gigabyte unpacked, and the pod's limit is a fraction of
 * that. The route got the same effect from {@code .streamCaching()}.
 */
@Component
public class Otp2MergedNetexExport {

    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2MergedNetexExport.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String UNPACKED_NETEX_SUBFOLDER = "/unpacked-netex";
    private static final String STOPS_FILES_SUBFOLDER = "/stops";
    private static final String MERGED_NETEX_SUBFOLDER = "/result";

    private final MardukPublicBlobStoreService blobStore;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final String localWorkingDirectory;
    private final String stopPlaceExportBlobPath;
    private final String netexExportMergedFilePath;
    private final String netexExportStopsFilePrefix;

    public Otp2MergedNetexExport(
            MardukPublicBlobStoreService blobStore,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            @Value("${otp2.netex.export.download.directory:files/netex/merged-otp2}") String localWorkingDirectory,
            @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}") String stopPlaceExportBlobPath,
            @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}") String netexExportMergedFilePath,
            @Value("${netex.export.stops.file.prefix:_stops}") String netexExportStopsFilePrefix) {
        this.blobStore = blobStore;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.localWorkingDirectory = localWorkingDirectory;
        this.stopPlaceExportBlobPath = stopPlaceExportBlobPath;
        this.netexExportMergedFilePath = netexExportMergedFilePath;
        this.netexExportStopsFilePrefix = netexExportStopsFilePrefix;
    }

    /**
     * Exports the merged archive.
     *
     * <p>The status events are reported against {@code message}, which also carries the export job forward -
     * the caller's own {@code RutebankenSystemStatus} header is replaced by this job's, as it was when the two
     * shared an exchange.
     *
     * @return false when there is no stop place export to merge. The route used {@code .stop()} there, which
     *         aborted the caller as well, so a caller must treat false as "give up on this build".
     */
    public boolean export(MardukMessage message) {
        LOGGER.info("Start export of merged Netex file for Norway for OTP2");
        String folder = localWorkingDirectory + "/" + message.getHeader(CORRELATION_ID, String.class)
                + "_" + LocalDateTime.now().format(TIMESTAMP);
        jobEvents.reportSystemJob(message, builder -> builder
                .jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH)
                .action(JobEvent.TimetableAction.EXPORT_NETEX_MERGED)
                .fileName(netexExportStopsFilePrefix)
                .state(JobEvent.State.STARTED)
                .newCorrelationId());
        deleteDirectoryRecursively(folder);
        try {
            fetchProviderNetexExports(folder);
            if (!fetchStopsNetexExport(folder, message)) {
                return false;
            }
            merge(folder);
            // The route wire-tapped this to keep the merge's body; there is no body to keep here, so it is a
            // plain call and the OK is reported before the caller continues rather than whenever.
            jobEvents.reportSystemJob(message, builder -> builder.state(JobEvent.State.OK));
            LOGGER.info("Completed export of merged Netex file for Norway for OTP2");
            return true;
        } finally {
            deleteDirectoryRecursively(folder);
        }
    }

    private void fetchProviderNetexExports(String folder) {
        LOGGER.debug("Fetching netex files for all providers.");
        for (String fileName : aggregatedNetexFiles()) {
            String fileHandle = BLOBSTORE_PATH_OUTBOUND + "netex/" + fileName;
            LOGGER.debug("Fetching {}", fileHandle);
            try (InputStream export = blobStore.getBlob(fileHandle)) {
                if (export == null) {
                    LOGGER.info("{} was empty when trying to fetch it from blobstore.", fileName);
                    continue;
                }
                ZipFileUtils.unzipFile(export, folder + UNPACKED_NETEX_SUBFOLDER);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + fileHandle, e);
            }
        }
    }

    private boolean fetchStopsNetexExport(String folder, MardukMessage message) {
        LOGGER.debug("Fetching {}", stopPlaceExportBlobPath);
        try (InputStream stops = blobStore.getBlob(stopPlaceExportBlobPath)) {
            if (stops == null) {
                LOGGER.warn("No stop place export found, unable to create merged Netex for Norway");
                jobEvents.reportSystemJob(message, builder -> builder.state(JobEvent.State.FAILED));
                return false;
            }
            ZipFileUtils.unzipFile(stops, folder + STOPS_FILES_SUBFOLDER);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + stopPlaceExportBlobPath, e);
        }
        copyAndRenameStopFiles(folder + STOPS_FILES_SUBFOLDER, folder + UNPACKED_NETEX_SUBFOLDER);
        return true;
    }

    private void merge(String folder) {
        LOGGER.debug("Merging Netex files for all providers and stop place registry.");
        File resultFolder = new File(folder + MERGED_NETEX_SUBFOLDER);
        resultFolder.mkdir();
        File merged = ZipFileUtils.zipFilesInFolder(
                folder + UNPACKED_NETEX_SUBFOLDER, folder + MERGED_NETEX_SUBFOLDER + "/merged.zip");
        String fileHandle = BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath;
        LOGGER.info("Uploading new combined Netex for Norway for OTP");
        try (InputStream contents = Files.newInputStream(merged.toPath())) {
            blobStore.uploadBlob(fileHandle, contents);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not upload " + fileHandle, e);
        }
    }

    List<String> aggregatedNetexFiles() {
        return providerRepository.getProviders().stream()
                .filter(provider -> provider.getChouetteInfo().getMigrateDataToProvider() == null)
                .map(provider -> provider.getChouetteInfo().getReferential() + "-" + CURRENT_AGGREGATED_NETEX_FILENAME)
                .toList();
    }

    /** Renamed so the merged archive complies with the profile, which the registry's own names do not. */
    private void copyAndRenameStopFiles(String sourceDir, String targetDir) {
        try {
            int i = 0;
            for (File stopFile : FileUtils.listFiles(new File(sourceDir), null, false)) {
                String targetFileName = netexExportStopsFilePrefix + (i > 0 ? i : "") + ".xml";
                FileUtils.copyFile(stopFile, new File(targetDir, targetFileName));
                i++;
            }
        } catch (IOException e) {
            throw new MardukException("Failed to copy/rename stop files from NSR: " + e.getMessage(), e);
        }
    }

    /** Inlines {@code direct:cleanUpLocalDirectory}. A directory left behind would be gigabytes. */
    private static void deleteDirectoryRecursively(String directory) {
        LOGGER.debug("Deleting local directory {} ...", directory);
        try {
            Path pathToDelete = Paths.get(directory);
            if (FileSystemUtils.deleteRecursively(pathToDelete)) {
                LOGGER.debug("Local directory {} cleanup done.", directory);
            } else {
                LOGGER.debug("The directory {} did not exist, ignoring deletion request", directory);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to delete directory {}", directory, e);
        }
    }
}
