package no.rutebanken.marduk.routes;

import com.google.common.base.Strings;
import no.rutebanken.marduk.management.ProviderRepository;
import no.rutebanken.marduk.routes.status.Status;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.ConnectException;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    @Autowired
    protected ProviderRepository providerRepository;

    @Override
    public void configure() throws Exception {
//        errorHandler(deadLetterChannel("activemq:queue:DeadLetterQueue")
//          .maximumRedeliveries(3)
//            .redeliveryDelay(3000));

        onException(ConnectException.class)
            .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

    }

    protected void addStatus(Exchange exchange, Status.Action action, Status.State state) {
        String fileName = exchange.getIn().getHeader(Exchange.FILE_NAME, String.class);
        if (Strings.isNullOrEmpty(fileName)) {
            throw new IllegalArgumentException("No file name");
        }

        String providerIdString = exchange.getIn().getHeader(PROVIDER_ID, String.class);
        if (Strings.isNullOrEmpty(providerIdString)) {
            throw new IllegalArgumentException("No provider id");
        }
        Long providerId = Long.valueOf(providerIdString);

        String correlationId = exchange.getIn().getHeader(CORRELATION_ID, String.class);
        if (Strings.isNullOrEmpty(correlationId)) {
            throw new IllegalArgumentException("No correlation id");
        }

        exchange.getOut().setBody(new Status(fileName, providerId, action, state, correlationId).toString());
        exchange.getOut().setHeaders(exchange.getIn().getHeaders());
    }

}
