package no.rutebanken.marduk.routes.chouette;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@SpringBootTest(classes = ChouetteTransferToDataspaceRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith
public class ChouetteTransferToDataspaceMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:chouetteCreateExport")
	protected MockEndpoint chouetteCreateExport;

	@EndpointInject(uri = "mock:chouetteGetData")
	protected MockEndpoint chouetteGetData;

	@EndpointInject(uri = "mock:chouetteCreateImport")
	protected MockEndpoint chouetteCreateImport;

	@EndpointInject(uri = "mock:pollJobStatus")
	protected MockEndpoint pollJobStatus;

	@EndpointInject(uri = "mock:checkScheduledJobsBeforeTriggeringNextAction")
	protected MockEndpoint checkScheduledJobsBeforeTriggeringNextAction;

	@EndpointInject(uri = "mock:updateStatus")
	protected MockEndpoint updateStatus;

	@Produce(uri = "activemq:queue:ChouetteTransferExportQueue")
	protected ProducerTemplate transferTemplate;

	@Produce(uri = "direct:processTransferImportResult")
	protected ProducerTemplate processTransferImportResultTemplate;

	@Produce(uri = "direct:processTransferExportResult")
	protected ProducerTemplate processTransferExportResultTemplate;

	
	@Value("${chouette.url}")
	private String chouetteUrl;

	@Test
	public void testTransferDataToDataspaceDataspace() throws Exception {

		// Mock initial call to Chouette to export job
		context.getRouteDefinition("chouette-send-transfer-export-job").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/exporter/neptune")
					.skipSendToOriginalEndpoint().to("mock:chouetteCreateExport");
				
				interceptSendToEndpoint("activemq:queue:ChouettePollStatusQueue")
					.skipSendToOriginalEndpoint().to("mock:pollJobStatus");

				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
				.to("mock:updateStatus");
}
		});

		// Mock  call to Chouette to import job
		context.getRouteDefinition("chouette-send-transfer-import-job").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/data_url")
				.skipSendToOriginalEndpoint().to("mock:chouetteGetData");

				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/importer/neptune")
				.skipSendToOriginalEndpoint().to("mock:chouetteCreateImport");

				interceptSendToEndpoint("activemq:queue:ChouettePollStatusQueue")
				.skipSendToOriginalEndpoint().to("mock:pollJobStatus");

				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
				.to("mock:updateStatus");
			}
		});

		// Mock update status calls
		context.getRouteDefinition("chouette-process-transfer-import-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
				.to("mock:updateStatus");
				interceptSendToEndpoint("direct:checkScheduledJobsBeforeTriggeringGTFSExport").skipSendToOriginalEndpoint()
				.to("mock:checkScheduledJobsBeforeTriggeringNextAction");
			}
		});

		chouetteGetData.expectedMessageCount(1);
		chouetteGetData.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					// Should be Neptune content, but this will do
					return (T) IOUtils.toString(getClass()
							.getResourceAsStream("/no/rutebanken/marduk/chouette/getActionReportResponseOK.json"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});

		// we must manually start when we are done with all the advice with
		context.start();

		
		// 1 initial import call
		chouetteCreateExport.expectedMessageCount(1);
		chouetteCreateExport.returnReplyHeader("Location", new SimpleExpression(
				chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));

		// 1 export call
		chouetteCreateImport.expectedMessageCount(1);
		chouetteCreateImport.returnReplyHeader("Location", new SimpleExpression(
				chouetteUrl.replace("http4://", "http://") + "/chouette_iev/referentials/rut/scheduled_jobs/1"));

		pollJobStatus.expectedMessageCount(2);
		updateStatus.expectedMessageCount(3);
		checkScheduledJobsBeforeTriggeringNextAction.expectedMessageCount(1);
		
		
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Constants.PROVIDER_ID, "2");
		transferTemplate.sendBodyAndHeaders(null, headers);
		
		Map<String, Object> exportJobCompletedHeaders = new HashMap<String, Object>();
		exportJobCompletedHeaders.put(Constants.PROVIDER_ID, "2");
		exportJobCompletedHeaders.put("action_report_result", "OK");
		exportJobCompletedHeaders.put("data_url", chouetteUrl + "/chouette_iev/referentials/rut/data_url");
		exportJobCompletedHeaders.put(Constants.FILE_HANDLE, "None");
		exportJobCompletedHeaders.put(Constants.FILE_NAME, "None");
		processTransferExportResultTemplate.sendBodyAndHeaders(null, exportJobCompletedHeaders);

		Map<String, Object> importJobCompletedHeaders = new HashMap<String, Object>();
		importJobCompletedHeaders.put(Constants.PROVIDER_ID, "2");
		importJobCompletedHeaders.put("action_report_result", "OK");
		importJobCompletedHeaders.put("validation_report_result", "OK");
		importJobCompletedHeaders.put(Constants.FILE_HANDLE, "None");
		importJobCompletedHeaders.put(Constants.FILE_NAME, "None");
		importJobCompletedHeaders.put(Constants.CORRELATION_ID, "None");
		processTransferImportResultTemplate.sendBodyAndHeaders(null, importJobCompletedHeaders);

		chouetteCreateImport.assertIsSatisfied();
		chouetteCreateExport.assertIsSatisfied();
		pollJobStatus.assertIsSatisfied();
		checkScheduledJobsBeforeTriggeringNextAction.assertIsSatisfied();
		updateStatus.assertIsSatisfied();
		
		
	}



}