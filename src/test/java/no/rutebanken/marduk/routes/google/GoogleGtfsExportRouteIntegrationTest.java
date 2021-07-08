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
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
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
import java.util.List;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class GoogleGtfsExportRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Produce("direct:exportGtfsGoogle")
    protected ProducerTemplate startRoute;

    @Value("${google.export.file.name:google/google_norway-aggregated-gtfs.zip}")
    private String googleExportFileName;

    @Produce("direct:exportQaGtfsGoogle")
    protected ProducerTemplate startQaRoute;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;


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

        AdviceWith.adviceWith(context, "gtfs-export-merged-report-ok", a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                .to("mock:updateStatus"));

        updateStatus.expectedMessageCount(2);

        context.start();

        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_rut-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);

        startRoute.requestBody(null);

        updateStatus.assertIsSatisfied();
        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.domain) && JobEvent.State.STARTED.equals(je.state)));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.domain) && JobEvent.State.OK.equals(je.state)));


        assertThat(mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + googleExportFileName)).as("Expected transformed gtfs file to have been uploaded").isNotNull();
    }

    @Test
    void testUploadQaGtfsToGoogle() throws Exception {

        AdviceWith.adviceWith(context, "gtfs-export-merged-report-ok", a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                .to("mock:updateStatus"));

        updateStatus.expectedMessageCount(2);

        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/rb_opp-aggregated-gtfs.zip", new FileInputStream(getExtendedGtfsTestFile()), false);

        context.start();

        startQaRoute.requestBody(null);

        updateStatus.assertIsSatisfied();
        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.domain) && JobEvent.State.STARTED.equals(je.state)));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.domain) && JobEvent.State.OK.equals(je.state)));


        assertThat(mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "gtfs/" + googleQaExportFileName)).as("Expected transformed gtfs file to have been uploaded").isNotNull();
    }

    private File getExtendedGtfsTestFile() throws IOException {
        Path extendedGTFSFile = Files.createTempFile("extendedGTFSFile", ".zip");
        Files.copy(Path.of( "src/test/resources/no/rutebanken/marduk/routes/gtfs/extended_gtfs.zip"), extendedGTFSFile, StandardCopyOption.REPLACE_EXISTING);
        return extendedGTFSFile.toFile();
    }


}