/*
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
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

package no.rutebanken.marduk.routes.flexlines;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class NetexFlexibleLinesExportRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:postValidation")
    protected MockEndpoint postValidation;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @BeforeEach
    protected void setUp() throws IOException {
        super.setUp();
        postValidation.reset();
        updateStatus.reset();
    }

    private void adviseRoute() throws Exception {
        AdviceWith.adviceWith(context, "netex-flexible-lines-export-queue", a -> {
            a.replaceFromWith("direct:flexibleLinesExportNotification");
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            a.weaveByToUri("direct:antuFlexibleNetexPostValidation").replace().to("mock:postValidation");
        });
        context.start();
    }

    @Test
    void knownReferentialIsForwardedToPostValidation() throws Exception {
        Provider provider = new Provider();
        provider.setId(42L);
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setReferential("rb_mal");
        provider.setChouetteInfo(chouetteInfo);
        when(providerRepository.getProviderId("rb_mal")).thenReturn(42L);
        when(providerRepository.getProvider(42L)).thenReturn(provider);

        adviseRoute();
        postValidation.expectedMessageCount(1);

        context.createProducerTemplate().sendBodyAndHeaders(
                "direct:flexibleLinesExportNotification",
                "rb_mal-flexible-lines-20260727141110.zip",
                java.util.Map.of(CHOUETTE_REFERENTIAL, "rb_mal", CORRELATION_ID, "test-correlation"));

        postValidation.assertIsSatisfied();
        assertEquals(42L, postValidation.getExchanges().getFirst().getIn().getHeader(PROVIDER_ID));
        assertEquals("rb_mal", postValidation.getExchanges().getFirst().getIn().getHeader(DATASET_REFERENTIAL));
        assertEquals("inbound/uttu/rb_mal-flexible-lines-20260727141110.zip",
                postValidation.getExchanges().getFirst().getIn().getHeader(FILE_HANDLE));
    }

    @Test
    void unknownReferentialIsDroppedWithoutError() throws Exception {
        when(providerRepository.getProviderId("rb_mal")).thenReturn(null);

        adviseRoute();
        postValidation.expectedMessageCount(0);
        updateStatus.expectedMessageCount(0);

        // The notification must be consumed, not failed: a failure nacks the Pub/Sub
        // message and it redelivers forever, since the referential never becomes known.
        assertDoesNotThrow(() -> context.createProducerTemplate().sendBodyAndHeader(
                "direct:flexibleLinesExportNotification",
                "rb_mal-flexible-lines-20260727141110.zip",
                CHOUETTE_REFERENTIAL, "rb_mal"));

        postValidation.assertIsSatisfied();
        updateStatus.assertIsSatisfied();
    }
}
