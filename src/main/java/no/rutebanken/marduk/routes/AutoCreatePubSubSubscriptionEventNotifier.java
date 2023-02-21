/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.routes;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.component.master.MasterEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.DefaultInterceptSendToEndpoint;
import org.apache.camel.support.EventNotifierSupport;
import org.entur.pubsub.base.EnturGooglePubSubAdmin;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Create PubSub topics and subscriptions on startup.
 * This is used only in unit tests and local environment.
 */
@Component
@Profile("google-pubsub-autocreate")
public class AutoCreatePubSubSubscriptionEventNotifier extends EventNotifierSupport {

    private final EnturGooglePubSubAdmin enturGooglePubSubAdmin;

    public AutoCreatePubSubSubscriptionEventNotifier(EnturGooglePubSubAdmin enturGooglePubSubAdmin) {
        this.enturGooglePubSubAdmin = enturGooglePubSubAdmin;
    }

    @Override
    public void notify(CamelEvent event) {

        if (event instanceof CamelEvent.CamelContextStartingEvent camelContextStartingEvent) {
            CamelContext context = camelContextStartingEvent.getContext();
            context.getEndpoints().stream().filter(e -> e.getEndpointUri().contains("google-pubsub:")).forEach(this::createSubscriptionIfMissing);
        }

    }

    private void createSubscriptionIfMissing(Endpoint e) {
        GooglePubsubEndpoint gep;
        if (e instanceof GooglePubsubEndpoint googlePubsubEndpoint) {
            gep = googlePubsubEndpoint;
        } else if (e instanceof MasterEndpoint masterEndpoint && ((MasterEndpoint) e).getEndpoint() instanceof GooglePubsubEndpoint) {
            gep = (GooglePubsubEndpoint) masterEndpoint.getEndpoint();
        } else if (e instanceof DefaultInterceptSendToEndpoint defaultInterceptSendToEndpoint && defaultInterceptSendToEndpoint.getOriginalEndpoint() instanceof GooglePubsubEndpoint googlePubsubEndpoint) {
            gep = googlePubsubEndpoint;
        } else if (e instanceof MasterEndpoint masterEndpoint && masterEndpoint.getEndpoint() instanceof DefaultInterceptSendToEndpoint defaultInterceptSendToEndpoint) {
            gep = (GooglePubsubEndpoint) defaultInterceptSendToEndpoint.getOriginalEndpoint();
        } else {
            throw new IllegalStateException("Incompatible endpoint: " + e);
        }
        enturGooglePubSubAdmin.createSubscriptionIfMissing(gep.getDestinationName());
    }

}
