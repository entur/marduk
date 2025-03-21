package no.rutebanken.marduk.routes.aggregation;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeaderPreservingGroupedMessageAggregationStrategyTest {

    HeaderPreservingGroupedMessageAggregationStrategy strategy;
    List<String> headersToPreserve = List.of("header1", "header2", "header3");

    Exchange exchange;
    Message message;

    @BeforeEach
    void setUp() {
        this.exchange = mock(Exchange.class);
        this.message = mock(Message.class);

        when(message.getHeaders()).thenReturn(Map.ofEntries(new AbstractMap.SimpleEntry<>("header1", "value1"), new AbstractMap.SimpleEntry<>("header4", "value4")));
        when(exchange.getIn()).thenReturn(message);

        this.strategy = new HeaderPreservingGroupedMessageAggregationStrategy(headersToPreserve);
    }

    @Test
    void aggregatePreservesSpecifiedHeaders() {
        Map<String, Object> resultingHeaders = strategy.copyHeadersForPreservation(this.exchange);
        Assertions.assertEquals("value1", resultingHeaders.get("header1"));
        Assertions.assertFalse(resultingHeaders.containsKey("header4"));
    }
}