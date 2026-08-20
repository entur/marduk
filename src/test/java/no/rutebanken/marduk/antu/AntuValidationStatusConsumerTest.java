package no.rutebanken.marduk.antu;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.experimental.ExperimentalImportPath;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.FileNameAndDigestIdempotentRepository;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.ValidationStages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.ANTU_VALIDATION_REPORT_ID;
import static no.rutebanken.marduk.Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_NAME;
import static no.rutebanken.marduk.Constants.FILE_TYPE;
import static no.rutebanken.marduk.Constants.FILTERING_FILE_CREATED_TIMESTAMP;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_FLEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_NIGHTLY_VALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_PREVALIDATION;
import static no.rutebanken.marduk.antu.AntuValidationStatusConsumer.STATUS_VALIDATION_FAILED;
import static no.rutebanken.marduk.antu.AntuValidationStatusConsumer.STATUS_VALIDATION_OK;
import static no.rutebanken.marduk.antu.AntuValidationStatusConsumer.STATUS_VALIDATION_STARTED;
import static no.rutebanken.marduk.antu.AntuValidationStatusConsumer.STATUS_VALIDATION_TIMEOUT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AntuValidationStatusConsumerTest {

    private static final String INTERNAL = "marduk-internal";
    private static final String EXCHANGE = "marduk-exchange";
    private static final String PUBLIC = "marduk";
    private static final String NISABA = "nisaba-exchange";

    private static final String DATASET = "inbound/received/tst/netex.zip";
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 3, 27, 12, 0, 0);

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();
    private final ProviderRepository providerRepository = mock(ProviderRepository.class);
    private final FileNameAndDigestIdempotentRepository idempotentRepository =
            mock(FileNameAndDigestIdempotentRepository.class);

    private boolean experimentalImport;
    private boolean blocksExport = true;
    private boolean enablePreValidation;
    private boolean enablePostValidation;

    @BeforeEach
    void setUp() {
        when(idempotentRepository.getCreatedAt("netex.zip")).thenReturn(RECEIVED_AT);
    }

    private AntuValidationStatusConsumer consumer() {
        Provider provider = provider(2L, "tst");
        Provider rbProvider = provider(1002L, "rb_tst");
        when(providerRepository.getProviders()).thenReturn(List.of(provider, rbProvider));
        when(providerRepository.getProvider(2L)).thenReturn(provider);
        when(providerRepository.getProvider(1002L)).thenReturn(rbProvider);
        when(providerRepository.getProviderId("tst")).thenReturn(2L);
        when(providerRepository.getProviderId("rb_tst")).thenReturn(1002L);

        ExperimentalImportHelpers helpers = new ExperimentalImportHelpers(true, providerRepository);
        JobEventPublisher jobEvents = new JobEventPublisher(publisher);
        // Servicelinker linking is left off so the experimental branches run straight through to Ashur.
        ExperimentalImportPath path = new ExperimentalImportPath(
                helpers, internalBlobStore(), publisher, jobEvents, false, EXCHANGE);
        return new AntuValidationStatusConsumer(
                providerRepository,
                helpers,
                path,
                new PrevalidatedDataset(internalBlobStore(), idempotentRepository, NISABA),
                internalBlobStore(),
                new ExchangeBlobStoreService(EXCHANGE, new InMemoryMardukBlobStoreRepository(buckets)),
                publisher,
                jobEvents,
                enablePreValidation,
                enablePostValidation,
                EXCHANGE,
                PUBLIC);
    }

    private MardukInternalBlobStoreService internalBlobStore() {
        return new MardukInternalBlobStoreService(INTERNAL, new InMemoryMardukBlobStoreRepository(buckets));
    }

    private Provider provider(long id, String referential) {
        Provider provider = new Provider();
        provider.setId(id);
        ChouetteInfo info = new ChouetteInfo();
        info.setId(id);
        info.setReferential(referential);
        info.setEnableExperimentalImport(experimentalImport && "tst".equals(referential));
        info.setEnableBlocksExport(blocksExport);
        provider.setChouetteInfo(info);
        return provider;
    }

    private void store(String container, String name, String content) {
        buckets.computeIfAbsent(container, key -> new ConcurrentHashMap<>())
                .put(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private MardukMessage status(String body, String stage, String referential) {
        store(INTERNAL, DATASET, "zip");
        return new MardukMessage()
                .setHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER, DATASET)
                .setHeader(VALIDATION_CORRELATION_ID_HEADER, "corr")
                .setHeader(VALIDATION_STAGE_HEADER, stage)
                .setHeader(DATASET_REFERENTIAL, referential)
                .setBody(body);
    }

    private MardukMessage status(String body, String stage) {
        return status(body, stage, "tst");
    }

    private byte[] blob(String container, String name) {
        return buckets.getOrDefault(container, Map.of()).get(name);
    }

    private List<JobEvent> reported() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(published -> JobEvent.fromString(published.body())).toList();
    }

    private JobEvent onlyReported() {
        assertEquals(1, reported().size(), "expected exactly one job event but got " + reported());
        return reported().getFirst();
    }

    // ------------------------------------------------------------------ dispatch

    @Test
    void aStatusWithoutTheDatasetFileHandleIsNackedRatherThanDropped() {
        MardukMessage message = status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION)
                .removeHeader(VALIDATION_DATASET_FILE_HANDLE_HEADER);

        assertThrows(MardukException.class, () -> consumer().handle(message));
        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void aStatusWithoutTheCorrelationIdIsNackedRatherThanDropped() {
        MardukMessage message = status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION)
                .removeHeader(VALIDATION_CORRELATION_ID_HEADER);

        assertThrows(MardukException.class, () -> consumer().handle(message));
    }

    @Test
    void theJobIsResolvedFromTheValidationRequestAntuEchoesBack() {
        MardukMessage message = status(STATUS_VALIDATION_STARTED, VALIDATION_STAGE_PREVALIDATION);

        consumer().handle(message);

        assertEquals("corr", message.getHeader(CORRELATION_ID, String.class));
        assertEquals(DATASET, message.getHeader(FILE_HANDLE, String.class));
        assertEquals(FileType.NETEXPROFILE.name(), message.getHeader(FILE_TYPE, String.class));
        assertEquals("tst", message.getHeader(CHOUETTE_REFERENTIAL, String.class));
        assertEquals(2L, message.getHeader(PROVIDER_ID, Long.class));
        assertEquals("netex.zip", message.getHeader(FILE_NAME, String.class));
    }

    @Test
    void anUnknownStatusIsDiscardedRatherThanRetried() {
        // Nacking a status no version of marduk understands would redeliver it for ever.
        consumer().handle(status("no-such-status", VALIDATION_STAGE_PREVALIDATION));

        assertTrue(publisher.published().isEmpty());
    }

    // ------------------------------------------------------------ stage mapping

    @Test
    void everyValidationStageMapsToTheJobItReportsAgainst() {
        assertEquals(JobEvent.TimetableAction.PREVALIDATION,
                ValidationStages.actionFor(VALIDATION_STAGE_PREVALIDATION));
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION,
                ValidationStages.actionFor(VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION));
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION,
                ValidationStages.actionFor(VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION));
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION,
                ValidationStages.actionFor(VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION));
        // A FLEX post-validation is reported as the ordinary NeTEx post-validation, and a nightly
        // re-validation as a pre-validation, which is what each is from the operator's point of view.
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION,
                ValidationStages.actionFor(VALIDATION_STAGE_FLEX_POSTVALIDATION));
        assertEquals(JobEvent.TimetableAction.PREVALIDATION,
                ValidationStages.actionFor(VALIDATION_STAGE_NIGHTLY_VALIDATION));
        assertNull(ValidationStages.actionFor("EnturValidationStageSomethingNew"));
        assertNull(ValidationStages.actionFor(null));
    }

    // ------------------------------------------------------------------- started

    @Test
    void aStartedValidationIsReportedAgainstTheStagesJobWithoutAReportToLinkTo() {
        MardukMessage message = status(STATUS_VALIDATION_STARTED, VALIDATION_STAGE_FLEX_POSTVALIDATION)
                .setHeader(ANTU_VALIDATION_REPORT_ID, "report-1");

        consumer().handle(message);

        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.STARTED, onlyReported().getState());
        assertNull(onlyReported().getExternalId(), "a validation that has only started has no report yet");
    }

    @Test
    void aStartedValidationForAnUnknownStageIsDiscarded() {
        consumer().handle(status(STATUS_VALIDATION_STARTED, "EnturValidationStageSomethingNew"));

        assertTrue(publisher.published().isEmpty());
    }

    // -------------------------------------------------------------------- failed

    @Test
    void aFailedValidationIsReportedAgainstTheStagesJobAndStopsTheFlow() {
        consumer().handle(status(STATUS_VALIDATION_FAILED, VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION));

        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.FAILED, onlyReported().getState());
        assertTrue(publisher.publishedTo(MardukQueues.PUBLISH_MERGED_NETEX_QUEUE).isEmpty());
    }

    @Test
    void aValidationAntuCouldNotFinishIsReportedAsTimedOutAndNotAsInvalidData() {
        consumer().handle(status(STATUS_VALIDATION_TIMEOUT, VALIDATION_STAGE_PREVALIDATION));

        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.TIMEOUT, onlyReported().getState());
        assertEquals(JobEvent.JOB_ERROR_VALIDATION_INCOMPLETE, onlyReported().getErrorCode());
    }

    @Test
    void aFailedValidationForAnUnknownStageIsDiscarded() {
        consumer().handle(status(STATUS_VALIDATION_FAILED, "EnturValidationStageSomethingNew"));
        consumer().handle(status(STATUS_VALIDATION_TIMEOUT, "EnturValidationStageSomethingNew"));

        assertTrue(publisher.published().isEmpty());
    }

    // ------------------------------------------------- complete: pre-validation

    @Test
    void aPrevalidatedDatasetIsKeptWithMetadataForTheNightlyRevalidation() {
        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION));

        assertNotNull(blob(INTERNAL, "last-prevalidated-files/tst/netex.metadata.json"));
        assertNotNull(blob(INTERNAL, "last-prevalidated-files/tst-netex.zip"));
        assertTrue(new String(blob(INTERNAL, "last-prevalidated-files/tst/netex.metadata.json"), StandardCharsets.UTF_8)
                .contains("netex.zip"));
    }

    @Test
    void aPrevalidatedDatasetTriggersTheChouetteImportWhenChouetteDoesNotPrevalidateItself() {
        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION));

        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).size());
        assertEquals("", publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).getFirst().body(),
                "the import trigger carried the empty body the metadata upload left behind");
        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.OK, onlyReported().getState());
    }

    @Test
    void aPrevalidatedDatasetIsNotImportedTwiceWhenChouettePrevalidatesDuringTheImport() {
        // chouette.enablePreValidation means the import itself validates, so the import is triggered
        // elsewhere. The Camel route reported nothing usable here either.
        enablePreValidation = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION));

        assertTrue(publisher.published().isEmpty());
        assertNotNull(blob(INTERNAL, "last-prevalidated-files/tst-netex.zip"), "the file is still kept");
    }

    @Test
    void anExperimentalCodespaceArchivesTheOriginalDatasetForNisaba() {
        experimentalImport = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION));

        assertNotNull(blob(NISABA, "imported/tst/tst_2026-03-27T12_00_00.zip"),
                "Nisaba reads the archive by name, so the timestamp has to be the one the file arrived with");
    }

    @Test
    void anExperimentalCodespaceReportsThePrevalidationBeforeTheReferentialsArePrefixed() {
        // The links to antu's pre-validation reports are built from the unprefixed referential, so the
        // report has to be out before the enrichment and filtering step renames them.
        experimentalImport = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION));

        List<JobEvent> events = reported();
        assertEquals(2, events.size());
        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), events.get(0).getAction());
        assertEquals("tst", events.get(0).getReferential());
        assertEquals(JobEvent.TimetableAction.FILTERING.name(), events.get(1).getAction());
        assertEquals("rb_tst", events.get(1).getReferential());
    }

    @Test
    void anExperimentalCodespaceGoesOnToFilteringInsteadOfTheChouetteImport() {
        experimentalImport = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_PREVALIDATION));

        assertEquals(1, publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).size());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_IMPORT_QUEUE).isEmpty());
    }

    // ------------------------------------ complete: NeTEx export post-validation

    @Test
    void anExperimentalNetexPostValidationPublishesTheAshurOutputForMergingAndAsTheLatest() {
        experimentalImport = true;
        store(INTERNAL, "filtered-netex/rb_tst/corr/rb_tst-aggregated-netex.zip", "filtered");

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION, "rb_tst"));

        assertNotNull(blob(INTERNAL, "filtered-netex/rb_tst/netex-before-merging/corr/rb_tst-aggregated-netex.zip"));
        // The stable path is what a later cross-flow merge reads when its own correlation id finds nothing.
        assertNotNull(blob(INTERNAL, "filtered-netex/rb_tst/latest-without-blocks/rb_tst-aggregated-netex.zip"));
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).size());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.OK, onlyReported().getState());
    }

    @Test
    void anExperimentalNetexPostValidationStartsTheBlockExportWhenTheProviderWantsBlocks() {
        experimentalImport = true;
        store(INTERNAL, "filtered-netex/rb_tst/corr/rb_tst-aggregated-netex.zip", "filtered");

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION, "rb_tst"));

        assertEquals(1, publisher.publishedTo(MardukQueues.EXPORT_NETEX_BLOCKS_QUEUE).size());
    }

    @Test
    void anExperimentalNetexPostValidationSkipsTheBlockExportWhenTheProviderDoesNot() {
        experimentalImport = true;
        blocksExport = false;
        store(INTERNAL, "filtered-netex/rb_tst/corr/rb_tst-aggregated-netex.zip", "filtered");

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION, "rb_tst"));

        assertTrue(publisher.publishedTo(MardukQueues.EXPORT_NETEX_BLOCKS_QUEUE).isEmpty());
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).size());
    }

    @Test
    void anOrdinaryNetexPostValidationPublishesTheChouetteExportForMergingAndForBlocks() {
        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION, "rb_tst"));

        assertNotNull(blob(INTERNAL, "chouette/netex/rb_tst-aggregated-netex.zip"));
        assertNotNull(blob(INTERNAL, "filtered-netex/rb_tst/latest-without-blocks/rb_tst-aggregated-netex.zip"));
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).size());
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE).size());
    }

    @Test
    void anOrdinaryNetexPostValidationOnlyReportsWhenChouetteOwnsThePostValidation() {
        enablePostValidation = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION, "rb_tst"));

        assertEquals(JobEvent.State.OK, onlyReported().getState());
        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).isEmpty());
    }

    // ----------------------------------- complete: NeTEx blocks post-validation

    @Test
    void aValidatedBlocksExportIsPublishedAsTheCurrentBlocksDataset() {
        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION, "rb_tst"));

        assertNotNull(blob(INTERNAL, "chouette/netex-with-blocks/rb_tst-aggregated-netex.zip"));
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.OK, onlyReported().getState());
    }

    @Test
    void aValidatedBlocksExportIsOnlyReportedWhenChouetteOwnsThePostValidation() {
        enablePostValidation = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION, "rb_tst"));

        assertEquals(JobEvent.State.OK, onlyReported().getState());
        assertNull(blob(INTERNAL, "chouette/netex-with-blocks/rb_tst-aggregated-netex.zip"));
    }

    // ------------------------------------------- complete: FLEX post-validation

    @Test
    void aFlexPostValidationIsReportedAgainstTheUnprefixedReferential() {
        // The rb_ prefix goes on for the outbound path and the merge trigger, but nabu keeps the job under
        // the referential the FLEX import ran as.
        MardukMessage message = status(STATUS_VALIDATION_OK, VALIDATION_STAGE_FLEX_POSTVALIDATION)
                .setHeader(VALIDATION_IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX);

        consumer().handle(message);

        assertEquals("tst", onlyReported().getReferential());
    }

    @Test
    void aFlexDatasetUploadedFromTheOperatorPortalIsCopiedIntoTheExchangeBucket() {
        MardukMessage message = status(STATUS_VALIDATION_OK, VALIDATION_STAGE_FLEX_POSTVALIDATION)
                .setHeader(VALIDATION_IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX);

        consumer().handle(message);

        assertEquals("rb_tst", message.getHeader(CHOUETTE_REFERENTIAL, String.class));
        assertNotNull(blob(EXCHANGE, "outbound/netex/rb_tst-flexible-lines.zip"));
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).size());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name(), onlyReported().getAction());
    }

    @Test
    void aFlexDatasetFromUttuOnlyMovesWithinTheExchangeBucket() {
        store(EXCHANGE, DATASET, "flex");

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_FLEX_POSTVALIDATION, "rb_tst"));

        assertEquals("flex", new String(blob(EXCHANGE, "outbound/netex/rb_tst-flexible-lines.zip"),
                StandardCharsets.UTF_8));
        assertEquals(1, publisher.publishedTo(MardukQueues.CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE).size());
    }

    // ----------------------------------------- complete: merged post-validation

    @Test
    void aValidatedMergedDatasetIsCopiedToThePublicBucketAndQueuedForPublication() {
        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION, "rb_tst"));

        assertNotNull(blob(PUBLIC, "outbound/netex/rb_tst-aggregated-netex.zip"));
        assertEquals(1, publisher.publishedTo(MardukQueues.PUBLISH_MERGED_NETEX_QUEUE).size());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.OK, onlyReported().getState());
    }

    @Test
    void aMergedPostValidationIsNotReportedWhenTheDownstreamPublishFails() {
        // Reporting OK first would tell nabu the merged export was published, while the redelivered message
        // still has the copy and the publish to do.
        publisher = new FailingPublisher(MardukQueues.PUBLISH_MERGED_NETEX_QUEUE);

        assertThrows(MardukException.class,
                () -> consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION, "rb_tst")));

        assertTrue(reported().isEmpty(), "the job was reported complete before the work was done");
    }

    @Test
    void aMergedFlexImportGetsTheRbPrefixOnTheChouetteReferential() {
        MardukMessage message = status(STATUS_VALIDATION_OK, VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION)
                .setHeader(VALIDATION_IMPORT_TYPE, IMPORT_TYPE_NETEX_FLEX);

        consumer().handle(message);

        assertEquals("rb_tst", message.getHeader(CHOUETTE_REFERENTIAL, String.class));
        assertNotNull(blob(PUBLIC, "outbound/netex/rb_tst-aggregated-netex.zip"));
        assertEquals("tst", onlyReported().getReferential(), "the prefix is for the path, not for nabu");
    }

    // --------------------------------------- complete: nightly re-validation

    @Test
    void aNightlyRevalidationFallsBackToAChouetteLevelOneValidation() {
        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_NIGHTLY_VALIDATION));

        Map<String, String> attributes =
                publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE).getFirst().attributes();
        assertEquals(JobEvent.TimetableAction.VALIDATION_LEVEL_1.name(),
                attributes.get(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL));
        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), onlyReported().getAction());
        assertEquals(JobEvent.State.OK, onlyReported().getState());
    }

    @Test
    void aNightlyRevalidationOnAnExperimentalCodespaceReimportsThroughAshur() {
        experimentalImport = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_NIGHTLY_VALIDATION)
                .setHeader(FILTERING_FILE_CREATED_TIMESTAMP, "2026-03-27T12:00:00"));

        assertTrue(publisher.publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE).isEmpty());
        assertEquals(1, publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).size());
        assertEquals(JobEvent.TimetableAction.PREVALIDATION.name(), reported().get(0).getAction());
        assertEquals(JobEvent.TimetableAction.FILTERING.name(), reported().get(1).getAction());
    }

    @Test
    void aNightlyRevalidationWithNoCreatedTimestampNeverReachesAshur() {
        // The nightly sweep stamps the timestamp when it asks antu to validate; without it Ashur could not
        // name its output consistently, so the filtering is cancelled.
        experimentalImport = true;

        consumer().handle(status(STATUS_VALIDATION_OK, VALIDATION_STAGE_NIGHTLY_VALIDATION));

        assertTrue(publisher.publishedTo(MardukQueues.FILTER_NETEX_FILE_QUEUE).isEmpty());
    }

    @Test
    void aCompleteValidationForAnUnknownStageIsDiscarded() {
        consumer().handle(status(STATUS_VALIDATION_OK, "EnturValidationStageSomethingNew"));

        assertTrue(publisher.published().isEmpty());
    }

    /** Fails one destination, so a test can check what has already been reported when the work throws. */
    private static class FailingPublisher extends RecordingPubSubPublisher {

        private final String failing;

        FailingPublisher(String failing) {
            this.failing = failing;
        }

        @Override
        public synchronized void publish(String destination, MardukMessage message) {
            if (failing.equals(destination)) {
                throw new MardukException("PubSub is down");
            }
            super.publish(destination, message);
        }
    }
}
