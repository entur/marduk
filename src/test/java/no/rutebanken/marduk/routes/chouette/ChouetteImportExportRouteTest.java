package no.rutebanken.marduk.routes.chouette;

import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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

@RunWith(CamelSpringJUnit4ClassRunner.class)
@BootstrapWith(CamelTestContextBootstrapper.class)
@ContextConfiguration(loader = CamelSpringDelegatingTestContextLoader.class, classes = CamelConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith(true)
public class ChouetteImportExportRouteTest {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:chouetteCreateImport")
	protected MockEndpoint chouetteCreateImport;
	
	@EndpointInject(uri = "mock:chouetteCreateExport")
	protected MockEndpoint chouetteCreateExport;
	
	@EndpointInject(uri = "mock:chouetteGetJobStatus")
	protected MockEndpoint chouetteGetJobStatus;
	
	@EndpointInject(uri = "mock:chouetteGetActionReport")
	protected MockEndpoint chouetteGetActionReport;
	
	@Produce(uri = "activemq:queue:ChouetteCleanQueue")
	protected ProducerTemplate importTemplate;
	
    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${nabu.rest.service.url}")
    private String nabuUrl;
    
    @Test
    public void dummu() {}
    
	//@Test
	public void testCleanDataspace() throws Exception {

		// Mock initial call to Chouette to import job
		context.getRouteDefinition("chouette-send-import-job").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl+"/chouette_iev/referentials/rut/importer/regtopp")
					.skipSendToOriginalEndpoint()
					.to("mock:chouetteCreateImport");
			}
		});

		// Mock get status call to chouette
		context.getRouteDefinition("chouette-get-job-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl+"/chouette_iev/referentials/rut/scheduled_jobs/1")
				.skipSendToOriginalEndpoint()
				.to("mock:chouetteGetJobStatus");
			}
		});

		context.getRouteDefinition("chouette-process-action-report").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint(chouetteUrl+"/chouette_iev/referentials/rut/data/1/action_report.json")
				.skipSendToOriginalEndpoint()
				.to("mock:chouetteGetActionReport");
				
		
			}
		});

		context.getRouteDefinition("chouette-process-job-status").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("activemq:queue:ChouetteExportQueue")
					.skipSendToOriginalEndpoint()
					.to("mock:chouetteCreateExport");
			}
		});

		// Mock Nabu / providerRepository (done differently since RestTemplate is being used which skips Camel)
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("netty4-http:"+nabuUrl+"/providers/2")
					.setBody()
					.constant(IOUtils.toString(new FileReader("src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")))
					.setHeader(Exchange.CONTENT_TYPE,constant("application/json"));
					
			}
			
		});	
		
		

		// we must manually start when we are done with all the advice with
		context.start();

		// 1 initial import call
		chouetteCreateImport.expectedMessageCount(1);
		chouetteCreateImport.returnReplyHeader("Location", new SimpleExpression(chouetteUrl.replace("http4://","http://")+"/chouette_iev/referentials/rut/scheduled_jobs/1"));
		
		
		// 1 status call
		chouetteGetJobStatus.expectedMessageCount(1);
		chouetteGetJobStatus.returnReplyBody(new Expression() {
			
			@SuppressWarnings("unchecked")
			@Override
			public <T> T evaluate(Exchange ex, Class<T> arg1) {
				try {
					return (T) IOUtils.toString(getClass().getResourceAsStream("/no/rutebanken/marduk/chouette/getJobStatusResponse.json"));
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
					return (T) IOUtils.toString(getClass().getResourceAsStream("/no/rutebanken/marduk/chouette/getActionReportResponse.json"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});
	
		// Should not trigger export
		chouetteCreateExport.expectedMessageCount(0);

		Map<String,Object> headers = new HashMap<String,Object>();
		headers.put(Constants.PROVIDER_ID, "2");
		importTemplate.sendBodyAndHeaders(null,headers);
		

		chouetteCreateImport.assertIsSatisfied();
		chouetteGetJobStatus.assertIsSatisfied();
		chouetteGetActionReport.assertIsSatisfied();
		chouetteCreateExport.assertIsSatisfied();
	}

}