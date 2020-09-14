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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OtpGraphsBlobStoreRoute extends BaseRouteBuilder {

    @Autowired
    OtpGraphsBlobStoreService otpGraphsBlobStoreService;

    @Override
    public void configure() {

        from("direct:uploadOtpGraphsBlob")
                .to(logDebugShowAll())
                .bean(otpGraphsBlobStoreService, "uploadBlob")
                .to(logDebugShowAll());
    }
}
