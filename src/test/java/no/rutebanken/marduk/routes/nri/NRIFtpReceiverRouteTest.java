package no.rutebanken.marduk.routes.nri;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.Provider;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringRunner;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Ignore;
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
@SpringBootTest(classes = NRIFtpReceiverRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith
@Ignore
// TODO Tommy is going to look at this test
public class NRIFtpReceiverRouteTest extends MardukRouteBuilderIntegrationTestBase {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:sor-trondelag")
	protected MockEndpoint sorTrondelag;
	
    @Value("${nabu.rest.service.url}")
    private String nabuUrl;

	@Before
	public void setUpProvider() throws IOException {
		when(providerRepository.getProvider(12L)).thenReturn(Provider.create(IOUtils.toString(new FileReader(
				"src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json"))));
	}

	@Test
	public void testFetchFilesFromFTP() throws Exception {

		FakeFtpServer fakeFtpServer = new FakeFtpServer();
		fakeFtpServer.setSystemName("UNIX");

		fakeFtpServer.addUserAccount(new UserAccount("username", "password", "/"));

		FileSystem fileSystem = new UnixFakeFileSystem();
		fileSystem.add(new DirectoryEntry("/rutedata"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)/1585"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)/1585/AtB Hovedsett 2016_unzipped"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)/985"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)/984"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)/985/AtB Hovedsett 2016_unzipped"));
		FileEntry file1585 = new FileEntry("/rutedata/AtB (Sør-Trøndelag fylke)/1585/AtB Hovedsett 2017.zip");
		file1585.setContents(IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
		fileSystem.add(file1585);
		FileEntry file984 = new FileEntry("/rutedata/AtB (Sør-Trøndelag fylke)/984/AtB Hovedsett 2016.zip");
		file984.setContents(IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
		fileSystem.add(file984 );
		FileEntry file985 = new FileEntry("/rutedata/AtB (Sør-Trøndelag fylke)/985/AtB Hovedsett 2016_v2.zip");
		file985.setContents(IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
		fileSystem.add(file985);

		
		fakeFtpServer.setFileSystem(fileSystem);
		fakeFtpServer.setServerControlPort(32220);

		fakeFtpServer.start();

		context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("activemq:queue:ProcessFileQueue").skipSendToOriginalEndpoint()
				.to("mock:sor-trondelag");
			}
		});

		// we must manually start when we are done with all the advice with
		context.startRoute("nri-ftp-activemq");
		context.start();
		

		// setup expectations on the mocks
		sorTrondelag.expectedMessageCount(3);

		// assert that the test was okay
		sorTrondelag.assertIsSatisfied();
		
		List<Exchange> exchanges = sorTrondelag.getExchanges();
		assertEquals(2L, exchanges.get(0).getIn().getHeader(Constants.PROVIDER_ID));
		assertNotNull(exchanges.get(0).getIn().getHeader(Constants.CORRELATION_ID));
		assertEquals("AtB_(Sør-Trøndelag_fylke)_984_AtB_Hovedsett_2016.zip", (String) exchanges.get(0).getIn().getHeader(Constants.FILE_NAME));
		assertEquals("AtB_(Sør-Trøndelag_fylke)_985_AtB_Hovedsett_2016_v2.zip", (String) exchanges.get(1).getIn().getHeader(Constants.FILE_NAME));
		assertEquals("AtB_(Sør-Trøndelag_fylke)_1585_AtB_Hovedsett_2017.zip", (String) exchanges.get(2).getIn().getHeader(Constants.FILE_NAME));
	}

}