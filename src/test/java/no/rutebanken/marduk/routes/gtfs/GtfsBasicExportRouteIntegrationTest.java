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
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.gtfs.GtfsFileUtilsTransformationTest;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;


class GtfsBasicExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Produce("direct:exportGtfsBasicMerged")
    protected ProducerTemplate startRoute;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;


    @Value("${gtfs.basic.norway.merged.file.name:rb_norway-aggregated-gtfs-basic.zip}")
    private String exportFileName;


    @BeforeEach
    void prepare() {
        when(providerRepository.getProviders()).thenReturn(Arrays.asList(provider("rb_avi", 1, null), provider(TestConstants.CHOUETTE_REFERENTIAL_RB_RUT, 2, null), provider("opp", 3, 4L)));
    }


    @Test
    void testUploadBasicGtfsMergedFile() throws Exception {

        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_avi-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);

        AdviceWith.adviceWith(context, "gtfs-export-merged-report-ok", a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                .to("mock:updateStatus"));

        updateStatus.expectedMessageCount(2);

        context.start();

        startRoute.requestBody(null);

        updateStatus.assertIsSatisfied();
        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.getDomain()) && JobEvent.State.STARTED.equals(je.getState())));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState())));


        InputStream mergedIS = mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + exportFileName);
        assertThat(mergedIS).as("Expected transformed gtfs file to have been uploaded").isNotNull();

        File mergedFile = File.createTempFile("mergedID", "tmp");
        FileUtils.copyInputStreamToFile(mergedIS, mergedFile);
        GtfsFileUtilsTransformationTest.assertRouteRouteTypesAreConvertedToBasicGtfsValues(mergedFile);
    }

    private File getExtendedGtfsTestFile() throws IOException {
        Path extendedGTFSFile = Files.createTempFile("extendedGTFSFile", ".zip");
        Files.copy(Path.of( "src/test/resources/no/rutebanken/marduk/routes/gtfs/extended_gtfs.zip"), extendedGTFSFile, StandardCopyOption.REPLACE_EXISTING);
        return extendedGTFSFile.toFile();
    }

}
