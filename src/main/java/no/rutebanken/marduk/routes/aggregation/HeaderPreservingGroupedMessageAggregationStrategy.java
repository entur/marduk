package no.rutebanken.marduk.routes.aggregation;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.GroupedMessageAggregationStrategy;

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
        copyHeadersForPreservation(newExchange, aggregatedExchange);
        return aggregatedExchange;
    }

    private void copyHeadersForPreservation(Exchange newExchange, Exchange aggregatedExchange) {
        Map<String, Object> headers = newExchange.getIn().getHeaders();
        for (String headerName : headersToPreserve) {
            Object headerValue = headers.get(headerName);
            if (headerValue != null) {
                aggregatedExchange.getIn().setHeader(headerName, headerValue);
            }
        }
    }
}
