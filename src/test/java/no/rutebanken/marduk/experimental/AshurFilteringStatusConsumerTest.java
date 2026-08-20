package no.rutebanken.marduk.experimental;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.repository.ProviderRepository;
import no.rutebanken.marduk.routes.experimental.AshurFilteringReportValidator;
import no.rutebanken.marduk.routes.experimental.ExperimentalImportHelpers;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import no.rutebanken.marduk.validation.NetexValidationProfiles;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILTERED_NETEX_FILE_PATH_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_ERROR_CODE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS;
import static no.rutebanken.marduk.Constants.FILTERING_PROFILE_STANDARD_IMPORT;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_FAILED;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_HEADER;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_STARTED;
import static no.rutebanken.marduk.Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_CLIENT_MARDUK;
import static no.rutebanken.marduk.Constants.VALIDATION_CORRELATION_ID_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_DATASET_FILE_HANDLE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_HEADER;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE;
import static no.rutebanken.marduk.Constants.VALIDATION_PROFILE_TIMETABLE_FINLAND;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_HEADER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AshurFilteringStatusConsumerTest {

    private static final String INTERNAL = "marduk-internal";
    private static final String ASHUR = "ashur-exchange";
    private static final String ANTU = "antu-exchange";
    private static final String FROM_ASHUR = "TST/corr/filtered_netex.zip";
    private static final String WITHOUT_BLOCKS = "filtered-netex/rb_tst/corr/rb_tst-aggregated-netex.zip";
    private static final String WITH_BLOCKS = "filtered-netex/rb_tst/blocks/corr/rb_tst-aggregated-netex.zip";

    private final Map<String, Map<String, byte[]>> buckets = new ConcurrentHashMap<>();
    private final RecordingPubSubPublisher publisher = new RecordingPubSubPublisher();

    private AshurFilteringStatusConsumer consumer() {
        return consumer(List.of());
    }

    private AshurFilteringStatusConsumer consumer(List<String> finnishCodespaces) {
        return new AshurFilteringStatusConsumer(
                new ExperimentalImportHelpers(true, mock(ProviderRepository.class)),
                new AshurFilteringReportValidator(),
                new NetexValidationProfiles(List.of(), finnishCodespaces),
                new MardukInternalBlobStoreService(INTERNAL, new InMemoryMardukBlobStoreRepository(buckets)),
                publisher,
                new JobEventPublisher(publisher),
                ASHUR,
                ANTU);
    }

    private MardukMessage status(String profile, String status, String report) {
        buckets.computeIfAbsent(ASHUR, key -> new ConcurrentHashMap<>())
                .put(FROM_ASHUR, "zip".getBytes(StandardCharsets.UTF_8));
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "rb_tst")
                .setHeader(CHOUETTE_REFERENTIAL, "rb_tst")
                .setHeader(FILTERING_PROFILE_HEADER, profile)
                .setHeader(FILTER_NETEX_FILE_STATUS_HEADER, status)
                .setHeader(FILTERED_NETEX_FILE_PATH_HEADER, FROM_ASHUR)
                .setBody(report);
    }

    private byte[] blob(String container, String name) {
        return buckets.getOrDefault(container, Map.of()).get(name);
    }

    private List<JobEvent> reported() {
        return publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).stream()
                .map(published -> JobEvent.fromString(published.body())).toList();
    }

    private Map<String, String> validationRequest() {
        return publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).getFirst().attributes();
    }

    @Test
    void aStartedStandardImportFilteringIsReportedAndNothingElseHappens() {
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_STARTED, null));

        assertEquals(1, reported().size());
        assertEquals(JobEvent.TimetableAction.FILTERING.name(), reported().getFirst().getAction());
        assertEquals(JobEvent.State.STARTED, reported().getFirst().getState());
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
    }

    @Test
    void aFilteredDatasetIsMovedIntoMardukAndHandedToAntuUnderTheSamePath() {
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_SUCCEEDED,
                report(FILTERING_PROFILE_STANDARD_IMPORT, 0)));

        assertNotNull(blob(INTERNAL, WITHOUT_BLOCKS), "the filtered dataset is kept in marduk's own bucket");
        // The path in the attribute is what antu opens, so it has to be the path in antu's bucket too.
        assertNotNull(blob(ANTU, WITHOUT_BLOCKS));
        assertEquals(WITHOUT_BLOCKS, validationRequest().get(VALIDATION_DATASET_FILE_HANDLE_HEADER));
    }

    @Test
    void aFilteredDatasetIsSentForOrdinaryNetexPostValidation() {
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_SUCCEEDED,
                report(FILTERING_PROFILE_STANDARD_IMPORT, 0)));

        Map<String, String> request = validationRequest();
        assertEquals(VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION, request.get(VALIDATION_STAGE_HEADER));
        assertEquals(VALIDATION_CLIENT_MARDUK, request.get(VALIDATION_CLIENT_HEADER));
        assertEquals("corr", request.get(VALIDATION_CORRELATION_ID_HEADER));
        assertEquals(VALIDATION_PROFILE_TIMETABLE, request.get(VALIDATION_PROFILE_HEADER));
    }

    @Test
    void theValidationProfileFollowsTheCodespace() {
        MardukMessage message = status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_SUCCEEDED,
                report(FILTERING_PROFILE_STANDARD_IMPORT, 0))
                .setHeader(DATASET_REFERENTIAL, "rb_oym");

        consumer(List.of("OYM")).handle(message);

        assertEquals(VALIDATION_PROFILE_TIMETABLE_FINLAND, validationRequest().get(VALIDATION_PROFILE_HEADER));
    }

    @Test
    void thePendingPostValidationIsReportedBeforeTheFilteringIsReportedComplete() {
        // The order is what nabu sees: the job that follows is pending before the one that produced it is
        // closed, which is how the two show up in sequence rather than overlapping.
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_SUCCEEDED,
                report(FILTERING_PROFILE_STANDARD_IMPORT, 0)));

        List<JobEvent> events = reported();
        assertEquals(2, events.size());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION.name(), events.get(0).getAction());
        assertEquals(JobEvent.State.PENDING, events.get(0).getState());
        assertEquals(JobEvent.TimetableAction.FILTERING.name(), events.get(1).getAction());
        assertEquals(JobEvent.State.OK, events.get(1).getState());
    }

    @Test
    void aReportNamingAnotherProfileThanTheOneRequestedStopsTheImport() {
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_SUCCEEDED,
                report(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS, 0)));

        assertEquals(1, reported().size());
        assertEquals(JobEvent.State.FAILED, reported().getFirst().getState());
        assertEquals(SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH, reported().getFirst().getErrorCode());
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
        assertNull(blob(INTERNAL, WITHOUT_BLOCKS), "a dataset that failed the profile check must not be kept");
    }

    @Test
    void aStandardImportReportContainingBlocksStopsTheImport() {
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_SUCCEEDED,
                report(FILTERING_PROFILE_STANDARD_IMPORT, 50)));

        assertEquals(SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH, reported().getFirst().getErrorCode());
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
    }

    @Test
    void aFailedStandardImportFilteringRelaysAshursErrorCode() {
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, FILTER_NETEX_FILE_STATUS_FAILED, null)
                .setHeader(FILTERING_ERROR_CODE_HEADER, "ASHUR_OOM"));

        assertEquals(JobEvent.TimetableAction.FILTERING.name(), reported().getFirst().getAction());
        assertEquals(JobEvent.State.FAILED, reported().getFirst().getState());
        assertEquals("ASHUR_OOM", reported().getFirst().getErrorCode());
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
    }

    @Test
    void aStartedBlocksFilteringIsReportedAgainstTheBlockExportJob() {
        consumer().handle(status(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS,
                FILTER_NETEX_FILE_STATUS_STARTED, null));

        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS.name(), reported().getFirst().getAction());
        assertEquals(JobEvent.State.STARTED, reported().getFirst().getState());
    }

    @Test
    void aFilteredBlocksDatasetIsSentForBlocksPostValidationUnderItsOwnPath() {
        consumer().handle(status(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS,
                FILTER_NETEX_FILE_STATUS_SUCCEEDED,
                report(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS, 50)));

        assertNotNull(blob(INTERNAL, WITH_BLOCKS));
        assertNotNull(blob(ANTU, WITH_BLOCKS));
        assertNull(blob(INTERNAL, WITHOUT_BLOCKS), "the with-blocks output must not overwrite the ordinary one");
        assertEquals(VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION,
                validationRequest().get(VALIDATION_STAGE_HEADER));

        List<JobEvent> events = reported();
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION.name(), events.get(0).getAction());
        assertEquals(JobEvent.State.PENDING, events.get(0).getState());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS.name(), events.get(1).getAction());
        assertEquals(JobEvent.State.OK, events.get(1).getState());
    }

    @Test
    void aBlocksReportNamingTheStandardProfileStopsTheBlockExport() {
        consumer().handle(status(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS,
                FILTER_NETEX_FILE_STATUS_SUCCEEDED, report(FILTERING_PROFILE_STANDARD_IMPORT, 0)));

        assertEquals(1, reported().size());
        assertEquals(JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS.name(), reported().getFirst().getAction());
        assertEquals(SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH, reported().getFirst().getErrorCode());
        assertTrue(publisher.publishedTo(MardukQueues.ANTU_NETEX_VALIDATION_QUEUE).isEmpty());
    }

    @Test
    void aFailedBlocksFilteringIsReportedWithoutAnErrorCode() {
        // Preserved from the routes: only the standard import path relays Ashur's error code.
        consumer().handle(status(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS,
                FILTER_NETEX_FILE_STATUS_FAILED, null).setHeader(FILTERING_ERROR_CODE_HEADER, "ASHUR_OOM"));

        assertEquals(JobEvent.State.FAILED, reported().getFirst().getState());
        assertNull(reported().getFirst().getErrorCode());
    }

    @Test
    void anUnknownFilteringStatusIsDiscardedRatherThanRetried() {
        consumer().handle(status(FILTERING_PROFILE_STANDARD_IMPORT, "NO_SUCH_STATUS", null));
        consumer().handle(status(FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS, "NO_SUCH_STATUS", null));

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void anUnknownFilteringProfileIsDiscardedRatherThanRetried() {
        consumer().handle(status("NoSuchFilter", FILTER_NETEX_FILE_STATUS_SUCCEEDED, null));

        assertTrue(publisher.published().isEmpty());
    }

    private static String report(String filterProfile, long blockCount) {
        return """
            {
              "created": "2026-03-27T12:00:00.000000",
              "correlationId": "corr",
              "codespace": "TST",
              "filterProfile": "%s",
              "status": "%s",
              "reason": null,
              "entityTypeCounts": {
                "ServiceJourney": 142,
                "Block": %d
              }
            }
            """.formatted(filterProfile, Constants.FILTER_NETEX_FILE_STATUS_SUCCEEDED, blockCount);
    }
}
