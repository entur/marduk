package no.rutebanken.marduk.security.oauth2;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.exceptions.MardukException;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Insert a "roles" claim in the JWT token based on the organisationID claim, for compatibility with the existing
 * authorization process (@{@link JwtRoleAssignmentExtractor}).
 */
@Component
public class Auth0RolesClaimAdapter implements Converter<Map<String, Object>, Map<String, Object>> {

    static final String ORG_RUTEBANKEN = "RB";

    private final ObjectMapper mapper = new ObjectMapper();

    private final MappedJwtClaimSetConverter delegate =
            MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

    @Value("#{${marduk.oauth2.resourceserver.auth0.partner.organisations}}")
    private Map<Long, String> rutebankenOrganisations;

    @Value("${marduk.oauth2.resourceserver.auth0.partner.admin.activated:false}")
    private boolean administratorAccessActivated;

    @Value("#{${netex.export.block.authorization}}")
    protected Map<String, String> authorizedProvidersForNetexBlocksConsumer = Collections.emptyMap();

    @Override
    public Map<String, Object> convert(Map<String, Object> claims) {
        Map<String, Object> convertedClaims = this.delegate.convert(claims);
        Long enturOrganisationId = (Long) convertedClaims.get("https://entur.io/organisationID");
        String rutebankenOrganisationId = getRutebankenOrganisationId(enturOrganisationId);
        List<String> roleAssignments = new ArrayList<>(2);

        // Add route data role
        String roleRouteData = administratorAccessActivated && isEnturUser(rutebankenOrganisationId)
                ? AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN
                : AuthorizationConstants.ROLE_ROUTE_DATA_EDIT;

        RoleAssignment.Builder routeDataRoleAssignmentBuilder = RoleAssignment.builder();
        routeDataRoleAssignmentBuilder.withRole(roleRouteData);
        routeDataRoleAssignmentBuilder.withOrganisation(rutebankenOrganisationId);
        roleAssignments.add(toJSON(routeDataRoleAssignmentBuilder.build()));

        // Add NeTEx Blocks view roles
        for(String authorizedNetexBlocksProviderForConsumer: getNetexBlocksProvidersForConsumer(rutebankenOrganisationId)) {
            RoleAssignment.Builder netexBlockRoleAssignmentBuilder = RoleAssignment.builder();
            netexBlockRoleAssignmentBuilder.withRole(AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW);
            netexBlockRoleAssignmentBuilder.withOrganisation(authorizedNetexBlocksProviderForConsumer);
            roleAssignments.add(toJSON(netexBlockRoleAssignmentBuilder.build()));
        }

        convertedClaims.put("roles", roleAssignments);
        return convertedClaims;
    }

    private boolean isEnturUser(String rutebankenOrganisationId) {
        return ORG_RUTEBANKEN.equals(rutebankenOrganisationId);
    }

    private List<String> getNetexBlocksProvidersForConsumer(String rutebankenOrganisationId) {
        if( authorizedProvidersForNetexBlocksConsumer.get(rutebankenOrganisationId) == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(authorizedProvidersForNetexBlocksConsumer.get(rutebankenOrganisationId).split(","));
        }

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
            throw new MardukException(e);
        }

    }
}
