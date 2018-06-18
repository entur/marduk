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

package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,classes = ChouettePollJobStatusRoute.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class ChouettePollJobStatusMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:destination")
	protected MockEndpoint destination;

	@EndpointInject(uri = "mock:updateStatus")
	protected MockEndpoint updateStatus;

	@EndpointInject(uri = "mock:chouetteGetJobStatus")
	protected MockEndpoint chouetteGetJobStatus;

	@EndpointInject(uri = "mock:chouetteGetActionReport")
	protected MockEndpoint chouetteGetActionReport;

	@EndpointInject(uri = "mock:chouetteGetValidationReport")
	protected MockEndpoint chouetteGetValidationReport;

	@Produce(uri = "activemq:queue:ChouettePollStatusQueue")
	protected ProducerTemplate pollStartTemplate;

	@Produce(uri = "direct:checkValidationReport")
	protected ProducerTemplate validationReportTemplate;

	@EndpointInject(uri = "mock:chouetteGetJobsForProvider")
	protected MockEndpoint getJobs;

	@Produce(uri = "direct:chouetteGetJobsForProvider")
	protected ProducerTemplate getJobsTemplate;

	@Value("${chouette.url}")
	private String chouetteUrl;

	@Test
	public void testPollJobStatus() throws Exception {

		// Mock get status call to chouette
		context.getRouteDefinition("chouette-get-job-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/scheduled_jobs/1")
						.skipSendToOriginalEndpoint().to("mock:chouetteGetJobStatus");
				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");

			}
		});

		context.getRouteDefinition("chouette-process-job-reports").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/data/1/action_report.json")
						.skipSendToOriginalEndpoint().to("mock:chouetteGetActionReport");

				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/data/1/validation_report.json")
						.skipSendToOriginalEndpoint().to("mock:chouetteGetValidationReport");

			}
		});

		context.getRouteDefinition("chouette-reschedule-job").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {

				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");

			}
		});


		// we must manually start when we are done with all the advice with
		context.start();

		// 2 status calls, first return SCHEDULED, then TERMINATED
		final AtomicInteger reportCounter = new AtomicInteger(0);
		chouetteGetJobStatus.expectedMessageCount(2);
		chouetteGetJobStatus.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					int currval = reportCounter.getAndIncrement();
					if (currval == 0) {
						return (T) IOUtils.toString(getClass().getResourceAsStream(
								"/no/rutebanken/marduk/chouette/getJobStatusResponseStarted.json"));

					} else {
						return (T) IOUtils.toString(getClass().getResourceAsStream(
								"/no/rutebanken/marduk/chouette/getJobStatusResponseTerminated.json"));

					}
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});

		// 1 aciton report call
		chouetteGetActionReport.expectedMessageCount(1);
		chouetteGetActionReport.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					return (T) IOUtils.toString(getClass()
							.getResourceAsStream("/no/rutebanken/marduk/chouette/getActionReportResponseOK.json"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});

		// 1 aciton report call
		chouetteGetValidationReport.expectedMessageCount(1);
		chouetteGetValidationReport.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					return (T) IOUtils.toString(getClass()
							.getResourceAsStream("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});

		// Should end up here with 2 headers
		destination.expectedHeaderReceived("validation_report_result", "OK");
		destination.expectedHeaderReceived("action_report_result", "OK");
		destination.expectedMessageCount(1);

		updateStatus.expectedMessageCount(1);

		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Constants.PROVIDER_ID, "2");
		headers.put(Constants.FILE_NAME, "file_name");
		headers.put(Constants.CORRELATION_ID, "corr_id");
		headers.put(Constants.FILE_HANDLE, "rut/file_name");
		headers.put(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, "mock:destination");
		headers.put(Constants.CHOUETTE_JOB_STATUS_URL, chouetteUrl + "/chouette_iev/referentials/rut/scheduled_jobs/1");
		headers.put(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, JobEvent.TimetableAction.IMPORT.name());
		pollStartTemplate.sendBodyAndHeaders(null, headers);

		chouetteGetJobStatus.assertIsSatisfied();
		chouetteGetActionReport.assertIsSatisfied();
		chouetteGetValidationReport.assertIsSatisfied();
		destination.assertIsSatisfied();
		updateStatus.assertIsSatisfied();
	}

	//@Test
	public void testValidationReportResultOK() throws Exception {
		testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json", "OK");
	}

	//@Test
	public void testValidationReportResultNOK() throws Exception {
		testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseNOK.json", "NOK");
	}

	public void testValidationReportResult(String validationReportClasspathReference, String expectedResult)
			throws Exception {

		context.start();

		validationReportTemplate.sendBodyAndHeader(getClass().getResourceAsStream(validationReportClasspathReference),
				Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, "mock:destination");

		destination.expectedMessageCount(1);
		destination.expectedHeaderReceived("validation_report_result", expectedResult);
		destination.assertIsSatisfied();

	}
	
	@Test
	public void getJobs() throws Exception {

		context.getRouteDefinition("chouette-list-jobs").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/jobs?addActionParameters=false")
				.skipSendToOriginalEndpoint()
				.to("mock:chouetteGetJobsForProvider");
			}
		});

		context.start();

		getJobs.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					return (T) IOUtils.toString(getClass()
							.getResourceAsStream("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});

		
		// Do rest call
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "GET");
		headers.put(Constants.PROVIDER_ID, "2");
		JobResponse[] rsp =  (JobResponse[]) getJobsTemplate.requestBodyAndHeaders(null, headers);
		// Parse response

		Assert.assertFalse(rsp.length == 0);
	

	}


}