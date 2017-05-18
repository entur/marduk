package no.rutebanken.marduk;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.when;
@RunWith(CamelSpringRunner.class)
@ActiveProfiles({"default", "in-memory-blobstore"})
@UseAdviceWith
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class MardukRouteBuilderIntegrationTestBase {

    @Autowired
    protected ModelCamelContext context;
    @MockBean
    public CacheProviderRepository providerRepository;

    @Before
    public void setUp() throws IOException {

        when(providerRepository.getProviders()).thenReturn(Collections.singletonList(Provider.create(IOUtils.toString(new FileReader(
						"src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")))));

        when(providerRepository.getProvider(2L)).thenReturn(Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json"))));

    }

    protected void replaceEndpoint(String routeId,String originalEndpoint,String replacementEndpoint) throws Exception {
        context.getRouteDefinition(routeId).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint(originalEndpoint)
                        .skipSendToOriginalEndpoint().to(replacementEndpoint);
            }
        });
    }


}
