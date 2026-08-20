package no.rutebanken.marduk.batch;

import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.InFlightWork;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BatchRunnerTest {

    private static final String KIND = "test-kind";

    private final BatchedRequests requests = mock(BatchedRequests.class);
    private final List<String> ran = new ArrayList<>();

    private BatchRunner runner(boolean leader) {
        return new BatchRunner(requests, () -> leader, new InFlightWork());
    }

    private static BatchedRequests.Batch batchOf(String... correlationIds) {
        List<MardukMessage> messages = new ArrayList<>();
        for (String correlationId : correlationIds) {
            messages.add(new MardukMessage().setHeader(CORRELATION_ID, correlationId));
        }
        return new BatchedRequests.Batch(KIND, UUID.randomUUID().toString(), messages);
    }

    @Test
    void theLeaderServesTheBatchAndCompletesIt() {
        BatchedRequests.Batch batch = batchOf("first", "second");
        when(requests.claim(KIND)).thenReturn(batch);

        runner(true).run(KIND, message -> ran.add(message.getHeader(CORRELATION_ID, String.class)));

        assertEquals(List.of("second"), ran, "the job runs once, for the newest request");
        verify(requests).complete(batch);
    }

    @Test
    void aFollowerDoesNotEvenClaim() {
        // Claiming without serving would hide the requests from the leader until the claim was released.
        runner(false).run(KIND, message -> ran.add("ran"));

        verify(requests, never()).claim(any());
        assertTrue(ran.isEmpty());
    }

    @Test
    void anEmptyBatchRunsNothing() {
        when(requests.claim(KIND)).thenReturn(batchOf());

        runner(true).run(KIND, message -> ran.add("ran"));

        assertTrue(ran.isEmpty());
        verify(requests, never()).complete(any());
    }

    @Test
    void aFailedJobReleasesTheBatchForTheNextTick() {
        BatchedRequests.Batch batch = batchOf("first");
        when(requests.claim(KIND)).thenReturn(batch);

        assertThrows(IllegalStateException.class, () -> runner(true).run(KIND, message -> {
            throw new IllegalStateException("the build failed");
        }));

        verify(requests).release(batch);
        verify(requests, never()).complete(any());
    }

    @Test
    void aFailingReleaseDoesNotHideWhyTheJobFailed() {
        BatchedRequests.Batch batch = batchOf("first");
        when(requests.claim(KIND)).thenReturn(batch);
        doThrow(new IllegalArgumentException("the database is gone")).when(requests).release(batch);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> runner(true).run(KIND, message -> {
                    throw new IllegalStateException("the build failed");
                }));

        assertEquals("the build failed", thrown.getMessage());
        assertEquals("the database is gone", thrown.getSuppressed()[0].getMessage());
    }

    @Test
    void theBatchIsCompletedOutsideTheJobsTry() {
        // complete() inside the try would let a failure in it be caught as if the job had failed, and the
        // requests would then be both deleted and released.
        BatchedRequests.Batch batch = batchOf("first");
        when(requests.claim(KIND)).thenReturn(batch);
        doThrow(new IllegalStateException("the delete failed")).when(requests).complete(batch);

        assertThrows(IllegalStateException.class, () -> runner(true).run(KIND, message -> ran.add("ran")));

        verify(requests, never()).release(any());
    }

    @Test
    void oneKindDoesNotRunWhileAnotherInItsExclusionGroupIs() {
        // Production and candidate graph builds shared a work directory and a published path, and the
        // aggregate controllers serialised them by sharing a route. Nothing else does now.
        String group = "graph-build";
        String otherKind = "other-kind";
        when(requests.claim(KIND)).thenReturn(batchOf("first"));
        when(requests.claim(otherKind)).thenReturn(batchOf("second"));
        BatchRunner runner = runner(true);

        runner.run(group, KIND, message -> {
            ran.add("outer");
            runner.run(group, otherKind, inner -> ran.add("inner"));
        });

        assertEquals(List.of("outer"), ran);
        verify(requests, never()).claim(otherKind);
    }

    @Test
    void anUnrelatedKindIsNotHeldUp() {
        String otherKind = "other-kind";
        when(requests.claim(KIND)).thenReturn(batchOf("first"));
        when(requests.claim(otherKind)).thenReturn(batchOf("second"));
        BatchRunner runner = runner(true);

        runner.run("one-group", KIND, message -> {
            ran.add("outer");
            runner.run("another-group", otherKind, inner -> ran.add("inner"));
        });

        assertEquals(List.of("outer", "inner"), ran);
    }

    @Test
    void aRunningBatchIsCountedAsInFlightWorkSoShutdownWaitsForIt() {
        InFlightWork inFlightWork = new InFlightWork();
        when(requests.claim(KIND)).thenReturn(batchOf("first"));

        new BatchRunner(requests, () -> true, inFlightWork)
                .run(KIND, message -> ran.add(String.valueOf(inFlightWork.count())));

        assertEquals(List.of("1"), ran);
        assertEquals(0, inFlightWork.count(), "the batch was left counted as in flight after it finished");
    }

    @Test
    void theStaleClaimSweepOnlyRunsOnTheLeader() {
        runner(false).releaseStaleClaimsOnSchedule();
        verify(requests, never()).releaseStaleClaims();

        runner(true).releaseStaleClaimsOnSchedule();
        verify(requests).releaseStaleClaims();
    }

    @Test
    void theBatchRunsUnderTheNewestRequestsCorrelationId() {
        when(requests.claim(KIND)).thenReturn(batchOf("first", "newest"));

        runner(true).run(KIND, message -> ran.add(org.slf4j.MDC.get("correlationId")));

        assertEquals(List.of("newest"), ran);
    }
}
