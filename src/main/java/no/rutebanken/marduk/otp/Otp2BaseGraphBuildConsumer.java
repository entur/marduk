package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.springframework.stereotype.Component;

/**
 * Records a request to build the OTP2 street graph.
 *
 * <p>Replaces the {@code Otp2BaseGraphBuildQueue} consumer in {@code Otp2BaseGraphRouteBuilder}, which held
 * its messages unacknowledged in an aggregator for as long as the build took - hence the four-hour
 * {@code maxAckExtensionPeriod} on the subscription. The row is written and the message acknowledged
 * immediately, so that parameter is gone and a pod dying mid-build no longer depends on redelivery.
 */
@Component
public class Otp2BaseGraphBuildConsumer extends MardukPubSubConsumer {

    private final BatchedRequests requests;

    public Otp2BaseGraphBuildConsumer(BatchedRequests requests) {
        this.requests = requests;
    }

    @Override
    protected String destination() {
        return MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        requests.record(Otp2BaseGraphBuild.KIND, message);
    }
}
