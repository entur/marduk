package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.GeoCoderConstants;
import no.rutebanken.marduk.geocoder.routes.tiamat.xml.ExportJob;
import no.rutebanken.marduk.geocoder.routes.tiamat.xml.JobStatus;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.apache.camel.builder.Builder.constant;

@RunWith(CamelSpringRunner.class)
@SpringBootTest(classes = TiamatPollJobStatusRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({"default", "in-memory-blobstore"})
@UseAdviceWith
public class TiamatPollJobStatusRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	@EndpointInject(uri = "mock:tiamat")
	protected MockEndpoint tiamatMock;


	@EndpointInject(uri = "mock:complete")
	protected MockEndpoint completeEndpointMock;

	@EndpointInject(uri = "mock:systemStatus")
	protected MockEndpoint systemStatusQueueMock;


	@Produce(uri = "direct:checkTiamatJobStatus")
	protected ProducerTemplate checkTiamatJobStatusTemplate;

	@Value("${tiamat.max.retries:3000}")
	private int maxRetries;

	@Value("${tiamat.retry.delay:15000}")
	private long retryDelay;

	private static String JOB_STATUS_URL = "job/status/url";

	@Before
	public void setUp() {
		try {
			context.getRouteDefinition("tiamat-get-job-status").adviceWith(context, new AdviceWithRouteBuilder() {
				@Override
				public void configure() throws Exception {
					interceptSendToEndpoint(tiamatUrl + "/" + JOB_STATUS_URL)
							.skipSendToOriginalEndpoint().to("mock:tiamat");
				}
			});
			context.getRouteDefinition("tiamat-process-job-status-done").adviceWith(context, new AdviceWithRouteBuilder() {
				@Override
				public void configure() throws Exception {
					interceptSendToEndpoint("direct:updateSystemStatus")
							.skipSendToOriginalEndpoint().to("mock:systemStatus");
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
		systemStatusQueueMock.assertIsSatisfied();
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
		systemStatusQueueMock
				.whenExchangeReceived(1, e -> Assert.assertTrue(e.getIn().getBody(String.class).contains(SystemStatus.State.FAILED.toString())));
		tiamatMock.returnReplyBody(constant(new ExportJob(JobStatus.FAILED)));

		context.start();

		Exchange e = checkStatus();

		tiamatMock.assertIsSatisfied();
		completeEndpointMock.assertIsSatisfied();
		systemStatusQueueMock.assertIsSatisfied();
		Assert.assertNull(e.getProperty(GeoCoderConstants.GEOCODER_RESCHEDULE_TASK));
		Assert.assertNull(e.getException());
	}


	private Exchange checkStatus() {
		Exchange exchange = checkTiamatJobStatusTemplate.request("direct:checkTiamatJobStatus", e -> {
			e.getIn().setHeader(Constants.JOB_STATUS_URL, JOB_STATUS_URL);
			e.getIn().setHeader(Constants.JOB_ID, "1");
			e.getIn().setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, "mock:complete");
			SystemStatus systemStatus = status(SystemStatus.State.STARTED);
			e.getIn().setHeader(Constants.SYSTEM_STATUS_CORRELATION_ID, systemStatus.getCorrelationId());
			e.getIn().setHeader(Constants.SYSTEM_STATUS_ACTION, systemStatus.getAction());
			e.getIn().setHeader(Constants.SYSTEM_STATUS_ENTITY, systemStatus.getEntity());
			e.getIn().setHeader(GeoCoderConstants.GEOCODER_CURRENT_TASK, GeoCoderConstants.TIAMAT_EXPORT_POLL);
		});
		return exchange;
	}

	private SystemStatus status(SystemStatus.State state) {
		return SystemStatus.builder().action(SystemStatus.Action.EXPORT).correlationId("corrId").entity(SystemStatus.Entity.POI).action(SystemStatus.Action.EXPORT).state(state).build();
	}
}
