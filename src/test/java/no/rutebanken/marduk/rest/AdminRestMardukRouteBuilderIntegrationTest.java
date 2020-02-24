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

package no.rutebanken.marduk.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_INBOUND;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes= TestApp.class)
public class AdminRestMardukRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @TestConfiguration
    @EnableWebSecurity
    static class AdminRestMardukRouteBuilderTestContextConfiguration extends WebSecurityConfigurerAdapter {

        @Override
        protected void configure(HttpSecurity http) throws Exception {
                    http.csrf().disable()
                            .authorizeRequests(authorizeRequests ->
                                    authorizeRequests
                                            .anyRequest().permitAll()
                            );

                }

            }



    @Autowired
    ModelCamelContext camelContext;

    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @EndpointInject(uri = "mock:chouetteImportQueue")
    protected MockEndpoint importQueue;

    @EndpointInject(uri = "mock:chouetteExportNetexQueue")
    protected MockEndpoint exportQueue;

    @Produce(uri = "http4:localhost:28080/services/timetable_admin/2/import")
    protected ProducerTemplate importTemplate;

    @Produce(uri = "http4:localhost:28080/services/timetable_admin/2/export")
    protected ProducerTemplate exportTemplate;

    @Produce(uri = "http4:localhost:28080/services/timetable_admin/2/files")
    protected ProducerTemplate listFilesTemplate;

    @Produce(uri = "http4:localhost:28080/services/timetable_admin/2/files/existing_regtopp-file.zip")
    protected ProducerTemplate getFileTemplate;

    @Produce(uri = "http4:localhost:28080/services/timetable_admin/2/files/unknown-file.zip")
    protected ProducerTemplate getUnknownFileTemplate;


    @Produce(uri = "http4:localhost:28080/services/timetable_admin/export/files")
    protected ProducerTemplate listExportFilesTemplate;


    @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
    private List<String> exportFileStaticPrefixes;


    @BeforeEach
    public void setUpProvider() {
        when(providerRepository.getReferential(2L)).thenReturn("rut");
    }

    @Test
    public void runImport() throws Exception {

        camelContext.getRouteDefinition("admin-chouette-import").adviceWith(camelContext, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("entur-google-pubsub:ProcessFileQueue").skipSendToOriginalEndpoint().to("mock:chouetteImportQueue");
            }
        });


        // we must manually start when we are done with all the advice with
        camelContext.start();

        BlobStoreFiles d = new BlobStoreFiles();
        d.add(new BlobStoreFiles.File("file1", null, null, null));
        d.add(new BlobStoreFiles.File("file2", null, null, null));

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
                interceptSendToEndpoint("entur-google-pubsub:ChouetteExportNetexQueue").skipSendToOriginalEndpoint().to("mock:chouetteExportNetexQueue");

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
//        BlobStoreFiles blobStoreFiles = inMemoryBlobStoreRepository.listBlobs(fileStorePath);

        camelContext.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectMapper mapper = new ObjectMapper();
        BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
        assertEquals(1, rsp.getFiles().size());
        assertEquals(fileStorePath + filename, rsp.getFiles().get(0).getName());

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

//		assertTrue(org.apache.commons.io.IOUtils.contentEquals(testFileStream, response));
    }


    @Test
    public void getBlobStoreFile_unknownFile() throws Exception {

        camelContext.start();

        assertThrows(CamelExecutionException.class, () -> {
            // Do rest call
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put(Exchange.HTTP_METHOD, "GET");
            getUnknownFileTemplate.requestBodyAndHeaders(null, headers);
        });
    }


    @Test
    public void getBlobStoreExportFiles() throws Exception {
        String testFileName = "testFile";
        //populate fake blob repo
        for (String prefix : exportFileStaticPrefixes) {
            inMemoryBlobStoreRepository.uploadBlob(prefix + testFileName, new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")), false);
        }
        camelContext.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        InputStream response = (InputStream) listExportFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectMapper mapper = new ObjectMapper();
        BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
        assertEquals(exportFileStaticPrefixes.size(), rsp.getFiles().size());
        exportFileStaticPrefixes.forEach(prefix -> rsp.getFiles().stream().anyMatch(file -> (prefix + testFileName).equals(file.getName())));
    }


}