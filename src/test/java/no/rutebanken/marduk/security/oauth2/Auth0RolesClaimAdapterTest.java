package no.rutebanken.marduk.security.oauth2;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Auth0RolesClaimAdapterTest extends MardukRouteBuilderIntegrationTestBase {

    private static final Long RUTEBANKEN_ORG_ID = 1L;
    private static final Long PROVIDER_ORG_ID = 2L;
    private static final Long ORG_NETEX_BLOCKS_VIEWER_ID = 100L;
    private static final String ORGANISATION_CLAIM = "https://entur.io/organisationID";

    @Autowired
    private EnturPartnerAuth0RolesClaimAdapter auth0RolesClaimAdapter;


    @Test
    void testVerifyRoleAdmin() {
        Map<String, Object> claims = Map.of(ORGANISATION_CLAIM, RUTEBANKEN_ORG_ID);
        Map<String, Object> convertedClaims = auth0RolesClaimAdapter.convert(claims);
        Assertions.assertNotNull(convertedClaims);
        Assertions.assertEquals(2, convertedClaims.size());
        Assertions.assertNotNull(convertedClaims.get(ORGANISATION_CLAIM));
        Long organisationId = (Long) convertedClaims.get(ORGANISATION_CLAIM);
        Assertions.assertEquals(RUTEBANKEN_ORG_ID, organisationId);
        Assertions.assertNotNull(convertedClaims.get("roles"));
        List<String> roles = (List<String>) convertedClaims.get("roles");
        Assertions.assertEquals(1, roles.size());
        String role = roles.get(0);
        Assertions.assertTrue(role.contains(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN), "Entur users should have administrator privileges");
    }

    @Test
    void testVerifyRoleEditor() {
        Map<String, Object> claims = Map.of(ORGANISATION_CLAIM, PROVIDER_ORG_ID);
        Map<String, Object> convertedClaims = auth0RolesClaimAdapter.convert(claims);
        Assertions.assertNotNull(convertedClaims);
        Assertions.assertEquals(2, convertedClaims.size());
        Assertions.assertNotNull(convertedClaims.get(ORGANISATION_CLAIM));
        Long organisationId = (Long) convertedClaims.get(ORGANISATION_CLAIM);
        Assertions.assertEquals(PROVIDER_ORG_ID, organisationId);
        Assertions.assertNotNull(convertedClaims.get("roles"));
        List<String> roles = (List<String>) convertedClaims.get("roles");
        Assertions.assertEquals(1, roles.size());
        String role = roles.get(0);
        Assertions.assertTrue(role.contains(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT), "Providers should have editor privileges");
    }

    @Test
    void testVerifyRoleNetexBlocksViewer() {
        Map<String, Object> claims = Map.of(ORGANISATION_CLAIM, ORG_NETEX_BLOCKS_VIEWER_ID);
        Map<String, Object> convertedClaims = auth0RolesClaimAdapter.convert(claims);
        Assertions.assertNotNull(convertedClaims);
        Assertions.assertEquals(2, convertedClaims.size());
        Assertions.assertNotNull(convertedClaims.get(ORGANISATION_CLAIM));
        Long organisationId = (Long) convertedClaims.get(ORGANISATION_CLAIM);
        Assertions.assertEquals(ORG_NETEX_BLOCKS_VIEWER_ID, organisationId);
        Assertions.assertNotNull(convertedClaims.get("roles"));
        List<String> roles = (List<String>) convertedClaims.get("roles");
        Assertions.assertEquals(2, roles.size());
        Assertions.assertTrue(roles.stream().anyMatch(r -> r.contains(AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW)), "NeTEx Blocks consumers should have NeTEx Blocks viewer privileges");
    }

}
