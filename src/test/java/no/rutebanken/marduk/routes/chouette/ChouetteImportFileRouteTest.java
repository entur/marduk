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
	
	@EndpointInject(uri = "mock:chouetteCreateExport")
	protected MockEndpoint chouetteCreateExport;
	
	@EndpointInject(uri = "mock:chouetteGetJobStatus")
	protected MockEndpoint chouetteGetJobStatus;
	
	@EndpointInject(uri = "mock:chouetteGetActionReport")
	protected MockEndpoint chouetteGetActionReport;
	
	@EndpointInject(uri = "mock:chouetteGetValidationReport")
	protected MockEndpoint chouetteGetValidationReport;
	
	@EndpointInject(uri = "mock:processActionReportResult")
	protected MockEndpoint processActionReportResult;
	
	@Produce(uri = "activemq:queue:ProcessFileQueue")
	protected ProducerTemplate importTemplate;
	
	@Produce(uri = "direct:checkValidationReport")
	protected ProducerTemplate validationReportTemplate;
	
    @Value("${chouette.url}")
    private String chouetteUrl;

    @Value("${nabu.rest.service.url}")
    private String nabuUrl;
    
    @Value("${blobstore.filesystem.baseDirectory}")
    private String blobStoreBaseDirectory;
    
    @Value("${blobstore.containerName}")
    private String containerName;
    
    // TODO when next version of camel is available, fix tests so that they use application.properties from src/test/resources
	//@Test
	public void testImportFileToDataspace() throws Exception {

		String filename = "ruter_fake_data.zip";
		
		File blobBase = new File(blobStoreBaseDirectory);
		File containerBase = new File(blobBase,containerName);
		File refBase = new File(containerBase,"rut");
		refBase.mkdirs();
		
		File importFile  = new File(refBase,filename);
		if(!importFile.exists()) {
			FileUtil.copyFile(new File("src/main/resources/no/rutebanken/marduk/routes/chouette/EMPTY_REGTOPP.zip"), importFile);
		}
		
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
				
				interceptSendToEndpoint(chouetteUrl+"/chouette_iev/referentials/rut/data/1/validation_report.json")
				.skipSendToOriginalEndpoint()
				.to("mock:chouetteGetValidationReport");
				
		
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
					return (T) IOUtils.toString(getClass().getResourceAsStream("/no/rutebanken/marduk/chouette/getActionReportResponseOK.json"));
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
					return (T) IOUtils.toString(getClass().getResourceAsStream("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json"));
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					return null;
				}
			}
		});
	
		// Should trigger export
		chouetteCreateExport.expectedMessageCount(1);

		Map<String,Object> headers = new HashMap<String,Object>();
		headers.put(Constants.PROVIDER_ID, "2");
		headers.put(Exchange.FILE_NAME, filename);
		headers.put(Constants.CORRELATION_ID, "corr_id");
		headers.put(Constants.FILE_HANDLE,"rut/"+filename);
		importTemplate.sendBodyAndHeaders(null,headers);
		

		chouetteCreateImport.assertIsSatisfied();
		chouetteGetJobStatus.assertIsSatisfied();
		chouetteGetActionReport.assertIsSatisfied();
		chouetteGetValidationReport.assertIsSatisfied();
		chouetteCreateExport.assertIsSatisfied();
	}
	
	
	@Test
	public void testValidationReportResultOK() throws Exception {
		testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseOK.json","OK");
	}	
	
	@Test
	public void testValidationReportResultNOK() throws Exception {
		testValidationReportResult("/no/rutebanken/marduk/chouette/getValidationReportResponseNOK.json","NOK");
	}	
	
	public void testValidationReportResult(String validationReportClasspathReference, String expectedResult) throws Exception {

		context.getRouteDefinition("chouette-process-validation-report").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("direct:processActionReportResult")
					.skipSendToOriginalEndpoint()
					.to("mock:processActionReportResult");
			}
		});

		context.start();

		validationReportTemplate.sendBody(getClass().getResourceAsStream(validationReportClasspathReference));

		processActionReportResult.expectedMessageCount(1);
		processActionReportResult.expectedPropertyReceived("validation_report_result", expectedResult);
		
		processActionReportResult.assertIsSatisfied();

}
	

}