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

package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.geocoder.routes.tiamat.xml.ExportJob;
import no.rutebanken.marduk.geocoder.routes.tiamat.xml.JobStatus;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.apache.camel.builder.Builder.constant;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TiamatPollJobStatusRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class TiamatPollJobStatusRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Autowired
    private ModelCamelContext context;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @EndpointInject(uri = "mock:tiamat")
    protected MockEndpoint tiamatMock;


    @EndpointInject(uri = "mock:complete")
    protected MockEndpoint completeEndpointMock;

    @EndpointInject(uri = "mock:statusQueue")
    protected MockEndpoint statusQueueMock;


    @Produce(uri = "direct:checkTiamatJobStatus")
    protected ProducerTemplate checkTiamatJobStatusTemplate;

    private static String JOB_URL = "/job/1234";

    @Before
    public void setUp() {
        completeEndpointMock.reset();
        statusQueueMock.reset();
        tiamatMock.reset();
        try {
            context.getRouteDefinition("tiamat-get-job-status").adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    interceptSendToEndpoint(tiamatUrl + JOB_URL + "/status")
                            .skipSendToOriginalEndpoint().to("mock:tiamat");
                }
            });
            context.getRouteDefinition("tiamat-process-job-status-done").adviceWith(context, new AdviceWithRouteBuilder() {
                @Override
                public void configure() throws Exception {
                    interceptSendToEndpoint("direct:updateStatus")
                            .skipSendToOriginalEndpoint().to("mock:statusQueue");
                }
            });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testInProgress() throws Exception {
        tiamatMock.expectedMessageCount(1);
        tiamatMock.returnReplyBody(constant(new ExportJob(JobStatus.PROCESSING)));
        context.start();

        Exchange e = checkStatus();

        tiamatMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        completeEndpointMock.assertIsSatisfied();
        Assert.assertTrue(e.getProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK, Boolean.class));
    }

    @Test
    public void testCompleted() throws Exception {
        tiamatMock.expectedMessageCount(1);
        completeEndpointMock.expectedMessageCount(1);

        tiamatMock.returnReplyBody(constant(new ExportJob(JobStatus.FINISHED)));

        context.start();

        Exchange e = checkStatus();

        tiamatMock.assertIsSatisfied();
        completeEndpointMock.assertIsSatisfied();
        Assert.assertNull(e.getProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK));
        Assert.assertNull(e.getException());
    }

    @Test
    public void testFailed() throws Exception {
        tiamatMock.expectedMessageCount(1);
        statusQueueMock
                .whenExchangeReceived(1, e -> Assert.assertTrue(e.getIn().getBody(String.class).contains(JobEvent.State.FAILED.toString())));
        tiamatMock.returnReplyBody(constant(new ExportJob(JobStatus.FAILED)));

        context.start();

        Exchange e = checkStatus();

        tiamatMock.assertIsSatisfied();
        completeEndpointMock.assertIsSatisfied();
        statusQueueMock.assertIsSatisfied();
        Assert.assertNull(e.getProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK));
        Assert.assertNull(e.getException());
    }


    private Exchange checkStatus() {
        Exchange exchange = checkTiamatJobStatusTemplate.request("direct:checkTiamatJobStatus", e -> {
            e.getIn().setHeader(Constants.JOB_URL, JOB_URL);
            e.getIn().setHeader(Constants.JOB_ID, "1");
            e.getIn().setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, "mock:complete");
            JobEvent jobEventEvent = status(e, JobEvent.State.STARTED);

            e.getIn().setHeader(Constants.SYSTEM_STATUS, jobEventEvent.toString());

            e.getIn().setHeader(GeoCoderConstants.GEOCODER_CURRENT_TASK, GeoCoderConstants.TIAMAT_EXPORT_POLL);
        });
        return exchange;
    }

    private JobEvent status(Exchange e, JobEvent.State state) {
        return JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE).correlationId("corrId").state(state).build();
    }
}
