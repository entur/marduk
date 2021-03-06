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

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import javax.annotation.PostConstruct;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import static org.mockito.Mockito.when;

@CamelSpringBootTest
@UseAdviceWith
@ActiveProfiles({"test", "default", "in-memory-blobstore", "google-pubsub-emulator"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class MardukRouteBuilderIntegrationTestBase {

    @Value("${blobstore.gcs.container.name}")
    private String mardukContainerName;

    @Value("${blobstore.gcs.exchange.container.name}")
    private String exchangeContainerName;

    @Value("${blobstore.gcs.graphs.container.name}")
    private String graphsContainerName;

    @Value("${blobstore.gcs.otpreport.container.name}")
    private String otpReportContainerName;

    @Autowired
    protected ModelCamelContext context;

    @Autowired
    protected PubSubTemplate pubSubTemplate;

    @Autowired
    protected InMemoryBlobStoreRepository mardukInMemoryBlobStoreRepository;

    @Autowired
    protected InMemoryBlobStoreRepository exchangeInMemoryBlobStoreRepository;

    @Autowired
    protected InMemoryBlobStoreRepository graphsInMemoryBlobStoreRepository;

    @Autowired
    protected InMemoryBlobStoreRepository otpReportInMemoryBlobStoreRepository;


    @MockBean
    public CacheProviderRepository providerRepository;

    @EndpointInject("mock:sink")
    protected MockEndpoint sink;

    @PostConstruct
    void initInMemoryBlobStoreRepositories() {
        mardukInMemoryBlobStoreRepository.setContainerName(mardukContainerName);
        exchangeInMemoryBlobStoreRepository.setContainerName(exchangeContainerName);
        graphsInMemoryBlobStoreRepository.setContainerName(graphsContainerName);
        otpReportInMemoryBlobStoreRepository.setContainerName(otpReportContainerName);
    }

    @BeforeEach
    protected void setUp() throws IOException {
        when(providerRepository.getProviders()).thenReturn(Collections.singletonList(Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")))));

        when(providerRepository.getProvider(2L)).thenReturn(Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json"))));

        when(providerRepository.getProviderId("rb_rut")).thenReturn(2L);

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
        provider.chouetteInfo.enableBlocksExport = true;

        return provider;
    }

    protected InputStream getTestNetexArchiveAsStream() {
        return getClass().getResourceAsStream("/no/rutebanken/marduk/routes/file/beans/netex.zip");
    }
}
