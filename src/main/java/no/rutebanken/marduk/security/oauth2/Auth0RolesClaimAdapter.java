package no.rutebanken.marduk.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Insert a "roles" claim in the JWT token based on the organisationID claim, for compatibility with the existing
 * authorization process (@{@link JwtRoleAssignmentExtractor}).
 */
class Auth0RolesClaimAdapter implements Converter<Map<String, Object>, Map<String, Object>> {


    private static final ObjectMapper mapper = new ObjectMapper();

    private static final String ORG_RUTEBANKEN = "RB";


    private static final Map<Long, String> rutebankenOrganisations = Map.of(
            1L, ORG_RUTEBANKEN,
            14L, "MOR",
            20L, "NSB",
            35L, "GOA",
            41L, "SJN",
            56L, "FLI"
    );

    private final MappedJwtClaimSetConverter delegate =
            MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

    private boolean administratorAccessActivated;

    Auth0RolesClaimAdapter(boolean administratorAccessActivated) {
        this.administratorAccessActivated = administratorAccessActivated;
    }


    public Map<String, Object> convert(Map<String, Object> claims) {
        Map<String, Object> convertedClaims = this.delegate.convert(claims);

        Long enturOrganisationId = (Long) convertedClaims.get("https://entur.io/organisationID");
        String rutebankenOrganisationId = getRutebankenOrganisationId(enturOrganisationId);

        String role = administratorAccessActivated && ORG_RUTEBANKEN.equals(rutebankenOrganisationId)
                ? AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN
                : AuthorizationConstants.ROLE_ROUTE_DATA_EDIT;

        RoleAssignment.Builder builder = RoleAssignment.builder();
        builder.withRole(role);
        builder.withOrganisation(rutebankenOrganisationId);

        List<String> roleAssignments = Arrays.asList(toJSON(builder.build()));
        convertedClaims.put("roles", roleAssignments);
        return convertedClaims;
    }

    private String getRutebankenOrganisationId(Long enturOrganisationId) {
        return Optional.ofNullable(rutebankenOrganisations.get(enturOrganisationId))
                .orElseThrow(() -> new IllegalArgumentException("unknown organisation " + enturOrganisationId));
    }


    private String toJSON(RoleAssignment roleAssignment) {
        StringWriter writer = new StringWriter();
        try {
            mapper.writeValue(writer, roleAssignment);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
