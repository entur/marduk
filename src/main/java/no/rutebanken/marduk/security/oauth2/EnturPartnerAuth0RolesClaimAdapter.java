package no.rutebanken.marduk.security.oauth2;

import com.fasterxml.jackson.databind.ObjectWriter;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import org.entur.oauth2.RoROAuth2Claims;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * Insert a "roles" claim in the JWT token based on the organisationID claim, for compatibility with the existing
 * authorization process (@{@link org.entur.oauth2.JwtRoleAssignmentExtractor}).
 */
@Component
public class EnturPartnerAuth0RolesClaimAdapter implements Converter<Map<String, Object>, Map<String, Object>> {

    static final String ORG_RUTEBANKEN = "RB";

    private static final ObjectWriter ROLE_ASSIGNMENT_OBJECT_WRITER = ObjectMapperFactory.getSharedObjectMapper().writerFor(RoleAssignment.class);

    private final MappedJwtClaimSetConverter delegate =
            MappedJwtClaimSetConverter.withDefaults(Collections.emptyMap());

    @Value("#{${marduk.oauth2.resourceserver.auth0.partner.organisations}}")
    private Map<Long, String> rutebankenOrganisations;

    @Value("${marduk.oauth2.resourceserver.auth0.partner.admin.activated:false}")
    private boolean administratorAccessActivated;

    @Value("#{${netex.export.block.authorization}}")
    protected final Map<String, String> authorizedProvidersForNetexBlocksConsumer = Collections.emptyMap();

    @Value("#{${netex.import.delegation.authorization}}")
    private final Map<String, String> delegatedNetexDataProviders = Collections.emptyMap();

    @Override
    public Map<String, Object> convert(Map<String, Object> claims) {
        Map<String, Object> convertedClaims = this.delegate.convert(claims);
        Long enturOrganisationId = (Long) convertedClaims.get("https://entur.io/organisationID");
        String rutebankenOrganisationId = getRutebankenOrganisationId(enturOrganisationId);
        List<String> roleAssignments = new ArrayList<>(2);

        // Add role to edit data from own organization
        String roleRouteData = administratorAccessActivated && isEnturUser(rutebankenOrganisationId)
                ? AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN
                : AuthorizationConstants.ROLE_ROUTE_DATA_EDIT;

        RoleAssignment.Builder routeDataRoleAssignmentBuilder = RoleAssignment.builder();
        routeDataRoleAssignmentBuilder.withRole(roleRouteData);
        routeDataRoleAssignmentBuilder.withOrganisation(rutebankenOrganisationId);
        roleAssignments.add(toJSON(routeDataRoleAssignmentBuilder.build()));

        // Add role to view NeTEx Blocks belonging to other organizations
        for (String authorizedNetexBlocksProviderForConsumer : getNetexBlocksProvidersForConsumer(rutebankenOrganisationId)) {
            RoleAssignment.Builder netexBlockRoleAssignmentBuilder = RoleAssignment.builder();
            netexBlockRoleAssignmentBuilder.withRole(AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW);
            netexBlockRoleAssignmentBuilder.withOrganisation(authorizedNetexBlocksProviderForConsumer);
            roleAssignments.add(toJSON(netexBlockRoleAssignmentBuilder.build()));
        }

        // Add role to edit data belonging to other organizations
        for (String delegatedNetexDataProvider : getDelegatedNetexDataProviders(rutebankenOrganisationId)) {
            RoleAssignment.Builder delegatedRouteDataRoleAssignmentBuilder = RoleAssignment.builder();
            delegatedRouteDataRoleAssignmentBuilder.withRole(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT);
            delegatedRouteDataRoleAssignmentBuilder.withOrganisation(delegatedNetexDataProvider);
            roleAssignments.add(toJSON(delegatedRouteDataRoleAssignmentBuilder.build()));
        }

        convertedClaims.put(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS, roleAssignments);

        // Add a preferred name to be displayed in Ninkasi
        convertedClaims.put(StandardClaimNames.PREFERRED_USERNAME, rutebankenOrganisationId + " (File transfer via API)");

        return convertedClaims;
    }


    private boolean isEnturUser(String rutebankenOrganisationId) {
        return ORG_RUTEBANKEN.equals(rutebankenOrganisationId);
    }

    /**
     * Return the list of codespaces for which the organization can view NeTEx block data.
     */
    private List<String> getNetexBlocksProvidersForConsumer(String rutebankenOrganisationId) {
        if (authorizedProvidersForNetexBlocksConsumer.get(rutebankenOrganisationId) == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(authorizedProvidersForNetexBlocksConsumer.get(rutebankenOrganisationId).split(","));
        }

    }

    /**
     * Return the list of codespaces for which the organization can edit NeTEx data.
     */
    private List<String> getDelegatedNetexDataProviders(String rutebankenOrganisationId) {
        if (delegatedNetexDataProviders.get(rutebankenOrganisationId) == null) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(delegatedNetexDataProviders.get(rutebankenOrganisationId).split(","));
        }
    }

    private String getRutebankenOrganisationId(Long enturOrganisationId) {
        return Optional.ofNullable(rutebankenOrganisations.get(enturOrganisationId))
                .orElseThrow(() -> new IllegalArgumentException("unknown organisation " + enturOrganisationId));
    }


    private String toJSON(RoleAssignment roleAssignment) {
        try {
            return ROLE_ASSIGNMENT_OBJECT_WRITER.writeValueAsString(roleAssignment);
        } catch (IOException e) {
            throw new MardukException(e);
        }

    }
}
