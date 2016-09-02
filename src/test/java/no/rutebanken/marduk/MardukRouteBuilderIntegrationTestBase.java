package no.rutebanken.marduk;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;

import static org.mockito.Mockito.when;

public class MardukRouteBuilderIntegrationTestBase {

    @MockBean
    protected ProviderRepository providerRepository;

    @Before
    public void setUp() throws IOException {
        when(providerRepository.getProviders()).thenReturn(Collections.singletonList(Provider.create(IOUtils.toString(new FileReader(
						"src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")))));

        when(providerRepository.getProvider(2L)).thenReturn(Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json"))));
    }

}
