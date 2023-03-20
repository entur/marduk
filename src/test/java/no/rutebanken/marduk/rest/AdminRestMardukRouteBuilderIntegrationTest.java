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

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.nimbusds.jose.JWSAlgorithm;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static no.rutebanken.marduk.Constants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class AdminRestMardukRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {


    @TestConfiguration
    @EnableWebSecurity
    static class AdminRestMardukRouteBuilderTestContextConfiguration {

        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.setAllowedHeaders(Arrays.asList("Origin", "Accept", "X-Requested-With", "Content-Type", "Access-Control-Request-Method", "Access-Control-Request-Headers", "Authorization", "x-correlation-id"));
            configuration.addAllowedOrigin("*");
            configuration.setAllowedMethods(Arrays.asList("GET", "PUT", "POST", "DELETE"));
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        @Bean
        @ConditionalOnWebApplication
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.cors(withDefaults()).csrf().disable().authorizeHttpRequests(authz -> authz.antMatchers("/services/swagger.json").permitAll().antMatchers("/services/timetable_admin/swagger.json").permitAll()
                    // exposed internally only, on a different port (pod-level)
                    .antMatchers("/actuator/prometheus").permitAll().antMatchers("/actuator/health").permitAll().antMatchers("/actuator/health/liveness").permitAll().antMatchers("/actuator/health/readiness").permitAll().anyRequest().authenticated()).oauth2ResourceServer(OAuth2ResourceServerConfigurer::jwt).oauth2Client();
            return http.build();
        }


        @Bean
        public JwtDecoder jwtdecoder() {
            return token -> createTestJwtToken();
        }

        private Jwt createTestJwtToken() {
            String userId = "test-user";
            String userName = "JTest User";

            return Jwt.withTokenValue("test-token").header("typ", "JWT").header("alg", JWSAlgorithm.RS256.getName()).claim("iss", "https://test-issuer.entur.org").claim("scope", "openid profile email").subject(userId).audience(Set.of("test-audience")).build();
        }
    }

    @EndpointInject("mock:chouetteImportQueue")
    protected MockEndpoint importQueue;

    @EndpointInject("mock:chouetteExportNetexQueue")
    protected MockEndpoint exportQueue;

    @EndpointInject("mock:processFileQueue")
    protected MockEndpoint processFileQueue;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/import")
    protected ProducerTemplate importTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/export")
    protected ProducerTemplate exportTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/files")
    protected ProducerTemplate listFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/files/netex.zip")
    protected ProducerTemplate getFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/files")
    protected ProducerTemplate postFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/flex/files")
    protected ProducerTemplate postFlexFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/2/files/unknown-file.zip")
    protected ProducerTemplate getUnknownFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/export/files")
    protected ProducerTemplate listExportFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/download_netex_blocks/rut")
    protected ProducerTemplate downloadNetexBlocksTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/upload/rut")
    protected ProducerTemplate uploadFileTemplate;

    @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
    private List<String> exportFileStaticPrefixes;


    @BeforeEach
    void setUpProvider() {
        when(providerRepository.getReferential(2L)).thenReturn("rut");
    }

    @Test
    void runImport() throws Exception {

        AdviceWith.adviceWith(context, "admin-chouette-import",
                a -> a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue")
                        .replace()
                        .to("mock:chouetteImportQueue")
        );
        // we must manually start when we are done with all the advice with
        context.start();

        BlobStoreFiles d = new BlobStoreFiles();
        d.add(new BlobStoreFiles.File("file1", null, null, null));
        d.add(new BlobStoreFiles.File("file2", null, null, null));

        ObjectWriter objectWriter = ObjectMapperFactory.getSharedObjectMapper().writerFor(BlobStoreFiles.class);
        String importJson = objectWriter.writeValueAsString(d);

        // Do rest call

        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");

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
    void runExport() throws Exception {

        AdviceWith.adviceWith(context, "admin-chouette-export",
                a -> a.weaveByToUri("google-pubsub:(.*):ChouetteExportNetexQueue")
                        .replace()
                        .to("mock:chouetteExportNetexQueue")
        );

        // we must manually start when we are done with all the advice with
        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");
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
    void getBlobStoreFiles() throws Exception {

        // Preparations
        String testFileName = "ruter_fake_data.zip";
        String testFileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
        InputStream testFile = getTestNetexArchiveAsStream();

        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(testFileStorePath + testFileName, testFile, false);

        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectReader objectReader = ObjectMapperFactory.getSharedObjectMapper().readerFor(BlobStoreFiles.class);
        BlobStoreFiles rsp = objectReader.readValue(s);
        assertEquals(1, rsp.getFiles().size(), "The list should contain exactly one file");
        assertEquals(testFileName, rsp.getFiles().get(0).getName(), "The file name should not be prefixed by the file store path");
    }

    @Test
    void getFile() throws Exception {
        // Preparations
        String filename = "netex.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
        InputStream testFile = getTestNetexArchiveAsStream();
        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(fileStorePath + filename, testFile, false);

        context.start();

        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        InputStream response = (InputStream) getFileTemplate.requestBodyAndHeaders(null, headers);

        assertTrue(org.apache.commons.io.IOUtils.contentEquals(getTestNetexArchiveAsStream(), response));
    }

    @Test
    void getBlobStoreFile_unknownFile() {

        context.start();

        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");

        assertThrows(CamelExecutionException.class, () -> getUnknownFileTemplate.requestBodyAndHeaders(null, headers));
    }

    @Test
    void getBlobStoreExportFiles() throws Exception {
        String testFileName = "netex.zip";
        InputStream testFile = getTestNetexArchiveAsStream();
        //populate fake blob repo
        for (String prefix : exportFileStaticPrefixes) {
            mardukInMemoryBlobStoreRepository.uploadBlob(prefix + testFileName, testFile, false);
        }
        context.start();

        // Do rest call
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        InputStream response = (InputStream) listExportFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectReader objectReader = ObjectMapperFactory.getSharedObjectMapper().readerFor(BlobStoreFiles.class);
        BlobStoreFiles rsp = objectReader.readValue(s);
        assertEquals(exportFileStaticPrefixes.size(), rsp.getFiles().size());
        assertTrue(exportFileStaticPrefixes.stream().allMatch(prefix -> rsp.getFiles().stream().anyMatch(file -> (prefix + testFileName).equals(file.getName()))));
    }

    @Test
    void postSmallFile() throws Exception {
        postFile(getTestNetexArchiveAsStream());
    }

    @Test
    void postLargeFile() throws Exception {
        postFile(getLargeTestNetexArchiveAsStream());
    }

    private void postFile(InputStream testFile) throws Exception {
        // Preparations
        String fileName = "netex-test-POST.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";

        AdviceWith.adviceWith(context, "upload-file-and-start-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue")
                    .replace()
                    .to("mock:processFileQueue");
        });

        context.start();

        HttpEntity httpEntity = MultipartEntityBuilder.create().addBinaryBody(fileName, testFile, ContentType.DEFAULT_BINARY, fileName).build();
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        postFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        InputStream receivedFile = mardukInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile);
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0);

    }

    @Test
    public void postFlexFile() throws Exception {

        InputStream testFile = getTestNetexArchiveAsStream();
        // Preparations
        String fileName = "netex-test-flex-POST.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";

        AdviceWith.adviceWith(context, "upload-file-and-start-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue")
                    .replace()
                    .to("mock:processFileQueue");
        });

        processFileQueue.expectedMessageCount(1);

        context.start();

        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody(fileName, testFile, ContentType.DEFAULT_BINARY, fileName)
                .build();
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        postFlexFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        processFileQueue.assertIsSatisfied();
        List<Exchange> exchanges = processFileQueue.getExchanges();

        assertEquals(IMPORT_TYPE_NETEX_FLEX,
                exchanges.get(0).getIn().getHeader(IMPORT_TYPE),
                "Flex import should have expected IMPORT_TYPE header");

        InputStream receivedFile = mardukInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile);
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0);
    }

    @Test
    void uploadNetexDataset() throws Exception {

        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + "rut/";
        String fileName = "netex-test-http-upload.zip";

        AdviceWith.adviceWith(context, "upload-file-and-start-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue").replace().to("mock:processFileQueue");
        });

        updateStatus.expectedMessageCount(1);
        processFileQueue.expectedMessageCount(1);

        HttpEntity httpEntity = MultipartEntityBuilder.create().addBinaryBody(fileName, getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName).build();
        Map<String, Object> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");

        context.start();
        uploadFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        updateStatus.assertIsSatisfied();
        processFileQueue.assertIsSatisfied();

        InputStream receivedFile = mardukInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile);
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0);
    }

    @Test
    void downloadNetexBlocks() throws Exception {

        // Preparations
        String filename = "rb_rut-aggregated-netex.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT;
        InputStream testFile = getTestNetexArchiveAsStream();
        //populate fake blob repo
        mardukInMemoryBlobStoreRepository.uploadBlob(fileStorePath + filename, testFile, false);

        Map<String, Object> headers = new HashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer test-token");

        context.start();
        InputStream response = (InputStream) downloadNetexBlocksTemplate.requestBodyAndHeaders(null, headers);
        assertTrue(org.apache.commons.io.IOUtils.contentEquals(getTestNetexArchiveAsStream(), response));
    }
}