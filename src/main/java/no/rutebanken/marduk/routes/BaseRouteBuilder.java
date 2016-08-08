package no.rutebanken.marduk.routes;

import java.net.ConnectException;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.repository.ProviderRepository;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends RouteBuilder {

    @Autowired
    private ProviderRepository providerRepository;

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

    protected ProviderRepository getProviderRepository(){
        return providerRepository;
    }
    
    protected String correlation() {
    	return "[providerId=${header."+Constants.PROVIDER_ID+"} referential=${header."+Constants.CHOUETTE_REFERENTIAL+"} correlationId=${header."+Constants.CORRELATION_ID+"}] ";
    }


}
