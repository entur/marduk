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

package no.rutebanken.marduk.routes.otp.netex;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import no.rutebanken.marduk.routes.otp.remote.RemoteNetexGraphRouteBuilder;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = RemoteNetexGraphRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class OtpNetexGraphRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;


    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @EndpointInject(uri = "mock:sink")
    protected MockEndpoint sink;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce(uri = "entur-google-pubsub:OtpGraphBuildQueue")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testStatusEventReporting() throws Exception {

        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob(  blobStoreSubdirectory+"/" + Constants.BASE_GRAPH_OBJ, IOUtils.toInputStream("dummyData", Charset.defaultCharset()), false);


        context.getRouteDefinition("otp-netex-graph-send-started-events").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            }
        });

        context.getRouteDefinition("otp-netex-graph-send-status-for-timetable-jobs").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            }
        });

        context.getRouteDefinition("otp-remote-netex-graph-build").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToUri("direct:exportMergedNetex").replace().to("mock:sink");
            }
        });

        context.getRouteDefinition("otp-remote-netex-graph-build-and-send-status").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
                weaveByToUri("direct:remoteBuildNetexGraph").replace().to("mock:sink");
            }
        });

        updateStatus.expectedMessageCount(3);

        context.start();

        producerTemplate.sendBody(null);
        producerTemplate.sendBodyAndHeaders(null, createProviderJobHeaders(2l, "ref", "corr-id"));

        updateStatus.assertIsSatisfied(20000);

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());

        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain) && JobEvent.State.STARTED.equals(je.state)));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.STARTED.equals(je.state) && 2 == je.providerId));
        assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.OK.equals(je.state) && 2 == je.providerId));
    }


    private Map<String, Object> createProviderJobHeaders(Long providerId, String ref, String correlationId) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, providerId);
        headers.put(Constants.CHOUETTE_REFERENTIAL, ref);
        headers.put(Constants.CORRELATION_ID, correlationId);

        return headers;
    }
}
