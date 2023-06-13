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

package no.rutebanken.marduk.routes.blobstore;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.services.MardukBlobStoreService;
import no.rutebanken.marduk.services.MardukExternalBlobStoreService;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_PREFIX;
import static no.rutebanken.marduk.Constants.FILE_VERSION;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

@Component
public class MardukExternalBlobStoreRoute extends BaseRouteBuilder {

    @Autowired
    MardukExternalBlobStoreService mardukExternalBlobStoreService;

    @Override
    public void configure() {

        from("direct:uploadMardukExternalBlob")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukExternalBlobStoreService, "uploadBlob")
                .setBody(simple(""))
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-marduk-external-upload");

        from("direct:copyMardukExternalBlobInBucket")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukExternalBlobStoreService, "copyBlobInBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} in blob store in Marduk bucket.")
                .routeId("blobstore-copy-marduk-external-in-bucket");

        from("direct:copyBlobMardukExternalToAnotherBucket")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                        //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukExternalBlobStoreService, "copyBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} from Marduk bucket to bucket ${header." + TARGET_CONTAINER + "}.")
                .routeId("blobstore-copy-marduk-external-to-another-bucket");

        from("direct:copyMardukExternalVersionedBlobToAnotherBucket")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukExternalBlobStoreService, "copyVersionedBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} (generation: ${header." + FILE_VERSION + "}) to file ${header." + TARGET_FILE_HANDLE + "} from Marduk bucket to bucket ${header." + TARGET_CONTAINER + "}.")
                .routeId("blobstore-copy-marduk-external-versioned-blob-to-another-bucket");

        from("direct:copyMardukExternalAllBlobs")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukExternalBlobStoreService, "copyAllBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from copying all files in folder ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-marduk-external-copy-all");

        from("direct:getMardukExternalBlob")
                .to(logDebugShowAll())
                .bean(mardukExternalBlobStoreService, "getBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-marduk-external-download");

        from("direct:findMardukExternalBlob")
                .to(logDebugShowAll())
                .bean(mardukExternalBlobStoreService, "findBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from looking up file with prefix ${header." + FILE_PREFIX + "} in the blob store.")
                .routeId("blobstore-marduk-external-find");

        from("direct:listMardukExternalBlobs")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean(mardukExternalBlobStoreService, "listBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-marduk-external-list");

        from("direct:listMardukExternalBlobsFlat")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean(mardukExternalBlobStoreService, "listBlobsFlat")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching flat file list from blob store.")
                .routeId("blobstore-marduk-external-list-flat");

        from("direct:listMardukExternalBlobsInFolders")
                .to(logDebugShowAll())
                .bean(mardukExternalBlobStoreService, "listBlobsInFolders")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store for multiple folders.")
                .routeId("blobstore-marduk-external-list-in-folders");

        from("direct:deleteMardukExternalAllBlobsInFolder")
                .to(logDebugShowAll())
                .bean(mardukExternalBlobStoreService, "deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from deleting all blobs in folder.")
                .routeId("blobstore-marduk-external-delete-in-folder");


    }
}
