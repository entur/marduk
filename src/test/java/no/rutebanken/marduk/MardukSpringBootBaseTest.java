package no.rutebanken.marduk;

import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.CacheProviderRepository;
import no.rutebanken.marduk.repository.MardukBlobStoreRepository;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.commons.io.IOUtils;
import org.entur.pubsub.base.EnturGooglePubSubAdmin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import jakarta.annotation.PostConstruct;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.List;

import static no.rutebanken.marduk.TestConstants.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,classes = TestApp.class)
@ActiveProfiles({"test", "default", "in-memory-blobstore", "google-pubsub-autocreate"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MardukSpringBootBaseTest {

    protected final Long testProviderId = 0L;
    protected final String testDatasetReferential = "TST";

    protected final Long testRbProviderId = 1L;
    protected final String testDatasetChouetteReferential = "rb_TST";

    private static PubSubEmulatorContainer pubsubEmulator;

    @Autowired
    private EnturGooglePubSubAdmin enturGooglePubSubAdmin;

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
    protected MardukBlobStoreRepository mardukInMemoryBlobStoreRepository;


    @Autowired
    protected MardukBlobStoreRepository internalInMemoryBlobStoreRepository;

    @Autowired
    protected MardukBlobStoreRepository exchangeInMemoryBlobStoreRepository;

    @Autowired
    protected MardukBlobStoreRepository graphsInMemoryBlobStoreRepository;

    @Autowired
    protected MardukBlobStoreRepository otpReportInMemoryBlobStoreRepository;

    @MockitoBean
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

    @BeforeAll
    public static void init() {
        pubsubEmulator =
                new PubSubEmulatorContainer(
                        DockerImageName.parse(
                                "gcr.io/google.com/cloudsdktool/cloud-sdk:emulators"
                        )
                );
        pubsubEmulator.start();
    }

    @AfterAll
    public static void tearDown() {
        pubsubEmulator.stop();
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

    @AfterEach
    public void teardown() {
        enturGooglePubSubAdmin.deleteAllSubscriptions();
    }

    @DynamicPropertySource
    static void emulatorProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.cloud.gcp.pubsub.emulator-host",
                pubsubEmulator::getEmulatorEndpoint
        );
        registry.add(
                "camel.component.google-pubsub.endpoint",
                pubsubEmulator::getEmulatorEndpoint
        );
    }



    protected static Provider provider(long id) throws IOException {
        return Provider.create(IOUtils.toString(new FileReader(
                "src/test/resources/no/rutebanken/marduk/providerRepository/provider" + id  + ".json")));
    }

    protected static  InputStream dummyData() {
        return IOUtils.toInputStream("dummyData", Charset.defaultCharset());
    }

    protected Provider testProvider(Boolean experimentalImportEnabled) {
        Provider provider = new Provider();
        provider.setId(testProviderId);
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setEnableExperimentalImport(experimentalImportEnabled);
        chouetteInfo.setReferential(testDatasetReferential);
        provider.setChouetteInfo(chouetteInfo);
        return provider;
    }

    protected Provider providerWithExperimentalImport() {
        return testProvider(true);
    }

    protected Provider providerWithoutExperimentalImport() {
        return testProvider(false);
    }

    protected Provider chouetteProvider() {
        Provider provider = testProvider(false);
        provider.setId(testRbProviderId);
        ChouetteInfo chouetteInfo = new ChouetteInfo();
        chouetteInfo.setId(testRbProviderId);
        chouetteInfo.setEnableExperimentalImport(false);
        chouetteInfo.setReferential(testDatasetChouetteReferential);
        provider.setChouetteInfo(chouetteInfo);
        return provider;
    }

    protected Exchange exchange() {
        CamelContext ctx = new DefaultCamelContext();
        Exchange exchange = new DefaultExchange(ctx);
        exchange.getIn().setHeader(Constants.PROVIDER_ID, testProviderId);
        exchange.getIn().setHeader(Constants.DATASET_REFERENTIAL, testDatasetReferential);
        exchange.getIn().setHeader(Constants.CHOUETTE_REFERENTIAL, testDatasetReferential);
        exchange.getIn().setHeader(Constants.CORRELATION_ID, "correlation");
        exchange.setProperty(Constants.FOLDER_NAME, "/base/folder");
        return exchange;
    }
}
