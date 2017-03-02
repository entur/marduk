package no.rutebanken.marduk.routes.nri;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.services.IdempotentRepositoryService;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(CamelSpringRunner.class)
@SpringBootTest(classes = NRIFtpReceiverRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "in-memory-blobstore" })
@UseAdviceWith
public class NRIFtpReceiverRouteTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@Autowired
	IdempotentRepositoryService idempotentRepositoryService;

	@EndpointInject(uri = "mock:buskerud")
	protected MockEndpoint buskerud;
	
    @Value("${nabu.rest.service.url}")
    private String nabuUrl;

	@Before
	public void setUpProvider() throws IOException {
		//wipe idempotent stores
		idempotentRepositoryService.cleanUniqueFileNameAndDigestRepo();
		when(providerRepository.getProvider(5L)).thenReturn(Provider.create(IOUtils.toString(new FileReader(
				"src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json"))));
	}

	@Test
	public void testFetchFilesFromFTP() throws Exception {

		FakeFtpServer fakeFtpServer = new FakeFtpServer();
		fakeFtpServer.setSystemName("UNIX");

		fakeFtpServer.addUserAccount(new UserAccount("username", "password", "/"));

		FileSystem fileSystem = new UnixFakeFileSystem();
		fileSystem.add(new DirectoryEntry("/rutedata"));
		fileSystem.add(new DirectoryEntry("/rutedata/Brakar (Buskerud fylke)"));
		fileSystem.add(new DirectoryEntry("/rutedata/Brakar (Buskerud fylke)/1585"));
		fileSystem.add(new DirectoryEntry("/rutedata/Brakar (Buskerud fylke)/1585/Hovedsett 2016_unzipped"));
		fileSystem.add(new DirectoryEntry("/rutedata/Brakar (Buskerud fylke)/985"));
		fileSystem.add(new DirectoryEntry("/rutedata/Brakar (Buskerud fylke)/984"));
		fileSystem.add(new DirectoryEntry("/rutedata/Brakar (Buskerud fylke)/985/Hovedsett 2016_unzipped"));
		FileEntry file1585 = new FileEntry("/rutedata/Brakar (Buskerud fylke)/1585/Hovedsett 2017.zip");
		file1585.setContents(new byte[]{12,11,10});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
		fileSystem.add(file1585);
		FileEntry file984 = new FileEntry("/rutedata/Brakar (Buskerud fylke)/984/Hovedsett 2016.zip");
		file984.setContents(new byte[]{11,10,12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
		fileSystem.add(file984 );
		FileEntry file985_1 = new FileEntry("/rutedata/Brakar (Buskerud fylke)/985/Hovedsett 2016_v2.zip");
		file985_1.setContents(new byte[]{10,11,12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
		fileSystem.add(file985_1);
		//idempotent filter should take care of this, since content is the same as above
		FileEntry file985_2 = new FileEntry("/rutedata/Brakar (Buskerud fylke)/985/ShouldBeFiltered.zip");
		file985_2.setContents(new byte[]{10,11,12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
		fileSystem.add(file985_2);

		fakeFtpServer.setFileSystem(fileSystem);
		fakeFtpServer.setServerControlPort(32220);

		fakeFtpServer.start();

		context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("activemq:queue:ProcessFileQueue").skipSendToOriginalEndpoint()
				.to("mock:buskerud");
			}
		});

		// we must manually start when we are done with all the advice with
		context.startRoute("nri-ftp-activemq");
		context.start();


		// setup expectations on the mocks
		buskerud.expectedMessageCount(3);

		// assert that the test was okay
		buskerud.assertIsSatisfied();

		List<Exchange> exchanges = buskerud.getExchanges();
		assertEquals(2L, exchanges.get(0).getIn().getHeader(Constants.PROVIDER_ID));
		assertNotNull(exchanges.get(0).getIn().getHeader(Constants.CORRELATION_ID));
		assertEquals("984_Hovedsett_2016.zip", exchanges.get(0).getIn().getHeader(Constants.FILE_NAME));
		assertEquals("985_Hovedsett_2016_v2.zip", exchanges.get(1).getIn().getHeader(Constants.FILE_NAME));
		assertEquals("1585_Hovedsett_2017.zip", exchanges.get(2).getIn().getHeader(Constants.FILE_NAME));
	}

}