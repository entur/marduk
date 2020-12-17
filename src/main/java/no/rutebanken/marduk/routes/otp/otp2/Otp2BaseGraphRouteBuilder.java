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

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilderProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TARGET_FILE_HANDLE;
import static no.rutebanken.marduk.Constants.TIMESTAMP;
import static org.apache.camel.builder.Builder.exceptionStackTrace;

/**
 * Build remotely a base OTP2 graph containing OSM data and elevation data (but not transit data)
 */
@Component
public class Otp2BaseGraphRouteBuilder extends BaseRouteBuilder {

    @Value("${otp.graph.blobstore.subdirectory:graphs}")
    private String blobStoreSubdirectory;

    @Autowired
    private Otp2BaseGraphBuilder otp2BaseGraphBuilder;

    @Override
    public void configure() throws Exception {
        super.configure();

        // acknowledgment mode switched to NONE so that the ack/nack callback can be set after message aggregation.
        singletonFrom("entur-google-pubsub:Otp2BaseGraphBuildQueue?ackMode=NONE").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .aggregate(simple("true", Boolean.class)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 base graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:remoteBuildOtp2BaseGraph")
                .routeId("otp2-base-graph-build");

        from("direct:remoteBuildOtp2BaseGraph")
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtp2BaseGraphStartedEventsInNewTransaction")
                .setProperty(OTP_REMOTE_WORK_DIR, simple(blobStoreSubdirectory + "/work/" + UUID.randomUUID().toString() + "/${exchangeProperty." + TIMESTAMP + "}"))

                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting OTP2 base graph building in directory ${exchangeProperty." + OTP_REMOTE_WORK_DIR + "}.")
                .to("direct:remoteBuildOtp2BaseGraphAndSendStatus")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Done with OTP2 base graph building route.")
                .routeId("otp2-remote-base-graph-build");

        from("direct:remoteBuildOtp2BaseGraphAndSendStatus")
                .log(LoggingLevel.INFO, correlation() + "Preparing OTP2 graph with all non-transit data...")
                .doTry()
                .to("direct:remoteOtp2BuildBaseGraph")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, correlation() + "Graph building failed: " + exceptionMessage() + " stacktrace: " + exceptionStackTrace())
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_BASE).state(JobEvent.State.FAILED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .end()
                .routeId("otp2-remote-base-graph-build-and-send-status");

        from("direct:remoteOtp2BuildBaseGraph")
                .process(new OtpGraphBuilderProcessor(otp2BaseGraphBuilder))
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP2 base graph.")
                // copy new base graph in remote storage
                .process(e -> {
                            String builtBaseGraphPath = e.getProperty(OTP_REMOTE_WORK_DIR, String.class) + "/" + OTP2_BASE_GRAPH_OBJ;
                            String publishedBaseGraphPath = blobStoreSubdirectory + "/" + OTP2_BASE_GRAPH_OBJ;

                            e.getIn().setHeader(FILE_HANDLE, builtBaseGraphPath);
                            e.getIn().setHeader(TARGET_FILE_HANDLE, publishedBaseGraphPath);
                        }
                )
                .to("direct:copyBlobInBucket")

                .to(logDebugShowAll())
                .log(LoggingLevel.INFO, correlation() + "Copied new OTP2 base graph, triggering full OTP2 graph build")
                .to(ExchangePattern.InOnly, "entur-google-pubsub:OtpGraphBuildQueue")

                .to("direct:remoteCleanUp")

                .routeId("otp2-remote-base-graph-build-build-otp");

        from("direct:sendOtp2BaseGraphStartedEventsInNewTransaction")

                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_BASE).state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build()).to("direct:updateStatus")
                .routeId("otp2-base-graph-build-send-started-events");

    }
}
