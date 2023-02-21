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
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.Charset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;


class OtpNetexGraphRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @EndpointInject("mock:sink")
    protected MockEndpoint sink;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:OtpGraphBuildQueue")
    protected ProducerTemplate producerTemplate;

    @Test
    void testStatusEventReporting() throws Exception {

        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(  blobStoreSubdirectory+"/" + Constants.BASE_GRAPH_OBJ, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);

        AdviceWith.adviceWith(context, "otp-netex-graph-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-netex-graph-send-status-for-timetable-jobs", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-publish", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-build", a -> a.weaveByToUri("direct:exportMergedNetex").replace().to("mock:sink"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-build-and-send-status", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            a.weaveByToUri("direct:remoteBuildNetexGraph").replace().to("mock:sink");
        });

        updateStatus.expectedMessageCount(6);
        updateStatus.setResultWaitTime(20000);
        context.start();

        for(long refId = 1; refId <= 2; refId++) {
            sendBodyAndHeadersToPubSub(producerTemplate, "", createProviderJobHeaders(refId, "ref" + refId, "corr-id-" + refId));
        }

        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain) && JobEvent.State.STARTED.equals(je.state)));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain) && JobEvent.State.OK.equals(je.state)));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.STARTED.equals(je.state) && 1 == je.providerId));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.OK.equals(je.state) && 1 == je.providerId));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.STARTED.equals(je.state) && 2 == je.providerId));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.OK.equals(je.state) && 2 == je.providerId));
    }



}
