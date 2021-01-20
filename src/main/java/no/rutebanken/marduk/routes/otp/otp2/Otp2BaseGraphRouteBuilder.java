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
import no.rutebanken.marduk.routes.MardukGroupedMessageAggregationStrategy;
import no.rutebanken.marduk.routes.otp.OtpGraphBuilderProcessor;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.PredicateBuilder;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static no.rutebanken.marduk.Constants.OTP2_BASE_GRAPH_OBJ_PREFIX;
import static no.rutebanken.marduk.Constants.OTP_BUILD_CANDIDATE;
import static no.rutebanken.marduk.Constants.OTP_REMOTE_WORK_DIR;
import static no.rutebanken.marduk.Constants.TIMESTAMP;

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
        singletonFrom("google-pubsub:{{spring.cloud.gcp.pubsub.project-id}}:Otp2BaseGraphBuildQueue?ackMode=NONE&synchronousPull=true").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .aggregate(simple("true", Boolean.class)).aggregationStrategy(new MardukGroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 base graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:buildOtp2BaseGraph")
                .routeId("pubsub-otp2-base-graph-build");

        singletonFrom("google-pubsub:{{spring.cloud.gcp.pubsub.project-id}}:Otp2BaseGraphCandidateBuildQueue?ackMode=NONE&synchronousPull=true").autoStartup("{{otp2.graph.build.autoStartup:true}}")
                .aggregate(simple("true", Boolean.class)).aggregationStrategy(new MardukGroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(this::addOnCompletionForAggregatedExchange)
                .process(this::setNewCorrelationId)
                .setProperty(OTP_BUILD_CANDIDATE, simple("true", Boolean.class))
                .log(LoggingLevel.INFO, correlation() + "Aggregated ${exchangeProperty.CamelAggregatedSize} OTP2 base graph candidate building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .to("direct:buildOtp2BaseGraph")
                .routeId("pubsub-otp2-base-graph-candidate-build");

        from("direct:buildOtp2BaseGraph")
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmssSSS}"))
                .to("direct:sendOtp2BaseGraphStartedEventsInNewTransaction")
                .setProperty(OTP_REMOTE_WORK_DIR, simple(blobStoreSubdirectory + "/work/" + UUID.randomUUID().toString() + "/${exchangeProperty." + TIMESTAMP + "}"))

                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Starting OTP2 base graph building in directory ${exchangeProperty." + OTP_REMOTE_WORK_DIR + "}.")
                .to("direct:remoteBuildOtp2BaseGraphAndSendStatus")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Done with OTP2 base graph building route.")
                .routeId("otp2-base-graph-build");

        from("direct:remoteBuildOtp2BaseGraphAndSendStatus")
                .log(LoggingLevel.INFO, correlation() + "Preparing OTP2 graph with all non-transit data...")
                .doTry()
                .to("direct:remoteBuildAndCopyOtp2BaseGraph")
                .doCatch(Exception.class)
                .log(LoggingLevel.ERROR, correlation() + "OTP2 Base Graph building failed: ${exception.message} stacktrace: ${exception.stacktrace}")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_BASE).state(JobEvent.State.FAILED).correlationId(e.getProperty(TIMESTAMP, String.class)).build())
                .to("direct:updateStatus")
                .end()
                .routeId("otp2-remote-base-graph-build-and-send-status");

        from("direct:remoteBuildAndCopyOtp2BaseGraph")
                .to("direct:remoteBuildOtp2BaseGraph")
                // copy new base graph in remote storage
                .setHeader(Constants.FILE_PREFIX, simple("${exchangeProperty." + OTP_REMOTE_WORK_DIR + "}/" + OTP2_BASE_GRAPH_OBJ_PREFIX))
                .to("direct:findBlob")
                .log(LoggingLevel.INFO, correlation() + "Found OTP2 base graph named ${body.fileNameOnly} matching file prefix ${header." + Constants.FILE_PREFIX + "}")
                .process(new Otp2BaseGraphPublishingProcessor(blobStoreSubdirectory))
                .to("direct:copyBlobInBucket")
                .to(logDebugShowAll())
                .choice()
                .when(PredicateBuilder.not(exchangeProperty(OTP_BUILD_CANDIDATE)))
                .log(LoggingLevel.INFO, correlation() + "Copied new OTP2 base graph, triggering full OTP2 graph build")
                .setBody(constant(""))
                .to(ExchangePattern.InOnly, "google-pubsub:{{spring.cloud.gcp.pubsub.project-id}}:OtpGraphBuildQueue")
                .otherwise()
                .log(LoggingLevel.INFO, correlation() + "Copied new OTP2 candidate base graph")
                .end()
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_BASE).state(JobEvent.State.OK).correlationId(e.getProperty(TIMESTAMP, String.class)).build())
                .to("direct:updateStatus")
                .to("direct:remoteOtp2CleanUp")
                .routeId("otp2-remote-base-graph-build-copy");

        from("direct:remoteBuildOtp2BaseGraph")
                .process(new OtpGraphBuilderProcessor(otp2BaseGraphBuilder))
                .log(LoggingLevel.INFO, correlation() + "Done building new OTP2 base graph.")
                .routeId("otp2-remote-base-graph-build");

        from("direct:sendOtp2BaseGraphStartedEventsInNewTransaction")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.GRAPH).action(JobEvent.TimetableAction.OTP2_BUILD_BASE).state(JobEvent.State.STARTED).correlationId(e.getProperty(TIMESTAMP, String.class)).build())
                .to("direct:updateStatus")
                .routeId("otp2-base-graph-build-send-started-events");

    }
}
