package no.rutebanken.marduk.pubsub;

import org.apache.camel.Endpoint;
import org.apache.camel.support.LifecycleStrategySupport;
import org.springframework.stereotype.Component;

@Component
public class EnturGooglePubSubSubscriptionCreator extends LifecycleStrategySupport {

    @Override
    public void onEndpointAdd(Endpoint endpoint) {
        if (endpoint instanceof EnturGooglePubSubEndpoint) {
            ((EnturGooglePubSubEndpoint) endpoint).createSubscriptionIfMissing();

        }
    }

}
