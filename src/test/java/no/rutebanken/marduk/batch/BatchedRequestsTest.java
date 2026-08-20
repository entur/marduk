package no.rutebanken.marduk.batch;

import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Against the real schema, because what this class is about is the claim statement and the migration.
 */
class BatchedRequestsTest extends MardukSpringBootBaseTest {

    private static final String KIND = "test-kind";
    private static final String OTHER_KIND = "other-kind";

    @Autowired
    private BatchedRequests requests;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void emptyTheTable() {
        jdbcTemplate.update("DELETE FROM batched_request");
    }

    private static MardukMessage request(String correlationId) {
        return new MardukMessage()
                .setHeader(CORRELATION_ID, correlationId)
                .setHeader(PROVIDER_ID, 2L);
    }

    @Test
    void aRecordedRequestIsWaiting() {
        requests.record(KIND, request("first"));

        assertEquals(1, requests.waiting(KIND));
    }

    @Test
    void claimingTakesEverythingWaitingForThatKind() {
        requests.record(KIND, request("first"));
        requests.record(KIND, request("second"));
        requests.record(OTHER_KIND, request("elsewhere"));

        BatchedRequests.Batch batch = requests.claim(KIND);

        assertEquals(2, batch.size());
        assertEquals(1, requests.waiting(OTHER_KIND), "another kind's requests were swept up");
    }

    @Test
    void theBatchIsRunForTheNewestRequest() {
        // The aggregation strategy kept the last message's headers; an older request's are already stale.
        requests.record(KIND, request("first"));
        requests.record(KIND, request("second"));
        requests.record(KIND, request("third"));

        assertEquals("third", requests.claim(KIND).newest().getHeader(CORRELATION_ID, String.class));
    }

    @Test
    void headersSurviveTheRoundTripThroughTheTable() {
        requests.record(KIND, request("first"));

        MardukMessage restored = requests.claim(KIND).newest();

        assertEquals("first", restored.getHeader(CORRELATION_ID, String.class));
        assertEquals(2L, restored.getHeader(PROVIDER_ID, Long.class));
    }

    @Test
    void aClaimedRequestIsNotClaimedAgain() {
        // Two runners can overlap during a leadership handover, and the batch must not run twice.
        requests.record(KIND, request("first"));

        BatchedRequests.Batch first = requests.claim(KIND);
        BatchedRequests.Batch second = requests.claim(KIND);

        assertEquals(1, first.size());
        assertTrue(second.isEmpty());
        assertNotEquals(first.claimId(), second.claimId());
    }

    @Test
    void completingABatchRemovesItsRequests() {
        requests.record(KIND, request("first"));
        BatchedRequests.Batch batch = requests.claim(KIND);

        requests.complete(batch);

        assertEquals(0, requests.waiting(KIND));
        assertEquals(0, rowsFor(KIND), "the rows are still there after the batch was served");
    }

    @Test
    void releasingABatchPutsItsRequestsBack() {
        // A failed run has to be retried; that is the reason the requests are rows and not heap.
        requests.record(KIND, request("first"));
        requests.record(KIND, request("second"));
        BatchedRequests.Batch batch = requests.claim(KIND);

        requests.release(batch);

        assertEquals(2, requests.waiting(KIND));
        assertEquals(2, requests.claim(KIND).size());
    }

    @Test
    void requestsRecordedWhileABatchRunsAreNotLost() {
        // The next tick has to see them, or a request that arrived mid-build is silently dropped.
        requests.record(KIND, request("first"));
        BatchedRequests.Batch running = requests.claim(KIND);
        requests.record(KIND, request("arrived while the batch was running"));

        requests.complete(running);

        assertEquals(1, requests.waiting(KIND));
        assertEquals("arrived while the batch was running",
                requests.claim(KIND).newest().getHeader(CORRELATION_ID, String.class));
    }

    @Test
    void theAggregateHasAnIdentityOfItsOwn() {
        // setNewCorrelationId gave the aggregated exchange an id of its own while the grouped messages kept
        // theirs. Reusing one request's id for the batch would stamp that one provider's job event with it.
        requests.record(KIND, request("first"));
        requests.record(KIND, request("newest"));
        BatchedRequests.Batch batch = requests.claim(KIND);

        MardukMessage aggregate = batch.aggregate();

        assertEquals(batch.claimId(), aggregate.getHeader(CORRELATION_ID, String.class));
        assertEquals(List.of("first", "newest"),
                batch.requests().stream().map(m -> m.getHeader(CORRELATION_ID, String.class)).toList(),
                "a contributing request was stamped with the batch's own correlation id");
    }

    @Test
    void theAggregateTakesNoHeadersFromEitherEndOfTheBatch() {
        // GroupedMessageAggregationStrategy aggregated into a new DefaultExchange(newExchange), whose
        // constructor copies the context and the unit of work but not the in message, so the OTP2 builds ran
        // on an exchange holding nothing but the correlation id setNewCorrelationId gave it. Neither the
        // first request's headers nor the newest's.
        requests.record(KIND, request("first"));
        requests.record(KIND, request("newest"));
        BatchedRequests.Batch batch = requests.claim(KIND);

        MardukMessage aggregate = batch.aggregate();

        assertEquals(Set.of(CORRELATION_ID), aggregate.getHeaders().keySet());
        assertNull(aggregate.getHeader(PROVIDER_ID),
                "a provider id from one arbitrary request reached the aggregate");
    }

    @Test
    void aClaimLeftBehindByADeadPodIsPutBack() {
        // The failure this guards: claim_id is set, the pod dies, and nothing ever clears it. The row is
        // invisible to waiting() and to claim(), so the export those requests asked for never runs again.
        requests.record(KIND, request("first"));
        BatchedRequests.Batch stranded = requests.claim(KIND);
        assertEquals(0, requests.waiting(KIND), "a claimed request is not waiting, which is the problem");
        backdateClaim(stranded, requests.claimTimeout().plusMinutes(1));

        assertEquals(1, requests.releaseStaleClaims());

        assertEquals(1, requests.waiting(KIND));
        assertEquals(1, requests.claim(KIND).size(), "the stranded request is claimable again");
    }

    @Test
    void aClaimYoungerThanTheTimeoutIsLeftAlone() {
        // Stealing the rows of a running batch would run the job twice, concurrently, on the same paths.
        requests.record(KIND, request("first"));
        BatchedRequests.Batch running = requests.claim(KIND);
        backdateClaim(running, requests.claimTimeout().minusMinutes(1));

        assertEquals(0, requests.releaseStaleClaims());

        assertEquals(0, requests.waiting(KIND));
    }

    @Test
    void theDefaultClaimTimeoutOutlastsTheLongestJobItCanInterrupt() throws IOException {
        // The longest batched job is an OTP2 graph build, bounded by the Kubernetes job timeout. A claim
        // timeout below it would steal the rows of a healthy build that is still waiting for its job.
        Duration graphBuildTimeout = Duration.ofSeconds(graphBuildTimeoutSecondsFromHelm());

        assertTrue(requests.claimTimeout().compareTo(graphBuildTimeout) > 0,
                "the claim timeout is " + requests.claimTimeout() + ", which is not longer than the "
                        + graphBuildTimeout + " a graph build may take");
    }

    private static long graphBuildTimeoutSecondsFromHelm() throws IOException {
        String configMap = Files.readString(
                Path.of("helm", "marduk", "templates", "configmap.yaml"));
        Matcher matcher = Pattern.compile("otp\\.graph\\.build\\.remote\\.kubernetes\\.timeout=(\\d+)")
                .matcher(configMap);
        assertTrue(matcher.find(), "the graph build timeout is no longer in the ConfigMap under that name");
        return Long.parseLong(matcher.group(1));
    }

    private void backdateClaim(BatchedRequests.Batch batch, Duration age) {
        jdbcTemplate.update("UPDATE batched_request SET claimed_at = ? WHERE claim_id = ?",
                Timestamp.valueOf(LocalDateTime.now().minus(age)), batch.claimId());
    }

    private int rowsFor(String kind) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM batched_request WHERE kind = ?", Integer.class, kind);
        return count == null ? 0 : count;
    }
}
