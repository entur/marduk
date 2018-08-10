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

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NetexExportMergedRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class NetexExportMergedRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Produce(uri = "direct:exportMergedNetex")
    protected ProducerTemplate startRoute;

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @Test
    public void testExportMergedNetex() throws Exception {

        // Create stop file in in memory blob store
        inMemoryBlobStoreRepository.uploadBlob(stopPlaceExportBlobPath, new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip")), false);

        // Create provider netex export in in memory blob store
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-aggregated-netex.zip", new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip")), false);


        startRoute.requestBody(null);

        Assert.assertNotNull("Expected merged netex file to have been uploaded", inMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath));
    }

}
