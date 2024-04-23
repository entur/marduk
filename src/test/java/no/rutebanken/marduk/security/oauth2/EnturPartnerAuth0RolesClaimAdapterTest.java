package no.rutebanken.marduk.security.oauth2;

import no.rutebanken.marduk.MardukSpringBootBaseTest;
import no.rutebanken.marduk.TestConstants;
import org.entur.oauth2.RoROAuth2Claims;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;

import java.util.List;
import java.util.Map;

import static no.rutebanken.marduk.security.oauth2.EnturPartnerAuth0RolesClaimAdapter.OPENID_AUDIENCE_CLAIM;
import static no.rutebanken.marduk.security.oauth2.EnturPartnerAuth0RolesClaimAdapter.ORGANISATION_ID_CLAIM;


class EnturPartnerAuth0RolesClaimAdapterTest extends MardukSpringBootBaseTest {

    private static final Long RUTEBANKEN_ORG_ID = 1L;
    private static final Long PROVIDER_ORG_ID = TestConstants.PROVIDER_ID_RUT;
    private static final Long ORG_NETEX_BLOCKS_VIEWER_ID = 100L;
    private static final Long ORG_DELEGATED_EDITOR_ID = 200L;

    @Autowired
    private EnturPartnerAuth0RolesClaimAdapter auth0RolesClaimAdapter;


    @Test
    void testVerifyRoleAdmin() {
        Map<String, Object> convertedClaims = claimsForOrganisationId(RUTEBANKEN_ORG_ID);
        Long organisationId = (Long) convertedClaims.get(ORGANISATION_ID_CLAIM);
        Assertions.assertEquals(RUTEBANKEN_ORG_ID, organisationId);
        Assertions.assertNotNull(convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS));
        List<String> roles = (List<String>) convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS);
        Assertions.assertEquals(1, roles.size());
        String role = roles.getFirst();
        Assertions.assertTrue(role.contains(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN), "Entur users should have administrator privileges");

        Assertions.assertNotNull(convertedClaims.get(StandardClaimNames.PREFERRED_USERNAME));
    }

    @Test
    void testVerifyRoleEditor() {
        Map<String, Object> convertedClaims = claimsForOrganisationId(PROVIDER_ORG_ID);
        Long organisationId = (Long) convertedClaims.get(ORGANISATION_ID_CLAIM);
        Assertions.assertEquals(PROVIDER_ORG_ID, organisationId);
        Assertions.assertNotNull(convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS));
        List<String> roles = (List<String>) convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS);
        Assertions.assertEquals(1, roles.size());
        String role = roles.getFirst();
        Assertions.assertTrue(role.contains(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT), "Providers should have editor privileges");

        Assertions.assertNotNull(convertedClaims.get(StandardClaimNames.PREFERRED_USERNAME));
    }

    @Test
    void testVerifyRoleNetexBlocksViewer() {
        Map<String, Object> convertedClaims = claimsForOrganisationId(ORG_NETEX_BLOCKS_VIEWER_ID);
        Long organisationId = (Long) convertedClaims.get(ORGANISATION_ID_CLAIM);
        Assertions.assertEquals(ORG_NETEX_BLOCKS_VIEWER_ID, organisationId);
        Assertions.assertNotNull(convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS));
        List<String> roles = (List<String>) convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS);
        Assertions.assertEquals(2, roles.size());
        Assertions.assertTrue(roles.stream().anyMatch(r -> r.contains(AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW)), "NeTEx Blocks consumers should have NeTEx Blocks viewer privileges");

        Assertions.assertNotNull(convertedClaims.get(StandardClaimNames.PREFERRED_USERNAME));
    }

    @Test
    void testVerifyRoleDelegatedEditor() {
        Map<String, Object> convertedClaims = claimsForOrganisationId(ORG_DELEGATED_EDITOR_ID);
        Long organisationId = (Long) convertedClaims.get(ORGANISATION_ID_CLAIM);
        Assertions.assertEquals(ORG_DELEGATED_EDITOR_ID, organisationId);
        Assertions.assertNotNull(convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS));
        List<String> roles = (List<String>) convertedClaims.get(RoROAuth2Claims.OAUTH2_CLAIM_ROLE_ASSIGNMENTS);
        Assertions.assertEquals(2, roles.size());
        Assertions.assertTrue(roles.stream().anyMatch(r -> r.contains(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT)), "Clients should have Route data privileges for their delegated providers");

        Assertions.assertNotNull(convertedClaims.get(StandardClaimNames.PREFERRED_USERNAME));
    }

    private Map<String, Object> claimsForOrganisationId(Long organisationId) {
        Map<String, Object> claims = Map.of(
                ORGANISATION_ID_CLAIM, organisationId,
                OPENID_AUDIENCE_CLAIM, "audience");
        Map<String, Object> convertedClaims = auth0RolesClaimAdapter.convert(claims);
        Assertions.assertNotNull(convertedClaims);
        Assertions.assertEquals(4, convertedClaims.size());
        Assertions.assertNotNull(convertedClaims.get(ORGANISATION_ID_CLAIM));
        Assertions.assertNotNull(convertedClaims.get(OPENID_AUDIENCE_CLAIM));

        return convertedClaims;
    }

}
