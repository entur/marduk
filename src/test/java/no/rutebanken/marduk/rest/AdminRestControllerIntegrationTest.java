package no.rutebanken.marduk.rest;

import com.fasterxml.jackson.databind.ObjectReader;
import com.nimbusds.jose.JWSAlgorithm;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.TestConstants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.authorization.AuthorizationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static no.rutebanken.marduk.Constants.BLOBSTORE_PATH_INBOUND;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE;
import static no.rutebanken.marduk.Constants.IMPORT_TYPE_NETEX_FLEX;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static no.rutebanken.marduk.Constants.USERNAME;
import static no.rutebanken.marduk.TestConstants.CHOUETTE_REFERENTIAL_RUT;
import static no.rutebanken.marduk.TestConstants.PROVIDER_ID_AS_STRING_RUT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.config.Customizer.withDefaults;

/**
 * The admin API end to end: Tomcat, the security filter chain, the controller, and whatever the controller
 * hands the request to.
 *
 * <p>Driven over real HTTP rather than with MockMvc, because two of the things worth guarding are outside the
 * controller: that a multipart upload survives being read once on the request thread, and that Ninkasi's
 * {@code Accept: application/json} on a text/plain endpoint is not rejected with a 406.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
class AdminRestControllerIntegrationTest extends MardukSpringBootBaseTest {

    @TestConfiguration
    @EnableWebSecurity
    static class AdminRestControllerTestContextConfiguration {

        @Bean
        @ConditionalOnWebApplication
        public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(authz -> authz
                            .requestMatchers("/services/openapi").permitAll()
                            .requestMatchers("/services/openapi.yaml").permitAll()
                            .requestMatchers("/services/timetable_admin/openapi.yaml").permitAll()
                            .requestMatchers("/services/health").permitAll()
                            .anyRequest().authenticated())
                    .oauth2ResourceServer(configurer -> configurer.jwt(withDefaults()));
            return http.build();
        }

        @Bean
        public JwtDecoder jwtdecoder() {
            return token -> Jwt.withTokenValue("test-token")
                    .header("typ", "JWT")
                    .header("alg", JWSAlgorithm.RS256.getName())
                    .claim("iss", "https://test-issuer.entur.org")
                    .claim("scope", "openid profile email")
                    .claim("preferred_username", "test-user")
                    .subject("test-user")
                    .audience(Set.of("test-audience"))
                    .build();
        }

        @Bean
        public AuthorizationService<Long> testAuthorizationService() {
            return new TestAuthorizationService();
        }

        /** Publishes are asserted on directly; the emulator adds nothing this test needs. */
        @Bean
        @Primary
        public MardukPubSubPublisher recordingPublisher() {
            return new RecordingPubSubPublisher();
        }
    }

    @Value("${server.port}")
    private int port;

    @Value("#{'${timetable.export.blob.prefixes:outbound/gtfs/,outbound/netex/}'.split(',')}")
    private List<String> exportFileStaticPrefixes;

    @org.springframework.beans.factory.annotation.Autowired
    private MardukPubSubPublisher publisher;

    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

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

    // ------------------------------------------------------------------------------------------ health

    @Test
    void healthIsServedAsPlainText() throws Exception {

        Response response = send(new HttpGet(url("/services/health")));

        assertEquals(200, response.status());
        assertEquals("OK", response.body());
    }

    @Test
    void theApiDocumentIsServedAtBothOfItsPaths() throws Exception {

        assertTrue(send(new HttpGet(url("/services/openapi.yaml"))).body().contains("/services/timetable_admin/"),
                "the document does not describe the admin API");
        assertEquals(200, send(new HttpGet(url("/services/timetable_admin/openapi.yaml"))).status());
    }

    @Test
    void theApiDocumentDescribesEveryEndpoint() throws Exception {
        // The document used to be generated from the same REST DSL that defined the routes, so it could not
        // drift. It is generated from these annotations now, which can only drift if springdoc stops seeing
        // them - so check that it still sees all of them.
        String document = send(new HttpGet(url("/services/openapi.yaml"))).body();

        for (String path : mappedPaths(AdminRestController.class)) {
            assertTrue(document.contains(path + ":"), path + " is missing from the API document");
        }
    }

    private static List<String> mappedPaths(Class<?> controller) {
        return Arrays.stream(controller.getDeclaredMethods())
                .flatMap(method -> Stream.of(
                                Optional.ofNullable(method.getAnnotation(GetMapping.class)).map(GetMapping::value),
                                Optional.ofNullable(method.getAnnotation(PostMapping.class)).map(PostMapping::value),
                                Optional.ofNullable(method.getAnnotation(DeleteMapping.class)).map(DeleteMapping::value))
                        .flatMap(Optional::stream)
                        .flatMap(Arrays::stream))
                .distinct()
                .toList();
    }

    // ------------------------------------------------------------------------------------------ import

    @Test
    void anImportIsQueuedOncePerFileInTheOrderGiven() throws Exception {

        BlobStoreFiles files = new BlobStoreFiles();
        files.add(new BlobStoreFiles.File("file1", null, null, null));
        files.add(new BlobStoreFiles.File("file2", null, null, null));

        Response response = send(postJson(
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/import", json(files)));

        assertEquals(200, response.status());
        List<RecordingPubSubPublisher.Published> queued = recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE);
        assertEquals(2, queued.size());
        assertEquals(PROVIDER_ID_AS_STRING_RUT, queued.getFirst().attributes().get(PROVIDER_ID));
        assertEquals(BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + "/file1",
                queued.getFirst().attributes().get(FILE_HANDLE));
        assertEquals(BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + "/file2",
                queued.get(1).attributes().get(FILE_HANDLE));
    }

    @Test
    void eachQueuedImportGetsItsOwnCorrelationId() throws Exception {

        BlobStoreFiles files = new BlobStoreFiles();
        files.add(new BlobStoreFiles.File("file1", null, null, null));
        files.add(new BlobStoreFiles.File("file2", null, null, null));

        send(postJson("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/import", json(files)));

        List<RecordingPubSubPublisher.Published> queued = recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE);
        assertNotEquals(queued.getFirst().attributes().get(CORRELATION_ID),
                queued.get(1).attributes().get(CORRELATION_ID),
                "two files reimported together must be two jobs in nabu, not one");
    }

    /** Ninkasi sends {@code Accept: application/json} to endpoints that answer with text/plain. */
    @Test
    void aJsonAcceptHeaderIsNotRejected() throws Exception {

        BlobStoreFiles files = new BlobStoreFiles();
        files.add(new BlobStoreFiles.File("file1", null, null, null));
        HttpPost request = postJson(
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/import", json(files));
        request.setHeader(HttpHeaders.ACCEPT, "application/json");

        assertEquals(200, send(request).status());
        assertEquals(1, recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE).size());
    }

    @Test
    void anUnknownCleanFilterIsABadRequest() throws Exception {
        // The route validated the same three values but turned a typo into a 500.

        assertEquals(400, send(post("/services/timetable_admin/clean/level3")).status());
    }

    /**
     * The same exception, deliberately two answers. The timetable-management spec Entur publishes to its
     * partners documents 200, 401, 403, 404 and 500 for these endpoints and no 400, and Camel answered 500
     * there; a client that integrated against that contract must keep getting it. Ninkasi is ours, so the
     * admin API keeps the more useful 400. Do not unify these.
     */
    @Test
    void aBadArgumentIs400OnTheAdminApiAnd500OnThePublishedPartnerApi() throws Exception {
        assertEquals(400, send(post("/services/timetable_admin/clean/level3")).status());

        HttpPost partnerUploadWithNoFilePart = post(
                "/services/timetable-management/datasets/" + CHOUETTE_REFERENTIAL_RUT);
        partnerUploadWithNoFilePart.setEntity(MultipartEntityBuilder.create()
                .addBinaryBody("not-the-file-part", "irrelevant".getBytes(),
                        ContentType.DEFAULT_BINARY, "ignored.zip")
                .build());

        assertEquals(500, send(partnerUploadWithNoFilePart).status());
    }

    @Test
    void anUnknownProviderIsNotFound() throws Exception {

        assertEquals(404, send(post("/services/timetable_admin/999999/export")).status());
    }

    // ------------------------------------------------------------------------------------------ export

    @Test
    void anExportIsQueuedForTheProvider() throws Exception {

        Response response = send(postJson(
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/export", ""));

        assertEquals(200, response.status());
        Map<String, String> attributes = recorded()
                .publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE).getFirst().attributes();
        assertEquals(PROVIDER_ID_AS_STRING_RUT, attributes.get(PROVIDER_ID));
    }

    @Test
    void theTriggeringUserIsRecordedOnTheJob() throws Exception {
        // nabu shows this as who started the job. The routes read it from the security context in
        // direct:setUsername; a controller reads the same context on the request thread.

        send(postJson("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/export", ""));

        assertEquals("test-user", recorded()
                .publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE).getFirst().attributes().get(USERNAME));
    }

    @Test
    void theRequestsBearerTokenIsNotPublished() throws Exception {
        // The routes had to strip the request headers because platform-http copied them onto the exchange.
        // A message built in the controller never carries them, which is what this pins.

        HttpPost request = postJson("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/export", "");
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer sensitive-secret-token");

        send(request);

        Map<String, String> attributes = recorded()
                .publishedTo(MardukQueues.CHOUETTE_EXPORT_NETEX_QUEUE).getFirst().attributes();
        assertFalse(attributes.containsKey(HttpHeaders.AUTHORIZATION));
        assertFalse(attributes.keySet().stream().anyMatch(key -> key.toLowerCase().contains("breadcrumb")));
    }

    @Test
    void aProviderThatMigratesItsDataOnwardsIsOnlyValidatedAtLevelOne() throws Exception {
        // Level 2 happens in the dataspace it migrates into, not here.

        send(postJson("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/validate", ""));

        assertEquals("VALIDATION_LEVEL_1", validationLevelPublished());
    }

    @Test
    void aProviderThatKeepsItsOwnDataIsValidatedAtLevelTwo() throws Exception {
        when(providerRepository.getProvider(4242L)).thenReturn(provider("rb_own", 4242L, null));

        send(postJson("/services/timetable_admin/4242/validate", ""));

        assertEquals("VALIDATION_LEVEL_2", validationLevelPublished());
    }

    private String validationLevelPublished() {
        return recorded().publishedTo(MardukQueues.CHOUETTE_VALIDATION_QUEUE).getFirst()
                .attributes().get(Constants.CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL);
    }

    /**
     * Ninkasi sends {@code Accept: application/json} everywhere, including to endpoints that answer with
     * text/plain or a binary body. Camel's platform-http was configured not to check
     * ({@code serverRequestValidation=false}); Spring MVC answers 406 unless nothing declares a produces.
     */
    @Test
    void noEndpointRejectsNinkasisAcceptHeader() throws Exception {
        internalInMemoryBlobStoreRepository.uploadBlob(
                BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + "/netex.zip", getTestNetexArchiveAsStream());

        for (HttpUriRequestBase request : List.of(
                post("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/export"),
                post("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/validate"),
                post("/services/timetable_admin/routing_graph/build"),
                get("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files"),
                get("/services/timetable_admin/export/files"),
                get("/services/timetable_admin/routing_graph/graphs"),
                get("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files/netex.zip"),
                get("/services/health"))) {
            request.setHeader(HttpHeaders.ACCEPT, "application/json");
            assertEquals(200, send(request).status(), request.getMethod() + " " + request.getRequestUri());
        }
    }

    /**
     * The same trap, guarded over the mappings themselves rather than a list of paths: Spring MVC answers 406
     * when a mapping declares a {@code produces} the request's {@code Accept} does not cover, and Ninkasi
     * sends {@code Accept: application/json} everywhere - including to endpoints that answer with text or
     * bytes. Camel's platform-http did not check at all.
     *
     * <p>Skipped, and only these: the three mappings the timetable-management interfaces generate from the
     * published spec, whose media types are the partner contract and predate the Camel removal. The skip is
     * per method, not per controller, so a mapping written by hand on the same controller is still checked -
     * and the count is asserted, so a generated mapping cannot appear or disappear unnoticed.
     */
    @Test
    void noMappingDeclaresAProducesThatWouldRejectJson() {
        List<String> generated = new ArrayList<>();
        List<String> offenders = new ArrayList<>();
        handlerMapping.getHandlerMethods().forEach((mapping, handler) -> {
            if (!handler.getBeanType().getPackageName().startsWith("no.rutebanken.marduk")) {
                return;
            }
            String endpoint = mapping + " -> " + handler.getMethod().getName();
            if (isGeneratedFromThePublishedSpec(handler.getMethod())) {
                generated.add(endpoint);
            } else if (wouldReject(mapping, MediaType.APPLICATION_JSON)) {
                offenders.add(endpoint);
            }
        });

        assertEquals(3, generated.size(),
                "the skipped set is no longer the generated partner API alone: " + generated);
        assertTrue(offenders.isEmpty(), "these would answer 406 to Accept: application/json: " + offenders);
    }

    /** True only for a method the controller inherits from a generated timetable-management interface. */
    private static boolean isGeneratedFromThePublishedSpec(Method method) {
        return Arrays.stream(method.getDeclaringClass().getInterfaces())
                .filter(api -> api.getPackageName().startsWith("no.rutebanken.marduk.rest.openapi.api"))
                .anyMatch(api -> declares(api, method));
    }

    private static boolean declares(Class<?> type, Method method) {
        try {
            type.getMethod(method.getName(), method.getParameterTypes());
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean wouldReject(RequestMappingInfo mapping, MediaType accepted) {
        Set<MediaType> produced = mapping.getProducesCondition().getProducibleMediaTypes();
        return !produced.isEmpty() && produced.stream().noneMatch(accepted::isCompatibleWith);
    }

    // ------------------------------------------------------------------------------------------- files

    @Test
    void theFilesAvailableForReimportAreListedByNameAlone() throws Exception {
        String testFileName = "ruter_fake_data.zip";
        internalInMemoryBlobStoreRepository.uploadBlob(
                BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/' + testFileName,
                getTestNetexArchiveAsStream());

        Response response = send(get("/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files"));

        BlobStoreFiles listed = blobStoreFiles(response.body());
        assertEquals(1, listed.getFiles().size());
        assertEquals(testFileName, listed.getFiles().getFirst().getName(),
                "the file name should not be prefixed by the file store path");
    }

    @Test
    void aFileCanBeDownloadedByName() throws Exception {
        internalInMemoryBlobStoreRepository.uploadBlob(
                BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + "/netex.zip", getTestNetexArchiveAsStream());

        Response response = send(get(
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files/netex.zip"));

        assertEquals(200, response.status());
        assertArrayEquals(getTestNetexArchiveAsStream().readAllBytes(), response.bytes());
    }

    @Test
    void anUnknownFileIsNotFound() throws Exception {

        assertEquals(404, send(get(
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files/unknown-file.zip")).status());
    }

    @Test
    void theExportedFilesAreListedAcrossEveryConfiguredPrefix() throws Exception {
        String testFileName = "netex.zip";
        for (String prefix : exportFileStaticPrefixes) {
            mardukInMemoryBlobStoreRepository.uploadBlob(prefix + testFileName, getTestNetexArchiveAsStream());
        }

        BlobStoreFiles listed = blobStoreFiles(send(get("/services/timetable_admin/export/files")).body());

        assertEquals(exportFileStaticPrefixes.size(), listed.getFiles().size());
        assertTrue(exportFileStaticPrefixes.stream().allMatch(prefix -> listed.getFiles().stream()
                .anyMatch(file -> (prefix + testFileName).equals(file.getName()))));
    }

    @Test
    void fileTimestampsStayEpochMillis() throws Exception {
        // Camel's REST binding wrote dates as timestamps; Spring's ObjectMapper writes ISO strings unless
        // told otherwise, and Ninkasi parses numbers.
        mardukInMemoryBlobStoreRepository.uploadBlob(
                exportFileStaticPrefixes.getFirst() + "netex.zip", getTestNetexArchiveAsStream());

        String body = send(get("/services/timetable_admin/export/files")).body();

        assertTrue(body.matches(".*\"created\":[0-9]+.*"), "created was not an epoch number: " + body);
        assertTrue(body.matches(".*\"updated\":[0-9]+.*"), "updated was not an epoch number: " + body);
    }

    // ------------------------------------------------------------------------------------------ upload

    @Test
    void aSmallFileIsStoredAndTheImportStarted() throws Exception {
        upload(getTestNetexArchiveAsStream(), "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files");
    }

    @Test
    void aFileTooLargeForTheHeapIsStoredAndTheImportStarted() throws Exception {
        upload(getLargeTestNetexArchiveAsStream(),
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files");
    }

    @Test
    void aFlexUploadIsMarkedAsAFlexImport() throws Exception {
        upload(getTestNetexArchiveAsStream(),
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/flex/files");

        assertEquals(IMPORT_TYPE_NETEX_FLEX, recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE)
                .getFirst().attributes().get(IMPORT_TYPE));
    }

    @Test
    void theDeprecatedCodespaceUploadStillWorks() throws Exception {
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);

        upload(getTestNetexArchiveAsStream(),
                "/services/timetable_admin/upload/" + CHOUETTE_REFERENTIAL_RUT);
    }

    @Test
    void theDeprecatedBlocksDownloadStillWorks() throws Exception {
        when(providerRepository.getProviderId(CHOUETTE_REFERENTIAL_RUT)).thenReturn(TestConstants.PROVIDER_ID_RUT);
        internalInMemoryBlobStoreRepository.uploadBlob(
                Constants.BLOBSTORE_PATH_NETEX_BLOCKS_EXPORT + "rb_rut-aggregated-netex.zip",
                getTestNetexArchiveAsStream());

        Response response = send(get(
                "/services/timetable_admin/download_netex_blocks/" + CHOUETTE_REFERENTIAL_RUT));

        assertEquals(200, response.status());
        assertArrayEquals(getTestNetexArchiveAsStream().readAllBytes(), response.bytes());
    }

    @Test
    void aPostThatIsNotMultipartIsUnsupportedMediaType() throws Exception {
        // The route read the request parts, which threw InvalidContentTypeException and was mapped to 415.
        // Spring hands an empty part map to the controller instead, so the caller got a 200 and no upload.
        for (String path : List.of(
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/files",
                "/services/timetable_admin/" + PROVIDER_ID_AS_STRING_RUT + "/flex/files",
                "/services/timetable_admin/upload/" + CHOUETTE_REFERENTIAL_RUT)) {
            assertEquals(415, send(postJson(path, "{}")).status(), path);
        }
        assertTrue(recorded().published().isEmpty(), "something was queued for a request with no file");
    }

    /**
     * Posts one multipart file and asserts it reached the blob store and the pipeline.
     *
     * <p>Nothing about the upload path is stubbed out beyond the publisher: the job status reporting is
     * real, because it used to stringify every header and destroy the live upload stream, and stubbing it
     * would hide that class of bug.
     */
    private void upload(InputStream file, String path) throws Exception {
        String fileName = "netex-test-POST.zip";

        HttpPost request = new HttpPost(url(path));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        // Named after the file, not "file": that is what Bel sends, and the multipart route it replaced
        // read the parts without looking at their names.
        request.setEntity(MultipartEntityBuilder.create()
                .addBinaryBody(fileName, file, ContentType.DEFAULT_BINARY, fileName)
                .build());

        assertEquals(200, send(request).status());

        assertEquals(1, recorded().publishedTo(MardukQueues.PROCESS_FILE_QUEUE).size(),
                "the import pipeline was not started for the uploaded file");
        InputStream stored = internalInMemoryBlobStoreRepository.getBlob(
                BLOBSTORE_PATH_INBOUND + CHOUETTE_REFERENTIAL_RUT + '/' + fileName);
        assertNotNull(stored, "the uploaded file was not stored");
        assertTrue(stored.readAllBytes().length > 0, "the uploaded file was stored empty");
    }

    // --------------------------------------------------------------------------------------- plumbing

    private record Response(int status, byte[] bytes) {
        String body() {
            return new String(bytes);
        }
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpGet get(String path) {
        HttpGet request = new HttpGet(url(path));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        return request;
    }

    private HttpPost post(String path) {
        HttpPost request = new HttpPost(url(path));
        request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        return request;
    }

    private HttpPost postJson(String path, String body) {
        HttpPost request = post(path);
        request.setEntity(new StringEntity(body, ContentType.APPLICATION_JSON));
        return request;
    }

    private Response send(HttpUriRequestBase request) throws IOException {
        return client.execute(request, response -> new Response(
                response.getCode(),
                response.getEntity() == null ? new byte[0] : EntityUtils.toByteArray(response.getEntity())));
    }

    private static String json(BlobStoreFiles files) throws IOException {
        return ObjectMapperFactory.getSharedObjectMapper().writerFor(BlobStoreFiles.class).writeValueAsString(files);
    }

    private static BlobStoreFiles blobStoreFiles(String body) throws IOException {
        ObjectReader reader = ObjectMapperFactory.getSharedObjectMapper().readerFor(BlobStoreFiles.class);
        return reader.readValue(body);
    }
}
