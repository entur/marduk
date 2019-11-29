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
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.UUID;

import static no.rutebanken.marduk.Constants.BASE_GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_BUILD_BASE_GRAPH;
import static no.rutebanken.marduk.Constants.OTP_GRAPH_DIR;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static org.apache.camel.builder.Builder.exceptionStackTrace;

/**
 * Prepare half baked otp base graph object, containing all data except transit data (osm, height)
 */
@Component
@Profile({"otp-invm-graph-builder", "otp-kubernetes-job-graph-builder"})
public class RemoteBaseGraphRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.base.graph.build.directory:files/otpgraph/base}")
    private String otpBaseGraphBuildDirectory;

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Autowired
    private RemoteGraphBuilderProcessor remoteGraphBuilderProcessor;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:remoteBuildOtpBaseGraph")
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtpBaseGraphStartedEventsInNewTransaction")
                .process(e -> e.setProperty(OTP_REMOTE_WORK_DIR, otpBaseGraphBuildDirectory + "/" + UUID.randomUUID().toString() + "/" + e.getProperty(TIMESTAMP)))
                .setProperty(OTP_BUILD_BASE_GRAPH, constant(true))

                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting OTP base graph building in directory ${property." + OTP_GRAPH_DIR + "}.")
                .to("direct:remoteBuildBaseGraphAndSendStatus")
                .log(LoggingLevel.INFO, getClass().getName(), "Done with OTP base graph building route.")
                .routeId("otp-remote-base-graph-build");

        from("direct:remoteBuildBaseGraphAndSendStatus")
                .log(LoggingLevel.INFO, correlation() + "Preparing OTP graph with all non-transit data...")
                .doTry()
                .to("direct:remoteBuildBaseGraph")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, correlation() + "Graph building failed: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_BASE").state(JobEvent.State.FAILED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .end()
                .routeId("otp-remote-base-graph-build-and-send-status");

        from("direct:remoteBuildBaseGraph")
                .process(remoteGraphBuilderProcessor)
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP base graph.")
                // copy new base graph in remote storage
                .process(e -> {
                            String builtBaseGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + BASE_GRAPH_OBJ;
                            String publishedBaseGraphPath = blobStoreSubdirectory + "/" + BASE_GRAPH_OBJ;

                            e.getIn().setHeader(FILE_HANDLE, builtBaseGraphPath);
                            e.getIn().setHeader(TARGET_FILE_HANDLE, publishedBaseGraphPath);
                        }
                )
                .to("direct:copyBlob")

                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
                .log(LoggingLevel.INFO, correlation() + "Copied new OTP base graph, triggering full OTP graph build")
                .inOnly("entur-google-pubsub:OtpGraphBuildQueue")
                .routeId("otp-remote-base-graph-build-build-otp");

    }
}
