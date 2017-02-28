package no.rutebanken.marduk;

public class Constants {
    public static final String FILE_TYPE = "RutebankenFileType";
    public static final String FILE_HANDLE = "RutebankenFileHandle";
    public static final String PROVIDER_ID = "RutebankenProviderId";
    public static final String ORIGINAL_PROVIDER_ID = "RutebankenOriginalProviderId"; // The original provider id that started this chain of events 
    // (providerId can change during the process when transferring data from one referential to another
    public static final String CORRELATION_ID = "RutebankenCorrelationId";
    public static final String CHOUETTE_REFERENTIAL = "RutebankenChouetteReferential";
    public static final String JSON_PART = "RutebankenJsonPart";
    public static final String OTP_GRAPH_DIR = "RutebankenOtpGraphDirectory";
    public static final String FILE_NAME = "RutebankenFileName";

    public static final String CURRENT_AGGREGATED_GTFS_FILENAME = "aggregated-gtfs.zip";
    public static final String GRAPH_OBJ = "Graph.obj";

    public static final String METADATA_DESCRIPTION = "MetadataDescription";
    public static final String METADATA_FILE = "MetadataFile";

    public static final String FILE_TARGET_MD5 = "RutebankenMd5SumRecordedForTargetFile";
    public static final String CLEAN_REPOSITORY = "RutebankenCleanRepository";
    public static final String ENABLE_VALIDATION = "RutebankenEnableValidation";
    public static final String FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES = "RutebankenSkipStatusUpdateForDuplicateFiles";

    public static final String BLOBSTORE_PATH_INBOUND = "inbound/received/";
    public static final String BLOBSTORE_PATH_OUTBOUND = "outbound/";

    public static final String CHOUETTE_JOB_STATUS_URL = "RutebankenChouetteJobStatusURL";
    public static final String CHOUETTE_JOB_ID = "RutebankenChouetteJobId";
    public static final String CHOUETTE_JOB_STATUS_ROUTING_DESTINATION = "RutebankenChouetteJobStatusRoutingDestination";
    public static final String CHOUETTE_JOB_STATUS_JOB_TYPE = "RutebankenChouetteJobStatusType";
    public static final String CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL = "RutebankenChouetteJobStatusValidationLevel";

    public static final String JOB_STATUS_URL = "RutebankenJobStatusURL";
    public static final String JOB_ID = "RutebankenJobId";
    public static final String JOB_STATUS_ROUTING_DESTINATION = "RutebankenobStatusRoutingDestination";

    public static final String KARTVERKET_DATASETID = "RutebankenKartverketDataSetId";
    public static final String KARTVERKET_FORMAT = "RutebankenKartverketFormat";

    public static final String BLOBSTORE_MAKE_BLOB_PUBLIC = "RutebankenBlobstoreMakeBlobPublic";

    public static final String SINGLETON_ROUTE_DEFINITION_GROUP_NAME = "RutebankenSingletonRouteDefinitionGroup";

    public static final String CONTENT_CHANGED = "RutebankenContentChanged";
    public static final String FOLDER_NAME = "RutebankenFolderName";
    public static final String SYSTEM_STATUS_CORRELATION_ID = "RutebankenSystemStatusCorrelationId";
    public static final String SYSTEM_STATUS_ACTION = "RutebankenSystemStatusAction";
    public static final String SYSTEM_STATUS_ENTITY = "RutebankenSystemStatusEntity";

    public static final String TIMESTAMP = "RutebankenTimeStamp";
    public static final String LOOP_COUNTER = "RutebankenLoopCounter";
}

