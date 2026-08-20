package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Records a request for a merged GTFS export.
 *
 * <p>Replaces the aggregating consumer in {@code GtfsMergedExportRouteBuilder}. The request becomes a row
 * and the message is acknowledged straight away; the aggregator instead held it unacknowledged until the
 * export finished, which is what the {@code removeSynchronization}/{@code addSynchronization} pair around
 * it was for.
 *
 * <p>The aggregator completed a group at {@code aggregation.completionSize} requests or after
 * {@code gtfs.export.aggregation.timeout} of inactivity, whichever came first. The timeout trigger is
 * {@link MergedGtfsExport#serveTheBatchOnceRequestsStopArriving()}; the size one is here, so a burst does
 * not wait out the quiet period. Nothing happens on a follower:
 * {@link no.rutebanken.marduk.batch.BatchRunner} is leader-gated, which matches the {@code singletonFrom}
 * the aggregating route used.
 */
@Component
public class MergedGtfsExportConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MergedGtfsExportConsumer.class);

    private final BatchedRequests requests;
    private final MergedGtfsExport export;
    private final int completionSize;

    public MergedGtfsExportConsumer(
            BatchedRequests requests,
            MergedGtfsExport export,
            @Value("${aggregation.completionSize:100}") int completionSize) {
        this.requests = requests;
        this.export = export;
        this.completionSize = completionSize;
    }

    @Override
    protected String destination() {
        return MardukQueues.GTFS_EXPORT_MERGED_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        requests.record(MergedGtfsExport.KIND, message);
        int waiting = requests.waiting(MergedGtfsExport.KIND);
        LOGGER.info("Recorded a merged GTFS export request, {} waiting", waiting);
        if (waiting < completionSize) {
            return;
        }
        try {
            export.serveTheBatch();
        } catch (RuntimeException e) {
            // The row is already written and BatchRunner has released the batch, so the next quiet period
            // serves it again. Letting this escape would nack a message whose request is safely recorded,
            // and the redelivery would record it a second time - one extra row per failure, for nothing.
            LOGGER.error("Serving the merged GTFS export batch failed; the request stays recorded", e);
        }
    }
}
