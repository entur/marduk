package no.rutebanken.marduk;

import com.fasterxml.jackson.databind.JsonNode;
import no.rutebanken.marduk.gtfs.DamuGtfsExportStatusConsumer;
import no.rutebanken.marduk.gtfs.DamuGtfsMergeStatusConsumer;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins everything other systems match on by name or by value.
 *
 * <p>Marduk publishes <em>every</em> message header that does not start with {@code Camel} as a PubSub
 * message attribute, so the value of every string constant used as a header name is part of the wire
 * contract, not an internal detail. The same goes for the values other services filter or switch on,
 * the topic and subscription names, the blob path layout and the JSON that nabu parses.
 *
 * <p>The assertions compare hard-coded literals against values read <b>reflectively at runtime</b>.
 * Reading them reflectively matters twice over. It stops the test from pinning a name through the same
 * constant it is meant to pin - {@code assertEquals(PROVIDER_ID, attributes.get(PROVIDER_ID))} moves
 * with any rename and pins nothing. And because {@code static final String} values are inlined into
 * call sites, a compile-time reference would let an incremental build compare two copies of the same
 * stale literal; reflection reads the current class file instead.
 *
 * <p>Changing anything asserted here changes what other services see. That is sometimes the intent,
 * but it is never a refactor: update the expectation deliberately and roll out the readers first.
 */
class WireContractTest {

    /**
     * Every string constant in {@link Constants}, by declared name. Additions and removals fail too,
     * so a new header cannot reach the wire without being pinned on purpose.
     */
    private static final Map<String, String> PINNED_CONSTANTS = Map.ofEntries(
            entry("FILE_TYPE", "RutebankenFileType"),
            entry("FILE_HANDLE", "RutebankenFileHandle"),
            entry("FILE_VERSION", "RutebankenFileVersion"),
            entry("TARGET_FILE_HANDLE", "RutebankenTargetFileHandle"),
            entry("TARGET_CONTAINER", "RutebankenTargetContainer"),
            entry("SOURCE_CONTAINER", "RutebankenSourceContainer"),
            entry("PROVIDER_ID", "RutebankenProviderId"),
            entry("PROVIDER_IDS", "RutebankenProviderIds"),
            entry("ORIGINAL_PROVIDER_ID", "RutebankenOriginalProviderId"),
            entry("CORRELATION_ID", "RutebankenCorrelationId"),
            entry("CHOUETTE_REFERENTIAL", "RutebankenChouetteReferential"),
            entry("FILE_NAME", "RutebankenFileName"),
            entry("CURRENT_AGGREGATED_GTFS_FILENAME", "aggregated-gtfs.zip"),
            entry("CURRENT_AGGREGATED_NETEX_FILENAME", "aggregated-netex.zip"),
            entry("CURRENT_NETEX_WITH_BLOCKS_FILENAME", "netex-with-blocks.zip"),
            entry("CURRENT_FLEXIBLE_LINES_NETEX_FILENAME", "flexible-lines.zip"),
            entry("CURRENT_PREVALIDATED_NETEX_FILENAME", "netex.zip"),
            entry("PREVALIDATED_NETEX_METADATA_FILENAME", "netex.metadata.json"),
            entry("OTP2_GRAPH_OBJ_PREFIX", "Graph-otp2"),
            entry("OTP2_BASE_GRAPH_OBJ_PREFIX", "streetGraph-otp2"),
            entry("OTP2_NETEX_GRAPH_DIR", "netex-otp2"),
            entry("OTP2_STREET_GRAPH_DIR", "street"),
            entry("OTP2_GRAPH_REPORT_INDEX_FILE", "index_otp2.html"),
            entry("FILE_APPLY_DUPLICATES_FILTER", "RutebankenApplyDuplicateFilter"),
            entry("FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY", "RutebankenApplyDuplicateFilterOnNameOnly"),
            entry("FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES", "RutebankenSkipStatusUpdateForDuplicateFiles"),
            entry("BLOBSTORE_PATH_INBOUND", "inbound/received/"),
            entry("BLOBSTORE_PATH_OUTBOUND", "outbound/"),
            entry("BLOBSTORE_PATH_CHOUETTE", "chouette/"),
            entry("BLOBSTORE_PATH_NETEX_EXPORT_BEFORE_VALIDATION", "chouette/netex-before-validation/"),
            entry("BLOBSTORE_PATH_NETEX_EXPORT", "chouette/netex/"),
            entry("BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES", "last-prevalidated-files/"),
            entry("BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT", "chouette/netex-with-blocks/"),
            entry("BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT_BEFORE_VALIDATION", "chouette/netex-with-blocks-before-validation/"),
            entry("CHOUETTE_JOB_STATUS_URL", "RutebankenChouetteJobStatusURL"),
            entry("CHOUETTE_JOB_ID", "RutebankenChouetteJobId"),
            entry("CHOUETTE_JOB_STATUS_ROUTING_DESTINATION", "RutebankenChouetteJobStatusRoutingDestination"),
            entry("CHOUETTE_JOB_STATUS_JOB_TYPE", "RutebankenChouetteJobStatusType"),
            entry("CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL", "RutebankenChouetteJobStatusValidationLevel"),
            entry("ANTU_VALIDATION_REPORT_ID", "EnturValidationReportId"),
            entry("USERNAME", "RutebankenUsername"),
            entry("JOB_ERROR_CODE", "RutebankenJobErrorCode"),
            entry("FOLDER_NAME", "RutebankenFolderName"),
            entry("SYSTEM_STATUS", "RutebankenSystemStatus"),
            entry("TIMESTAMP", "RutebankenTimeStamp"),
            entry("ET_CLIENT_NAME_HEADER", "ET-Client-Name"),
            entry("DATASET_REFERENTIAL", "EnturDatasetReferential"),
            entry("GTFS_ROUTE_DISPATCHER_HEADER_NAME", "Action"),
            entry("GTFS_ROUTE_DISPATCHER_AGGREGATION_HEADER_VALUE", "Aggregation"),
            entry("GTFS_ROUTE_DISPATCHER_EXPORT_HEADER_VALUE", "Export"),
            entry("VALIDATION_DATASET_FILE_HANDLE_HEADER", "EnturValidationDatasetFileHandle"),
            entry("VALIDATION_CORRELATION_ID_HEADER", "EnturValidationCorrelationId"),
            entry("VALIDATION_STAGE_HEADER", "EnturValidationStage"),
            entry("VALIDATION_IMPORT_TYPE", "EnturValidationImportType"),
            entry("VALIDATION_STAGE_PREVALIDATION", "EnturValidationStagePreValidation"),
            entry("VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION", "EnturValidationStageExportNetexPostValidation"),
            entry("VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION", "EnturValidationStageExportNetexBlocksPostValidation"),
            entry("VALIDATION_STAGE_NIGHTLY_VALIDATION", "EnturValidationStageNightlyValidation"),
            entry("VALIDATION_STAGE_FLEX_POSTVALIDATION", "EnturValidationStageFlexPostValidation"),
            entry("VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION", "EnturValidationStageExportMergedPostValidation"),
            entry("VALIDATION_CLIENT_HEADER", "EnturValidationClient"),
            entry("VALIDATION_CLIENT_MARDUK", "Marduk"),
            entry("VALIDATION_PROFILE_HEADER", "EnturValidationProfile"),
            entry("VALIDATION_PROFILE_TIMETABLE", "Timetable"),
            entry("VALIDATION_PROFILE_TIMETABLE_FLEX", "TimetableFlexibleTransport"),
            entry("VALIDATION_PROFILE_IMPORT_TIMETABLE_FLEX", "ImportTimetableFlexibleTransport"),
            entry("VALIDATION_PROFILE_TIMETABLE_FLEX_MERGING", "TimetableFlexibleTransportMerging"),
            entry("VALIDATION_PROFILE_TIMETABLE_SWEDEN", "TimetableSweden"),
            entry("VALIDATION_PROFILE_TIMETABLE_FINLAND", "TimetableFinland"),
            entry("FILTERING_PROFILE_HEADER", "EnturFilteringProfile"),
            entry("FILTERING_PROFILE_AS_IS", "AsIsImportFilter"),
            entry("FILTERING_PROFILE_STANDARD_IMPORT", "StandardImportFilter"),
            entry("FILTERING_PROFILE_INCLUDE_BLOCKS_AND_RESTRICTED_JOURNEYS", "IncludeBlocksAndRestrictedJourneysFilter"),
            entry("FILTERING_FILE_CREATED_TIMESTAMP", "FileCreatedTimestamp"),
            entry("FILTERING_NETEX_SOURCE_HEADER", "NetexSource"),
            entry("FILTERING_NETEX_SOURCE_CHOUETTE", "chouette"),
            entry("FILTERING_NETEX_SOURCE_MARDUK", "marduk"),
            entry("FILTER_NETEX_FILE_STATUS_HEADER", "Status"),
            entry("FILTER_NETEX_FILE_STATUS_STARTED", "STARTED"),
            entry("FILTER_NETEX_FILE_STATUS_SUCCEEDED", "SUCCESS"),
            entry("FILTER_NETEX_FILE_STATUS_FAILED", "FAILED"),
            entry("FILTERED_NETEX_FILE_PATH_HEADER", "FilteredNetexFilePath"),
            entry("FILTERING_ERROR_CODE_HEADER", "FilteringErrorCode"),
            entry("LINKING_NETEX_FILE_STATUS_HEADER", "LinkingStatus"),
            entry("LINKING_NETEX_FILE_STATUS_STARTED", "STARTED"),
            entry("LINKING_NETEX_FILE_STATUS_SUCCEEDED", "SUCCESS"),
            entry("LINKING_NETEX_FILE_STATUS_FAILED", "FAILED"),
            entry("LINKED_NETEX_FILE_PATH_HEADER", "LinkedNetexFilePath"),
            entry("LINKING_ERROR_CODE_HEADER", "LinkingFailureReason"),
            entry("LINKING_STATUS_EVENT_TIME_HEADER", "LinkingStatusEventTime"),
            entry("SERVICE_LINK_MODES_HEADER", "ServiceLinkModes"),
            entry("IMPORT_TYPE", "ImportType"),
            entry("IMPORT_TYPE_NETEX_FLEX", "ImportType_netex_flex"),
            entry("IMPORT_TYPE_UTTU_EXPORT", "ImportType_uttu_export"),
            entry("SECURITY_ERROR_CODE_FILTERING_PROFILE_MISMATCH", "FILTERING_PROFILE_MISMATCH"));

    /** Every error code nabu displays, by declared name in {@link JobEvent}. */
    private static final Map<String, String> PINNED_ERROR_CODES = Map.ofEntries(
            entry("JOB_ERROR_FILE_UNKNOWN_FILE_EXTENSION", "ERROR_FILE_UNKNOWN_FILE_EXTENSION"),
            entry("JOB_ERROR_FILE_NOT_A_ZIP_FILE", "ERROR_FILE_NOT_A_ZIP_FILE"),
            entry("JOB_ERROR_DUPLICATE_FILE", "ERROR_FILE_DUPLICATE"),
            entry("JOB_ERROR_UNKNOWN_FILE_TYPE", "ERROR_FILE_UNKNOWN_FILE_TYPE"),
            entry("JOB_ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES", "ERROR_FILE_ZIP_CONTAINS_SUB_DIRECTORIES"),
            entry("JOB_ERROR_INVALID_ZIP_ENTRY_ENCODING", "ERROR_FILE_INVALID_ZIP_ENTRY_ENCODING"),
            entry("JOB_ERROR_INVALID_XML_ENCODING", "ERROR_FILE_INVALID_XML_ENCODING_ERROR"),
            entry("JOB_ERROR_INVALID_XML_CONTENT", "ERROR_FILE_INVALID_XML_CONTENT"),
            entry("JOB_ERROR_NETEX_EXPORT_EMPTY", "ERROR_NETEX_EXPORT_EMPTY_EXPORT"),
            entry("JOB_ERROR_VALIDATION_NO_DATA", "ERROR_VALIDATION_NO_DATA"),
            entry("JOB_ERROR_VALIDATION_INCOMPLETE", "ERROR_VALIDATION_INCOMPLETE"),
            // Read off Chouette's action report, not produced here.
            entry("CHOUETTE_JOB_FAILURE_CODE_NO_DATA_PROCEEDED", "NO_DATA_PROCEEDED"),
            entry("CHOUETTE_JOB_FAILURE_CODE_NO_DATA_FOUND", "NO_DATA_FOUND"));

    /**
     * Topics marduk publishes to and subscriptions it consumes, unqualified by project. Terraformed
     * outside this repo, so a rename here silently stops routing.
     */
    private static final Set<String> PINNED_PUBSUB_DESTINATIONS = Set.of(
            "AntuNetexValidationQueue",
            "AntuNetexValidationStatusQueue",
            "ChouetteExportNetexBlocksQueue",
            "ChouetteExportNetexQueue",
            "ChouetteImportQueue",
            "ChouetteMergeWithFlexibleLinesQueue",
            "ChouettePollStatusQueue",
            "ChouetteTransferExportQueue",
            "ChouetteValidationQueue",
            "DamuExportGtfsStatusQueue",
            "ExportNetexBlocksQueue",
            "FilterNetexFileQueue",
            "FilterNetexFileStatusQueue",
            "FlexibleLinesExportQueue",
            "GtfsExportMergedQueue",
            "GtfsRouteDispatcherTopic",
            "JobEventQueue",
            "LineStatisticsCalculationQueue",
            "MardukAggregateGtfsStatusQueue",
            "MardukDeadLetterQueue",
            "MardukInboundQueue",
            "NetexExportNotificationQueue",
            "Otp2BaseGraphBuildQueue",
            "Otp2BaseGraphCandidateBuildQueue",
            "Otp2GraphBuildQueue",
            "Otp2GraphCandidateBuildQueue",
            "ProcessFileQueue",
            "PublishMergedNetexQueue",
            "ServicelinkerInboundQueue",
            "ServicelinkerStatusQueue");

    /**
     * Values of the {@code RutebankenChouetteJobStatusRoutingDestination} attribute. Marduk sets this
     * on the message it puts on {@code ChouettePollStatusQueue} and re-publishes it unchanged on every
     * poll, up to 3000 times at 30s intervals - so a job started before a deploy is still being polled
     * long after, carrying the value the <em>old</em> version wrote. Whatever dispatches on this must
     * keep accepting these literals for as long as a poll can outlive a rollout.
     */
    private static final Set<String> PINNED_ROUTING_DESTINATIONS = Set.of(
            "direct:processImportResult",
            "direct:processValidationResult",
            "direct:processNetexExportResult",
            "direct:processNetexBlocksExportResult",
            "direct:processTransferExportResult");

    @Test
    void constantsMatchThePinnedWireContract() {
        assertEquals(
                new TreeMap<>(PINNED_CONSTANTS),
                new TreeMap<>(stringConstantsOf(Constants.class)),
                """
                        The string constants in Constants have drifted from the pinned wire contract.
                        Marduk publishes every non-Camel header as a PubSub message attribute, so a changed
                        value renames an attribute other services filter on, and a changed path moves a blob
                        another service reads. Update the expectation only together with those readers.""");
    }

    @Test
    void jobEventErrorCodesMatchThePinnedWireContract() {
        assertEquals(new TreeMap<>(PINNED_ERROR_CODES), new TreeMap<>(stringConstantsOf(JobEvent.class)),
                """
                        Every string constant in JobEvent is pinned, not only the ones whose name starts with
                        JOB_ERROR_ or CHOUETTE_JOB_: a code named anything else would otherwise reach nabu
                        unpinned. A constant here that is not a wire value belongs somewhere else.""");
    }

    @Test
    void jobEventStatesArePinned() {
        assertEquals(
                Set.of("PENDING", "STARTED", "TIMEOUT", "FAILED", "OK", "DUPLICATE", "CANCELLED"),
                namesOf(EnumSet.allOf(JobEvent.State.class)),
                "nabu switches on the state string; an unrecognised one is logged and discarded.");
    }

    @Test
    void jobEventDomainsArePinned() {
        assertEquals(
                Set.of("TIMETABLE", "GRAPH", "TIMETABLE_PUBLISH"),
                namesOf(EnumSet.allOf(JobEvent.JobDomain.class)));
    }

    @Test
    void jobEventTimetableActionsArePinned() {
        assertEquals(
                Set.of("FILE_TRANSFER", "FILE_CLASSIFICATION", "PREVALIDATION", "LINKING", "FILTERING",
                        "IMPORT", "EXPORT", "VALIDATION_LEVEL_1", "VALIDATION_LEVEL_2", "CLEAN",
                        "DATASPACE_TRANSFER", "OTP2_BUILD_GRAPH", "OTP2_BUILD_BASE", "EXPORT_NETEX",
                        "EXPORT_NETEX_POSTVALIDATION", "EXPORT_NETEX_MERGED",
                        "EXPORT_NETEX_MERGED_POSTVALIDATION", "EXPORT_NETEX_BLOCKS",
                        "EXPORT_NETEX_BLOCKS_POSTVALIDATION", "EXPORT_GTFS_MERGED",
                        "EXPORT_GTFS_BASIC_MERGED"),
                namesOf(EnumSet.allOf(JobEvent.TimetableAction.class)),
                """
                        The action reaches nabu as a string, and the same names are parsed back out of the
                        RutebankenChouetteJobStatusType attribute by TimetableAction.valueOf - so a rename
                        breaks in-flight poll messages as well as the status history.""");
    }

    @Test
    void fileTypesArePinned() {
        assertEquals(
                Set.of("NOT_A_ZIP_FILE", "INVALID_FILE_NAME", "GTFS", "ZIP_CONTAINS_SUBDIRECTORIES",
                        "NETEXPROFILE", "UNKNOWN_FILE_TYPE", "UNKNOWN_FILE_EXTENSION",
                        "INVALID_ZIP_FILE_ENTRY_NAME_ENCODING", "INVALID_ZIP_FILE_ENTRY_CONTENT_ENCODING",
                        "INVALID_ZIP_FILE_ENTRY_XML_CONTENT"),
                namesOf(EnumSet.allOf(FileType.class)),
                "The file type travels as the RutebankenFileType attribute.");
    }

    @Test
    void chouetteJobStatusesArePinned() {
        assertEquals(
                Set.of("SCHEDULED", "STARTED", "TERMINATED", "CANCELED", "ABORTED", "RESCHEDULED"),
                namesOf(EnumSet.allOf(Status.class)),
                "Deserialized from Chouette's job status JSON; note CANCELED has one L, as Chouette spells it.");
    }

    @Test
    void jobEventJsonIsPinned() throws IOException {
        String json = JobEvent.builder()
                .fileName("netex.zip")
                .correlationId("corr")
                .providerId(2L)
                .jobDomain(JobEvent.JobDomain.TIMETABLE)
                .jobId("job")
                .action(JobEvent.TimetableAction.IMPORT)
                .state(JobEvent.State.OK)
                .eventTime(Instant.ofEpochSecond(1_700_000_000L, 123_456_789))
                .referential("rb_tst")
                .username("user")
                .errorCode("code")
                .build()
                .toString();
        JsonNode parsed = ObjectMapperFactory.getSharedObjectMapper().readTree(json);

        List<String> fieldNames = new ArrayList<>();
        parsed.fieldNames().forEachRemaining(fieldNames::add);
        assertEquals(
                Set.of("name", "correlationId", "providerId", "domain", "externalId", "action", "state",
                        "eventTime", "referential", "username", "errorCode"),
                new TreeSet<>(fieldNames),
                "These are the property names nabu binds to.");

        assertEquals("IMPORT", parsed.get("action").asText());
        assertEquals("OK", parsed.get("state").asText());
        assertEquals("TIMETABLE", parsed.get("domain").asText());
        assertEquals("job", parsed.get("externalId").asText(), "jobId() is serialized as externalId");
        assertTrue(parsed.get("eventTime").isNumber(),
                """
                        eventTime is an epoch-seconds decimal, because the shared ObjectMapper registers
                        JavaTimeModule without disabling WRITE_DATES_AS_TIMESTAMPS. Disabling it would switch
                        the field to an ISO-8601 string for every reader.""");
        // Asserted against the serialized text, not the parsed node: reading the node back through a
        // double loses the nanos, so a parsed comparison would pass against a truncated wire value.
        assertTrue(json.contains("\"eventTime\":1700000000.123456789"),
                "eventTime is not serialized with full nanosecond precision: " + json);
    }

    @Test
    void damuExportStatusValuesArePinned() {
        // Damu reports its per-codespace GTFS export status in the message BODY, and its own WireContractTest
        // pins the sending side. These three strings are the whole contract; an unrecognised one is dropped,
        // leaving the job with no terminal state.
        assertEquals(
                Map.of(
                        "STATUS_EXPORT_STARTED", "started",
                        "STATUS_EXPORT_OK", "ok",
                        "STATUS_EXPORT_FAILED", "failed"),
                new TreeMap<>(stringConstantsOf(DamuGtfsExportStatusConsumer.class)),
                "Every string constant the class declares is pinned, so a fourth status cannot slip in.");
    }

    @Test
    void damuAggregationStatusValuesArePinned() {
        // The aggregated national merge reports through the "status" ATTRIBUTE rather than the body - a
        // different mechanism from the per-codespace export above, with the same three values.
        assertEquals(
                Map.of(
                        "STATUS_HEADER", "status",
                        "STATUS_MERGE_STARTED", "started",
                        "STATUS_MERGE_OK", "ok",
                        "STATUS_MERGE_FAILED", "failed"),
                new TreeMap<>(stringConstantsOf(DamuGtfsMergeStatusConsumer.class)),
                """
                        These are damu's values; changing one here stops marduk recognising the merge status.
                        Every string constant the class declares is pinned, not just the STATUS_ ones.""");
    }

    @Test
    void mardukQueuesDeclaresExactlyThePinnedDestinations() {
        Map<String, String> declared = stringConstantsOf(MardukQueues.class);
        assertEquals(
                PINNED_PUBSUB_DESTINATIONS,
                new TreeSet<>(declared.values()),
                """
                        MardukQueues has drifted from the pinned destination set. These names are terraformed
                        outside this repository, so a rename here stops routing rather than failing loudly.""");
        // The comparison above is over values, so two constants holding the same name would collapse into
        // one entry and pass. The count is what makes an addition visible.
        assertEquals(PINNED_PUBSUB_DESTINATIONS.size(), declared.size(),
                "MardukQueues declares two constants for the same destination: " + declared);
    }

    /**
     * Every destination is pinned, however it is spelled.
     *
     * <p>Replaces a scan for {@code google-pubsub:} endpoint URIs, which passed vacuously the moment the
     * last Camel route went. A destination reaches PubSub as a literal string somewhere in the sources, so
     * that is what is read here - a queue introduced without going through {@link MardukQueues} is caught
     * as well as one added to it.
     */
    @Test
    void noUnpinnedPubSubDestinationIsNamedInTheSources() {
        Matcher matcher = Pattern.compile("\"([A-Z][A-Za-z0-9]*(?:Queue|Topic))\"").matcher(mainSources());
        Set<String> used = new TreeSet<>();
        while (matcher.find()) {
            used.add(matcher.group(1));
        }
        used.removeAll(PINNED_PUBSUB_DESTINATIONS);
        assertEquals(Set.of(), used,
                """
                        A queue or topic name is used in the sources without being pinned above. Add it to
                        PINNED_PUBSUB_DESTINATIONS together with the terraform that creates it.""");
    }

    @Test
    void everyPinnedPubSubDestinationIsStillReferenced() {
        String sources = mainSources();
        assertEquals(Set.of(), PINNED_PUBSUB_DESTINATIONS.stream().filter(name -> !sources.contains(name))
                        .collect(Collectors.toCollection(TreeSet::new)),
                "Pinned PubSub destinations no longer named anywhere in the sources. Renaming one stops routing.");
    }

    @Test
    void everyPinnedRoutingDestinationIsStillAccepted() {
        String sources = mainSources();
        assertEquals(Set.of(), PINNED_ROUTING_DESTINATIONS.stream().filter(value -> !sources.contains(value))
                        .collect(Collectors.toCollection(TreeSet::new)),
                """
                        A RutebankenChouetteJobStatusRoutingDestination value is no longer named in the
                        sources. Chouette polls outlive a rollout, so dropping one strands every job whose
                        poll message was written by the previous version.""");
    }

    private static Map<String, String> stringConstantsOf(Class<?> type) {
        Map<String, String> values = new LinkedHashMap<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class) {
                field.setAccessible(true);
                try {
                    values.put(field.getName(), (String) field.get(null));
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Cannot read " + type.getName() + "." + field.getName(), e);
                }
            }
        }
        return values;
    }

    private static <E extends Enum<E>> Set<String> namesOf(EnumSet<E> constants) {
        return constants.stream().map(Enum::name).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String mainSources() {
        try (Stream<Path> paths = Files.walk(Path.of("src", "main", "java"))) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(WireContractTest::read)
                    .collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
