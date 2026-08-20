package no.rutebanken.marduk.health;

import com.google.api.core.ApiService;
import no.rutebanken.marduk.pubsub.MardukPubSubConsumer;
import org.entur.pubsub.base.AbstractEnturGooglePubSubConsumer;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reports whether the PubSub subscribers are actually consuming.
 *
 * <p>A subscriber that dies takes its subscription with it and nothing else notices: the pod serves the
 * admin API, answers the readiness probe and reports UP, while messages pile up on a subscription no thread
 * is pulling. The library escalates a terminal failure only if
 * {@code entur.pubsub.consumer.break-liveness-on-terminal-failure} is on, and its own gRPC health check -
 * {@code management.health.pubsub.enabled}, off in the chart - answers for the PubSub API rather than for
 * this pod's subscribers.
 *
 * <p>This is the actual subscriber state, from {@link MardukPubSubConsumer#subscriberStates()}. A consumer
 * that reports no subscriber is DOWN rather than absent, so a subscription that never started is visible.
 */
@Component
public class PubSubSubscriberHealthIndicator implements HealthIndicator {

    private static final String RUNNING = ApiService.State.RUNNING.name();
    private static final String NOT_SUBSCRIBED = "NOT_SUBSCRIBED";

    private final List<MardukPubSubConsumer> consumers;

    public PubSubSubscriberHealthIndicator(List<MardukPubSubConsumer> consumers) {
        this.consumers = consumers;
    }

    @Override
    public Health health() {
        return evaluate(MardukPubSubConsumer.subscribersReadable() ? states() : null);
    }

    /**
     * @param states the state of every subscriber by subscription name, or null if they cannot be read
     */
    Health evaluate(Map<String, String> states) {
        if (states == null) {
            return Health.unknown()
                    .withDetail("reason", "the library no longer exposes its subscribers")
                    .build();
        }
        Map<String, String> broken = new TreeMap<>();
        states.forEach((subscription, state) -> {
            if (!RUNNING.equals(state)) {
                broken.put(subscription, state);
            }
        });
        if (states.isEmpty()) {
            return Health.down().withDetail("reason", "no subscriber is registered").build();
        }
        Health.Builder health = broken.isEmpty() ? Health.up() : Health.down();
        return health
                .withDetail("subscribers", states.size())
                .withDetail("notRunning", broken)
                .build();
    }

    private Map<String, String> states() {
        Map<String, String> states = new LinkedHashMap<>();
        for (MardukPubSubConsumer consumer : consumers) {
            Map<String, ApiService.State> subscribers = consumer.subscriberStates();
            if (subscribers.isEmpty()) {
                states.put(consumer.getClass().getSimpleName(), NOT_SUBSCRIBED);
            }
            subscribers.forEach((subscription, state) -> states.put(subscription, state.name()));
        }
        return states;
    }
}
