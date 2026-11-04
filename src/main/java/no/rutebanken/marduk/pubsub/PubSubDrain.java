package no.rutebanken.marduk.pubsub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Lets work that is already running finish before the context tears the pod down.
 *
 * <p>{@link SmartLifecycle#stop()} is the right hook, and the only one. Spring's shutdown runs
 * {@code ContextClosedEvent} listeners first - which is where {@code AbstractEnturGooglePubSubConsumer}
 * stops its subscribers, so by the time this runs nothing new arrives - then stops {@code Lifecycle} beans,
 * and only then calls {@code destroyBeans()}. Draining here therefore happens while the publisher, the blob
 * store and the datasource are all still usable by the work that is finishing, which is the whole point:
 * work that drains but cannot publish its result is the redelivery a drain exists to prevent.
 *
 * <p>Note that {@code spring.lifecycle.timeout-per-shutdown-phase} does <b>not</b> bound this, because
 * {@code stop()} is invoked inline. The timeout here is the only bound.
 *
 * <p>Sizing, against {@code terminationGracePeriodSeconds}, which the chart sets to 120:
 * subscriber close is up to 10 s per busy subscriber and is spent before this runs, then this drain, then
 * bean destruction. The default below leaves room for all three. Do not raise it past the grace period: a
 * SIGKILL mid-drain is worse than an abandoned message, because the work is neither finished nor nacked.
 */
@Component
public class PubSubDrain implements SmartLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(PubSubDrain.class);

    private final InFlightWork inFlightWork;
    private final Duration timeout;
    private volatile boolean running;

    public PubSubDrain(
            InFlightWork inFlightWork,
            @Value("${marduk.shutdown.drain.timeout.seconds:60}") long timeoutSeconds) {
        this.inFlightWork = inFlightWork;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    @Override
    public void start() {
        running = true;
    }

    @Override
    public void stop() {
        running = false;
        int outstanding = inFlightWork.count();
        if (outstanding == 0) {
            LOGGER.info("Shutting down with no work in flight");
            return;
        }
        LOGGER.info("Waiting up to {} for {} unit(s) of work to finish", timeout, outstanding);
        if (inFlightWork.awaitIdle(timeout)) {
            LOGGER.info("All in-flight work finished");
        } else {
            // Not an error the pod can do anything about, but it is the line that explains a redelivery
            // after a restart, so it is worth being explicit that work was abandoned rather than lost.
            LOGGER.warn(
                    "Drain timed out after {} with {} unit(s) still running; they will be redelivered",
                    timeout, inFlightWork.count());
        }
    }

    /**
     * Highest phase, so this stops first and drains while every other bean is still up.
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
