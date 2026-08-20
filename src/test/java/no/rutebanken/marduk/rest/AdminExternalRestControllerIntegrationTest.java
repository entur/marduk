package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static no.rutebanken.marduk.Constants.IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * The machine-to-machine timetable-management API end to end, over real HTTP.
 *
 * <p>Nothing on the upload path is stubbed out beyond the publisher: the job status reporting is real,
 * because it used to stringify every header and destroy the live upload stream, and stubbing it would hide
 * that class of bug.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class AdminExternalRestControllerIntegrationTest extends MardukSpringBootBaseTest {

    private static final String DATASETS = "/services/timetable-management/datasets/";
    private static final String FLEX_DATASETS = "/services/timetable-management/flex-datasets/";

    /**
     * The recording publisher comes from {@link AdminRestControllerIntegrationTest}'s nested
     * {@code @TestConfiguration}, which reaches every Spring test in the suite: {@code TestApp} spells out
     * {@code @ComponentScan}, and doing so replaces Boot's default {@code TypeExcludeFilter} instead of
     * adding to it. The same leak is what supplies the suite's {@code AuthorizationService}, so declaring a
     * second publisher here would only collide with the first. Left as it is deliberately; putting the filter
     * back means giving every Spring test its own authorization bean, which is its own change.
     */
    @Autowired
    private MardukPubSubPublisher publisher;

    @Value("${server.port}")
    private int port;

    private CloseableHttpClient client;

    @BeforeEach
    void setUpClient() {
        client = HttpClients.createDefault();
        when(providerRepository.getReferential(TestConstants.PROVIDER_ID_RUT)).thenReturn(CHOUETTE_REFERENTIAL_RUT);
        recorded().clear();
    }

    @AfterEach
    void closeClient() throws IOException {
        client.close();
    }

    private RecordingPubSubPublisher recorded() {
        return (RecordingPubSubPublisher) publisher;
    }

    @Test
    void aDatasetIsStoredAndTheImportStarted() throws Exception {
        knownCodespace();
        String fileName = "netex-test-spring-http-upload.zip";

        assertEquals(200, send(upload(DATASETS + CHOUETTE_REFERENTIAL_RUT, fileName)).status());

        assertStored(fileName);
        assertEquals(1, recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE).size());
    }

    @Test
    void anOrdinaryDatasetIsNotMarkedAsAFlexImport() throws Exception {
        knownCodespace();

        send(upload(DATASETS + CHOUETTE_REFERENTIAL_RUT, "netex.zip"));

        assertNotEquals(IMPORT_TYPE_NETEX_FLEX, importTypePublished());
    }

    @Test
    void aFlexDatasetIsMarkedAsAFlexImport() throws Exception {
        knownCodespace();
        String fileName = "netex-flex-test-spring-http-upload.zip";

        assertEquals(200, send(upload(FLEX_DATASETS + CHOUETTE_REFERENTIAL_RUT, fileName)).status());

        assertStored(fileName);
        assertEquals(IMPORT_TYPE_NETEX_FLEX, importTypePublished());
    }

    @Test
    void theFilteredDatasetIsDownloadedAsItWasStored() throws Exception {
        knownCodespace();
        byte[] content = "test-netex-content".getBytes();
        internalInMemoryBlobStoreRepository.uploadBlob(filteredDatasetPath(), new ByteArrayInputStream(content));

        Response response = send(get(DATASETS + CHOUETTE_REFERENTIAL_RUT + "/filtered"));

        assertEquals(200, response.status());
        assertArrayEquals(content, response.bytes());
    }

    @Test
    void aMissingFilteredDatasetIsNotFound() throws Exception {
        knownCodespace();

        assertEquals(404, send(get(DATASETS + CHOUETTE_REFERENTIAL_RUT + "/filtered")).status());
    }

    @Test
    void anUploadForAnUnknownCodespaceIsNotFound() throws Exception {
        when(providerRepository.getProviderId("unknown_codespace")).thenReturn(null);

        assertEquals(404, send(upload(DATASETS + "unknown_codespace", "netex.zip")).status());
    }

    @Test
    void anUploadThatFailsAnswers500() throws Exception {
        // An upload that did not happen must not answer 200. getProviderId answers, so authorization passes,
        // but the provider itself is not there - which is what the upload needs in order to decide whether to
        // start the import.
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(4242L);

        assertEquals(500, send(upload(DATASETS + CHOUETTE_REFERENTIAL_RUT, "netex-upload-failure.zip")).status());
    }

    @Test
    void anUploadWithoutAFilePartIsRejected() throws Exception {
        knownCodespace();
        HttpPost request = post(DATASETS + CHOUETTE_REFERENTIAL_RUT);
        request.setEntity(MultipartEntityBuilder.create()
                .addBinaryBody("not-the-file-part", "irrelevant".getBytes(), ContentType.DEFAULT_BINARY, "ignored.zip")
                .build());

        assertTrue(send(request).status() >= 400, "an upload with no file part answered a success");
        assertTrue(recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE).isEmpty());
    }

    // --------------------------------------------------------------------------------------- plumbing

    private void knownCodespace() {
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);
    }

    private static String filteredDatasetPath() {
        return Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT
                + "rb_" + CHOUETTE_REFERENTIAL_RUT.toLowerCase()
                + "-" + Constants.CURRENT_AGGREGATED_NETEX_FILENAME;
    }

    private void assertStored(String fileName) throws IOException {
        InputStream stored = internalInMemoryBlobStoreRepository.getBlob(
                Constants.BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/' + fileName);
        assertNotNull(stored, "the uploaded file was not stored");
        assertTrue(stored.readAllBytes().length > 0, "the uploaded file was stored empty");
    }

    private String importTypePublished() {
        return recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE).getFirst().attributes().get(IMPORT_TYPE);
    }

    private record Response(int status, byte[] bytes) {
    }

    private HttpPost upload(String path, String fileName) {
        HttpPost request = post(path);
        request.setEntity(MultipartEntityBuilder.create()
                .addBinaryBody("file", getTestNetexArchiveAsStream(), ContentType.DEFAULT_BINARY, fileName)
                .build());
        return request;
    }

    private HttpPost post(String path) {
        HttpPost request = new HttpPost("http://localhost:" + port + path);
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        return request;
    }

    private HttpGet get(String path) {
        HttpGet request = new HttpGet("http://localhost:" + port + path);
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        return request;
    }

    private Response send(HttpUriRequestBase request) throws IOException {
        return client.execute(request, response -> new Response(
                response.getCode(),
                response.getEntity() == null ? new byte[0] : EntityUtils.toByteArray(response.getEntity())));
    }
}
