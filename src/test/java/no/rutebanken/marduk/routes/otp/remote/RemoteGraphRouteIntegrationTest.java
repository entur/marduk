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

package no.rutebanken.marduk.routes.otp.remote;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
public class RemoteGraphRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private BlobStoreRepository blobStoreRepository;


    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @EndpointInject(uri = "mock:sink")
    protected MockEndpoint sink;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce(uri = "entur-google-pubsub:OtpGraphBuildQueue")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testRemoteNetexGraphBuildStatusEventReporting() throws Exception {

        // create a dummy base graph object in the blobstore repository
        blobStoreRepository.uploadBlob(blobStoreSubdirectory + "/" + Constants.BASE_GRAPH_OBJ, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);


        context.getRouteDefinition("otp-netex-graph-send-started-events").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            }
        });

        context.getRouteDefinition("otp-netex-graph-send-status-for-timetable-jobs").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            }
        });

        context.getRouteDefinition("otp-remote-netex-graph-build").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:exportMergedNetex").replace().to("mock:sink");
            }
        });


        context.getRouteDefinition("otp-remote-netex-graph-build-and-send-status").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");

                // create a dummy graph object in the remote graph builder work directory
                weaveByToUri("direct:remoteBuildNetexGraph")
                        .replace()
                        .process(e -> {
                            String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + GRAPH_OBJ;
                            blobStoreRepository.uploadBlob(builtOtpGraphPath, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);
                        });
            }
        });

        updateStatus.expectedMessageCount(3);

        context.start();

        producerTemplate.sendBody(null);
        producerTemplate.sendBodyAndHeaders(null, createMessageHeaders(2l, "ref", "corr-id", false));

        updateStatus.assertIsSatisfied(20000);

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain) && JobEvent.State.STARTED.equals(je.state)));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.STARTED.equals(je.state) && 2 == je.providerId));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.OK.equals(je.state) && 2 == je.providerId));
    }

    @Test
    public void testRemoteBaseGraphBuildStatusEventReporting() throws Exception {

        context.getRouteDefinition("otp-netex-graph-send-started-events").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            }
        });

        context.getRouteDefinition("otp-netex-graph-send-status-for-timetable-jobs").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            }
        });


        context.getRouteDefinition("otp-remote-base-graph-build-and-send-status").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
                weaveByToUri("direct:remoteBuildBaseGraph").replace().to("mock:sink");
            }
        });

        context.getRouteDefinition("otp-remote-netex-graph-build-and-send-status").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
                // create a dummy graph object in the remote graph builder work directory
                weaveByToUri("direct:remoteBuildNetexGraph")
                        .replace()
                        .process(e -> {
                            String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + GRAPH_OBJ;
                            blobStoreRepository.uploadBlob(builtOtpGraphPath, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);
                        });
            }
        });

        updateStatus.expectedMessageCount(2);

        context.start();

        producerTemplate.sendBody(null);
        producerTemplate.sendBodyAndHeaders(null, createMessageHeaders(2l, "ref", "corr-id", true));

        updateStatus.assertIsSatisfied(20000);

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain)
                && "BUILD_BASE".equals(je.action)
                && JobEvent.State.OK.equals(je.state))
        );

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain)
                && "BUILD_GRAPH".equals(je.action)
                && JobEvent.State.STARTED.equals(je.state))
        );
    }

    private Map<String, Object> createMessageHeaders(Long providerId, String ref, String correlationId, boolean buildBaseGraph) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, providerId);
        headers.put(Constants.CHOUETTE_REFERENTIAL, ref);
        headers.put(Constants.CORRELATION_ID, correlationId);

        if (buildBaseGraph) {
            headers.put(Constants.ADMIN_REST_OTP_BASE_GRAPH_BUILD_REQUESTED, "true");
        }

        return headers;
    }
}
