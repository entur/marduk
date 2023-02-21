package no.rutebanken.marduk.security.oauth2;

import com.nimbusds.jwt.JWTParser;
import org.entur.oauth2.AudienceValidator;
import org.entur.oauth2.RoRJwtDecoderBuilder;
import org.entur.oauth2.RorAuthenticationConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * This is achieved by extracting the issuer from the token and matching it against either the Entur Partner
 * issuer URI or the RoR Auth0 issuer URI.
 * The two @{@link AuthenticationManager}s (one for Entur Partner Auth0, one for RoR Auth0) are instantiated during the first request and then cached.
 */
@Component
public class MultiIssuerAuthenticationManagerResolver
        implements AuthenticationManagerResolver<HttpServletRequest> {


    @Value("${marduk.oauth2.resourceserver.auth0.partner.jwt.audience}")
    private String enturPartnerAuth0Audience;

    @Value("${marduk.oauth2.resourceserver.auth0.partner.jwt.issuer-uri}")
    private String enturPartnerAuth0Issuer;

    @Value("${marduk.oauth2.resourceserver.auth0.ror.jwt.audience}")
    private String rorAuth0Audience;

    @Value("${marduk.oauth2.resourceserver.auth0.ror.jwt.issuer-uri}")
    private String rorAuth0Issuer;

    @Value("${marduk.oauth2.resourceserver.auth0.ror.claim.namespace}")
    private String rorAuth0ClaimNamespace;

    @Autowired
    EnturPartnerAuth0RolesClaimAdapter enturPartnerAuth0RolesClaimAdapter;

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final BearerTokenResolver resolver = new DefaultBearerTokenResolver();

    private final Map<String, AuthenticationManager> authenticationManagers = new ConcurrentHashMap<>();

    /**
     * Build a @{@link JwtDecoder} for Entur Partner Auth0 tenant.
     * To ensure compatibility with the existing authorization process ({@link org.entur.oauth2.JwtRoleAssignmentExtractor}), a "roles"
     * claim is inserted in the token thanks to @{@link EnturPartnerAuth0RolesClaimAdapter}
     *
     * @return a @{@link JwtDecoder} for Auth0.
     */
    private JwtDecoder enturPartnerAuth0JwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(enturPartnerAuth0Issuer);

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(enturPartnerAuth0Audience);
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(enturPartnerAuth0Issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);
        jwtDecoder.setClaimSetConverter(enturPartnerAuth0RolesClaimAdapter);
        return jwtDecoder;
    }

    /**
     * Build a @{@link JwtDecoder} for Ror Auth0 tenant.
     *
     * @return a @{@link JwtDecoder} for Auth0.
     */
    private JwtDecoder rorAuth0JwtDecoder() {
        return new RoRJwtDecoderBuilder().withIssuer(rorAuth0Issuer)
                .withAudience(rorAuth0Audience)
                .withAuth0ClaimNamespace(rorAuth0ClaimNamespace)
                .build();
    }

    private JwtDecoder jwtDecoder(String issuer) {
        if (enturPartnerAuth0Issuer.equals(issuer)) {
            return enturPartnerAuth0JwtDecoder();
        } else if (rorAuth0Issuer.equals(issuer)) {
            return rorAuth0JwtDecoder();
        } else {
            throw new IllegalArgumentException("Received JWT token with unknown OAuth2 issuer: " + issuer);
        }
    }


    private String toIssuer(HttpServletRequest request) {
        try {
            String token = this.resolver.resolve(request);
            String issuer = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
            logger.debug("Received JWT token from OAuth2 issuer {}", issuer);
            return issuer;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    AuthenticationManager fromIssuer(String issuer) {
        return Optional.ofNullable(issuer)
                .map(this::jwtDecoder)
                .map(this::jwtAuthenticationProvider)
                .orElseThrow(() -> new IllegalArgumentException("Received JWT token with null OAuth2 issuer"))::authenticate;
    }

    @Override
    public AuthenticationManager resolve(HttpServletRequest request) {
        return this.authenticationManagers.computeIfAbsent(toIssuer(request), this::fromIssuer);
    }

    private JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder jwtDecoder) {
        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtDecoder);
        jwtAuthenticationProvider.setJwtAuthenticationConverter(new RorAuthenticationConverter());
        return jwtAuthenticationProvider;
    }

}
