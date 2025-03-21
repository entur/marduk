package no.rutebanken.marduk.routes.aggregation;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeaderPreservingGroupedMessageAggregationStrategy extends GroupedMessageAggregationStrategy {
    private List<String> headersToPreserve;

    public HeaderPreservingGroupedMessageAggregationStrategy(List<String> headersToPreserve) {
        this.headersToPreserve = headersToPreserve;
    }

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        Exchange aggregatedExchange = super.aggregate(oldExchange, newExchange);
        Map<String, Object> aggregatedHeaders = copyHeadersForPreservation(newExchange);
        aggregatedExchange.getIn().setHeaders(aggregatedHeaders);
        return aggregatedExchange;
    }

    protected Map<String, Object> copyHeadersForPreservation(Exchange newExchange) {
        Map<String, Object> headers = newExchange.getIn().getHeaders();
        Map<String, Object> preservedHeaders = new HashMap<>();
        for (String headerName : headersToPreserve) {
            Object headerValue = headers.get(headerName);
            if (headerValue != null) {
                preservedHeaders.put(headerName, headerValue);
            }
        }
        return preservedHeaders;
    }
}
