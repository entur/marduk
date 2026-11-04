package no.rutebanken.marduk.flexlines;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_UTTU_EXPORT;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_FLEX;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_FLEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;

/**
 * Receives Uttu's notification of a new flexible-lines NeTEx export and asks antu to validate it before the
 * merge with the Chouette export.
 *
 * <p>The archive is successively copied into three folders: the inbound folder Uttu uploads to, the validation
 * folder antu reads, and - once antu approves - the outbound folder the merge reads. The separate inbound and
 * outbound folders are what keeps a non-validated file from being merged, since the merge can be triggered by
 * either a Chouette export or an Uttu export.
 *
 * <p>Replaces {@code NetexFlexibleLinesExportRouteBuilder} and {@code direct:antuFlexibleNetexPostValidation}.
 * The request is built here rather than through {@code AntuValidation} because this file lives in the exchange
 * bucket, not the internal one, and the profile is fixed rather than chosen from the codespace.
 */
@Component
public class FlexibleLinesExportConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlexibleLinesExportConsumer.class);

    private static final String BLOBSTORE_PATH_UTTU = "inbound/uttu/";

    private final ProviderRepository providerRepository;
    private final ExchangeBlobStoreService exchangeBlobStore;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final String antuExchangeContainer;

    public FlexibleLinesExportConsumer(
            ProviderRepository providerRepository,
            ExchangeBlobStoreService exchangeBlobStore,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${blobstore.gcs.antu.exchange.container.name}") String antuExchangeContainer) {
        this.providerRepository = providerRepository;
        this.exchangeBlobStore = exchangeBlobStore;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.antuExchangeContainer = antuExchangeContainer;
    }

    @Override
    protected String destination() {
        return MardukQueues.FLEXIBLE_LINES_EXPORT_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        String uttuFileName = message.getBody(String.class);
        LOGGER.info("Received notification from Uttu of new flexible NeTEx dataset {}", uttuFileName);

        String referential = message.getHeader(CHOUETTE_REFERENTIAL, String.class);
        Long providerId = providerRepository.getProviderId(referential);
        if (providerId == null) {
            // An unknown referential cannot succeed on redelivery, so ack and drop the notification instead
            // of failing and having Pub/Sub redeliver it forever.
            LOGGER.warn("Ignoring flexible lines export notification for unknown referential {}", referential);
            return;
        }
        message.setHeader(PROVIDER_ID, providerId);

        Provider provider = providerRepository.getProvider(providerId);
        message.setHeader(DATASET_REFERENTIAL, provider.getChouetteInfo().getReferential());
        message.setHeader(FILE_HANDLE, BLOBSTORE_PATH_UTTU + uttuFileName);

        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX)
                .state(JobEvent.State.OK)
                .jobId(null));

        requestPostValidation(message);
    }

    private void requestPostValidation(MardukMessage message) {
        LOGGER.info("Post-validating flexible NeTEx dataset");
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        message.setHeader(TARGET_CONTAINER, antuExchangeContainer);
        message.setHeader(TARGET_FILE_HANDLE, fileHandle);
        exchangeBlobStore.copyBlobToAnotherBucket(fileHandle, antuExchangeContainer, fileHandle);

        message.setHeader(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_FLEX_POSTVALIDATION);
        message.setHeader(VALIDATION_CLIENT_HEADER, VALIDATION_CLIENT_MARDUK);
        message.setHeader(VALIDATION_PROFILE_HEADER, VALIDATION_PROFILE_TIMETABLE_FLEX);
        message.setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER, message.getHeader(TARGET_FILE_HANDLE, String.class));
        message.setHeader(VALIDATION_CORRELATION_ID_HEADER, message.getHeader(CORRELATION_ID, String.class));
        message.setHeader(VALIDATION_IMPORT_TYPE, IMPORT_TYPE_UTTU_EXPORT);
        publisher.publish(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE, message);

        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION)
                .state(JobEvent.State.PENDING)
                .jobId(null));
    }
}
