package no.rutebanken.marduk.upload;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_APPLY_DUPLICATES_FILTER;
import static no.rutebanken.marduk.Constants.FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_VERSION;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USERNAME;

/**
 * Stores an uploaded timetable file and starts the import pipeline for it.
 *
 * <p>Both HTTP entry points funnel through here, so the multipart stream is read exactly once and in one
 * place: it is drained to a temporary file before anything else touches it, because a servlet's stream is
 * gone by the time the blob upload would want it and the digest needs to read it twice.
 *
 * <p>Replaces {@code direct:uploadFileAndStartImport} and {@code direct:processFileAfterImport}. Always to
 * disk, where Camel's stream caching kept files under its spool threshold in memory - a little more I/O for
 * a small file in exchange for one code path at any size, on an endpoint that accepts 150MB.
 */
@Component
public class TimetableFileUploader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimetableFileUploader.class);

    /**
     * What the pipeline needs to know about the upload beyond the file itself.
     *
     * @param referential                     the value the file handle is built from, and the referential
     *                                        the rest of the pipeline sees
     * @param importType                      {@code null} for an ordinary import
     * @param applyDuplicatesFilter           whether a file already seen should be rejected
     * @param applyDuplicatesFilterOnNameOnly compare on the name alone, ignoring the content
     */
    public record Upload(
            String referential,
            Long providerId,
            String correlationId,
            String username,
            String importType,
            boolean applyDuplicatesFilter,
            boolean applyDuplicatesFilterOnNameOnly) {
    }

    private final MardukInternalBlobStoreService internalBlobStore;
    private final ProviderRepository providerRepository;
    private final DuplicateFileFilter duplicateFileFilter;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;

    public TimetableFileUploader(
            MardukInternalBlobStoreService internalBlobStore,
            ProviderRepository providerRepository,
            DuplicateFileFilter duplicateFileFilter,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher) {
        this.internalBlobStore = internalBlobStore;
        this.providerRepository = providerRepository;
        this.duplicateFileFilter = duplicateFileFilter;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
    }

    /** @throws MardukException if the file could not be stored, so the caller answers with a failure */
    public void upload(MultipartFile file, Upload upload) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file provided");
        }
        String fileName = file.getOriginalFilename();
        LOGGER.debug("[{}] Processing file: name={}, size={}, contentType={}",
                upload.correlationId(), fileName, file.getSize(), file.getContentType());

        store(describe(upload, fileName), fileName, file);
    }

    /**
     * Uploads every file of a multi-file request, one file's failure not touching the next.
     *
     * <p>The splitter this replaces processed each part on its own, so the files around a failing one were
     * still stored and started. Each file's outcome is its own job event; the response says nothing about
     * them, as it did not before either.
     */
    public void uploadAll(List<MultipartFile> files, Upload upload) {
        for (MultipartFile file : files) {
            try {
                upload(file, upload);
            } catch (RuntimeException e) {
                LOGGER.warn("Upload failed for {}, continuing with the remaining files",
                        file == null ? null : file.getOriginalFilename(), e);
            }
        }
    }

    private void store(MardukMessage message, String fileName, MultipartFile file) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.STARTED));

        Path content = null;
        DuplicateFileFilter.Claim claim = null;
        try {
            content = drainToTempFile(file, fileName);
            claim = duplicateFileFilter.claim(message, fileName, content);
            if (claim.duplicate()) {
                return;
            }
            String fileHandle = message.getHeader(FILE_HANDLE, String.class);
            LOGGER.info("Uploading timetable file to blob store: {}", fileHandle);
            try (InputStream bytes = Files.newInputStream(content)) {
                message.setHeader(FILE_VERSION, internalBlobStore.uploadBlob(fileHandle, bytes));
            }
            LOGGER.info("Finished uploading timetable file to blob store: {}", fileHandle);
            startImportIfEnabled(message);
        } catch (IOException | RuntimeException e) {
            fail(message, claim);
            throw new MardukException("Upload failed for " + fileName, e);
        } finally {
            deleteQuietly(content);
        }
    }

    /**
     * A provider with auto import switched off wants the file kept but not processed, which is reported as a
     * cancelled transfer rather than a successful one.
     */
    private void startImportIfEnabled(MardukMessage message) {
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        if (providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class))
                .getChouetteInfo().isEnableAutoImport()) {
            message.setBody("");
            publisher.publish(MardukQueues.PROCESS_FILE_QUEUE, message);
            LOGGER.info("Triggered import pipeline for timetable file: {}", fileHandle);
            return;
        }
        LOGGER.info("Do not initiate processing of {} as autoImport is not enabled for provider", fileHandle);
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.CANCELLED));
    }

    private void fail(MardukMessage message, DuplicateFileFilter.Claim claim) {
        LOGGER.warn("Upload of timetable data to blob store failed for file: {}",
                message.getHeader(FILE_HANDLE));
        if (claim != null) {
            duplicateFileFilter.release(claim);
        }
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED));
    }

    private static MardukMessage describe(Upload upload, String fileName) {
        MardukMessage message = new MardukMessage()
                .setHeader(CHOUETTE_REFERENTIAL, upload.referential())
                .setHeader(PROVIDER_ID, upload.providerId())
                .setHeader(CORRELATION_ID, upload.correlationId())
                .setHeader(USERNAME, upload.username())
                .setHeader(FILE_APPLY_DUPLICATES_FILTER, upload.applyDuplicatesFilter())
                .setHeader(FILE_NAME, fileName)
                .setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND + upload.referential() + "/" + fileName);
        if (upload.applyDuplicatesFilterOnNameOnly()) {
            message.setHeader(FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY, true);
        }
        message.setHeaderIfPresent(IMPORT_TYPE, upload.importType());
        return message;
    }

    private static Path drainToTempFile(MultipartFile file, String fileName) {
        try {
            Path target = Files.createTempFile("marduk-upload-", ".zip");
            try (InputStream bytes = file.getInputStream()) {
                Files.copy(bytes, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new MardukException("Failed to read the uploaded file " + fileName, e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Worth knowing about: these are up to 150MB and the endpoint is used all day.
            LOGGER.warn("Could not delete the temporary upload {}", file, e);
        }
    }
}
