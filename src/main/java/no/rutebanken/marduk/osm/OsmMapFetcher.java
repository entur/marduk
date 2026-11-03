package no.rutebanken.marduk.osm;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.leader.LeaderElection;
import no.rutebanken.marduk.pipeline.MardukMdc;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pipeline.RetryPolicy;
import no.rutebanken.marduk.pubsub.MardukPubSubPublisher;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.services.MardukPublicBlobStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Detects updates of the OSM data file and triggers a base graph build when the file has changed.
 *
 * <p>Replaces {@code FetchOsmRouteBuilder} and then {@code OsmUpdateRouteBuilder}. The OSM data file is
 * maintained in the shared osm-data bucket (see the entur/osm-data project), together with an MD5 checksum file
 * obtained by adding {@code .md5} to the file URL. The graph builder reads the OSM data file directly from that
 * bucket, so marduk does not download it: it only keeps a copy of the checksum of the last OSM data file for
 * which a base graph build was triggered, and compares it with the current checksum.
 *
 * <p>The stored checksum is written <b>last</b>, once the build has been triggered, so a failed trigger is
 * retried on the next run: the checksum is the record that this map has been dealt with.
 */
@Component
public class OsmMapFetcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(OsmMapFetcher.class);

    private static final String MAP_FILE = "norway-latest.osm.pbf";

    private static final Pattern MD5_PATTERN = Pattern.compile("[0-9a-f]{32}");

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
            @Value("${fetch.osm.map.url:https://storage.googleapis.com/ror-osmdata-prd/osm-data/norway-latest.osm.pbf}") String osmMapUrl,
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
            // A bad upstream file must not turn into an endless retry, and the next firing will pick up a
            // corrected one.
            LOGGER.error("Failed while checking for an updated OSM file", e);
        } finally {
            MardukMdc.clear();
        }
    }

    /**
     * Triggers a base graph build if the remote checksum differs from the stored one.
     *
     * @return a description of what was done, which the admin endpoint returns to its caller
     */
    public String fetchIfChanged() {
        String correlationId = newCorrelationId();
        LOGGER.debug("Checking whether the OSM map has been updated");
        String currentMd5File = fetchRemoteMd5File();
        String storedMd5File = storedMd5File();

        boolean updated = storedMd5File == null || storedMd5File.isEmpty()
                || !extractMd5(storedMd5File).equals(extractMd5(currentMd5File));
        if (!updated) {
            LOGGER.info("There is no update of the map file. No need to build the base graph");
            return "No need to build the base graph, as the MD5 sum of the map file has not changed";
        }

        LOGGER.info("The map file was updated, therefore triggering OSM base graph build");
        // Only the correlation id: nothing else of this run belongs on the build request.
        retryPolicy.run("Triggering the OSM base graph build", () ->
                publisher.publish(MardukQueues.OTP2_BASE_GRAPH_BUILD_QUEUE,
                        new MardukMessage().setHeader(Constants.CORRELATION_ID, correlationId).setBody("")));
        // Stored only after the build has been triggered, so that a failed trigger is retried on the next run.
        retryPolicy.run("Storing " + md5Path(), () -> blobStore.uploadBlob(md5Path(),
                new ByteArrayInputStream(currentMd5File.getBytes(StandardCharsets.UTF_8))));
        return "The map file was updated. Triggered the base graph build";
    }

    /** Labels this run's log lines and travels with the graph build it triggers. */
    private static String newCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        MardukMdc.setCorrelationId(correlationId);
        return correlationId;
    }

    /**
     * The whole published checksum file, which is {@code <md5>  <filename>}. Stored verbatim; only the first
     * field is ever compared.
     *
     * @throws MardukException if it is not a checksum file, so that an empty or unexpected response (truncated
     *                         object, HTML error page) is neither compared with nor stored as a checksum
     */
    private String fetchRemoteMd5File() {
        String md5File = new String(get(osmMapUrl + ".md5").body(), StandardCharsets.UTF_8);
        if (!MD5_PATTERN.matcher(extractMd5(md5File)).matches()) {
            throw new MardukException("Invalid MD5 file content fetched from " + osmMapUrl + ".md5: '" + md5File + "'");
        }
        return md5File;
    }

    private String storedMd5File() {
        try (var stored = retryPolicy.call("Reading " + md5Path(), () -> blobStore.getBlob(md5Path()))) {
            return stored == null ? null : new String(stored.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the stored OSM checksum", e);
        }
    }

    /** The checksum of an MD5 file formatted as "checksum filename". */
    private static String extractMd5(String md5File) {
        return md5File.split(" ")[0];
    }

    private String md5Path() {
        return blobStoreSubdirectory + "/" + MAP_FILE + ".md5";
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
