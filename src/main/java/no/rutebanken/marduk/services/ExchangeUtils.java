package no.rutebanken.marduk.services;

import org.apache.camel.Exchange;

public class ExchangeUtils {

    public static void addHeadersAndAttachments(Exchange exchange) {
        // copy headers from IN to OUT to propagate them
        exchange.getIn().setHeaders(exchange.getIn().getHeaders());
        // copy attachements from IN to OUT to propagate them
        exchange.getIn().setAttachments(exchange.getIn().getAttachments());
    }
}
