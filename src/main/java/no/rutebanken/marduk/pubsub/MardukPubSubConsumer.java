package no.rutebanken.marduk.pubsub;

import com.google.api.core.ApiService;
import com.google.cloud.pubsub.v1.Subscriber;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.entur.pubsub.base.AbstractEnturGooglePubSubConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Base class for marduk's PubSub consumers.
 *
 * <p>Adds the four things every consumer needs and the library base class does not do: the project-aware
 * subscription name, MDC, in-flight tracking for the shutdown drain, and turning the raw payload and
 * attribute map into a {@link MardukMessage}.
 *
 * <p>Failure handling is left to the library: an exception escaping {@link #handle} reaches its callback,
 * which nacks the message and lets PubSub redeliver after the subscription's {@code minimum_backoff}. In
 * process retries belong <em>inside</em> {@link #handle}, around the specific call that is worth retrying,
 * using {@code RetryPolicy} - not around the whole message, which would retry side effects that already
 * succeeded.
 *
 * <p>One caveat inherited from the library: after {@link #handle} throws, its callback sleeps
 * {@code entur.pubsub.consumer.retry.delay} on the subscriber thread before returning. That sleep also
 * keeps the subscriber from terminating during shutdown, so marduk sets the property low and relies on the
 * subscriptions' 10 s {@code retry_policy.minimum_backoff} to throttle instead.
 */
public abstract class MardukPubSubConsumer extends AbstractEnturGooglePubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MardukPubSubConsumer.class);

    /** The library keeps its subscribers private and offers no accessor. */
    private static final Field SUBSCRIBERS = subscribersField();

    @Autowired
    private MardukQueues queues;

    @Autowired
    private InFlightWork inFlightWork;

    /** False if the library moved its subscribers, so their state cannot be reported at all. */
    public static boolean subscribersReadable() {
        return SUBSCRIBERS != null;
    }

    /** The state of each subscriber this consumer started, by subscription name, empty until they exist. */
    public Map<String, ApiService.State> subscriberStates() {
        try {
            @SuppressWarnings("unchecked")
            List<Subscriber> subscribers = (List<Subscriber>) SUBSCRIBERS.get(this);
            return List.copyOf(subscribers).stream().collect(Collectors.toMap(
                    Subscriber::getSubscriptionNameString, ApiService::state, (a, b) -> a, LinkedHashMap::new));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Map.of();
        }
    }

    private static Field subscribersField() {
        try {
            Field field = AbstractEnturGooglePubSubConsumer.class.getDeclaredField("subscribers");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Cannot read the PubSub subscribers, so their state will not be reported", e);
            return null;
        }
    }

    /** The unqualified destination name, one of the {@link MardukQueues} constants. */
    protected abstract String destination();

    /**
     * Gives a message that arrived without a correlation id one, on the message and in the MDC.
     *
     * <p>The MDC half is the point: {@link #onMessage} has already read the empty header by the time
     * {@link #handle} runs, so a handler that only sets the header leaves every log line of the job it
     * starts - and the library's own failure line - unattributed.
     */
    protected static void ensureCorrelationId(MardukMessage message) {
        if (message.getHeader(Constants.CORRELATION_ID) == null) {
            String correlationId = UUID.randomUUID().toString();
            message.setHeader(Constants.CORRELATION_ID, correlationId);
            MardukMdc.setCorrelationId(correlationId);
        }
    }

    /** Handles one message. Throwing nacks it. */
    protected abstract void handle(MardukMessage message);

    @Override
    protected final String getDestinationName() {
        return queues.subscription(destination());
    }

    @Override
    public final void onMessage(byte[] content, Map<String, String> attributes) {
        MardukMessage message = PubSubAttributes.toMessage(content, attributes);
        // Set on the way in and deliberately not cleared on the way out: when handle() throws, the library
        // logs the failure after this method returns, and that ERROR line is the one most worth having the
        // job's correlation id on. The next message on this thread replaces the values.
        MardukMdc.set(message);
        try (InFlightWork.Tracked tracked = inFlightWork.start()) {
            LOGGER.debug("Handling a message from {}", destination());
            handle(message);
        }
    }
}
