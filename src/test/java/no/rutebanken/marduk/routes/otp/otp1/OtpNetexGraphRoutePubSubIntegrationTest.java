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
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class,
        properties = {
                "marduk.camel.redelivery.max=1",
                "marduk.camel.redelivery.delay=0",
                "marduk.camel.redelivery.backoff.multiplier=1",
        })
class OtpNetexGraphRoutePubSubIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:buildOtpGraph")
    protected MockEndpoint buildOtpGraph;


    @Produce("entur-google-pubsub:OtpGraphBuildQueue")
    protected ProducerTemplate producerTemplate;



    @Test
    void testOtpGraphMessageAggregationOneMessageWithoutException() throws Exception {

        AdviceWith.adviceWith(context, "otp-graph-build", a -> a.weaveByToUri("direct:remoteBuildOtpGraph").replace().to("mock:buildOtpGraph"));

        buildOtpGraph.expectedMessageCount(1);

        context.start();

        producerTemplate.sendBodyAndHeaders(null, createProviderJobHeaders(2L, "ref", "corr-id"));

        buildOtpGraph.assertIsSatisfied(20000);

    }


    @Test
    void testOtpGraphMessageAggregationWithoutException() throws Exception {

        AdviceWith.adviceWith(context, "otp-graph-build", a -> a.weaveByToUri("direct:remoteBuildOtpGraph").replace().to("mock:buildOtpGraph"));

        buildOtpGraph.expectedMessageCount(1);

        context.start();

        producerTemplate.sendBody("");
        producerTemplate.sendBody("");
        producerTemplate.sendBody("");
        producerTemplate.sendBody("");
        producerTemplate.sendBodyAndHeaders(null, createProviderJobHeaders(2L, "ref", "corr-id"));

        buildOtpGraph.assertIsSatisfied(20000);

    }

    @Test
    void testOtpGraphMessageAggregationWithException() throws Exception {

        AdviceWith.adviceWith(context, "otp-graph-build", a -> a.weaveByToUri("direct:remoteBuildOtpGraph").replace().to("mock:buildOtpGraph"));

        buildOtpGraph.whenAnyExchangeReceived(exchange -> {
            throw new RuntimeException("Test - Triggering exception in Exchange");
        });

        // expect at least one failing first exchange  + one failing local delivery + one external delivery
        buildOtpGraph.expectedMinimumMessageCount(3);

        context.start();

        producerTemplate.sendBody("");
        producerTemplate.sendBody("");
        producerTemplate.sendBody("");
        producerTemplate.sendBody("");
        producerTemplate.sendBodyAndHeaders(null, createProviderJobHeaders(2L, "ref", "corr-id"));

        buildOtpGraph.assertIsSatisfied(20000);

    }


    private Map<String, Object> createProviderJobHeaders(Long providerId, String ref, String correlationId) {

        Map<String, Object> headers = new HashMap<>();
        headers.put(Constants.PROVIDER_ID, providerId);
        headers.put(Constants.CHOUETTE_REFERENTIAL, ref);
        headers.put(Constants.CORRELATION_ID, correlationId);

        return headers;
    }
}
