package no.rutebanken.marduk.chouette;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;
import static no.rutebanken.marduk.pipeline.RetryPolicies.retriesWithoutWaiting;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Driven against a real HTTP server on a loopback port rather than a mock, because the behaviours worth
 * pinning - what a non-2xx does, whether the Location header comes back, what the multipart body looks like
 * on the wire - are all properties of the transport.
 */
class ChouetteClientTest {

    private record Received(String method, String path, String contentType, byte[] body) {
    }

    private HttpServer server;
    private ChouetteClient client;
    private final List<Received> received = new ArrayList<>();

    /** What the next request should answer with. */
    private int responseStatus = 200;
    private String responseBody = "";
    private String locationHeader;

    /** How many requests answer 500 before the stub starts answering properly. */
    private int failuresFirst;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
        client = new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());
    }

    @AfterEach
    void stopServer() throws IOException {
        client.close();
        server.stop(0);
    }

    private void respond(HttpExchange exchange) throws IOException {
        try (InputStream body = exchange.getRequestBody()) {
            received.add(new Received(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    body.readAllBytes()));
        }
        if (locationHeader != null) {
            exchange.getResponseHeaders().add("Location", locationHeader);
        }
        if (failuresFirst > 0) {
            failuresFirst--;
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
            return;
        }
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatus, payload.length);
        try (var out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    @Test
    void getStringReturnsTheBody() {
        responseBody = "{\"status\":\"TERMINATED\"}";

        assertEquals("{\"status\":\"TERMINATED\"}", client.getString("/chouette_iev/referentials/rb_tst/jobs/7"));
        assertEquals("GET", received.getFirst().method());
        assertEquals("/chouette_iev/referentials/rb_tst/jobs/7", received.getFirst().path());
    }

    @Test
    void getBytesReturnsTheBodyUnchanged() {
        responseBody = "netex-zip-bytes";

        assertArrayEquals("netex-zip-bytes".getBytes(StandardCharsets.UTF_8), client.getBytes("/data"));
    }

    @Test
    void postMultipartReturnsTheJobLocation() {
        // The whole point of the call: the Location is the job status URL the pipeline then polls, and it is
        // republished onto ChouettePollStatusQueue for up to 3000 attempts.
        locationHeader = "http://chouette/chouette_iev/referentials/rb_tst/jobs/42";

        String location = client.postMultipart("/chouette_iev/referentials/rb_tst/importer/netexprofile",
                MultipartEntityBuilder.create()
                        .addBinaryBody("parameters", "{}".getBytes(StandardCharsets.UTF_8),
                                ContentType.DEFAULT_BINARY, "parameters.json")
                        .build());

        assertEquals("http://chouette/chouette_iev/referentials/rb_tst/jobs/42", location);
        assertEquals("POST", received.getFirst().method());
        assertTrue(received.getFirst().contentType().startsWith("multipart/form-data"),
                "not sent as multipart: " + received.getFirst().contentType());
        assertTrue(new String(received.getFirst().body(), StandardCharsets.UTF_8).contains("parameters.json"),
                "the parameters part did not reach Chouette");
    }

    @Test
    void aJobSubmissionWithoutALocationFailsLoudly() {
        // Silently returning null would strand the job: nothing would ever poll it, and no terminal status
        // would be reported.
        locationHeader = null;

        assertThrows(MardukException.class, () -> client.postMultipart("/importer/netexprofile",
                MultipartEntityBuilder.create().addTextBody("parameters", "{}").build()));
    }

    @Test
    void aServerErrorThrowsAndKeepsTheBodyInTheMessage() {
        // Camel's http component defaults to throwExceptionOnFailure=true, so a 5xx failed the exchange
        // rather than returning a body that later steps would misread as a result.
        responseStatus = 500;
        responseBody = "referential rb_tst is locked";

        MardukException thrown = assertThrows(MardukException.class, () -> client.getString("/jobs"));

        assertTrue(thrown.getMessage().contains("500"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("referential rb_tst is locked"),
                "the reason Chouette gave was discarded: " + thrown.getMessage());
    }

    @Test
    void aNotFoundThrowsToo() {
        responseStatus = 404;

        assertThrows(MardukException.class, () -> client.getString("/jobs/does-not-exist"));
    }

    @Test
    void deleteAndPostUseTheRightMethods() {
        client.delete("/chouette_iev/referentials/rb_tst/scheduled_jobs/7");
        client.post("/chouette_iev/referentials/clean/stop_areas");

        assertEquals("DELETE", received.get(0).method());
        assertEquals("POST", received.get(1).method());
    }

    @Test
    void anAbsoluteUrlIsUsedAsGiven() {
        // Chouette hands back absolute action_report and data URLs in its job response, and those are
        // fetched as-is rather than appended to the base URL.
        responseBody = "report";

        client.getString("http://127.0.0.1:" + server.getAddress().getPort() + "/absolute/report");

        assertEquals("/absolute/report", received.getFirst().path());
    }

    @Test
    void aTransientFailureIsRedeliveredRatherThanFailingTheMessage() throws IOException {
        // What the defaultErrorHandler every route inherited did, and what nothing did between the Camel
        // removal and this: a Chouette restart failed the whole message on the first refused connection.
        failuresFirst = 2;
        responseBody = "{\"status\":\"TERMINATED\"}";

        try (ChouetteClient retrying = retryingClient()) {
            assertEquals("{\"status\":\"TERMINATED\"}", retrying.getString("/jobs/7"));
        }

        assertEquals(3, received.size(), "the call was not retried");
    }

    @Test
    void theRetriesRunOutAndTheFailureReachesTheCaller() {
        responseStatus = 500;

        try (ChouetteClient retrying = retryingClient()) {
            assertThrows(MardukException.class, () -> retrying.getString("/jobs/7"));
        } catch (IOException e) {
            throw new AssertionError(e);
        }

        assertEquals(4, received.size(), "three redeliveries means four attempts");
    }

    @Test
    void aJobSubmissionIsNotRetried() throws IOException {
        // The multipart entity reads a blob store stream that cannot be replayed, so a second attempt would
        // post a truncated body - and a submission that reached Chouette before the failure has already
        // created a job that nothing would then poll.
        responseStatus = 500;

        try (ChouetteClient retrying = retryingClient()) {
            assertThrows(MardukException.class, () -> retrying.postMultipart("/importer/netexprofile",
                    MultipartEntityBuilder.create().addTextBody("parameters", "{}").build()));
        }

        assertEquals(1, received.size(), "a job submission was sent twice");
    }

    private ChouetteClient retryingClient() {
        return new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), retriesWithoutWaiting());
    }

    @Test
    void theTestOnlyCamelUrlNotationIsRepaired() {
        // chouette.url is a normal URL in every deployed environment. Only the test configuration uses
        // http:host:port, which existed because Camel's endpoint interception could not cope with "//".
        assertEquals("http://chouette:8080", ChouetteClient.normalise("http:chouette:8080"));
        assertEquals("http://chouette.prd.entur.internal", ChouetteClient.normalise("http://chouette.prd.entur.internal"));
    }
}
