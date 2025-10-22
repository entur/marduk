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

        HttpEntity httpEntity = MultipartEntityBuilder.create().addBinaryBody(fileName, getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName).build();
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

    private static Map<String, Object> getTestHeaders(String method) {
        return Map.of(
                Exchange.HTTP_METHOD, method,
                HttpHeaders.AUTHORIZATION, "Bearer test-token",
                CHOUETTE_REFERENTIAL, CHOUETTE_REFERENTIAL_RUT);
    }
}
