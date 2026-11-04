package no.rutebanken.marduk.otp;

import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.pipeline.MardukMessage;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * The four consumers only write a row and return, which is the point: the message is acknowledged before the
 * build starts, so no subscription needs a four-hour ack extension any more.
 */
class Otp2GraphBuildConsumersTest {

    private final BatchedRequests requests = mock(BatchedRequests.class);

    private static MardukMessage request() {
        return new MardukMessage()
                .setHeader(CORRELATION_ID, "corr-id")
                .setHeader(CHOUETTE_REFERENTIAL, "rb_rut");
    }

    @Test
    void aStreetGraphRequestIsRecordedUnderTheStreetGraphKind() {
        MardukMessage message = request();

        new Otp2BaseGraphBuildConsumer(requests).handle(message);

        verify(requests).record(Otp2BaseGraphBuild.KIND, message);
    }

    @Test
    void aCandidateStreetGraphRequestIsRecordedUnderItsOwnKind() {
        MardukMessage message = request();

        new Otp2BaseGraphCandidateBuildConsumer(requests).handle(message);

        verify(requests).record(Otp2BaseGraphBuild.CANDIDATE_KIND, message);
    }

    @Test
    void aTransitGraphRequestIsRecordedUnderTheTransitGraphKind() {
        MardukMessage message = request();

        new Otp2NetexGraphBuildConsumer(requests).handle(message);

        verify(requests).record(Otp2NetexGraphBuild.KIND, message);
    }

    @Test
    void aCandidateTransitGraphRequestIsRecordedUnderItsOwnKind() {
        MardukMessage message = request();

        new Otp2NetexGraphCandidateBuildConsumer(requests).handle(message);

        verify(requests).record(Otp2NetexGraphBuild.CANDIDATE_KIND, message);
    }

    @Test
    void theFourKindsAreDistinctSoTheBatchesCannotBeServedByTheWrongBuild() {
        assertEquals(4, Set.of(
                Otp2BaseGraphBuild.KIND,
                Otp2BaseGraphBuild.CANDIDATE_KIND,
                Otp2NetexGraphBuild.KIND,
                Otp2NetexGraphBuild.CANDIDATE_KIND).size());
    }
}
