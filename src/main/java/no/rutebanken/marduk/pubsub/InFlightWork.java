package no.rutebanken.marduk.pubsub;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts units of work currently being processed, so shutdown can wait for them.
 *
 * <p>Nothing in {@code entur-google-pubsub} tracks this: its only bound on in-flight work is
 * {@code awaitTerminated(10, SECONDS)} per subscriber, which expires in full whenever a callback is still
 * running and then lets the context proceed to destroy the publisher underneath it. That is how work
 * drains successfully and is then unable to report its result.
 */
@Component
public class InFlightWork {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final Object idle = new Object();

    /**
     * Marks one unit of work as running until the returned handle is closed. Intended for
     * try-with-resources so the decrement cannot be skipped by an early return or a throw.
     */
    public Tracked start() {
        inFlight.incrementAndGet();
        return this::finish;
    }

    public int count() {
        return inFlight.get();
    }

    /**
     * Waits for the count to reach zero.
     *
     * @return true if everything finished, false on timeout
     */
    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        synchronized (idle) {
            while (inFlight.get() > 0) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    return false;
                }
                try {
                    // Waiting with a timeout rather than sleeping in a poll loop, so the last unit of work
                    // finishing wakes this immediately instead of after the rest of a poll interval.
                    idle.wait(Math.max(1, Duration.ofNanos(remaining).toMillis()));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return inFlight.get() == 0;
                }
            }
            return true;
        }
    }

    private void finish() {
        if (inFlight.decrementAndGet() <= 0) {
            synchronized (idle) {
                idle.notifyAll();
            }
        }
    }

    /** Closeable without a checked exception, so callers can use try-with-resources cleanly. */
    @FunctionalInterface
    public interface Tracked extends AutoCloseable {
        @Override
        void close();
    }
}
