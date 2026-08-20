package no.rutebanken.marduk.netex;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.GTFS_ROUTE_DISPATCHER_EXPORT_HEADER_VALUE;
import static no.rutebanken.marduk.Constants.GTFS_ROUTE_DISPATCHER_HEADER_NAME;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.SYSTEM_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MergedNetexPublicationTest {

    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private DatedExportUpload datedExportUpload;
    private ChouetteInfo chouetteInfo;

    @BeforeEach
    void setUp() {
        publisher = new RecordingPubSubPublisher();
        datedExportUpload = mock(DatedExportUpload.class);

        Provider provider = new Provider();
        provider.setId(1002L);
        chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential("rb_rut");
        provider.setChouetteInfo(chouetteInfo);
        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(1002L)).thenReturn(provider);
    }

    private MergedNetexPublication publication(
            boolean useChouetteGtfsExport, boolean lineStatisticsCalculationEnabled) {
        return new MergedNetexPublication(
                providerRepository,
                datedExportUpload,
                new JobEventPublisher(publisher),
                publisher,
                useChouetteGtfsExport,
                lineStatisticsCalculationEnabled);
    }

    private static MardukMessage mergedDataset() {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 1002L)
                .setHeader(CHOUETTE_REFERENTIAL, "rb_rut")
                .setHeader(DATASET_REFERENTIAL, "rut")
                .setHeader(CORRELATION_ID, "corr");
    }

    @Test
    void theOtpGraphBuildIsTriggeredAndReportedAsPending() {
        publication(true, false).publishMergedDataset(mergedDataset());

        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_GRAPH_BUILD_QUEUE).size());
        JobEvent reported = JobEvent.fromString(
                publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getFirst().body());
        assertEquals(JobEvent.TimetableAction.OTP2_BUILD_GRAPH.name(), reported.getAction());
        assertEquals(JobEvent.State.PENDING, reported.getState());
    }

    @Test
    void theExportNotificationCarriesTheBareCodespaceAndNoHeaders() {
        publication(true, false).publishMergedDataset(mergedDataset());

        var notification = publisher.publishedTo(MardukQueues.NETEX_EXPORT_NOTIFICATION_QUEUE).getFirst();
        assertEquals("rut", notification.body());
        assertTrue(notification.attributes().isEmpty(), "the notification leaked headers: " + notification);
    }

    @Test
    void strippingTheNotificationSHeadersDoesNotAffectTheRestOfThePublication() {
        // The route wireTapped a copy, so the removeHeaders("*") could not reach the main flow.
        MardukMessage message = mergedDataset();

        publication(true, false).publishMergedDataset(message);

        assertEquals("rb_rut", publisher.publishedTo(MardukQueues.OTP2_GRAPH_BUILD_QUEUE).getFirst()
                .attributes().get(CHOUETTE_REFERENTIAL));
    }

    @Test
    void damuIsAskedToExportGtfsForTheCodespace() {
        publication(true, false).publishMergedDataset(mergedDataset());

        var dispatched = publisher.publishedTo(MardukQueues.GTFS_ROUTE_DISPATCHER_TOPIC).getFirst();
        assertEquals("rb_rut", dispatched.body());
        assertEquals(GTFS_ROUTE_DISPATCHER_EXPORT_HEADER_VALUE,
                dispatched.attributes().get(GTFS_ROUTE_DISPATCHER_HEADER_NAME));
        // Removed so damu does not mistake the merged dataset's referential for its own export target.
        assertNull(dispatched.attributes().get(DATASET_REFERENTIAL));
    }

    @Test
    void damuGetsThePendingExportEventOnTheMessageWhenItOwnsTheGtfsExport() {
        // The route built the EXPORT/PENDING event without reporting it. All that survives is the
        // RutebankenSystemStatus header it leaves behind, which is what travels to damu.
        publication(false, false).publishMergedDataset(mergedDataset());

        var dispatched = publisher.publishedTo(MardukQueues.GTFS_ROUTE_DISPATCHER_TOPIC).getFirst();
        JobEvent carried = JobEvent.fromString(dispatched.attributes().get(SYSTEM_STATUS));
        assertEquals(JobEvent.TimetableAction.EXPORT.name(), carried.getAction());
        assertEquals(JobEvent.State.PENDING, carried.getState());
        // Still exactly one reported event: the graph build. The export one is never sent to nabu.
        assertEquals(1, publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).size());
    }

    @Test
    void lineStatisticsAreOnlyRequestedWhenEnabled() {
        publication(true, false).publishMergedDataset(mergedDataset());
        assertTrue(publisher.publishedTo(MardukQueues.LINE_STATISTICS_CALCULATION_QUEUE).isEmpty());

        publisher.clear();
        publication(true, true).publishMergedDataset(mergedDataset());
        assertEquals(1, publisher.publishedTo(MardukQueues.LINE_STATISTICS_CALCULATION_QUEUE).size());
    }

    @Test
    void aDatedCopyIsKeptOnlyForCodespacesGeneratingDatedServiceJourneyIds() {
        publication(true, false).publishMergedDataset(mergedDataset());
        verify(datedExportUpload, never()).copyDatedExport(any());

        chouetteInfo.setGenerateDatedServiceJourneyIds(true);
        MardukMessage message = mergedDataset();
        publication(true, false).publishMergedDataset(message);
        verify(datedExportUpload).copyDatedExport(message);
    }
}
