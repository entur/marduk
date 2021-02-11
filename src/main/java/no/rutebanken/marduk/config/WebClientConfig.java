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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesRegistrationAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ClientCredentialsReactiveOAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.InMemoryReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.RemoveAuthorizedClientReactiveOAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebClientConfig {

    /**
     * Return a WebClient for authorized API calls.
     * The WebClient inserts a JWT bearer token in the Authorization HTTP header.
     * The JWT token is obtained from the configured Authorization Server.
     *
     * @param properties The spring.security.oauth2.client.registration.* properties
     * @param audience The API audience, required for obtaining a token from Auth0
     * @return  a WebClient for authorized API calls.
     */
    @Bean
    WebClient webClient(OAuth2ClientProperties properties, @Value("${marduk.oauth2.client.audience}") String audience) {

        ReactiveClientRegistrationRepository clientRegistrations = clientRegistrationRepository(properties);
        ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService = new InMemoryReactiveOAuth2AuthorizedClientService(clientRegistrations);

        // the failure handler ensures that the authorized client is removed if an authorization error occurs. A new token will then be requested.
        // this makes it possible to replace an expired token with a new one.
        ReactiveOAuth2AuthorizationFailureHandler authorizationFailureHandler =
                new RemoveAuthorizedClientReactiveOAuth2AuthorizationFailureHandler(
                        (clientRegistrationId, principal, attributes) ->
                                reactiveOAuth2AuthorizedClientService.removeAuthorizedClient(clientRegistrationId, principal.getName()));

        ServerOAuth2AuthorizedClientExchangeFilterFunction oauth = new ServerOAuth2AuthorizedClientExchangeFilterFunction(reactiveOAuth2AuthorizedClientManager(clientRegistrations, reactiveOAuth2AuthorizedClientService, authorizationFailureHandler, audience));
        oauth.setDefaultClientRegistrationId("marduk");
        oauth.setAuthorizationFailureHandler(authorizationFailureHandler);

        return WebClient.builder()
                .filters(exchangeFilterFunctions -> exchangeFilterFunctions.add(oauth))
                .build();
    }


    /**
     * Return the repository of OAuth2 clients.
     * In a reactive Spring Boot application this bean would be auto-configured.
     * Since Marduk is servlet-based (not reactive), the bean must be created manually.
     *
     * @param properties The spring.security.oauth2.client.registration.* properties
     * @return the repository of OAuth2 clients.
     */
    private ReactiveClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        List<ClientRegistration> registrations = new ArrayList<>(
                OAuth2ClientPropertiesRegistrationAdapter.getClientRegistrations(properties).values());
        return new InMemoryReactiveClientRegistrationRepository(registrations);
    }


    /**
     * Return an Authorized Client Manager.
     * This must be manually configured in order to inject a WebClient compatible with Auth0.
     * See {@link #webClientForTokenRequest(String)}
     *
     * @param audience The API audience, required for obtaining a token from Auth0
     * @return an Authorized Client Manager
     */
    private ReactiveOAuth2AuthorizedClientManager reactiveOAuth2AuthorizedClientManager(ReactiveClientRegistrationRepository clientRegistrations, ReactiveOAuth2AuthorizedClientService reactiveOAuth2AuthorizedClientService, ReactiveOAuth2AuthorizationFailureHandler authorizationFailureHandler, String audience) {

        WebClientReactiveClientCredentialsTokenResponseClient webClientReactiveClientCredentialsTokenResponseClient = new WebClientReactiveClientCredentialsTokenResponseClient();
        webClientReactiveClientCredentialsTokenResponseClient.setWebClient(webClientForTokenRequest(audience));

        ClientCredentialsReactiveOAuth2AuthorizedClientProvider reactiveOAuth2AuthorizedClientProvider = new ClientCredentialsReactiveOAuth2AuthorizedClientProvider();
        reactiveOAuth2AuthorizedClientProvider.setAccessTokenResponseClient(webClientReactiveClientCredentialsTokenResponseClient);

        AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager authorizedClientServiceReactiveOAuth2AuthorizedClientManager = new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(clientRegistrations, reactiveOAuth2AuthorizedClientService);
        authorizedClientServiceReactiveOAuth2AuthorizedClientManager.setAuthorizationFailureHandler(authorizationFailureHandler);
        authorizedClientServiceReactiveOAuth2AuthorizedClientManager.setAuthorizedClientProvider(reactiveOAuth2AuthorizedClientProvider);
        return authorizedClientServiceReactiveOAuth2AuthorizedClientManager;
    }

    /**
     * Return a WebClient for requesting a token to the Authorization Server.
     * Auth0 requires that the form data in the body include an "audience" parameter in addition to the standard
     * "grant_type" parameter.
     *
     * @param audience the audience to be inserted in the request body for compatibility with Auth0.
     * @return a WebClient instance that can be used for requesting a token to the Authorization Server.
     */
    private WebClient webClientForTokenRequest(String audience) {

        // The exchange filter adds the 2 required parameters in the request body.
        ExchangeFilterFunction tokenRequestFilter = ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            ClientRequest.Builder builder = ClientRequest.from(clientRequest);
            LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("grant_type", "client_credentials");
            formData.add("audience", audience);
            builder.body(BodyInserters.fromFormData(formData));
            return Mono.just(builder.build());
        });

        return WebClient.builder()
                .filters(exchangeFilterFunctions -> exchangeFilterFunctions.add(tokenRequestFilter))
                .build();
    }


}


