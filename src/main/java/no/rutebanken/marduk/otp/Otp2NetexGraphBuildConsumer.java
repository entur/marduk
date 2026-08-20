package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.springframework.stereotype.Component;

/**
 * Records a request to build the full OTP2 graph.
 *
 * <p>Replaces the {@code Otp2GraphBuildQueue} consumer in {@code Otp2NetexGraphRouteBuilder}. Every
 * provider's finished import lands here, which is why the batch matters: one graph build serves them all.
 * The recorded request keeps its referential, so {@link Otp2NetexGraphBuild} can still report the build back
 * to each provider.
 */
@Component
public class Otp2NetexGraphBuildConsumer extends MardukPubSubConsumer {

    private final BatchedRequests requests;

    public Otp2NetexGraphBuildConsumer(BatchedRequests requests) {
        this.requests = requests;
    }

    @Override
    protected String destination() {
        return MardukQueues.OTP2_GRAPH_BUILD_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        requests.record(Otp2NetexGraphBuild.KIND, message);
    }
}
