package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import no.rutebanken.marduk.pubsub.MardukQueues;
import org.springframework.stereotype.Component;

/**
 * Publishes a merged NeTEx dataset that antu has approved.
 *
 * <p>Replaces {@code PublishMergedNetexRouteBuilder}'s consumer. The merge itself reaches
 * {@link MergedNetexPublication} directly, without going round the queue, exactly as the routes did.
 */
@Component
public class PublishMergedNetexConsumer extends MardukPubSubConsumer {

    private final MergedNetexPublication publication;

    public PublishMergedNetexConsumer(MergedNetexPublication publication) {
        this.publication = publication;
    }

    @Override
    protected String destination() {
        return MardukQueues.PUBLISH_MERGED_NETEX_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        publication.publishMergedDataset(message);
    }
}
