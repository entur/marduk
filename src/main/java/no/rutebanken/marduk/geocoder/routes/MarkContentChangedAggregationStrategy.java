package no.rutebanken.marduk.geocoder.routes;


import org.apache.camel.Exchange;
import org.apache.camel.processor.aggregate.AggregationStrategy;

import static no.rutebanken.marduk.Constants.CONTENT_CHANGED;

/**
 * Aggregate CONTENT_CHANGED headers, marking aggregation as changed if at least on exchange is marked such.
 */
public class MarkContentChangedAggregationStrategy implements AggregationStrategy {

	@Override
	public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
		if (newExchange.getIn().getHeader(CONTENT_CHANGED, false, Boolean.class)) {
			return newExchange;
		}
		return oldExchange;
	}
}
