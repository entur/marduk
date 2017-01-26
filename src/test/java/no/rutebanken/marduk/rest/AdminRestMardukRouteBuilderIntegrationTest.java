package no.rutebanken.marduk.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.Assert;
import org.junit.Before;
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

import static no.rutebanken.marduk.Constants.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(CamelSpringRunner.class)
@SpringBootTest(classes = AdminRestRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({"default", "in-memory-blobstore"})
@UseAdviceWith
@ContextConfiguration
public class AdminRestMardukRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	ModelCamelContext camelContext;

	@Autowired
	private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

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

	@Produce(uri = "http4:localhost:28080/admin/services/chouette/2/files/existing_regtopp-file.zip")
	protected ProducerTemplate getFileTemplate;

	@Produce(uri = "http4:localhost:28080/admin/services/chouette/2/files/unknown-file.zip")
	protected ProducerTemplate getUnknownFileTemplate;


	@Value("${nabu.rest.service.url}")
	private String nabuUrl;

	@Before
	public void setUpProvider() {
		when(providerRepository.getReferential(2L)).thenReturn("rut");
	}

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
		assertEquals(BLOBSTORE_PATH_INBOUND + "rut/file1", s3FileHandle);
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
		String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
		String pathname = "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";

		//populate fake blob repo
		inMemoryBlobStoreRepository.uploadBlob(fileStorePath + filename, new FileInputStream(new File(pathname)), false);
//		BlobStoreFiles blobStoreFiles = inMemoryBlobStoreRepository.listBlobs(fileStorePath);

		camelContext.start();

		// Do rest call
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "GET");
		InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
		// Parse response

		String s = new String(IOUtils.toByteArray(response));

		ObjectMapper mapper = new ObjectMapper();
		BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
		Assert.assertEquals(1, rsp.getFiles().size());
		Assert.assertEquals(fileStorePath + filename, rsp.getFiles().get(0).getName());

	}


	@Test
	public void getBlobStoreFile() throws Exception {
		// Preparations
		String filename = "existing_regtopp-file.zip";
		String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
		String pathname = "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";
		FileInputStream testFileStream = new FileInputStream(new File(pathname));
		//populate fake blob repo
		inMemoryBlobStoreRepository.uploadBlob(fileStorePath + filename, testFileStream, false);


		camelContext.start();

		// Do rest call
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "GET");
		InputStream response = (InputStream) getFileTemplate.requestBodyAndHeaders(null, headers);
		// Parse response

		Assert.assertTrue(org.apache.commons.io.IOUtils.contentEquals(testFileStream, response));
	}


	@Test(expected = CamelExecutionException.class)
	public void getBlobStoreFile_unknownFile() throws Exception {

		camelContext.start();

		// Do rest call
		Map<String, Object> headers = new HashMap<String, Object>();
		headers.put(Exchange.HTTP_METHOD, "GET");
		getUnknownFileTemplate.requestBodyAndHeaders(null, headers);
	}

}