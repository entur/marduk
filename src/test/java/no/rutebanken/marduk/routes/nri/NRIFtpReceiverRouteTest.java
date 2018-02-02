/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

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
import org.apache.commons.lang3.time.DateUtils;
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
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

@RunWith(CamelSpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NRIFtpReceiverRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles({"default", "in-memory-blobstore"})
@UseAdviceWith
public class NRIFtpReceiverRouteTest extends MardukRouteBuilderIntegrationTestBase {

    @Value("${nri.ftp.file.age.filter.months:3}")
    private int fileAgeFilterMonths;

    @Autowired
    private ModelCamelContext context;

    @Autowired
    IdempotentRepositoryService idempotentRepositoryService;

    @EndpointInject(uri = "mock:processFileMock")
    protected MockEndpoint processFileMock;


    public void setUp(boolean autoImport, long id) throws IOException {
        //wipe idempotent stores
        idempotentRepositoryService.cleanUniqueFileNameAndDigestRepo();
        Provider provider = Provider.create(IOUtils.toString(new FileReader(
                                                                                   "src/test/resources/no/rutebanken/marduk/providerRepository/provider2.json")));
        provider.chouetteInfo.enableAutoImport = autoImport;
        when(providerRepository.getProvider(id)).thenReturn(provider);

        processFileMock.reset();
    }

    @Test
    public void testFetchFilesFromFTPWithAutoImport() throws Exception {
        setUp(true,4);
        testFetchFilesFromFTP(true,"Jotunheimen og Valdresruten Bilselskap");
    }

    @Test
    public void testFetchFilesFromFTPWithoutAutoImport() throws Exception {
        setUp(false,5);
        testFetchFilesFromFTP(false,"Brakar (Buskerud fylke)");
    }


    public void testFetchFilesFromFTP(boolean autoImport, String providerFolder) throws Exception {

        FakeFtpServer fakeFtpServer = new FakeFtpServer();
        fakeFtpServer.setSystemName("UNIX");

        fakeFtpServer.addUserAccount(new UserAccount("username", "password", "/"));

        FileSystem fileSystem = new UnixFakeFileSystem();
        fileSystem.add(new DirectoryEntry("/rutedata"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + ""));

        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/1585"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/1585/Hovedsett 2016_unzipped"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/985"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/984"));
        fileSystem.add(new DirectoryEntry("/rutedata/" + providerFolder + "/985/Hovedsett 2016_unzipped"));
        FileEntry file1585 = new FileEntry("/rutedata/" + providerFolder + "/1585/Hovedsett 2017.zip");
        file1585.setLastModified(DateUtils.addMonths(new Date(), -(fileAgeFilterMonths - 1)));
        file1585.setContents(new byte[]{12, 11, 10});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file1585);
        FileEntry file984 = new FileEntry("/rutedata/" + providerFolder + "/984/Hovedsett 2016.zip");
        file984.setContents(new byte[]{11, 10, 12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file984);
        FileEntry file985_1 = new FileEntry("/rutedata/" + providerFolder + "/985/Hovedsett 2016_v2.zip");
        file985_1.setContents(new byte[]{10, 11, 12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file985_1);
        //idempotent filter should take care of this, since content is the same as above
        FileEntry file985_2 = new FileEntry("/rutedata/" + providerFolder + "/985/ShouldBeFiltered.zip");
        file985_2.setContents(new byte[]{10, 11, 12});//IOUtils.toByteArray(getClass().getResourceAsStream("/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")));
        fileSystem.add(file985_2);

        FileEntry toOld = new FileEntry("/rutedata/" + providerFolder + "/985/TooOldShouldBeFiltered.zip");
        toOld.setContents(new byte[]{10, 11, 12, 13});
        toOld.setLastModified(DateUtils.addMonths(new Date(), -(fileAgeFilterMonths + 1)));
        fileSystem.add(toOld);


        fakeFtpServer.setFileSystem(fileSystem);
        fakeFtpServer.setServerControlPort(32220);

        fakeFtpServer.start();

        context.getRouteDefinitions().get(0).adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("activemq:queue:ProcessFileQueue").skipSendToOriginalEndpoint()
                        .to("mock:processFileMock");
            }
        });

        // we must manually start when we are done with all the advice with
        context.startRoute("nri-ftp-activemq");
        context.start();


        // setup expectations on the mocks
        processFileMock.expectedMessageCount(autoImport? 3 : 0);

        // assert that the test was okay
        processFileMock.assertIsSatisfied();

        if (autoImport) {
            List<Exchange> exchanges = processFileMock.getExchanges();
            assertEquals(2L, exchanges.get(0).getIn().getHeader(Constants.PROVIDER_ID));
            assertNotNull(exchanges.get(0).getIn().getHeader(Constants.CORRELATION_ID));
            assertEquals("984_Hovedsett_2016.zip", exchanges.get(0).getIn().getHeader(Constants.FILE_NAME));
            assertEquals("985_Hovedsett_2016_v2.zip", exchanges.get(1).getIn().getHeader(Constants.FILE_NAME));
            assertEquals("1585_Hovedsett_2017.zip", exchanges.get(2).getIn().getHeader(Constants.FILE_NAME));
        }

        fakeFtpServer.stop();
    }

}