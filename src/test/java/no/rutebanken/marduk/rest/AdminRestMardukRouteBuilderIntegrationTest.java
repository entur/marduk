package no.rutebanken.marduk.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.Assert.assertEquals;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@SpringBootTest(classes = AdminRestRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith
@ContextConfiguration
public class AdminRestMardukRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	ModelCamelContext camelContext;

	@EndpointInject(uri = "mock:chouetteImportQueue")
	protected MockEndpoint importQueue;

	@EndpointInject(uri = "mock:chouetteExportQueue")
	protected MockEndpoint exportQueue;

	@Produce(uri = "http4:localhost:28080/admin/services/chouette/2/import")
	protected ProducerTemplate importTemplate;

	@Produce(uri = "http4:localhost:28080/admin/services/chouette/2/export")
	protected ProducerTemplate exportTemplate;

	@Produce(uri = "http4:localhost:28080/admin/services/chouette/2/files")
	protected ProducerTemplate listFilesTemplate;

	@Value("${nabu.rest.service.url}")
	private String nabuUrl;

	@Value("${blobstore.filesystem.baseDirectory}")
	private String blobStoreBaseDirectory;

	@Value("${blobstore.containerName}")
	private String containerName;

	@Test
	public void runImport() throws Exception {

		camelContext.getRouteDefinition("admin-chouette-import").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("activemq:queue:ProcessFileQueue").skipSendToOriginalEndpoint().to("mock:chouetteImportQueue");
			}
		});


		// we must manually start when we are done with all the advice with
		camelContext.start();

		S3Files d = new S3Files();
		d.add(new S3Files.File("file1", null, null));
		d.add(new S3Files.File("file2", null, null));

		ObjectMapper mapper = new ObjectMapper();
		StringWriter writer = new StringWriter();
		mapper.writeValue(writer, d);
		String importJson = writer.toString();

		// Do rest call

		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "POST");
		importTemplate.sendBodyAndHeaders(importJson, headers);

		// setup expectations on the mocks
		importQueue.expectedMessageCount(2);

		// assert that the test was okay
		importQueue.assertIsSatisfied();

		List<Exchange> exchanges = importQueue.getExchanges();
		String providerId = (String) exchanges.get(0).getIn().getHeader(PROVIDER_ID);
		assertEquals("2", providerId);
		String s3FileHandle = (String) exchanges.get(0).getIn().getHeader(FILE_HANDLE);
		assertEquals("file1", s3FileHandle);
	}

	@Test
	public void runExport() throws Exception {

		camelContext.getRouteDefinition("admin-chouette-export").adviceWith(camelContext, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("activemq:queue:ChouetteExportQueue").skipSendToOriginalEndpoint().to("mock:chouetteExportQueue");

			}
		});

		// we must manually start when we are done with all the advice with
		camelContext.start();

		// Do rest call
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "POST");
		exportTemplate.sendBodyAndHeaders(null, headers);

		// setup expectations on the mocks
		exportQueue.expectedMessageCount(1);

		// assert that the test was okay
		exportQueue.assertIsSatisfied();

		List<Exchange> exchanges = exportQueue.getExchanges();
		String providerId = (String) exchanges.get(0).getIn().getHeader(PROVIDER_ID);
		assertEquals("2", providerId);
	}

	@Test
	public void getS3Files() throws Exception {

		// Preparations
		String filename = "ruter_fake_file.zip";
		String filestorePath = Constants.BLOBSTORE_PATH_INBOUND_RECEIVED+"rut";
		File blobBase = new File(blobStoreBaseDirectory);
		File containerBase = new File(blobBase, containerName);
		File refBase = new File(containerBase, filestorePath);
		FileUtils.deleteQuietly(refBase);
		refBase.mkdirs();

		File importFile = new File(refBase, filename);
		importFile.createNewFile();

		camelContext.start();

		// Do rest call
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "GET");
		InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
		// Parse response

		ObjectMapper mapper = new ObjectMapper();
		S3Files rsp = mapper.readValue(response, S3Files.class);
		Assert.assertEquals(1, rsp.getFiles().size());
		Assert.assertEquals(filestorePath+"/"+filename, rsp.getFiles().get(0).getName());

	}

}