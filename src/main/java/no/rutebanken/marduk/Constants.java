/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk;

public final class Constants {
    public static final String FILE_TYPE = "RutebankenFileType";
    public static final String FILE_HANDLE = "RutebankenFileHandle";
    public static final String FILE_VERSION = "RutebankenFileVersion";
    public static final String FILE_PREFIX = "RutebankenFilePrefix";
    public static final String TARGET_FILE_HANDLE = "RutebankenTargetFileHandle";
    public static final String TARGET_FILE_PARENT = "RutebankenTargetFileParent";
    public static final String TARGET_CONTAINER = "RutebankenTargetContainer";

    public static final String SOURCE_CONTAINER = "RutebankenSourceContainer";

    public static final String FILE_PARENT_COLLECTION = "RutebankenFileParentCollection";
    public static final String PROVIDER_ID = "RutebankenProviderId";
    public static final String PROVIDER_IDS = "RutebankenProviderIds";
    public static final String ORIGINAL_PROVIDER_ID = "RutebankenOriginalProviderId"; // The original provider id that started this chain of events 
    // (providerId can change during the process when transferring data from one referential to another
    public static final String CORRELATION_ID = "RutebankenCorrelationId";
    public static final String CHOUETTE_REFERENTIAL = "RutebankenChouetteReferential";
    public static final String JSON_PART = "RutebankenJsonPart";
    public static final String FILE_NAME = "RutebankenFileName";

    public static final String CURRENT_AGGREGATED_GTFS_FILENAME = "aggregated-gtfs.zip";
    public static final String CURRENT_AGGREGATED_NETEX_FILENAME = "aggregated-netex.zip";
    public static final String CURRENT_FLEXIBLE_LINES_NETEX_FILENAME = "flexible-lines.zip";
    public static final String CURRENT_PREVALIDATED_NETEX_FILENAME = "netex.zip";
    public static final String PREVALIDATED_NETEX_METADATA_FILENAME = "netex.metadata.json";

    public static final String GRAPH_COMPATIBILITY_VERSION = "RutebankenGraphCompatibilityVersion";

    public static final String OTP2_GRAPH_OBJ = "Graph-otp2.obj";
    public static final String OTP2_GRAPH_OBJ_PREFIX = "Graph-otp2";
    public static final String OTP2_BASE_GRAPH_OBJ_PREFIX = "streetGraph-otp2";
    public static final String OTP2_NETEX_GRAPH_DIR = "netex-otp2";

    public static final String OTP2_STREET_GRAPH_DIR = "street";
    public static final String OTP2_GRAPH_REPORT_INDEX_FILE = "index_otp2.html";

    public static final String FILE_TARGET_MD5 = "RutebankenMd5SumRecordedForTargetFile";

    public static final String FILE_APPLY_DUPLICATES_FILTER = "RutebankenApplyDuplicateFilter";
    public static final String FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY = "RutebankenApplyDuplicateFilterOnNameOnly";
    public static final String FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES = "RutebankenSkipStatusUpdateForDuplicateFiles";

    public static final String BLOBSTORE_PATH_INBOUND = "inbound/received/";
    public static final String BLOBSTORE_PATH_OUTBOUND = "outbound/";

    public static final String BLOBSTORE_PATH_CHOUETTE = "chouette/";

    public static final String BLOBSTORE_PATH_NETEX_EXPORT_BEFORE_VALIDATION = BLOBSTORE_PATH_CHOUETTE + "netex-before-validation/";
    public static final String BLOBSTORE_PATH_NETEX_EXPORT = BLOBSTORE_PATH_CHOUETTE + "netex/";

    public static final String BLOBSTORE_PATH_LAST_SUCCESSFULLY_PREVALIDATED_FILES = "last-prevalidated-files/";

    public static final String BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT = BLOBSTORE_PATH_CHOUETTE + "netex-with-blocks/";
    public static final String BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT_BEFORE_VALIDATION = BLOBSTORE_PATH_CHOUETTE + "netex-with-blocks-before-validation/";


    public static final String CHOUETTE_JOB_STATUS_URL = "RutebankenChouetteJobStatusURL";
    public static final String CHOUETTE_JOB_ID = "RutebankenChouetteJobId";
    public static final String CHOUETTE_JOB_STATUS_ROUTING_DESTINATION = "RutebankenChouetteJobStatusRoutingDestination";
    public static final String CHOUETTE_JOB_STATUS_JOB_TYPE = "RutebankenChouetteJobStatusType";
    public static final String CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL = "RutebankenChouetteJobStatusValidationLevel";

    public static final String ANTU_VALIDATION_REPORT_ID = "EnturValidationReportId";

    public static final String USERNAME = "RutebankenUsername";
    public static final String JOB_ACTION = "RutebankenJobAction";
    public static final String JOB_ERROR_CODE = "RutebankenJobErrorCode";

    public static final String FOLDER_NAME = "RutebankenFolderName";
    public static final String SYSTEM_STATUS = "RutebankenSystemStatus";

    public static final String TIMESTAMP = "RutebankenTimeStamp";

    public static final String ET_CLIENT_NAME_HEADER = "ET-Client-Name";

    public static final String OTP_REMOTE_WORK_DIR = "RutebankenOtpRemoteWorkDir";
    public static final String OTP_GRAPH_VERSION = "RutebankenGraphVersion";
    public static final String OTP_BUILD_CANDIDATE = "RutebankenOtpBuildCandidate";


    public static final String INCLUDE_SHAPES = "IncludeShapes";

    public static final String CAMEL_ALL_HEADERS = "Camel*";
    public static final String CAMEL_ALL_HTTP_HEADERS = "CamelHttp*";

    /**
     * Dataset codespace used for interacting with Damu and Antu
     */
    public static final String DATASET_REFERENTIAL = "EnturDatasetReferential";

    public static final String GTFS_ROUTE_DISPATCHER_HEADER_NAME = "Action";
    public static final String GTFS_ROUTE_DISPATCHER_AGGREGATION_HEADER_VALUE = "Aggregation";
    public static final String GTFS_ROUTE_DISPATCHER_EXPORT_HEADER_VALUE = "Export";

    public static final String VALIDATION_DATASET_FILE_HANDLE_HEADER = "EnturValidationDatasetFileHandle";
    public static final String VALIDATION_CORRELATION_ID_HEADER  = "EnturValidationCorrelationId";

    public static final String VALIDATION_STAGE_HEADER = "EnturValidationStage";
    public static final String VALIDATION_IMPORT_TYPE = "EnturValidationImportType";
    public static final String VALIDATION_STAGE_PREVALIDATION = "EnturValidationStagePreValidation";
    public static final String VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION = "EnturValidationStageExportNetexPostValidation";
    public static final String VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION = "EnturValidationStageExportNetexBlocksPostValidation";
    public static final String VALIDATION_STAGE_NIGHTLY_VALIDATION = "EnturValidationStageNightlyValidation";

    public static final String VALIDATION_STAGE_FLEX_POSTVALIDATION = "EnturValidationStageFlexPostValidation";

    public static final String VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION = "EnturValidationStageExportMergedPostValidation";

    public static final String VALIDATION_CLIENT_HEADER = "EnturValidationClient";
    public static final String VALIDATION_CLIENT_MARDUK = "Marduk";

    public static final String VALIDATION_PROFILE_HEADER = "EnturValidationProfile";
    public static final String VALIDATION_PROFILE_TIMETABLE = "Timetable";
    public static final String VALIDATION_PROFILE_TIMETABLE_FLEX = "TimetableFlexibleTransport";
    public static final String VALIDATION_PROFILE_IMPORT_TIMETABLE_FLEX = "ImportTimetableFlexibleTransport";

    public static final String VALIDATION_PROFILE_TIMETABLE_FLEX_MERGING = "TimetableFlexibleTransportMerging";
    public static final String VALIDATION_PROFILE_TIMETABLE_SWEDEN = "TimetableSweden";
    public static final String VALIDATION_PROFILE_TIMETABLE_FINLAND = "TimetableFinland";

    public static final String FILTERING_PROFILE_HEADER = "EnturFilteringProfile";
    public static final String FILTERING_PROFILE_AS_IS = "AsIsImportFilter";
    public static final String FILTERING_PROFILE_STANDARD_IMPORT = "StandardImportFilter";
    public static final String FILTERING_FILE_CREATED_TIMESTAMP = "FileCreatedTimestamp";

    public static final String FILTERING_NETEX_SOURCE_HEADER = "NetexSource";
    public static final String FILTERING_NETEX_SOURCE_CHOUETTE = "chouette";
    public static final String FILTERING_NETEX_SOURCE_MARDUK = "marduk";

    public static final String FILTER_NETEX_FILE_SUBSCRIPTION = "FilterNetexFileQueue";
    public static final String FILTER_NETEX_FILE_STATUS_TOPIC = "FilterNetexFileStatusQueue";

    public static final String FILTER_NETEX_FILE_STATUS_HEADER = "Status";

    public static final String FILTER_NETEX_FILE_STATUS_STARTED = "STARTED";
    public static final String FILTER_NETEX_FILE_STATUS_SUCCEEDED = "SUCCESS";
    public static final String FILTER_NETEX_FILE_STATUS_FAILED = "FAILED";

    public static final String FILTERED_NETEX_FILE_PATH_HEADER = "FilteredNetexFilePath";
    public static final String FILTERING_ERROR_CODE_HEADER = "FilteringErrorCode";

    public static final String SERVICELINKER_STATUS_TOPIC = "ServicelinkerStatusQueue";

    public static final String LINKING_NETEX_FILE_STATUS_HEADER = "LinkingStatus";
    public static final String LINKING_NETEX_FILE_STATUS_SUCCEEDED = "SUCCESS";
    public static final String LINKING_NETEX_FILE_STATUS_FAILED = "FAILED";

    public static final String LINKED_NETEX_FILE_PATH_HEADER = "LinkedNetexFilePath";
    public static final String LINKING_ERROR_CODE_HEADER = "LinkingFailureReason";
    public static final String SERVICE_LINK_MODES_HEADER = "ServiceLinkModes";

    public static final String IMPORT_TYPE = "ImportType";

    public static final String IMPORT_TYPE_NETEX_FLEX = "ImportType_netex_flex";

    public static final String IMPORT_TYPE_UTTU_EXPORT = "ImportType_uttu_export";

    private Constants() {
    }
}

