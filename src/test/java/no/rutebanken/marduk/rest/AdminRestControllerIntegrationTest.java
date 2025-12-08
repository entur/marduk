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
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for AdminRestController - Spring REST API endpoints.
 * These endpoints mirror the Camel-based AdminRestRouteBuilder with "_new" suffix.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class AdminRestControllerIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/idempotentfilter/clean")
    protected ProducerTemplate cleanIdempotentFilterTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/routing_graph/build")
    protected ProducerTemplate buildGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/clean/all")
    protected ProducerTemplate cleanDataspacesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/clean/invalid?throwExceptionOnFailure=false")
    protected ProducerTemplate cleanDataspacesInvalidTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/jobs")
    protected ProducerTemplate listJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/export/files")
    protected ProducerTemplate listExportFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/completed_jobs")
    protected ProducerTemplate removeCompletedJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/" + TestConstants.PROVIDER_ID_RUT + "/files")
    protected ProducerTemplate listProviderFilesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/" + TestConstants.PROVIDER_ID_RUT + "/jobs/123")
    protected ProducerTemplate cancelJobTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/999/files?throwExceptionOnFailure=false")
    protected ProducerTemplate listProviderFilesUnknownProviderTemplate;

    // New endpoints - second batch
    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/jobs")
    protected ProducerTemplate cancelAllJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/validate/prevalidation")
    protected ProducerTemplate triggerPrevalidationTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/validate/level2")
    protected ProducerTemplate triggerLevel2ValidationTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/stop_places/clean")
    protected ProducerTemplate cleanStopPlacesTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/line_statistics/refresh")
    protected ProducerTemplate refreshLineStatisticsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/routing_graph/build_base")
    protected ProducerTemplate buildBaseGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/routing_graph/build_candidate/otp2_base")
    protected ProducerTemplate buildCandidateBaseGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/routing_graph/build_candidate/otp2_netex")
    protected ProducerTemplate buildCandidateNetexGraphTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/routing_graph/build_candidate/invalid?throwExceptionOnFailure=false")
    protected ProducerTemplate buildCandidateInvalidTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/routing_graph/graphs")
    protected ProducerTemplate listGraphsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/" + TestConstants.PROVIDER_ID_RUT + "/jobs")
    protected ProducerTemplate cancelAllProviderJobsTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable_admin_new/" + TestConstants.PROVIDER_ID_RUT + "/flex/import")
    protected ProducerTemplate importFlexFilesTemplate;

    @BeforeEach
    void setUpProvider() {
        when(providerRepository.getReferential(TestConstants.PROVIDER_ID_RUT)).thenReturn(CHOUETTE_REFERENTIAL_RUT);
        when(providerRepository.getProvider(TestConstants.PROVIDER_ID_RUT)).thenReturn(provider(CHOUETTE_REFERENTIAL_RUT, TestConstants.PROVIDER_ID_RUT, null));
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

    private static Map<String, Object> getTestHeaders(String method) {
        return Map.of(
                Exchange.HTTP_METHOD, method,
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);
    }
}
