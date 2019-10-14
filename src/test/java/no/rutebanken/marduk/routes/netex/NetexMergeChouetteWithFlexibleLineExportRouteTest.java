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

package no.rutebanken.marduk.routes.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_CHOUETTE;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NetexMergeChouetteWithFlexibleLineExportRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class NetexMergeChouetteWithFlexibleLineExportRouteTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Produce(uri = "direct:mergeChouetteExportWithFlexibleLinesExport")
    protected ProducerTemplate startRoute;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint updateStatus;


    @EndpointInject(uri = "mock:OtpGraphBuildQueue")
    protected MockEndpoint otpBuildGraph;


    @Test
    public void testExportMergedNetex() throws Exception {

        // Mock status update
        context.getRouteDefinition("netex-export-merge-chouette-with-flexible-lines").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {

                interceptSendToEndpoint("entur-google-pubsub:OtpGraphBuildQueue").skipSendToOriginalEndpoint()
                        .to("mock:OtpGraphBuildQueue");
                interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                        .to("mock:updateStatus");
            }
        });


        context.start();

        otpBuildGraph.expectedMessageCount(1);
        updateStatus.expectedMessageCount(1);

        // Create flexible line export in in memory blob store
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-" + CURRENT_FLEXIBLE_LINES_NETEX_FILENAME, new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex_with_two_files.zip")), false);

        // Create chouette netex export in in memory blob store
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_CHOUETTE + "netex/rb_rut-aggregated-netex.zip", new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip")), false);


        startRoute.requestBodyAndHeader(null, Constants.CHOUETTE_REFERENTIAL, "rb_rut");

        Assert.assertNotNull("Expected merged netex file to have been uploaded", inMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME));
        Assert.assertNotNull("Expected merged netex file to have been uploaded to marduk exchange for DatedServiceJourneyId-generation", inMemoryBlobStoreRepository.listBlobs(BLOBSTORE_PATH_OUTBOUND + "dated").getFiles().size() > 0);


        updateStatus.assertIsSatisfied();
        otpBuildGraph.assertIsSatisfied();
    }

}
