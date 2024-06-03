package no.rutebanken.marduk.security;

import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

import java.util.Collection;
import java.util.List;

import static no.rutebanken.marduk.TestConstants.PROVIDER_ID_RUT;


class DefaultAuthorizationServiceTest {

    private static final String CODESPACE_RB = "RB";
    private static final String CODESPACE_RUT = "RUT";


    @Test
    void testVerifyAtLeastOne() {
        List<RoleAssignment> roleAssignments = List.of(
                RoleAssignment.builder().withOrganisation(CODESPACE_RB).withRole(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN).build());
        DefaultAuthorizationService authorizationService = new DefaultAuthorizationService(providerRepository(), rolAssignmentExtractor(roleAssignments), true);
        AuthorizationClaim authorizationClaim = new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN);
        Assertions.assertDoesNotThrow(() -> authorizationService.verifyAtLeastOne(authorizationClaim));
    }

    @Test
    void testVerifyAdministratorPrivileges() {
        List<RoleAssignment> roleAssignments = List.of(
                RoleAssignment.builder().withOrganisation(CODESPACE_RB).withRole(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN).build());
        DefaultAuthorizationService authorizationService = new DefaultAuthorizationService(providerRepository(), rolAssignmentExtractor(roleAssignments), true);
        authorizationService.verifyAdministratorPrivileges();
    }

    @Test
    void testVerifyAdministratorPrivilegesMissing() {
        List<RoleAssignment> roleAssignments = List.of();
        DefaultAuthorizationService authorizationService = new DefaultAuthorizationService(providerRepository(), rolAssignmentExtractor(roleAssignments), true);
        Assertions.assertThrows(AccessDeniedException.class, authorizationService::verifyAdministratorPrivileges);
    }

    @Test
    void testVerifyAdministratorPrivilegesMissingAndAuthorizationDisabled() {
        List<RoleAssignment> roleAssignments = List.of();
        DefaultAuthorizationService authorizationService = new DefaultAuthorizationService(providerRepository(), rolAssignmentExtractor(roleAssignments), false);
        Assertions.assertDoesNotThrow(authorizationService::verifyAdministratorPrivileges);
    }


    @Test
    void testVerifyRouteDataEditorPrivileges() {
        List<RoleAssignment> roleAssignments = List.of(
                RoleAssignment.builder().withOrganisation(CODESPACE_RUT).withRole(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT).build());
        DefaultAuthorizationService authorizationService = new DefaultAuthorizationService(providerRepository(), rolAssignmentExtractor(roleAssignments), true);
        authorizationService.verifyRouteDataEditorPrivileges(PROVIDER_ID_RUT);
    }

    @Test
    void testVerifyBlockViewerPrivileges() {
        List<RoleAssignment> roleAssignments = List.of(
                RoleAssignment.builder().withOrganisation(CODESPACE_RUT).withRole(AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW).build());
        DefaultAuthorizationService authorizationService = new DefaultAuthorizationService(providerRepository(), rolAssignmentExtractor(roleAssignments), true);
        authorizationService.verifyBlockViewerPrivileges(PROVIDER_ID_RUT);
    }


    private RoleAssignmentExtractor rolAssignmentExtractor(List<RoleAssignment> roleAssignments) {
        return new RoleAssignmentExtractor() {

            @Override
            public List<RoleAssignment> getRoleAssignmentsForUser() {
                return roleAssignments;
            }

            @Override
            public List<RoleAssignment> getRoleAssignmentsForUser(Authentication authentication) {
                return getRoleAssignmentsForUser();
            }
        };
    }

    private ProviderRepository providerRepository() {
        return new ProviderRepository() {
            @Override
            public Collection<Provider> getProviders() {
                return List.of();
            }

            @Override
            public Provider getProvider(Long id) {
                if (PROVIDER_ID_RUT == id) {
                    Provider provider = new Provider();
                    provider.setId(PROVIDER_ID_RUT);
                    ChouetteInfo chouetteInfo = new ChouetteInfo();
                    chouetteInfo.setReferential(CODESPACE_RUT);
                    provider.setChouetteInfo(chouetteInfo);
                    return provider;

                }
                return null;
            }

            @Override
            public String getReferential(Long id) {
                return "";
            }

            @Override
            public Long getProviderId(String referential) {
                return 0L;
            }
        };
    }

}
