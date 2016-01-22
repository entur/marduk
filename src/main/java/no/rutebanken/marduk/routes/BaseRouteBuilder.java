package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.management.ProviderRepository;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.ConnectException;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    @Autowired
    protected ProviderRepository providerRepository;

    @Override
    public void configure() throws Exception {
        errorHandler(deadLetterChannel("activemq:queue:DeadLetterQueue")
            .maximumRedeliveries(3)
            .redeliveryDelay(3000));

        onException(ConnectException.class)
            .maximumRedeliveries(10)
                .redeliveryDelay(10000)
                .useExponentialBackOff();

    }

    protected ProviderRepository getProviderRepository(){
        return providerRepository;
    }

}
