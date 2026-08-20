package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.springframework.stereotype.Component;

/**
 * Records a request to build a candidate OTP2 street graph.
 *
 * <p>A separate batch from the ordinary street graph, as it was a separate aggregation: the two routes named
 * the same aggregate controller, but each call to {@code getAggregateControllerForRoute} returned a new one,
 * so they only ever shared a force-completion.
 */
@Component
public class Otp2BaseGraphCandidateBuildConsumer extends MardukPubSubConsumer {

    private final BatchedRequests requests;

    public Otp2BaseGraphCandidateBuildConsumer(BatchedRequests requests) {
        this.requests = requests;
    }

    @Override
    protected String destination() {
        return MardukQueues.OTP2_BASE_GRAPH_CANDIDATE_BUILD_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        requests.record(Otp2BaseGraphBuild.CANDIDATE_KIND, message);
    }
}
