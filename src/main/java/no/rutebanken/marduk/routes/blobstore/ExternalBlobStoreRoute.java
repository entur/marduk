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
import no.rutebanken.marduk.services.ExchangeBlobStoreService;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;

@Component
public class ExternalBlobStoreRoute extends BaseRouteBuilder {

    final ExchangeBlobStoreService exchangeBlobStoreService;

    public ExternalBlobStoreRoute(ExchangeBlobStoreService exchangeBlobStoreService) {
        this.exchangeBlobStoreService = exchangeBlobStoreService;
    }

    @Override
    public void configure() {

        from("direct:uploadExternalBlob")
                .to(logDebugShowAll())
                .bean(exchangeBlobStoreService,"uploadBlob")
                .to(logDebugShowAll());

        from("direct:fetchExternalBlob")
                .to(logDebugShowAll())
                .bean(exchangeBlobStoreService,"getBlob")
                .to(logDebugShowAll());

        from("direct:deleteExternalBlob")
                .log(LoggingLevel.INFO, correlation() + "Deleting blob ${header." + FILE_HANDLE + "} from external blob store.")
                .to(logDebugShowAll())
                .bean(exchangeBlobStoreService,"deleteBlob")
                .to(logDebugShowAll());

        from("direct:copyExternalBlobInBucket")
                .to(logDebugShowAll())
                .bean(exchangeBlobStoreService, "copyBlobInBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} in blob store in Marduk exchange bucket.")
                .routeId("blobstore-external-copy-in-bucket");

        from("direct:copyExchangeBlobToAnotherBucket")
                .to(logDebugShowAll())
                .bean(exchangeBlobStoreService, "copyBlobToAnotherBucket")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied file ${header." + FILE_HANDLE + "} to file ${header." + TARGET_FILE_HANDLE + "} from Marduk Exchange bucket to bucket ${header." + TARGET_CONTAINER + "}.")
                .routeId("blobstore-exchange-copy-to-another-bucket");

        from("direct:copyExternalBlobToValidationBucket")
                .setHeader(TARGET_CONTAINER, simple("${properties:blobstore.gcs.antu.exchange.container.name}"))
                .setHeader(TARGET_FILE_HANDLE, header(FILE_HANDLE))
                .to("direct:copyExchangeBlobToAnotherBucket")
                .routeId("netex-flexible-copy-to-validation-folder");

    }
}

