package no.rutebanken.marduk.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The chart settings that are load-bearing rather than tunable.
 *
 * <p>Each of these has a failure mode that no test and no code review catches, because the ConfigMap is
 * data: a deploy that will not start, a deploy that starts and does the work twice, a deploy that starts and
 * consumes nothing. The values are read out of the chart rather than restated here, so this fails when the
 * chart changes and not when a number is tuned.
 */
class ConfigMapInvariantsTest {

    private static final Path CONFIGMAP = Path.of("helm/marduk/templates/configmap.yaml");
    private static final Path VALUES = Path.of("helm/marduk/values.yaml");
    private static final Path SOURCES = Path.of("src/main/java");

    private final Properties properties = applicationProperties();

    @Test
    void aRolledBackImageCanStillStart() {
        // Without this, Flyway finds V2 in flyway_schema_history, cannot find it on the classpath, and
        // refuses to start. The rollback is then the outage.
        assertEquals("true", properties.getProperty("spring.flyway.enabled"));
        String ignored = properties.getProperty("spring.flyway.ignore-migration-patterns");
        assertNotNull(ignored, "spring.flyway.ignore-migration-patterns is missing; a rollback past a "
                + "migration cannot start");
        assertTrue(ignored.contains("*:missing"), "does not ignore missing migrations: " + ignored);
    }

    @Test
    void leaderElectionIsConfigured() {
        // Absent, both replicas run every scheduled job and serve every batch. The application refuses to
        // start in a pod without it, so this is the test that says why.
        assertEquals("true", properties.getProperty("marduk.leader.kubernetes.enabled"));
    }

    /** {@code EnturGooglePubSubUtils.closeSubscriber} awaits termination for this long per subscriber. */
    private static final long SUBSCRIBER_CLOSE_SECONDS = 10;

    /** How many subscribers we accept being mid-callback at SIGTERM. A judgement, not a measurement. */
    private static final long BUSY_SUBSCRIBERS_COVERED = 3;

    @Test
    void theShutdownBudgetAddsUpToLessThanTheGracePeriod() {
        // These are consecutive, not nested: the subscriber close runs in ContextClosedEvent listeners, the
        // drain in the first Lifecycle phase, the HTTP shutdown in a later one. An ordering assertion here
        // used to require the drain to be smaller than the Spring phase timeout, which is not a real
        // constraint - the drain's stop() is inline, so that timeout never bounds it - and which forced the
        // one budget that IS enforced, the HTTP one, up until the total overran the grace period.
        long drain = Long.parseLong(properties.getProperty("marduk.shutdown.drain.timeout.seconds"));
        long http = seconds(properties.getProperty("spring.lifecycle.timeout-per-shutdown-phase"));
        long grace = terminationGracePeriodSeconds();

        assertTrue(drain + http < grace, "the drain (" + drain + "s) and the graceful HTTP shutdown (" + http
                + "s) already reach the grace period (" + grace + "s) before a single subscriber is closed");
        long forSubscriberClose = grace - drain - http;
        assertTrue(forSubscriberClose >= BUSY_SUBSCRIBERS_COVERED * SUBSCRIBER_CLOSE_SECONDS,
                forSubscriberClose + "s left for closing subscribers covers only "
                        + forSubscriberClose / SUBSCRIBER_CLOSE_SECONDS + " busy ones");
    }

    @Test
    void inFlightRequestsFinishRatherThanBeingReset() {
        // Camel waited 25s for in-flight platform-http exchanges. Without this a rolling restart resets
        // whatever upload was in progress, and the uploader cannot tell whether the file landed.
        assertEquals("graceful", properties.getProperty("server.shutdown"));
    }

    @Test
    void everySubscriptionCanBeDeliveredToWithoutQueueingBehindAnother() {
        // One global pool serves every subscriber, so fewer threads than subscriptions means one slow
        // message stops the others.
        int threads = Integer.parseInt(
                properties.getProperty("spring.cloud.gcp.pubsub.subscriber.executor-threads"));

        assertTrue(threads >= consumerCount(),
                "executor-threads=" + threads + " for " + consumerCount() + " subscriptions");
    }

    @Test
    void theConfiguredClaimTimeoutOutlastsTheLongestJobItCanInterrupt() {
        // Reclaiming a batch whose build is still running has a second pod start the same build.
        long claim = java.time.Duration.parse(properties.getProperty("marduk.batch.claim.timeout"))
                .toSeconds();
        long graphBuild = Long.parseLong(
                properties.getProperty("otp.graph.build.remote.kubernetes.timeout"));

        assertTrue(claim > graphBuild,
                "the claim timeout (" + claim + "s) must outlast a remote graph build (" + graphBuild + "s)");
    }

    @Test
    void publishesIssuedWhileShuttingDownAreAccepted() {
        // The drain exists so work finishes and reports its result; a rejected publish wastes both.
        assertEquals("true", properties.getProperty(
                "spring.cloud.gcp.pubsub.publisher.executor-accept-tasks-after-context-close"));
    }

    @Test
    void nothingConfiguresWhatWasRemoved() {
        // The camel.* block above these is deliberate and documented, and goes with the image it is for.
        assertNull(properties.getProperty("marduk.shutdown.timeout"), "nothing reads it");
        assertEquals(List.of(), properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith("logging.level.org.apache.camel"))
                .sorted()
                .toList(), "levels set on loggers that no longer log");
    }

    private static long seconds(String duration) {
        assertNotNull(duration, "spring.lifecycle.timeout-per-shutdown-phase is unset, so it defaults to "
                + "30s and the shutdown budget is whatever Spring happens to use");
        return java.time.Duration.parse("PT" + duration).toSeconds();
    }

    @SuppressWarnings("unchecked")
    private static long terminationGracePeriodSeconds() {
        Map<String, Object> values = new Yaml().load(read(VALUES));
        Map<String, Object> common = (Map<String, Object>) values.get("common");
        Map<String, Object> deployment = (Map<String, Object>) common.get("deployment");
        return ((Number) deployment.get("terminationGracePeriodSeconds")).longValue();
    }

    /** Every {@code MardukPubSubConsumer} subclass is one subscription with one subscriber. */
    private static long consumerCount() {
        try (Stream<Path> sources = Files.walk(SOURCES)) {
            return sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("extends MardukPubSubConsumer"))
                    .count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The properties out of the template's block scalar. Helm placeholders survive as opaque values, which
     * is enough: every invariant here is on a key or on a literal.
     */
    private static Properties applicationProperties() {
        List<String> lines = read(CONFIGMAP).lines().toList();
        int start = lines.indexOf("  application.properties: |+") + 1;
        assertTrue(start > 0, "the ConfigMap no longer holds application.properties as a block scalar");
        StringBuilder block = new StringBuilder();
        for (String line : lines.subList(start, lines.size())) {
            if (!line.isBlank() && !line.startsWith("    ")) {
                break;
            }
            block.append(line.stripLeading()).append('\n');
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(block.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertTrue(properties.size() > 50, "only read " + properties.size() + " properties");
        return properties;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
