package no.rutebanken.marduk.rest;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.Assert.assertEquals;

import java.net.URLEncoder;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.CamelTestContextBootstrapper;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@BootstrapWith(CamelTestContextBootstrapper.class)
@ContextConfiguration(loader = CamelSpringDelegatingTestContextLoader.class, classes = CamelConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith(true)
public class AdminRestRouteBUilderTest {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:chouetteImportQueue")
	protected MockEndpoint importQueue;

	@EndpointInject(uri = "mock:chouetteExportQueue")
	protected MockEndpoint exportQueue;

	@Produce(uri = "http4:localhost:8081/admin/services/2/chouette/import?fileHandle=file/path/down/the/road")
	protected ProducerTemplate importTemplate;

	@Produce(uri = "http4:localhost:8081/admin/services/2/chouette/export")
	protected ProducerTemplate exportTemplate;

	@Test
	public void runImport() throws Exception {

		context.getRouteDefinition("admin-chouette-import").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("activemq:queue:*").skipSendToOriginalEndpoint().to("mock:chouetteImportQueue");

			}
		});

		// we must manually start when we are done with all the advice with
		context.start();

		String fileHandle = URLEncoder.encode("file/path/down/the/road", "UTF-8");

		// Do rest call
		importTemplate.sendBody(null);

		// setup expectations on the mocks
		importQueue.expectedMessageCount(1);

		// assert that the test was okay
		importQueue.assertIsSatisfied();

		List<Exchange> exchanges = importQueue.getExchanges();
		String providerId = (String) exchanges.get(0).getIn().getHeader(PROVIDER_ID);
		assertEquals("2", providerId);
		String s3FileHandle = (String) exchanges.get(0).getIn().getHeader(FILE_HANDLE);
		assertEquals(fileHandle, s3FileHandle);
	}

	

	@Test
	public void runExport() throws Exception {

		context.getRouteDefinition("admin-chouette-export").adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("activemq:queue:*").skipSendToOriginalEndpoint().to("mock:chouetteExportQueue");

			}
		});

		// we must manually start when we are done with all the advice with
		context.start();

		// Do rest call
		exportTemplate.sendBody(null);

		// setup expectations on the mocks
		exportQueue.expectedMessageCount(1);

		// assert that the test was okay
		exportQueue.assertIsSatisfied();

		List<Exchange> exchanges = exportQueue.getExchanges();
		String providerId = (String) exchanges.get(0).getIn().getHeader(PROVIDER_ID);
		assertEquals("2", providerId);
	}

}