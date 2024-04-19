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

package no.rutebanken.marduk.routes.otp.otp1;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.io.InputStream;
import java.util.List;

import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static org.junit.jupiter.api.Assertions.assertTrue;


class OtpNetexGraphRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @EndpointInject("mock:sink")
    protected MockEndpoint sink;

    @EndpointInject("mock:remoteBuildNetexGraph")
    protected MockEndpoint remoteBuildNetexGraph;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:OtpGraphBuildQueue")
    protected ProducerTemplate producerTemplate;

    @Test
    void testStatusEventReporting() throws Exception {

        //populate fake blob repo
        InputStream dummyData = dummyData();
        mardukInMemoryBlobStoreRepository.uploadBlob(  blobStoreSubdirectory+"/" + Constants.BASE_GRAPH_OBJ, dummyData, false);

        AdviceWith.adviceWith(context, "otp-netex-graph-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-netex-graph-send-status-for-timetable-jobs", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-publish", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-build", a -> a.weaveByToUri("direct:exportMergedNetex").replace().to("mock:sink"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-build-and-send-status", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            a.weaveByToUri("direct:remoteBuildNetexGraph").replace().to("mock:remoteBuildNetexGraph");
        });

        remoteBuildNetexGraph.whenAnyExchangeReceived(e -> {
            InputStream dummyData2 = dummyData();
            internalInMemoryBlobStoreRepository.uploadBlob(e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + GRAPH_OBJ, dummyData2, false);

        });

        updateStatus.expectedMessageCount(6);
        updateStatus.setResultWaitTime(20000);
        context.start();




        for(long refId = 1; refId <= 2; refId++) {
            sendBodyAndHeadersToPubSub(producerTemplate, "", createProviderJobHeaders(refId, "ref" + refId, "corr-id-" + refId));
        }

        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.getDomain()) && JobEvent.State.STARTED.equals(je.getState())));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState())));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain()) && JobEvent.State.STARTED.equals(je.getState()) && 1 == je.getProviderId()));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState()) && 1 == je.getProviderId()));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain()) && JobEvent.State.STARTED.equals(je.getState()) && 2 == je.getProviderId()));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState()) && 2 == je.getProviderId()));
    }




}
