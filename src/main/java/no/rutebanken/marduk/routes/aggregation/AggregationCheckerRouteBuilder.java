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

package no.rutebanken.marduk.routes.aggregation;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.AbstractChouetteRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * Periodically check if a list of monitored routes are idle and trigger aggregation for Camel aggregators associated to these routes.
 */
@Component
public class AggregationCheckerRouteBuilder extends BaseRouteBuilder {

    @Value("${marduk.aggregation.checker.quartz.trigger:trigger.repeatInterval=5000&trigger.repeatCount=-1&startDelayedSeconds=10&stateful=true}")
    private String quartzTrigger;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom(("quartz://marduk/checkAggregation?" + quartzTrigger))
                .process(this::setNewCorrelationId)
                .log(LoggingLevel.DEBUG, correlation() + "Quartz triggers check aggregation.")
                .bean("idleRouteAggregationMonitor", "checkAggregation")
                .log(LoggingLevel.DEBUG, correlation() + "Quartz triggers check aggregation done.")
                .routeId("aggregation-checker-quartz");
    }
}
