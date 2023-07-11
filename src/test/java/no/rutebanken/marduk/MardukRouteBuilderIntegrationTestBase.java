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

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.google.pubsub.GooglePubsubConstants;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@CamelSpringBootTest
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class MardukRouteBuilderIntegrationTestBase extends MardukSpringBootBaseTest{

    @Autowired
    protected CamelContext context;

    protected Provider provider(String ref, long id, Long migrateToProvider) {
        return provider(ref, id, migrateToProvider, false, false);
    }

    protected Provider provider(String ref, long id, Long migrateToProvider, boolean googleUpload, boolean googleQAUpload) {
        Provider provider = new Provider();
        provider.setChouetteInfo(new ChouetteInfo());
        provider.getChouetteInfo().setReferential(ref);
        provider.getChouetteInfo().setMigrateDataToProvider(migrateToProvider);
        provider.setId(id);
        provider.getChouetteInfo().setGoogleUpload(googleUpload);
        provider.getChouetteInfo().setGoogleQAUpload(googleQAUpload);
        provider.getChouetteInfo().setEnableBlocksExport(true);

        return provider;
    }

    protected InputStream getTestNetexArchiveAsStream() {
        return getClass().getResourceAsStream("/no/rutebanken/marduk/routes/file/beans/netex.zip");
    }

    protected InputStream getLargeTestNetexArchiveAsStream() {
        return getClass().getResourceAsStream("/no/rutebanken/marduk/routes/file/beans/AOR.zip");
    }

    protected void sendBodyAndHeadersToPubSub(ProducerTemplate producerTemplate, Object body, Map<String, String> headers) {
        producerTemplate.sendBodyAndHeader(body, GooglePubsubConstants.ATTRIBUTES  ,headers);
    }

    protected Map<String, String> createProviderJobHeaders(Long providerId, String ref, String correlationId) {

        Map<String, String> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, providerId.toString());
        headers.put(Constants.CHOUETTE_REFERENTIAL, ref);
        headers.put(Constants.CORRELATION_ID, correlationId);

        return headers;
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

}

