package no.rutebanken.marduk.chouette;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static no.rutebanken.marduk.pipeline.RetryPolicies.noRetries;

/**
 * A stand-in Chouette on a loopback port.
 *
 * <p>A real server rather than a mocked client, because what these tests are about is the URL Chouette is
 * asked for and what comes back on the wire.
 */
class ChouetteStub implements AutoCloseable {

    record Request(String method, String path, String query, byte[] body) {
    }

    private final HttpServer server;
    private final List<Request> received = new CopyOnWriteArrayList<>();

    /** Path fragment to response body; the first match wins. */
    private final Map<String, String> responses = new LinkedHashMap<>();

    private volatile String location;

    ChouetteStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::respond);
        server.start();
    }

    ChouetteClient client() {
        return new ChouetteClient("http://127.0.0.1:" + server.getAddress().getPort(), noRetries());
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    ChouetteStub answers(String pathFragment, String body) {
        responses.put(pathFragment, body);
        return this;
    }

    ChouetteStub answersWithLocation(String location) {
        this.location = location;
        return this;
    }

    List<Request> received() {
        return List.copyOf(received);
    }

    List<String> paths() {
        return received.stream().map(Request::path).toList();
    }

    private void respond(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readAllBytes();
        }
        received.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(), body));
        if (location != null) {
            exchange.getResponseHeaders().add("Location", location);
        }
        String payload = responses.entrySet().stream()
                .filter(entry -> exchange.getRequestURI().getPath().contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("{}");
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
