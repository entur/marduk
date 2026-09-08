package no.rutebanken.marduk.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;

/**
 * Requests waiting to be served in one batch.
 *
 * <p>Some work is worth doing once for many requests: building an OTP graph, or exporting the merged GTFS
 * dataset. Those requests arrive one per PubSub message, and running the job per message would repeat an
 * expensive job for no gain.
 *
 * <p>Replaces the Camel aggregator that did this in heap. Two things change. A request is a row, so it
 * outlives the pod that received it - the aggregator held the messages unacknowledged, which is why those
 * subscriptions needed a four-hour ack extension, and a pod dying mid-build lost the batch unless PubSub
 * happened to redeliver in time. And the message is acknowledged as soon as the row is written, so a slow
 * job no longer holds a subscription's flow-control budget.
 */
@Component
public class BatchedRequests {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchedRequests.class);

    private static final TypeReference<Map<String, Object>> HEADERS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final Duration claimTimeout;

    public BatchedRequests(
            JdbcTemplate jdbcTemplate,
            // Longer than any run can legitimately take, or a healthy batch has its rows stolen mid-flight
            // and the job runs twice. The longest is an OTP2 graph build, bounded by
            // otp.graph.build.remote.kubernetes.timeout, which the ConfigMap sets to 12000 s. Asserted
            // against that value by BatchedRequestsTest.
            @Value("${marduk.batch.claim.timeout:PT5H}") Duration claimTimeout) {
        this.jdbcTemplate = jdbcTemplate;
        this.claimTimeout = claimTimeout;
    }

    /** One claimed batch. Empty means there was nothing waiting. */
    public record Batch(String kind, String claimId, List<MardukMessage> requests) {

        public boolean isEmpty() {
            return requests.isEmpty();
        }

        public int size() {
            return requests.size();
        }

        /**
         * The newest request, which is what the merged GTFS export runs on.
         *
         * <p>Only that aggregation preserved a request's headers. It used
         * {@code HeaderPreservingGroupedMessageAggregationStrategy}, which overwrote the aggregate's
         * headers with five of them off the last message to arrive - so the newest request's correlation id
         * is the one damu sees. The OTP2 builds ran on an aggregate with no request's headers at all; they
         * use {@link #aggregate()}.
         */
        public MardukMessage newest() {
            return requests.getLast();
        }

        /**
         * The message the batch runs as, for a job whose identity is the batch's and not any one request's.
         *
         * <p>No headers but the correlation id, because that is all the OTP2 routes' aggregated exchange
         * had. {@code GroupedMessageAggregationStrategy} overrides {@code aggregate} to put the grouped
         * messages in the body of a {@code new DefaultExchange(newExchange)} - "the outgoing exchange must
         * not be one of the grouped exchanges" - and that constructor copies the context, the pattern, the
         * from-endpoint and the unit of work, not the in message. So the aggregate started empty and
         * {@code setNewCorrelationId} gave it the only header it carried.
         *
         * <p>Taking a request's headers instead is not a cosmetic difference: the finished street graph
         * build publishes this message to the transit build's queue, and every header on it goes out as an
         * attribute. One arbitrary provider's referential would arrive there and be reported a graph job it
         * never asked for.
         */
        public MardukMessage aggregate() {
            return new MardukMessage().setHeader(CORRELATION_ID, claimId);
        }
    }

    /** Records a request and returns immediately; the caller acknowledges its message. */
    public void record(String kind, MardukMessage request) {
        jdbcTemplate.update("INSERT INTO batched_request (kind, headers) VALUES (?, ?)",
                kind, toJson(request.getHeaders()));
        LOGGER.info("Recorded a {} request to be served in the next batch", kind);
    }

    /**
     * Takes everything waiting for {@code kind}, oldest first.
     *
     * <p>One statement, so two runners cannot claim the same row: the {@code UPDATE} is what decides the
     * winner. Leader election makes a second runner unlikely, not impossible - a leadership handover can
     * overlap.
     */
    public Batch claim(String kind) {
        String claimId = UUID.randomUUID().toString();
        jdbcTemplate.update("UPDATE batched_request SET claim_id = ?, claimed_at = current_timestamp "
                + "WHERE kind = ? AND claim_id IS NULL", claimId, kind);
        List<String> claimed = jdbcTemplate.queryForList(
                "SELECT headers FROM batched_request WHERE claim_id = ? ORDER BY id", String.class, claimId);
        if (!claimed.isEmpty()) {
            LOGGER.debug("Claimed {} {} requests as {}", claimed.size(), kind, claimId);
        }
        return new Batch(kind, claimId, claimed.stream().map(BatchedRequests::toMessage).toList());
    }

    /** The batch was served; the requests in it are done with. */
    public void complete(Batch batch) {
        int deleted = jdbcTemplate.update("DELETE FROM batched_request WHERE claim_id = ?", batch.claimId());
        LOGGER.debug("Completed {} {} requests", deleted, batch.kind());
    }

    /**
     * The batch was not served. The requests go back in the queue rather than being dropped, so the next
     * tick tries again - which is the whole point of keeping them in a table.
     */
    public void release(Batch batch) {
        int released = jdbcTemplate.update(
                "UPDATE batched_request SET claim_id = NULL, claimed_at = NULL WHERE claim_id = ?",
                batch.claimId());
        LOGGER.warn("Released {} {} requests after a failed run; they will be retried", released, batch.kind());
    }

    /**
     * Releases claims older than {@code marduk.batch.claim.timeout}.
     *
     * <p>The claim is what makes a batch survive the pod serving it. It also strands it: a claimed row is
     * invisible to {@link #waiting} and nothing else clears {@code claim_id}, so a pod dying mid-batch
     * takes those requests out of circulation permanently and the export they asked for never runs again.
     *
     * @return how many requests were put back
     */
    public int releaseStaleClaims() {
        Timestamp cutoff = Timestamp.valueOf(LocalDateTime.now().minus(claimTimeout));
        int released = jdbcTemplate.update("UPDATE batched_request SET claim_id = NULL, claimed_at = NULL "
                + "WHERE claim_id IS NOT NULL AND claimed_at < ?", cutoff);
        if (released > 0) {
            LOGGER.warn("Released {} requests claimed more than {} ago; whatever claimed them is gone",
                    released, claimTimeout);
        }
        return released;
    }

    /** The claim timeout in force, so a test can check it against the graph build timeout. */
    public Duration claimTimeout() {
        return claimTimeout;
    }

    /** How many requests are waiting. */
    public int waiting(String kind) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM batched_request WHERE kind = ? AND claim_id IS NULL", Integer.class, kind);
        return count == null ? 0 : count;
    }

    private static String toJson(Map<String, Object> headers) {
        try {
            return ObjectMapperFactory.getSharedObjectMapper().writeValueAsString(headers);
        } catch (IOException e) {
            throw new MardukException("Could not store a batched request", e);
        }
    }

    private static MardukMessage toMessage(String json) {
        try {
            Map<String, Object> headers = ObjectMapperFactory.getSharedObjectMapper().readValue(json, HEADERS);
            return new MardukMessage(new HashMap<>(headers), "");
        } catch (IOException e) {
            throw new MardukException("Could not read a batched request", e);
        }
    }
}
