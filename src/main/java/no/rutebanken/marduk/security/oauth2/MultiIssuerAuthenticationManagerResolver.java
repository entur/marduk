package no.rutebanken.marduk.security.oauth2;

import com.nimbusds.jwt.JWTParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolve the @{@link AuthenticationManager} that should authenticate the current JWT token.
 * This is achieved by extracting the issuer from the token and matching it against either the Keycloack
 * issuer URI or the Auth0 issuer URI.
 * The two @{@link AuthenticationManager}s (one for Keycloak, one for Auth0) are instantiated during the first request and then cached.
 *
 */
@Component
public class MultiIssuerAuthenticationManagerResolver
        implements AuthenticationManagerResolver<HttpServletRequest> {

    @Value("${marduk.oauth2.resourceserver.keycloak.jwt.audience}")
    private String keycloakAudience;

    @Value("${marduk.oauth2.resourceserver.keycloak.jwt.issuer-uri}")
    private String keycloakIssuer;

    @Value("${marduk.oauth2.resourceserver.keycloak.jwt.jwkset-uri}")
    private String keycloakJwksetUri;

    @Value("${marduk.oauth2.resourceserver.auth0.jwt.audience}")
    private String auth0Audience;

    @Value("${marduk.oauth2.resourceserver.auth0.jwt.issuer-uri}")
    private String auth0Issuer;

    @Value("${marduk.oauth2.resourceserver.auth0.admin.activated:false}")
    private boolean administratorAccessActivated;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final BearerTokenResolver resolver = new DefaultBearerTokenResolver();

    private final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap<>();

    /**
     * Build a @{@link JwtDecoder} for Auth0.
     * To ensure compatibility with the existing authorization process ({@link JwtRoleAssignmentExtractor}), a "roles"
     * claim is inserted in the token thanks to @{@link Auth0RolesClaimAdapter}
     * @return a @{@link JwtDecoder} for Auth0.
     */
    private JwtDecoder auth0JwtDecoder() {
        NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder)
                JwtDecoders.fromOidcIssuerLocation(auth0Issuer);

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(auth0Audience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(auth0Issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);
        jwtDecoder.setClaimSetConverter(new Auth0RolesClaimAdapter(administratorAccessActivated));
        return jwtDecoder;
    }

    /**
     * Build a @{@link JwtDecoder} for Keycloak.
     * Keycloak exposes a non-standard JWK-Set URI that must be configured explicitly.
     * @return a @{@link JwtDecoder} for Keycloak.
     */
    private JwtDecoder keycloakJwtDecoder() {

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(keycloakJwksetUri).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(keycloakAudience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(keycloakIssuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);
        return jwtDecoder;
    }

    private JwtDecoder jwtDecoder(String issuer) {
        if (auth0Issuer.equals(issuer)) {
            return auth0JwtDecoder();
        } else if (keycloakIssuer.equals(issuer)) {
            return keycloakJwtDecoder();
        } else {
            throw new IllegalArgumentException("unknown issuer");
        }
    }


    private String toIssuer(HttpServletRequest request) {
        try {
            String token = this.resolver.resolve(request);
            String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            logger.debug("Received JWT token from issuer {}", issuer);
            return issuer;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    private AuthenticationManager fromIssuer(String issuer) {
        return Optional.ofNullable(issuer)
                .map(this::jwtDecoder)
                .map(JwtAuthenticationProvider::new)
                .orElseThrow(() -> new IllegalArgumentException("unknown issuer"))::authenticate;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        return this.authenticationManagers.computeIfAbsent(toIssuer(request), this::fromIssuer);
    }

}
