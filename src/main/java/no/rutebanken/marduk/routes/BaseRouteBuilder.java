package no.rutebanken.marduk.routes;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.apache.camel.spring.SpringRouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Defines common route behavior.
 */
public abstract class BaseRouteBuilder extends SpringRouteBuilder {

    @Autowired
    private ProviderRepository providerRepository;

    @Override
    public void configure() throws Exception {

        errorHandler(transactionErrorHandler()
                .logExhausted(true)
                .logRetryStackTrace(true));
    }

    protected ProviderRepository getProviderRepository(){
        return providerRepository;
    }

    protected String correlation() {
    	return "[providerId=${header."+Constants.PROVIDER_ID+"} referential=${header."+Constants.CHOUETTE_REFERENTIAL+"} correlationId=${header."+Constants.CORRELATION_ID+"}] ";
    }


}
