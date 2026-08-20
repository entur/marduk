package no.rutebanken.marduk.pubsub;

import org.entur.pubsub.base.EnturGooglePubSubAdmin;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Creates the destinations marduk only publishes to.
 *
 * <p>Camel's autocreate notifier walked every endpoint in the context, so it created a topic for producers
 * as well as consumers. {@code AbstractEnturGooglePubSubConsumer} creates only what it subscribes to, which
 * is exactly the asymmetry that left antu's outbound status topic uncreated and its first publish failing
 * with {@code NOT_FOUND}. Marduk publishes to eight destinations it never consumes.
 *
 * <p>A no-op wherever {@code entur.pubsub.subscriber.autocreate} is false, which is every deployed
 * environment - it exists for a fresh emulator in tests and local development.
 */
@Component
public class PubSubPublishTargets {

    private final EnturGooglePubSubAdmin enturGooglePubSubAdmin;
    private final MardukQueues queues;

    public PubSubPublishTargets(EnturGooglePubSubAdmin enturGooglePubSubAdmin, MardukQueues queues) {
        this.enturGooglePubSubAdmin = enturGooglePubSubAdmin;
        this.queues = queues;
    }

    @EventListener
    void handleContextRefreshed(ContextRefreshedEvent contextRefreshedEvent) {
        // The bare destination name, not queues.topic(...): the admin passes whatever it is given to both
        // createTopic and createSubscription, so a project-qualified name would be nonsense to one of them.
        // Cross-project destinations are terraformed anyway, and autocreate is off wherever they are real.
        queues.publishOnlyDestinations().forEach(enturGooglePubSubAdmin::createSubscriptionIfMissing);
    }
}
