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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Integration tests for external REST API endpoints (AdminExternalRestRouteBuilder).
 * These endpoints are designed for machine-to-machine communication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class AdminExternalRestMardukRouteBuilderIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    @EndpointInject("mock:processFileQueue")
    protected MockEndpoint processFileQueue;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @Produce("http:localhost:{{server.port}}/services/timetable-management/flex-datasets/" + CHOUETTE_REFERENTIAL_RUT)
    protected ProducerTemplate uploadFlexFileExternalTemplate;

    @Value("${server.port}")
    private int serverPort;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @BeforeEach
    void setUpProvider() {
        when(providerRepository.getReferential(TestConstants.PROVIDER_ID_RUT)).thenReturn(CHOUETTE_REFERENTIAL_RUT);
    }

    @Test
    void uploadFlexNetexDatasetViaExternalApi() throws Exception {

        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);

        String fileStorePath = Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/';
        String fileName = "netex-flex-test-http-upload.zip";

        AdviceWith.adviceWith(context, "process-file-after-import", a -> {
            a.interceptSendToEndpoint("direct:updateStatus").skipSendToOriginalEndpoint().to("mock:updateStatus");
            a.weaveByToUri("google-pubsub:(.*):ProcessFileQueue").replace().to("mock:processFileQueue");
        });

        updateStatus.expectedMessageCount(1);
        processFileQueue.expectedMessageCount(1);

        org.apache.hc.core5.http.HttpEntity httpEntity = MultipartEntityBuilder.create().addBinaryBody(fileName, getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName).build();
        Map<String, Object> headers = getTestHeaders("POST");

        context.start();
        uploadFlexFileExternalTemplate.requestBodyAndHeaders(httpEntity, headers);

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
    void authorizationHeaderIsReturnedToCaller() throws Exception {
        AdviceWith.adviceWith(context, "admin-external-upload-flex-file", a ->
                a.weaveByToUri("direct:uploadFilesAndStartImport").replace().to("mock:sink"));

        byte[] filePayload;
        try (InputStream inputStream = getTestNetexArchiveAsStream()) {
            filePayload = inputStream.readAllBytes();
        }

        ByteArrayResource fileResource = new ByteArrayResource(filePayload) {
            @Override
            public String getFilename() {
                return "netex-flex-test-http-upload.zip";
            }
        };

        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        HttpEntity<ByteArrayResource> filePart = new HttpEntity<>(fileResource, partHeaders);

        MultiValueMap<String, Object> multipartBody = new LinkedMultiValueMap<>();
        multipartBody.add(fileResource.getFilename(), filePart);

        Map<String, Object> headers = getTestHeaders("POST");
        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        requestHeaders.set(HttpHeaders.AUTHORIZATION, headers.get(HttpHeaders.AUTHORIZATION).toString());
        requestHeaders.set(CHOUETTE_REFERENTIAL, headers.get(CHOUETTE_REFERENTIAL).toString());

        RestTemplate restTemplate = restTemplateBuilder.requestFactory(SimpleClientHttpRequestFactory::new).build();

        context.start();
        ResponseEntity<String> response = restTemplate.exchange(
                URI.create("http://localhost:" + serverPort + "/services/timetable-management/flex-datasets/" + CHOUETTE_REFERENTIAL_RUT),
                HttpMethod.POST,
                new HttpEntity<>(multipartBody, requestHeaders),
                String.class);

        List<String> authorizationHeader = response.getHeaders().get(HttpHeaders.AUTHORIZATION);
        assertTrue(authorizationHeader == null || authorizationHeader.isEmpty(),
                "Authorization header should not be returned to caller but was " + authorizationHeader);
    }

    private static Map<String, Object> getTestHeaders(String method) {
        return Map.of(
                Exchange.HTTP_METHOD, method,
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);
    }
}
