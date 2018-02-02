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

package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.chouette.ChouetteExportGtfsRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = ChouetteExportGtfsRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class OtpGtfsGraphRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {
    @Value("${otp.graph.blobstore.subdirectory}")
    private String blobStoreSubdirectory;

    @EndpointInject(uri = "mock:sink")
    protected MockEndpoint sink;

    @EndpointInject(uri = "mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce(uri = "activemq:queue:OtpGtfsGraphQueue")
    protected ProducerTemplate producerTemplate;

    @Test
    public void testStatusEventReporting() throws Exception {

        replaceEndpoint("otp-graph-send-started-events", "direct:updateStatus", "mock:updateStatus");
        replaceEndpoint("otp-graph-send-status-for-timetable-jobs", "direct:updateStatus", "mock:updateStatus");
        replaceEndpoint("otp-graph-build-and-send-status", "direct:updateStatus", "mock:updateStatus");

        // Skip everything but event reporting
        replaceEndpoint("otp-graph-build", "direct:fetchConfig", "mock:sink");
        replaceEndpoint("otp-graph-build", "direct:fetchMap", "mock:sink");
        replaceEndpoint("otp-graph-build", "direct:fetchLatestGtfs", "mock:sink");
        replaceEndpoint("otp-graph-build", "direct:mergeGtfs", "mock:sink");
        replaceEndpoint("otp-graph-build", "direct:transformToOTPIds", "mock:sink");
        replaceEndpoint("otp-graph-build-and-send-status", "direct:buildGraph", "mock:sink");

        producerTemplate.sendBody(null);
        producerTemplate.sendBodyAndHeaders(null, createProviderJobHeaders(2l, "ref", "corr-id"));

        context.start();

        updateStatus.expectedMessageCount(3);

        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).collect(Collectors.toList());

        Assert.assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.GRAPH.equals(je.domain) && JobEvent.State.STARTED.equals(je.state)));
        Assert.assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.STARTED.equals(je.state) && 2 == je.providerId));
        Assert.assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.domain) && JobEvent.State.OK.equals(je.state) && 2 == je.providerId));
    }


    private Map<String, Object> createProviderJobHeaders(Long providerId, String ref, String correlationId) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, providerId);
        headers.put(Constants.CHOUETTE_REFERENTIAL, ref);
        headers.put(Constants.CORRELATION_ID, correlationId);

        return headers;
    }

}

