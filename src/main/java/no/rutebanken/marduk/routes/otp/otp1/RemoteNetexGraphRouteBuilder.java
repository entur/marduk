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

package no.rutebanken.marduk.routes.otp.otp1;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.otp.RemoteGraphBuilderProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_BUILD_BASE_GRAPH;
import static no.rutebanken.marduk.Constants.OTP_GRAPH_DIR;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_CONTAINER;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TARGET_FILE_PARENT;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static org.apache.camel.builder.Builder.exceptionStackTrace;

/**
 * Build remotely a full OTP graph (containing OSM data, elevation data and NeTEx data).
 */
@Component
public class RemoteNetexGraphRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Value("${otp.graph.current.file:graphs/current}")
    private String otpGraphCurrentFile;

    @Value("${otp.graph.build.remote.work.dir.cleanup:true}")
    private boolean deleteOtpRemoteWorkDir;

    @Value("${blobstore.gcs.otpreport.container.name}")
    String otpReportContainerName;

    private static final String PROP_MESSAGES = "RutebankenPropMessages";

    private static final String PROP_STATUS = "RutebankenGraphBuildStatus";

    private static final String GRAPH_VERSION = "RutebankenGraphVersion";

    private static final String GRAPH_PATH_PROPERTY = "RutebankenGraphPath";

    @Autowired
    private KubernetesJobGraphBuilder kubernetesJobGraphBuilder;

    @Autowired
    private OtpReportBlobStoreService otpReportBlobStoreService;

    @Override
    public void configure() throws Exception {
        super.configure();

        // acknowledgment mode switched to NONE so that the ack/nack callback can be set after message aggregation.
        singletonFrom("entur-google-pubsub:OtpGraphBuildQueue?ackMode=NONE").autoStartup("{{otp.graph.build.autoStartup:true}}")
                .aggregate(constant(true)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .log(LoggingLevel.INFO, "Aggregated ${exchangeProperty.CamelAggregatedSize} graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtpGraph")
                .routeId("otp-graph-build");

        from("direct:remoteBuildOtpGraph")
                .setProperty(PROP_MESSAGES, simple("${body}"))
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtpNetexGraphBuildStartedEventsInNewTransaction")
                .setProperty(OTP_REMOTE_WORK_DIR, simple(blobStoreSubdirectory + "/work/" + UUID.randomUUID().toString() + "/${property." + TIMESTAMP + "}"))
                .setProperty(OTP_BUILD_BASE_GRAPH, constant(false))
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting graph building in remote directory ${property." + OTP_GRAPH_DIR + "}.")

                .to("direct:exportMergedNetex")

                .to("direct:remoteBuildNetexGraphAndSendStatus")
                .to("direct:remoteGraphPublishing")
                .log(LoggingLevel.INFO, getClass().getName(), "Done with OTP graph building route.")
                .routeId("otp-remote-netex-graph-build");

        from("direct:remoteBuildNetexGraphAndSendStatus")
                .log(LoggingLevel.INFO, correlation() + "Building OTP graph...")
                .doTry()
                .to("direct:remoteBuildNetexGraph")
                .setProperty(PROP_STATUS, constant(JobEvent.State.OK))
                .to("direct:sendStatusForOtpNetexJobs")
                .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, correlation() + "Graph building failed: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_GRAPH").state(JobEvent.State.FAILED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .setProperty(PROP_STATUS, constant(JobEvent.State.FAILED))
                .to("direct:sendStatusForOtpNetexJobs")
                .to("direct:remoteCleanUp")
                .stop()
                .end()
                .routeId("otp-remote-netex-graph-build-and-send-status");

        from("direct:remoteBuildNetexGraph")
                .process(new RemoteGraphBuilderProcessor(kubernetesJobGraphBuilder))
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP graph.")
                .routeId("otp-remote-netex-graph-build-otp");

        from("direct:remoteGraphPublishing")

                // copy the new graph from the OTP remote work directory to the graphs directory in GCS
                .process(e -> {
                            String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + GRAPH_OBJ;
                            String publishedGraphPath = blobStoreSubdirectory
                                                        + "/" + Constants.NETEX_GRAPH_DIR
                                                        + "/" + e.getProperty(TIMESTAMP, String.class)
                                                        + '-' + GRAPH_OBJ;
                            String publishedGraphVersion = Constants.NETEX_GRAPH_DIR + "/" + e.getProperty(TIMESTAMP, String.class) + "-report";

                            e.getIn().setHeader(FILE_HANDLE, builtOtpGraphPath);
                            e.getIn().setHeader(TARGET_FILE_HANDLE, publishedGraphPath);
                            e.setProperty(GRAPH_VERSION, publishedGraphVersion);
                        }
                )
                .to("direct:copyBlob")
                .log(LoggingLevel.INFO, "Done copying new OTP graph: ${header." + FILE_HANDLE + "}")

                .setProperty(GRAPH_PATH_PROPERTY, header(FILE_HANDLE))

                // update file containing the reference to the latest graph
                .setBody(header(TARGET_FILE_HANDLE))
                .setHeader(FILE_HANDLE, constant(otpGraphCurrentFile))
                .to("direct:uploadBlob")
                .log(LoggingLevel.INFO, "Done uploading reference to current graph: ${header." + FILE_HANDLE + "}")

                // copy the graph build report and update the reference to the current report
                .setHeader(FILE_HANDLE, exchangeProperty(GRAPH_PATH_PROPERTY))
                .to("direct:remoteCopyVersionedGraphBuildReport")
                .to("direct:remoteUpdateCurrentGraphReportVersion")
                .log(LoggingLevel.INFO, "Done uploading OTP graph build reports.")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_GRAPH").state(JobEvent.State.OK).correlationId(e.getProperty(TIMESTAMP, String.class)).build())
                .to("direct:updateStatus")

                .to("direct:remoteCleanUp")


                .routeId("otp-remote-netex-graph-publish");


        from("direct:remoteCopyVersionedGraphBuildReport")
                .process(e -> {
                    e.getIn().setHeader(Exchange.FILE_PARENT, e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/report");
                    e.getIn().setHeader(TARGET_CONTAINER, otpReportContainerName);
                    e.getIn().setHeader(TARGET_FILE_PARENT, e.getProperty(GRAPH_VERSION, String.class));
                    e.getIn().setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, "true");
                })
                .log(LoggingLevel.INFO, "Copying OTP graph build reports to gs://${header." + TARGET_CONTAINER + "}/${header." + TARGET_FILE_PARENT + "}")
                .to("direct:copyAllBlobs")
                .log(LoggingLevel.INFO, "Done copying OTP graph build reports.")
                .routeId("otp-remote-graph-build-report-versioned-upload");

        from("direct:remoteUpdateCurrentGraphReportVersion")
                .log(LoggingLevel.INFO, "Uploading OTP graph build reports current version.")
                .process(e ->
                        otpReportBlobStoreService.uploadBlob("index.html", createRedirectPage(e.getProperty(GRAPH_VERSION, String.class)), true))
                .routeId("otp-remote-graph-report-update-current");

        from("direct:remoteCleanUp")
                .choice()
                .when(constant(deleteOtpRemoteWorkDir))
                .log(LoggingLevel.INFO, getClass().getName(), "Deleting OTP remote work directory ${property." + Exchange.FILE_PARENT + "} ...")
                .setHeader(Exchange.FILE_PARENT, exchangeProperty(OTP_REMOTE_WORK_DIR))
                .to("direct:deleteAllBlobsInFolder")
                .log(LoggingLevel.INFO, getClass().getName(), "Deleting OTP remote work directory ${property." + Exchange.FILE_PARENT + "} cleanup done.")
                .end()
                .routeId("otp-remote-graph-cleanup");

        from("direct:sendOtpNetexGraphBuildStartedEventsInNewTransaction")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_GRAPH").state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .setProperty(PROP_STATUS, constant(JobEvent.State.STARTED))
                .to("direct:sendStatusForOtpNetexJobs")
                .routeId("otp-netex-graph-send-started-events");

        from("direct:sendStatusForOtpNetexJobs")
                .doTry() // <- doTry seems necessary for correct transactional handling. not sure why...
                .split().exchangeProperty(PROP_MESSAGES)
                .filter(simple("${headers[" + CHOUETTE_REFERENTIAL + "]}"))
                .process(e -> {
                    JobEvent.State state = e.getProperty(PROP_STATUS, JobEvent.State.class);
                    JobEvent.providerJobBuilder(e).timetableAction(JobEvent.TimetableAction.BUILD_GRAPH).state(state).build();
                })
                .to("direct:updateStatus")
                .routeId("otp-netex-graph-send-status-for-timetable-jobs");

    }


    private InputStream createRedirectPage(String version) {
            String url = "http://" + otpReportContainerName + "/" + version + "/index.html";
            String html = "<html>\n" +
                    "<head>\n" +
                    "    <meta http-equiv=\"refresh\" content=\"0; url=" + url + "\" />\n" +
                    "</head>\n" +
                    "</html>";

            return IOUtils.toInputStream(html, StandardCharsets.UTF_8);
    }

}
