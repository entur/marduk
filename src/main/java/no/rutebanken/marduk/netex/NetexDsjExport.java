/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.AbstractBlobStoreService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.USERNAME;

/**
 * Distributes a freshly published NeTEx dataset to the three export folders used during the transition to the
 * NeTEx 1.16 DatedServiceJourney structure (see {@link NetexDsjExportConfig}):
 * the dataset exported by the import pipeline is stored in the "new" folder, the legacy copy (downgraded when
 * the dataset carries replacement information, a plain copy otherwise) is stored in the "legacy" folder, and the
 * variant configured as default is copied into the default folder.
 *
 * <p>Replaces {@code NetexDsjExportRouteBuilder}. Its {@code direct:} routes are methods here; the exchange
 * properties that steered them ({@code PROP_IGNORE_DISTRIBUTION_FAILURES}, {@code PROP_REPORT_JOB_EVENTS}) are
 * the {@link Mode} each entry point passes down.
 */
@Component
public class NetexDsjExport {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetexDsjExport.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String DOWNGRADED_FILE_NAME = "legacy.zip";
    /**
     * Reported as the user of the job events of an export that no user triggered, typically an aggregated export
     * distributing the provider exports that are missing from the legacy or new folder.
     */
    private static final String SYSTEM_USERNAME = "System";

    /**
     * How one distribution treats failures and job events.
     *
     * @param ignoreFailures whether the distribution of one provider may fail without failing the whole batch.
     *                       Only the manual backfill sets it: a batch whose result is merged into an aggregated
     *                       export must fail rather than skip a provider.
     * @param reportJobEvents whether the distribution reports its progress as a job event of the provider. Only
     *                        the batches clear it: they distribute every provider with the correlation id of the
     *                        batch, and Nabu, which aggregates job events by correlation id, would merge them
     *                        into one job attributed to whichever provider was distributed first. A batch is a
     *                        system action that belongs to no provider's import job.
     */
    private record Mode(boolean ignoreFailures, boolean reportJobEvents) {
        static final Mode PUBLICATION = new Mode(false, true);
        static final Mode BACKFILL = new Mode(true, false);
        static final Mode MISSING = new Mode(false, false);
    }

    private final NetexDsjExportConfig config;
    private final MardukPublicBlobStoreService publicBlobStore;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final JobEventPublisher jobEvents;

    public NetexDsjExport(
            NetexDsjExportConfig config,
            MardukPublicBlobStoreService publicBlobStore,
            MardukInternalBlobStoreService internalBlobStore,
            JobEventPublisher jobEvents) {
        this.config = config;
        this.publicBlobStore = publicBlobStore;
        this.internalBlobStore = internalBlobStore;
        this.jobEvents = jobEvents;
    }

    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * Stores the legacy and new variants of the export of the message's provider and copies the default variant
     * into the default folder. A failure is reported and rethrown, so that the publication is retried: the default
     * folder must not diverge from the other folders.
     */
    public void distributeExport(MardukMessage message) {
        distribute(message, Mode.PUBLICATION);
    }

    /**
     * Stores a legacy copy of the NeTEx blocks export next to the one produced by the pipeline. Called after every
     * publication of the blocks export; a no-op unless the dual export is enabled.
     *
     * <p>The legacy copy is overwritten in place, so a failure fails the calling step and is retried: swallowing it
     * would leave the previous legacy copy next to a freshly published blocks export.
     */
    public void distributeBlocksExport(MardukMessage message) {
        if (config.isEnabled()) {
            distributeBlocks(message, Mode.PUBLICATION);
        }
    }

    /**
     * Stores the dataset uploaded by the provider in the folders of the Nisaba bucket used during the transition,
     * the same way a published dataset is stored in three folders of the public bucket. Writes the legacy copy in
     * {@code imported-dsj-legacy} and the variant selected by {@code netex.export.dsj.default.variant} in the
     * default folder {@code imported}; the caller stores the dataset as uploaded in {@code imported-dsj-new} (see
     * {@link NetexDsjExportConfig#originalDatasetPublicationPath(String)}). A no-op unless the dual export is
     * enabled.
     *
     * <p>Expects the headers set for the upload of the original dataset: {@code FILE_HANDLE} (original dataset in
     * the internal bucket), {@code TARGET_FILE_HANDLE} ({@code imported/<referential>/<referential>_<timestamp>.zip})
     * and {@code TARGET_CONTAINER} (Nisaba bucket). The message is left as it was found.
     *
     * <p>The default folder receives the dataset from here only: swallowing a failure would leave the import
     * without an original dataset in Nisaba, silently and for good, so it fails the calling step instead.
     */
    public void distributeOriginalDatasetToNisaba(MardukMessage message) {
        if (!config.isEnabled()) {
            return;
        }
        String originalDatasetPath = message.getHeader(TARGET_FILE_HANDLE, String.class);
        if (originalDatasetPath == null) {
            throw new MardukException("No target file handle for the original dataset");
        }
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        String original = message.getHeader(FILE_HANDLE, String.class);
        String container = message.getHeader(TARGET_CONTAINER, String.class);
        LOGGER.info("Storing the original dataset {} in the DatedServiceJourney folders of Nisaba", original);
        File folder = workingFolder(message);
        try {
            createLegacyOriginalDataset(message, folder, referential, original, container, originalDatasetPath);
            copyDefaultVariantOriginalDatasetToNisaba(referential, original, container, originalDatasetPath);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to store the original dataset of {} in the DatedServiceJourney folders of Nisaba: {}",
                    referential, e.getMessage());
            throw e;
        } finally {
            deleteDirectoryRecursively(folder);
        }
    }

    /**
     * Backfill: distributes the current export of every published provider. An export that cannot be distributed
     * must not prevent the other providers from being processed. A provider that cannot be downgraded is swallowed
     * (see {@link Mode#BACKFILL}); any other failure is rethrown at the end so that the caller does not go on to
     * build an aggregated export from folders that are not filled.
     */
    public void distributeAllExports(MardukMessage message, List<Provider> providers) {
        setCorrelationIdIfMissing(message);
        LOGGER.info("Distributing the NeTEx exports of all providers to the legacy and new DatedServiceJourney export folders");
        RuntimeException failure = null;
        for (Provider provider : providers) {
            try {
                MardukMessage forProvider = forProvider(message, provider);
                distributeIfPublished(forProvider, Mode.BACKFILL);
                distributeBlocksIfPresent(forProvider, Mode.BACKFILL);
            } catch (RuntimeException e) {
                LOGGER.error("Failed to distribute the NeTEx export of {}", provider.getChouetteInfo().getReferential(), e);
                failure = failure == null ? e : failure;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Distributes the export of every given provider that is missing from the legacy or new folder (typically
     * providers that have not published since the dual export was enabled), so that aggregated exports built from
     * these folders are complete.
     *
     * <p>An aggregated export built from a folder from which a provider export is missing would silently lose that
     * provider: a provider that cannot be distributed fails the aggregated export, which is then retried, rather
     * than being skipped.
     *
     * @param providers the providers the caller snapshotted, so that the distribution and the aggregation reason
     *                  about the same providers even if the provider cache is refreshed in between
     */
    public void distributeMissingExports(MardukMessage message, List<Provider> providers) {
        setCorrelationIdIfMissing(message);
        for (Provider provider : getProvidersMissingFromAVariantFolder(providers)) {
            distributeIfMissing(forProvider(message, provider), Mode.MISSING);
        }
    }

    // ------------------------------------------------------------------------------------ public exports

    private void distribute(MardukMessage message, Mode mode) {
        String referential = requireReferential(message);
        defaultUsernameToSystem(message);
        LOGGER.info("Distributing the NeTEx export of {} to the legacy and new DatedServiceJourney export folders", referential);
        report(message, JobEvent.State.STARTED, mode);
        File folder = workingFolder(message);
        try {
            seedNewExportIfMissing(referential);
            createLegacyExport(folder, referential);
            publicBlobStore.copyBlobInBucket(config.defaultVariantSourcePath(referential), config.defaultExportPath(referential));
            report(message, JobEvent.State.OK, mode);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to distribute the NeTEx export of {}: {}", referential, e.getMessage());
            report(message, JobEvent.State.FAILED, mode);
            throw e;
        } finally {
            deleteDirectoryRecursively(folder);
            message.setBody("");
        }
    }

    /**
     * Legacy variant of the public export: downgraded for the codespaces whose datasets contain DatedServiceJourney
     * replacement information, a plain copy of the new variant for the others.
     */
    private void createLegacyExport(File folder, String referential) {
        if (config.hasDsjReplacements(referential)) {
            File downgraded = downgrade(publicBlobStore.getBlob(config.newExportPath(referential)), folder, referential);
            upload(publicBlobStore, config.legacyExportPath(referential), downgraded);
        } else {
            LOGGER.info("The datasets of {} contain no DatedServiceJourney replacement information: the legacy export is a copy of the new export", referential);
            publicBlobStore.copyBlobInBucket(config.newExportPath(referential), config.legacyExportPath(referential));
        }
    }

    /**
     * When a provider has not published since the dual export was enabled, its dataset exists only in the default
     * folder: use it as the source (backfill).
     */
    private void seedNewExportIfMissing(String referential) {
        if (publicBlobStore.findBlob(config.newExportPath(referential)) != null) {
            return;
        }
        if (publicBlobStore.findBlob(config.defaultExportPath(referential)) == null) {
            throw new MardukException("No NeTEx export found for the provider");
        }
        LOGGER.info("No NeTEx export of {} in the new DatedServiceJourney export folder, seeding it from the default folder", referential);
        publicBlobStore.copyBlobInBucket(config.defaultExportPath(referential), config.newExportPath(referential));
    }

    // ------------------------------------------------------------------------------------ blocks export

    private void distributeBlocks(MardukMessage message, Mode mode) {
        String referential = requireReferential(message);
        defaultUsernameToSystem(message);
        LOGGER.info("Downgrading the NeTEx blocks export of {} to NeTEx 1.15", referential);
        Object savedBody = message.getBody();
        File folder = workingFolder(message);
        try {
            createLegacyBlocksExport(folder, referential);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to downgrade the NeTEx blocks export of {}: {}", referential, e.getMessage());
            report(message, JobEvent.State.FAILED, mode);
            // the timetable API cannot tell a stale legacy copy from a current one: fail the publication so that
            // it is retried, rather than serving the previous copy as the current dataset. Only the manual
            // backfill tolerates it, so that one provider that cannot be downgraded does not stop the others.
            if (!mode.ignoreFailures()) {
                throw e;
            }
        } finally {
            deleteDirectoryRecursively(folder);
            message.setBody(savedBody);
        }
    }

    private void createLegacyBlocksExport(File folder, String referential) {
        if (config.hasDsjReplacements(referential)) {
            File downgraded = downgrade(internalBlobStore.getBlob(config.blocksExportPath(referential)), folder, referential);
            upload(internalBlobStore, config.legacyBlocksExportPath(referential), downgraded);
        } else {
            LOGGER.info("The datasets of {} contain no DatedServiceJourney replacement information: the legacy blocks export is a copy of the blocks export", referential);
            internalBlobStore.copyBlobInBucket(config.blocksExportPath(referential), config.legacyBlocksExportPath(referential));
        }
    }

    /** Backfill variant: providers without a blocks export are skipped silently. */
    private void distributeBlocksIfPresent(MardukMessage message, Mode mode) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        if (internalBlobStore.findBlob(config.blocksExportPath(referential)) == null) {
            LOGGER.info("No NeTEx blocks export found for {}", referential);
            return;
        }
        if (config.isEnabled()) {
            distributeBlocks(message, mode);
        }
        message.setBody("");
    }

    // ----------------------------------------------------------------------------------- original dataset

    private void createLegacyOriginalDataset(MardukMessage message, File folder, String referential, String original,
                                             String container, String originalDatasetPath) {
        String legacyPath = NetexDsjExportConfig.legacyOriginalDatasetPath(originalDatasetPath);
        if (config.hasDsjReplacements(referential)) {
            File downgraded = downgrade(internalBlobStore.getBlob(original), folder, referential);
            String staging = NetexDsjExportConfig.legacyOriginalDatasetStagingPath(originalDatasetPath);
            upload(internalBlobStore, staging, downgraded);
            internalBlobStore.copyBlobToAnotherBucket(staging, container, legacyPath);
        } else {
            LOGGER.info("The datasets of {} contain no DatedServiceJourney replacement information: the legacy original dataset is a copy of the original dataset", referential);
            internalBlobStore.copyBlobToAnotherBucket(original, container, legacyPath);
        }
    }

    /**
     * The default folder receives the variant selected by netex.export.dsj.default.variant: the legacy copy staged
     * in the internal bucket when the legacy variant is the default and the dataset was downgraded, the dataset as
     * uploaded otherwise (the two are identical for a codespace without replacement information).
     */
    private void copyDefaultVariantOriginalDatasetToNisaba(String referential, String original, String container,
                                                           String originalDatasetPath) {
        boolean downgraded = config.getDefaultVariant() == NetexDsjExportConfig.Variant.LEGACY
                && config.hasDsjReplacements(referential);
        String source = downgraded ? NetexDsjExportConfig.legacyOriginalDatasetStagingPath(originalDatasetPath) : original;
        internalBlobStore.copyBlobToAnotherBucket(source, container, originalDatasetPath);
    }

    // ------------------------------------------------------------------------------------ batches

    private void distributeIfMissing(MardukMessage message, Mode mode) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        boolean newExists = publicBlobStore.findBlob(config.newExportPath(referential)) != null;
        if (newExists && publicBlobStore.findBlob(config.legacyExportPath(referential)) != null) {
            LOGGER.debug("The NeTEx export of {} exists in the legacy and new DatedServiceJourney export folders", referential);
            return;
        }
        LOGGER.info("The NeTEx export of {} is missing from the legacy or new DatedServiceJourney export folder, distributing it", referential);
        distributeIfPublished(message, mode);
    }

    /**
     * A provider that has never published has no export in any folder: there is nothing to distribute, and
     * reporting a failed export for it on every backfill would be misleading. The export is looked up in the new
     * folder and, when it is missing there, in the default folder, which is where the export of a provider that
     * has not published since the dual export was enabled still is.
     */
    private void distributeIfPublished(MardukMessage message, Mode mode) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        boolean published = publicBlobStore.findBlob(config.newExportPath(referential)) != null
                || publicBlobStore.findBlob(config.defaultExportPath(referential)) != null;
        if (!published) {
            LOGGER.debug("{} has not published a NeTEx export yet, nothing to distribute", referential);
            return;
        }
        try {
            distribute(message, mode);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to distribute the NeTEx export of {}: {}", referential, e.getMessage());
            if (!mode.ignoreFailures()) {
                throw e;
            }
        }
        message.setBody("");
    }

    /** The given published providers whose export is missing from the legacy or the new export folder. */
    private List<Provider> getProvidersMissingFromAVariantFolder(List<Provider> providers) {
        List<String> fileNames = providers.stream()
                .map(p -> NetexDsjExportConfig.aggregatedNetexFileName(p.getChouetteInfo().getReferential()))
                .toList();
        Map<String, Long> newExports = countExportsByFileName(config.getNewBlobPath(), fileNames);
        Map<String, Long> legacyExports = countExportsByFileName(config.getLegacyBlobPath(), fileNames);
        List<Provider> missing = providers.stream()
                .filter(p -> !isPresentExactlyOnce(newExports, p, config.getNewBlobPath())
                        || !isPresentExactlyOnce(legacyExports, p, config.getLegacyBlobPath()))
                .toList();
        LOGGER.info("Checked {} providers against the DatedServiceJourney export folders, {} need to be distributed",
                providers.size(), missing.size());
        return missing;
    }

    /**
     * Lists the folder once and counts, for each export file name, the blobs whose name starts with it. Prefix
     * matching mirrors {@code findBlob}, which the distribution uses to look an export up.
     */
    private Map<String, Long> countExportsByFileName(String folder, List<String> fileNames) {
        Map<String, Long> counts = new HashMap<>();
        for (BlobStoreFiles.File file : publicBlobStore.listBlobsFlatInFolder(folder).getFiles()) {
            for (String fileName : fileNames) {
                if (file.getName().startsWith(fileName)) {
                    counts.merge(fileName, 1L, Long::sum);
                }
            }
        }
        return counts;
    }

    /**
     * Whether the folder holds exactly one blob for the export of the provider. No blob means the export must be
     * distributed; several blobs is an error, as in {@code findBlob}, since the export cannot be identified.
     */
    private boolean isPresentExactlyOnce(Map<String, Long> exports, Provider provider, String folder) {
        String fileName = NetexDsjExportConfig.aggregatedNetexFileName(provider.getChouetteInfo().getReferential());
        long count = exports.getOrDefault(fileName, 0L);
        if (count > 1) {
            throw new MardukException("Found multiple files matching the prefix " + folder + fileName);
        }
        return count == 1;
    }

    // ---------------------------------------------------------------------------------------- helpers

    private File downgrade(InputStream export, File folder, String referential) {
        if (export == null) {
            throw new MardukException("No NeTEx export found for the provider");
        }
        try (export) {
            if (!folder.mkdirs() && !folder.isDirectory()) {
                throw new MardukException("Failed to create the working directory " + folder);
            }
            File downgradedExport = new File(folder, DOWNGRADED_FILE_NAME);
            NetexDsjConverter.Report report = NetexDsjConverter.downgradeZip(export, downgradedExport, folder, config.getMaxXsltFileSizeBytes());
            LOGGER.info("Downgraded {} of {} files of the NeTEx export of {} to NeTEx 1.15", report.converted(), report.entries(), referential);
            return downgradedExport;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not downgrade the NeTEx export of " + referential, e);
        }
    }

    private static void upload(AbstractBlobStoreService blobStore, String path, File file) {
        try (InputStream contents = Files.newInputStream(file.toPath())) {
            blobStore.uploadBlobWithoutVersionHeader(path, contents);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not upload " + path, e);
        }
    }

    /**
     * Reports the state as an {@code EXPORT_NETEX_LEGACY} job event of the provider being distributed, unless the
     * distribution runs as part of a batch (see {@link Mode}). Reporting leaves the event as the message body, so
     * the callers restore it.
     */
    private void report(MardukMessage message, JobEvent.State state, Mode mode) {
        if (mode.reportJobEvents()) {
            jobEvents.reportProviderJob(message, builder -> builder
                    .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_LEGACY)
                    .state(state));
        }
    }

    private static String requireReferential(MardukMessage message) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        if (referential == null) {
            throw new MardukException("No " + CHOUETTE_REFERENTIAL + " to distribute the NeTEx export of");
        }
        return referential;
    }

    /**
     * Reports the job events of an export that no user triggered as triggered by the system, the way the other
     * work triggered by the pipeline itself does: a job event without a username is rendered as an empty entry.
     */
    private static void defaultUsernameToSystem(MardukMessage message) {
        if (message.getHeader(USERNAME) == null) {
            message.setHeader(USERNAME, SYSTEM_USERNAME);
        }
    }

    private static void setCorrelationIdIfMissing(MardukMessage message) {
        if (message.getHeader(CORRELATION_ID) == null) {
            message.setHeader(CORRELATION_ID, UUID.randomUUID().toString());
        }
    }

    private static MardukMessage forProvider(MardukMessage message, Provider provider) {
        return message.copy()
                .setHeader(PROVIDER_ID, provider.getId())
                .setHeader(CHOUETTE_REFERENTIAL, provider.getChouetteInfo().getReferential())
                .setBody("");
    }

    private File workingFolder(MardukMessage message) {
        return new File(config.getDownloadDirectory() + "/" + message.getHeader(CORRELATION_ID, String.class)
                + "_" + LocalDateTime.now().format(TIMESTAMP));
    }

    private static void deleteDirectoryRecursively(File directory) {
        try {
            FileSystemUtils.deleteRecursively(Paths.get(directory.getPath()));
        } catch (IOException e) {
            LOGGER.warn("Failed to delete directory {}", directory, e);
        }
    }
}
