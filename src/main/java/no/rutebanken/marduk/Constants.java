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
    public static final String FILE_NAME="RutebankenFileName";

    public static final String CURRENT_AGGREGATED_GTFS_FILENAME = "aggregated-gtfs.zip";
    public static final String GRAPH_OBJ = "Graph.obj";

    public static final String METADATA_DESCRIPTION = "MetadataDescription";
    public static final String METADATA_FILE = "MetadataFile";

    public static final String FILE_TARGET_MD5 = "RutebankenMd5SumRecordedForTargetFile";
	public static final String CLEAN_REPOSITORY = "RutebankenCleanRepository";
    public static final String ENABLE_VALIDATION = "RutebankenEnableValidation";


	public static final String BLOBSTORE_PATH_INBOUND_RECEIVED = "inbound/received/";
	
    public static final String CHOUETTE_JOB_STATUS_URL = "RutebankenChouetteJobStatusURL";
    public static final String CHOUETTE_JOB_ID = "RutebankenChouetteJobId";
	public static final String CHOUETTE_JOB_STATUS_ROUTING_DESTINATION = "RutebankenChouetteJobStatusRoutingDestination";
	public static final String CHOUETTE_JOB_STATUS_JOB_TYPE = "RutebankenCHouetteJobStatusType";



}
