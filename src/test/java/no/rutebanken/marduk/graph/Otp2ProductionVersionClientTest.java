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

package no.rutebanken.marduk.graph;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Otp2ProductionVersionClientTest {

    private static final String LOCALHOST = "127.0.0.1";
    private static final String PATH = "/journey-planner/v3/graphql";
    private static final Duration TIMEOUT = Duration.ofMillis(500);

    private record ReceivedRequest(String method, String clientName, String contentType, String body) {
    }

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicReference<ReceivedRequest> receivedRequest = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final CountDownLatch slowAnswerReleased = new CountDownLatch(1);

    private HttpServer otp;
    private volatile int status = 200;
    private volatile String answer = "{\"data\":{\"serverInfo\":{\"otpSerializationVersionId\":\"EN-0241\"}}}";
    private volatile boolean slow;

    @BeforeEach
    void startOtp() throws IOException {
        otp = HttpServer.create(new InetSocketAddress(LOCALHOST, 0), 0);
        otp.setExecutor(executor);
        otp.createContext(PATH, exchange -> {
            requests.incrementAndGet();
            receivedRequest.set(new ReceivedRequest(exchange.getRequestMethod(),
                    exchange.getRequestHeaders().getFirst("ET-Client-Name"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            if (slow) {
                try {
                    slowAnswerReleased.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        otp.start();
    }

    @AfterEach
    void stopOtp() {
        slowAnswerReleased.countDown();
        otp.stop(0);
        executor.shutdownNow();
    }

    @Test
    void serializationVersionReportedByOtp() {
        assertEquals(Optional.of("EN-0241"), client().serializationId());

        ReceivedRequest request = receivedRequest.get();
        assertEquals("POST", request.method());
        assertEquals(Otp2ProductionVersionClient.CLIENT_NAME, request.clientName());
        assertEquals("application/json", request.contentType());
        assertEquals(Otp2ProductionVersionClient.QUERY, request.body());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"errors\":[{\"message\":\"Validation error (FieldUndefined@[serverInfo/otpSerializationVersionId])\"}]}",
            "{\"data\":{\"serverInfo\":{\"otpSerializationVersionId\":null}}}",
            "{\"data\":{\"serverInfo\":{\"otpSerializationVersionId\":\" \"}}}",
            "{\"data\":{\"serverInfo\":{\"otpSerializationVersionId\":241}}}",
            "{\"data\":null}",
            "<html><body>Bad gateway</body></html>",
            ""
    })
    void answerWithoutSerializationVersion(String otpAnswer) {
        answer = otpAnswer;
        assertEquals(Optional.empty(), client().serializationId());
    }

    @Test
    void httpError() {
        status = 503;
        assertEquals(Optional.empty(), client().serializationId());
    }

    @Test
    void otpDoesNotAnswerInTime() {
        slow = true;
        long start = System.nanoTime();
        assertEquals(Optional.empty(), client().serializationId());
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofSeconds(5)) < 0);
    }

    @Test
    void otpUnreachable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getByName(LOCALHOST))) {
            closedPort = socket.getLocalPort();
        }
        assertEquals(Optional.empty(), new Otp2ProductionVersionClient(url(closedPort), TIMEOUT).serializationId());
    }

    @Test
    void interruptedCallerKeepsItsInterruptStatus() {
        Otp2ProductionVersionClient client = client();
        Thread.currentThread().interrupt();
        try {
            assertEquals(Optional.empty(), client.serializationId());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void turnedOffWithoutUrl(String url) {
        assertEquals(Optional.empty(), new Otp2ProductionVersionClient(url).serializationId());
        assertEquals(0, requests.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not a url", "ftp://otp.example/graphql"})
    void invalidUrlFailsAtStartup(String url) {
        assertThrows(IllegalArgumentException.class, () -> new Otp2ProductionVersionClient(url));
    }

    private Otp2ProductionVersionClient client() {
        return new Otp2ProductionVersionClient(url(otp.getAddress().getPort()), TIMEOUT);
    }

    private static String url(int port) {
        return "http://" + LOCALHOST + ":" + port + PATH;
    }
}
