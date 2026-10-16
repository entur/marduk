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

package no.rutebanken.marduk.rest;

import com.fasterxml.jackson.databind.JsonNode;
import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import no.rutebanken.marduk.graph.Otp2GraphControlService;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Admin API of the emergency controls of the OTP2 graph publication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
@Import(AdminRestMardukRouteBuilderIntegrationTest.AdminRestMardukRouteBuilderTestContextConfiguration.class)
class OtpGraphControlRestIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String PRODUCTION_ID = "EN-0239";
    private static final String OLD_GRAPH = graph(PRODUCTION_ID, "20261013080000000");
    private static final String LATEST_GRAPH = graph(PRODUCTION_ID, "20261014080000000");
    private static final String NEXT_VERSION_GRAPH = graph("EN-0240", "20261014090000000");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${server.port}")
    private int port;

    @Autowired
    private Otp2GraphControlService otp2GraphControlService;

    @BeforeEach
    void publishGraphs() {
        upload(OLD_GRAPH, "old graph");
        upload(LATEST_GRAPH, "latest graph");
        upload(NEXT_VERSION_GRAPH, "next version graph");
        upload(Constants.OTP2_NETEX_GRAPH_DIR + "/" + PRODUCTION_ID + "/" + Constants.OTP2_CURRENT_GRAPH_OBJ, "latest graph");
        upload(Constants.OTP2_NETEX_GRAPH_DIR + "/" + PRODUCTION_ID + "/current-otp2", LATEST_GRAPH);
        upload("current-otp2", LATEST_GRAPH);
        upload("last-otp2", LATEST_GRAPH);
    }

    @Test
    void rolloutAndPinAPreviousGraph() throws Exception {
        context.start();

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true,\"reason\":\"bad data\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json(response);
        assertEquals(PRODUCTION_ID, result.get("serializationId").asText());
        assertTrue(result.get("production").asBoolean());
        assertTrue(result.get("restartTriggered").asBoolean());
        assertTrue(result.get("pinned").asBoolean());

        assertEquals("old graph", read(Constants.OTP2_NETEX_GRAPH_DIR + "/" + PRODUCTION_ID + "/" + Constants.OTP2_CURRENT_GRAPH_OBJ));
        assertEquals(OLD_GRAPH, read(Constants.OTP2_NETEX_GRAPH_DIR + "/" + PRODUCTION_ID + "/current-otp2"));
        assertEquals(OLD_GRAPH, read("current-otp2"));
        // the root pointer file changed: the redeployment job detects the change by itself
        assertEquals(LATEST_GRAPH, read("last-otp2"));
        assertEquals(1, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_GRAPH_REPORT_INDEX_FILE).getFiles().size());

        JsonNode status = json(get("routing_graph/control"));
        assertEquals(PRODUCTION_ID, status.get("production").get("serializationId").asText());
        assertEquals(OLD_GRAPH, status.get("production").get("currentGraph").asText());
        assertTrue(status.get("production").get("pinned").asBoolean());
        assertEquals(OLD_GRAPH, status.get("pins").get(0).get("graph").asText());
        assertEquals("test-user", status.get("pins").get(0).get("pinnedBy").asText());
        assertEquals("ROLLOUT_AND_PIN", status.get("recentActions").get(0).get("action").asText());
        assertEquals("bad data", status.get("recentActions").get(0).get("reason").asText());

        HttpResponse<String> unpinResponse = post("routing_graph/unpin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"fixed\"}");
        assertEquals(200, unpinResponse.statusCode(), unpinResponse.body());
        assertTrue(json(unpinResponse).get("pins").isEmpty());
        // the current graph is unchanged
        assertEquals(OLD_GRAPH, read("current-otp2"));

        assertEquals(404, post("routing_graph/unpin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"fixed\"}").statusCode());
    }

    @Test
    void pinTheCurrentGraph() throws Exception {
        context.start();

        HttpResponse<String> response = post("routing_graph/pin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"freeze during the incident\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode status = json(response);
        assertTrue(status.get("production").get("pinned").asBoolean());
        assertEquals(LATEST_GRAPH, status.get("pins").get(0).get("graph").asText());
        assertEquals("PIN", status.get("recentActions").get(0).get("action").asText());
        // the graph files are unchanged: OTP is not redeployed
        assertEquals(LATEST_GRAPH, read("current-otp2"));
        assertEquals(LATEST_GRAPH, read("last-otp2"));

        assertEquals(404, post("routing_graph/pin", "{\"serializationId\":\"EN-0001\",\"reason\":\"freeze during the incident\"}").statusCode());
    }

    @Test
    void rolloutOfTheGraphReferencedByTheRootPointerForcesTheRedeployment() throws Exception {
        context.start();

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + LATEST_GRAPH + "\",\"pin\":false,\"reason\":\"restore the stable copy\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(json(response).get("restartTriggered").asBoolean());
        assertNull(read("last-otp2"));
    }

    @Test
    void rolloutOfAnotherSerializationVersionDoesNotTouchProduction() throws Exception {
        context.start();

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + NEXT_VERSION_GRAPH + "\",\"pin\":false,\"reason\":\"prepare the next OTP version\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json(response);
        assertFalse(result.get("production").asBoolean());
        assertFalse(result.get("restartTriggered").asBoolean());

        assertEquals("next version graph", read(Constants.OTP2_NETEX_GRAPH_DIR + "/EN-0240/" + Constants.OTP2_CURRENT_GRAPH_OBJ));
        assertEquals(NEXT_VERSION_GRAPH, read(Constants.OTP2_NETEX_GRAPH_DIR + "/EN-0240/current-otp2"));
        assertEquals(LATEST_GRAPH, read("current-otp2"));
        assertEquals(LATEST_GRAPH, read("last-otp2"));
    }

    @Test
    void invalidRolloutRequests() throws Exception {
        context.start();

        assertEquals(400, post("routing_graph/rollout", "{\"graph\":\"current-otp2\",\"pin\":true,\"reason\":\"bad data\"}").statusCode());
        assertEquals(400, post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true}").statusCode());
        assertEquals(400, post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true,\"reason\":\"  \"}").statusCode());
        assertEquals(404, post("routing_graph/rollout", "{\"graph\":\"" + graph(PRODUCTION_ID, "20250101000000000") + "\",\"pin\":true,\"reason\":\"bad data\"}").statusCode());
        // nothing changed
        assertEquals(LATEST_GRAPH, read("current-otp2"));
    }

    @Test
    void pauseAndResumeTheGraphBuilder() throws Exception {
        context.start();

        HttpResponse<String> pauseResponse = post("routing_graph/builder/pause", "{\"reason\":\"investigating\"}");
        assertEquals(200, pauseResponse.statusCode(), pauseResponse.body());
        JsonNode builder = json(pauseResponse).get("builder");
        assertTrue(builder.get("paused").asBoolean());
        assertEquals("test-user", builder.get("pausedBy").asText());
        assertEquals("investigating", builder.get("reason").asText());
        assertTrue(json(get("routing_graph/control")).get("builder").get("paused").asBoolean());

        assertEquals(400, post("routing_graph/builder/resume", "{}").statusCode());

        HttpResponse<String> resumeResponse = post("routing_graph/builder/resume", "{\"reason\":\"fixed\"}");
        assertEquals(200, resumeResponse.statusCode(), resumeResponse.body());
        assertFalse(json(resumeResponse).get("builder").get("paused").asBoolean());
        assertEquals("RESUME", json(resumeResponse).get("recentActions").get(0).get("action").asText());
    }

    @Test
    void listGraphVersions() throws Exception {
        context.start();
        otp2GraphControlService.rollout(OLD_GRAPH, true, "bad data", "test-user");

        HttpResponse<String> response = get("routing_graph/versions?limit=10");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode versions = json(response).get("serializationVersions");
        assertEquals(2, versions.size());

        JsonNode production = versions.get(0);
        assertEquals(PRODUCTION_ID, production.get("serializationId").asText());
        assertTrue(production.get("production").asBoolean());
        assertEquals(OLD_GRAPH, production.get("currentGraph").asText());
        assertEquals(OLD_GRAPH, production.get("pinnedGraph").asText());
        assertEquals(2, production.get("totalGraphs").asInt());
        // most recent first, the stable copy and the pointer files are not listed
        assertEquals(LATEST_GRAPH, production.get("graphs").get(0).get("graph").asText());
        assertFalse(production.get("graphs").get(0).get("current").asBoolean());
        assertEquals(OLD_GRAPH, production.get("graphs").get(1).get("graph").asText());
        assertTrue(production.get("graphs").get(1).get("current").asBoolean());
        assertTrue(production.get("graphs").get(1).get("pinned").asBoolean());

        assertEquals("EN-0240", versions.get(1).get("serializationId").asText());
        assertFalse(versions.get(1).get("production").asBoolean());

        assertEquals(1, json(get("routing_graph/versions?limit=1")).get("serializationVersions").get(0).get("graphs").size());
        assertEquals(400, get("routing_graph/versions?limit=0").statusCode());
    }

    @Test
    void candidateGraphOfTheProductionSerializationVersionIsNotPromotedWhileTheGraphBuilderIsPaused() {
        otp2GraphControlService.pause("investigating", "test-user");

        String candidateGraph = graph(PRODUCTION_ID, "20261015080000000");
        upload(candidateGraph, "candidate graph");
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_PAUSED,
                otp2GraphControlService.promoteBuiltGraph(candidateGraph, PRODUCTION_ID, true, "netex-otp2/20261015080000000-report"));
        assertEquals("latest graph", read(Constants.OTP2_NETEX_GRAPH_DIR + "/" + PRODUCTION_ID + "/" + Constants.OTP2_CURRENT_GRAPH_OBJ));

        String nextVersionCandidateGraph = graph("EN-0240", "20261015080000000");
        upload(nextVersionCandidateGraph, "next version candidate graph");
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(nextVersionCandidateGraph, "EN-0240", true, "netex-otp2/20261015080000000-report"));
        assertEquals(nextVersionCandidateGraph, read(Constants.OTP2_NETEX_GRAPH_DIR + "/EN-0240/current-otp2"));
        assertEquals(LATEST_GRAPH, read("current-otp2"));
    }

    @Test
    void olderGraphIsNotPromoted() {
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_OLDER,
                otp2GraphControlService.promoteBuiltGraph(OLD_GRAPH, PRODUCTION_ID, false, "netex-otp2/20261013080000000-report"));
        assertEquals(LATEST_GRAPH, read("current-otp2"));
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return httpClient.send(request(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
        return httpClient.send(request(path).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/services/timetable_admin/" + path))
                .header("Authorization", "Bearer test-token")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json");
    }

    private static JsonNode json(HttpResponse<String> response) throws IOException {
        return ObjectMapperFactory.getSharedObjectMapper().readTree(response.body());
    }

    private static String graph(String serializationId, String timestamp) {
        return Constants.OTP2_NETEX_GRAPH_DIR + "/" + serializationId + "/" + timestamp + "-Graph-otp2-" + serializationId + ".obj";
    }

    private void upload(String name, String content) {
        graphsInMemoryBlobStoreRepository.uploadBlob(name, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private String read(String name) {
        try (InputStream content = graphsInMemoryBlobStoreRepository.getBlob(name)) {
            return content == null ? null : IOUtils.toString(content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
