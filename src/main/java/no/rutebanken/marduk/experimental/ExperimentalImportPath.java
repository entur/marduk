package no.rutebanken.marduk.experimental;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_MARDUK;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_STANDARD_IMPORT;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

/**
 * The two hops the experimental import takes between antu's pre-validation and antu's post-validation:
 * servicelinker adds the missing service links, then Ashur filters the dataset down to what may be imported.
 *
 * <p>Both are here rather than one per consumer because the antu status handling enters at the first hop and
 * the servicelinker status handling at the second, and the first falls through to the second whenever
 * servicelinker is skipped.
 *
 * <p>Was {@code direct:servicelinkerEnrichAfterPreValidation} and
 * {@code direct:ashurNetexFilterAfterPreValidation}.
 */
@Component
public class ExperimentalImportPath {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExperimentalImportPath.class);

    private final ExperimentalImportHelpers helpers;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final MardukPubSubPublisher publisher;
    private final JobEventPublisher jobEvents;
    private final boolean linkingEnabled;
    private final String exchangeContainer;

    public ExperimentalImportPath(
            ExperimentalImportHelpers helpers,
            MardukInternalBlobStoreService internalBlobStore,
            MardukPubSubPublisher publisher,
            JobEventPublisher jobEvents,
            @Value("${servicelinker.linkingEnabled:false}") boolean linkingEnabled,
            @Value("${blobstore.gcs.exchange.container.name}") String exchangeContainer) {
        this.helpers = helpers;
        this.internalBlobStore = internalBlobStore;
        this.publisher = publisher;
        this.jobEvents = jobEvents;
        this.linkingEnabled = linkingEnabled;
        this.exchangeContainer = exchangeContainer;
    }

    /**
     * Hands the pre-validated dataset to servicelinker, or goes straight on to Ashur when servicelinker has
     * nothing to do. Servicelinker answers asynchronously; {@link ServicelinkerEnrichmentStatusConsumer}
     * picks the flow up again and calls {@link #filterAfterPreValidation}.
     */
    public void enrichThenFilter(MardukMessage message) {
        String referential = message.getHeader(DATASET_REFERENTIAL, String.class);
        if (!linkingEnabled) {
            LOGGER.info("Servicelinker linking is disabled, skipping enrichment");
            filterAfterPreValidation(message);
            return;
        }
        if (helpers.shouldSkipServicelinker(message)) {
            LOGGER.info("Service link modes explicitly set to empty for referential {}, skipping servicelinker", referential);
            filterAfterPreValidation(message);
            return;
        }

        LOGGER.info("Triggering Servicelinker enrichment for referential {}", referential);
        String forServicelinker = helpers.pathToNetexForServicelinker(message);
        message.setHeader(TARGET_FILE_HANDLE, forServicelinker);
        message.setHeader(TARGET_CONTAINER, exchangeContainer);
        helpers.setServiceLinkModesHeader(message);
        internalBlobStore.copyBlobToAnotherBucket(
                message.getHeader(FILE_HANDLE, String.class), exchangeContainer, forServicelinker);
        publisher.publish(MardukQueues.SERVICELINKER_INBOUND_QUEUE, message);
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.LINKING).state(JobEvent.State.PENDING));
        LOGGER.info("Done sending to Servicelinker for enrichment");
    }

    /**
     * Hands the dataset to Ashur for standard import filtering, and saves a copy of what went in so the
     * block export can filter the same input again later.
     */
    public void filterAfterPreValidation(MardukMessage message) {
        if (message.getHeader(FILTERING_FILE_CREATED_TIMESTAMP, String.class) == null) {
            // The Camel route built a CANCELLED filtering event here, but the route ended without an
            // updateStatus step, so the cancellation never reached nabu. Left as it was.
            LOGGER.error("Cancelled triggering of filtering because no created timestamp was found for file name: {}",
                    message.getHeader(FILE_NAME, String.class));
            return;
        }

        // The rb_ prefix goes on only now, after the pre-validation status has been sent to nabu: the links
        // to antu's pre-validation reports are built from the unprefixed referential.
        message.setHeader(DATASET_REFERENTIAL, "rb_" + message.getHeader(DATASET_REFERENTIAL, String.class));
        message.setHeader(CHOUETTE_REFERENTIAL, "rb_" + message.getHeader(CHOUETTE_REFERENTIAL, String.class));
        LOGGER.info("Updated value of dataset referential header: {}", message.getHeader(DATASET_REFERENTIAL, String.class));
        LOGGER.info("Updated value of chouette referential header: {}", message.getHeader(CHOUETTE_REFERENTIAL, String.class));

        String dataset = message.getHeader(FILE_HANDLE, String.class);

        // The pre-filtering file is either servicelinker's output or the original, and the block export
        // needs it after the standard filtering and post-validation hops have moved FILE_HANDLE on.
        String blockExportInput = helpers.pathToPreFilteringNetexForBlockExport(message);
        message.setHeader(TARGET_FILE_HANDLE, blockExportInput);
        internalBlobStore.copyBlobInBucket(dataset, blockExportInput);

        String forAshur = helpers.pathToNetexForAshurFiltering(message);
        message.setHeader(TARGET_FILE_HANDLE, forAshur);
        message.setHeader(TARGET_CONTAINER, exchangeContainer);
        internalBlobStore.copyBlobToAnotherBucket(dataset, exchangeContainer, forAshur);

        message.setHeader(FILTERING_PROFILE_HEADER, FILTERING_PROFILE_STANDARD_IMPORT);
        message.setHeader(FILTERING_NETEX_SOURCE_HEADER, FILTERING_NETEX_SOURCE_MARDUK);
        publisher.publish(MardukQueues.FILTER_NETEX_FILE_QUEUE, message);
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.FILTERING).state(JobEvent.State.PENDING));
        LOGGER.info("Done sending to Ashur for filtering");
    }
}
