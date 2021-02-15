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

import org.entur.oauth2.AuthorizedWebClientBuilder;
import org.entur.oauth2.JwtRoleAssignmentExtractor;
import org.entur.oauth2.RorAuth0RolesClaimAdapter;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class OAuth2Config {

    /**
     * Return a WebClient for authorized API calls.
     * The WebClient inserts a JWT bearer token in the Authorization HTTP header.
     * The JWT token is obtained from the configured Authorization Server.
     *
     * @param properties The spring.security.oauth2.client.registration.* properties
     * @param audience   The API audience, required for obtaining a token from Auth0
     * @return a WebClient for authorized API calls.
     */
    @Bean
    WebClient webClient(OAuth2ClientProperties properties, @Value("${marduk.oauth2.client.audience}") String audience) {
        return new AuthorizedWebClientBuilder()
                .withOAuth2ClientProperties(properties)
                .withAudience(audience)
                .withClientRegistrationId("marduk")
                .build();
    }

    @Bean
    public RoleAssignmentExtractor roleAssignmentExtractor() {
        return new JwtRoleAssignmentExtractor();
    }

    @Bean
    public RorAuth0RolesClaimAdapter rorAuth0RolesClaimAdapter(@Value("${marduk.oauth2.resourceserver.auth0.ror.claim.namespace}") String rorAuth0ClaimNamespace) {
        return new RorAuth0RolesClaimAdapter(rorAuth0ClaimNamespace);
    }


}

