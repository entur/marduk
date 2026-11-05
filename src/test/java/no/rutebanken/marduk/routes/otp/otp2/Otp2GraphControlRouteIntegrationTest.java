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

package no.rutebanken.marduk.routes.otp.otp2;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.OtpGraphControlStatus;
import no.rutebanken.marduk.graph.Otp2GraphControlService;
import no.rutebanken.marduk.graph.Otp2GraphStateStore;
import no.rutebanken.marduk.graph.Otp2GraphStateStore.GraphPointer;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The emergency controls applied by the OTP2 graph build route.
 */
class Otp2GraphControlRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String SERIALIZATION_ID = "EN-TEST";
    private static final String PINNED_GRAPH = "netex-otp2/" + SERIALIZATION_ID + "/20260101000000000-Graph-otp2-" + SERIALIZATION_ID + ".obj";
    private static final String STABLE_GRAPH = "netex-otp2/" + SERIALIZATION_ID + "/" + Constants.OTP2_CURRENT_GRAPH_OBJ;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @EndpointInject("mock:exportMergedNetex")
    protected MockEndpoint exportMergedNetex;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:Otp2GraphBuildQueue")
    protected ProducerTemplate graphBuildQueue;

    @Autowired
    private Otp2GraphControlService otp2GraphControlService;

    @Autowired
    private Otp2GraphStateStore stateStore;

    @Test
    void graphIsNotPromotedWhenTheSerializationVersionIsPinned() throws Exception {
        // the pinned graph was promoted by a production build
        graphsInMemoryBlobStoreRepository.uploadBlob(PINNED_GRAPH, content("pinned graph"));
        assertEquals(Otp2GraphControlService.Promotion.PROMOTED,
                otp2GraphControlService.promoteBuiltGraph(PINNED_GRAPH, SERIALIZATION_ID, false, "netex-otp2/20260101000000000-report"));
        otp2GraphControlService.pin(SERIALIZATION_ID, "bad data in the latest build", "test-user");

        adviceGraphBuild();
        // GRAPH STARTED, provider STARTED, provider OK, GRAPH OK
        updateStatus.expectedMessageCount(4);
        updateStatus.setResultWaitTime(20000);
        exportMergedNetex.expectedMessageCount(1);
        context.start();

        sendBodyAndHeadersToPubSub(graphBuildQueue, "", createProviderJobHeaders(1L, "ref1", "corr-id-1"));
        updateStatus.assertIsSatisfied();
        exportMergedNetex.assertIsSatisfied();

        // the new graph is published, but the pinned graph stays current
        List<String> publishedGraphs = publishedGraphs();
        assertEquals(2, publishedGraphs.size());
        assertEquals("pinned graph", read(STABLE_GRAPH));
        assertEquals(PINNED_GRAPH, stateStore.readPointer(SERIALIZATION_ID).map(GraphPointer::graph).orElseThrow());
        assertEquals(PINNED_GRAPH, stateStore.readRootPointer().map(GraphPointer::graph).orElseThrow());
        // the build report of the new graph is available
        assertEquals(1, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_NETEX_GRAPH_DIR).getFiles().size());

        OtpGraphControlStatus status = otp2GraphControlService.status();
        assertEquals(SERIALIZATION_ID, status.production().serializationId());
        assertTrue(status.production().pinned());
        assertEquals(PINNED_GRAPH, status.production().currentGraph());
        // the build is listed with the skipped builds, the control operations stay visible
        assertEquals("BUILD_NOT_PROMOTED", status.skippedBuilds().getFirst().action());
        assertEquals(publishedGraphs.stream().filter(g -> !g.equals(PINNED_GRAPH)).findFirst().orElseThrow(), status.skippedBuilds().getFirst().graph());
        assertEquals("PIN", status.recentActions().getFirst().action());
        assertTrue(status.recentActions().stream().noneMatch(a -> "BUILD_NOT_PROMOTED".equals(a.action())));
    }

    @Test
    void promotionFailureLeavesTheGraphPublishedButNotCurrent() throws Exception {
        AtomicInteger promotionAttempts = new AtomicInteger();
        adviceGraphBuild();
        AdviceWith.adviceWith(context, "otp2-graph-promotion", a -> a.weaveById(Otp2NetexGraphRouteBuilder.PROMOTE_GRAPH_PROCESSOR_ID).replace().process(e -> {
            promotionAttempts.incrementAndGet();
            throw new IllegalStateException("bucket unavailable");
        }));
        // GRAPH STARTED, provider STARTED, provider OK, GRAPH OK: the publication succeeds
        updateStatus.expectedMessageCount(4);
        updateStatus.setResultWaitTime(20000);
        context.start();

        sendBodyAndHeadersToPubSub(graphBuildQueue, "", createProviderJobHeaders(1L, "ref1", "corr-id-1"));
        updateStatus.assertIsSatisfied();

        // the promotion was retried on its own, without rebuilding the graph
        assertEquals(3, promotionAttempts.get());
        List<String> publishedGraphs = publishedGraphs();
        assertEquals(1, publishedGraphs.size());
        assertNull(read(STABLE_GRAPH));
        assertTrue(stateStore.readRootPointer().isEmpty());
        OtpGraphControlStatus.Action skippedBuild = otp2GraphControlService.status().skippedBuilds().getFirst();
        assertEquals("BUILD_NOT_PROMOTED", skippedBuild.action());
        assertEquals(publishedGraphs.getFirst(), skippedBuild.graph());
        assertEquals("The promotion failed: bucket unavailable", skippedBuild.reason());
    }

    /**
     * Replace the graph builder with the creation of a dummy graph for the serialization version {@link #SERIALIZATION_ID}.
     */
    private void adviceGraphBuild() throws Exception {
        AdviceWith.adviceWith(context, "otp2-netex-graph-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-netex-graph-send-status-for-timetable-jobs", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-remote-netex-graph-publish", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-remote-netex-graph-build", a -> a.weaveByToUri("direct:otp2ExportMergedNetex").replace().to("mock:exportMergedNetex"));
        AdviceWith.adviceWith(context, "otp2-remote-netex-graph-build-and-send-status", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            a.weaveByToUri("direct:remoteBuildOtp2NetexGraph").replace().process(exchange -> {
                String workDir = exchange.getProperty(OTP_REMOTE_WORK_DIR, String.class);
                internalInMemoryBlobStoreRepository.uploadBlob(workDir + "/Graph-otp2-" + SERIALIZATION_ID + ".obj", content("new graph"));
                internalInMemoryBlobStoreRepository.uploadBlob(workDir + "/report/report.html", content("report"));
            });
        });
    }

    private List<String> publishedGraphs() {
        return graphsInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_NETEX_GRAPH_DIR).getFiles().stream()
                .map(BlobStoreFiles.File::getName)
                .filter(name -> name.matches(".*/\\d{17}-Graph-otp2-.*\\.obj"))
                .toList();
    }

    private String read(String name) {
        try (InputStream content = graphsInMemoryBlobStoreRepository.getBlob(name)) {
            return content == null ? null : IOUtils.toString(content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream content(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
