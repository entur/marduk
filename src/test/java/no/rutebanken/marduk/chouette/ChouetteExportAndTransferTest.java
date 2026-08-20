package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.AntuValidation;
import no.rutebanken.marduk.validation.NetexValidationProfiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.ORIGINAL_PROVIDER_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The three Chouette steps that follow a validation: the NeTEx export, the blocks export, and the transfer to
 * the next dataspace.
 */
class ChouetteExportAndTransferTest {

    private static final String ANTU = "antu-exchange";
    private static final String EXPORT_DATA = "/chouette_iev/referentials/rb_rut/data/1/exported.zip";

    private ChouetteStub chouette;
    private ChouetteClient client;
    private ChouetteJobs chouetteJobs;
    private InMemoryMardukBlobStoreRepository internalRepository;
    private InMemoryMardukBlobStoreRepository antuRepository;
    private RecordingPubSubPublisher publisher;
    private ProviderRepository providerRepository;
    private ChouetteNetexExport export;
    private AntuValidation antuValidation;

    @BeforeEach
    void setUp() throws IOException {
        chouette = new ChouetteStub();
        chouette.answersWithLocation("http://chouette/chouette_iev/referentials/rb_rut/scheduled_jobs/1");
        chouette.answers("jobs", "[]");
        chouette.answers("exported.zip", "the netex archive");
        client = chouette.client();
        publisher = new RecordingPubSubPublisher();

        Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
        internalRepository = new InMemoryMardukBlobStoreRepository(buckets);
        internalRepository.setContainerName("marduk-internal");
        antuRepository = new InMemoryMardukBlobStoreRepository(buckets);
        antuRepository.setContainerName(ANTU);

        providerRepository = mock(ProviderRepository.class);
        when(providerRepository.getProvider(2L)).thenReturn(provider(2L, "rut", 4L, true));
        when(providerRepository.getProvider(4L)).thenReturn(provider(4L, "rb_rut", null, true));
        when(providerRepository.getProvider(5L)).thenReturn(provider(5L, "rb_noblocks", null, false));
        chouetteJobs = new ChouetteJobs(client, providerRepository);

        MardukInternalBlobStoreService internalBlobStore =
                new MardukInternalBlobStoreService("marduk-internal", internalRepository);
        export = new ChouetteNetexExport(client, internalBlobStore, noRetries());
        antuValidation = new AntuValidation(internalBlobStore, providerRepository,
                new NetexValidationProfiles(List.of(), List.of("OYM")),
                new JobEventPublisher(publisher), publisher, ANTU);
    }

    @AfterEach
    void tearDown() throws IOException {
        chouetteJobs.stopFanOut();
        client.close();
        chouette.close();
    }

    private static Provider provider(long id, String referential, Long migrateTo, boolean blocks) {
        Provider provider = new Provider();
        provider.setId(id);
        ChouetteInfo info = new ChouetteInfo();
        info.setReferential(referential);
        info.setMigrateDataToProvider(migrateTo);
        info.setEnableBlocksExport(blocks);
        provider.setChouetteInfo(info);
        return provider;
    }

    private static MardukMessage request(long providerId) {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, providerId)
                .setHeader(CORRELATION_ID, "corr");
    }

    private MardukMessage exportResult(String actionReport) {
        return request(4L)
                .setHeader(CHOUETTE_REFERENTIAL, "rb_rut")
                .setHeader("action_report_result", actionReport)
                .setHeader("data_url", chouette.url(EXPORT_DATA));
    }

    private List<JobEvent> reportedEvents() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(p -> JobEvent.fromString(p.body()))
                .toList();
    }

    // ---------------------------------------------------------------------------------- netex export

    @Test
    void theNetexExporterIsCalledForTheDataspace() {
        netexExportConsumer(true).handle(request(4L));

        assertEquals(List.of("/chouette_iev/referentials/rb_rut/exporter/netexprofile"), chouette.paths());
        assertEquals("direct:processNetexExportResult", publisher
                .publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst()
                .attributes().get(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION));
    }

    @Test
    void aFinishedExportIsStoredWhereTheRestOfThePipelineReadsIt() {
        netexExportResultHandler(true).handle(exportResult("OK"));

        assertTrue(internalRepository.exist("chouette/netex/rb_rut-aggregated-netex.zip"));
    }

    @Test
    void aFinishedExportTriggersTheFlexibleLinesMergeAndTheBlocksExport() {
        netexExportResultHandler(true).handle(exportResult("OK"));

        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).size());
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE).size());
    }

    @Test
    void aFinishedExportIsHandedToAntuForPostValidation() {
        netexExportResultHandler(true).handle(exportResult("OK"));

        assertEquals(Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION,
                publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst()
                        .attributes().get(VALIDATION_STAGE_HEADER));
        assertTrue(antuRepository.exist("chouette/netex/rb_rut-aggregated-netex.zip"),
                "antu cannot validate a file it cannot reach");
    }

    @Test
    void withPostValidationOffTheArchiveIsParkedAndNothingDownstreamIsTriggered() {
        netexExportResultHandler(false).handle(exportResult("OK"));

        assertTrue(internalRepository.exist("chouette/netex-before-validation/rb_rut-aggregated-netex.zip"));
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).isEmpty());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE).isEmpty());
    }

    @Test
    void anEmptyExportIsReportedWithItsOwnErrorCode() {
        MardukMessage message = exportResult("NOK")
                .setHeader(ChouetteJobPoller.CHOUETTE_FAILURE_CODE,
                        JobEvent.CHOUETTE_JOB_FAILURE_CODE_NO_DATA_PROCEEDED);

        netexExportResultHandler(true).handle(message);

        assertEquals(JobEvent.JOB_ERROR_NETEX_EXPORT_EMPTY,
                message.getHeader(Constants.JOB_ERROR_CODE, String.class));
        assertEquals(JobEvent.State.FAILED, reportedEvents().getLast().getState());
    }

    @Test
    void aFailedExportStoresNothing() {
        netexExportResultHandler(true).handle(exportResult("NOK"));

        assertFalse(internalRepository.exist("chouette/netex/rb_rut-aggregated-netex.zip"));
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
    }

    // --------------------------------------------------------------------------------- blocks export

    @Test
    void aProviderWithoutBlocksIsSkippedWithoutCallingChouette() {
        blocksExportConsumer().handle(request(5L));

        assertTrue(chouette.received().isEmpty());
        assertTrue(publisher.published().isEmpty(), "a skipped export must not show up as a job");
    }

    @Test
    void aProviderWithBlocksGetsASecondExport() {
        blocksExportConsumer().handle(request(4L));

        assertEquals(List.of("/chouette_iev/referentials/rb_rut/exporter/netexprofile"), chouette.paths());
        assertEquals("direct:processNetexBlocksExportResult", publisher
                .publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst()
                .attributes().get(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION));
    }

    @Test
    void aFinishedBlocksExportIsStoredUnderItsOwnPrefixAndPostValidated() {
        blocksExportResultHandler().handle(exportResult("OK"));

        assertTrue(internalRepository.exist("chouette/netex-with-blocks/rb_rut-aggregated-netex.zip"));
        assertEquals(Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION,
                publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst()
                        .attributes().get(VALIDATION_STAGE_HEADER));
    }

    @Test
    void aBlocksExportDoesNotStartAnotherExport() {
        blocksExportResultHandler().handle(exportResult("OK"));

        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE).isEmpty());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).isEmpty());
    }

    // -------------------------------------------------------------------------------------- transfer

    @Test
    void theTransferExporterIsCalledForTheSourceDataspace() {
        transferConsumer().handle(request(2L));

        assertEquals(List.of("/chouette_iev/referentials/rut/exporter/transfer"), chouette.paths());
        assertEquals("direct:processTransferExportResult", publisher
                .publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst()
                .attributes().get(CHOUETTE_JOB_STATUS_ROUTING_DESTINATION));
    }

    @Test
    void aFinishedTransferHandsTheWorkToTheDestinationDataspace() {
        MardukMessage message = request(2L)
                .setHeader(CHOUETTE_REFERENTIAL, "rut")
                .setHeader("action_report_result", "OK");

        transferResultHandler().handle(message);

        assertEquals(4L, message.getHeader(PROVIDER_ID, Long.class));
        assertEquals("2", message.getHeader(ORIGINAL_PROVIDER_ID, String.class));
        assertEquals("VALIDATION_LEVEL_2", publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE)
                .getFirst().attributes().get(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL));
    }

    @Test
    void aChainOfTransfersStillPointsBackAtTheProviderTheDataCameFrom() {
        MardukMessage message = request(2L)
                .setHeader(CHOUETTE_REFERENTIAL, "rut")
                .setHeader(ORIGINAL_PROVIDER_ID, 99L)
                .setHeader("action_report_result", "OK");

        transferResultHandler().handle(message);

        assertEquals("99", message.getHeader(ORIGINAL_PROVIDER_ID, String.class));
    }

    @Test
    void aFailedTransferMovesNothingOn() {
        MardukMessage message = request(2L)
                .setHeader(CHOUETTE_REFERENTIAL, "rut")
                .setHeader("action_report_result", "NOK");

        transferResultHandler().handle(message);

        assertEquals(JobEvent.State.FAILED, reportedEvents().getLast().getState());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE).isEmpty());
        assertEquals(2L, message.getHeader(PROVIDER_ID, Long.class), "the provider must not switch");
    }

    @Test
    void aRetriedJobDoesNotInheritTheEarlierJobsId() {
        transferConsumer().handle(request(2L).setHeader(CHOUETTE_JOB_ID, "9"));

        assertEquals("1", publisher.publishedTo(MardukQueues.CHOUETTE_POLL_STATUS_QUEUE).getFirst()
                .attributes().get(CHOUETTE_JOB_ID));
    }

    @Test
    void everyQueueIsSubscribedToUnderItsOwnName() {
        assertEquals(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE, netexExportConsumer(true).destination());
        assertEquals(MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE, blocksExportConsumer().destination());
        assertEquals(MardukQueues.CHOUETTE_TRANSFER_EXPORT_QUEUE, transferConsumer().destination());
    }

    // ------------------------------------------------------------------------------------ subjects

    private ChouetteNetexExportConsumer netexExportConsumer(boolean postValidation) {
        return new ChouetteNetexExportConsumer(client, providerRepository, new JobEventPublisher(publisher),
                new ChouetteJobSubmission(publisher), postValidation, List.of());
    }

    private ChouetteNetexExportResultHandler netexExportResultHandler(boolean postValidation) {
        return new ChouetteNetexExportResultHandler(export, antuValidation,
                new JobEventPublisher(publisher), publisher, postValidation);
    }

    private ChouetteNetexBlocksExportConsumer blocksExportConsumer() {
        return new ChouetteNetexBlocksExportConsumer(client, providerRepository,
                new JobEventPublisher(publisher), new ChouetteJobSubmission(publisher), true, List.of());
    }

    private ChouetteNetexBlocksExportResultHandler blocksExportResultHandler() {
        return new ChouetteNetexBlocksExportResultHandler(export, antuValidation,
                new JobEventPublisher(publisher), true);
    }

    private ChouetteTransferConsumer transferConsumer() {
        return new ChouetteTransferConsumer(client, providerRepository, new JobEventPublisher(publisher),
                new ChouetteJobSubmission(publisher));
    }

    private ChouetteTransferResultHandler transferResultHandler() {
        return new ChouetteTransferResultHandler(chouetteJobs, providerRepository,
                new JobEventPublisher(publisher), publisher);
    }
}
