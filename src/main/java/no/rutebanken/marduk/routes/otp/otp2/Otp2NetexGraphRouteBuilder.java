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
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilderProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.OTP2_GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_GRAPH_DIR;
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

    @Value("${otp2.graph.current.file:graphs/current-otp2}")
    private String otpGraphCurrentFile;

    @Value("${blobstore.gcs.graphs.container.name:otp-graphs}")
    private String otpGraphsBucketName;

    @Value("${otp.graph.build.remote.work.dir.cleanup:true}")
    private boolean deleteOtpRemoteWorkDir;

    private static final String PROP_MESSAGES = "RutebankenPropMessages";

    private static final String PROP_STATUS = "RutebankenGraphBuildStatus";

    private static final String GRAPH_VERSION = "RutebankenGraphVersion";

    private static final String GRAPH_PATH_PROPERTY = "RutebankenGraphPath";

    @Autowired
    private Otp2NetexGraphBuilder otp2NetexGraphBuilder;

    @Override
    public void configure() throws Exception {
        super.configure();

        // acknowledgment mode switched to NONE so that the ack/nack callback can be set after message aggregation.
        singletonFrom("entur-google-pubsub:Otp2GraphBuildQueue?ackMode=NONE").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .aggregate(constant(true)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .log(LoggingLevel.INFO, "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtp2Graph")
                .routeId("otp2-graph-build");

        from("direct:remoteBuildOtp2Graph")
                .setProperty(PROP_MESSAGES, simple("${body}"))
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtp2NetexGraphBuildStartedEventsInNewTransaction")
                .setProperty(OTP_REMOTE_WORK_DIR, simple(blobStoreSubdirectory + "/work/" + UUID.randomUUID().toString() + "/${property." + TIMESTAMP + "}"))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting OTP2 graph building in remote directory ${property." + OTP_GRAPH_DIR + "}.")

                .to("direct:otp2ExportMergedNetex")

                .to("direct:remoteBuildOtp2NetexGraphAndSendStatus")
                .to("direct:remoteOtp2GraphPublishing")
                .log(LoggingLevel.INFO, getClass().getName(), "Done with OTP2 graph building route.")
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

                // copy the new graph from the OTP remote work directory to the graphs directory in GCS
                .process(e -> {
                            String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + OTP2_GRAPH_OBJ;
                            String publishedGraphPath = Constants.OTP2_NETEX_GRAPH_DIR
                                                        + "/" + e.getProperty(TIMESTAMP, String.class)
                                                        + '-' + OTP2_GRAPH_OBJ;
                            String publishedGraphVersion = Constants.OTP2_NETEX_GRAPH_DIR + "/" + e.getProperty(TIMESTAMP, String.class) + "-report";

                            e.getIn().setHeader(FILE_HANDLE, builtOtpGraphPath);
                            e.getIn().setHeader(TARGET_FILE_HANDLE, publishedGraphPath);
                            e.getIn().setHeader(TARGET_CONTAINER, otpGraphsBucketName);
                            e.getIn().setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false));
                            e.setProperty(GRAPH_VERSION, publishedGraphVersion);
                        }
                )
                .to("direct:copyBlobToAnotherBucket")
                .log(LoggingLevel.INFO, "Done copying new OTP2 graph: ${header." + FILE_HANDLE + "}")

                .setProperty(GRAPH_PATH_PROPERTY, header(FILE_HANDLE))

                // update file containing the reference to the latest graph
                .setBody(header(TARGET_FILE_HANDLE))
                .setHeader(FILE_HANDLE, constant(otpGraphCurrentFile))
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(false))
                .to("direct:uploadOtpGraphsBlob")
                .log(LoggingLevel.INFO, "Done uploading reference to current OTP2graph: ${header." + FILE_HANDLE + "}")

                .log(LoggingLevel.INFO, "Done uploading OTP2 graph build reports.")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("OTP2_BUILD_GRAPH").state(JobEvent.State.OK).correlationId(e.getProperty(TIMESTAMP, String.class)).build())
                .to("direct:updateStatus")

                .to("direct:remoteOtp2CleanUp")


                .routeId("otp2-remote-netex-graph-publish");

        from("direct:remoteOtp2CleanUp")
                .choice()
                .when(constant(deleteOtpRemoteWorkDir))
                .log(LoggingLevel.INFO, getClass().getName(), "Deleting OTP2 remote work directory ${property." + Exchange.FILE_PARENT + "} ...")
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(OTP_REMOTE_WORK_DIR))
                .to("direct:deleteAllBlobsInFolder")
                .log(LoggingLevel.INFO, getClass().getName(), "Deleting OTP2 remote work directory ${property." + Exchange.FILE_PARENT + "} cleanup done.")
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
