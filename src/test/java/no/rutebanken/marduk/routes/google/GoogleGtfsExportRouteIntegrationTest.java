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

package no.rutebanken.marduk.routes.google;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GoogleGtfsExportRoute.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class GoogleGtfsExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Produce(uri = "activemq:queue:GoogleExportQueue")
    protected ProducerTemplate startRoute;


    @Value("${google.export.file.name:google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;


    @Before
    public void prepare() throws Exception {
        when(providerRepository.getProviders()).thenReturn(Arrays.asList(provider("rb_avi", 1, null), provider("rb_rut", 2, null), provider("opp", 3, 4l)));
    }


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
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(new File(pathname)), false);

        startRoute.request("activemq:queue:GoogleExportQueue", ex -> {
        });


        Assert.assertNotNull("Expected transformed gtfs file to have been uploaded", inMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/google/" + googleExportFileName));
    }


    private Provider provider(String ref, long id, Long migrateToProvider) throws Exception {
        Provider provider = new Provider();
        provider.chouetteInfo = new ChouetteInfo();
        provider.chouetteInfo.referential = ref;
        provider.chouetteInfo.migrateDataToProvider = migrateToProvider;
        provider.id = id;

        return provider;
    }
}