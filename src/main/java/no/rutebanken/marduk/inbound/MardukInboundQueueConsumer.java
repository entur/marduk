package no.rutebanken.marduk.inbound;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.file.beans.FileTypeClassifierBean;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Takes a file another system has put in the exchange bucket and moves it into marduk's own.
 *
 * <p>This is the entry point for uploads that do not come through marduk's REST API: the notifying system
 * writes the file and publishes its handle, and marduk fetches, classifies and re-stores it before handing
 * off to classification.
 *
 * <p>Replaces {@code InboundQueueRouteBuilder}. A file that cannot be classified - including one the
 * notification points at that is not there - goes to the dead letter queue with an empty body, as it did
 * under {@code onException(ValidationException.class).handled(true)}: handled, so the message is acked
 * rather than redelivered against a file that will never classify.
 */
@Component
public class MardukInboundQueueConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MardukInboundQueueConsumer.class);

    private final ExchangeBlobStoreService exchangeBlobStore;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final ProviderRepository providerRepository;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final FileTypeClassifierBean fileTypeClassifier;
    private final boolean deleteExternalBlobs;

    public MardukInboundQueueConsumer(
            ExchangeBlobStoreService exchangeBlobStore,
            MardukInternalBlobStoreService internalBlobStore,
            ProviderRepository providerRepository,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${blobstore.delete.external.blobs:true}") boolean deleteExternalBlobs) {
        this.exchangeBlobStore = exchangeBlobStore;
        this.internalBlobStore = internalBlobStore;
        this.providerRepository = providerRepository;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.fileTypeClassifier = new FileTypeClassifierBean();
        this.deleteExternalBlobs = deleteExternalBlobs;
    }

    @Override
    protected String destination() {
        return MardukQueues.MARDUK_INBOUND_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        LOGGER.info("Received notification about file '{}' on inbound queue. Fetching file ...",
                message.getHeader(Constants.FILE_NAME, String.class));
        LOGGER.info("Fetching blob {}", fileHandle);

        byte[] data = fetch(fileHandle);
        setChouetteReferential(message);

        if (data == null || !fileTypeClassifier.validateFile(data, message)) {
            LOGGER.info("Could not process file {}", fileHandle);
            deadLetter(message);
            return;
        }

        LOGGER.info("File handle is: {}", fileHandle);
        message.setHeader(Constants.FILE_VERSION,
                internalBlobStore.uploadBlob(fileHandle, new ByteArrayInputStream(data)));
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.FILE_TRANSFER)
                .state(JobEvent.State.STARTED));

        if (deleteExternalBlobs) {
            LOGGER.info("Deleting blob {} from external blob store.", fileHandle);
            exchangeBlobStore.deleteBlob(fileHandle);
        }

        // Empty, unlike the Camel version, which published whatever the previous step had left on the
        // exchange - the job event JSON, or the boolean from the blob delete. Nothing reads it:
        // classification replaces the body with the blob it fetches by handle.
        message.setBody("");
        publisher.publish(MardukQueues.PROCESS_FILE_QUEUE, message);
    }

    private void deadLetter(MardukMessage message) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.FILE_CLASSIFICATION)
                .state(JobEvent.State.FAILED));
        message.setBody("");
        publisher.publish(MardukQueues.MARDUK_DEAD_LETTER_QUEUE, message);
    }

    private void setChouetteReferential(MardukMessage message) {
        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        String referential = provider.getChouetteInfo().getReferential();
        message.setHeader(CHOUETTE_REFERENTIAL, referential);
        MardukMdc.setCodespaceIfMissing(referential);
    }

    /**
     * @return the file's bytes, or null when the notification points at nothing - no handle, or no blob
     *         under it. A blob that is there but cannot be read throws, so the message is redelivered.
     */
    private byte[] fetch(String fileHandle) {
        if (fileHandle == null || fileHandle.isBlank()) {
            return null;
        }
        InputStream blob = exchangeBlobStore.getBlob(fileHandle);
        if (blob == null) {
            return null;
        }
        try (InputStream contents = blob) {
            return contents.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + fileHandle, e);
        }
    }
}
