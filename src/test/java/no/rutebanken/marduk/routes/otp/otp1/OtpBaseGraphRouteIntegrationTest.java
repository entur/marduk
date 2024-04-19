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
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static org.junit.jupiter.api.Assertions.assertTrue;


class OtpBaseGraphRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @EndpointInject("mock:sink")
    protected MockEndpoint sink;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:OtpGraphBuildQueue")
    protected ProducerTemplate graphProducerTemplate;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:OtpBaseGraphBuildQueue")
    protected ProducerTemplate baseGraphProducerTemplate;


    @Test
    void testRemoteBaseGraphBuildStatusEventReporting() throws Exception {

        AdviceWith.adviceWith(context, "otp-netex-graph-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-netex-graph-send-status-for-timetable-jobs", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-base-graph-build-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-remote-base-graph-build-and-send-status", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            a.weaveByToUri("direct:remoteBuildBaseGraph").replace().to("mock:sink");
        });

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-build-and-send-status", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            // create a dummy graph object in the remote graph builder work directory
            a.weaveByToUri("direct:remoteBuildNetexGraph")
                    .replace()
                    .process(e -> {
                        String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + GRAPH_OBJ;
                        internalInMemoryBlobStoreRepository.uploadBlob(builtOtpGraphPath, dummyData(), false);
                    });
        });

        updateStatus.expectedMessageCount(2);
        updateStatus.setResultWaitTime(20000);

        context.start();

        baseGraphProducerTemplate.sendBodyAndHeaders(null, createMessageHeaders(TestConstants.PROVIDER_ID_RUT, "ref", "corr-id"));

        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.getDomain())
                && "BUILD_BASE".equals(je.getAction())
                && JobEvent.State.OK.equals(je.getState()))
        );

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.getDomain())
                && "BUILD_BASE".equals(je.getAction())
                && JobEvent.State.STARTED.equals(je.getState()))
        );
    }

    private Map<String, Object> createMessageHeaders(Long providerId, String ref, String correlationId) {

        return Map.of(
                Constants.PROVIDER_ID, providerId,
                Constants.CHOUETTE_REFERENTIAL, ref,
                Constants.CORRELATION_ID, correlationId
        );
    }
}
