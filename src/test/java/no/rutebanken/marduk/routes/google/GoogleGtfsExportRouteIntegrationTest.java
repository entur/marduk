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
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class GoogleGtfsExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Produce("direct:exportGtfsGoogle")
    protected ProducerTemplate startRoute;

    @Value("${google.export.file.name:google/google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;

    @Produce("direct:exportQaGtfsGoogle")
    protected ProducerTemplate startQaRoute;


    @Value("${google.export.qa.file.name:google/google_norway-aggregated-qa-gtfs.zip}")
    private String googleQaExportFileName;

    @BeforeEach
    void prepare() {
        Provider rbOppProvider = provider("rb_opp", 4, null, false, false);
        when(providerRepository.getProviders()).thenReturn(Arrays.asList(provider("rb_avi", 1, null, false, false), provider("rb_rut", 2, null, true, false),
                provider("opp", 3, rbOppProvider.id, false, true), rbOppProvider));

        when(providerRepository.getProvider(rbOppProvider.id)).thenReturn(rbOppProvider);
    }


    @Test
    void testUploadGtfsToGoogle() throws Exception {
        context.start();

        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);

        startRoute.requestBody(null);

        assertThat(mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + googleExportFileName)).as("Expected transformed gtfs file to have been uploaded").isNotNull();
    }

    @Test
    void testUploadQaGtfsToGoogle() throws Exception {
        context.start();

        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_opp-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);

        startQaRoute.requestBody(null);

        assertThat(mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + googleQaExportFileName)).as("Expected transformed gtfs file to have been uploaded").isNotNull();
    }

    private File getExtendedGtfsTestFile() throws IOException {
        Path extendedGTFSFile = Files.createTempFile("extendedGTFSFile", ".zip");
        Files.copy(Path.of( "src/test/resources/no/rutebanken/marduk/routes/gtfs/extended_gtfs.zip"), extendedGTFSFile, StandardCopyOption.REPLACE_EXISTING);
        return extendedGTFSFile.toFile();
    }


}