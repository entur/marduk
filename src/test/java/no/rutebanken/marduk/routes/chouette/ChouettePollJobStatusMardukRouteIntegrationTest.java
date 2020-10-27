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
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.commons.io.IOUtils;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,classes = TestApp.class)
class ChouettePollJobStatusMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject("mock:destination")
	protected MockEndpoint destination;

	@EndpointInject("mock:updateStatus")
	protected MockEndpoint updateStatus;

	@EndpointInject("mock:chouetteGetJobStatus")
	protected MockEndpoint chouetteGetJobStatus;

	@EndpointInject("mock:chouetteGetActionReport")
	protected MockEndpoint chouetteGetActionReport;

	@EndpointInject("mock:chouetteGetValidationReport")
	protected MockEndpoint chouetteGetValidationReport;

	@Produce("entur-google-pubsub:ChouettePollStatusQueue")
	protected ProducerTemplate pollStartTemplate;

	@Produce("direct:checkValidationReport")
	protected ProducerTemplate validationReportTemplate;

	@EndpointInject("mock:chouetteGetJobsForProvider")
	protected MockEndpoint getJobs;

	@Produce("direct:chouetteGetJobsForProvider")
	protected ProducerTemplate getJobsTemplate;

	@Value("${chouette.url}")
	private String chouetteUrl;

	@Test
	void testPollJobStatus() throws Exception {

		// Mock get status call to chouette
		AdviceWithRouteBuilder.adviceWith(context, "chouette-get-job-status", a -> {
			a.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/scheduled_jobs/1")
					.skipSendToOriginalEndpoint().to("mock:chouetteGetJobStatus");
			a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
		});

		AdviceWithRouteBuilder.adviceWith(context, "chouette-process-job-reports", a -> {
			a.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/data/1/action_report.json")
					.skipSendToOriginalEndpoint().to("mock:chouetteGetActionReport");

			a.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/data/1/validation_report.json")
					.skipSendToOriginalEndpoint().to("mock:chouetteGetValidationReport");
		});

		AdviceWithRouteBuilder.adviceWith(context, "chouette-reschedule-job", a -> a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus"));

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
								"/no/rutebanken/marduk/chouette/getJobStatusResponseStarted.json"), StandardCharsets.UTF_8);

					} else {
						return (T) IOUtils.toString(getClass().getResourceAsStream(
								"/no/rutebanken/marduk/chouette/getJobStatusResponseTerminated.json"), StandardCharsets.UTF_8);

					}
				} catch (IOException e) {
					throw new RuntimeException(e);
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
							.getResourceAsStream("/no/rutebanken/marduk/chouette/getActionReportResponseOK.json"), StandardCharsets.UTF_8);
				} catch (IOException e) {
					throw new RuntimeException(e);
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
							.getResourceAsStream("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json"), StandardCharsets.UTF_8);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});

		// Should end up here with 2 headers
		destination.expectedHeaderReceived("validation_report_result", "OK");
		destination.expectedHeaderReceived("action_report_result", "OK");
		destination.expectedMessageCount(1);

		updateStatus.expectedMessageCount(1);

		Map<String, Object> headers = new HashMap<>();
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
	void testValidationReportResultOK() throws Exception {
		testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json", "OK");
	}

	//@Test
	void testValidationReportResultNOK() throws Exception {
		testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseNOK.json", "NOK");
	}

	void testValidationReportResult(String validationReportClasspathReference, String expectedResult)
			throws Exception {

		context.start();

		validationReportTemplate.sendBodyAndHeader(getClass().getResourceAsStream(validationReportClasspathReference),
				Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION, "mock:destination");

		destination.expectedMessageCount(1);
		destination.expectedHeaderReceived("validation_report_result", expectedResult);
		destination.assertIsSatisfied();

	}
	
	@Test
	void getJobs() throws Exception {

		AdviceWithRouteBuilder.adviceWith(context, "chouette-list-jobs", a -> a.interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/jobs?addActionParameters=false")
				.skipSendToOriginalEndpoint()
				.to("mock:chouetteGetJobsForProvider"));

		getJobs.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					return (T) IOUtils.toString(getClass()
							.getResourceAsStream("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json"), StandardCharsets.UTF_8);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});

		context.start();



		
		// Do rest call
		Map<String, Object> headers = new HashMap<>();
		headers.put(Exchange.HTTP_METHOD, "GET");
		headers.put(Constants.PROVIDER_ID, "2");
		List<JobResponse> rsp =  (List<JobResponse>) getJobsTemplate.requestBodyAndHeaders(null, headers);
		// Parse response

		assertNotEquals(0, rsp.size());
	

	}


}