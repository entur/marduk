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

package no.rutebanken.marduk.config;

import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.InterceptSendToEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CamelConfigTest {

    @Test
    void defaultsMaxDeliveryAttemptsWhenNotSetExplicitly() {
        GooglePubsubEndpoint endpoint = mock(GooglePubsubEndpoint.class);
        when(endpoint.isMaxDeliveryAttemptsExplicitlySet()).thenReturn(false);

        assertSame(endpoint, CamelConfig.defaultPubsubMaxDeliveryAttempts("google-pubsub:p:Q", endpoint));
        verify(endpoint).setMaxDeliveryAttempts(0);
    }

    @Test
    void leavesExplicitlyConfiguredEndpointUntouched() {
        GooglePubsubEndpoint endpoint = mock(GooglePubsubEndpoint.class);
        when(endpoint.isMaxDeliveryAttemptsExplicitlySet()).thenReturn(true);

        CamelConfig.defaultPubsubMaxDeliveryAttempts("google-pubsub:p:Q?maxDeliveryAttempts=5", endpoint);
        verify(endpoint, never()).setMaxDeliveryAttempts(anyInt());
    }

    @Test
    void defaultsMaxDeliveryAttemptsOnInterceptedEndpoint() {
        GooglePubsubEndpoint delegate = mock(GooglePubsubEndpoint.class);
        when(delegate.isMaxDeliveryAttemptsExplicitlySet()).thenReturn(false);
        InterceptSendToEndpoint wrapper = mock(InterceptSendToEndpoint.class);
        when(wrapper.getOriginalEndpoint()).thenReturn(delegate);

        assertSame(wrapper, CamelConfig.defaultPubsubMaxDeliveryAttempts("google-pubsub:p:Q", wrapper));
        verify(delegate).setMaxDeliveryAttempts(0);
    }

    @Test
    void ignoresNonPubsubEndpoints() {
        Endpoint endpoint = mock(Endpoint.class);

        assertSame(endpoint, CamelConfig.defaultPubsubMaxDeliveryAttempts("direct:somewhere", endpoint));
        verifyNoInteractions(endpoint);
    }

    @Test
    void beanDefaultsMaxDeliveryAttemptsOnRealPubsubEndpoint() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            new CamelConfig().pubsubMaxDeliveryAttemptsDefault().beforeApplicationStart(context);
            context.start();

            GooglePubsubEndpoint endpoint =
                    context.getEndpoint("google-pubsub:my-project:MyQueue", GooglePubsubEndpoint.class);

            assertTrue(endpoint.isMaxDeliveryAttemptsExplicitlySet());
            assertEquals(0, endpoint.getMaxDeliveryAttempts());
        }
    }

    @Test
    void beanLeavesExplicitlyConfiguredRealEndpointUntouched() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            new CamelConfig().pubsubMaxDeliveryAttemptsDefault().beforeApplicationStart(context);
            context.start();

            GooglePubsubEndpoint endpoint = context.getEndpoint(
                    "google-pubsub:my-project:MyQueue?maxDeliveryAttempts=5", GooglePubsubEndpoint.class);

            assertEquals(5, endpoint.getMaxDeliveryAttempts());
        }
    }

    // Production shape: interceptSendToEndpoint (BaseRouteBuilder) registers competing endpoint
    // strategies applied in nondeterministic order, so the callback may see wrapped endpoints.
    // Without unwrapping, only a random subset of endpoints gets the default on each start.
    @Test
    void beanDefaultsMaxDeliveryAttemptsOnEndpointsWrappedByInterceptors() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            new CamelConfig().pubsubMaxDeliveryAttemptsDefault().beforeApplicationStart(context);
            for (int i = 0; i < 10; i++) {
                final int n = i;
                context.addRoutes(new RouteBuilder() {
                    @Override
                    public void configure() {
                        interceptSendToEndpoint("google-pubsub:*").to("mock:intercepted" + n);
                        from("direct:in" + n).autoStartup(false).to("google-pubsub:my-project:queue" + n);
                    }
                });
            }
            context.start();

            for (int i = 0; i < 10; i++) {
                Endpoint endpoint = context.getEndpoint("google-pubsub:my-project:queue" + i);
                GooglePubsubEndpoint pubsub = endpoint instanceof InterceptSendToEndpoint intercepted
                        ? (GooglePubsubEndpoint) intercepted.getOriginalEndpoint()
                        : (GooglePubsubEndpoint) endpoint;
                assertEquals(0, pubsub.getMaxDeliveryAttempts(), "queue" + i + " did not get the default");
                assertTrue(pubsub.isMaxDeliveryAttemptsExplicitlySet());
            }
        }
    }
}
