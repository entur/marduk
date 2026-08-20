package no.rutebanken.marduk.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    /** The deployed configuration: nothing in the ConfigMap overrides these code defaults. */
    private static RetryPolicy deployed() {
        return new RetryPolicy(3, 5000, 3, 60000);
    }

    /** Non-zero but fast, so the retry mechanics are exercised without the test taking a minute. */
    private static RetryPolicy fast() {
        return new RetryPolicy(3, 1, 1, 60000);
    }

    @Test
    void theDeployedDelaysAreTheOnesCamelComputed() {
        RetryPolicy policy = deployed();

        long first = policy.nextDelay(0);
        long second = policy.nextDelay(first);
        long third = policy.nextDelay(second);

        assertEquals(5000, first);
        assertEquals(15000, second);
        assertEquals(45000, third);
        assertEquals(65000, first + second + third,
                "a message that fails every attempt holds its consumer thread this long, as it did before");
    }

    @Test
    void theDelayIsCappedAtTheMaximum() {
        // Does not bind at the deployed multiplier, but does the moment anyone raises the delay or the
        // multiplier, which is why the cap is reproduced rather than dropped.
        RetryPolicy policy = new RetryPolicy(5, 30000, 3, 60000);

        assertEquals(30000, policy.nextDelay(0));
        assertEquals(60000, policy.nextDelay(30000));
        assertEquals(60000, policy.nextDelay(60000));
    }

    @Test
    void aMultiplierOfOneKeepsAFlatDelay() {
        RetryPolicy policy = new RetryPolicy(3, 5000, 1, 60000);

        assertEquals(5000, policy.nextDelay(0));
        assertEquals(5000, policy.nextDelay(5000));
    }

    @Test
    void aSucceedingCallRunsOnce() {
        AtomicInteger attempts = new AtomicInteger();

        String result = fast().call("work", () -> {
            attempts.incrementAndGet();
            return "done";
        });

        assertEquals("done", result);
        assertEquals(1, attempts.get());
    }

    @Test
    void aTransientFailureIsRetriedUntilItSucceeds() {
        AtomicInteger attempts = new AtomicInteger();

        String result = fast().call("work", () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new IllegalStateException("not yet");
            }
            return "done";
        });

        assertEquals("done", result);
        assertEquals(3, attempts.get());
    }

    @Test
    void threeRedeliveriesMeansFourAttempts() {
        AtomicInteger attempts = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> fast().call("work", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always");
        }));

        assertEquals(4, attempts.get(), "maximumRedeliveries counts redeliveries, not total attempts");
    }

    @Test
    void aMaximumOfZeroMeansNoRetryAtAll() {
        // What src/test/resources/application.properties configures, and therefore the only behaviour the
        // existing suite has ever exercised.
        AtomicInteger attempts = new AtomicInteger();
        RetryPolicy noRetry = new RetryPolicy(0, 5000, 3, 60000);

        assertThrows(IllegalStateException.class, () -> noRetry.call("work", () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always");
        }));

        assertEquals(1, attempts.get());
    }

    @Test
    void theLastFailureIsRethrownUnwrapped() {
        IllegalArgumentException failure = new IllegalArgumentException("bad request");

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> fast().call("work", () -> {
                    throw failure;
                }));

        assertEquals(failure, thrown, "the caller decides whether to nack based on the original exception");
    }

    @Test
    void aCheckedExceptionIsWrappedRatherThanSwallowed() {
        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> fast().call("work", () -> {
                    throw new java.io.IOException("no route to host");
                }));

        assertEquals("no route to host", thrown.getCause().getMessage());
    }

    @Test
    void runIsCalledForItsSideEffect() {
        List<String> calls = new ArrayList<>();

        fast().run("work", () -> calls.add("ran"));

        assertEquals(List.of("ran"), calls);
    }

    @Test
    void anInterruptAbandonsTheRetriesInsteadOfDelayingShutdown() throws InterruptedException {
        // A pod being shut down must not spend a further minute per in-flight failure before its
        // subscriber can stop. The interrupt flag has to survive, or the caller cannot tell why it failed.
        RetryPolicy slow = new RetryPolicy(3, 60000, 3, 60000);
        AtomicInteger attempts = new AtomicInteger();
        List<Boolean> interruptFlagAfterwards = new ArrayList<>();

        Thread worker = new Thread(() -> {
            try {
                slow.call("work", () -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("always");
                });
            } catch (IllegalStateException expected) {
                interruptFlagAfterwards.add(Thread.currentThread().isInterrupted());
            }
        });
        worker.start();
        while (attempts.get() == 0) {
            Thread.onSpinWait();
        }
        worker.interrupt();
        worker.join(30_000);

        assertTrue(interruptFlagAfterwards.contains(true), "the interrupt flag was swallowed");
        assertEquals(1, attempts.get(), "the work was retried despite the interrupt");
    }
}
