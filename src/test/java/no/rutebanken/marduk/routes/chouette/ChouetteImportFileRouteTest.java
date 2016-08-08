package no.rutebanken.marduk.routes.chouette;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.CamelTestContextBootstrapper;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.camel.util.FileUtil;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.file.FileType;
import no.rutebanken.marduk.routes.status.Status.Action;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@BootstrapWith(CamelTestContextBootstrapper.class)
@ContextConfiguration(loader = CamelSpringDelegatingTestContextLoader.class, classes = CamelConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith(true)
public class ChouetteImportFileRouteTest {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:chouetteCreateImport")
	protected MockEndpoint chouetteCreateImport;

	@EndpointInject(uri = "mock:pollJobStatus")
	protected MockEndpoint pollJobStatus;

	@EndpointInject(uri = "mock:chouetteGetJobs")
	protected MockEndpoint chouetteGetJobs;

	@EndpointInject(uri = "mock:processImportResult")
	protected MockEndpoint processActionReportResult;

	@EndpointInject(uri = "mock:chouetteValidationQueue")
	protected MockEndpoint chouetteValidationQueue;

	@EndpointInject(uri = "mock:checkScheduledJobsBeforeTriggeringValidation")
	protected MockEndpoint chouetteCheckScheduledJobs;

	@EndpointInject(uri = "mock:updateStatus")
	protected MockEndpoint updateStatus;

	@Produce(uri = "activemq:queue:ProcessFileQueue")
	protected ProducerTemplate importTemplate;

	@Produce(uri = "direct:processImportResult")
	protected ProducerTemplate processImportResultTemplate;

	@Produce(uri = "direct:checkScheduledJobsBeforeTriggeringValidation")
	protected ProducerTemplate triggerJobListTemplate;

	@Value("${chouette.url}")
	private String chouetteUrl;

	@Value("${nabu.rest.service.url}")
	private String nabuUrl;

	@Value("${blobstore.filesystem.baseDirectory}")
	private String blobStoreBaseDirectory;

	@Value("${blobstore.containerName}")
	private String containerName;

	
	@Test
	public void testDummy() {}
	// TODO when next version of camel is available, fix tests so that they use
	// application.properties from src/test/resources
	// @Test
	public void testImportFileToDataspace() throws Exception {

		String filename = "ruter_fake_data.zip";

		File blobBase = new File(blobStoreBaseDirectory);
		File containerBase = new File(blobBase, containerName);
		File refBase = new File(containerBase, "rut");
		refBase.mkdirs();

		File importFile = new File(refBase, filename);
		if (!importFile.exists()) {
			FileUtil.copyFile(new File("src/main/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip"),
					importFile);
		}

		// Mock initial call to Chouette to import job
		context.getRouteDefinition("chouette-send-import-job").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/importer/regtopp")
						.skipSendToOriginalEndpoint().to("mock:chouetteCreateImport");
			}
		});

		// Mock job polling route - AFTER header validatio (to ensure that we send correct headers in test as well
		context.getRouteDefinition("chouette-validate-job-status-parameters").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:checkJobStatus").skipSendToOriginalEndpoint()
				.to("mock:pollJobStatus");
			}
		});

		// Mock update status calls
		context.getRouteDefinition("chouette-process-import-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
				.to("mock:updateStatus");
				interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringValidation").skipSendToOriginalEndpoint()
				.to("mock:checkScheduledJobsBeforeTriggeringValidation");
			}
		});

		// Mock Nabu / providerRepository (done differently since RestTemplate
		// is being used which skips Camel)
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("netty4-http:" + nabuUrl + "/providers/2").setBody()
						.constant(IOUtils.toString(new FileReader(
								"src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")))
						.setHeader(Exchange.CONTENT_TYPE, constant("application/json"));
			}

		});

		// we must manually start when we are done with all the advice with
		context.start();

		// 1 initial import call
		chouetteCreateImport.expectedMessageCount(1);
		chouetteCreateImport.returnReplyHeader("Location", new SimpleExpression(
				chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));

	
		pollJobStatus.expectedMessageCount(1);
		
		
		updateStatus.expectedMessageCount(3);
		chouetteCheckScheduledJobs.expectedMessageCount(1);
		
		
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Constants.PROVIDER_ID, "2");
		headers.put(Constants.FILE_NAME, filename);
		headers.put(Constants.CORRELATION_ID, "corr_id");
		headers.put(Constants.FILE_HANDLE, "rut/" + filename);
		importTemplate.sendBodyAndHeaders(null, headers);

		chouetteCreateImport.assertIsSatisfied();
		pollJobStatus.assertIsSatisfied();
		
		Exchange exchange = pollJobStatus.getReceivedExchanges().get(0);
		exchange.getIn().setHeader("action_report_result", "OK");
		exchange.getIn().setHeader("validation_report_result", "OK");
		processImportResultTemplate.send(exchange );
		
		chouetteCheckScheduledJobs.assertIsSatisfied();
		updateStatus.assertIsSatisfied();
		
		
	}


	//@Test
	public void testJobListResponseTerminated() throws Exception {
		testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseAllTerminated.json", true);
	}

	//@Test
	public void testJobListResponseScheduled() throws Exception {
		testJobListResponse("/no/rutebanken/marduk/chouette/getJobListResponseScheduled.json", false);
	}

	public void testJobListResponse(String jobListResponseClasspathReference, boolean expectExport) throws Exception {

		context.getRouteDefinition("chouette-process-job-list-after-import").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/jobs?action=importer")
						.skipSendToOriginalEndpoint()
						.to("mock:chouetteGetJobs");
				interceptSendToEndpoint("activemq:queue:ChouetteValidationQueue")
					.skipSendToOriginalEndpoint()
					.to("mock:chouetteValidationQueue");
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

		triggerJobListTemplate.sendBodyAndHeader(null, Constants.CHOUETTE_REFERENTIAL, "rut");
		
		chouetteGetJobs.assertIsSatisfied();

		if (expectExport) {
			chouetteValidationQueue.expectedMessageCount(1);
		}
		chouetteValidationQueue.assertIsSatisfied();

	}

}