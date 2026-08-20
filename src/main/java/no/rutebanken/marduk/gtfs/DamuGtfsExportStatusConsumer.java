package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Tracks damu's per-codespace GTFS export, and triggers the national merge when one finishes.
 *
 * <p>Replaces {@code DamuExportGtfsStatusRouteBuilder}.
 *
 * <p>Nothing happens beyond a log line while {@code gtfs.export.chouette} is true, because the export that
 * matters is then Chouette's and damu's is running in parallel for comparison. That flag gated everything
 * after it in the Camel version, including the merge trigger, and still does.
 */
@Component
public class DamuGtfsExportStatusConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DamuGtfsExportStatusConsumer.class);

    /**
     * Damu sends its export status in the message <b>body</b>, not an attribute. Pinned by
     * {@code WireContractTest}; damu's own {@code WireContractTest} pins the other side.
     */
    static final String STATUS_EXPORT_STARTED = "started";
    static final String STATUS_EXPORT_OK = "ok";
    static final String STATUS_EXPORT_FAILED = "failed";

    private final JobEventPublisher jobEvents;
    private final MardukPubSubPublisher publisher;
    private final boolean useChouetteGtfsExport;

    public DamuGtfsExportStatusConsumer(
            JobEventPublisher jobEvents,
            MardukPubSubPublisher publisher,
            @Value("${gtfs.export.chouette:true}") boolean useChouetteGtfsExport) {
        this.jobEvents = jobEvents;
        this.publisher = publisher;
        this.useChouetteGtfsExport = useChouetteGtfsExport;
    }

    @Override
    protected String destination() {
        return MardukQueues.DAMU_EXPORT_GTFS_STATUS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        setCorrelationIdIfMissing(message);
        String codespace = message.getHeader(Constants.DATASET_REFERENTIAL, String.class);

        switch (message.getBody(String.class)) {
            case STATUS_EXPORT_STARTED -> {
                LOGGER.info("Damu GTFS export started for codespace {}", codespace);
                reportStatus(message, JobEvent.State.STARTED);
            }
            case STATUS_EXPORT_OK -> {
                LOGGER.info("Damu GTFS export complete for codespace {}", codespace);
                if (!useChouetteGtfsExport) {
                    // Published before the status report, which overwrites the body with the job event.
                    publisher.publish(MardukQueues.GTFS_EXPORT_MERGED_QUEUE, message);
                    reportStatus(message, JobEvent.State.OK);
                }
            }
            case STATUS_EXPORT_FAILED -> {
                LOGGER.info("Damu GTFS export failed for codespace {}", codespace);
                reportStatus(message, JobEvent.State.FAILED);
            }
            // The Camel version had no otherwise branch, so an unrecognised status was acked and dropped
            // in silence. Kept, but said out loud - a status nobody recognises is how a job ends up with
            // no terminal state.
            default -> LOGGER.warn(
                    "Ignoring unrecognised Damu GTFS export status '{}' for codespace {}",
                    message.getBody(String.class), codespace);
        }
    }

    private void reportStatus(MardukMessage message, JobEvent.State state) {
        if (useChouetteGtfsExport) {
            return;
        }
        jobEvents.reportProviderJob(message,
                builder -> builder.timetableAction(JobEvent.TimetableAction.EXPORT).state(state));
    }

    /**
     * Sets the MDC after filling in a missing correlation id, so the log lines above carry one. Camel's
     * {@code interceptFrom} ran before the route could generate it, and these lines went out unlabelled.
     */
    private static void setCorrelationIdIfMissing(MardukMessage message) {
        if (message.getHeader(Constants.CORRELATION_ID, String.class) == null) {
            message.setHeader(Constants.CORRELATION_ID, UUID.randomUUID().toString());
            MardukMdc.set(message);
        }
    }
}
