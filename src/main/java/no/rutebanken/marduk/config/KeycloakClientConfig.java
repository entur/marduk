package no.rutebanken.marduk.config;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakClientConfig {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm:rutebanken}")
    private String realm;

    @Value("${iam.keycloak.client.secret}")
    private String clientSecret;

    @Value("${keycloak.resource}")
    private String clientId;

    @Bean
    public Keycloak keycloakClient() {
        return KeycloakBuilder.builder().clientId(clientId).clientSecret(clientSecret)
                       .realm(realm).serverUrl(authServerUrl).grantType("client_credentials").build();
    }
}
