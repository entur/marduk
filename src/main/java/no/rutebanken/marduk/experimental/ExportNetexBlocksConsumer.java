package no.rutebanken.marduk.experimental;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_NETEX_SOURCE_MARDUK;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

/**
 * Asks Ashur for a second, blocks-included filtering of the dataset the standard import filter already saw.
 *
 * <p>The input is the file saved before the standard filtering, not the filtered output: the standard filter
 * strips blocks, so filtering its output again could never produce them.
 *
 * <p>Replaces {@code ExportNetexBlocksQueueRouteBuilder}.
 */
@Component
public class ExportNetexBlocksConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportNetexBlocksConsumer.class);

    private final ProviderRepository providerRepository;
    private final ExperimentalImportHelpers helpers;
    private final MardukInternalBlobStoreService internalBlobStore;
    private final MardukPubSubPublisher publisher;
    private final JobEventPublisher jobEvents;
    private final String exchangeContainer;

    public ExportNetexBlocksConsumer(
            ProviderRepository providerRepository,
            ExperimentalImportHelpers helpers,
            MardukInternalBlobStoreService internalBlobStore,
            MardukPubSubPublisher publisher,
            JobEventPublisher jobEvents,
            @Value("${blobstore.gcs.exchange.container.name}") String exchangeContainer) {
        this.providerRepository = providerRepository;
        this.helpers = helpers;
        this.internalBlobStore = internalBlobStore;
        this.publisher = publisher;
        this.jobEvents = jobEvents;
        this.exchangeContainer = exchangeContainer;
    }

    @Override
    protected String destination() {
        return MardukQueues.EXPORT_NETEX_BLOCKS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        if (!providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class))
                .getChouetteInfo().isEnableBlocksExport()) {
            LOGGER.info("Skipping export of NetEx blocks to Ashur after post-validation because provider has blocks export disabled");
            return;
        }

        String referential = message.getHeader(DATASET_REFERENTIAL, String.class);
        LOGGER.info("Export blocks flag enabled. Starting export with experimental import");
        String preFiltering = helpers.pathToPreFilteringNetexForBlockExport(message);
        String forAshur = helpers.pathToNetexWithBlocksForAshurFiltering(message);
        message.setHeader(FILE_HANDLE, preFiltering);
        message.setHeader(TARGET_FILE_HANDLE, forAshur);
        message.setHeader(TARGET_CONTAINER, exchangeContainer);
        LOGGER.info("Copying file with blocks to exchange bucket for Ashur filtering");
        internalBlobStore.copyBlobToAnotherBucket(preFiltering, exchangeContainer, forAshur);

        LOGGER.info("Triggering Ashur filtering for block export for referential {}", referential);
        message.setHeader(FILTERING_PROFILE_HEADER, FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS);
        message.setHeader(FILTERING_NETEX_SOURCE_HEADER, FILTERING_NETEX_SOURCE_MARDUK);
        publisher.publish(MardukQueues.FILTER_NETEX_FILE_QUEUE, message);
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS).state(JobEvent.State.PENDING));
        LOGGER.info("Done sending to Ashur for block export.");
    }
}
