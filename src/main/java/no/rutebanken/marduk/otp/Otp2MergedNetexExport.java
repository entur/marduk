package no.rutebanken.marduk.otp;

import jakarta.annotation.PreDestroy;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.netex.NetexDsjExport;
import no.rutebanken.marduk.netex.NetexDsjExportConfig;
import no.rutebanken.marduk.netex.NetexDsjExportConfig.Variant;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.RawZipMerger;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;

/**
 * Builds the one NeTEx archive for Norway that the OTP2 graph is built from.
 *
 * <p>Replaces {@code Otp2NetexExportMergedRouteBuilder}. Every provider's latest export and the stop place
 * registry's export are copied into one archive, the registry's files renamed to the names the profile expects,
 * and the result is published.
 *
 * <p>The entries are copied without being decompressed (see {@link RawZipMerger}): the exports are already
 * deflated and merging does not change their content. The provider exports are downloaded in parallel and held
 * in memory while the archive is written to the working directory.
 *
 * <p>When the dual DatedServiceJourney export is enabled (see {@link NetexDsjExportConfig}), the aggregated
 * export is built twice, from the legacy (NeTEx 1.15 structure) and from the new (NeTEx 1.16 structure)
 * per-provider exports, and stored in the corresponding folders. The variant configured as default is then
 * copied into the default folder.
 */
@Component
public class Otp2MergedNetexExport {

    private static final Logger LOGGER = LoggerFactory.getLogger(Otp2MergedNetexExport.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String MERGED_NETEX_SUBFOLDER = "/result";

    /**
     * Dedicated to downloading the per-provider exports: the aggregated export is on the critical path of every
     * OTP2 graph build, so it does not share a pool with anything else.
     */
    private final ExecutorService downloads = Executors.newFixedThreadPool(
            8, Thread.ofPlatform().name("netex-aggregation-", 0).daemon().factory());

    private final MardukPublicBlobStoreService blobStore;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final NetexDsjExport netexDsjExport;
    private final NetexDsjExportConfig netexDsjExportConfig;
    private final String localWorkingDirectory;
    private final String stopPlaceExportBlobPath;
    private final String netexExportMergedFilePath;
    private final String netexExportStopsFilePrefix;
    private final boolean failOnDuplicateEntry;

    public Otp2MergedNetexExport(
            MardukPublicBlobStoreService blobStore,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            NetexDsjExport netexDsjExport,
            NetexDsjExportConfig netexDsjExportConfig,
            @Value("${otp2.netex.export.download.directory:files/netex/merged-otp2}") String localWorkingDirectory,
            @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}") String stopPlaceExportBlobPath,
            @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}") String netexExportMergedFilePath,
            @Value("${netex.export.stops.file.prefix:_stops}") String netexExportStopsFilePrefix,
            // Fail when two archives contribute an entry under the same name, instead of keeping the last one.
            @Value("${netex.export.merged.fail.on.duplicate.entry:false}") boolean failOnDuplicateEntry) {
        this.blobStore = blobStore;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.netexDsjExport = netexDsjExport;
        this.netexDsjExportConfig = netexDsjExportConfig;
        this.localWorkingDirectory = localWorkingDirectory;
        this.stopPlaceExportBlobPath = stopPlaceExportBlobPath;
        this.netexExportMergedFilePath = netexExportMergedFilePath;
        this.netexExportStopsFilePrefix = netexExportStopsFilePrefix;
        this.failOnDuplicateEntry = failOnDuplicateEntry;
    }

    @PreDestroy
    void shutdown() {
        downloads.shutdownNow();
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
        // Read once, so that the distribution of the missing exports and every variant work on the same
        // providers: the provider cache is refreshed every few minutes, and a provider dropping out of it between
        // two readings would silently leave its export out of an aggregate without being recorded as missing.
        List<Provider> providers = publishedProviders();
        jobEvents.reportSystemJob(message, builder -> builder
                .jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH)
                .action(JobEvent.TimetableAction.EXPORT_NETEX_MERGED)
                .fileName(netexExportStopsFilePrefix)
                .state(JobEvent.State.STARTED)
                .newCorrelationId());
        deleteDirectoryRecursively(folder);
        try {
            byte[] stops = fetchStopsNetexExport(message);
            if (stops == null) {
                return false;
            }
            buildMergedExports(message, folder, providers, stops);
            // The route wire-tapped this to keep the merge's body; there is no body to keep here, so it is a
            // plain call and the OK is reported before the caller continues rather than whenever.
            jobEvents.reportSystemJob(message, builder -> builder.state(JobEvent.State.OK));
            LOGGER.info("Completed export of merged Netex file for Norway for OTP2");
            return true;
        } finally {
            deleteDirectoryRecursively(folder);
        }
    }

    private byte[] fetchStopsNetexExport(MardukMessage message) {
        LOGGER.debug("Fetching {}", stopPlaceExportBlobPath);
        try (InputStream stops = blobStore.getBlob(stopPlaceExportBlobPath)) {
            if (stops == null) {
                LOGGER.warn("No stop place export found, unable to create merged Netex for Norway");
                jobEvents.reportSystemJob(message, builder -> builder.state(JobEvent.State.FAILED));
                return null;
            }
            return stops.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + stopPlaceExportBlobPath, e);
        }
    }

    private void buildMergedExports(MardukMessage message, String folder, List<Provider> providers, byte[] stops) {
        List<String> fileNames = providers.stream()
                .map(p -> NetexDsjExportConfig.aggregatedNetexFileName(p.getChouetteInfo().getReferential()))
                .toList();
        String defaultTarget = BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath;
        if (!netexDsjExportConfig.isEnabled()) {
            buildVariant(folder, NetexDsjExportConfig.DEFAULT_EXPORT_PATH, defaultTarget, fileNames, stops, null);
            return;
        }
        // Providers that have not published since the dual export was enabled exist only in the default folder.
        netexDsjExport.distributeMissingExports(message, providers);

        Set<String> referenceMissing = buildVariant(folder, netexDsjExportConfig.variantFolder(Variant.NEW),
                netexDsjExportConfig.variantPath(Variant.NEW, mergedNetexFileName()), fileNames, stops, null);
        buildVariant(folder, netexDsjExportConfig.variantFolder(Variant.LEGACY),
                netexDsjExportConfig.variantPath(Variant.LEGACY, mergedNetexFileName()), fileNames, stops, referenceMissing);

        Variant defaultVariant = netexDsjExportConfig.getDefaultVariant();
        LOGGER.info("Copying the {} variant of the combined Netex for Norway to the default folder", defaultVariant);
        blobStore.copyBlobInBucket(netexDsjExportConfig.variantPath(defaultVariant, mergedNetexFileName()), defaultTarget);
    }

    /**
     * Builds one aggregated export from the per-provider exports found in the source folder and the stop place
     * export, and uploads it to the target. The stop place export contains no DatedServiceJourney and is used as
     * is in every variant.
     *
     * @param referenceMissing the exports the first variant was missing, which this one must be missing too
     * @return the per-provider exports that were not found in the source folder
     */
    private Set<String> buildVariant(String folder, String sourceFolder, String target, List<String> fileNames,
                                     byte[] stops, Set<String> referenceMissing) {
        LOGGER.info("Building combined Netex for Norway from {}", sourceFolder);
        Set<String> missing = Collections.synchronizedSet(new LinkedHashSet<>());
        Map<String, byte[]> archives = fetchProviderExports(sourceFolder, fileNames, missing);
        verifyVariantCoversTheSameProviders(sourceFolder, missing, referenceMissing);

        File resultFolder = new File(folder + MERGED_NETEX_SUBFOLDER);
        resultFolder.mkdirs();
        try {
            Path merged = buildAggregatedExport(folder, fileNames, archives, stops);
            LOGGER.info("Uploading new combined Netex for Norway for OTP to {}", target);
            try (InputStream contents = Files.newInputStream(merged)) {
                blobStore.uploadBlob(target, contents);
            }
            // free the disk space before building the next variant
            FileUtils.deleteDirectory(resultFolder);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not build the combined Netex for Norway in " + target, e);
        }
        return missing;
    }

    /**
     * The downloads are network-bound and independent; the archives are merged afterwards in the order of
     * {@code fileNames}, so the aggregated export does not depend on the completion order.
     */
    private Map<String, byte[]> fetchProviderExports(String sourceFolder, List<String> fileNames, Set<String> missing) {
        LOGGER.debug("Fetching netex files for all providers.");
        Map<String, byte[]> archives = new ConcurrentHashMap<>();
        List<Future<?>> downloading = new ArrayList<>();
        for (String fileName : fileNames) {
            downloading.add(downloads.submit(() -> fetchProviderExport(sourceFolder, fileName, archives, missing)));
        }
        RuntimeException failure = null;
        for (Future<?> download : downloading) {
            try {
                download.get();
            } catch (ExecutionException e) {
                RuntimeException thrown = e.getCause() instanceof RuntimeException runtime
                        ? runtime : new MardukException(e.getCause());
                failure = failure == null ? thrown : failure;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MardukException("Interrupted while fetching the provider NeTEx exports", e);
            }
        }
        if (failure != null) {
            throw failure;
        }
        return archives;
    }

    private void fetchProviderExport(String sourceFolder, String fileName, Map<String, byte[]> archives, Set<String> missing) {
        String fileHandle = sourceFolder + fileName;
        LOGGER.debug("Fetching {}", fileHandle);
        try (InputStream export = blobStore.getBlob(fileHandle)) {
            if (export == null) {
                LOGGER.warn("{} was empty when trying to fetch it from blobstore.", fileHandle);
                missing.add(fileName);
                return;
            }
            archives.put(fileName, export.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + fileHandle, e);
        }
    }

    /**
     * Every variant of the aggregated export must be built from the exports of the same providers. A provider
     * export that is present in the folder of one variant but missing from the folder of another would silently
     * disappear from one of the aggregated exports, and with it from the OTP graph built from that export, so the
     * export is failed instead: the caller (a publication or a graph build) retries it.
     *
     * <p>A provider that has never published has no export in any folder and is missing from every variant, which
     * is accepted.
     */
    private static void verifyVariantCoversTheSameProviders(String sourceFolder, Set<String> missing, Set<String> reference) {
        if (reference != null && !reference.equals(missing)) {
            throw new MardukException("The NeTEx exports found in " + sourceFolder
                    + " do not cover the same providers as the ones the previous variant of the aggregated export was built from"
                    + " (missing here: " + missing + ", missing there: " + reference
                    + "): aborting to avoid publishing an incomplete aggregated export for Norway");
        }
    }

    /**
     * Builds the aggregated export by copying the entries of the per-provider exports and of the stop place export
     * into a single archive, without decompressing them.
     *
     * <p>The archives are merged in the order of {@code fileNames}, and the entries of each in physical order, so
     * the aggregated export is reproducible. The stop place export comes last, because its entries used to be
     * copied into the unpacked folder after the provider exports and therefore won over an entry of the same name.
     */
    private Path buildAggregatedExport(String folder, List<String> fileNames, Map<String, byte[]> archives, byte[] stops)
            throws IOException {
        Path target = Paths.get(folder + MERGED_NETEX_SUBFOLDER + "/merged.zip");
        try (RawZipMerger.Plan plan = RawZipMerger.plan().failOnDuplicateEntry(failOnDuplicateEntry)) {
            int archiveCount = 0;
            for (String fileName : fileNames) {
                byte[] archive = archives.get(fileName);
                if (archive != null) {
                    plan.add(RawZipMerger.Source.of(fileName, archive));
                    archiveCount++;
                }
            }
            plan.addRenamed(RawZipMerger.Source.of(stopPlaceExportBlobPath, stops), netexExportStopsFilePrefix, ".xml");
            archiveCount++;

            RawZipMerger.Result result = plan.writeTo(target);
            LOGGER.info("Built the combined Netex for Norway from {} archives: {} entries, {} bytes, {} duplicate entry name(s)",
                    archiveCount, result.entriesWritten(), result.bytesWritten(), result.duplicateNames().size());
        }
        return target;
    }

    /**
     * Providers whose dataset is published in the public bucket, that is the providers whose data is not migrated
     * to another provider.
     */
    public List<Provider> publishedProviders() {
        return providerRepository.getProviders().stream()
                .filter(provider -> provider.getChouetteInfo().getMigrateDataToProvider() == null)
                .toList();
    }

    /** Name of the merged export file, without its folder (for instance {@code rb_norway-aggregated-netex.zip}). */
    private String mergedNetexFileName() {
        return Paths.get(netexExportMergedFilePath).getFileName().toString();
    }

    /** A directory left behind would be gigabytes. */
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
