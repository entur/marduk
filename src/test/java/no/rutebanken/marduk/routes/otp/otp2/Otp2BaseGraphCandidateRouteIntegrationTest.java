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

package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;

import static no.rutebanken.marduk.Constants.*;
import static org.junit.jupiter.api.Assertions.assertTrue;


class Otp2BaseGraphCandidateRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String OTP2_BASE_GRAPH_FILE_NAME = OTP2_BASE_GRAPH_OBJ_PREFIX + "-XXX.obj";


    @Value("${otp.graph.blobstore.subdirectory}")
    private String graphSubdirectory;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:Otp2BaseGraphCandidateBuildQueue")
    protected ProducerTemplate producerTemplate;

    @Test
    void testBaseGraphCandidateBuilding() throws Exception {


        AdviceWith.adviceWith(context, "otp2-base-graph-build-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp2-remote-base-graph-build-and-send-status", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp2-remote-base-graph-build-copy", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            a.weaveByToUri("direct:remoteBuildOtp2BaseGraph").replace().process(exchange -> {
                // create dummy base graph file in the blob store with an arbitrary serialization id
                String graphFileName = exchange.getProperty(OTP_REMOTE_WORK_DIR, String.class) + '/' + OTP2_BASE_GRAPH_OBJ_PREFIX + "-XXX.obj";
                internalInMemoryBlobStoreRepository.uploadBlob(graphFileName, dummyData(), false);
            });
        });

        updateStatus.expectedMessageCount(2);
        updateStatus.setResultWaitTime(20000);

        context.start();

        sendBodyAndHeadersToPubSub(producerTemplate, "", createProviderJobHeaders(TestConstants.PROVIDER_ID_RUT, "ref", "corr-id"));

        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.getDomain()) && JobEvent.State.STARTED.equals(je.getState())));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState())));

        // the graph object is present in the main bucket
        BlobStoreFiles blobsInVersionedSubDirectory = internalInMemoryBlobStoreRepository.listBlobs(graphSubdirectory + '/'  + OTP2_STREET_GRAPH_DIR + '/' + OTP2_BASE_GRAPH_FILE_NAME);

        Assertions.assertEquals(1, blobsInVersionedSubDirectory.getFiles().size(), "The candidate base graph object should be present in the main bucket");

    }
}
