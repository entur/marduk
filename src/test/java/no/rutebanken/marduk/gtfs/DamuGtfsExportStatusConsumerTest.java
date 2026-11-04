package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamuGtfsExportStatusConsumerTest {

    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();

    private DamuGtfsExportStatusConsumer consumer(boolean useChouetteGtfsExport) {
        return new DamuGtfsExportStatusConsumer(
                new JobEventPublisher(publisher), publisher, useChouetteGtfsExport);
    }

    private static MardukMessage status(String body) {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "rb_tst")
                .setBody(body);
    }

    @Test
    void aFinishedExportTriggersTheNationalMergeAndReportsOk() {
        consumer(false).handle(status("ok"));

        assertEquals(1, publisher.publishedTo(MardukQueues.GTFS_EXPORT_MERGED_QUEUE).size());
        assertEquals(1, publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).size());
        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals("EXPORT", reported.getAction());
        assertEquals(JobEvent.State.OK, reported.getState());
    }

    @Test
    void theMergeIsTriggeredBeforeTheStatusReportOverwritesTheBody() {
        // The status report replaces the body with the job event, so the order decides what the merge queue
        // receives. Pinned because swapping the two is an easy and invisible mistake.
        consumer(false).handle(status("ok"));

        assertEquals("ok", publisher.publishedTo(MardukQueues.GTFS_EXPORT_MERGED_QUEUE).getFirst().body());
    }

    @Test
    void aStartedExportReportsStartedAndTriggersNothing() {
        consumer(false).handle(status("started"));

        assertTrue(publisher.publishedTo(MardukQueues.GTFS_EXPORT_MERGED_QUEUE).isEmpty());
        assertEquals(JobEvent.State.STARTED, JobEvent.fromString(publisher.onlyPublished().body()).getState());
    }

    @Test
    void aFailedExportReportsFailedAndTriggersNothing() {
        consumer(false).handle(status("failed"));

        assertTrue(publisher.publishedTo(MardukQueues.GTFS_EXPORT_MERGED_QUEUE).isEmpty());
        assertEquals(JobEvent.State.FAILED, JobEvent.fromString(publisher.onlyPublished().body()).getState());
    }

    @Test
    void nothingIsReportedWhileChouetteStillOwnsTheGtfsExport() {
        // gtfs.export.chouette gates everything after the log line, including the merge trigger, because
        // damu's export is then running only for comparison.
        DamuGtfsExportStatusConsumer consumer = consumer(true);

        consumer.handle(status("started"));
        consumer.handle(status("ok"));
        consumer.handle(status("failed"));

        assertEquals(0, publisher.published().size());
    }

    @Test
    void anUnrecognisedStatusIsDroppedRatherThanFailing() {
        // No terminal status is reported, matching the Camel version, which had no otherwise branch. Throwing
        // here would nack and redeliver a message no version of marduk can ever handle.
        consumer(false).handle(status("unknown-status"));

        assertEquals(0, publisher.published().size());
    }

    @Test
    void aMissingCorrelationIdIsGenerated() {
        MardukMessage message = new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(DATASET_REFERENTIAL, "rb_tst")
                .setBody("failed");

        consumer(false).handle(message);

        assertNotNull(JobEvent.fromString(publisher.onlyPublished().body()).getCorrelationId(),
                "nabu cannot group a job whose status has no correlation id");
    }

    @Test
    void theSubscriptionIsDamuSStatusQueue() {
        assertEquals(MardukQueues.DAMU_EXPORT_GTFS_STATUS_QUEUE, consumer(false).destination());
    }
}
