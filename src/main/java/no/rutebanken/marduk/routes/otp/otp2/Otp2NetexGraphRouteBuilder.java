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
import no.rutebanken.marduk.domain.BlobStoreFiles;
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

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP_BUILD_CANDIDATE;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static org.apache.camel.builder.Builder.exceptionStackTrace;

/**
 * Build remotely a full OTP graph (containing OSM data, elevation data and NeTEx data).
 */
@Component
public class Otp2NetexGraphRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Value("${otp2.graph.current.file:current-otp2}")
    private String otpGraphCurrentFile;

    @Value("${blobstore.gcs.graphs.container.name:otp-graphs}")
    private String otpGraphsBucketName;

    @Value("${otp.graph.build.remote.work.dir.cleanup:true}")
    private boolean deleteOtpRemoteWorkDir;

    private static final String PROP_MESSAGES = "RutebankenPropMessages";

    private static final String PROP_STATUS = "RutebankenGraphBuildStatus";

    private static final String GRAPH_PATH_PROPERTY = "RutebankenGraphPath";

    @Autowired
    private Otp2NetexGraphBuilder otp2NetexGraphBuilder;

    @Override
    public void configure() throws Exception {
        super.configure();

        // acknowledgment mode switched to NONE so that the ack/nack callback can be set after message aggregation.
        singletonFrom("entur-google-pubsub:Otp2GraphBuildQueue?ackMode=NONE").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .aggregate(simple("true", Boolean.class)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtp2Graph")
                .routeId("otp2-graph-build");

        singletonFrom("entur-google-pubsub:Otp2GraphCandidateBuildQueue?ackMode=NONE").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .aggregate(simple("true", Boolean.class)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .setProperty(OTP_BUILD_CANDIDATE, simple("true", Boolean.class))
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 graph candidate building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtp2Graph")
                .routeId("otp2-graph-candidate-build");

        from("direct:remoteBuildOtp2Graph")
                .setProperty(PROP_MESSAGES, simple("${body}"))
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtp2NetexGraphBuildStartedEventsInNewTransaction")
                .setProperty(OTP_REMOTE_WORK_DIR, simple(blobStoreSubdirectory + "/work/" + UUID.randomUUID().toString() + "/${exchangeProperty." + TIMESTAMP + "}"))
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
                .log(LoggingLevel.ERROR, correlation() + "OTP2 Graph building failed: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("OTP2_BUILD_GRAPH").state(JobEvent.State.FAILED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
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
                .to("direct:findBlob")
                .log(LoggingLevel.INFO, correlation() + "Found OTP2 graph named ${body.fileNameOnly} matching file prefix ${header." + Constants.FILE_PREFIX + "}")

                // copy the new graph from the OTP remote work directory to the graphs directory in GCS
                .process(new Otp2NetexGraphPublishingProcessor(otpGraphsBucketName))
                .to("direct:copyBlobToAnotherBucket")
                .log(LoggingLevel.INFO, correlation() + "Done copying new OTP2 graph: ${header." + FILE_HANDLE + "}")

                .setProperty(GRAPH_PATH_PROPERTY, header(FILE_HANDLE))

                // update file containing the reference to the latest graph
                .setBody(header(TARGET_FILE_HANDLE))
                .setHeader(FILE_HANDLE, constant(otpGraphCurrentFile))
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, simple("false", Boolean.class))
                .to("direct:uploadOtpGraphsBlob")
                .log(LoggingLevel.INFO, correlation() + "Done uploading reference to current OTP2graph: ${header." + FILE_HANDLE + "}")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("OTP2_BUILD_GRAPH").state(JobEvent.State.OK).correlationId(e.getProperty(TIMESTAMP, String.class)).build())
                .to("direct:updateStatus")
                .to("direct:remoteOtp2CleanUp")
                .routeId("otp2-remote-netex-graph-publish");

        from("direct:remoteOtp2CleanUp")
                .choice()
                .when(constant(deleteOtpRemoteWorkDir))
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(OTP_REMOTE_WORK_DIR))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Deleting OTP2 remote work directory ${header." + Exchange.FILE_PARENT + "} ...")
                .to("direct:deleteAllBlobsInFolder")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Deleted OTP2 remote work directory ${header." + Exchange.FILE_PARENT + "}")
                .end()
                .routeId("otp2-remote-graph-cleanup");

        from("direct:sendOtp2NetexGraphBuildStartedEventsInNewTransaction")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("OTP2_BUILD_GRAPH").state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
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


}
