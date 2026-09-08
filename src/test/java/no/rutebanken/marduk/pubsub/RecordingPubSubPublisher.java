package no.rutebanken.marduk.pubsub;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pipeline.MardukMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Records what would have been published, so a test can assert on the wire without an emulator.
 *
 * <p>Captures the body and attributes at publish time rather than keeping the message: a message is mutable
 * and usually keeps being written to after the publish, so holding a reference would let a later mutation
 * change what the test believes was sent.
 *
 * <p>Rejects, rather than records, a publish that the real one could not have made: an unknown destination
 * (which in production would create or write to the wrong topic), and a publish after the publisher has been
 * destroyed. {@link #failsFor} makes a destination fail on demand, which is the only way to test that a
 * caller nacks rather than acking work it could not report.
 *
 * <p>Recording is thread-safe, because fan-outs publish from a pool. Only the order <em>within</em> one
 * destination is worth asserting on, and only when the publishes were sequential; use
 * {@link #publishedTo(String)} for that and set membership otherwise.
 */
public class RecordingPubSubPublisher implements MardukPubSubPublisher {

    /** The destinations that exist. A name outside this set is a typo, not a queue. */
    private static final Set<String> DESTINATIONS = destinationConstants();

    public record Published(String destination, String body, Map<String, String> attributes) {

        public String attribute(String name) {
            return attributes.get(name);
        }
    }

    private final List<Published> published = new CopyOnWriteArrayList<>();
    private volatile String failingDestination;
    private volatile boolean destroyed;

    @Override
    public void publish(String destination, MardukMessage message) {
        if (!DESTINATIONS.contains(destination)) {
            throw new AssertionError("Published to " + destination + ", which is not a MardukQueues destination");
        }
        if (destroyed) {
            throw new AssertionError("Published to " + destination
                    + " after the publisher was destroyed, so the message would have been lost");
        }
        String body = message.getBody(String.class);
        Map<String, String> attributes = PubSubAttributes.toAttributes(message);
        if (destination.equals(failingDestination)) {
            throw new MardukException("Failed to publish to " + destination);
        }
        published.add(new Published(destination, body == null ? "" : body, attributes));
    }

    /** Makes every publish to {@code destination} throw, as an unreachable topic does. */
    public void failsFor(String destination) {
        failingDestination = destination;
    }

    /** As destroying the publisher bean does. A publish after this is the loss a drain exists to prevent. */
    public void destroy() {
        destroyed = true;
    }

    public List<Published> published() {
        return List.copyOf(published);
    }

    public List<Published> publishedTo(String destination) {
        return published.stream().filter(p -> p.destination().equals(destination)).toList();
    }

    public Published onlyPublished() {
        if (published.size() != 1) {
            throw new AssertionError("Expected exactly one published message but got " + published);
        }
        return published.getFirst();
    }

    /**
     * The one message published to {@code destination}, whatever else was published elsewhere. For a
     * fan-out, where the arrival order across destinations is whatever the pool decided.
     */
    public Published onlyPublishedTo(String destination) {
        List<Published> matching = publishedTo(destination);
        if (matching.size() != 1) {
            throw new AssertionError("Expected exactly one message on " + destination + " but got " + matching);
        }
        return matching.getFirst();
    }

    /** Every value of {@code name}, as a set, for asserting on a fan-out without depending on its order. */
    public Set<String> attributeValues(String destination, String name) {
        return publishedTo(destination).stream()
                .map(p -> p.attribute(name))
                .collect(Collectors.toSet());
    }

    public void clear() {
        published.clear();
    }

    private static Set<String> destinationConstants() {
        return java.util.Arrays.stream(MardukQueues.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()) && field.getType() == String.class)
                .map(RecordingPubSubPublisher::value)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String value(Field field) {
        try {
            return (String) field.get(null);
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
