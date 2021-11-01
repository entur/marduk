package no.rutebanken.marduk.config;

import org.apache.camel.CamelContext;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Monitor a list of Camel routes and triggers associated aggregators only if the route is idle (i.e. the route has no inflight exchange).
 * This can be used to implement back pressure from a downstream route to an aggregator: the aggregator keeps accumulating requests while a downstream route is busy processing an earlier request.
 */
public class IdleRouteAggregationMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdleRouteAggregationMonitor.class);

    private final CamelContext camelContext;
    private final Map<String, AggregateController> aggregateControllers;

    public IdleRouteAggregationMonitor(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.aggregateControllers = new HashMap<>();
    }

    /**
     * Return an aggregate controller for a given route.
     * @param routeId the Camel route id.
     * @return an aggregate controller that will trigger aggregation when the route is idle.
     */
    public AggregateController getAggregateControllerForRoute(String routeId) {
        return aggregateControllers.computeIfAbsent(routeId, k -> new DefaultAggregateController());
    }

    /**
     * Scheduled method that checks each registered route and trigger aggregation if the route has no inflight exchange.
     */
    public void checkAggregation() {
        aggregateControllers.forEach((routeId, aggregateController) -> {
            int nbInflightExchanges = camelContext.getInflightRepository().size(routeId);
            if (nbInflightExchanges == 0) {
                LOGGER.debug("Route {} has no inflight exchange, triggering aggregation", routeId);
                int nbGroups = aggregateController.forceCompletionOfAllGroups();
                LOGGER.debug("{} groups aggregated for route {}", nbGroups, routeId);
            } else {
                LOGGER.debug("Route {} has {} inflight exchanges, postponing aggregation", routeId, nbInflightExchanges);
            }
        });

    }

}
