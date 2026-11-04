package no.rutebanken.marduk.pubsub;

import no.rutebanken.marduk.pipeline.MardukMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PubSubDrainTest {

    @Test
    void trackingCountsWorkInAndOut() {
        InFlightWork work = new InFlightWork();

        assertEquals(0, work.count());
        try (InFlightWork.Tracked first = work.start()) {
            assertEquals(1, work.count());
            try (InFlightWork.Tracked second = work.start()) {
                assertEquals(2, work.count());
            }
            assertEquals(1, work.count());
        }
        assertEquals(0, work.count());
    }

    @Test
    void aThrowingUnitOfWorkStillDecrements() {
        // try-with-resources rather than an explicit decrement, precisely so an exception cannot leave the
        // count high and make every later shutdown wait out the full drain timeout.
        InFlightWork work = new InFlightWork();

        try (InFlightWork.Tracked tracked = work.start()) {
            throw new IllegalStateException("boom");
        } catch (IllegalStateException expected) {
            // asserted below
        }

        assertEquals(0, work.count());
    }

    @Test
    void awaitIdleReturnsImmediatelyWhenNothingIsRunning() {
        assertTrue(new InFlightWork().awaitIdle(Duration.ofSeconds(1)));
    }

    @Test
    void awaitIdleWakesAsSoonAsTheLastUnitFinishes() throws InterruptedException {
        InFlightWork work = new InFlightWork();
        InFlightWork.Tracked tracked = work.start();
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicBoolean drained = new AtomicBoolean();

        Thread waiter = new Thread(() -> {
            waiting.countDown();
            drained.set(work.awaitIdle(Duration.ofSeconds(30)));
        });
        waiter.start();
        assertTrue(waiting.await(5, TimeUnit.SECONDS));
        tracked.close();
        waiter.join(10_000);

        assertTrue(drained.get(), "the waiter did not observe the count reaching zero");
    }

    @Test
    void awaitIdleTimesOutWhileWorkIsStillRunning() {
        InFlightWork work = new InFlightWork();

        try (InFlightWork.Tracked tracked = work.start()) {
            assertFalse(work.awaitIdle(Duration.ofMillis(50)));
        }
    }

    @Test
    void drainReturnsAtOnceWithNothingInFlight() {
        InFlightWork work = new InFlightWork();
        PubSubDrain drain = new PubSubDrain(work, 30);

        drain.start();
        assertTrue(drain.isRunning());
        drain.stop();

        assertFalse(drain.isRunning());
    }

    @Test
    void drainWaitsForWorkAndThenReturns() throws InterruptedException {
        InFlightWork work = new InFlightWork();
        PubSubDrain drain = new PubSubDrain(work, 30);
        InFlightWork.Tracked tracked = work.start();
        AtomicBoolean finished = new AtomicBoolean();
        drain.start();

        Thread finisher = new Thread(() -> {
            sleepBriefly();
            finished.set(true);
            tracked.close();
        });
        finisher.start();
        drain.stop();

        // Not "the count is zero afterwards", which holds whether or not stop() waited for anything.
        assertTrue(finished.get(), "stop() returned while the work was still running");
        finisher.join(10_000);
        assertEquals(0, work.count());
    }

    @Test
    void workFinishingDuringTheDrainCanStillReportItsResult() throws InterruptedException {
        // The whole reason the drain runs in SmartLifecycle.stop() rather than a destroy callback: work that
        // drains and is then unable to publish is exactly the redelivery a drain exists to prevent. The
        // publisher stands in for every bean the finishing work still needs.
        InFlightWork work = new InFlightWork();
        RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();
        PubSubDrain drain = new PubSubDrain(work, 30);
        InFlightWork.Tracked tracked = work.start();
        drain.start();

        Thread worker = new Thread(() -> {
            sleepBriefly();
            try {
                publisher.publish(MardukQueues.JOB_EVENT_QUEUE, new MardukMessage().setBody("done"));
            } finally {
                tracked.close();
            }
        });
        worker.start();
        drain.stop();
        // What the context does next, once every Lifecycle bean has stopped.
        publisher.destroy();
        worker.join(10_000);

        assertEquals(1, publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).size(),
                "the work could not report its result, so it will be redelivered");
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    void drainGivesUpAfterItsTimeoutRatherThanBlockingTheShutdown() {
        // A drain that outlasts terminationGracePeriodSeconds is worse than an abandoned message: SIGKILL
        // arrives and the work is neither finished nor nacked.
        InFlightWork work = new InFlightWork();
        PubSubDrain drain = new PubSubDrain(work, 0);

        try (InFlightWork.Tracked tracked = work.start()) {
            // Preemptively, so a drain that ignores its timeout fails as an assertion rather than hanging
            // the build until surefire's own timeout.
            // A block lambda, not drain::stop: the method reference is ambiguous between Executable and
            // ThrowingSupplier.
            assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
                drain.stop();
            }, "stop() blocked past its timeout");
        }
    }

    @Test
    void theDrainStopsBeforeEveryOtherLifecycleBean() {
        // SmartLifecycle beans stop in reverse phase order, so the highest phase drains while the
        // publisher, the blob store and the datasource are all still up.
        assertEquals(Integer.MAX_VALUE, new PubSubDrain(new InFlightWork(), 30).getPhase());
    }
}
