package no.rutebanken.marduk.routes.aggregation;

import org.apache.camel.processor.aggregate.DefaultAggregateController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;

/**
 * Suspend the aggregation over a given time period.
 */
public class DisabledOnPeriodAggregateController extends DefaultAggregateController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisabledOnPeriodAggregateController.class);

    private final LocalTime startDisabledPeriod;
    private final LocalTime endDisabledPeriod;

    public DisabledOnPeriodAggregateController(LocalTime startDisabledPeriod, LocalTime endDisabledPeriod) {
        this.startDisabledPeriod = startDisabledPeriod;
        this.endDisabledPeriod = endDisabledPeriod;
    }

    private boolean isAggregationActive() {
        LocalTime now = LocalTime.now();
        return now.isAfter(endDisabledPeriod) || now.isBefore(startDisabledPeriod);
    }

    @Override
    public int forceCompletionOfAllGroups() {
        if (isAggregationActive()) {
            LOGGER.debug("Aggregation triggered");
            return super.forceCompletionOfAllGroups();
        }
        LOGGER.debug("Aggregation disabled in time period {}-{}. Aggregation postponed", startDisabledPeriod, endDisabledPeriod);
        return 0;
    }
}
