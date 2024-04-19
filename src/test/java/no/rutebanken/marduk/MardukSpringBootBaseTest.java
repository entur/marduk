package no.rutebanken.marduk;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.PostConstruct;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;

import static no.rutebanken.marduk.TestConstants.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,classes = TestApp.class)
@ActiveProfiles({"test", "default", "in-memory-blobstore", "google-pubsub-emulator", "google-pubsub-autocreate"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MardukSpringBootBaseTest {


    @Value("${blobstore.gcs.container.name}")
    private String mardukContainerName;

    @Value("${blobstore.gcs.internal.container.name}")
    private String internalContainerName;

    @Value("${blobstore.gcs.exchange.container.name}")
    private String exchangeContainerName;

    @Value("${blobstore.gcs.graphs.container.name}")
    private String graphsContainerName;

    @Value("${blobstore.gcs.otpreport.container.name}")
    private String otpReportContainerName;

    @Autowired
    protected PubSubTemplate pubSubTemplate;

    @Autowired
    private ReactorResourceFactory reactorResourceFactory;

    @Autowired
    protected InMemoryBlobStoreRepository mardukInMemoryBlobStoreRepository;


    @Autowired
    protected InMemoryBlobStoreRepository internalInMemoryBlobStoreRepository;

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
        internalInMemoryBlobStoreRepository.setContainerName(internalContainerName);
        exchangeInMemoryBlobStoreRepository.setContainerName(exchangeContainerName);
        graphsInMemoryBlobStoreRepository.setContainerName(graphsContainerName);
        otpReportInMemoryBlobStoreRepository.setContainerName(otpReportContainerName);
    }

    @BeforeEach
    protected void setUp() throws IOException {

        // prevent Netty from waiting 2s (default value) during each Spring Boot context shutdown
        reactorResourceFactory.setShutdownQuietPeriod(Duration.ofSeconds(0));

        Provider provider2 = provider(PROVIDER_ID_RUT);
        Provider provider1002 = provider(PROVIDER_ID_RB_RUT);

        when(providerRepository.getProvider(PROVIDER_ID_RUT)).thenReturn(provider2);
        when(providerRepository.getProvider(PROVIDER_ID_RB_RUT)).thenReturn(provider1002);
        when(providerRepository.getProviders()).thenReturn(List.of(provider2, provider1002));
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(PROVIDER_ID_RUT);
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RB_RUT)).thenReturn(PROVIDER_ID_RB_RUT);

        mardukInMemoryBlobStoreRepository.deleteAllFilesInFolder("");
        exchangeInMemoryBlobStoreRepository.deleteAllFilesInFolder("");
        graphsInMemoryBlobStoreRepository.deleteAllFilesInFolder("");
        otpReportInMemoryBlobStoreRepository.deleteAllFilesInFolder("");

    }

    protected static Provider provider(long id) throws IOException {
        return Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider" + id  + ".json")));
    }

    protected static @NotNull InputStream dummyData() {
        return IOUtils.toInputStream("dummyData", Charset.defaultCharset());
    }

}
