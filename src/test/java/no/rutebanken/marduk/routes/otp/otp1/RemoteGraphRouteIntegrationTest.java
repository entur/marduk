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
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class RemoteGraphRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private BlobStoreRepository blobStoreRepository;


    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @EndpointInject("mock:sink")
    protected MockEndpoint sink;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("entur-google-pubsub:OtpGraphBuildQueue")
    protected ProducerTemplate graphProducerTemplate;

    @Produce("entur-google-pubsub:OtpBaseGraphBuildQueue")
    protected ProducerTemplate baseGraphProducerTemplate;

    @Test
    void testRemoteNetexGraphBuildStatusEventReporting() throws Exception {

        // create a dummy base graph object in the blobstore repository
        blobStoreRepository.uploadBlob(blobStoreSubdirectory + "/" + Constants.BASE_GRAPH_OBJ, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);

        AdviceWith.adviceWith(context, "otp-netex-graph-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-netex-graph-send-status-for-timetable-jobs", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-build", a -> a.weaveByToUri("direct:exportMergedNetex").replace().to("mock:sink"));

        AdviceWith.adviceWith(context, "otp-remote-netex-graph-build-and-send-status", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");

            // create a dummy graph object in the remote graph builder work directory
            a.weaveByToUri("direct:remoteBuildNetexGraph")
                    .replace()
                    .process(e -> {
                        String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + GRAPH_OBJ;
                        blobStoreRepository.uploadBlob(builtOtpGraphPath, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);
                    });
        });


        updateStatus.expectedMessageCount(3);

        context.start();

        graphProducerTemplate.sendBody(null);
        graphProducerTemplate.sendBodyAndHeaders(null, createMessageHeaders(2L, "ref", "corr-id"));

        updateStatus.assertIsSatisfied(20000);

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain) && JobEvent.State.STARTED.equals(je.state)));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.STARTED.equals(je.state) && 2 == je.providerId));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.OK.equals(je.state) && 2 == je.providerId));
    }

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
                        blobStoreRepository.uploadBlob(builtOtpGraphPath, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);
                    });
        });

        updateStatus.expectedMessageCount(2);

        context.start();

        baseGraphProducerTemplate.sendBody(null);
        baseGraphProducerTemplate.sendBodyAndHeaders(null, createMessageHeaders(2L, "ref", "corr-id"));

        updateStatus.assertIsSatisfied(20000);

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain)
                && "BUILD_BASE".equals(je.action)
                && JobEvent.State.OK.equals(je.state))
        );

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain)
                && "BUILD_BASE".equals(je.action)
                && JobEvent.State.STARTED.equals(je.state))
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
