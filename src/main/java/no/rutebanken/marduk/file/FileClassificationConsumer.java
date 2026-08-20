package no.rutebanken.marduk.file;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.flexlines.FlexibleLinesImport;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.file.MardukFileUtils;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.AntuValidation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.JOB_ERROR_CODE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Decides what a newly stored file is, and starts the right import for it.
 *
 * <p>Replaces {@code FileClassificationRouteBuilder}'s consumer and the three routes only it called.
 *
 * <p>Every way a file can be unusable reports {@code FILE_CLASSIFICATION FAILED} with its own error code,
 * because that code is what the operator sees in nabu. A name with characters the rest of the pipeline cannot
 * carry is the one recoverable case: the file is renamed and put back on the queue.
 */
@Component
public class FileClassificationConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileClassificationConsumer.class);

    private final MardukInternalBlobStoreService internalBlobStore;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final AntuValidation antuValidation;
    private final FlexibleLinesImport flexibleLinesImport;
    private final FileTypeClassifierBean fileTypeClassifier = new FileTypeClassifierBean();
    private final boolean chouettePreValidationEnabled;

    public FileClassificationConsumer(
            MardukInternalBlobStoreService internalBlobStore,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            AntuValidation antuValidation,
            FlexibleLinesImport flexibleLinesImport,
            @Value("${chouette.enablePreValidation:true}") boolean chouettePreValidationEnabled) {
        this.internalBlobStore = internalBlobStore;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.antuValidation = antuValidation;
        this.flexibleLinesImport = flexibleLinesImport;
        this.chouettePreValidationEnabled = chouettePreValidationEnabled;
    }

    @Override
    protected String destination() {
        return MardukQueues.PROCESS_FILE_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        jobEvents.reportProviderJob(message, b -> b
                .timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.OK));
        jobEvents.reportProviderJob(message, b -> b
                .timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.STARTED));

        byte[] data = read(internalBlobStore.getBlob(fileHandle), fileHandle);
        if (!fileTypeClassifier.validateFile(data, message)) {
            // The ValidationException path: not a file type we recognise at all, so there is nothing to
            // report beyond the failure, and no point redelivering it.
            LOGGER.info("Could not process file {}", fileHandle);
            reportClassificationFailed(message, null);
            message.setBody("");
            publisher.publish(MardukQueues.MARDUK_DEAD_LETTER_QUEUE, message);
            return;
        }

        FileType fileType = FileType.valueOf(message.getHeader(FILE_TYPE, String.class));
        switch (fileType) {
            case UNKNOWN_FILE_EXTENSION -> reject(message, fileHandle,
                    "does not end with a .zip or .ZIP extension", JobEvent.JOB_ERROR_FILE_UNKNOWN_FILE_EXTENSION);
            case UNKNOWN_FILE_TYPE -> reject(message, fileHandle,
                    "cannot be processed: unknown file type", JobEvent.JOB_ERROR_UNKNOWN_FILE_TYPE);
            case NOT_A_ZIP_FILE -> reject(message, fileHandle,
                    "is not a valid zip archive", JobEvent.JOB_ERROR_FILE_NOT_A_ZIP_FILE);
            case ZIP_CONTAINS_SUBDIRECTORIES -> reject(message, fileHandle,
                    "contains one or more subdirectories", JobEvent.JOB_ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES);
            case INVALID_ZIP_FILE_ENTRY_CONTENT_ENCODING -> reject(message, fileHandle,
                    "contains one or more invalid XML files: invalid encoding", JobEvent.JOB_ERROR_INVALID_XML_ENCODING);
            case INVALID_ZIP_FILE_ENTRY_XML_CONTENT -> reject(message, fileHandle,
                    "contains one or more invalid XML files: unparseable XML", JobEvent.JOB_ERROR_INVALID_XML_CONTENT);
            case INVALID_ZIP_FILE_ENTRY_NAME_ENCODING -> reject(message, fileHandle,
                    "contains one or more invalid zip entry names: invalid encoding",
                    JobEvent.JOB_ERROR_INVALID_ZIP_ENTRY_ENCODING);
            case INVALID_FILE_NAME -> sanitizeFileName(message, data);
            case GTFS, NETEXPROFILE -> processValidFile(message);
        }
    }

    /**
     * Renames the file to something the rest of the pipeline can carry and puts it back on the queue, so it
     * comes round again and classifies on its content this time.
     */
    private void sanitizeFileName(MardukMessage message, byte[] data) {
        LOGGER.warn("File with invalid characters in file name {}", message.getHeader(FILE_HANDLE, String.class));
        String sanitized = MardukFileUtils.sanitizeFileName(message.getHeader(FILE_NAME, String.class));
        String referential = providerRepository.getReferential(message.getHeader(PROVIDER_ID, Long.class));
        String newHandle = Constants.BLOBSTORE_PATH_INBOUND + referential + "/" + sanitized;

        message.setHeader(FILE_HANDLE, newHandle);
        message.setHeader(FILE_NAME, sanitized);
        LOGGER.info("Uploading file with new file name {}", newHandle);
        message.setHeader(Constants.FILE_VERSION,
                internalBlobStore.uploadBlob(newHandle, new ByteArrayInputStream(data)));
        // The queue carries the handle, not the file: republishing the bytes would blow the message limit.
        message.setBody("");
        publisher.publish(MardukQueues.PROCESS_FILE_QUEUE, message);
    }

    private void processValidFile(MardukMessage message) {
        message.setBody("");
        jobEvents.reportProviderJob(message, b -> b
                .timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.OK));
        message.setBody("");

        if (IMPORT_TYPE_NETEX_FLEX.equals(message.getHeader(IMPORT_TYPE, String.class))) {
            flexibleLinesImport.start(message);
            return;
        }

        antuValidation.requestPreValidation(message);

        // Chouette imports GTFS itself, and does its own pre-validation of NeTEx when that is switched on.
        boolean gtfs = FileType.GTFS.name().equals(message.getHeader(FILE_TYPE, String.class));
        if (chouettePreValidationEnabled || gtfs) {
            LOGGER.info("Posting {} {} and {} {} on chouette import queue.",
                    FILE_HANDLE, message.getHeader(FILE_HANDLE, String.class),
                    FILE_TYPE, message.getHeader(FILE_TYPE, String.class));
            publisher.publish(MardukQueues.CHOUETTE_IMPORT_QUEUE, message);
        }
    }

    private void reject(MardukMessage message, String fileHandle, String why, String errorCode) {
        LOGGER.warn("The file {} {}", fileHandle, why);
        reportClassificationFailed(message, errorCode);
    }

    private void reportClassificationFailed(MardukMessage message, String errorCode) {
        if (errorCode != null) {
            message.setHeader(JOB_ERROR_CODE, errorCode);
        }
        jobEvents.reportProviderJob(message, b -> b
                .timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION).state(JobEvent.State.FAILED));
    }

    private static byte[] read(InputStream blob, String fileHandle) {
        if (blob == null) {
            throw new UncheckedIOException(new IOException("No file found at " + fileHandle));
        }
        try (InputStream contents = blob) {
            return contents.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + fileHandle, e);
        }
    }
}
