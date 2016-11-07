package no.rutebanken.marduk.routes.chouette;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
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
@SpringBootTest(classes = ChouetteExportRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith
public class ChouetteExportFileMardukRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:chouetteCreateExport")
	protected MockEndpoint chouetteCreateExport;

	@EndpointInject(uri = "mock:pollJobStatus")
	protected MockEndpoint pollJobStatus;

	@EndpointInject(uri = "mock:processExportResult")
	protected MockEndpoint processExportResult;

	@EndpointInject(uri = "mock:updateStatus")
	protected MockEndpoint updateStatus;
	
	@EndpointInject(uri = "mock:chouetteGetData")
	protected MockEndpoint chouetteGetData;

	@EndpointInject(uri = "mock:OtpGraphQueue")
	protected MockEndpoint otpGraphQueue;



	@Produce(uri = "activemq:queue:ChouetteExportQueue")
	protected ProducerTemplate importTemplate;

	@Produce(uri = "direct:processExportResult")
	protected ProducerTemplate processExportResultTemplate;

	@Value("${chouette.url}")
	private String chouetteUrl;

	@Value("${nabu.rest.service.url}")
	private String nabuUrl;


	@Test
	public void testExportDataspace() throws Exception {

		// Mock initial call to Chouette to import job
		context.getRouteDefinition("chouette-send-export-job").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl + "/chouette_iev/referentials/rut/exporter/gtfs")
						.skipSendToOriginalEndpoint().to("mock:chouetteCreateExport");
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
		context.getRouteDefinition("chouette-process-export-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint()
				.to("mock:updateStatus");
				interceptSendToEndpoint("activemq:queue:OtpGraphQueue").skipSendToOriginalEndpoint().to("mock:OtpGraphQueue");

			}
		});
		
		context.getRouteDefinition("chouette-get-job-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl+ "/chouette_iev/referentials/rut/jobs/1/data")
						.skipSendToOriginalEndpoint().to("mock:chouetteGetData");
				interceptSendToEndpoint("activemq:queue:OtpGraphQueue").skipSendToOriginalEndpoint().to("mock:OtpGraphQueue");

			}
		});

		
		chouetteGetData.expectedMessageCount(1);
		chouetteGetData.returnReplyBody(new Expression() {

			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					// Should be GTFS contnet
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

	
		pollJobStatus.expectedMessageCount(1);
		
		
		updateStatus.expectedMessageCount(1);
		
		otpGraphQueue.expectedMessageCount(1);
		
		
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Constants.PROVIDER_ID, "2");
	
		importTemplate.sendBodyAndHeaders(null, headers);

		chouetteCreateExport.assertIsSatisfied();
		pollJobStatus.assertIsSatisfied();
		
		Exchange exchange = pollJobStatus.getReceivedExchanges().get(0);
		exchange.getIn().setHeader("action_report_result", "OK");
		exchange.getIn().setHeader("data_url", chouetteUrl+ "/chouette_iev/referentials/rut/jobs/1/data");
		processExportResultTemplate.send(exchange );
		
		chouetteGetData.assertIsSatisfied();
		updateStatus.assertIsSatisfied();
		otpGraphQueue.assertIsSatisfied();
		
	}

}