package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoogleGtfsExportRoute.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class GoogleGtfsExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;


    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String gtfsNorwayMergedFileName;


    @Produce(uri = "activemq:queue:GoogleExportQueue")
    protected ProducerTemplate startRoute;


    @Value("${google.export.file.name:google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;



    @Test
    public void testUploadGtfsToGoogle() throws Exception {
        // Mock queue for triggering publish
        context.getRouteDefinition("google-export-route").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("activemq:queue:GooglePublishQueue").skipSendToOriginalEndpoint()
                        .to("mock:publishQueue");
            }
        });

        context.start();

        String pathname = "src/test/resources/no/rutebanken/marduk/routes/google/gtfs_for_google_transform.zip";

        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + gtfsNorwayMergedFileName, new FileInputStream(new File(pathname)), false);


        startRoute.request("activemq:queue:GoogleExportQueue", ex -> {
        });


        Assert.assertNotNull("Expected transformed gtfs file to have been uploaded", inMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/google/" + googleExportFileName));
    }


}