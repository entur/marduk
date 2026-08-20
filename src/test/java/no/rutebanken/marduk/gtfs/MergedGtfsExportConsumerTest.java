package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two things the consumer decides: that a request is recorded rather than served, and when a burst is
 * served without waiting for the scheduled tick.
 */
class MergedGtfsExportConsumerTest {

    private static final int COMPLETION_SIZE = 3;

    private final BatchedRequests requests = mock(BatchedRequests.class);
    private final MergedGtfsExport export = mock(MergedGtfsExport.class);
    private final MergedGtfsExportConsumer consumer =
            new MergedGtfsExportConsumer(requests, export, COMPLETION_SIZE);

    private static MardukMessage request() {
        return new MardukMessage().setHeader(CORRELATION_ID, "corr");
    }

    @Test
    void aRequestIsRecordedRatherThanExported() {
        // The row is what makes the message safe to acknowledge now: the aggregator held it unacknowledged
        // until the export finished, which is why the subscription needed a long ack extension.
        when(requests.waiting(MergedGtfsExport.KIND)).thenReturn(1);
        MardukMessage message = request();

        consumer.handle(message);

        verify(requests).record(MergedGtfsExport.KIND, message);
        verify(export, never()).serveTheBatch();
    }

    @Test
    void aBurstReachingTheCompletionSizeIsServedWithoutWaitingForTheTick() {
        when(requests.waiting(MergedGtfsExport.KIND)).thenReturn(COMPLETION_SIZE);

        consumer.handle(request());

        verify(export).serveTheBatch();
    }

    @Test
    void aFailedServeDoesNotNackAnAlreadyRecordedRequest() {
        // A nack here redelivers a message whose row is already written, so the retry records it twice.
        when(requests.waiting(MergedGtfsExport.KIND)).thenReturn(COMPLETION_SIZE);
        doThrow(new IllegalStateException("damu is unreachable")).when(export).serveTheBatch();

        assertDoesNotThrow(() -> consumer.handle(request()));
    }

    @Test
    void theSubscriptionIsTheMergedExportQueue() {
        assertEquals(MardukQueues.GTFS_EXPORT_MERGED_QUEUE, consumer.destination());
    }
}
