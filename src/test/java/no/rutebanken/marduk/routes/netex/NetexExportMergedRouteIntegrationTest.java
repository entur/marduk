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
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.io.FileInputStream;
import java.util.List;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;


class NetexExportMergedRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:exportMergedNetex")
    protected ProducerTemplate startRoute;

    @Value("${netex.export.download.directory:files/netex/merged}")
    private String localWorkingDirectory;

    @Value("${netex.export.stop.place.blob.path:tiamat/Full_latest.zip}")
    private String stopPlaceExportBlobPath;

    @Value("${netex.export.file.path:netex/rb_norway-aggregated-netex.zip}")
    private String netexExportMergedFilePath;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Test
    void testExportMergedNetex() throws Exception {

        AdviceWith.adviceWith(context, "netex-export-merged-route", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "netex-export-merged-report-ok", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));


        // Create stop file in memory blob store
        mardukInMemoryBlobStoreRepository.uploadBlob(stopPlaceExportBlobPath, new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip"), false);

        // Create provider netex export in memory blob store
        mardukInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-aggregated-netex.zip", new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip"), false);

        updateStatus.expectedMessageCount(2);

        context.start();

        startRoute.requestBody(null);

        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.getDomain()) && JobEvent.State.STARTED.equals(je.getState())));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE_PUBLISH.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState())));

        assertThat(mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + netexExportMergedFilePath)).as("Expected merged netex file to have been uploaded").isNotNull();
    }

}
