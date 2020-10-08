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

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilderProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.BASE_GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.OTP_GRAPH_DIR;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static org.apache.camel.builder.Builder.exceptionStackTrace;

/**
 * Build remotely a base OTP graph containing OSM data and elevation data (but not transit data)
 */
@Component
public class BaseGraphRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Autowired
    private BaseGraphBuilder baseGraphBuilder;

    @Override
    public void configure() throws Exception {
        super.configure();

        // acknowledgment mode switched to NONE so that the ack/nack callback can be set after message aggregation.
        singletonFrom("entur-google-pubsub:OtpBaseGraphBuildQueue?ackMode=NONE").autoStartup("{{otp.graph.build.autoStartup:true}}")
                .aggregate(constant(true)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .log(LoggingLevel.INFO, "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP base graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtpBaseGraph")
                .routeId("otp-base-graph-build");

        from("direct:remoteBuildOtpBaseGraph")
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtpBaseGraphStartedEventsInNewTransaction")
                .setProperty(OTP_REMOTE_WORK_DIR, simple(blobStoreSubdirectory + "/work/" + UUID.randomUUID().toString() + "/${exchangeProperty." + TIMESTAMP + "}"))

                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting OTP base graph building in directory ${exchangeProperty." + OTP_GRAPH_DIR + "}.")
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
                .process(new OtpGraphBuilderProcessor(baseGraphBuilder))
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP base graph.")
                // copy new base graph in remote storage
                .process(e -> {
                            String builtBaseGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + BASE_GRAPH_OBJ;
                            String publishedBaseGraphPath = blobStoreSubdirectory + "/" + BASE_GRAPH_OBJ;

                            e.getIn().setHeader(FILE_HANDLE, builtBaseGraphPath);
                            e.getIn().setHeader(TARGET_FILE_HANDLE, publishedBaseGraphPath);
                        }
                )
                .to("direct:copyBlobInBucket")

                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied new OTP base graph, triggering full OTP graph build")
                .inOnly("entur-google-pubsub:OtpGraphBuildQueue")

                .to("direct:remoteCleanUp")

                .routeId("otp-remote-base-graph-build-build-otp");

        from("direct:sendOtpBaseGraphStartedEventsInNewTransaction")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action("BUILD_BASE").state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .routeId("otp-base-graph-build-send-started-events");

    }
}
