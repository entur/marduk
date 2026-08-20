package no.rutebanken.marduk.osm;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.exceptions.Md5ChecksumValidationException;
import no.rutebanken.marduk.leader.LeaderElection;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * Keeps the OpenStreetMap extract for Norway current, and rebuilds the OTP base graph when it changes.
 *
 * <p>Replaces {@code FetchOsmRouteBuilder}. The source publishes the archive and a {@code .md5} beside it,
 * so the check is: fetch the remote checksum, compare it with the one stored next to the last download, and
 * only pull the archive if they differ.
 *
 * <p>A checksum mismatch on a fresh download is logged and swallowed, not retried - the Camel version had
 * {@code onException(MardukException.class).handled(true)}, and a truncated download would otherwise be
 * retried against the same bad upstream file every time the schedule fired.
 */
@Component
public class OsmMapFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OsmMapFetcher.class);

    private static final String MAP_FILE = "norway-latest.osm.pbf";

    private final MardukPublicBlobStoreService blobStore;
    private final MardukPubSubPublisher publisher;
    private final LeaderElection leaderElection;
    private final RetryPolicy retryPolicy;
    private final String osmMapUrl;
    private final String blobStoreSubdirectory;
    private final HttpClient httpClient;

    public OsmMapFetcher(
            MardukPublicBlobStoreService blobStore,
            MardukPubSubPublisher publisher,
            LeaderElection leaderElection,
            RetryPolicy retryPolicy,
            @Value("${fetch.osm.map.url:https://download.geofabrik.de/europe/norway-latest.osm.pbf}") String osmMapUrl,
            @Value("${osm.pbf.blobstore.subdirectory:osm}") String blobStoreSubdirectory) {
        this.blobStore = blobStore;
        this.publisher = publisher;
        this.leaderElection = leaderElection;
        this.retryPolicy = retryPolicy;
        this.osmMapUrl = osmMapUrl;
        this.blobStoreSubdirectory = blobStoreSubdirectory;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * The deployed schedule is hourly at 11 minutes past, not daily - {@code 0+11+*+*+*+?}. The code default
     * below matches the deployed shape rather than the old code default of {@code 0+*+*<!-- -->/23+?+*+MON-FRI},
     * which had {@code *} in the minute field and would have fired sixty times an hour during two hours of
     * the day. It was never in effect because the ConfigMap always sets the value.
     */
    @Scheduled(cron = "${fetch.osm.cron:0 11 * * * ?}", zone = "Europe/Oslo")
    void fetchIfChangedOnSchedule() {
        if (!leaderElection.isLeader()) {
            LOGGER.debug("Not the leader, skipping the scheduled OSM map check");
            return;
        }
        MardukMdc.clear();
        try {
            fetchIfChanged();
        } catch (MardukException e) {
            // Handled, as onException(MardukException.class).handled(true) did: a bad upstream file must not
            // turn into an endless retry, and the next firing will pick up a corrected one.
            LOGGER.error("Failed while fetching the OSM file", e);
        } finally {
            MardukMdc.clear();
        }
    }

    /**
     * Downloads the map only if the remote checksum differs from the stored one.
     *
     * @return a description of what was done, which the admin endpoint returns to its caller
     */
    public String fetchIfChanged() {
        String correlationId = newCorrelationId();
        String remoteMd5 = firstField(fetchRemoteMd5File());
        String storedMd5 = storedMd5();
        if (remoteMd5.equals(storedMd5)) {
            LOGGER.info("There is no update of the map file. No need to fetch external file");
            return "No need to updated the map file, as the MD5 sum has not changed";
        }
        LOGGER.info("Need to update the map file. Calling the update map route");
        fetch(correlationId);
        return "Need to fetch map file. Called update map route";
    }

    /**
     * Downloads the map unconditionally, verifies it, stores it and triggers a base graph build.
     *
     * <p>The stored checksum is written <b>last</b>, which the Camel version did the other way round.
     * Storing it first meant that anything failing afterwards - a truncated download swallowed by the
     * handled exception, or a base graph build that was never triggered - left the stored checksum matching
     * the remote one, so {@link #fetchIfChanged()} saw nothing to do and the map that had just arrived was
     * never built from. The checksum is the record that this map has been dealt with, so it is committed
     * only once it has been. Deliberately fixed rather than reproduced: nothing outside marduk reads either
     * object.
     */
    public void fetch() {
        fetch(newCorrelationId());
    }

    private void fetch(String correlationId) {
        LOGGER.debug("Fetching OSM map over Norway.");
        String md5File = fetchRemoteMd5File();
        String expectedMd5 = firstField(md5File);

        // Streamed to a temp file rather than held in memory: the Norway extract is well over a gigabyte and
        // the pod's limit is smaller than that. Camel spooled it the same way, through the route's
        // .streamCaching() with camel.main.streamCachingSpoolEnabled.
        Path downloaded = downloadToTempFile();
        try {
            LOGGER.debug("OSM map downloaded. Checking MD5");
            String actualMd5 = md5Of(downloaded);
            if (!actualMd5.equals(expectedMd5)) {
                throw new Md5ChecksumValidationException("MD5 of body (" + actualMd5
                        + ") does not match MD5 which was read from source (" + expectedMd5 + ").");
            }

            // The stream is opened per attempt: a retry cannot re-read a consumed one.
            retryPolicy.run("Storing " + mapPath(), () -> {
                try (InputStream map = Files.newInputStream(downloaded)) {
                    blobStore.uploadBlob(mapPath(), map);
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not read the downloaded OSM map", e);
                }
            });
            LOGGER.info("Map was updated, therefore triggering OSM base graph build");
            retryPolicy.run("Triggering the OSM base graph build", () ->
                    publisher.publish(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE,
                            new MardukMessage().setHeader(Constants.CORRELATION_ID, correlationId)));
            retryPolicy.run("Storing " + md5Path(), () -> blobStore.uploadBlob(md5Path(),
                    new ByteArrayInputStream(md5File.getBytes(StandardCharsets.UTF_8))));
            LOGGER.debug("Processing of OSM map finished");
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the downloaded OSM map", e);
        } finally {
            deleteQuietly(downloaded);
        }
    }

    private Path downloadToTempFile() {
        try {
            Path target = Files.createTempFile("osm-map-", ".osm.pbf");
            // ofFile replaces the file rather than appending, so a retry cannot concatenate two downloads.
            HttpResponse<Path> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(osmMapUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                deleteQuietly(target);
                throw new MardukException("Fetching " + osmMapUrl + " returned " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new MardukException("Failed to fetch " + osmMapUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MardukException("Interrupted while fetching " + osmMapUrl, e);
        }
    }

    /** Labels this run's log lines and travels with the graph build it triggers. */
    private static String newCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        MardukMdc.setCorrelationId(correlationId);
        return correlationId;
    }

    private static String md5Of(Path file) throws IOException {
        try (InputStream contents = Files.newInputStream(file)) {
            return DigestUtils.md5Hex(contents);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // A leftover temp file is not worth failing the fetch over, but it is worth knowing about: the
            // schedule runs hourly and these are gigabyte-sized.
            LOGGER.warn("Could not delete the temporary OSM download {}", file, e);
        }
    }

    /**
     * The whole published checksum file, which is {@code <md5>  <filename>}. Stored verbatim, as before;
     * only the first field is ever compared.
     */
    private String fetchRemoteMd5File() {
        LOGGER.debug("Fetching MD5 sum for map over Norway");
        return new String(get(osmMapUrl + ".md5").body(), StandardCharsets.UTF_8);
    }

    private String storedMd5() {
        try (var stored = retryPolicy.call("Reading " + md5Path(), () -> blobStore.getBlob(md5Path()))) {
            if (stored == null) {
                return "";
            }
            return firstField(new String(stored.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the stored OSM checksum", e);
        }
    }

    private static String firstField(String md5File) {
        return md5File == null || md5File.isEmpty() ? "" : md5File.split(" ")[0];
    }

    private String md5Path() {
        return blobStoreSubdirectory + "/" + MAP_FILE + ".md5";
    }

    private String mapPath() {
        return blobStoreSubdirectory + "/" + MAP_FILE;
    }

    private HttpResponse<byte[]> get(String url) {
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new MardukException("Fetching " + url + " returned " + response.statusCode());
            }
            return response;
        } catch (IOException e) {
            throw new MardukException("Failed to fetch " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MardukException("Interrupted while fetching " + url, e);
        }
    }
}
