package no.rutebanken.marduk.security.oauth2;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;


/**
 * Insert a "roles" and "realm_access" claims in the JWT token based on the "permission" claim and the  namespaced "roles" and "role_assignments" claims, for compatibility with the existing
 * authorization process (@{@link no.rutebanken.marduk.security.oauth2.JwtRoleAssignmentExtractor}).
 */
@Component
public class RorAuth0RolesClaimAdapter implements Converter<Map<String, Object>, Map<String, Object>> {

    private final MappedJwtClaimSetConverter delegate =
            MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

    @Value("${marduk.oauth2.resourceserver.auth0.ror.claim.namespace}")
    private String rorAuth0ClaimNamespace;

    @Override
    public Map<String, Object> convert(Map<String, Object> claims) {
        Map<String, Object> convertedClaims = this.delegate.convert(claims);

        // roles assigned to a user are found in the namespaced "roles" claim
        Object roles = convertedClaims.get(rorAuth0ClaimNamespace + "roles");
        if (roles != null) {
            convertedClaims.put("realm_access", Map.of("roles", roles));
        } else {
            // roles assigned to an application (machine-to-machine service call) are found in the "permissions" claim
            Object permissions = convertedClaims.get("permissions");
            if (permissions != null) {
                convertedClaims.put("realm_access", Map.of("roles", permissions));
            }
        }

        // role assignments for users and applications are found in the namespaced "role_assignments" claim
        Object roleAssignments = convertedClaims.get(rorAuth0ClaimNamespace + "role_assignments");
        if (roleAssignments != null) {
            convertedClaims.put("roles", roleAssignments);
        }

        return convertedClaims;
    }


}
