package no.rutebanken.marduk.security.oauth2;

import org.entur.oauth2.AudienceValidator;
import org.entur.oauth2.JwtRoleAssignmentExtractor;
import org.entur.oauth2.multiissuer.MultiIssuerAuthenticationManagerResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolve the @{@link AuthenticationManager} that should authenticate the current JWT token.
 * This is achieved by extracting the issuer from the token and matching it against either the Entur Partner
 * issuer URI or the RoR Auth0 issuer URI.
 * The two @{@link AuthenticationManager}s (one for Entur Partner Auth0, one for RoR Auth0) are instantiated during the first request and then cached.
 */
@Component
public class MardukMultiIssuerAuthenticationManagerResolver
        extends MultiIssuerAuthenticationManagerResolver {

    private final EnturPartnerAuth0RolesClaimAdapter enturPartnerAuth0RolesClaimAdapter;
    private final String enturPartnerAuth0Issuer;
    private final String enturPartnerAuth0Audience;
    private final String rorAuth0Audience;

    public MardukMultiIssuerAuthenticationManagerResolver(
            @Value("${marduk.oauth2.resourceserver.auth0.partner.jwt.audience}") String enturPartnerAuth0Audience,
            @Value("${marduk.oauth2.resourceserver.auth0.partner.jwt.issuer-uri}") String enturPartnerAuth0Issuer,
            @Value("${marduk.oauth2.resourceserver.auth0.ror.jwt.audience}") String rorAuth0Audience,
            @Value("${marduk.oauth2.resourceserver.auth0.ror.jwt.issuer-uri}") String rorAuth0Issuer,
            @Value("${marduk.oauth2.resourceserver.auth0.ror.claim.namespace}") String rorAuth0ClaimNamespace,
            EnturPartnerAuth0RolesClaimAdapter enturPartnerAuth0RolesClaimAdapter) {
        super(null, null, enturPartnerAuth0Audience, enturPartnerAuth0Issuer, rorAuth0Audience, rorAuth0Issuer, rorAuth0ClaimNamespace);
        this.enturPartnerAuth0RolesClaimAdapter = enturPartnerAuth0RolesClaimAdapter;
        this.enturPartnerAuth0Issuer = enturPartnerAuth0Issuer;
        this.enturPartnerAuth0Audience = enturPartnerAuth0Audience;
        this.rorAuth0Audience = rorAuth0Audience;
    }

    /**
     * Build a @{@link JwtDecoder} for Entur Partner Auth0 tenant.
     * To ensure compatibility with the existing authorization process ({@link JwtRoleAssignmentExtractor}), a "roles"
     * claim is inserted in the token thanks to @{@link EnturPartnerAuth0RolesClaimAdapter}
     *
     * @return a @{@link JwtDecoder} for Auth0.
     */
    @Override
    protected JwtDecoder enturPartnerAuth0JwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(enturPartnerAuth0Issuer);

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(List.of(enturPartnerAuth0Audience, rorAuth0Audience));
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(enturPartnerAuth0Issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);
        jwtDecoder.setClaimSetConverter(enturPartnerAuth0RolesClaimAdapter);
        return jwtDecoder;
    }

}
