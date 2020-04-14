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
import static org.assertj.core.api.Assertions.assertThat;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.mockito.Mockito.when;
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
public class GtfsExtendedExportRouteIntegrationTest  extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @Produce(uri = "direct:exportGtfsExtendedMerged")
    protected ProducerTemplate startRoute;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint updateStatus;


    @Value("${gtfs.norway.merged.file.name:rb_norway-aggregated-gtfs.zip}")
    private String exportFileName;


    @BeforeEach
    public void prepare() {
        when(providerRepository.getProviders()).thenReturn(Arrays.asList(provider("rb_avi", 1, null), provider("rb_rut", 2, null), provider("opp", 3, 4L)));
    }


    @Test
    public void testUploadExtendedGtfsMergedFile() throws Exception {
        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);
        inMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_avi-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);

        context.getRouteDefinition("gtfs-export-merged-report-ok").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                        .to("mock:updateStatus");
            }
        });
        updateStatus.expectedMessageCount(2);

        context.start();

        startRoute.requestBody(null);

        updateStatus.assertIsSatisfied();

        InputStream mergedIS = inMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + exportFileName);
        assertThat(mergedIS).as("Expected transformed gtfs file to have been uploaded").isNotNull();

        File mergedFile = File.createTempFile("mergedID", "tmp");
        FileUtils.copyInputStreamToFile(mergedIS, mergedFile);
        assertStopVehicleTypesAreNotConverted(mergedFile);
    }

   private void assertStopVehicleTypesAreNotConverted(File out) throws IOException {
        List<String> stopLines = IOUtils.readLines(new ByteArrayInputStream(ZipFileUtils.extractFileFromZipFile(out, "stops.txt")), StandardCharsets.UTF_8);
        stopLines.remove(0); // remove header
        
        assertThat(stopLines.get(0)).as("Line without vehicle type should not be changed").endsWith(",,");
        assertThat(stopLines.get(1)).as("Line with valid value 701 should not be changed").endsWith(",701,");

        assertThat(stopLines.get(2)).as("Line with extended value 1012 should not be changed").endsWith(",1012,");
        assertThat(stopLines.get(3)).as("Line with extended value 1601 should not be changed").endsWith(",1601,");
    }

    private File getExtendedGtfsTestFile() throws IOException {
        Path extendedGTFSFile = Files.createTempFile("extendedGTFSFile", ".zip");
        Files.copy(Path.of( "src/test/resources/no/rutebanken/marduk/routes/gtfs/extended_gtfs.zip"), extendedGTFSFile, StandardCopyOption.REPLACE_EXISTING);
        return extendedGTFSFile.toFile();
    }

}
