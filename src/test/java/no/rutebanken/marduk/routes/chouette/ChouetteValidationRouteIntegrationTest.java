package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RunWith(CamelSpringRunner.class)
@SpringBootTest(classes = ChouetteValidationRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "in-memory-blobstore" })
@UseAdviceWith
public class ChouetteValidationRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:chouetteCreateValidation")
	protected MockEndpoint chouetteCreateValidation;

	@EndpointInject(uri = "mock:pollJobStatus")
	protected MockEndpoint pollJobStatus;

	@EndpointInject(uri = "mock:chouetteGetJobsForProvider")
	protected MockEndpoint chouetteGetJobs;

	@EndpointInject(uri = "mock:processValidationResult")
	protected MockEndpoint processValidationResult;

	@EndpointInject(uri = "mock:chouetteExportQueue")
	protected MockEndpoint chouetteExportQueue;

	@EndpointInject(uri = "mock:checkScheduledJobsBeforeTriggeringExport")
	protected MockEndpoint chouetteCheckScheduledJobs;

	@EndpointInject(uri = "mock:updateStatus")
	protected MockEndpoint updateStatus;

	@Produce(uri = "activemq:queue:ChouetteValidationQueue")
	protected ProducerTemplate validationTemplate;

	@Produce(uri = "direct:processValidationResult")
	protected ProducerTemplate processValidationResultTemplate;

	@Produce(uri = "direct:checkScheduledJobsBeforeTriggeringExport")
	protected ProducerTemplate triggerJobListTemplate;

	@Value("${chouette.url}")
	private String chouetteUrl;

	@Value("${nabu.rest.service.url}")
	private String nabuUrl;

	@Test
	public void testRunChouetteValidation() throws Exception {

		// Mock initial call to Chouette to validation job
		context.getRouteDefinition("chouette-send-validation-job").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/validator")
						.skipSendToOriginalEndpoint().to("mock:chouetteCreateValidation");
				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
						.to("mock:updateStatus");
			}
		});

		// Mock job polling route - AFTER header validatio (to ensure that we send correct headers in test as well)
		context.getRouteDefinition("chouette-validate-job-status-parameters").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:checkJobStatus").skipSendToOriginalEndpoint()
				.to("mock:pollJobStatus");
			}
		});

		// Mock update status calls
		context.getRouteDefinition("chouette-process-validation-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
				.to("mock:updateStatus");
				interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringExport").skipSendToOriginalEndpoint()
				.to("mock:checkScheduledJobsBeforeTriggeringExport");
			}
		});

		// we must manually start when we are done with all the advice with
		context.start();

		// 1 initial import call
		chouetteCreateValidation.expectedMessageCount(1);
		chouetteCreateValidation.returnReplyHeader("Location", new SimpleExpression(
				chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));

	
		pollJobStatus.expectedMessageCount(1);
		
		
		updateStatus.expectedMessageCount(2);
		chouetteCheckScheduledJobs.expectedMessageCount(1);
		
		
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Constants.PROVIDER_ID, "2");
		headers.put(Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, Status.Action.VALIDATION_LEVEL_2.toString());
		validationTemplate.sendBodyAndHeaders(null, headers);

		chouetteCreateValidation.assertIsSatisfied();
		pollJobStatus.assertIsSatisfied();
		
		Exchange exchange = pollJobStatus.getReceivedExchanges().get(0);
		exchange.getIn().setHeader("action_report_result", "OK");
		exchange.getIn().setHeader("validation_report_result", "OK");
		processValidationResultTemplate.send(exchange );
		
		chouetteCheckScheduledJobs.assertIsSatisfied();
		updateStatus.assertIsSatisfied();
		
		
	}


	@Test
	public void testJobListResponseTerminated() throws Exception {
		testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseAllTerminated.json", true);
	}

	@Test
	public void testJobListResponseScheduled() throws Exception {
		testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json", false);
	}

	public void testJobListResponse(String jobListResponseClasspathReference, boolean expectExport) throws Exception {

		context.getRouteDefinition("chouette-process-job-list-after-validation").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/*")
						.skipSendToOriginalEndpoint()
						.to("mock:chouetteGetJobsForProvider");
				interceptSendToEndpoint("activemq:queue:ChouetteTransferExportQueue")
					.skipSendToOriginalEndpoint()
					.to("mock:chouetteExportQueue");
			}
		});

		context.start();

		// 1 call to list other import jobs in referential
		chouetteGetJobs.expectedMessageCount(1);
		chouetteGetJobs.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					return (T) IOUtils.toString(getClass().getResourceAsStream(jobListResponseClasspathReference));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});

		Map<String, Object> headers = new HashMap<String,Object>();
		headers.put(Constants.CHOUETTE_REFERENTIAL, "rut");
		headers.put(Constants.PROVIDER_ID,2);
		
		triggerJobListTemplate.sendBodyAndHeaders(null,headers);
		
		chouetteGetJobs.assertIsSatisfied();

		if (expectExport) {
			chouetteExportQueue.expectedMessageCount(1);
		}
		chouetteExportQueue.assertIsSatisfied();

	}

}