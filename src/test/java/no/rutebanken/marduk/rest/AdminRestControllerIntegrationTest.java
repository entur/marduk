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

import com.nimbusds.jose.JWSAlgorithm;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.TestConstants;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.authorization.AuthorizationService;
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

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.config.Customizer.withDefaults;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AdminRestController - Spring REST API endpoints.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class AdminRestControllerIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @TestConfiguration
    @EnableWebSecurity
    static class AdminRestControllerTestContextConfiguration {

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
                    .authorizeHttpRequests(authz -> authz.requestMatchers(AntPathRequestMatcher.antMatcher("/services/openapi.yaml")).permitAll()
                            .requestMatchers(AntPathRequestMatcher.antMatcher("/services/timetable_admin/openapi.yaml")).permitAll()
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

        @Bean
        public AuthorizationService<Long> testAuthorizationService() {
            return new TestAuthorizationService();
        }
    }

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/idempotentfilter/clean")
    protected ProducerTemplate cleanIdempotentFilterTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/routing_graph/build")
    protected ProducerTemplate buildGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/clean/all")
    protected ProducerTemplate cleanDataspacesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/clean/invalid?throwExceptionOnFailure=false")
    protected ProducerTemplate cleanDataspacesInvalidTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/jobs")
    protected ProducerTemplate listJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/export/files")
    protected ProducerTemplate listExportFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/completed_jobs")
    protected ProducerTemplate removeCompletedJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/files")
    protected ProducerTemplate listProviderFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/jobs/123")
    protected ProducerTemplate cancelJobTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/999/files?throwExceptionOnFailure=false")
    protected ProducerTemplate listProviderFilesUnknownProviderTemplate;

    // New endpoints - second batch
    @Produce("http:localhost:{{server.port}}/services/timetable_admin/jobs")
    protected ProducerTemplate cancelAllJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/validate/prevalidation")
    protected ProducerTemplate triggerPrevalidationTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/validate/level2")
    protected ProducerTemplate triggerLevel2ValidationTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/stop_places/clean")
    protected ProducerTemplate cleanStopPlacesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/line_statistics/refresh")
    protected ProducerTemplate refreshLineStatisticsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/routing_graph/build_base")
    protected ProducerTemplate buildBaseGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/routing_graph/build_candidate/otp2_base")
    protected ProducerTemplate buildCandidateBaseGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/routing_graph/build_candidate/otp2_netex")
    protected ProducerTemplate buildCandidateNetexGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/routing_graph/build_candidate/invalid?throwExceptionOnFailure=false")
    protected ProducerTemplate buildCandidateInvalidTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/routing_graph/graphs")
    protected ProducerTemplate listGraphsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/jobs")
    protected ProducerTemplate cancelAllProviderJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/flex/import")
    protected ProducerTemplate importFlexFilesTemplate;

    // Third batch of endpoints
    @Produce("http:localhost:{{server.port}}/services/timetable_admin/line_statistics/all")
    protected ProducerTemplate getLineStatisticsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/line_statistics/invalid?throwExceptionOnFailure=false")
    protected ProducerTemplate getLineStatisticsInvalidTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/export/gtfs/merged")
    protected ProducerTemplate triggerMergedGtfsExportTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/files/test.zip?throwExceptionOnFailure=false")
    protected ProducerTemplate downloadProviderFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/line_statistics")
    protected ProducerTemplate getProviderLineStatisticsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/jobs")
    protected ProducerTemplate listProviderJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/export")
    protected ProducerTemplate triggerProviderExportTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/validate")
    protected ProducerTemplate triggerProviderValidationTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/clean")
    protected ProducerTemplate cleanProviderDataspaceTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/transfer")
    protected ProducerTemplate transferProviderDataTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/" + TestConstants.PROVIDER_ID_RUT + "/flex/files")
    protected ProducerTemplate uploadFlexFileTemplate;

    // Map admin endpoints
    @Produce("http:localhost:{{server.port}}/services/map_admin/download")
    protected ProducerTemplate downloadOsmDataTemplate;

    // Deprecated codespace-based endpoints
    @Produce("http:localhost:{{server.port}}/services/timetable_admin/upload/" + TestConstants.CHOUETTE_REFERENTIAL_RUT)
    protected ProducerTemplate uploadFileByCodespaceTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/upload/INVALID_CODESPACE?throwExceptionOnFailure=false")
    protected ProducerTemplate uploadFileByInvalidCodespaceTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/download_netex_blocks/" + TestConstants.CHOUETTE_REFERENTIAL_RUT + "?throwExceptionOnFailure=false")
    protected ProducerTemplate downloadNetexBlocksTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin/download_netex_blocks/INVALID_CODESPACE?throwExceptionOnFailure=false")
    protected ProducerTemplate downloadNetexBlocksInvalidCodespaceTemplate;

    @BeforeEach
    void setUpProvider() {
        when(providerRepository.getReferential(TestConstants.PROVIDER_ID_RUT)).thenReturn(CHOUETTE_REFERENTIAL_RUT);
        when(providerRepository.getProvider(TestConstants.PROVIDER_ID_RUT)).thenReturn(provider(CHOUETTE_REFERENTIAL_RUT, TestConstants.PROVIDER_ID_RUT, null));
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);
        // Explicitly return null for unknown codespaces
        when(providerRepository.getProviderId("INVALID_CODESPACE")).thenReturn(null);
    }

    @Test
    void cleanIdempotentFilter() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cleanIdempotentFilterTemplate.request(
                cleanIdempotentFilterTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void buildGraph() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = buildGraphTemplate.request(
                buildGraphTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void cleanDataspaces() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cleanDataspacesTemplate.request(
                cleanDataspacesTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void cleanDataspacesInvalidFilter() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cleanDataspacesInvalidTemplate.request(
                cleanDataspacesInvalidTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(400, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void listJobs() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = listJobsTemplate.request(
                listJobsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void listExportFiles() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = listExportFilesTemplate.request(
                listExportFilesTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void removeCompletedJobs() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "DELETE",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = removeCompletedJobsTemplate.request(
                removeCompletedJobsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void listProviderFiles() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = listProviderFilesTemplate.request(
                listProviderFilesTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void cancelJob() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "DELETE",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cancelJobTemplate.request(
                cancelJobTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void listProviderFilesUnknownProvider() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = listProviderFilesUnknownProviderTemplate.request(
                listProviderFilesUnknownProviderTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(404, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void uploadFile() {
        context.start();

        String fileName = "netex-test-spring-upload.zip";
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = listProviderFilesTemplate.request(
                listProviderFilesTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setBody(httpEntity);
                    exchange.getIn().setHeaders(headers);
                });

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    // Tests for second batch of endpoints

    @Test
    void cancelAllJobs() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "DELETE",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cancelAllJobsTemplate.request(
                cancelAllJobsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void triggerPrevalidation() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = triggerPrevalidationTemplate.request(
                triggerPrevalidationTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void triggerLevel2Validation() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = triggerLevel2ValidationTemplate.request(
                triggerLevel2ValidationTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void cleanStopPlaces() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cleanStopPlacesTemplate.request(
                cleanStopPlacesTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void refreshLineStatistics() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = refreshLineStatisticsTemplate.request(
                refreshLineStatisticsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void buildBaseGraph() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = buildBaseGraphTemplate.request(
                buildBaseGraphTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void buildCandidateBaseGraph() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = buildCandidateBaseGraphTemplate.request(
                buildCandidateBaseGraphTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void buildCandidateNetexGraph() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = buildCandidateNetexGraphTemplate.request(
                buildCandidateNetexGraphTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void buildCandidateInvalidGraphType() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = buildCandidateInvalidTemplate.request(
                buildCandidateInvalidTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(400, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void listGraphs() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = listGraphsTemplate.request(
                listGraphsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void cancelAllProviderJobs() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "DELETE",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cancelAllProviderJobsTemplate.request(
                cancelAllProviderJobsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void importFlexFiles() {
        context.start();

        String jsonBody = "{\"files\":[{\"name\":\"test-flex.zip\"}]}";

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                Exchange.CONTENT_TYPE, "application/json",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = importFlexFilesTemplate.request(
                importFlexFilesTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setBody(jsonBody);
                    exchange.getIn().setHeaders(headers);
                });

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    // Tests for third batch of endpoints

    @Test
    void getLineStatistics() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = getLineStatisticsTemplate.request(
                getLineStatisticsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void getLineStatisticsInvalidFilter() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = getLineStatisticsInvalidTemplate.request(
                getLineStatisticsInvalidTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(400, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void triggerMergedGtfsExport() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = triggerMergedGtfsExportTemplate.request(
                triggerMergedGtfsExportTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void downloadProviderFile() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = downloadProviderFileTemplate.request(
                downloadProviderFileTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        // File doesn't exist, so expect 404
        assertEquals(404, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void getProviderLineStatistics() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = getProviderLineStatisticsTemplate.request(
                getProviderLineStatisticsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void listProviderJobs() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = listProviderJobsTemplate.request(
                listProviderJobsTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void triggerProviderExport() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = triggerProviderExportTemplate.request(
                triggerProviderExportTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void triggerProviderValidation() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = triggerProviderValidationTemplate.request(
                triggerProviderValidationTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void cleanProviderDataspace() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = cleanProviderDataspaceTemplate.request(
                cleanProviderDataspaceTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void transferProviderData() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = transferProviderDataTemplate.request(
                transferProviderDataTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void uploadFlexFile() {
        context.start();

        String fileName = "netex-flex-test.zip";
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = uploadFlexFileTemplate.request(
                uploadFlexFileTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setBody(httpEntity);
                    exchange.getIn().setHeaders(headers);
                });

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    // Tests for map admin endpoints

    @Test
    void downloadOsmData() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = downloadOsmDataTemplate.request(
                downloadOsmDataTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    // Tests for deprecated codespace-based endpoints

    @Test
    void uploadFileByCodespace() {
        context.start();

        String fileName = "netex-codespace-upload.zip";
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = uploadFileByCodespaceTemplate.request(
                uploadFileByCodespaceTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setBody(httpEntity);
                    exchange.getIn().setHeaders(headers);
                });

        assertEquals(200, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        // Verify response body contains correlationId
        String responseBody = response.getMessage().getBody(String.class);
        assertNotNull(responseBody);
        assertTrue(responseBody.contains("correlationId"));
    }

    @Test
    void uploadFileByInvalidCodespace() {
        context.start();

        String fileName = "netex-codespace-upload.zip";
        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = uploadFileByInvalidCodespaceTemplate.request(
                uploadFileByInvalidCodespaceTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setBody(httpEntity);
                    exchange.getIn().setHeaders(headers);
                });

        assertEquals(404, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void downloadNetexBlocks() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = downloadNetexBlocksTemplate.request(
                downloadNetexBlocksTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        // File doesn't exist, so expect 404
        assertEquals(404, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void downloadNetexBlocksInvalidCodespace() {
        context.start();

        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token");

        Exchange response = downloadNetexBlocksInvalidCodespaceTemplate.request(
                downloadNetexBlocksInvalidCodespaceTemplate.getDefaultEndpoint(),
                exchange -> exchange.getIn().setHeaders(headers));

        assertEquals(404, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    private static Map<String, Object> getTestHeaders(String method) {
        return Map.of(
                Exchange.HTTP_METHOD, method,
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);
    }
}
