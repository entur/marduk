package no.rutebanken.marduk.routes;

import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;

public class MardukGroupedMessageAggregationStrategy extends GroupedMessageAggregationStrategy {

    @Override
    public boolean isStoreAsBodyOnCompletion() {
        return true;
    }
}
