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
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

@Component
public class OtpGraphsBlobStoreRoute extends BaseRouteBuilder {

    final OtpGraphsBlobStoreService otpGraphsBlobStoreService;

    public OtpGraphsBlobStoreRoute(OtpGraphsBlobStoreService otpGraphsBlobStoreService) {
        this.otpGraphsBlobStoreService = otpGraphsBlobStoreService;
    }

    @Override
    public void configure() {

        from("direct:uploadOtpGraphsBlob")
                .to(logDebugShowAll())
                .bean(otpGraphsBlobStoreService, "uploadBlob")
                .to(logDebugShowAll())
                .routeId("blobstore-otp-graph-upload");

        from("direct:listOtpGraphBlobsInFolders")
                .to(logDebugShowAll())
                .bean(otpGraphsBlobStoreService, "listBlobsInFolders")
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Returning from fetching file list from blob store for multiple folders.")
                .routeId("blobstore-otp-graph-list-in-folders");
    }
}
