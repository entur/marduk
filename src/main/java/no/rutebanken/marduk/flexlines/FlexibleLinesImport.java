package no.rutebanken.marduk.flexlines;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_IMPORT_TIMETABLE_FLEX;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_FLEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;

/**
 * Asks antu to validate a flexible-lines NeTEx archive uploaded through the operator portal.
 *
 * <p>The Uttu path is {@link FlexibleLinesExportConsumer}; this one differs in where the file sits - the
 * internal bucket rather than the exchange bucket - and in the profile, which tells antu the archive is an
 * import rather than an export.
 *
 * <p>Replaces the body of {@code direct:flexibleLinesImport}.
 */
@Component
public class FlexibleLinesImport {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlexibleLinesImport.class);

    private final ProviderRepository providerRepository;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final String antuExchangeContainer;

    public FlexibleLinesImport(
            ProviderRepository providerRepository,
            MardukInternalBlobStoreService internalBlobStore,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${blobstore.gcs.antu.exchange.container.name}") String antuExchangeContainer) {
        this.providerRepository = providerRepository;
        this.internalBlobStore = internalBlobStore;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.antuExchangeContainer = antuExchangeContainer;
    }

    public void start(MardukMessage message) {
        LOGGER.info("Post-validating flexible NeTEx dataset");
        String fileHandle = message.getHeader(FILE_HANDLE, String.class);
        message.setHeader(TARGET_CONTAINER, antuExchangeContainer);
        message.setHeader(TARGET_FILE_HANDLE, fileHandle);
        internalBlobStore.copyBlobToAnotherBucket(fileHandle, antuExchangeContainer, fileHandle);

        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        message.setHeader(DATASET_REFERENTIAL, provider.getChouetteInfo().getReferential());

        message.setHeader(VALIDATION_STAGE_HEADER, VALIDATION_STAGE_FLEX_POSTVALIDATION);
        message.setHeader(VALIDATION_CLIENT_HEADER, VALIDATION_CLIENT_MARDUK);
        message.setHeader(VALIDATION_PROFILE_HEADER, VALIDATION_PROFILE_IMPORT_TIMETABLE_FLEX);
        message.setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER, fileHandle);
        message.setHeader(VALIDATION_CORRELATION_ID_HEADER, message.getHeader(CORRELATION_ID, String.class));
        message.setHeader(VALIDATION_IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX);
        publisher.publish(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE, message);

        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION)
                .state(JobEvent.State.PENDING)
                .jobId(null));
    }
}
