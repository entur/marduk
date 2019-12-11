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

package no.rutebanken.marduk.routes.otp.remote;

import no.rutebanken.marduk.Utils;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.services.OtpReportBlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
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
@Profile({"otp-invm-graph-builder", "otp-kubernetes-job-graph-builder"})
public class RemoteNetexGraphRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    // File name for import. OTP requires specific file name pattern to parse file as netex (ends with netex_no.zip)
    @Value("${otp.netex.import.file.name:netex_no.zip}")
    private String otpGraphImportFileName;

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
    private RemoteGraphBuilderProcessor remoteGraphBuilderProcessor;

    @Autowired
    private OtpReportBlobStoreService otpReportBlobStoreService;

    @Override
    public void configure() throws Exception {
        super.configure();

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
                .end()
                .routeId("otp-remote-netex-graph-build-and-send-status");

        from("direct:remoteBuildNetexGraph")
                .process(remoteGraphBuilderProcessor)
                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP graph.")
                .routeId("otp-remote-netex-graph-build-otp");

        from("direct:remoteGraphPublishing")

                // copy the new graph from the OTP remote work directory to the graphs directory in GCS
                .process(e -> {
                            String builtOtpGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + GRAPH_OBJ;
                            String publishedGraphPath = blobStoreSubdirectory
                                                        + "/" + Utils.getOtpVersion()
                                                        + "/" + e.getProperty(TIMESTAMP, String.class)
                                                        + '-' + GRAPH_OBJ;
                            String publishedGraphVersion = Utils.getOtpVersion() + "/" + e.getProperty(TIMESTAMP, String.class) + "-report";

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

                .to("direct:notify")

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

    }


    private InputStream createRedirectPage(String version) {
        try {
            String url = "http://" + otpReportContainerName + "/" + version + "/index.html";
            String html = "<html>\n" +
                    "<head>\n" +
                    "    <meta http-equiv=\"refresh\" content=\"0; url=" + url + "\" />\n" +
                    "</head>\n" +
                    "</html>";

            return IOUtils.toInputStream(html, "UTF-8");
        } catch (IOException ioE) {
            throw new MardukException("Failed to create input stream for redirect page: " + ioE.getMessage(), ioE);
        }
    }

}
