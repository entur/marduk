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
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.apache.camel.*;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static no.rutebanken.marduk.TestConstants.PROVIDER_ID_AS_STRING_RUT;
import static org.junit.jupiter.api.Assertions.*;
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
            http.cors(withDefaults()).csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authz -> authz.requestMatchers(AntPathRequestMatcher.antMatcher("/services/openapi.json")).permitAll()
                            .requestMatchers(AntPathRequestMatcher.antMatcher("/services/timetable_admin/openapi.json")).permitAll()
                    .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/prometheus")).permitAll()
                            .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health")).permitAll()
                            .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/liveness")).permitAll()
                            .requestMatchers(AntPathRequestMatcher.antMatcher("/actuator/health/readiness")).permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(configurer -> configurer.jwt(withDefaults()))
                    .oauth2Client(withDefaults());
            return http.build();
        }


        @Bean
        public JwtDecoder jwtdecoder() {
            return token -> createTestJwtToken();
        }

        private Jwt createTestJwtToken() {
            String userId = "test-user";
            return Jwt.withTokenValue("test-token")
                    .header("typ", "JWT")
                    .header("alg", JWSAlgorithm.RS256.getName())
                    .claim("iss", "https://test-issuer.entur.org")
                    .claim("scope", "openid profile email")
                    .subject(userId)
                    .audience(Set.of("test-audience"))
                    .build();
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

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/import")
    protected ProducerTemplate importTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/export")
    protected ProducerTemplate exportTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files")
    protected ProducerTemplate listFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files/netex.zip")
    protected ProducerTemplate getFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files")
    protected ProducerTemplate postFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/flex/files")
    protected ProducerTemplate postFlexFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files/unknown-file.zip")
    protected ProducerTemplate getUnknownFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/export/files")
    protected ProducerTemplate listExportFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/download_netex_blocks/" + CHOUETTE_REFERENTIAL_RUT)
    protected ProducerTemplate downloadNetexBlocksTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/upload/" + CHOUETTE_REFERENTIAL_RUT)
    protected ProducerTemplate uploadFileTemplate;

    @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
    private List<String> exportFileStaticPrefixes;


    @BeforeEach
    void setUpProvider() {
        when(providerRepository.getReferential(TestConstants.PROVIDER_ID_RUT)).thenReturn(CHOUETTE_REFERENTIAL_RUT);
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

        Map<String, Object> headers = getTestHeaders("POST");

        importTemplate.sendBodyAndHeaders(importJson, headers);

        // setup expectations on the mocks
        importQueue.expectedMessageCount(2);

        // assert that the test was okay
        importQueue.assertIsSatisfied();

        List<Exchange> exchanges = importQueue.getExchanges();
        String providerId = (String) exchanges.getFirst().getIn().getHeader(PROVIDER_ID);
        assertEquals(PROVIDER_ID_AS_STRING_RUT, providerId);
        String s3FileHandle = (String) exchanges.getFirst().getIn().getHeader(FILE_HANDLE);
        assertEquals(BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT +  "/file1", s3FileHandle);
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
        Map<String, Object> headers = getTestHeaders("POST");
        exportTemplate.sendBodyAndHeaders(null, headers);

        // setup expectations on the mocks
        exportQueue.expectedMessageCount(1);

        // assert that the test was okay
        exportQueue.assertIsSatisfied();

        List<Exchange> exchanges = exportQueue.getExchanges();
        String providerId = (String) exchanges.getFirst().getIn().getHeader(PROVIDER_ID);
        assertEquals(PROVIDER_ID_AS_STRING_RUT, providerId);
    }

    @Test
    void getBlobStoreFiles() throws Exception {

        // Preparations
        String testFileName = "ruter_fake_data.zip";
        String testFileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';
        InputStream testFile = getTestNetexArchiveAsStream();

        //populate fake blob repo
        internalInMemoryBlobStoreRepository.uploadBlob(testFileStorePath + testFileName, testFile);

        context.start();

        // Do rest call
        Map<String, Object> headers = getTestHeaders("GET");
        InputStream response = (InputStream) listFilesTemplate.requestBodyAndHeaders(null, headers);
        // Parse response

        String s = new String(IOUtils.toByteArray(response));

        ObjectReader objectReader = ObjectMapperFactory.getSharedObjectMapper().readerFor(BlobStoreFiles.class);
        BlobStoreFiles rsp = objectReader.readValue(s);
        assertEquals(1, rsp.getFiles().size(), "The list should contain exactly one file");
        assertEquals(testFileName, rsp.getFiles().getFirst().getName(), "The file name should not be prefixed by the file store path");
    }

    @Test
    void getFile() throws Exception {
        // Preparations
        String filename = "netex.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';
        InputStream testFile = getTestNetexArchiveAsStream();
        //populate fake blob repo
        internalInMemoryBlobStoreRepository.uploadBlob(fileStorePath + filename, testFile);

        context.start();

        Map<String, Object> headers = getTestHeaders("GET");
        InputStream response = (InputStream) getFileTemplate.requestBodyAndHeaders(null, headers);

        assertTrue(org.apache.commons.io.IOUtils.contentEquals(getTestNetexArchiveAsStream(), response));
    }

    @Test
    void getBlobStoreFile_unknownFile() {

        context.start();

        Map<String, Object> headers = getTestHeaders("GET");

        assertThrows(CamelExecutionException.class, () -> getUnknownFileTemplate.requestBodyAndHeaders(null, headers));
    }

    @Test
    void getBlobStoreExportFiles() throws Exception {
        String testFileName = "netex.zip";
        InputStream testFile = getTestNetexArchiveAsStream();
        //populate fake blob repo
        for (String prefix : exportFileStaticPrefixes) {
            mardukInMemoryBlobStoreRepository.uploadBlob(prefix + testFileName, testFile);
        }
        context.start();

        // Do rest call
        Map<String, Object> headers = getTestHeaders("GET");
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
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';

        AdviceWith.adviceWith(context, "process-file-after-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus")
                    .skipSendToOriginalEndpoint()
                    .to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue")
                    .replace()
                    .to("mock:processFileQueue");
        });

        context.start();

        HttpEntity httpEntity = MultipartEntityBuilder.create().addBinaryBody(fileName, testFile, ContentType.DEFAULT_BINARY, fileName).build();
        Map<String, Object> headers = getTestHeaders("POST");
        postFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        InputStream receivedFile = internalInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile);
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0);

    }

    @Test
    void postFlexFile() throws Exception {

        InputStream testFile = getTestNetexArchiveAsStream();
        // Preparations
        String fileName = "netex-test-flex-POST.zip";
        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';

        AdviceWith.adviceWith(context, "process-file-after-import", a -> {
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
        Map<String, Object> headers = getTestHeaders("POST");
        postFlexFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        processFileQueue.assertIsSatisfied();
        List<Exchange> exchanges = processFileQueue.getExchanges();

        assertEquals(IMPORT_TYPE_NETEX_FLEX,
                exchanges.getFirst().getIn().getHeader(IMPORT_TYPE),
                "Flex import should have expected IMPORT_TYPE header");

        InputStream receivedFile = internalInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile);
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0);
    }

    @Test
    void uploadNetexDataset() throws Exception {

        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);


        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';
        String fileName = "netex-test-http-upload.zip";

        AdviceWith.adviceWith(context, "process-file-after-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue").replace().to("mock:processFileQueue");
        });

        updateStatus.expectedMessageCount(1);
        processFileQueue.expectedMessageCount(1);

        HttpEntity httpEntity = MultipartEntityBuilder.create().addBinaryBody(fileName, getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName).build();
        Map<String, Object> headers = getTestHeaders("POST");

        context.start();
        uploadFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        updateStatus.assertIsSatisfied();
        processFileQueue.assertIsSatisfied();

        InputStream receivedFile = internalInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile);
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0);
    }

    @Test
    void downloadNetexBlocks() throws Exception {

        // Preparations
        String filename = "rb_rut-aggregated-netex.zip";
        InputStream testFile = getTestNetexArchiveAsStream();
        //populate fake blob repo
        internalInMemoryBlobStoreRepository.uploadBlob(Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT + filename, testFile);

        Map<String, Object> headers = getTestHeaders("GET");

        context.start();
        InputStream response = (InputStream) downloadNetexBlocksTemplate.requestBodyAndHeaders(null, headers);
        assertTrue(org.apache.commons.io.IOUtils.contentEquals(getTestNetexArchiveAsStream(), response));
    }

    @NotNull
    private static Map<String, Object> getTestHeaders(String method) {
        return Map.of(
                Exchange.HTTP_METHOD, method,
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);
    }
}