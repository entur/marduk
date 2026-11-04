package no.rutebanken.marduk.gtfs;

import no.rutebanken.marduk.batch.BatchRunner;
import no.rutebanken.marduk.batch.BatchedRequests;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.InFlightWork;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.ET_CLIENT_NAME_HEADER;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.GTFS_ROUTE_DISPATCHER_HEADER_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USERNAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What one served batch asks damu to merge, and which of the requesting message's attributes travel with it.
 */
class MergedGtfsExportTest {

    private final BatchedRequests requests = mock(BatchedRequests.class);
    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);

    @BeforeEach
    void threeProvidersOfWhichOneMigratesOnwards() {
        when(providerRepository.getProviders()).thenReturn(List.of(
                provider("rb_avi", null),
                provider("rb_lev1", 3L),
                provider("rb_tst", null)));
    }

    private static Provider provider(String referential, Long migrateTo) {
        return new Provider().setChouetteInfo(
                new ChouetteInfo().setReferential(referential).setMigrateDataToProvider(migrateTo));
    }

    private MergedGtfsExport export(boolean scheduleEnabled) {
        return export(scheduleEnabled, 0);
    }

    private MergedGtfsExport export(boolean scheduleEnabled, long inactivityTimeoutMillis) {
        return new MergedGtfsExport(new BatchRunner(requests, () -> true, new InFlightWork()), requests,
                providerRepository, publisher, scheduleEnabled, inactivityTimeoutMillis);
    }

    private void waiting(MardukMessage... batched) {
        when(requests.claim(MergedGtfsExport.KIND))
                .thenReturn(new BatchedRequests.Batch(MergedGtfsExport.KIND, "claim", List.of(batched)));
        when(requests.waiting(MergedGtfsExport.KIND)).thenReturn(batched.length);
    }

    private static MardukMessage request(String correlationId) {
        return new MardukMessage()
                .setHeader(CORRELATION_ID, correlationId)
                .setHeader(DATASET_REFERENTIAL, "rb_avi")
                .setHeader(CHOUETTE_REFERENTIAL, "rb_avi")
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(ET_CLIENT_NAME_HEADER, "entur-marduk");
    }

    @Test
    void theMergeCoversOnlyTheProvidersThatKeepTheirOwnData() {
        // A provider that migrates its data onwards has no aggregated export of its own; the dataspace it
        // migrates into has it.
        waiting(request("corr"));

        export(true).serveTheBatch();

        assertEquals(
                "rb_avi-aggregated-gtfs.zip,rb_tst-aggregated-gtfs.zip",
                publisher.publishedTo(MardukQueues.GTFS_ROUTE_DISPATCHER_TOPIC).getFirst().body());
    }

    @Test
    void damuIsAskedToAggregateRatherThanExport() {
        // The dispatcher topic carries both requests and damu tells them apart by this attribute alone.
        waiting(request("corr"));

        export(true).serveTheBatch();

        assertEquals(
                "Aggregation",
                publisher.onlyPublished().attributes().get(GTFS_ROUTE_DISPATCHER_HEADER_NAME));
    }

    @Test
    void onlyTheHeadersTheAggregatorPreservedReachDamu() {
        // The aggregation strategy kept five headers from the newest request and dropped the rest. The batch
        // keeps whole messages, so without the restriction the admin caller's username and the triggering
        // export's file handle would newly be published onto a topic other services read.
        MardukMessage request = request("corr")
                .setHeader(USERNAME, "someone@entur.org")
                .setHeader(FILE_HANDLE, "rb_avi/netex.zip");
        waiting(request);

        export(true).serveTheBatch();

        assertEquals(
                Set.of(DATASET_REFERENTIAL, CORRELATION_ID, PROVIDER_ID, CHOUETTE_REFERENTIAL,
                        ET_CLIENT_NAME_HEADER, GTFS_ROUTE_DISPATCHER_HEADER_NAME),
                publisher.onlyPublished().attributes().keySet());
    }

    @Test
    void theNewestRequestsCorrelationIdIsTheOneDamuSees() {
        waiting(request("oldest"), request("newest"));

        export(true).serveTheBatch();

        assertEquals("newest", publisher.onlyPublished().attributes().get(CORRELATION_ID));
    }

    @Test
    void anEmptyBatchAsksDamuForNothing() {
        waiting();

        export(true).serveTheBatch();

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void theBatchIsServedOnceRequestsStopArriving() {
        waiting(request("corr"));
        MergedGtfsExport export = export(true);

        // The check that first sees the request only starts the quiet period.
        export.serveTheBatchOnceRequestsStopArriving();
        verify(requests, never()).claim(any());

        export.serveTheBatchOnceRequestsStopArriving();
        assertEquals(1, publisher.publishedTo(MardukQueues.GTFS_ROUTE_DISPATCHER_TOPIC).size());
        verify(requests).complete(any());
    }

    @Test
    void aRequestStillArrivingKeepsTheBatchWaiting() {
        // The regression this guards: on a fixed period, a burst straddling a tick becomes two merges.
        MergedGtfsExport export = export(true);
        when(requests.claim(MergedGtfsExport.KIND)).thenReturn(
                new BatchedRequests.Batch(MergedGtfsExport.KIND, "claim", List.of(request("corr"))));
        when(requests.waiting(MergedGtfsExport.KIND)).thenReturn(1, 2, 3);

        export.serveTheBatchOnceRequestsStopArriving();
        export.serveTheBatchOnceRequestsStopArriving();
        export.serveTheBatchOnceRequestsStopArriving();

        verify(requests, never()).claim(any());
    }

    @Test
    void aQuietPeriodShorterThanTheTimeoutServesNothing() {
        waiting(request("corr"));
        MergedGtfsExport export = export(true, 3_600_000);

        export.serveTheBatchOnceRequestsStopArriving();
        export.serveTheBatchOnceRequestsStopArriving();

        verify(requests, never()).claim(any());
    }

    @Test
    void anEmptyTableIsNotAQuietPeriodWorthServing() {
        waiting();
        MergedGtfsExport export = export(true);

        export.serveTheBatchOnceRequestsStopArriving();
        export.serveTheBatchOnceRequestsStopArriving();

        verify(requests, never()).claim(any());
    }

    @Test
    void autoStartupFalseStopsTheScheduleButNotTheOperation() {
        // The flag used to decide whether the consumer route started, which also disabled the admin endpoint.
        waiting(request("corr"));
        MergedGtfsExport export = export(false);

        export.serveTheBatchOnceRequestsStopArriving();
        export.serveTheBatchOnceRequestsStopArriving();
        verify(requests, never()).claim(any());

        export.serveTheBatch();
        assertEquals(1, publisher.published().size());
    }
}
