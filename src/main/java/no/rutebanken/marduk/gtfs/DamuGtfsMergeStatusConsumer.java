package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;

/**
 * Reports damu's progress on the national GTFS merge to nabu.
 *
 * <p>Replaces the {@code MardukAggregateGtfsStatusQueue} consumer in {@code GtfsMergedExportRouteBuilder}.
 * Every state is a system job rather than a provider one: the merge covers all providers, so there is no
 * provider whose job it is.
 */
@Component
public class DamuGtfsMergeStatusConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DamuGtfsMergeStatusConsumer.class);

    /**
     * Damu sends its merge status as this message <b>attribute</b>, unlike the per-codespace export status
     * it sends in the body. Damu's own wire contract pins the other side.
     */
    private static final String STATUS_HEADER = "status";

    private static final String STATUS_MERGE_STARTED = "started";
    private static final String STATUS_MERGE_OK = "ok";
    private static final String STATUS_MERGE_FAILED = "failed";

    private final JobEventPublisher jobEvents;

    public DamuGtfsMergeStatusConsumer(JobEventPublisher jobEvents) {
        this.jobEvents = jobEvents;
    }

    @Override
    protected String destination() {
        return MardukQueues.MARDUK_AGGREGATE_GTFS_STATUS_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        String status = message.getHeader(STATUS_HEADER, String.class);
        switch (status) {
            case STATUS_MERGE_STARTED -> {
                LOGGER.info("Received status STARTED from damu aggregation");
                // A generated correlation id, where OK and FAILED reuse the incoming one, so the STARTED
                // event lands under an id no later event shares. Preserved from the Camel version.
                jobEvents.reportSystemJob(message,
                        builder -> merge(builder, JobEvent.State.STARTED).newCorrelationId());
            }
            case STATUS_MERGE_OK -> {
                LOGGER.info("Received status OK from damu aggregation");
                jobEvents.reportSystemJob(message, builder -> merge(builder, JobEvent.State.OK)
                        .correlationId(message.getHeader(CORRELATION_ID, String.class)));
            }
            case STATUS_MERGE_FAILED -> {
                LOGGER.info("Received status FAILED from damu aggregation");
                jobEvents.reportSystemJob(message, builder -> merge(builder, JobEvent.State.FAILED)
                        .correlationId(message.getHeader(CORRELATION_ID, String.class)));
            }
            // The Camel version had no otherwise branch, so an unrecognised status was acked and dropped in
            // silence, leaving the merge with no terminal state. Kept, but said out loud.
            case null, default -> LOGGER.warn("Ignoring unrecognised damu GTFS merge status '{}'", status);
        }
    }

    private static JobEvent.Builder merge(JobEvent.Builder builder, JobEvent.State state) {
        return builder
                .jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH)
                .action(JobEvent.TimetableAction.EXPORT_GTFS_MERGED)
                .state(state);
    }
}
