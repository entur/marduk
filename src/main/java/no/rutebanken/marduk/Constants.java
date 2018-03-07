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

public class Constants {
    public static final String FILE_TYPE = "RutebankenFileType";
    public static final String FILE_HANDLE = "RutebankenFileHandle";
    public static final String FILE_PARENT_COLLECTION = "RutebankenFileParentCollection";
    public static final String PROVIDER_ID = "RutebankenProviderId";
    public static final String PROVIDER_IDS = "RutebankenProviderIds";
    public static final String ORIGINAL_PROVIDER_ID = "RutebankenOriginalProviderId"; // The original provider id that started this chain of events 
    // (providerId can change during the process when transferring data from one referential to another
    public static final String CORRELATION_ID = "RutebankenCorrelationId";
    public static final String CHOUETTE_REFERENTIAL = "RutebankenChouetteReferential";
    public static final String JSON_PART = "RutebankenJsonPart";
    public static final String OTP_GRAPH_DIR = "RutebankenOtpGraphDirectory";
    public static final String FILE_NAME = "RutebankenFileName";

    public static final String CURRENT_AGGREGATED_GTFS_FILENAME = "aggregated-gtfs.zip";
    public static final String CURRENT_AGGREGATED_NETEX_FILENAME = "aggregated-netex.zip";
    public static final String GRAPH_OBJ = "Graph.obj";
    public static final String BASE_GRAPH_OBJ = "baseGraph.obj";

    public static final String METADATA_DESCRIPTION = "MetadataDescription";
    public static final String METADATA_FILE = "MetadataFile";

    public static final String FILE_TARGET_MD5 = "RutebankenMd5SumRecordedForTargetFile";
    public static final String ENABLE_VALIDATION = "RutebankenEnableValidation";
    public static final String FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES = "RutebankenSkipStatusUpdateForDuplicateFiles";

    public static final String BLOBSTORE_PATH_INBOUND = "inbound/received/";
    public static final String BLOBSTORE_PATH_OUTBOUND = "outbound/";

    public static final String CHOUETTE_JOB_STATUS_URL = "RutebankenChouetteJobStatusURL";
    public static final String CHOUETTE_JOB_ID = "RutebankenChouetteJobId";
    public static final String CHOUETTE_JOB_STATUS_ROUTING_DESTINATION = "RutebankenChouetteJobStatusRoutingDestination";
    public static final String CHOUETTE_JOB_STATUS_JOB_TYPE = "RutebankenChouetteJobStatusType";
    public static final String CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL = "RutebankenChouetteJobStatusValidationLevel";

    public static final String JOB_ACTION = "RutebankenJobAction";

    public static final String BLOBSTORE_MAKE_BLOB_PUBLIC = "RutebankenBlobstoreMakeBlobPublic";

    public static final String SINGLETON_ROUTE_DEFINITION_GROUP_NAME = "RutebankenSingletonRouteDefinitionGroup";

    public static final String FOLDER_NAME = "RutebankenFolderName";
    public static final String SYSTEM_STATUS = "RutebankenSystemStatus";

    public static final String TIMESTAMP = "RutebankenTimeStamp";

    public static final String ETCD_KEY = "RutebankenEtcdKey";

    public static final String ET_CLIENT_NAME_HEADER = "ET-Client-Name";
    public static final String ET_CLIENT_ID_HEADER = "ET-Client-ID";

    public static final String PROVIDER_BLACK_LIST = "RutebankenProviderBlackList";
    public static final String PROVIDER_WHITE_LIST = "RutebankenProviderWhiteList";
    public static final String TRANSFORMATION_ROUTING_DESTINATION = "RutebankenTransformationRoutingDestination";

    public static final String OTP_BASE_GRAPH_BUILD = "RutebankenOtpBaseGraphBuild";
}

