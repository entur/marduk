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

package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class OtpGraphBuilderControlRoute extends BaseRouteBuilder {

    private enum Mode {BASE, FULL, BOTH}

    private static final String MODE_PROP_NAME="otpMode";

    @Override
    public void configure() throws Exception {
        super.configure();
        // acknowledgment mode switched to NONE so that the ack/nack callback can be set after message aggregation.
        singletonFrom("entur-google-pubsub:OtpGraphBuildQueue?ackMode=NONE").autoStartup("{{otp.graph.build.autoStartup:true}}")
                .aggregate(constant(true)).aggregationStrategy(new GroupedMessageAggregationStrategy()).completionSize(100).completionTimeout(1000)
                .process(exchange -> addOnCompletionForAggregatedExchange(exchange))
                .log(LoggingLevel.INFO, "Aggregated ${exchangeProperty.CamelAggregatedSize} graph building requests (aggregation completion triggered by ${exchangeProperty.CamelAggregatedCompletedBy}).")
                .doTry() // <- doTry seems necessary for correct transactional handling. not sure why...
                .process(e -> e.setProperty(MODE_PROP_NAME, getBuildMode(e)))
                .choice()
                .when(exchangeProperty(MODE_PROP_NAME).isEqualTo(Mode.FULL))
                    // Build full graph (step2)
                    .to("direct:buildOtpGraph")
                .otherwise()
                .doTry()
                    // Build base graph (step1)
                    .to("direct:buildOtpBaseGraph")
                    .doFinally()
                        .choice().when(exchangeProperty(MODE_PROP_NAME).isEqualTo(Mode.BOTH))
                            // Trigger build of full graph (step2). This may already have been done if base graph build was successful, any duplicates will be discarded.
                            .inOnly("entur-google-pubsub:OtpGraphBuildQueue")
                        .end()
                    .end()
                .end()
                .routeId("otp-graph-build-jms");
    }


    private Mode getBuildMode(Exchange e) {
        Collection<Message> messages = e.getIn().getBody(List.class);
        if (CollectionUtils.isEmpty(messages)) {
            return Mode.FULL;
        }

        if (messages.stream().allMatch(m -> isBaseGraphBuild(m))) {
            return Mode.BASE;
        } else if (messages.stream().noneMatch(m -> isBaseGraphBuild(m))) {
            return Mode.FULL;
        }

        return Mode.BOTH;
    }

    private boolean isBaseGraphBuild(Message message) {
        try {
            return message.getHeader(Constants.OTP_BASE_GRAPH_BUILD) != null;
        } catch (Exception e) {
            return false;
        }
    }

}
