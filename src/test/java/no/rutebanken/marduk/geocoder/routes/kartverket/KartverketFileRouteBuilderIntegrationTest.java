package no.rutebanken.marduk.geocoder.routes.kartverket;

import com.amazonaws.util.StringInputStream;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.geocoder.services.KartverketService;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,classes = KartverketFileRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class KartverketFileRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {
	@Autowired
	private ModelCamelContext context;

	@Autowired
	private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;


	@Autowired
	private IdempotentRepository idempotentDownloadRepository;

	@MockBean
	public KartverketService kartverketService;

	@Produce(uri = "direct:uploadUpdatedFiles")
	protected ProducerTemplate uploadUpdatedFilesTemplate;

	private String blobFolder = "blobTest";

	@Test
	public void testNewFilesAreUploadedToBlobStore() throws Exception {
		idempotentDownloadRepository.clear();
		String[] fileNames=new String[]{"1","2","3"};
		when(kartverketService.downloadFiles(anyString(), anyString(), anyString())).thenReturn(files(fileNames));

		context.start();
		startUpdate(blobFolder, true);

		assertBlobs(blobFolder,fileNames);

		// Verify that second invocation does nothing if content is unchanged
		startUpdate(blobFolder, false);


		String otherBlobFolder = "otherBlobTest";
		startUpdate(otherBlobFolder, true);

		assertBlobs(otherBlobFolder,fileNames);
	}

	private void assertBlobs(String path, String... objectNames) {
		Arrays.stream(objectNames).forEach(o -> Assert.assertNotNull(inMemoryBlobStoreRepository.getBlob(path + "/" + o)));
	}

	@Test
	public void testNoLongerActiveFilesAreDeletedFromBlobStore() throws Exception {
		idempotentDownloadRepository.clear();
		inMemoryBlobStoreRepository.uploadBlob(blobFolder + "/1", new StringInputStream("1"), false);
		inMemoryBlobStoreRepository.uploadBlob(blobFolder + "/2", new StringInputStream("2"), false);
		inMemoryBlobStoreRepository.uploadBlob(blobFolder + "/3", new StringInputStream("3"), false);
		when(kartverketService.downloadFiles(anyString(), anyString(), anyString())).thenReturn(files("1"));

		context.start();
		startUpdate(blobFolder, true);

		assertBlobs(blobFolder,"1");
		Assert.assertNull(inMemoryBlobStoreRepository.getBlob(blobFolder + "/2"));
		Assert.assertNull(inMemoryBlobStoreRepository.getBlob(blobFolder + "/3"));
	}


	private void startUpdate(String blobFolder, boolean shouldChangeContent) {
		Exchange exchange = uploadUpdatedFilesTemplate.request("direct:uploadUpdatedFiles", e -> e.getIn().setHeader(Constants.FOLDER_NAME, blobFolder));
		Assert.assertEquals(shouldChangeContent, Boolean.TRUE.equals(exchange.getIn().getHeader(Constants.CONTENT_CHANGED)));
	}

	private List<File> files(String... names) throws Exception {
		List<File> files = Arrays.stream(names).map(n -> new File("target/files/" + n)).collect(Collectors.toList());
		for (File file : files) {
			FileUtils.writeStringToFile(file, file.getName());
		}
		return files;
	}


}
