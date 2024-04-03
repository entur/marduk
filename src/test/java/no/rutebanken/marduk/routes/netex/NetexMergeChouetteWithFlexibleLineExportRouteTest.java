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
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_NETEX_EXPORT;
import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_OUTBOUND;
import static no.rutebanken.marduk.Constants.CURRENT_FLEXIBLE_LINES_NETEX_FILENAME;
import static org.junit.jupiter.api.Assertions.*;


class NetexMergeChouetteWithFlexibleLineExportRouteTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("direct:mergeChouetteExportWithFlexibleLinesExport")
    protected ProducerTemplate startRoute;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;


    @EndpointInject("mock:OtpGraphBuildQueue")
    protected MockEndpoint otpBuildGraph;

    @EndpointInject("mock:NetexExportNotificationQueue")
    protected MockEndpoint netexExportNotificationQueue;


    @Test
    void testExportMergedNetex() throws Exception {

        // Mock status update
        AdviceWith.adviceWith(context, "publish-merged-dataset", a -> {

            a.weaveByToUri("google-pubsub:(.*):OtpGraphBuildQueue").replace().to("mock:OtpGraphBuildQueue");

            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
        });

        AdviceWith.adviceWith(context, "netex-notify-export", a -> a.weaveByToUri("google-pubsub:(.*):NetexExportNotificationQueue").replace().to("mock:NetexExportNotificationQueue"));

        otpBuildGraph.expectedMessageCount(1);
        otpBuildGraph.setResultWaitTime(20000);
        updateStatus.expectedMessageCount(1);
        updateStatus.setResultWaitTime(20000);
        netexExportNotificationQueue.expectedMessageCount(1);
        netexExportNotificationQueue.setResultWaitTime(20000);

        context.start();

        // Create flexible line export in in memory blob store
        internalInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-" + CURRENT_FLEXIBLE_LINES_NETEX_FILENAME, new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex_with_two_files.zip"));

        // Create chouette netex export in in memory blob store
        internalInMemoryBlobStoreRepository.uploadBlob(BLOBSTORE_PATH_NETEX_EXPORT + "rb_rut-aggregated-netex.zip", new FileInputStream("src/test/resources/no/rutebanken/marduk/routes/file/beans/netex.zip"));


        startRoute.requestBodyAndHeader(null, Constants.CHOUETTE_REFERENTIAL, "rb_rut");

        updateStatus.assertIsSatisfied();
        otpBuildGraph.assertIsSatisfied();
        netexExportNotificationQueue.assertIsSatisfied();

        assertNotNull(mardukInMemoryBlobStoreRepository.getBlob(BLOBSTORE_PATH_OUTBOUND + "netex/rb_rut-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME), "Expected merged netex file to have been uploaded");
        assertFalse(exchangeInMemoryBlobStoreRepository.listBlobs(BLOBSTORE_PATH_OUTBOUND + "dated").getFiles().isEmpty(), "Expected merged netex file to have been uploaded to marduk exchange for DatedServiceJourneyId-generation");
        assertEquals("rut", netexExportNotificationQueue.getExchanges().get(0).getIn().getBody());

    }

}
