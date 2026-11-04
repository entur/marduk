package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.springframework.stereotype.Component;

/**
 * Records a request to build a candidate full OTP2 graph.
 *
 * <p>A separate batch from the ordinary graph, as it was a separate aggregation, and the only subscription in
 * this cluster that never carried a {@code maxAckExtensionPeriod} to begin with.
 */
@Component
public class Otp2NetexGraphCandidateBuildConsumer extends MardukPubSubConsumer {

    private final BatchedRequests requests;

    public Otp2NetexGraphCandidateBuildConsumer(BatchedRequests requests) {
        this.requests = requests;
    }

    @Override
    protected String destination() {
        return MardukQueues.OTP2_GRAPH_CANDIDATE_BUILD_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        requests.record(Otp2NetexGraphBuild.CANDIDATE_KIND, message);
    }
}
