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

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.TestConstants;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the new Spring REST API endpoints (AdminExternalRestController).
 * These endpoints are designed for machine-to-machine communication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class AdminExternalRestControllerIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:processFileQueue")
    protected MockEndpoint processFileQueue;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("http:localhost:{{server.port}}/services/timetable-management/datasets/" + CHOUETTE_REFERENTIAL_RUT)
    protected ProducerTemplate uploadFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable-management/flex-datasets/" + CHOUETTE_REFERENTIAL_RUT)
    protected ProducerTemplate uploadFlexFileTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable-management/datasets/" + CHOUETTE_REFERENTIAL_RUT + "/filtered")
    protected ProducerTemplate downloadFilteredDatasetTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable-management/datasets/" + CHOUETTE_REFERENTIAL_RUT + "/filtered?throwExceptionOnFailure=false")
    protected ProducerTemplate downloadFilteredDatasetNotFoundTemplate;

    @Produce("http:localhost:{{server.port}}/services/timetable-management/datasets/unknown_codespace?throwExceptionOnFailure=false")
    protected ProducerTemplate uploadFileUnknownCodespaceTemplate;

    @BeforeEach
    void setUpProvider() {
        when(providerRepository.getReferential(TestConstants.PROVIDER_ID_RUT)).thenReturn(CHOUETTE_REFERENTIAL_RUT);
    }

    @Test
    void uploadNetexDatasetViaSpringApi() throws Exception {
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);

        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';
        String fileName = "netex-test-spring-http-upload.zip";

        AdviceWith.adviceWith(context, "process-file-after-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue").replace().to("mock:processFileQueue");
        });

        updateStatus.expectedMessageCount(1);
        processFileQueue.expectedMessageCount(1);

        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build();
        Map<String, Object> headers = getTestHeaders("POST");

        context.start();
        uploadFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        updateStatus.assertIsSatisfied();
        processFileQueue.assertIsSatisfied();

        // Verify file was uploaded to blob store
        InputStream receivedFile = internalInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile, "File should be uploaded to blob store");
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0, "Uploaded file should have content");

        // Verify IMPORT_TYPE header is NOT set to NETEX_FLEX for regular dataset uploads
        List<Exchange> exchanges = processFileQueue.getExchanges();
        assertEquals(1, exchanges.size());
        String importType = exchanges.getFirst().getIn().getHeader(IMPORT_TYPE, String.class);
        assertNotEquals(IMPORT_TYPE_NETEX_FLEX, importType, "IMPORT_TYPE should NOT be set to IMPORT_TYPE_NETEX_FLEX for regular dataset uploads");
    }

    @Test
    void uploadFlexNetexDatasetViaSpringApi() throws Exception {
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);

        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';
        String fileName = "netex-flex-test-spring-http-upload.zip";

        AdviceWith.adviceWith(context, "process-file-after-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue").replace().to("mock:processFileQueue");
        });

        updateStatus.expectedMessageCount(1);
        processFileQueue.expectedMessageCount(1);

        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build();
        Map<String, Object> headers = getTestHeaders("POST");

        context.start();
        uploadFlexFileTemplate.requestBodyAndHeaders(httpEntity, headers);

        updateStatus.assertIsSatisfied();
        processFileQueue.assertIsSatisfied();

        // Verify file was uploaded to blob store
        InputStream receivedFile = internalInMemoryBlobStoreRepository.getBlob(fileStorePath + fileName);
        assertNotNull(receivedFile, "File should be uploaded to blob store");
        byte[] fileContent = receivedFile.readAllBytes();
        assertTrue(fileContent.length > 0, "Uploaded file should have content");

        // Verify IMPORT_TYPE header was set to NETEX_FLEX in the process queue
        List<Exchange> exchanges = processFileQueue.getExchanges();
        assertEquals(1, exchanges.size());
        String importType = exchanges.getFirst().getIn().getHeader(IMPORT_TYPE, String.class);
        assertEquals(IMPORT_TYPE_NETEX_FLEX, importType, "IMPORT_TYPE should be set to IMPORT_TYPE_NETEX_FLEX for flex dataset uploads");
    }

    @Test
    void downloadFilteredDatasetViaSpringApi() throws Exception {
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);

        // Set up a blob in the store for download
        String blobPath = Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                + "rb_" + CHOUETTE_REFERENTIAL_RUT.toLowerCase()
                + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
        byte[] testContent = "test-netex-content".getBytes();
        internalInMemoryBlobStoreRepository.uploadBlob(blobPath, new ByteArrayInputStream(testContent));

        Map<String, Object> headers = getTestHeaders("GET");

        context.start();
        Exchange response = downloadFilteredDatasetTemplate.request(downloadFilteredDatasetTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setHeaders(headers);
                });

        assertNotNull(response.getMessage().getBody(byte[].class), "Response body should not be null");
        assertArrayEquals(testContent, response.getMessage().getBody(byte[].class), "Downloaded content should match uploaded content");
    }

    @Test
    void downloadFilteredDatasetNotFound() throws Exception {
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);

        // Don't set up any blob - should return 404
        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "GET",
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);

        context.start();
        Exchange response = downloadFilteredDatasetNotFoundTemplate.request(downloadFilteredDatasetNotFoundTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setHeaders(headers);
                });

        assertEquals(404, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    @Test
    void uploadDatasetUnknownCodespace() throws Exception {
        when(providerRepository.getProviderId("unknown_codespace")).thenReturn(null);

        String fileName = "netex-test-unknown-codespace.zip";

        HttpEntity httpEntity = MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build();
        Map<String, Object> headers = Map.of(
                Exchange.HTTP_METHOD, "POST",
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, "unknown_codespace");

        context.start();
        Exchange response = uploadFileUnknownCodespaceTemplate.request(uploadFileUnknownCodespaceTemplate.getDefaultEndpoint(),
                exchange -> {
                    exchange.getIn().setBody(httpEntity);
                    exchange.getIn().setHeaders(headers);
                });

        assertEquals(404, response.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
    }

    private static Map<String, Object> getTestHeaders(String method) {
        return Map.of(
                Exchange.HTTP_METHOD, method,
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);
    }
}
