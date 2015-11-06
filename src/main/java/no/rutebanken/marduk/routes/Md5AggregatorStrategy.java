package no.rutebanken.marduk.routes;

import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

public class Md5AggregatorStrategy implements AggregationStrategy {

    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        if (oldExchange == null) {
            return newExchange;
        }

        //We return the map data exchange, md5 is used as a correlation id, so it will be matched by camel
        if (firstOneIsMd5File(oldExchange, newExchange)) {
            return newExchange;
        } else if (firstOneIsMd5File(newExchange, oldExchange)) {
            return oldExchange;
        }

        throw new RuntimeException("Could not aggregate: '" + oldExchange + "' and '" + newExchange + "'");
    }

    private String getInFileName(Exchange exchange) {
        return exchange.getIn().getHeader("CamelFileNameOnly", String.class);
    }

    private boolean firstOneIsMd5File(Exchange oldExchange, Exchange newExchange) {
        String oldFileName = getInFileName(oldExchange);
        String newFileName = getInFileName(newExchange);
        return oldFileName.endsWith(".md5") && !newFileName.endsWith(".md5");
    }

}