package no.rutebanken.marduk.routes.nri;

import java.util.List;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.test.spring.CamelSpringDelegatingTestContextLoader;
import org.apache.camel.test.spring.CamelSpringJUnit4ClassRunner;
import org.apache.camel.test.spring.CamelTestContextBootstrapper;
import org.apache.camel.test.spring.UseAdviceWith;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockftpserver.fake.FakeFtpServer;
import org.mockftpserver.fake.UserAccount;
import org.mockftpserver.fake.filesystem.DirectoryEntry;
import org.mockftpserver.fake.filesystem.FileEntry;
import org.mockftpserver.fake.filesystem.FileSystem;
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.BootstrapWith;
import org.springframework.test.context.ContextConfiguration;
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

	@Test
	public void testSendError() throws Exception {

		FakeFtpServer fakeFtpServer = new FakeFtpServer();
		fakeFtpServer.setSystemName("UNIX");

		fakeFtpServer.addUserAccount(new UserAccount("username", "password", "/"));

		FileSystem fileSystem = new UnixFakeFileSystem();
		fileSystem.add(new DirectoryEntry("/rutedata"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)/985"));
		fileSystem.add(new DirectoryEntry("/rutedata/AtB (Sør-Trøndelag fylke)/985/AtB Hovedsett 2016_unzipped"));
		fileSystem.add(new FileEntry("/rutedata/AtB (Sør-Trøndelag fylke)/985/AtB Hovedsett 2016.zip"));

		fakeFtpServer.setFileSystem(fileSystem);
		fakeFtpServer.setServerControlPort(32220);

		fakeFtpServer.start();

		context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
			@Override
			public void configure() throws Exception {
				interceptSendToEndpoint("sftp://sor-trondelag@{{sftp.host}}").skipSendToOriginalEndpoint()
						.to("mock:sor-trondelag");
			}
		});

		// we must manually start when we are done with all the advice with
		context.start();

		// setup expectations on the mocks
		sorTrondelag.expectedMessageCount(1);

		// assert that the test was okay
		sorTrondelag.assertIsSatisfied();
		
		List<Exchange> exchanges = sorTrondelag.getExchanges();
		String sftpFileName = (String) exchanges.get(0).getProperty("CamelFileName");
		assertEquals("AtB_(Sør-Trøndelag_fylke)_985_AtB_Hovedsett_2016.zip", sftpFileName);
	}

}