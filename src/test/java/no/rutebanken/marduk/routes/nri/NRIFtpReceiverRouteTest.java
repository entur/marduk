package no.rutebanken.marduk.routes.nri;

import java.io.FileReader;
import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.CamelTestContextBootstrapper;
import org.apache.camel.test.spring.UseAdviceWith;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.FileSystemEntry;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;

import no.rutebanken.marduk.Constants;

import static org.junit.Assert.*;

@RunWith(CamelSpringJUnit4ClassRunner.class)
@BootstrapWith(CamelTestContextBootstrapper.class)
@ContextConfiguration(loader = CamelSpringDelegatingTestContextLoader.class, classes = CamelConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ActiveProfiles({ "default", "dev" })
@UseAdviceWith(true)
public class NRIFtpReceiverRouteTest {

	@Autowired
	private ModelCamelContext context;

	@EndpointInject(uri = "mock:sor-trondelag")
	protected MockEndpoint sorTrondelag;
	
    @Value("${nabu.rest.service.url}")
    private String nabuUrl;


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
		file1585.setContents(IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/EMPTY_REGTOPP.zip")));
		fileSystem.add(file1585);
		FileEntry file984 = new FileEntry("/rutedata/AtB (Sør-Trøndelag fylke)/984/AtB Hovedsett 2016.zip");
		file984.setContents(IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/EMPTY_REGTOPP.zip")));
		fileSystem.add(file984 );
		FileEntry file985 = new FileEntry("/rutedata/AtB (Sør-Trøndelag fylke)/985/AtB Hovedsett 2016_v2.zip");
		file985.setContents(IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/EMPTY_REGTOPP.zip")));
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

		// Mock Nabu / providerRepository (done differently since RestTemplate is being used which skips Camel)
		context.addRoutes(new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				from("netty4-http:"+nabuUrl+"/providers/12")
					.setBody()
					.constant(IOUtils.toString(new FileReader("src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")))
					.setHeader(Exchange.CONTENT_TYPE,constant("application/json"));
					
			}
			
		});	
		// we must manually start when we are done with all the advice with
		context.start();

		// setup expectations on the mocks
		sorTrondelag.expectedMessageCount(3);

		// assert that the test was okay
		sorTrondelag.assertIsSatisfied();
		
		List<Exchange> exchanges = sorTrondelag.getExchanges();
		assertEquals(2L, exchanges.get(0).getIn().getHeader(Constants.PROVIDER_ID));
		assertNotNull(exchanges.get(0).getIn().getHeader(Constants.CORRELATION_ID));
		assertEquals("AtB_(Sør-Trøndelag_fylke)_984_AtB_Hovedsett_2016.zip", (String) exchanges.get(0).getIn().getHeader(Exchange.FILE_NAME));
		assertEquals("AtB_(Sør-Trøndelag_fylke)_985_AtB_Hovedsett_2016_v2.zip", (String) exchanges.get(1).getIn().getHeader(Exchange.FILE_NAME));
		assertEquals("AtB_(Sør-Trøndelag_fylke)_1585_AtB_Hovedsett_2017.zip", (String) exchanges.get(2).getIn().getHeader(Exchange.FILE_NAME));
	}

}