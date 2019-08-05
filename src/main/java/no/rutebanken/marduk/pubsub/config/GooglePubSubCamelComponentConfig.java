package no.rutebanken.marduk.pubsub.config;

import no.rutebanken.marduk.pubsub.EnturGooglePubSubComponent;
import org.apache.camel.CamelContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Register the Camel PubSub component.
 */
@Configuration
public class GooglePubSubCamelComponentConfig {


    @Autowired
    public void registerPubsubComponent(CamelContext camelContext, EnturGooglePubSubComponent enturGooglePubsub) {
        camelContext.addComponent("entur-google-pubsub", enturGooglePubsub);
    }


    @Bean
    public EnturGooglePubSubComponent googlePubsubComponent() {
        EnturGooglePubSubComponent component = new EnturGooglePubSubComponent();
        return component;
    }


}
