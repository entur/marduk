package no.rutebanken.marduk.security;

import no.rutebanken.marduk.MardukRouteBuilderIntegrationTestBase;
import no.rutebanken.marduk.TestApp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = TestApp.class)
class AuthorizationServiceTest extends MardukRouteBuilderIntegrationTestBase {

    private static final Long ORG_ID_RUT = 2L;
    private static final String CODESPACE_RB = "RB";
    private static final String CODESPACE_RUT = "RUT";

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    MockedRoleAssignmentExtractor mockedRoleAssignmentExtractor;

    @AfterEach
    void resetRoleAssignments() {
        mockedRoleAssignmentExtractor.setNextReturnedRoleAssignmentList(null);
    }


    @Test
    void testVerifyAtLeastOne() {
        AuthorizationClaim authorizationClaim = new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN);
        Assertions.assertDoesNotThrow(() -> authorizationService.verifyAtLeastOne(authorizationClaim));
    }

    @Test
    void testVerifyAdministratorPrivileges() {
        List<RoleAssignment> roleAssignments = List.of(
                RoleAssignment.builder().withOrganisation(CODESPACE_RB).withRole(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN).build());
        mockedRoleAssignmentExtractor.setNextReturnedRoleAssignmentList(roleAssignments);
        authorizationService.verifyAdministratorPrivileges();
    }

    @Test
    void testVerifyRouteDataEditorPrivileges() {
        List<RoleAssignment> roleAssignments = List.of(
                RoleAssignment.builder().withOrganisation(CODESPACE_RUT).withRole(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT).build());
        mockedRoleAssignmentExtractor.setNextReturnedRoleAssignmentList(roleAssignments);
        authorizationService.verifyRouteDataEditorPrivileges(ORG_ID_RUT);
    }

    @Test
    void testVerifyBlockViewerPrivileges() {
        List<RoleAssignment> roleAssignments = List.of(
                RoleAssignment.builder().withOrganisation(CODESPACE_RUT).withRole(AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW).build());
        mockedRoleAssignmentExtractor.setNextReturnedRoleAssignmentList(roleAssignments);
        authorizationService.verifyBlockViewerPrivileges(ORG_ID_RUT);
    }

}
