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
import no.rutebanken.marduk.domain.OtpGraphControlStatus;
import no.rutebanken.marduk.graph.GraphControlRepository;
import no.rutebanken.marduk.graph.GraphControlRepository.GraphType;
import no.rutebanken.marduk.graph.Otp2GraphControlService;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pin and pause of the OTP2 transit graph publication, through the graph build routes.
 */
class Otp2GraphControlRouteIntegrationTest extends MardukRouteBuilderIntegrationTestBase {

    private static final String SERIALIZATION_ID = "EN-TEST";
    private static final String PINNED_GRAPH = "netex-otp2/" + SERIALIZATION_ID + "/20260101000000000-Graph-otp2-" + SERIALIZATION_ID + ".obj";
    private static final String CURRENT_POINTER = "current-otp2";
    private static final String VERSIONED_POINTER = "netex-otp2/" + SERIALIZATION_ID + "/current-otp2";
    private static final String STABLE_GRAPH = "netex-otp2/" + SERIALIZATION_ID + "/" + Constants.OTP2_CURRENT_GRAPH_OBJ;

    @EndpointInject("mock:updateStatus")
    protected MockEndpoint updateStatus;

    @EndpointInject("mock:baseGraphBuildStarted")
    protected MockEndpoint baseGraphBuildStarted;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:Otp2GraphBuildQueue")
    protected ProducerTemplate graphBuildQueue;

    @Produce("google-pubsub:{{marduk.pubsub.project.id}}:Otp2BaseGraphBuildQueue")
    protected ProducerTemplate baseGraphBuildQueue;

    @Autowired
    private Otp2GraphControlService otp2GraphControlService;

    @Autowired
    private GraphControlRepository graphControlRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void graphIsNotPromotedWhenTheSerializationVersionIsPinned() throws Exception {
        graphsInMemoryBlobStoreRepository.uploadBlob(PINNED_GRAPH, content("pinned graph"));
        otp2GraphControlService.rollout(PINNED_GRAPH, true, "bad data in the latest build", "test-user");

        adviceGraphBuild(null);
        // GRAPH STARTED, provider STARTED, provider OK, GRAPH OK
        updateStatus.expectedMessageCount(4);
        updateStatus.setResultWaitTime(20000);
        context.start();

        sendBodyAndHeadersToPubSub(graphBuildQueue, "", createProviderJobHeaders(1L, "ref1", "corr-id-1"));
        updateStatus.assertIsSatisfied();

        // the new graph is published, but the pinned graph stays current
        List<String> publishedGraphs = publishedGraphs();
        assertEquals(2, publishedGraphs.size());
        assertEquals("pinned graph", read(STABLE_GRAPH));
        assertEquals(PINNED_GRAPH, read(VERSIONED_POINTER));
        assertEquals(PINNED_GRAPH, read(CURRENT_POINTER));
        // the build report of the new graph is available
        assertEquals(1, otpReportInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_NETEX_GRAPH_DIR).getFiles().size());

        OtpGraphControlStatus status = otp2GraphControlService.status();
        assertTrue(status.production().pinned());
        assertEquals(PINNED_GRAPH, status.production().currentGraph());
        assertEquals("BUILD_NOT_PROMOTED", status.recentActions().getFirst().action());
    }

    @Test
    void buildRequestsAreDeferredWhileTheGraphBuilderIsPausedAndReplayedOnResume() throws Exception {
        otp2GraphControlService.pause("investigating", "test-user");

        adviceGraphBuild(null);
        context.start();

        for (long refId = 1; refId <= 2; refId++) {
            sendBodyAndHeadersToPubSub(graphBuildQueue, "", createProviderJobHeaders(refId, "ref" + refId, "corr-id-" + refId));
        }
        waitUntil(() -> deferredBuildRequests(GraphType.TRANSIT) == 2);
        // the messages are acknowledged: they are not redelivered
        Thread.sleep(3000);
        assertEquals(2, deferredBuildRequests(GraphType.TRANSIT));
        assertEquals(0, updateStatus.getReceivedCounter());
        assertTrue(publishedGraphs().isEmpty());

        // GRAPH STARTED, 2 x provider STARTED, 2 x provider OK, GRAPH OK
        updateStatus.expectedMinimumMessageCount(6);
        updateStatus.setResultWaitTime(30000);
        otp2GraphControlService.resume("fixed", "test-user");
        updateStatus.assertIsSatisfied();

        List<JobEvent> events = updateStatus.getExchanges().stream().map(e -> JobEvent.fromString(e.getIn().getBody().toString())).toList();
        for (long providerId = 1; providerId <= 2; providerId++) {
            long id = providerId;
            assertTrue(events.stream().anyMatch(je -> JobEvent.JobDomain.TIMETABLE.equals(je.getDomain()) && JobEvent.State.OK.equals(je.getState())
                    && id == je.getProviderId() && ("corr-id-" + id).equals(je.getCorrelationId())));
        }
        assertEquals(0, deferredBuildRequests(GraphType.TRANSIT));
        waitUntil(() -> read(CURRENT_POINTER) != null);
        assertFalse(otp2GraphControlService.status().builder().paused());
    }

    @Test
    void graphBuiltWhileTheGraphBuilderIsPausedIsNotPromoted() throws Exception {
        // the graph builder is paused while the first graph is being built
        AtomicBoolean firstBuild = new AtomicBoolean(true);
        adviceGraphBuild(() -> {
            if (firstBuild.getAndSet(false)) {
                otp2GraphControlService.pause("investigating", "test-user");
            }
        });
        updateStatus.expectedMessageCount(4);
        updateStatus.setResultWaitTime(20000);
        context.start();

        sendBodyAndHeadersToPubSub(graphBuildQueue, "", createProviderJobHeaders(1L, "ref1", "corr-id-1"));
        updateStatus.assertIsSatisfied();

        assertEquals(1, publishedGraphs().size());
        assertNull(read(STABLE_GRAPH));
        assertNull(read(VERSIONED_POINTER));
        assertNull(read(CURRENT_POINTER));
        // a new build is requested when the graph builder is resumed
        assertEquals(1, deferredBuildRequests(GraphType.TRANSIT));

        otp2GraphControlService.resume("fixed", "test-user");
        assertEquals(0, deferredBuildRequests(GraphType.TRANSIT));
        waitUntil(() -> read(CURRENT_POINTER) != null);
        assertEquals(2, publishedGraphs().size());
        assertEquals(read(CURRENT_POINTER), read(VERSIONED_POINTER));
        assertEquals("new graph", read(STABLE_GRAPH));
    }

    @Test
    void baseGraphBuildRequestsAreDeferredWhileTheGraphBuilderIsPaused() throws Exception {
        otp2GraphControlService.pause("investigating", "test-user");

        AdviceWith.adviceWith(context, "otp2-base-graph-build", a -> {
            a.weaveByToUri("direct:sendOtp2BaseGraphStartedEventsInNewTransaction").replace().to("mock:baseGraphBuildStarted");
            a.weaveByToUri("direct:remoteBuildOtp2BaseGraphAndSendStatus").replace().to("mock:sink");
        });
        context.start();

        sendBodyAndHeadersToPubSub(baseGraphBuildQueue, "", Map.of(Constants.CORRELATION_ID, "corr-id-osm"));
        waitUntil(() -> deferredBuildRequests(GraphType.BASE) == 1);
        assertEquals(0, baseGraphBuildStarted.getReceivedCounter());

        baseGraphBuildStarted.expectedMessageCount(1);
        baseGraphBuildStarted.setResultWaitTime(20000);
        otp2GraphControlService.resume("fixed", "test-user");
        baseGraphBuildStarted.assertIsSatisfied();
        assertEquals(0, deferredBuildRequests(GraphType.BASE));
    }

    /**
     * Replace the graph builder with the creation of a dummy graph for the serialization version {@link #SERIALIZATION_ID}.
     *
     * @param duringBuild an action run while the graph is being built
     */
    private void adviceGraphBuild(Runnable duringBuild) throws Exception {
        AdviceWith.adviceWith(context, "otp2-netex-graph-send-started-events", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-netex-graph-send-status-for-timetable-jobs", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-remote-netex-graph-publish", a -> a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus"));
        AdviceWith.adviceWith(context, "otp2-remote-netex-graph-build", a -> a.weaveByToUri("direct:otp2ExportMergedNetex").replace().to("mock:sink"));
        AdviceWith.adviceWith(context, "otp2-remote-netex-graph-build-and-send-status", a -> {
            a.weaveByToUri("direct:updateStatus").replace().to("mock:updateStatus");
            a.weaveByToUri("direct:remoteBuildOtp2NetexGraph").replace().process(exchange -> {
                if (duringBuild != null) {
                    duringBuild.run();
                }
                String workDir = exchange.getProperty(OTP_REMOTE_WORK_DIR, String.class);
                internalInMemoryBlobStoreRepository.uploadBlob(workDir + "/Graph-otp2-" + SERIALIZATION_ID + ".obj", content("new graph"));
                internalInMemoryBlobStoreRepository.uploadBlob(workDir + "/report/report.html", content("report"));
            });
        });
    }

    private List<String> publishedGraphs() {
        return graphsInMemoryBlobStoreRepository.listBlobs(Constants.OTP2_NETEX_GRAPH_DIR).getFiles().stream()
                .map(f -> f.getName())
                .filter(name -> name.matches(".*/\\d{17}-Graph-otp2-.*\\.obj"))
                .toList();
    }

    private int deferredBuildRequests(GraphType graphType) {
        return new TransactionTemplate(transactionManager).execute(status -> graphControlRepository.countDeferredBuildRequests(graphType));
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

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition not met within 20 seconds");
            }
            Thread.sleep(200);
        }
    }
}
