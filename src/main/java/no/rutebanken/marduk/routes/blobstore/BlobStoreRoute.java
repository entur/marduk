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
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    @Override
    public void configure() {

        from("direct:uploadBlob")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "uploadBlob")
                .setBody(simple(""))
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

        from("direct:copyBlob")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "copyBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from copying file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-copy");

        from("direct:copyAllBlobs")
                .to(logDebugShowAll())
                .choice()
                .when(header(BLOBSTORE_MAKE_BLOB_PUBLIC).isNull())
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))     //defaulting to false if not specified
                .end()
                .bean("blobStoreService", "copyAllBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from copying all files in folder ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-copy-all");

        from("direct:getBlob")
                .to(logDebugShowAll())
                .bean("blobStoreService", "getBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-download");

        from("direct:listBlobs")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean("blobStoreService", "listBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-list");

        from("direct:listBlobsFlat")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .bean("blobStoreService", "listBlobsFlat")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-list-flat");

        from("direct:listBlobsInFolders")
                .to(logDebugShowAll())
                .bean("blobStoreService", "listBlobsInFolders")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store for multiple folders.")
                .routeId("blobstore-list-in-folders");

        from("direct:deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .bean("blobStoreService", "deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from deleting blobs in folder.")
                .routeId("blobstore-delete-in-folder");


    }
}
