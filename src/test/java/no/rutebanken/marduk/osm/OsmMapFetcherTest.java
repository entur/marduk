package no.rutebanken.marduk.osm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.exceptions.Md5ChecksumValidationException;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.InMemoryMardukBlobStoreRepository;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static no.rutebanken.marduk.pipeline.RetryPolicies.retriesWithoutWaiting;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsmMapFetcherTest {

    private static final String MAP = "osm-pbf-contents";
    private static final String MAP_PATH = "osm/norway-latest.osm.pbf";
    private static final String MD5_PATH = "osm/norway-latest.osm.pbf.md5";

    private HttpServer server;
    private InMemoryMardukBlobStoreRepository blobStoreRepository;
    private MardukPublicBlobStoreService blobStore;
    private RecordingPubSubPublisher publisher;

    /** What the source serves. */
    private String servedMap = MAP;
    private String servedMd5File = DigestUtils.md5Hex(MAP) + "  norway-latest.osm.pbf";

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
        String payload = exchange.getRequestURI().getPath().endsWith(".md5") ? servedMd5File : servedMap;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
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

    @Test
    void aChangedChecksumFetchesTheMapAndTriggersABaseGraphBuild() {
        storeMd5("0000  norway-latest.osm.pbf");

        String outcome = fetcher().fetchIfChanged();

        assertEquals("Need to fetch map file. Called update map route", outcome);
        assertTrue(blobStoreRepository.exist(MAP_PATH));
        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE).size());
    }

    @Test
    void anUnchangedChecksumFetchesNothing() {
        storeMd5(servedMd5File);

        String outcome = fetcher().fetchIfChanged();

        assertEquals("No need to updated the map file, as the MD5 sum has not changed", outcome);
        assertTrue(blobStoreRepository.exist(MD5_PATH));
        assertEquals(0, publisher.published().size(), "a base graph build was triggered for an unchanged map");
    }

    @Test
    void aMissingStoredChecksumCountsAsChanged() {
        // First run against an empty bucket: there is nothing to compare, so the map has to be fetched.
        String outcome = fetcher().fetchIfChanged();

        assertEquals("Need to fetch map file. Called update map route", outcome);
        assertTrue(blobStoreRepository.exist(MAP_PATH));
    }

    @Test
    void theStoredChecksumFileIsKeptVerbatim() throws IOException {
        // The source publishes "<md5>  <filename>" and the whole line is stored, as before; only the first
        // field is ever compared.
        fetcher().fetch();

        assertArrayEquals(
                servedMd5File.getBytes(StandardCharsets.UTF_8),
                blobStoreRepository.getBlob(MD5_PATH).readAllBytes());
    }

    @Test
    void aTruncatedDownloadIsRejected() {
        servedMd5File = DigestUtils.md5Hex("something-else") + "  norway-latest.osm.pbf";

        assertThrows(Md5ChecksumValidationException.class, () -> fetcher().fetch());
        assertTrue(!blobStoreRepository.exist(MAP_PATH), "a map that failed its checksum was stored anyway");
    }

    @Test
    void aTruncatedDownloadLeavesTheStoredChecksumAloneSoTheNextRunRetries() throws IOException {
        // The Camel version stored the checksum before verifying the map, so a truncated download left the
        // stored checksum matching the source and fetchIfChanged then saw nothing to do - the stale map
        // survived until the source published a new file. This is the regression test for that fix.
        storeMd5("0000  norway-latest.osm.pbf");
        servedMd5File = DigestUtils.md5Hex("something-else") + "  norway-latest.osm.pbf";

        assertThrows(Md5ChecksumValidationException.class, () -> fetcher().fetch());

        assertEquals("0000  norway-latest.osm.pbf",
                new String(blobStoreRepository.getBlob(MD5_PATH).readAllBytes(), StandardCharsets.UTF_8),
                "the stored checksum was overwritten, so the next run would skip the retry");
        assertEquals("Need to fetch map file. Called update map route", fetcherServingAGoodMap().fetchIfChanged(),
                "the next run did not retry the download");
    }

    private OsmMapFetcher fetcherServingAGoodMap() {
        servedMd5File = DigestUtils.md5Hex(MAP) + "  norway-latest.osm.pbf";
        return fetcher();
    }

    @Test
    void theGraphBuildTriggerCarriesTheCorrelationId() {
        // Without it the graph build and everything downstream of it logs with an empty MDC, so there is no
        // way to tie the build back to the map that caused it.
        fetcher().fetch();

        RecordingPubSubPublisher.Published triggered =
                publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE).getFirst();
        assertNotNull(triggered.attributes().get(CORRELATION_ID), "no correlation id on the graph build");
        assertFalse(triggered.attributes().get(CORRELATION_ID).isBlank());
    }

    @Test
    void aFailedTriggerLeavesTheChecksumSoTheNextRunRetries() {
        // The checksum is the record that this map has been dealt with. Committing it before the trigger
        // meant a failed publish left marduk believing the new map was handled, and the graph was never
        // rebuilt from it.
        storeMd5("0000  norway-latest.osm.pbf");
        publisher.failsFor(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE);

        assertThrows(MardukException.class, () -> fetcher().fetch());

        publisher.failsFor(null);
        assertEquals("Need to fetch map file. Called update map route", fetcher().fetchIfChanged(),
                "the next run believed the new map had already been dealt with");
        assertEquals(1, publisher.publishedTo(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE).size());
    }

    @Test
    void aBlobStoreBlipIsRetriedRatherThanDiscardingTheDownload() {
        // The defaultErrorHandler redelivered a failed upload; between the Camel removal and this, one
        // refused connection threw away a gigabyte download and left the stale map in place.
        FlakyRepository flaky = new FlakyRepository(2);
        flaky.setContainerName("marduk");
        MardukPublicBlobStoreService flakyStore = new MardukPublicBlobStoreService("marduk", flaky);
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/norway-latest.osm.pbf";

        new OsmMapFetcher(flakyStore, publisher, () -> true, retriesWithoutWaiting(), url, "osm").fetch();

        assertTrue(flaky.exist(MAP_PATH));
        assertEquals(3, flaky.attempts(), "the upload was not retried");
    }

    /** Fails the first {@code failures} uploads, as a blob store that is briefly unavailable would. */
    private static class FlakyRepository extends InMemoryMardukBlobStoreRepository {

        private final java.util.concurrent.atomic.AtomicInteger attempts =
                new java.util.concurrent.atomic.AtomicInteger();
        private int failuresLeft;

        FlakyRepository(int failures) {
            super(new java.util.concurrent.ConcurrentHashMap<>());
            this.failuresLeft = failures;
        }

        int attempts() {
            return attempts.get();
        }

        @Override
        public long uploadBlob(String objectName, java.io.InputStream inputStream) {
            if (MAP_PATH.equals(objectName)) {
                attempts.incrementAndGet();
                if (failuresLeft-- > 0) {
                    throw new IllegalStateException("blob store unavailable");
                }
            }
            return super.uploadBlob(objectName, inputStream);
        }
    }

    @Test
    void onlyTheLeaderRunsTheScheduledCheck() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/norway-latest.osm.pbf";

        new OsmMapFetcher(blobStore, publisher, () -> false, noRetries(), url, "osm")
                .fetchIfChangedOnSchedule();

        assertEquals(0, publisher.published().size());
        assertTrue(!blobStoreRepository.exist(MAP_PATH));
    }

    @Test
    void theScheduledCheckSwallowsAChecksumFailure() {
        // onException(MardukException.class).handled(true): a bad upstream file must not become an endless
        // retry, and the next firing picks up a corrected one.
        storeMd5("0000  norway-latest.osm.pbf");
        servedMd5File = DigestUtils.md5Hex("something-else") + "  norway-latest.osm.pbf";

        fetcher().fetchIfChangedOnSchedule();

        assertEquals(0, publisher.published().size());
    }
}
