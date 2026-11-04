package no.rutebanken.marduk.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

/**
 * In-process retry with exponential backoff, replacing the {@code defaultErrorHandler} that every route
 * inherited from {@code BaseRouteBuilder}.
 *
 * <p>This is not a formality. The deployed ConfigMap never set {@code marduk.camel.redelivery.max}, so
 * production really ran the code default of 3 redeliveries with a 5000 ms base delay and a multiplier of
 * 3 - unlike antu, where the deployed value turned out to be 0 and there was nothing to reproduce. The
 * delays are the ones Camel computed: 5 s, then 15 s, then 45 s, capped at
 * {@code maximumRedeliveryDelay}, and taken synchronously on the calling thread because
 * {@code asyncDelayedRedelivery} defaults to false. A message that fails every attempt therefore occupies
 * its consumer thread for about 65 s, as it did before.
 *
 * <p>Note that {@code src/test/resources/application.properties} sets the maximum to 0, so the suite has
 * never exercised the retry path production depends on. {@code RetryPolicyTest} drives the mechanics with
 * non-default values for that reason.
 *
 * <p>The sleep is interruptible and an interrupt abandons the retries, so a pod being shut down does not
 * spend a further minute per in-flight failure before the subscriber can stop.
 */
@Component
public class RetryPolicy {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryPolicy.class);

    private final int maximumRedeliveries;
    private final long redeliveryDelay;
    private final double backOffMultiplier;
    private final long maximumRedeliveryDelay;

    public RetryPolicy(
            @Value("${marduk.camel.redelivery.max:3}") int maximumRedeliveries,
            @Value("${marduk.camel.redelivery.delay:5000}") long redeliveryDelay,
            @Value("${marduk.camel.redelivery.backoff.multiplier:3}") double backOffMultiplier,
            // Camel's RedeliveryPolicy default, read off the 4.21.0 sources jar. It never binds at the
            // configured multiplier (5000 x 3^2 = 45000), but it does the moment anyone raises either.
            @Value("${marduk.camel.redelivery.max.delay:60000}") long maximumRedeliveryDelay) {
        this.maximumRedeliveries = maximumRedeliveries;
        this.redeliveryDelay = redeliveryDelay;
        this.backOffMultiplier = backOffMultiplier;
        this.maximumRedeliveryDelay = maximumRedeliveryDelay;
    }

    public int getMaximumRedeliveries() {
        return maximumRedeliveries;
    }

    public long getRedeliveryDelay() {
        return redeliveryDelay;
    }

    /**
     * Runs {@code work}, retrying up to {@code maximumRedeliveries} times. Rethrows the last failure once
     * the attempts are exhausted, so the caller can nack and let PubSub redeliver.
     *
     * @param description what is being attempted, for the log line
     */
    public <T> T call(String description, Callable<T> work) {
        long delay = 0;
        for (int redelivery = 0; ; redelivery++) {
            try {
                return work.call();
            } catch (Exception e) {
                if (redelivery >= maximumRedeliveries) {
                    LOGGER.warn(
                            "{} failed after {} attempt(s), giving up",
                            description, redelivery + 1, e);
                    throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
                }
                delay = nextDelay(delay);
                LOGGER.warn(
                        "{} failed, redelivering the message locally, attempt {}/{} in {} ms...",
                        description, redelivery + 1, maximumRedeliveries, delay, e);
                if (!sleep(delay)) {
                    LOGGER.warn("{} interrupted while waiting to retry, abandoning the retries", description);
                    throw e instanceof RuntimeException runtime ? runtime : new IllegalStateException(e);
                }
            }
        }
    }

    public void run(String description, Runnable work) {
        call(description, () -> {
            work.run();
            return null;
        });
    }

    /**
     * The delay Camel's {@code RedeliveryPolicy.calculateRedeliveryDelay} would have computed: the base
     * delay first, then the previous delay times the multiplier, capped.
     */
    long nextDelay(long previousDelay) {
        long next = previousDelay == 0
                ? redeliveryDelay
                : (backOffMultiplier > 1 ? Math.round(backOffMultiplier * previousDelay) : previousDelay);
        return maximumRedeliveryDelay > 0 ? Math.min(next, maximumRedeliveryDelay) : next;
    }

    /** @return false if the wait was interrupted, in which case the interrupt flag is restored */
    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
