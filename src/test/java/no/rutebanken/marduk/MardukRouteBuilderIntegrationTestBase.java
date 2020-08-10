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
import no.rutebanken.marduk.repository.CacheProviderRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spring.SpringCamelContext;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.gcp.pubsub.core.PubSubTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ActiveProfiles({"test", "default", "in-memory-blobstore", "google-pubsub-emulator"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class MardukRouteBuilderIntegrationTestBase {

    @Autowired
    protected ModelCamelContext context;

    @Autowired
    protected PubSubTemplate pubSubTemplate;

    @MockBean
    public CacheProviderRepository providerRepository;

    @EndpointInject(uri = "mock:sink")
    protected MockEndpoint sink;

    // manually start the camel context so that routes can be reliably modified (mocked).
    
    @BeforeAll
    public static void disableStartBeforeContext() {
    	SpringCamelContext.setNoStart(true);
    }

    @BeforeEach
    public void enableStart() {
    	assertFalse(context.getStatus().isStarted());
    	SpringCamelContext.setNoStart(false);
    }

    @AfterEach
    public void disableStartBeforeReloadedContext() throws Exception {
    	SpringCamelContext.setNoStart(true);
    	// Explicitly stop the Camel context here so that PubSub resources are released before the PubSub emulator is stopped
    	context.stop();
    }

    @BeforeEach
    public void setUp() throws IOException {
        when(providerRepository.getProviders()).thenReturn(Collections.singletonList(Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")))));

        when(providerRepository.getProvider(2L)).thenReturn(Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json"))));

        when(providerRepository.getProviderId("rb_rut")).thenReturn(2L);

    }

    protected void replaceEndpoint(String routeId, String originalEndpoint, String replacementEndpoint) throws Exception {
        context.getRouteDefinition(routeId).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint(originalEndpoint)
                        .skipSendToOriginalEndpoint().to(replacementEndpoint);
            }
        });
    }

    protected Provider provider(String ref, long id, Long migrateToProvider) {
        return provider(ref, id, migrateToProvider, false, false);
    }

    protected Provider provider(String ref, long id, Long migrateToProvider, boolean googleUpload, boolean googleQAUpload) {
        Provider provider = new Provider();
        provider.chouetteInfo = new ChouetteInfo();
        provider.chouetteInfo.referential = ref;
        provider.chouetteInfo.migrateDataToProvider = migrateToProvider;
        provider.id = id;
        provider.chouetteInfo.googleUpload = googleUpload;
        provider.chouetteInfo.googleQAUpload = googleQAUpload;

        return provider;
    }

    protected InputStream getTestNetexArchiveAsStream() {
        return getClass().getResourceAsStream("/no/rutebanken/marduk/routes/file/beans/netex.zip");
    }
}
