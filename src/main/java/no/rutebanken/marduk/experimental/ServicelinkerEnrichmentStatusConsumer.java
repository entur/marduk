package no.rutebanken.marduk.experimental;

import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.LINKED_NETEX_FILE_PATH_HEADER;
import static no.rutebanken.marduk.Constants.LINKING_ERROR_CODE_HEADER;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_FAILED;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_HEADER;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_STARTED;
import static no.rutebanken.marduk.Constants.LINKING_NETEX_FILE_STATUS_SUCCEEDED;
import static no.rutebanken.marduk.Constants.LINKING_STATUS_EVENT_TIME_HEADER;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.SOURCE_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

/**
 * Handles servicelinker's verdict on an enrichment job and continues the import into Ashur filtering.
 *
 * <p>Enrichment is best effort: a failure is reported and the flow carries on with the file that was sent,
 * because a dataset without generated service links is still importable. Only a status nobody recognises
 * stops the import here.
 *
 * <p>Replaces the PubSub route in {@code ServicelinkerEnrichmentStatusRouteBuilder}; the enrichment request
 * it also held is now {@link ExperimentalImportPath#enrichThenFilter}.
 */
@Component
public class ServicelinkerEnrichmentStatusConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServicelinkerEnrichmentStatusConsumer.class);

    private final ProviderRepository providerRepository;
    private final ExperimentalImportPath experimentalImportPath;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final JobEventPublisher jobEvents;
    private final String servicelinkerExchangeContainer;

    public ServicelinkerEnrichmentStatusConsumer(
            ProviderRepository providerRepository,
            ExperimentalImportPath experimentalImportPath,
            MardukInternalBlobStoreService internalBlobStore,
            JobEventPublisher jobEvents,
            @Value("${blobstore.gcs.servicelinker.exchange.container.name}") String servicelinkerExchangeContainer) {
        this.providerRepository = providerRepository;
        this.experimentalImportPath = experimentalImportPath;
        this.internalBlobStore = internalBlobStore;
        this.jobEvents = jobEvents;
        this.servicelinkerExchangeContainer = servicelinkerExchangeContainer;
    }

    @Override
    protected String destination() {
        return MardukQueues.SERVICELINKER_STATUS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        String referential = message.getHeader(DATASET_REFERENTIAL, String.class);
        message.setHeader(PROVIDER_ID, providerRepository.getProviderId(referential));
        message.setHeader(CHOUETTE_REFERENTIAL, referential);
        MardukMdc.set(message);

        String status = message.getHeader(LINKING_NETEX_FILE_STATUS_HEADER, String.class);
        switch (status) {
            case LINKING_NETEX_FILE_STATUS_SUCCEEDED -> {
                LOGGER.info("Received notification that Servicelinker enrichment has succeeded. File location: {}",
                        message.getHeader(LINKED_NETEX_FILE_PATH_HEADER, String.class));
                copyEnrichedDatasetToInternalBucket(message);
                report(message, JobEvent.State.OK);
            }
            case LINKING_NETEX_FILE_STATUS_STARTED -> {
                LOGGER.info("Received notification that Servicelinker enrichment has started.");
                report(message, JobEvent.State.STARTED);
                return;
            }
            case LINKING_NETEX_FILE_STATUS_FAILED -> {
                LOGGER.warn("Received notification that Servicelinker enrichment has failed. Continuing with original file.");
                reportFailed(message);
            }
            case null, default -> {
                LOGGER.error("Received notification with unknown Servicelinker linking status: {}", status);
                return;
            }
        }
        experimentalImportPath.filterAfterPreValidation(message);
    }

    /** Keeps the enriched file at its own path, leaving the file that was sent for enrichment untouched. */
    private void copyEnrichedDatasetToInternalBucket(MardukMessage message) {
        String enriched = message.getHeader(LINKED_NETEX_FILE_PATH_HEADER, String.class);
        message.setHeader(FILE_HANDLE, enriched);
        message.setHeader(TARGET_FILE_HANDLE, enriched);
        message.setHeader(SOURCE_CONTAINER, servicelinkerExchangeContainer);
        internalBlobStore.copyBlobFromAnotherBucket(servicelinkerExchangeContainer, enriched, enriched);
    }

    private void report(MardukMessage message, JobEvent.State state) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.LINKING)
                .state(state)
                .eventTime(eventTime(message)));
    }

    /**
     * Reports servicelinker's own failure reason, overriding the error code the builder reads off the
     * message so that a stale {@code RutebankenJobErrorCode} cannot be reported as the reason linking failed.
     */
    private void reportFailed(MardukMessage message) {
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.LINKING)
                .state(JobEvent.State.FAILED)
                .errorCode(message.getHeader(LINKING_ERROR_CODE_HEADER, String.class))
                .eventTime(eventTime(message)));
    }

    /**
     * Servicelinker stamps each status message with the instant it emitted the status. Using that as the
     * JobEvent event time keeps STARTED ordered before SUCCESS even when PubSub delivers the two messages
     * out of order. Falls back to null, i.e. now, when the attribute is missing or unparseable - a message
     * from an older servicelinker, for instance.
     */
    private static Instant eventTime(MardukMessage message) {
        String value = message.getHeader(LINKING_STATUS_EVENT_TIME_HEADER, String.class);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            LOGGER.warn("Could not parse {} header value '{}', falling back to receive time",
                    LINKING_STATUS_EVENT_TIME_HEADER, value);
            return null;
        }
    }
}
