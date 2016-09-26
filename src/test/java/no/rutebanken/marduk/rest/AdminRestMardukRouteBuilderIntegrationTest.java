package no.rutebanken.marduk.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.repository.FakeBlobStoreRepository;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.UseAdviceWith;
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
import java.io.FileInputStream;
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

	@Autowired
	private FakeBlobStoreRepository fakeBlobStoreRepository;

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

		BlobStoreFiles d = new BlobStoreFiles();
		d.add(new BlobStoreFiles.File("file1", null, null));
		d.add(new BlobStoreFiles.File("file2", null, null));

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
	public void getBlobStoreFiles() throws Exception {

		// Preparations
		String filename = "ruter_fake_data.zip";
		String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND_RECEIVED+"rut/";
		String pathname = "src/main/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";

		//populate fake blob repo
		fakeBlobStoreRepository.uploadBlob(fileStorePath + filename, new FileInputStream(new File(pathname)), false);
		BlobStoreFiles blobStoreFiles = fakeBlobStoreRepository.listBlobs(fileStorePath);

		camelContext.start();

		// Do rest call
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "GET");
		InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
		// Parse response

		ObjectMapper mapper = new ObjectMapper();
		BlobStoreFiles rsp = mapper.readValue(response, BlobStoreFiles.class);
		Assert.assertEquals(1, rsp.getFiles().size());
		Assert.assertEquals(fileStorePath + filename, rsp.getFiles().get(0).getName());

	}

}