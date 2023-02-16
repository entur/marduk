package no.rutebanken.marduk;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
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
import java.util.Collections;

import static org.mockito.Mockito.when;

@ActiveProfiles({"test", "default", "in-memory-blobstore", "google-pubsub-emulator", "google-pubsub-autocreate"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MardukSpringBootBaseTest {

    @Value("${blobstore.gcs.container.name}")
    private String mardukContainerName;

    @Value("${blobstore.gcs.exchange.container.name}")
    private String exchangeContainerName;

    @Value("${blobstore.gcs.graphs.container.name}")
    private String graphsContainerName;

    @Value("${blobstore.gcs.otpreport.container.name}")
    private String otpReportContainerName;

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

        mardukInMemoryBlobStoreRepository.deleteAllFilesInFolder("");
        exchangeInMemoryBlobStoreRepository.deleteAllFilesInFolder("");
        graphsInMemoryBlobStoreRepository.deleteAllFilesInFolder("");
        otpReportInMemoryBlobStoreRepository.deleteAllFilesInFolder("");

    }

}
