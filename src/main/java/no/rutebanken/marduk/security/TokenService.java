package no.rutebanken.marduk.security;

import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    @Autowired
    private Keycloak keycloakClient;

    public String getToken() {
        return keycloakClient.tokenManager().getAccessTokenString();
    }

}
