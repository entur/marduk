package no.rutebanken.marduk.geocoder.routes.maplayer;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MapBoxUpdateRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")

public class MapBoxUpdateRouteBuilderTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private ModelCamelContext context;

    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Produce(uri = "direct:insertElasticsearchIndexData")
    protected ProducerTemplate insertESDataTemplate;

    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Test
    public void testMapLayerDataSuccess() throws Exception {
        inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForTiamatGeoCoderExport + "/tiamat/tiamat-export-latest.xml",
                new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/geocoder/netex/tiamat-export.xml")), false);

        context.start();
        Exchange e = insertESDataTemplate.request("direct:convertTiamatDataMapLayer", System.out::println);
    }
}