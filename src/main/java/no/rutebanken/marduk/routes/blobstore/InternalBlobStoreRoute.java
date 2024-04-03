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
import no.rutebanken.marduk.services.MardukInternalBlobStoreService;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;

@Component
public class InternalBlobStoreRoute extends BaseRouteBuilder {

    @Autowired
    MardukInternalBlobStoreService mardukInternalBlobStoreService;

    @Override
    public void configure() {

        from("direct:uploadInternalBlob")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "uploadBlob")
                .setBody(simple(""))
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Stored file ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-internal-upload");

        from("direct:copyInternalBlobInBucket")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "copyBlobInBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} in blob store in Marduk internal bucket.")
                .routeId("blobstore-internal-copy-in-bucket");

        from("direct:copyInternalBlobToAnotherBucket")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "copyBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} from Marduk internal bucket to bucket ${header." + TARGET_CONTAINER + "}.")
                .routeId("blobstore-internal-copy-to-another-bucket");

        from("direct:copyVersionedInternalBlobToAnotherBucket")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "copyVersionedBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} (generation: ${header." + FILE_VERSION + "}) to file ${header." + TARGET_FILE_HANDLE + "} from Marduk internal bucket to bucket ${header." + TARGET_CONTAINER + "}.")
                .routeId("blobstore-internal-copy-versioned-blob-to-another-bucket");

        from("direct:copyAllInternalBlobs")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "copyAllBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from copying all files in folder ${header." + FILE_HANDLE + "} in blob store.")
                .routeId("blobstore-internal-copy-all");

        from("direct:getInternalBlob")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "getBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file ${header." + FILE_HANDLE + "} from blob store.")
                .routeId("blobstore-internal-download");

        from("direct:findInternalBlob")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "findBlob")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from looking up file with prefix ${header." + FILE_PREFIX + "} in the blob store.")
                .routeId("blobstore-internal-find");

        from("direct:listInternalBlobs")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().getReferential()))
                .bean(mardukInternalBlobStoreService, "listBlobs")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from internal blob store.")
                .routeId("blobstore-internal-list");

        from("direct:listInternalBlobsFlat")
                .to(logDebugShowAll())
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).getChouetteInfo().getReferential()))
                .bean(mardukInternalBlobStoreService, "listBlobsFlat")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching flat file list from internal blob store.")
                .routeId("blobstore-internal-list-flat");

        from("direct:listInternalBlobsInFolders")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "listBlobsInFolders")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from internal blob store for multiple folders.")
                .routeId("blobstore-internal-list-in-folders");

        from("direct:deleteAllInternalBlobsInFolder")
                .to(logDebugShowAll())
                .bean(mardukInternalBlobStoreService, "deleteAllBlobsInFolder")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from deleting all blobs in folder.")
                .routeId("blobstore-internal-delete-in-folder");

        from("direct:copyInternalBlobToValidationBucket")

                .setHeader(TARGET_CONTAINER, simple("${properties:blobstore.gcs.antu.exchange.container.name}"))
                .setHeader(TARGET_FILE_HANDLE, header(FILE_HANDLE))
                .to("direct:copyInternalBlobToAnotherBucket")
                .routeId("copy-internal-to-validation-folder");


    }
}
