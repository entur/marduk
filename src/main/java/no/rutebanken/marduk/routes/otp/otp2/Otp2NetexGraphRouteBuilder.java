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
import no.rutebanken.marduk.graph.Otp2GraphControlService;
import no.rutebanken.marduk.kubernetes.KubernetesJobTimeoutException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilderProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP_BUILD_CANDIDATE;
import static no.rutebanken.marduk.Constants.OTP_GRAPH_VERSION;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TARGET_FILE_PARENT;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

/**
 * Build remotely a full OTP graph (containing OSM data, elevation data and NeTEx data).
 */
@Component
public class Otp2NetexGraphRouteBuilder extends BaseRouteBuilder {

    private static final String PROP_MESSAGES = "RutebankenPropMessages";

    private static final String PROP_STATUS = "RutebankenGraphBuildStatus";

    private static final String PUBLISHED_GRAPH_PATH_PROPERTY = "RutebankenPublishedGraphPath";

    private static final String PROP_GRAPH_PROMOTION = "RutebankenGraphPromotion";

    /**
     * Id of the processor promoting the built graph, for tests.
     */
    static final String PROMOTE_GRAPH_PROCESSOR_ID = "otp2-graph-promote";

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Value("${blobstore.gcs.graphs.container.name:otp-graphs}")
    private String otpGraphsBucketName;

    @Value("${otp2.graph.promotion.max.redeliveries:2}")
    private int promotionMaxRedeliveries;

    @Value("${otp2.graph.promotion.redelivery.delay:10000}")
    private long promotionRedeliveryDelay;

    @Value("${otp.graph.build.remote.work.dir.cleanup:true}")
    private boolean deleteOtpRemoteWorkDir;

    @Value("${blobstore.gcs.otpreport.container.name}")
    String otpReportContainerName;

    @Autowired
    private Otp2NetexGraphBuilder otp2NetexGraphBuilder;

    @Autowired
    private Otp2GraphControlService otp2GraphControlService;

    @Override
    public void configure() throws Exception {
        super.configure();


        // the job is deleted on timeout, so a redelivery rebuilds from scratch instead of reattaching to it
        onException(KubernetesJobTimeoutException.class)
                .maximumRedeliveries(0);

        singletonFrom("google-pubsub:{{marduk.pubsub.project.id}}:Otp2GraphBuildQueue?maxAckExtensionPeriod=14400").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(new GroupedMessageAggregationStrategy()).constant(true).completionSize(100).aggregateController(idleRouteAggregationMonitor.getAggregateControllerForRoute("otp2-remote-netex-graph-build"))
                .process(this::addSynchronizationForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtp2Graph")
                .routeId("otp2-graph-build");

        singletonFrom("google-pubsub:{{marduk.pubsub.project.id}}:Otp2GraphCandidateBuildQueue?maxAckExtensionPeriod=14400").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .process(this::removeSynchronizationForAggregatedExchange)
                .aggregate(new GroupedMessageAggregationStrategy()).constant(true).completionSize(100).aggregateController(idleRouteAggregationMonitor.getAggregateControllerForRoute("otp2-remote-netex-graph-build"))
                .process(this::addSynchronizationForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .setProperty(OTP_BUILD_CANDIDATE, simple("true", Boolean.class))
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 graph candidate building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtp2Graph")
                .routeId("otp2-graph-candidate-build");

        from("direct:remoteBuildOtp2Graph")
                .setProperty(PROP_MESSAGES, simple("${body}"))
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtp2NetexGraphBuildStartedEventsInNewTransaction")
                .setProperty(OTP_REMOTE_WORK_DIR, simple(blobStoreSubdirectory + "/work/" + UUID.randomUUID() + "/${exchangeProperty." + TIMESTAMP + "}"))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting OTP2 graph building in remote directory ${exchangeProperty." + OTP_REMOTE_WORK_DIR + "}.")

                .choice()
                .when(PredicateBuilder.not(exchangeProperty(OTP_BUILD_CANDIDATE)))
                .to("direct:otp2ExportMergedNetex")
                .end()

                .to("direct:remoteBuildOtp2NetexGraphAndSendStatus")
                .to("direct:remoteOtp2GraphPublishing")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Done with OTP2 graph building route.")
                .routeId("otp2-remote-netex-graph-build");

        from("direct:remoteBuildOtp2NetexGraphAndSendStatus")
                .log(LoggingLevel.INFO, correlation() + "Building OTP2 graph...")
                .doTry()
                .to("direct:remoteBuildOtp2NetexGraph")
                .setProperty(PROP_STATUS, constant(JobEvent.State.OK))
                .to("direct:sendStatusForOtp2NetexJobs")
                .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, correlation() + "OTP2 Graph building failed: ${exception.message} stacktrace: ${exception.stacktrace}")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_GRAPH).state(JobEvent.State.FAILED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .setProperty(PROP_STATUS, constant(JobEvent.State.FAILED))
                .to("direct:sendStatusForOtp2NetexJobs")
                .to("direct:remoteOtp2CleanUp")
                .stop()
                .end()
                .routeId("otp2-remote-netex-graph-build-and-send-status");

        from("direct:remoteBuildOtp2NetexGraph")
                .process(new OtpGraphBuilderProcessor(otp2NetexGraphBuilder))
                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP2 graph.")
                .routeId("otp2-remote-netex-graph-build-otp");

        from("direct:remoteOtp2GraphPublishing")
                .setHeader(Constants.FILE_PREFIX, simple("${exchangeProperty." + OTP_REMOTE_WORK_DIR + "}/" + OTP2_GRAPH_OBJ_PREFIX))
                .to("direct:findInternalBlob")
                .validate(body().isNotNull())
                .log(LoggingLevel.INFO, correlation() + "Found OTP2 graph named ${body.fileNameOnly} matching file prefix ${header." + Constants.FILE_PREFIX + "}")

                // copy the new graph from the OTP remote work directory to the graphs directory in GCS
                .process(new Otp2NetexGraphPublishingProcessor(otpGraphsBucketName))
                .to("direct:copyInternalBlobToAnotherBucket")
                .log(LoggingLevel.INFO, correlation() + "Done copying new OTP2 graph: ${header." + FILE_HANDLE + "}")

                .setProperty(PUBLISHED_GRAPH_PATH_PROPERTY, header(TARGET_FILE_HANDLE))

                // copy the graph build report of a production build, so that it is available even if the graph is not promoted
                .choice()
                .when(PredicateBuilder.not(exchangeProperty(OTP_BUILD_CANDIDATE)))
                .to("direct:remoteCopyVersionedOtp2GraphBuildReport")
                .log(LoggingLevel.INFO, correlation() + "Done uploading OTP2 graph build reports.")
                .end()

                .to("direct:promoteOtp2Graph")
                .log(LoggingLevel.INFO, correlation() + "OTP2 graph ${exchangeProperty." + PUBLISHED_GRAPH_PATH_PROPERTY + "} promotion: ${exchangeProperty." + PROP_GRAPH_PROMOTION + "}")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_GRAPH).state(JobEvent.State.OK).correlationId(e.getProperty(TIMESTAMP, String.class)).build())
                .to("direct:updateStatus")
                .to("direct:remoteOtp2CleanUp")
                .routeId("otp2-remote-netex-graph-publish");

        // make the new graph the current graph: copy it to the stable name loaded by the OTP instances and record it
        // in the pointer file (and the report index and root pointer file for a production build), unless the
        // serialization version is pinned, a more recent graph is current, or this is a candidate build of a
        // serialization version whose current graph is a production build.
        // The promotion is cheap compared to the build: it is retried on its own, and a persistent failure leaves the
        // graph published but not current (available for rollout) instead of failing the publication, which would
        // rebuild the graph from scratch.
        from("direct:promoteOtp2Graph")
                .onException(Exception.class)
                    .maximumRedeliveries(promotionMaxRedeliveries)
                    .redeliveryDelay(promotionRedeliveryDelay)
                    .useExponentialBackOff()
                    // the publication continues after the failed promotion
                    .continued(true)
                    .log(LoggingLevel.ERROR, correlation() + "The promotion of the OTP2 graph ${exchangeProperty." + PUBLISHED_GRAPH_PATH_PROPERTY + "} failed, the graph is published but not current: ${exception.message} stacktrace: ${exception.stacktrace}")
                    .process(this::recordPromotionFailure)
                .end()
                .process(this::promoteGraph).id(PROMOTE_GRAPH_PROCESSOR_ID)
                .routeId("otp2-graph-promotion");

        from("direct:remoteCopyVersionedOtp2GraphBuildReport")
                .process(e -> {
                    e.getIn().setHeader(Exchange.FILE_PARENT, e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/report");
                    e.getIn().setHeader(TARGET_CONTAINER, otpReportContainerName);
                    e.getIn().setHeader(TARGET_FILE_PARENT, e.getProperty(OTP_GRAPH_VERSION, String.class));
                })
                .log(LoggingLevel.INFO, correlation() + "Copying OTP2 graph build reports to gs://${header." + TARGET_CONTAINER + "}/${header." + TARGET_FILE_PARENT + "}")
                .to("direct:copyAllInternalBlobs")
                .log(LoggingLevel.INFO, correlation() + "Done copying OTP2 graph build reports.")
                .routeId("otp2-remote-graph-build-report-versioned-upload");

        from("direct:remoteOtp2CleanUp")
                .choice()
                .when(constant(deleteOtpRemoteWorkDir))
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(OTP_REMOTE_WORK_DIR))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Deleting OTP2 remote work directory ${header." + Exchange.FILE_PARENT + "} ...")
                .to("direct:deleteAllInternalBlobsInFolder")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Deleted OTP2 remote work directory ${header." + Exchange.FILE_PARENT + "}")
                .end()
                .routeId("otp2-remote-graph-cleanup");

        from("direct:sendOtp2NetexGraphBuildStartedEventsInNewTransaction")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_GRAPH).state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .setProperty(PROP_STATUS, constant(JobEvent.State.STARTED))
                .to("direct:sendStatusForOtp2NetexJobs")
                .routeId("otp2-netex-graph-send-started-events");

        from("direct:sendStatusForOtp2NetexJobs")
                .doTry() // <- doTry seems necessary for correct transactional handling. not sure why...
                .split().exchangeProperty(PROP_MESSAGES)
                .filter(simple("${headers[" + CHOUETTE_REFERENTIAL + "]}"))
                .process(e -> {
                    JobEvent.State state = e.getProperty(PROP_STATUS, JobEvent.State.class);
                    JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.OTP2_BUILD_GRAPH).state(state).build();
                })
                .to("direct:updateStatus")
                .routeId("otp2-netex-graph-send-status-for-timetable-jobs");

    }

    private void promoteGraph(Exchange e) {
        Otp2GraphControlService.Promotion promotion = otp2GraphControlService.promoteBuiltGraph(
                e.getProperty(PUBLISHED_GRAPH_PATH_PROPERTY, String.class),
                e.getIn().getHeader(Constants.GRAPH_COMPATIBILITY_VERSION, String.class),
                isCandidateBuild(e),
                e.getProperty(OTP_GRAPH_VERSION, String.class));
        e.setProperty(PROP_GRAPH_PROMOTION, promotion);
    }

    private void recordPromotionFailure(Exchange e) {
        e.setProperty(PROP_GRAPH_PROMOTION, Otp2GraphControlService.Promotion.FAILED);
        otp2GraphControlService.recordPromotionFailure(
                e.getProperty(PUBLISHED_GRAPH_PATH_PROPERTY, String.class),
                e.getIn().getHeader(Constants.GRAPH_COMPATIBILITY_VERSION, String.class),
                e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class));
    }

    private static boolean isCandidateBuild(Exchange e) {
        return Boolean.TRUE.equals(e.getProperty(OTP_BUILD_CANDIDATE, Boolean.class));
    }


}
