package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.GTFS_ROUTE_DISPATCHER_EXPORT_HEADER_VALUE;
import static no.rutebanken.marduk.Constants.GTFS_ROUTE_DISPATCHER_HEADER_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

/**
 * Publishes a merged NeTEx dataset and tells the downstream consumers about it: the OTP2 graph build, the
 * NeTEx export notification topic, damu's GTFS export and, when enabled, the line statistics calculation.
 *
 * <p>Replaces {@code direct:publishMergedDataset} and the two routes only it called.
 */
@Component
public class MergedNetexPublication {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergedNetexPublication.class);

    private final ProviderRepository providerRepository;
    private final DatedExportUpload datedExportUpload;
    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final boolean useChouetteGtfsExport;
    private final boolean lineStatisticsCalculationEnabled;

    public MergedNetexPublication(
            ProviderRepository providerRepository,
            DatedExportUpload datedExportUpload,
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${gtfs.export.chouette:true}") boolean useChouetteGtfsExport,
            @Value("${line.statistics.calculation.enabled:false}") boolean lineStatisticsCalculationEnabled) {
        this.providerRepository = providerRepository;
        this.datedExportUpload = datedExportUpload;
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.useChouetteGtfsExport = useChouetteGtfsExport;
        this.lineStatisticsCalculationEnabled = lineStatisticsCalculationEnabled;
    }

    public void publishMergedDataset(MardukMessage message) {
        Provider provider = providerRepository.getProvider(message.getHeader(PROVIDER_ID, Long.class));
        if (provider.getChouetteInfo().isGenerateDatedServiceJourneyIds()) {
            datedExportUpload.copyDatedExport(message);
        }

        // Was a wireTap, so the notification gets a copy: it strips every header before publishing.
        notifyExportNetexWithFlexibleLines(message.copy());

        message.setBody("");
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.OTP2_BUILD_GRAPH)
                .state(JobEvent.State.PENDING));
        LOGGER.info("FlexibleLines merging OK, triggering OTP graph build.");
        publisher.publish(MardukQueues.OTP2_GRAPH_BUILD_QUEUE, message);

        startDamuGtfsExport(message);

        if (lineStatisticsCalculationEnabled) {
            publisher.publish(MardukQueues.LINE_STATISTICS_CALCULATION_QUEUE, message);
        }
    }

    private void notifyExportNetexWithFlexibleLines(MardukMessage notification) {
        notification.setBody(notification.getHeader(CHOUETTE_REFERENTIAL, String.class).replace("rb_", ""));
        notification.removeAllHeaders();
        publisher.publish(MardukQueues.NETEX_EXPORT_NOTIFICATION_QUEUE, notification);
    }

    private void startDamuGtfsExport(MardukMessage message) {
        LOGGER.info("Triggering GTFS export in Damu.");
        if (!useChouetteGtfsExport) {
            // Built but deliberately not reported: the route had no updateStatus after it. Building it is
            // still load-bearing, because that is what leaves the event on the message as
            // RutebankenSystemStatus, and that header travels to damu.
            JobEvent.providerJobBuilder(message)
                    .timetableAction(JobEvent.TimetableAction.EXPORT)
                    .state(JobEvent.State.PENDING)
                    .build();
        }
        message.removeHeader(DATASET_REFERENTIAL);
        message.setBody(message.getHeader(CHOUETTE_REFERENTIAL, String.class));
        message.setHeader(GTFS_ROUTE_DISPATCHER_HEADER_NAME, GTFS_ROUTE_DISPATCHER_EXPORT_HEADER_VALUE);
        publisher.publish(MardukQueues.GTFS_ROUTE_DISPATCHER_TOPIC, message);
    }
}
