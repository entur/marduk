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

package no.rutebanken.marduk.config;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import no.rutebanken.marduk.routes.aggregation.IdleRouteAggregationMonitor;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubHeaderFilterStrategy;
import org.apache.camel.spi.ComponentCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
public class CamelConfig {

    /**
     * Configure the Camel thread pool for bulk operations on providers.
     *
     */
    @Bean
    public ExecutorService allProvidersExecutorService(CamelContext camelContext) throws Exception {
        ThreadPoolBuilder poolBuilder = new ThreadPoolBuilder(camelContext);
        return poolBuilder
                .poolSize(20)
                .maxPoolSize(20)
                .maxQueueSize(1000)
                .build("allProvidersExecutorService");
    }

    /**
     * Configure the Camel thread pool for GTFS export routes.
     * The pool size is set to 1 in order to limit resource usage and prioritize other routes.
     * This means that at most one route among GTFS extended and GTFS basic export routes
     * can be running at any given time.
     *
     */
    @Bean
    public ExecutorService gtfsExportExecutorService(CamelContext camelContext) throws Exception {
        ThreadPoolBuilder poolBuilder = new ThreadPoolBuilder(camelContext);
        return poolBuilder
                .poolSize(1)
                .maxPoolSize(1)
                .maxQueueSize(100)
                .build("gtfsExportExecutorService");
    }

    /**
     * Register Java Time Module for JSON serialization/deserialization of Java Time objects.
     */
    @Bean("jacksonJavaTimeModule")
    JavaTimeModule jacksonJavaTimeModule() {
        return new JavaTimeModule();
    }


    /**
     * Configure an idle route monitor that triggers Camel aggregators when a monitored route is idle.
     */
    @Bean("idleRouteAggregationMonitor")
    IdleRouteAggregationMonitor idleRouteAggregationMonitor(CamelContext camelContext) {
        return new IdleRouteAggregationMonitor(camelContext);
    }

    /**
     * Restricts outbound Google PubSub message attributes to the explicit attribute map built by
     * {@link no.rutebanken.marduk.routes.BaseRouteBuilder#configureOutboundPubSubInterceptor()}.
     *
     * <p>As of Camel 4.18 (the previous 4.4.5 baseline did not do this) the PubSub producer, in
     * addition to publishing the curated {@code ATTRIBUTES} map, copies every non-filtered Camel
     * header into the published message attributes, which would leak internal headers such as
     * {@code breadcrumbId}. This strategy suppresses that second pass by filtering out all Camel
     * headers when producing, so only the curated map is published.
     *
     * <p>Note the curated {@code ATTRIBUTES} map itself is published verbatim and does NOT pass
     * through this strategy. The exclusions that keep {@code Authorization}/{@code breadcrumbId} out
     * of that map live in {@code configureOutboundPubSubInterceptor}, not here. Inbound filtering
     * keeps the component default behaviour.
     */
    @Bean
    ComponentCustomizer googlePubsubHeaderFilterCustomizer() {
        return ComponentCustomizer
                .builder(GooglePubsubComponent.class)
                .build(component -> component.setHeaderFilterStrategy(new OutboundFilteringHeaderFilterStrategy()));
    }

    static class OutboundFilteringHeaderFilterStrategy extends GooglePubsubHeaderFilterStrategy {

        @Override
        public boolean applyFilterToCamelHeaders(String headerName, Object headerValue, Exchange exchange) {
            return true;
        }
    }

}
