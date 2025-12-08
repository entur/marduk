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

    private static Map<String, Object> getTestHeaders(String method) {
        return Map.of(
                Exchange.HTTP_METHOD, method,
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);
    }
}
