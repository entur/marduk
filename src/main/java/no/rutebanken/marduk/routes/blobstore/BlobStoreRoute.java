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
public class BlobStoreRoute extends BaseRouteBuilder {

    @Autowired
    MardukBlobStoreService mardukBlobStoreService;

    @Override
    public void configure() {

        from("direct:uploadBlob")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukBlobStoreService, "uploadBlob")
                .setBody(simple(""))
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

        from("direct:copyBlobInBucket")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukBlobStoreService, "copyBlobInBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} in blob store in Marduk bucket.")
                .routeId("blobstore-copy-in-bucket");

        from("direct:copyBlobToAnotherBucket")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                        //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukBlobStoreService, "copyBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} in blob store from Marduk bucket to another bucket (${header." + TARGET_CONTAINER + "}).")
                .routeId("blobstore-copy-to-another-bucket");

        from("direct:copyVersionedBlobToAnotherBucket")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukBlobStoreService, "copyVersionedBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} (generation: ${header." + FILE_VERSION + "}) to file ${header." + TARGET_FILE_HANDLE + "} in blob store in Marduk bucket.")
                .routeId("blobstore-copy-versioned-blob-to-another-bucket");

        from("direct:copyAllBlobs")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                //defaulting to private access if not specified
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .end()
                .bean(mardukBlobStoreService, "copyAllBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from copying all files in folder ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-copy-all");

        from("direct:getBlob")
                .to(logDebugShowAll())
                .bean(mardukBlobStoreService, "getBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-download");

        from("direct:findBlob")
                .to(logDebugShowAll())
                .bean(mardukBlobStoreService, "findBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from looking up file with prefix ${header." + FILE_PREFIX + "} in the blob store.")
                .routeId("blobstore-find");

        from("direct:listBlobs")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean(mardukBlobStoreService, "listBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-list");

        from("direct:listBlobsFlat")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean(mardukBlobStoreService, "listBlobsFlat")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching flat file list from blob store.")
                .routeId("blobstore-list-flat");

        from("direct:listBlobsInFolders")
                .to(logDebugShowAll())
                .bean(mardukBlobStoreService, "listBlobsInFolders")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store for multiple folders.")
                .routeId("blobstore-list-in-folders");

        from("direct:deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .bean(mardukBlobStoreService, "deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from deleting all blobs in folder.")
                .routeId("blobstore-delete-in-folder");


    }
}
