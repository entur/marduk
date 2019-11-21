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
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.repository.InMemoryBlobStoreRepository;
import no.rutebanken.marduk.test.TestApp;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ModelCamelContext;
import org.apache.commons.compress.utils.IOUtils;
import org.entur.jwt.junit5.entur.test.auth0.PartnerAuth0AuthorizationServer;
import org.entur.jwt.junit5.entur.test.auth0.PartnerAuth0Token;
import org.entur.jwt.junit5.entur.test.organsation.OrganisationAuthorizationServer;
import org.entur.jwt.junit5.entur.test.organsation.OrganisationToken;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static no.rutebanken.marduk.Constants.*;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = AdminRestRouteBuilder.class, properties = "spring.main.sources=no.rutebanken.marduk.test")
public class AdminRestMardukRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    // "{\"r\":\"adminEditRouteData\",\"o\":\"RB\"}")
    // RoleAssignment.builder().withOrganisation("RB").withRole("adminEditRouteData").build().toJson();
    private static final String adminEditRouteDataForRB = "{\"r\":\"adminEditRouteData\",\"o\":\"RB\"}";
    
    @LocalServerPort
    public int port;

    @Autowired
    private InMemoryBlobStoreRepository inMemoryBlobStoreRepository;

    @EndpointInject(uri = "mock:chouetteImportQueue")
    protected MockEndpoint importQueue;

    @EndpointInject(uri = "mock:chouetteExportNetexQueue")
    protected MockEndpoint exportQueue;

    @Produce(uri = "http4:localhost:8080/services/timetable_admin/2/import")
    protected ProducerTemplate importTemplate;

    @Produce(uri = "http4:localhost:8080/services/timetable_admin/2/export")
    protected ProducerTemplate exportTemplate;

    @Produce(uri = "http4:localhost:8080/services/timetable_admin/2/files")
    protected ProducerTemplate listFilesTemplate;

    @Produce(uri = "http4:localhost:8080/services/timetable_admin/2/files/existing_regtopp-file.zip")
    protected ProducerTemplate getFileTemplate;

    @Produce(uri = "http4:localhost:8080/services/timetable_admin/2/files/unknown-file.zip")
    protected ProducerTemplate getUnknownFileTemplate;

    @Produce(uri = "http4:localhost:8080/services/timetable_admin/export/files")
    protected ProducerTemplate listExportFilesTemplate;

    @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
    private List<String> exportFileStaticPrefixes;

    @BeforeEach
    public void setUpProvider() {
        when(providerRepository.getReferential(2L)).thenReturn("rut");
    }

    @Test
    public void runImport(@OrganisationToken(roles = adminEditRouteDataForRB) String token) throws Exception {

        context.getRouteDefinition("admin-chouette-import").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("entur-google-pubsub:ProcessFileQueue").skipSendToOriginalEndpoint().to("mock:chouetteImportQueue");
            }
        });


        // we must manually start when we are done with all the advice with
        context.start();

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
        headers.put("Authorization", token);
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
    public void runExport(@OrganisationToken(roles = adminEditRouteDataForRB) String token) throws Exception {

        context.getRouteDefinition("admin-chouette-export").adviceWith(context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("entur-google-pubsub:ChouetteExportNetexQueue").skipSendToOriginalEndpoint().to("mock:chouetteExportNetexQueue");

            }
        });

        // we must manually start when we are done with all the advice with
        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put("Authorization", token);
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
    public void getBlobStoreFiles(@OrganisationToken(roles = adminEditRouteDataForRB) String token) throws Exception {

        // Preparations
        String filename = "ruter_fake_data.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";

        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob(fileStorePath + filename, new FileInputStream(new File(pathname)), false);
//        BlobStoreFiles blobStoreFiles = inMemoryBlobStoreRepository.listBlobs(fileStorePath);

        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put("Authorization", token);
        InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectMapper mapper = new ObjectMapper();
        BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
        assertEquals(1, rsp.getFiles().size());
        assertEquals(fileStorePath + filename, rsp.getFiles().get(0).getName());

    }

    @Test
    public void getBlobStoreFile(@OrganisationToken(roles = adminEditRouteDataForRB) String token) throws Exception {
        // Preparations
        String filename = "existing_regtopp-file.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
        String pathname = "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip";
        FileInputStream testFileStream = new FileInputStream(new File(pathname));
        //populate fake blob repo
        inMemoryBlobStoreRepository.uploadBlob(fileStorePath + filename, testFileStream, false);


        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put("Authorization", token);
        InputStream response = (InputStream) getFileTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

//        assertTrue(org.apache.commons.io.IOUtils.contentEquals(testFileStream, response));
    }

    @Test
    public void getBlobStoreFile_unknownFile(@OrganisationToken(roles = adminEditRouteDataForRB) String token) throws Exception {

        context.start();

        assertThrows(CamelExecutionException.class, () -> {
            // Do rest call
            Map<String, Object> headers = new HashMap<String, Object>();
            headers.put(Exchange.HTTP_METHOD, "GET");
            headers.put("Authorization", token);
            getUnknownFileTemplate.requestBodyAndHeaders(null, headers);
        });
    }


    @Test
    public void getBlobStoreExportFiles(@OrganisationToken(roles = adminEditRouteDataForRB) String token) throws Exception {
        String testFileName = "testFile";
        //populate fake blob repo
        for (String prefix : exportFileStaticPrefixes) {
            inMemoryBlobStoreRepository.uploadBlob(prefix + testFileName, new FileInputStream(new File( "src/test/resources/no/rutebanken/marduk/routes/chouette/empty_regtopp.zip")), false);
        }
        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put("Authorization", token);
        InputStream response = (InputStream) listExportFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectMapper mapper = new ObjectMapper();
        BlobStoreFiles rsp = mapper.readValue(s, BlobStoreFiles.class);
        assertEquals(exportFileStaticPrefixes.size(), rsp.getFiles().size());
        exportFileStaticPrefixes.forEach(prefix -> rsp.getFiles().stream().anyMatch(file -> (prefix + testFileName).equals(file.getName())));
    }

    @Test
    public void testThatSwaggerSpecificationIsAvailableWithoutAuthentication() throws Exception {
        context.start();
        
        URI uri = new URI("http://localhost:" + port + "/services/swagger.json");

        when()
            .get(uri)
        .then()
            .log().all()
               .assertThat()
               .statusCode(200);
    }

    @Test
    public void testTimeTableAdmin(@OrganisationToken(roles = adminEditRouteDataForRB) String token) throws Exception {
        context.start();
        
        URI uri = new URI("http://localhost:" + port + "/services/timetable_admin/export/files");

         given()
             .header("Authorization", token)
        .when()
            .get(uri)
        .then()
            .assertThat()
            .statusCode(200);
        
    }
    
    @Test
    public void testTimeTableAdminWithInvalidToken() throws Exception {
        context.start();

        URI uri = new URI("http://localhost:" + port + "/services/timetable_admin/export/files");

        String token = "Bearer invalid.token.value";
        
         given()
             .header("Authorization", token)
        .when()
            .get(uri)
        .then()
            .assertThat()
            .statusCode(401);
    }        

    @Test
    public void testTimeTableAdminWithoutToken() throws Exception {
        context.start();
        
        URI uri = new URI("http://localhost:" + port + "/services/timetable_admin/export/files");

        when()
            .get(uri)
        .then()
            .assertThat()
            .statusCode(401);
    }
    
    @Test
    public void testPartnerRouteWithPartnerToken(@PartnerAuth0Token(organisationId = 1) String token) throws Exception {
        context.start();
        
        URI uri = new URI("http://localhost:" + port + "/services/myPath/myCodeSpace");

        given()
            .header("Authorization", token)
            .log().all()
        .when()
            .get(uri)
        .then()
            .log().all()
            .assertThat()
            .statusCode(200);
    }
    
    @Test
    public void testPartnerRouteWithoutToken() throws Exception {
        context.start();
        
        URI uri = new URI("http://localhost:" + port + "/services/myPath/myCodeSpace");

        given()
            .log().all()
        .when()
            .get(uri)
        .then()
            .log().all()
            .assertThat()
            .statusCode(403);
    }

}