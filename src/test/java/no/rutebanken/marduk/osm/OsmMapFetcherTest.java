package no.rutebanken.marduk.osm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsmMapFetcherTest {

    private static final String CURRENT_MD5 = "471f160aa5580e7943acf1fb1c268d29";
    private static final String CURRENT_MD5_FILE_CONTENT = CURRENT_MD5 + "  norway-latest.osm.pbf\n";
    private static final String PREVIOUS_MD5_FILE_CONTENT = "0123456789abcdef0123456789abcdef  norway-latest.osm.pbf\n";
    private static final String MD5_PATH = "osm/norway-latest.osm.pbf.md5";
    private static final String UPDATED = "The map file was updated. Triggered the base graph build";
    private static final String UNCHANGED = "No need to build the base graph, as the MD5 sum of the map file has not changed";

    private HttpServer server;
    private InMemoryMardukBlobStoreRepository blobStoreRepository;
    private MardukPublicBlobStoreService blobStore;
    private RecordingPubSubPublisher publisher;

    /** What the source serves as the checksum file. The map itself is never requested. */
    private String servedMd5File = CURRENT_MD5_FILE_CONTENT;
    private final java.util.List<String> requestedPaths = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::serve);
        server.start();
        blobStoreRepository = new InMemoryMardukBlobStoreRepository(new java.util.concurrent.ConcurrentHashMap<>());
        blobStoreRepository.setContainerName("marduk");
        blobStore = new MardukPublicBlobStoreService("marduk", blobStoreRepository);
        publisher = new RecordingPubSubPublisher();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serve(HttpExchange exchange) throws IOException {
        requestedPaths.add(exchange.getRequestURI().getPath());
        byte[] bytes = servedMd5File.getBytes(StandardCharsets.UTF_8);
        // a typical HTTP response header, which must not end up on the build request
        exchange.getResponseHeaders().add("ETag", "\"" + CURRENT_MD5 + "\"");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private OsmMapFetcher fetcher() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/norway-latest.osm.pbf";
        return new OsmMapFetcher(blobStore, publisher, () -> true, noRetries(), url, "osm");
    }

    private void storeMd5(String content) {
        blobStoreRepository.uploadBlob(MD5_PATH, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String storedMd5() throws IOException {
        var blob = blobStoreRepository.getBlob(MD5_PATH);
        return blob == null ? null : new String(blob.readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void aBaseGraphBuildIsTriggeredWhenNoChecksumIsStored() throws IOException {
        assertEquals(UPDATED, fetcher().fetchIfChanged());

        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE).size());
        assertEquals(CURRENT_MD5_FILE_CONTENT, storedMd5(), "the checksum file is kept verbatim");
    }

    @Test
    void aBaseGraphBuildIsTriggeredWhenTheChecksumHasChanged() throws IOException {
        storeMd5(PREVIOUS_MD5_FILE_CONTENT);

        assertEquals(UPDATED, fetcher().fetchIfChanged());

        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE).size());
        assertEquals(CURRENT_MD5_FILE_CONTENT, storedMd5());
    }

    @Test
    void nothingIsTriggeredWhenTheChecksumIsUnchanged() throws IOException {
        storeMd5(CURRENT_MD5_FILE_CONTENT);

        assertEquals(UNCHANGED, fetcher().fetchIfChanged());

        assertEquals(0, publisher.published().size(), "a base graph build was triggered for an unchanged map");
        assertEquals(CURRENT_MD5_FILE_CONTENT, storedMd5());
    }

    @Test
    void onlyTheChecksumFileIsFetchedSinceTheGraphBuilderReadsTheMapItself() {
        fetcher().fetchIfChanged();

        assertEquals(java.util.List.of("/norway-latest.osm.pbf.md5"), requestedPaths);
        assertFalse(blobStoreRepository.exist("osm/norway-latest.osm.pbf"), "the map was stored by marduk");
    }

    @Test
    void onlyTheCorrelationIdIsPublishedWithTheBuildRequest() {
        fetcher().fetchIfChanged();

        // Without it the graph build and everything downstream of it logs with an empty MDC, so there is no
        // way to tie the build back to the map that caused it.
        RecordingPubSubPublisher.Published triggered =
                publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE).getFirst();
        assertNotNull(triggered.attributes().get(CORRELATION_ID), "no correlation id on the graph build");
        assertEquals(java.util.Set.of(CORRELATION_ID), triggered.attributes().keySet());
    }

    @Test
    void aFailedTriggerLeavesTheChecksumSoTheNextRunRetries() throws IOException {
        // The checksum is the record that this map has been dealt with. Committing it before the trigger
        // meant a failed publish left marduk believing the new map was handled, and the graph was never
        // rebuilt from it.
        storeMd5(PREVIOUS_MD5_FILE_CONTENT);
        publisher.failsFor(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE);

        assertThrows(MardukException.class, () -> fetcher().fetchIfChanged());

        assertEquals(PREVIOUS_MD5_FILE_CONTENT, storedMd5());
        publisher.failsFor(null);
        assertEquals(UPDATED, fetcher().fetchIfChanged(), "the next run believed the new map had been dealt with");
        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE).size());
    }

    @Test
    void anInvalidChecksumFileIsNeitherComparedNorStored() throws IOException {
        servedMd5File = "";
        storeMd5(PREVIOUS_MD5_FILE_CONTENT);

        assertThrows(MardukException.class, () -> fetcher().fetchIfChanged());

        assertEquals(0, publisher.published().size());
        assertEquals(PREVIOUS_MD5_FILE_CONTENT, storedMd5());
    }

    @Test
    void onlyTheLeaderRunsTheScheduledCheck() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/norway-latest.osm.pbf";

        new OsmMapFetcher(blobStore, publisher, () -> false, noRetries(), url, "osm")
                .fetchIfChangedOnSchedule();

        assertEquals(0, publisher.published().size());
        assertTrue(requestedPaths.isEmpty());
    }

    @Test
    void theScheduledCheckSwallowsAnInvalidChecksumFile() {
        // A bad upstream file must not become an endless retry, and the next firing picks up a corrected one.
        servedMd5File = "<html>error</html>";

        fetcher().fetchIfChangedOnSchedule();

        assertEquals(0, publisher.published().size());
    }
}
