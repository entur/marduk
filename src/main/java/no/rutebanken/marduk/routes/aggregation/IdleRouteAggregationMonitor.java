package no.rutebanken.marduk.routes.aggregation;

import org.apache.camel.CamelContext;
import org.apache.camel.processor.aggregate.AggregateController;
import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Monitor a list of Camel routes and triggers associated aggregators only if the route is idle (i.e. the route has no inflight exchange).
 * This can be used to implement back pressure from a downstream route to an aggregator: the aggregator keeps accumulating requests while a downstream route is busy processing an earlier request.
 */
public class IdleRouteAggregationMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdleRouteAggregationMonitor.class);

    private final CamelContext camelContext;
    private final Map<String, List<AggregateController>> aggregateControllerMap;

    public IdleRouteAggregationMonitor(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.aggregateControllerMap = new HashMap<>();
    }

    /**
     * Return an aggregate controller for a given route.
     *
     * @param routeId the Camel route id.
     * @return an aggregate controller that will trigger aggregation when the route is idle.
     */
    public AggregateController getAggregateControllerForRoute(String routeId) {
        List<AggregateController> aggregateControllers = this.aggregateControllerMap.computeIfAbsent(routeId, k -> new ArrayList<>());
        AggregateController aggregateController = new DefaultAggregateController();
        aggregateControllers.add(aggregateController);
        return aggregateController;
    }

    /**
     * Scheduled method that checks each registered route and trigger aggregation if the route has no inflight exchange.
     */
    public void checkAggregation() {
        aggregateControllerMap.forEach((routeId, aggregateControllers) -> {
            int nbInflightExchanges = camelContext.getInflightRepository().size(routeId);
            if (nbInflightExchanges == 0) {
                LOGGER.debug("Route {} has no inflight exchange, triggering aggregation", routeId);
                int nbGroups = aggregateControllers.stream().map(AggregateController::forceCompletionOfAllGroups).mapToInt(Integer::intValue).sum();
                LOGGER.debug("{} groups aggregated for route {}", nbGroups, routeId);
            } else {
                LOGGER.debug("Route {} has {} inflight exchanges, postponing aggregation", routeId, nbInflightExchanges);
            }
        });

    }

}
