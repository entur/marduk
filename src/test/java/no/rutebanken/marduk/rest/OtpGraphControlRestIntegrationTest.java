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
import no.rutebanken.marduk.domain.OtpGraphControlStatus;
import no.rutebanken.marduk.graph.GraphControlRepository;
import no.rutebanken.marduk.graph.Otp2GraphControlService;
import no.rutebanken.marduk.graph.Otp2GraphPaths;
import no.rutebanken.marduk.graph.Otp2GraphStateStore;
import no.rutebanken.marduk.graph.Otp2GraphStateStore.GraphPin;
import no.rutebanken.marduk.graph.Otp2GraphStateStore.GraphPointer;
import no.rutebanken.marduk.graph.Otp2ProductionVersionClient;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.services.OtpGraphsBlobStoreService;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Admin API of the emergency controls of the OTP2 graph publication.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, classes = TestApp.class)
@Import(AdminRestMardukRouteBuilderIntegrationTest.AdminRestMardukRouteBuilderTestContextConfiguration.class)
class OtpGraphControlRestIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String PRODUCTION_ID = "EN-0239";
    private static final String NEXT_ID = "EN-0240";
    private static final String OLD_GRAPH = graph(PRODUCTION_ID, "20261013080000000");
    private static final String LATEST_GRAPH = graph(PRODUCTION_ID, "20261014080000000");
    private static final String NEXT_VERSION_GRAPH = graph(NEXT_ID, "20261014090000000");
    private static final String ROOT_POINTER = "current-otp2";
    private static final String STABLE_GRAPH = Otp2GraphPaths.stableGraphPath(PRODUCTION_ID);

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${server.port}")
    private int port;

    @Autowired
    private Otp2GraphControlService otp2GraphControlService;

    @Autowired
    private Otp2GraphStateStore stateStore;

    @Autowired
    private GraphControlRepository graphControlRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OtpGraphsBlobStoreService otpGraphsBlobStoreService;

    /**
     * Does not answer unless a test says so: the serialization version used in production is then read from the root
     * pointer file.
     */
    @MockitoBean
    private Otp2ProductionVersionClient productionVersionClient;

    @Value("${blobstore.gcs.otpreport.container.name}")
    private String otpReportContainerName;

    /**
     * Publish three graphs, the latest of the production serialization version being the current graph promoted by a
     * production build, with its build report.
     */
    @BeforeEach
    void publishGraphs() {
        upload(OLD_GRAPH, "old graph");
        upload(LATEST_GRAPH, "latest graph");
        upload(NEXT_VERSION_GRAPH, "next version graph");
        uploadReport(OLD_GRAPH);
        uploadReport(LATEST_GRAPH);
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(LATEST_GRAPH, PRODUCTION_ID, false, reportVersion(LATEST_GRAPH)));
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(LATEST_GRAPH, rootPointer().graph());
        otpReportInMemoryBlobStoreRepository.delete(Constants.OTP2_GRAPH_REPORT_INDEX_FILE);
    }

    @Test
    void rolloutAndPinAPreviousGraph() throws Exception {
        context.start();

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true,\"reason\":\"bad data\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json(response);
        assertEquals(PRODUCTION_ID, result.get("serializationId").asText());
        assertTrue(result.get("production").asBoolean());
        assertTrue(result.get("pinned").asBoolean());

        assertEquals("old graph", read(STABLE_GRAPH));
        GraphPointer pointer = pointer(PRODUCTION_ID);
        assertEquals(OLD_GRAPH, pointer.graph());
        assertFalse(pointer.candidate());
        assertEquals("test-user", pointer.updatedBy());
        assertEquals("bad data", pointer.reason());
        // the root pointer file triggers the redeployment of OTP
        assertEquals(pointer, rootPointer());
        GraphPin pin = stateStore.readPin(PRODUCTION_ID).orElseThrow();
        assertEquals(OLD_GRAPH, pin.graph());
        assertEquals("test-user", pin.pinnedBy());
        assertEquals(1, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_GRAPH_REPORT_INDEX_FILE).getFiles().size());

        JsonNode status = json(get("routing_graph/control"));
        assertEquals(PRODUCTION_ID, status.get("production").get("serializationId").asText());
        assertEquals(OLD_GRAPH, status.get("production").get("currentGraph").asText());
        assertTrue(status.get("production").get("pinned").asBoolean());
        assertEquals(OLD_GRAPH, status.get("pins").get(0).get("graph").asText());
        assertEquals(OLD_GRAPH, status.get("pins").get(0).get("currentGraph").asText());
        assertEquals("test-user", status.get("pins").get(0).get("pinnedBy").asText());
        assertEquals("ROLLOUT_AND_PIN", status.get("recentActions").get(0).get("action").asText());
        assertEquals("bad data", status.get("recentActions").get(0).get("reason").asText());

        // the pinned graph is not replaced by a new build
        String newGraph = graph(PRODUCTION_ID, "20261015080000000");
        upload(newGraph, "new graph");
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_PINNED,
                otp2GraphControlService.promoteBuiltGraph(newGraph, PRODUCTION_ID, false, reportVersion(newGraph)));
        assertEquals("old graph", read(STABLE_GRAPH));

        HttpResponse<String> unpinResponse = post("routing_graph/unpin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"fixed\"}");
        assertEquals(200, unpinResponse.statusCode(), unpinResponse.body());
        JsonNode unpinResult = json(unpinResponse);
        assertEquals(PRODUCTION_ID, unpinResult.get("serializationId").asText());
        assertEquals(OLD_GRAPH, unpinResult.get("graph").asText());
        assertFalse(unpinResult.get("pinned").asBoolean());
        JsonNode statusAfterUnpin = json(get("routing_graph/control"));
        assertTrue(statusAfterUnpin.get("pins").isEmpty());
        // the current graph is unchanged until the next build
        assertEquals(OLD_GRAPH, statusAfterUnpin.get("production").get("currentGraph").asText());
        assertEquals("old graph", read(STABLE_GRAPH));
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(newGraph, PRODUCTION_ID, false, reportVersion(newGraph)));
        assertEquals("new graph", read(STABLE_GRAPH));

        assertEquals(404, post("routing_graph/unpin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"fixed\"}").statusCode());
    }

    @Test
    void pinTheCurrentGraph() throws Exception {
        context.start();
        String rootPointerContent = read(ROOT_POINTER);

        HttpResponse<String> response = post("routing_graph/pin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"freeze during the incident\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json(response);
        assertEquals(PRODUCTION_ID, result.get("serializationId").asText());
        assertEquals(LATEST_GRAPH, result.get("graph").asText());
        assertTrue(result.get("pinned").asBoolean());
        JsonNode status = json(get("routing_graph/control"));
        assertTrue(status.get("production").get("pinned").asBoolean());
        assertEquals(LATEST_GRAPH, status.get("pins").get(0).get("graph").asText());
        assertEquals("PIN", status.get("recentActions").get(0).get("action").asText());
        // the graph files are unchanged, OTP is not redeployed
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(rootPointerContent, read(ROOT_POINTER));

        // no current graph for the next serialization version
        assertEquals(404, post("routing_graph/pin", "{\"serializationId\":\"" + NEXT_ID + "\",\"reason\":\"freeze during the incident\"}").statusCode());
        assertEquals(400, post("routing_graph/pin", "{\"serializationId\":\"" + PRODUCTION_ID + "\"}").statusCode());
    }

    @Test
    void rolloutOfTheCurrentGraphRedeploysOtp() throws Exception {
        context.start();
        // the stable copy was restored by hand, OTP must be restarted
        String rootPointerContent = read(ROOT_POINTER);

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + LATEST_GRAPH + "\",\"pin\":false,\"reason\":\"restart OTP\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(json(response).get("production").asBoolean());
        assertFalse(json(response).get("pinned").asBoolean());
        assertEquals("latest graph", read(STABLE_GRAPH));
        // the root pointer file names the same graph, but its content changes, which triggers the redeployment
        assertEquals(LATEST_GRAPH, rootPointer().graph());
        assertEquals("test-user", rootPointer().updatedBy());
        assertNotEquals(rootPointerContent, read(ROOT_POINTER));
        assertTrue(stateStore.readPin(PRODUCTION_ID).isEmpty());
    }

    @Test
    void rolloutOfAGraphWithoutBuildReportLeavesTheReportIndexUnchanged() throws Exception {
        context.start();
        otpReportInMemoryBlobStoreRepository.deleteAllFilesInFolder(reportVersion(OLD_GRAPH));

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":false,\"reason\":\"bad data\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("old graph", read(STABLE_GRAPH));
        assertEquals(OLD_GRAPH, rootPointer().graph());
        assertEquals(0, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_GRAPH_REPORT_INDEX_FILE).getFiles().size());
    }

    @Test
    void rolloutOfAnotherSerializationVersionDoesNotTouchProduction() throws Exception {
        context.start();
        String rootPointerContent = read(ROOT_POINTER);

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + NEXT_VERSION_GRAPH + "\",\"pin\":false,\"reason\":\"prepare the next OTP version\"}");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json(response);
        assertEquals(NEXT_ID, result.get("serializationId").asText());
        assertFalse(result.get("production").asBoolean());

        assertEquals("next version graph", read(Otp2GraphPaths.stableGraphPath(NEXT_ID)));
        assertEquals(NEXT_VERSION_GRAPH, pointer(NEXT_ID).graph());
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(rootPointerContent, read(ROOT_POINTER));
        assertEquals(0, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_GRAPH_REPORT_INDEX_FILE).getFiles().size());

        JsonNode status = json(get("routing_graph/control"));
        assertEquals(PRODUCTION_ID, status.get("production").get("serializationId").asText());
        assertEquals(LATEST_GRAPH, status.get("production").get("currentGraph").asText());
        assertEquals("ROOT_POINTER", status.get("production").get("source").asText());
        assertEquals("ROLLOUT", status.get("recentActions").get(0).get("action").asText());

        // the next serialization version has a current graph to pin now
        assertEquals(200, post("routing_graph/pin", "{\"serializationId\":\"" + NEXT_ID + "\",\"reason\":\"freeze\"}").statusCode());
        assertEquals(NEXT_VERSION_GRAPH, stateStore.readPin(NEXT_ID).orElseThrow().graph());
        assertFalse(json(get("routing_graph/control")).get("production").get("pinned").asBoolean());
    }

    @Test
    void invalidRolloutRequests() throws Exception {
        context.start();
        String rootPointerContent = read(ROOT_POINTER);

        assertEquals(400, post("routing_graph/rollout", "{\"graph\":\"current-otp2\",\"pin\":true,\"reason\":\"bad data\"}").statusCode());
        assertEquals(400, post("routing_graph/rollout", "{\"graph\":\"" + STABLE_GRAPH + "\",\"pin\":true,\"reason\":\"bad data\"}").statusCode());
        assertEquals(400, post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true}").statusCode());
        assertEquals(400, post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true,\"reason\":\"  \"}").statusCode());
        assertEquals(404, post("routing_graph/rollout", "{\"graph\":\"" + graph(PRODUCTION_ID, "20250101000000000") + "\",\"pin\":true,\"reason\":\"bad data\"}").statusCode());

        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(rootPointerContent, read(ROOT_POINTER));
        assertTrue(stateStore.readPin(PRODUCTION_ID).isEmpty());
        assertEquals(LATEST_GRAPH, json(get("routing_graph/control")).get("production").get("currentGraph").asText());
    }

    @Test
    void reasonLengthIsCheckedAfterTrimming() throws Exception {
        context.start();
        String fullReason = "r".repeat(1000);

        assertEquals(400, post("routing_graph/pin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"" + fullReason + "r\"}").statusCode());
        HttpResponse<String> response = post("routing_graph/pin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"" + fullReason + "\\n  \"}");
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(fullReason, stateStore.readPin(PRODUCTION_ID).orElseThrow().reason());
    }

    @Test
    void promotionFailureWithALongMessageIsAudited() {
        String newGraph = graph(PRODUCTION_ID, "20261015080000000");
        otp2GraphControlService.recordPromotionFailure(newGraph, PRODUCTION_ID, new IllegalStateException("x".repeat(5000)));

        JsonNode skippedBuild = ObjectMapperFactory.getSharedObjectMapper().valueToTree(otp2GraphControlService.status().skippedBuilds().getFirst());
        assertEquals(newGraph, skippedBuild.get("graph").asText());
        String reason = skippedBuild.get("reason").asText();
        assertEquals(1000, reason.length());
        assertTrue(reason.startsWith("The promotion failed: xxx"), reason);
        assertTrue(reason.endsWith("…"), reason);
    }

    @Test
    void listGraphVersions() throws Exception {
        context.start();
        assertEquals(200, post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true,\"reason\":\"bad data\"}").statusCode());

        HttpResponse<String> response = get("routing_graph/versions");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode versions = json(response).get("serializationVersions");
        assertEquals(2, versions.size());

        JsonNode production = versions.get(0);
        assertEquals(PRODUCTION_ID, production.get("serializationId").asText());
        assertTrue(production.get("production").asBoolean());
        assertEquals(OLD_GRAPH, production.get("currentGraph").asText());
        assertEquals(OLD_GRAPH, production.get("pinnedGraph").asText());
        assertEquals(2, production.get("totalGraphs").asInt());
        // most recent first
        assertEquals(LATEST_GRAPH, production.get("graphs").get(0).get("graph").asText());
        assertFalse(production.get("graphs").get(0).get("current").asBoolean());
        assertEquals(OLD_GRAPH, production.get("graphs").get(1).get("graph").asText());
        assertTrue(production.get("graphs").get(1).get("current").asBoolean());
        assertTrue(production.get("graphs").get(1).get("pinned").asBoolean());

        JsonNode next = versions.get(1);
        assertEquals(NEXT_ID, next.get("serializationId").asText());
        assertFalse(next.get("production").asBoolean());
        assertTrue(next.get("currentGraph").isNull());
        assertTrue(next.get("pinnedGraph").isNull());

        assertEquals(1, json(get("routing_graph/versions?limit=1")).get("serializationVersions").get(0).get("graphs").size());
        assertEquals(400, get("routing_graph/versions?limit=0").statusCode());
    }

    /**
     * The graph builder wrote a graph file whose name does not carry the serialization version: the graph is published
     * under an unknown-version folder, with a path that a pointer file cannot record.
     */
    @Test
    void graphNotPublishedUnderAGraphPathIsNotPromoted() {
        String unknownVersion = "unknown-version";
        String misnamedGraph = Otp2GraphPaths.versionedGraphPath(unknownVersion, "20261015080000000", "Graph-otp2.obj");
        upload(misnamedGraph, "misnamed graph");

        assertEquals(Otp2GraphControlService.Promotion.FAILED,
                otp2GraphControlService.promoteBuiltGraph(misnamedGraph, unknownVersion, false, Otp2GraphPaths.reportVersion("20261015080000000")));
        assertNull(read(Otp2GraphPaths.stableGraphPath(unknownVersion)));
        assertNull(read(stateStore.versionedPointerPath(unknownVersion)));
        assertEquals(LATEST_GRAPH, rootPointer().graph());
        JsonNode status = ObjectMapperFactory.getSharedObjectMapper().valueToTree(otp2GraphControlService.status());
        assertEquals("BUILD_NOT_PROMOTED", status.get("skippedBuilds").get(0).get("action").asText());
        assertEquals(misnamedGraph, status.get("skippedBuilds").get(0).get("graph").asText());
        assertTrue(status.get("unreadableFiles").isEmpty());

        // a graph of another serialization version than its folder is not promoted either
        assertEquals(Otp2GraphControlService.Promotion.FAILED,
                otp2GraphControlService.promoteBuiltGraph(NEXT_VERSION_GRAPH, PRODUCTION_ID, false, reportVersion(NEXT_VERSION_GRAPH)));
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(LATEST_GRAPH, pointer(PRODUCTION_ID).graph());
        assertEquals(LATEST_GRAPH, rootPointer().graph());
    }

    @Test
    void candidateGraphOfTheProductionSerializationVersionIsNeverPromoted() {
        String candidateGraph = graph(PRODUCTION_ID, "20261015080000000");
        upload(candidateGraph, "candidate graph");
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_CANDIDATE,
                otp2GraphControlService.promoteBuiltGraph(candidateGraph, PRODUCTION_ID, true, reportVersion(candidateGraph)));
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(LATEST_GRAPH, rootPointer().graph());
        JsonNode status = ObjectMapperFactory.getSharedObjectMapper().valueToTree(otp2GraphControlService.status());
        assertEquals("BUILD_NOT_PROMOTED", status.get("skippedBuilds").get(0).get("action").asText());
        assertEquals(candidateGraph, status.get("skippedBuilds").get(0).get("graph").asText());
        assertEquals(PRODUCTION_ID, status.get("production").get("serializationId").asText());
    }

    @Test
    void reportIndexFailureDoesNotPreventThePromotion() {
        String newGraph = graph(PRODUCTION_ID, "20261015080000000");
        upload(newGraph, "new graph");
        uploadReport(newGraph);
        OtpReportBlobStoreService unavailableReports = new OtpReportBlobStoreService(otpReportContainerName, otpReportInMemoryBlobStoreRepository) {
            @Override
            public void uploadHtmlBlob(String name, InputStream inputStream) {
                throw new IllegalStateException("report bucket unavailable");
            }
        };
        Otp2GraphControlService service = new Otp2GraphControlService(graphControlRepository, transactionManager, stateStore,
                otpGraphsBlobStoreService, unavailableReports, productionVersionClient, otpReportContainerName);

        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                service.promoteBuiltGraph(newGraph, PRODUCTION_ID, false, reportVersion(newGraph)));
        assertEquals("new graph", read(STABLE_GRAPH));
        assertEquals(newGraph, pointer(PRODUCTION_ID).graph());
        assertEquals(newGraph, rootPointer().graph());
        assertEquals(0, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_GRAPH_REPORT_INDEX_FILE).getFiles().size());
    }

    @Test
    void unreadableStateFilesAreReportedWithoutFailingTheControls() throws Exception {
        context.start();
        String brokenPointer = stateStore.versionedPointerPath("EN-0200");
        String brokenPin = Otp2GraphPaths.pinPath(NEXT_ID);
        String emptyPointer = stateStore.versionedPointerPath("EN-0201");
        upload(brokenPointer, "{");
        upload(brokenPin, "{\"graph\":");
        upload(emptyPointer, "");

        HttpResponse<String> statusResponse = get("routing_graph/control");
        assertEquals(200, statusResponse.statusCode(), statusResponse.body());
        JsonNode status = json(statusResponse);
        assertEquals(Set.of(brokenPointer, brokenPin, emptyPointer), textValues(status.get("unreadableFiles")));
        assertEquals(LATEST_GRAPH, status.get("production").get("currentGraph").asText());
        assertTrue(status.get("pins").isEmpty());

        HttpResponse<String> versionsResponse = get("routing_graph/versions");
        assertEquals(200, versionsResponse.statusCode(), versionsResponse.body());
        assertEquals(Set.of(brokenPointer, brokenPin, emptyPointer), textValues(json(versionsResponse).get("unreadableFiles")));

        // the controls of the other serialization versions keep working
        HttpResponse<String> pinResponse = post("routing_graph/pin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"freeze\"}");
        assertEquals(200, pinResponse.statusCode(), pinResponse.body());
        JsonNode statusAfterPin = json(get("routing_graph/control"));
        assertTrue(statusAfterPin.get("production").get("pinned").asBoolean());
        assertEquals(Set.of(brokenPointer, brokenPin, emptyPointer), textValues(statusAfterPin.get("unreadableFiles")));
    }

    @Test
    void rolloutAndPinRepairsAnUnreadablePointer() throws Exception {
        context.start();
        String brokenPointer = stateStore.versionedPointerPath(PRODUCTION_ID);
        upload(brokenPointer, "{");
        assertEquals(Set.of(brokenPointer), textValues(json(get("routing_graph/control")).get("unreadableFiles")));

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":true,\"reason\":\"repair the pointer\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(json(response).get("production").asBoolean());

        assertEquals("old graph", read(STABLE_GRAPH));
        GraphPointer pointer = pointer(PRODUCTION_ID);
        assertEquals(OLD_GRAPH, pointer.graph());
        // the nature of the serialization version is unknown: read as a production build
        assertFalse(pointer.candidate());
        assertEquals(pointer, rootPointer());
        assertEquals(OLD_GRAPH, stateStore.readPin(PRODUCTION_ID).orElseThrow().graph());
        assertTrue(json(get("routing_graph/control")).get("unreadableFiles").isEmpty());
    }

    @Test
    void rolloutRepairsAnEmptyPointerAndRemovesThePin() throws Exception {
        context.start();
        String rootPointerContent = read(ROOT_POINTER);
        String emptyPointer = stateStore.versionedPointerPath(NEXT_ID);
        upload(emptyPointer, "");
        stateStore.writePin(NEXT_ID, new GraphPin(NEXT_VERSION_GRAPH, Instant.now(), "test-user", "freeze"));

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + NEXT_VERSION_GRAPH + "\",\"pin\":false,\"reason\":\"repair the pointer\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertFalse(json(response).get("production").asBoolean());

        assertEquals("next version graph", read(Otp2GraphPaths.stableGraphPath(NEXT_ID)));
        GraphPointer pointer = pointer(NEXT_ID);
        assertEquals(NEXT_VERSION_GRAPH, pointer.graph());
        assertFalse(pointer.candidate());
        assertTrue(stateStore.readPin(NEXT_ID).isEmpty());
        // another serialization version than production: OTP is not redeployed
        assertEquals(rootPointerContent, read(ROOT_POINTER));
        assertTrue(json(get("routing_graph/control")).get("unreadableFiles").isEmpty());
    }

    @Test
    void unreadableRootPointerLeavesProductionUnknown() throws Exception {
        context.start();
        upload(ROOT_POINTER, "{");

        HttpResponse<String> response = get("routing_graph/control");
        assertEquals(200, response.statusCode(), response.body());
        JsonNode status = json(response);
        // production is unknown: depending on the serialization settings, null or left out
        JsonNode production = status.get("production");
        assertTrue(production == null || production.isNull(), status.toString());
        assertEquals(Set.of(ROOT_POINTER), textValues(status.get("unreadableFiles")));
    }

    /**
     * An OTP release changed the serialization version: production OTP runs the next serialization version and loads
     * its candidate graph, while the root pointer file names a graph of the previous serialization version until the
     * first production build of the next one.
     */
    @Test
    void productionIsTheSerializationVersionReportedByOtp() throws Exception {
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(NEXT_VERSION_GRAPH, NEXT_ID, true, reportVersion(NEXT_VERSION_GRAPH)));
        when(productionVersionClient.serializationId()).thenReturn(Optional.of(NEXT_ID));
        context.start();
        String rootPointerContent = read(ROOT_POINTER);

        JsonNode production = json(get("routing_graph/control")).get("production");
        assertEquals(NEXT_ID, production.get("serializationId").asText());
        assertEquals(NEXT_VERSION_GRAPH, production.get("currentGraph").asText());
        assertEquals("OTP", production.get("source").asText());
        JsonNode versions = json(get("routing_graph/versions")).get("serializationVersions");
        assertEquals(NEXT_ID, versions.get(0).get("serializationId").asText());
        assertTrue(versions.get(0).get("production").asBoolean());
        assertFalse(versions.get(1).get("production").asBoolean());

        // OTP does not load the previous serialization version: it is not redeployed
        HttpResponse<String> previousVersionRollout = post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":false,\"reason\":\"bad data\"}");
        assertEquals(200, previousVersionRollout.statusCode(), previousVersionRollout.body());
        assertFalse(json(previousVersionRollout).get("production").asBoolean());
        assertEquals("old graph", read(STABLE_GRAPH));
        assertEquals(rootPointerContent, read(ROOT_POINTER));

        // a rollout of the serialization version OTP runs rewrites the root pointer file, which redeploys OTP
        HttpResponse<String> rollout = post("routing_graph/rollout", "{\"graph\":\"" + NEXT_VERSION_GRAPH + "\",\"pin\":false,\"reason\":\"restart OTP\"}");
        assertEquals(200, rollout.statusCode(), rollout.body());
        assertTrue(json(rollout).get("production").asBoolean());
        assertEquals(NEXT_VERSION_GRAPH, rootPointer().graph());
        assertEquals("test-user", rootPointer().updatedBy());
    }

    /**
     * OTP was downgraded to the previous serialization version, after a production build of the next one.
     */
    @Test
    void rolloutAfterAnOtpDowngradeRedeploysOtp() throws Exception {
        String nextVersionProductionGraph = graph(NEXT_ID, "20261015080000000");
        upload(nextVersionProductionGraph, "next version production graph");
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(nextVersionProductionGraph, NEXT_ID, false, reportVersion(nextVersionProductionGraph)));
        assertEquals(nextVersionProductionGraph, rootPointer().graph());
        when(productionVersionClient.serializationId()).thenReturn(Optional.of(PRODUCTION_ID));
        context.start();

        JsonNode production = json(get("routing_graph/control")).get("production");
        assertEquals(PRODUCTION_ID, production.get("serializationId").asText());
        assertEquals(LATEST_GRAPH, production.get("currentGraph").asText());

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":false,\"reason\":\"bad data\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(json(response).get("production").asBoolean());
        assertEquals("old graph", read(STABLE_GRAPH));
        assertEquals(OLD_GRAPH, rootPointer().graph());
    }

    @Test
    void rolloutOfTheSerializationVersionReportedByOtpRepairsAnUnreadableRootPointer() throws Exception {
        upload(ROOT_POINTER, "{");
        when(productionVersionClient.serializationId()).thenReturn(Optional.of(PRODUCTION_ID));
        context.start();

        JsonNode status = json(get("routing_graph/control"));
        assertEquals(PRODUCTION_ID, status.get("production").get("serializationId").asText());
        assertEquals("OTP", status.get("production").get("source").asText());
        assertEquals(Set.of(ROOT_POINTER), textValues(status.get("unreadableFiles")));

        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + LATEST_GRAPH + "\",\"pin\":false,\"reason\":\"repair the root pointer\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(json(response).get("production").asBoolean());
        assertEquals(LATEST_GRAPH, rootPointer().graph());
        assertTrue(json(get("routing_graph/control")).get("unreadableFiles").isEmpty());
    }

    @Test
    void rolloutRepairsAnUnreadableRootPointerWhenOtpCannotBeAsked() throws Exception {
        upload(ROOT_POINTER, "{");
        when(productionVersionClient.serializationId()).thenReturn(Optional.empty());
        context.start();

        // production is unknown
        JsonNode status = json(get("routing_graph/control"));
        JsonNode productionBefore = status.get("production");
        assertTrue(productionBefore == null || productionBefore.isNull(), status.toString());
        assertEquals(Set.of(ROOT_POINTER), textValues(status.get("unreadableFiles")));

        // the rollout is read as a rollout of production: it repairs the root pointer file, which redeploys OTP
        HttpResponse<String> response = post("routing_graph/rollout", "{\"graph\":\"" + OLD_GRAPH + "\",\"pin\":false,\"reason\":\"bad data\"}");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(json(response).get("production").asBoolean());
        assertEquals("old graph", read(STABLE_GRAPH));
        assertEquals(OLD_GRAPH, rootPointer().graph());
        assertEquals(pointer(PRODUCTION_ID), rootPointer());

        JsonNode statusAfter = json(get("routing_graph/control"));
        assertTrue(statusAfter.get("unreadableFiles").isEmpty());
        assertEquals(PRODUCTION_ID, statusAfter.get("production").get("serializationId").asText());
        assertEquals(OLD_GRAPH, statusAfter.get("production").get("currentGraph").asText());
    }

    @Test
    void olderGraphIsNotPromoted() {
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_OLDER,
                otp2GraphControlService.promoteBuiltGraph(OLD_GRAPH, PRODUCTION_ID, false, reportVersion(OLD_GRAPH)));
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(LATEST_GRAPH, rootPointer().graph());
    }

    @Test
    void productionBuildIsNotHeldBackByAnOverlappingCandidateBuild() {
        // a candidate build of the next serialization version finished first
        String candidateGraph = graph(NEXT_ID, "20261015090000000");
        upload(candidateGraph, "next version candidate graph");
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(candidateGraph, NEXT_ID, true, reportVersion(candidateGraph)));
        assertTrue(pointer(NEXT_ID).candidate());
        // a candidate build does not touch production
        assertEquals(LATEST_GRAPH, rootPointer().graph());

        // OTP is upgraded in production: the first production build of the next serialization version, started before
        // the candidate build, moves production to the next serialization version
        String productionGraph = graph(NEXT_ID, "20261015080000000");
        upload(productionGraph, "next version production graph");
        uploadReport(productionGraph);
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(productionGraph, NEXT_ID, false, reportVersion(productionGraph)));
        assertEquals("next version production graph", read(Otp2GraphPaths.stableGraphPath(NEXT_ID)));
        assertEquals(productionGraph, rootPointer().graph());
        assertFalse(pointer(NEXT_ID).candidate());
        assertEquals(1, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_GRAPH_REPORT_INDEX_FILE).getFiles().size());

        JsonNode status = ObjectMapperFactory.getSharedObjectMapper().valueToTree(otp2GraphControlService.status());
        assertEquals(NEXT_ID, status.get("production").get("serializationId").asText());
        assertEquals(productionGraph, status.get("production").get("currentGraph").asText());

        // a later candidate build of the production serialization version is not promoted
        String laterCandidateGraph = graph(NEXT_ID, "20261015100000000");
        upload(laterCandidateGraph, "later candidate graph");
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_CANDIDATE,
                otp2GraphControlService.promoteBuiltGraph(laterCandidateGraph, NEXT_ID, true, reportVersion(laterCandidateGraph)));
        assertEquals("next version production graph", read(Otp2GraphPaths.stableGraphPath(NEXT_ID)));
    }

    @Test
    void productionBuildReplacesAnUnreadablePointer() {
        String brokenPointer = stateStore.versionedPointerPath(PRODUCTION_ID);
        upload(brokenPointer, "{");

        // the current graph is unknown: the build is promoted even if it is older than the graph the pointer named
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(OLD_GRAPH, PRODUCTION_ID, false, reportVersion(OLD_GRAPH)));
        assertEquals("old graph", read(STABLE_GRAPH));
        GraphPointer pointer = pointer(PRODUCTION_ID);
        assertEquals(OLD_GRAPH, pointer.graph());
        assertFalse(pointer.candidate());
        assertEquals(pointer, rootPointer());

        // the replacement is audited with the control operations, the replaced content is lost
        OtpGraphControlStatus status = otp2GraphControlService.status();
        assertTrue(status.unreadableFiles().isEmpty());
        OtpGraphControlStatus.Action replacement = status.recentActions().getFirst();
        assertEquals("POINTER_REPLACED", replacement.action());
        assertEquals(OLD_GRAPH, replacement.graph());
        assertEquals("marduk", replacement.performedBy());
        assertTrue(replacement.reason().contains(brokenPointer), replacement.reason());
    }

    @Test
    void candidateBuildDoesNotReplaceAnUnreadablePointer() {
        String brokenPointer = stateStore.versionedPointerPath(PRODUCTION_ID);
        upload(brokenPointer, "{");
        String candidateGraph = graph(PRODUCTION_ID, "20261015080000000");
        upload(candidateGraph, "candidate graph");

        // the unknown current graph may be a production build, loaded by the production OTP instances
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_CANDIDATE,
                otp2GraphControlService.promoteBuiltGraph(candidateGraph, PRODUCTION_ID, true, reportVersion(candidateGraph)));
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals("{", read(brokenPointer));
        assertEquals(LATEST_GRAPH, rootPointer().graph());

        OtpGraphControlStatus status = otp2GraphControlService.status();
        assertEquals(List.of(brokenPointer), status.unreadableFiles());
        OtpGraphControlStatus.Action skippedBuild = status.skippedBuilds().getFirst();
        assertEquals("BUILD_NOT_PROMOTED", skippedBuild.action());
        assertEquals(candidateGraph, skippedBuild.graph());
        assertTrue(skippedBuild.reason().startsWith("Candidate build: the current graph is unknown"), skippedBuild.reason());
    }

    @Test
    void unreadablePinHoldsBackTheBuildsUntilUnpinned() throws Exception {
        context.start();
        String brokenPin = Otp2GraphPaths.pinPath(PRODUCTION_ID);
        upload(brokenPin, "{\"graph\":");
        String newGraph = graph(PRODUCTION_ID, "20261015080000000");
        upload(newGraph, "new graph");

        // the pin file is there: the serialization version is pinned, to an unknown graph
        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_PINNED,
                otp2GraphControlService.promoteBuiltGraph(newGraph, PRODUCTION_ID, false, reportVersion(newGraph)));
        assertEquals("latest graph", read(STABLE_GRAPH));
        assertEquals(LATEST_GRAPH, rootPointer().graph());
        JsonNode status = json(get("routing_graph/control"));
        assertTrue(status.get("production").get("pinned").asBoolean());
        assertEquals(Set.of(brokenPin), textValues(status.get("unreadableFiles")));
        JsonNode skippedBuild = status.get("skippedBuilds").get(0);
        assertEquals(newGraph, skippedBuild.get("graph").asText());
        assertTrue(skippedBuild.get("reason").asText().contains(brokenPin), skippedBuild.get("reason").asText());

        // unpinning removes the file without a rollout: OTP is not redeployed
        String rootPointerContent = read(ROOT_POINTER);
        HttpResponse<String> unpinResponse = post("routing_graph/unpin", "{\"serializationId\":\"" + PRODUCTION_ID + "\",\"reason\":\"remove the broken pin\"}");
        assertEquals(200, unpinResponse.statusCode(), unpinResponse.body());
        JsonNode unpinResult = json(unpinResponse);
        assertFalse(unpinResult.get("pinned").asBoolean());
        assertTrue(unpinResult.get("graph") == null || unpinResult.get("graph").isNull(), unpinResult.toString());
        assertNull(read(brokenPin));
        assertEquals(rootPointerContent, read(ROOT_POINTER));

        JsonNode statusAfterUnpin = json(get("routing_graph/control"));
        assertFalse(statusAfterUnpin.get("production").get("pinned").asBoolean());
        assertTrue(statusAfterUnpin.get("unreadableFiles").isEmpty());
        JsonNode unpin = statusAfterUnpin.get("recentActions").get(0);
        assertEquals("UNPIN", unpin.get("action").asText());
        assertTrue(unpin.get("reason").asText().startsWith("remove the broken pin (the pin file could not be parsed"), unpin.get("reason").asText());

        // the next build is promoted
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(newGraph, PRODUCTION_ID, false, reportVersion(newGraph)));
        assertEquals("new graph", read(STABLE_GRAPH));
    }

    @Test
    void pointerFilesWrittenBeforeTheControlsAreUnderstood() throws Exception {
        // the pointer files only contain the path of the graph
        upload(ROOT_POINTER, LATEST_GRAPH + "\n");
        upload(stateStore.versionedPointerPath(PRODUCTION_ID), LATEST_GRAPH + "\n");
        context.start();

        JsonNode status = json(get("routing_graph/control"));
        assertEquals(PRODUCTION_ID, status.get("production").get("serializationId").asText());
        assertEquals(LATEST_GRAPH, status.get("production").get("currentGraph").asText());

        assertEquals(Otp2GraphControlService.Promotion.SKIPPED_OLDER,
                otp2GraphControlService.promoteBuiltGraph(OLD_GRAPH, PRODUCTION_ID, false, reportVersion(OLD_GRAPH)));
        String newGraph = graph(PRODUCTION_ID, "20261015080000000");
        upload(newGraph, "new graph");
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(newGraph, PRODUCTION_ID, false, reportVersion(newGraph)));
        assertEquals("new graph", read(STABLE_GRAPH));
        assertEquals(newGraph, rootPointer().graph());
        assertEquals("marduk", rootPointer().updatedBy());
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

    private static Set<String> textValues(JsonNode array) {
        Set<String> values = new HashSet<>();
        array.forEach(node -> values.add(node.asText()));
        return values;
    }

    private static JsonNode json(HttpResponse<String> response) throws IOException {
        return ObjectMapperFactory.getSharedObjectMapper().readTree(response.body());
    }

    private static String graph(String serializationId, String timestamp) {
        return Otp2GraphPaths.versionedGraphPath(serializationId, timestamp, "Graph-otp2-" + serializationId + ".obj");
    }

    private static String reportVersion(String graph) {
        return Otp2GraphPaths.parseVersionedGraph(graph).orElseThrow().reportVersion();
    }

    private GraphPointer rootPointer() {
        return stateStore.readRootPointer().orElseThrow();
    }

    private GraphPointer pointer(String serializationId) {
        return stateStore.readPointer(serializationId).orElseThrow();
    }

    private void upload(String name, String content) {
        graphsInMemoryBlobStoreRepository.uploadBlob(name, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private void uploadReport(String graph) {
        otpReportInMemoryBlobStoreRepository.uploadBlob(reportVersion(graph) + "/index.html", new ByteArrayInputStream("report".getBytes(StandardCharsets.UTF_8)));
    }

    private String read(String name) {
        try (InputStream content = graphsInMemoryBlobStoreRepository.getBlob(name)) {
            return content == null ? null : IOUtils.toString(content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
