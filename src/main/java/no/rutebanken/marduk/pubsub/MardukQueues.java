package no.rutebanken.marduk.pubsub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Every PubSub destination marduk touches, and which GCP project it lives in.
 *
 * <p>The names are terraformed outside this repository and matched by name by other services, so they are
 * wire contract; {@code WireContractTest} pins them. Camel spelled the project into each endpoint URI
 * ({@code google-pubsub:{{antu.pubsub.project.id}}:AntuNetexValidationQueue}), which is the only reason it
 * worked across five projects. That information has to live somewhere once the URIs are gone.
 *
 * <p>Four destinations are outside marduk's own project:
 *
 * <ul>
 *   <li>{@code AntuNetexValidationQueue} is antu's topic, and {@code JobEventQueue} is nabu's.
 *   <li>{@code FilterNetexFileStatusQueue} and {@code ServicelinkerStatusQueue} are topics in
 *       <em>marduk's</em> project, but the subscriptions marduk reads are cross-project ones created by
 *       ashur's and servicelinker's own terraform. So the topic is local and the subscription is remote,
 *       which is why they are named by the other project here.
 * </ul>
 *
 * <p>A destination in marduk's own project is left as a bare name, and only a foreign one is qualified to
 * {@code projects/<project>/...}. That matters for more than tidiness: in tests and locally all five
 * project ids are the same value, so everything stays bare, and {@code EnturGooglePubSubAdmin} - which
 * passes the destination name to both {@code createTopic} and {@code createSubscription} - keeps working.
 */
@Component
public class MardukQueues {

    // Inbound work, marduk's own project.
    public static final String MARDUK_INBOUND_QUEUE = "MardukInboundQueue";
    public static final String PROCESS_FILE_QUEUE = "ProcessFileQueue";
    public static final String MARDUK_DEAD_LETTER_QUEUE = "MardukDeadLetterQueue";

    // Chouette.
    public static final String CHOUETTE_IMPORT_QUEUE = "ChouetteImportQueue";
    public static final String CHOUETTE_POLL_STATUS_QUEUE = "ChouettePollStatusQueue";
    public static final String CHOUETTE_VALIDATION_QUEUE = "ChouetteValidationQueue";
    public static final String CHOUETTE_EXPORT_NETEX_QUEUE = "ChouetteExportNetexQueue";
    public static final String CHOUETTE_EXPORT_NETEX_BLOCKS_QUEUE = "ChouetteExportNetexBlocksQueue";
    public static final String CHOUETTE_TRANSFER_EXPORT_QUEUE = "ChouetteTransferExportQueue";
    public static final String CHOUETTE_MERGE_WITH_FLEXIBLE_LINES_QUEUE = "ChouetteMergeWithFlexibleLinesQueue";

    // NeTEx export and publication.
    public static final String PUBLISH_MERGED_NETEX_QUEUE = "PublishMergedNetexQueue";
    public static final String NETEX_EXPORT_NOTIFICATION_QUEUE = "NetexExportNotificationQueue";
    public static final String FLEXIBLE_LINES_EXPORT_QUEUE = "FlexibleLinesExportQueue";
    public static final String EXPORT_NETEX_BLOCKS_QUEUE = "ExportNetexBlocksQueue";
    public static final String LINE_STATISTICS_CALCULATION_QUEUE = "LineStatisticsCalculationQueue";

    // GTFS, shared with damu.
    public static final String GTFS_ROUTE_DISPATCHER_TOPIC = "GtfsRouteDispatcherTopic";
    public static final String GTFS_EXPORT_MERGED_QUEUE = "GtfsExportMergedQueue";
    public static final String DAMU_EXPORT_GTFS_STATUS_QUEUE = "DamuExportGtfsStatusQueue";
    public static final String MARDUK_AGGREGATE_GTFS_STATUS_QUEUE = "MardukAggregateGtfsStatusQueue";

    // OTP2 graph builds.
    public static final String OTP2_GRAPH_BUILD_QUEUE = "Otp2GraphBuildQueue";
    public static final String OTP2_GRAPH_CANDIDATE_BUILD_QUEUE = "Otp2GraphCandidateBuildQueue";
    public static final String OTP2_BASE_GRAPH_BUILD_QUEUE = "Otp2BaseGraphBuildQueue";
    public static final String OTP2_BASE_GRAPH_CANDIDATE_BUILD_QUEUE = "Otp2BaseGraphCandidateBuildQueue";

    // Validation status from antu, in marduk's project.
    public static final String ANTU_NETEX_VALIDATION_STATUS_QUEUE = "AntuNetexValidationStatusQueue";

    // Ashur filtering and servicelinker enrichment. The status subscriptions are cross-project.
    public static final String FILTER_NETEX_FILE_QUEUE = "FilterNetexFileQueue";
    public static final String FILTER_NETEX_FILE_STATUS_QUEUE = "FilterNetexFileStatusQueue";
    public static final String SERVICELINKER_INBOUND_QUEUE = "ServicelinkerInboundQueue";
    public static final String SERVICELINKER_STATUS_QUEUE = "ServicelinkerStatusQueue";

    // Foreign projects.
    public static final String ANTU_NETEX_VALIDATION_QUEUE = "AntuNetexValidationQueue";
    public static final String JOB_EVENT_QUEUE = "JobEventQueue";

    private final String mardukProject;
    private final Map<String, String> foreignProjects;

    public MardukQueues(
            @Value("${marduk.pubsub.project.id}") String mardukProject,
            @Value("${antu.pubsub.project.id}") String antuProject,
            @Value("${nabu.pubsub.project.id}") String nabuProject,
            @Value("${ashur.pubsub.project.id}") String ashurProject,
            @Value("${servicelinker.pubsub.project.id}") String servicelinkerProject) {
        this.mardukProject = mardukProject;
        this.foreignProjects = Map.of(
                ANTU_NETEX_VALIDATION_QUEUE, antuProject,
                JOB_EVENT_QUEUE, nabuProject,
                FILTER_NETEX_FILE_STATUS_QUEUE, ashurProject,
                SERVICELINKER_STATUS_QUEUE, servicelinkerProject);
    }

    /** The name to publish a message to, qualified only when the topic is in another project. */
    public String topic(String destination) {
        String project = projectOf(destination);
        return isLocal(project) ? destination : "projects/" + project + "/topics/" + destination;
    }

    /** The name to subscribe to, qualified only when the subscription is in another project. */
    public String subscription(String destination) {
        String project = projectOf(destination);
        return isLocal(project) ? destination : "projects/" + project + "/subscriptions/" + destination;
    }

    /** Destinations marduk publishes to but never consumes, so nothing else creates them locally. */
    public Set<String> publishOnlyDestinations() {
        return Set.of(
                MARDUK_DEAD_LETTER_QUEUE,
                NETEX_EXPORT_NOTIFICATION_QUEUE,
                LINE_STATISTICS_CALCULATION_QUEUE,
                GTFS_ROUTE_DISPATCHER_TOPIC,
                FILTER_NETEX_FILE_QUEUE,
                SERVICELINKER_INBOUND_QUEUE,
                ANTU_NETEX_VALIDATION_QUEUE,
                JOB_EVENT_QUEUE);
    }

    private String projectOf(String destination) {
        return foreignProjects.getOrDefault(destination, mardukProject);
    }

    private boolean isLocal(String project) {
        return project.equals(mardukProject);
    }
}
