package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The job events damu's merge status produces, including which of them starts a new job.
 */
class DamuGtfsMergeStatusConsumerTest {

    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();
    private final DamuGtfsMergeStatusConsumer consumer =
            new DamuGtfsMergeStatusConsumer(new JobEventPublisher(publisher));

    private static MardukMessage status(String status) {
        MardukMessage message = new MardukMessage().setHeader(CORRELATION_ID, "corr");
        return status == null ? message : message.setHeader("status", status);
    }

    private JobEvent reported() {
        assertEquals(MardukQueues.JOB_EVENT_QUEUE, publisher.onlyPublished().destination());
        return JobEvent.fromString(publisher.onlyPublished().body());
    }

    @Test
    void aStartedMergeIsReportedAsASystemJobUnderAFreshCorrelationId() {
        consumer.handle(status("started"));

        JobEvent reported = reported();
        assertEquals(JobEvent.State.STARTED, reported.getState());
        assertEquals(JobEvent.JobDomain.TIMETABLE_PUBLISH, reported.getDomain());
        assertEquals("EXPORT_GTFS_MERGED", reported.getAction());
        assertNotNull(reported.getCorrelationId());
        assertNotEquals("corr", reported.getCorrelationId(),
                "STARTED generates its own id where OK and FAILED reuse the incoming one");
    }

    @Test
    void aFinishedMergeIsReportedUnderTheIncomingCorrelationId() {
        consumer.handle(status("ok"));

        JobEvent reported = reported();
        assertEquals(JobEvent.State.OK, reported.getState());
        assertEquals(JobEvent.JobDomain.TIMETABLE_PUBLISH, reported.getDomain());
        assertEquals("EXPORT_GTFS_MERGED", reported.getAction());
        assertEquals("corr", reported.getCorrelationId());
    }

    @Test
    void aFailedMergeIsReportedUnderTheIncomingCorrelationId() {
        consumer.handle(status("failed"));

        JobEvent reported = reported();
        assertEquals(JobEvent.State.FAILED, reported.getState());
        assertEquals("corr", reported.getCorrelationId());
    }

    @Test
    void anUnrecognisedStatusIsDroppedRatherThanFailing() {
        // No terminal state is reported, matching the Camel version, which had no otherwise branch. Throwing
        // would nack a message no version of marduk can handle, and this subscription has no retry backoff.
        consumer.handle(status("half-done"));

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void aMissingStatusIsDroppedRatherThanFailing() {
        consumer.handle(status(null));

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void theSubscriptionIsMardukSOwnAggregationStatusQueue() {
        assertEquals(MardukQueues.MARDUK_AGGREGATE_GTFS_STATUS_QUEUE, consumer.destination());
    }
}
