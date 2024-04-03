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
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.FILE_PREFIX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

@Component
public class BlobStoreRoute extends BaseRouteBuilder {

    private final MardukPublicBlobStoreService mardukPublicBlobStoreService;

    public BlobStoreRoute(MardukPublicBlobStoreService mardukPublicBlobStoreService) {
        this.mardukPublicBlobStoreService = mardukPublicBlobStoreService;
    }

    @Override
    public void configure() {

        from("direct:uploadBlob")
                .to(logDebugShowAll())
                .bean(mardukPublicBlobStoreService, "uploadBlob")
                .setBody(simple(""))
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-upload");

        from("direct:copyBlobInBucket")
                .to(logDebugShowAll())
                .bean(mardukPublicBlobStoreService, "copyBlobInBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} in blob store in Marduk bucket.")
                .routeId("blobstore-copy-in-bucket");

        from("direct:copyBlobToAnotherBucket")
                .to(logDebugShowAll())
                .bean(mardukPublicBlobStoreService, "copyBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} from Marduk bucket to bucket ${header." + TARGET_CONTAINER + "}.")
                .routeId("blobstore-copy-to-another-bucket");

        from("direct:getBlob")
                .to(logDebugShowAll())
                .bean(mardukPublicBlobStoreService, "getBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-download");

        from("direct:findBlob")
                .to(logDebugShowAll())
                .bean(mardukPublicBlobStoreService, "findBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from looking up file with prefix ${header." + FILE_PREFIX + "} in the blob store.")
                .routeId("blobstore-find");

        from("direct:listBlobs")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().getReferential()))
                .bean(mardukPublicBlobStoreService, "listBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store.")
                .routeId("blobstore-list");

        from("direct:listBlobsFlat")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().getReferential()))
                .bean(mardukPublicBlobStoreService, "listBlobsFlat")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching flat file list from blob store.")
                .routeId("blobstore-list-flat");

        from("direct:listBlobsInFolders")
                .to(logDebugShowAll())
                .bean(mardukPublicBlobStoreService, "listBlobsInFolders")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store for multiple folders.")
                .routeId("blobstore-list-in-folders");

        from("direct:deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .bean(mardukPublicBlobStoreService, "deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from deleting all blobs in folder.")
                .routeId("blobstore-delete-in-folder");


    }
}
