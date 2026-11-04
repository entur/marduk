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

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
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

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.FILE_VERSION;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Upgrades the pre-1.16 NeTEx files of a dataset to NeTEx 1.16 once the import pipeline works with NeTEx 1.16.
 * <p>
 * Only the datasets of the codespaces whose DatedServiceJourneys carry replacement information
 * (see {@link NetexDsjExportConfig#hasDsjReplacements(String)}) are concerned: the other datasets are accepted as is.
 * The upgraded dataset replaces the stored file in the internal bucket, so that the rest of the pipeline
 * (pre-validation, import, export) works on a NeTEx 1.16 dataset.
 * <p>
 * Since the upgrade overwrites the stored file, the dataset as it was uploaded is first copied next to it, under the
 * same name postfixed by {@code -original-v115} ({@code netex.zip} -> {@code netex-original-v115.zip}, see
 * {@link NetexDsjExportConfig#originalV115BackupPath(String)}), so that the bytes the provider submitted remain
 * available while the transition lasts. The copy is taken only when the upgrade actually converted a file: a dataset
 * that is already NeTEx 1.16 is left alone and leaves no copy. It is a regular file of the provider's folder, listed
 * and downloadable through the timetable API like the dataset itself, and a later upload replaces it, so that it
 * always mirrors the dataset currently stored.
 * <p>
 * Re-importing the copy itself upgrades it in place and backs it up under a twice-postfixed name. This is accepted
 * rather than guarded against: skipping the copy for an already-postfixed name would leave a re-imported original
 * unpreserved, and refusing to overwrite an existing copy would pin it to the first upload for good.
 * <p>
 * Called both when a provider uploads a dataset and when the nightly validation reuses the dataset stored by the
 * previous upload, which may predate the switch to NeTEx 1.16.
 *
 * <p>Replaces {@code NetexDsjUpgradeRouteBuilder}.
 */
@Component
public class NetexDsjUpgrade {

    private static final Logger LOGGER = LoggerFactory.getLogger(NetexDsjUpgrade.class);

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final String UPGRADED_FILE_NAME = "upgraded.zip";

    private final NetexDsjExportConfig config;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;

    public NetexDsjUpgrade(
            NetexDsjExportConfig config,
            MardukInternalBlobStoreService internalBlobStore,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents) {
        this.config = config;
        this.internalBlobStore = internalBlobStore;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
    }

    /**
     * Called after the classification of an uploaded file, with {@code FILE_HANDLE} pointing to the file in the
     * internal bucket. A no-op unless the upgrade is enabled, the file is a NeTEx dataset and its codespace
     * produces DatedServiceJourney replacement information. When the stored file is replaced, {@code FILE_VERSION}
     * is updated to the generation of the upgraded file.
     */
    public void upgradeIfNeeded(MardukMessage message) {
        if (requiresUpgradeCheck(message)) {
            upgrade(message);
        }
    }

    /**
     * The nightly validation reuses the dataset stored by the last upload, which may predate the switch to
     * NeTEx 1.16: it must be upgraded before it is copied to the validation bucket, exactly like an uploaded
     * dataset. A failed upgrade fails this provider's nightly run: validating and re-importing the stored pre-1.16
     * dataset is what the upgrade exists to prevent, since a NeTEx 1.16 Chouette drops its DatedServiceJourney
     * replacement references silently. There is therefore no fallback to the stored dataset.
     */
    public void upgradeForNightlyValidation(MardukMessage message) {
        try {
            upgradeIfNeeded(message);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to upgrade {} to NeTEx 1.16 before the nightly validation: {}",
                    message.getHeader(FILE_HANDLE, String.class), e.getMessage());
            jobEvents.reportProviderJob(message, builder -> builder
                    .timetableAction(JobEvent.TimetableAction.PREVALIDATION).state(JobEvent.State.FAILED));
            throw e;
        }
    }

    private void upgrade(MardukMessage message) {
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        LOGGER.info("Checking {} for NeTEx files to upgrade to NeTEx 1.16", fileHandle);
        File folder = new File(config.getDownloadDirectory() + "/upgrade_" + message.getHeader(CORRELATION_ID, String.class)
                + "_" + LocalDateTime.now().format(TIMESTAMP));
        try {
            File upgraded = upgradeDataset(fileHandle, folder);
            if (upgraded != null) {
                replaceStoredDataset(message, fileHandle, upgraded);
            }
        } finally {
            deleteDirectoryRecursively(folder);
            message.setBody("");
        }
    }

    /**
     * Upgrades the pre-1.16 NeTEx files of the dataset.
     *
     * @return the upgraded archive, or null when no file needed upgrading
     */
    private File upgradeDataset(String fileHandle, File folder) {
        try (InputStream dataset = internalBlobStore.getBlob(fileHandle)) {
            if (dataset == null) {
                throw new MardukException("File not found in the blob store: " + fileHandle);
            }
            if (!folder.mkdirs() && !folder.isDirectory()) {
                throw new MardukException("Failed to create the working directory " + folder);
            }
            File upgradedDataset = new File(folder, UPGRADED_FILE_NAME);
            NetexDsjConverter.Report report = NetexDsjConverter.upgradeZip(dataset, upgradedDataset, folder, config.getMaxXsltFileSizeBytes());
            if (report.converted() > 0) {
                LOGGER.info("Upgraded {} of {} files of {} to NeTEx 1.16", report.converted(), report.entries(), fileHandle);
                return upgradedDataset;
            }
            LOGGER.info("No NeTEx file older than 1.16 found in {}, the dataset is kept as is", fileHandle);
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not upgrade " + fileHandle, e);
        }
    }

    /**
     * Keeps the dataset as uploaded next to the stored file, then overwrites the stored file. The copy is a
     * server-side one, taken while the stored file still holds the dataset as it was uploaded, so a failed copy
     * fails the upgrade before the original is overwritten.
     */
    private void replaceStoredDataset(MardukMessage message, String fileHandle, File upgraded) {
        String backup = NetexDsjExportConfig.originalV115BackupPath(fileHandle);
        LOGGER.info("Keeping {} as uploaded in {} before upgrading it to NeTEx 1.16", fileHandle, backup);
        internalBlobStore.copyBlobInBucket(fileHandle, backup);
        LOGGER.info("Replacing {} with the dataset upgraded to NeTEx 1.16", fileHandle);
        try (InputStream contents = Files.newInputStream(upgraded.toPath())) {
            message.setHeader(FILE_VERSION, internalBlobStore.uploadBlob(fileHandle, contents));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not upload " + fileHandle, e);
        }
    }

    private boolean requiresUpgradeCheck(MardukMessage message) {
        return config.isUpgradeEnabled()
                && isNetexOrUnclassified(message)
                && config.hasDsjReplacements(referentialFor(message));
    }

    /**
     * FILE_TYPE is set by the classification of an uploaded file. The nightly validation reuses a dataset that is
     * already stored and never classifies it, so its message carries no FILE_TYPE: an absent header is not a reason
     * to skip the upgrade, the codespace decides. A header naming another type (GTFS, ...) still skips it.
     */
    private static boolean isNetexOrUnclassified(MardukMessage message) {
        String fileType = message.getHeader(FILE_TYPE, String.class);
        return fileType == null || FileType.NETEXPROFILE.name().equals(fileType);
    }

    /**
     * The referential of the dataset: the CHOUETTE_REFERENTIAL header when present (files uploaded through
     * the HTTP endpoints), otherwise the referential of the provider identified by the PROVIDER_ID header.
     */
    private String referentialFor(MardukMessage message) {
        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        if (referential != null) {
            return referential;
        }
        Long providerId = message.getHeader(PROVIDER_ID, Long.class);
        return providerId == null ? null : providerRepository.getReferential(providerId);
    }

    private static void deleteDirectoryRecursively(File directory) {
        try {
            FileSystemUtils.deleteRecursively(Paths.get(directory.getPath()));
        } catch (IOException e) {
            LOGGER.warn("Failed to delete directory {}", directory, e);
        }
    }
}
