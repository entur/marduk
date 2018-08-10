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

package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Arrays;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = GtfsBasicMergedExportRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class GtfsBasicExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Produce(uri = "direct:exportGtfsBasicMerged")
    protected ProducerTemplate startRoute;


    @Value("${gtfs.basic.norway.merged.file.name:rb_norway-aggregated-gtfs-basic.zip}")
    private String exportFileName;


    @Before
    public void prepare() throws Exception {
        when(providerRepository.getProviders()).thenReturn(Arrays.asList(provider("rb_avi", 1, null), provider("rb_rut", 2, null), provider("opp", 3, 4l)));
    }


    @Test
    public void testUploadBasicGtfsMergedFile() throws Exception {
        context.start();

        String pathname = "src/test/resources/no/rutebanken/marduk/routes/gtfs/extended_gtfs.zip";

        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(new File(pathname)), false);
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_avi-aggregated-gtfs.zip", new FileInputStream(new File(pathname)), false);

        startRoute.requestBody(null);

        InputStream mergedIS = inMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + exportFileName);
        Assert.assertNotNull("Expected transformed gtfs file to have been uploaded", mergedIS);

        File mergedFile = File.createTempFile("mergedID", "tmp");
        FileUtils.copyInputStreamToFile(mergedIS, mergedFile);
        GtfsTransformationServiceTest.assertRouteRouteTypesAreConvertedToBasicGtfsValues(mergedFile);
    }

}
