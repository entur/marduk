package no.rutebanken.marduk.routes;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

/**
 * An @see{AggregationStrategy} that returns the exchange with the data file. The md5 exchange is only used as a correlation id and hence will be dropped.
 */
public class Md5AggregationStrategy implements AggregationStrategy {

    private static final String CAMEL_FILE_NAME_ONLY = "CamelFileNameOnly";

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        if (getInFileName(oldExchange).endsWith(".md5"))
            return newExchange;
        else if (getInFileName(newExchange).endsWith(".md5"))
            return oldExchange;
        else
            throw new RuntimeException("Could not aggregate: '" + getInFileName(oldExchange) + "' and '" + getInFileName(newExchange) + "'");
    }

    private String getInFileName(Exchange exchange) {
        return exchange.getIn().getHeader(CAMEL_FILE_NAME_ONLY, String.class);
    }

}