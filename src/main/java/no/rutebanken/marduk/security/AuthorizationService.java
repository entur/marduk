/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.security;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AuthorizationService {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private RoleAssignmentExtractor roleAssignmentExtractor;


    @Value("${authorization.enabled:true}")
    protected boolean authorizationEnabled;

    public void verifyAdministratorPrivileges() {
        verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN));
    }

    /**
     * Users can edit route data if they have administrator privileges,
     * or if it has editor privileges for this provider.
     * @param providerId
     */    public void verifyRouteDataEditorPrivileges(Long providerId) {
        verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN),
                new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT, providerId));
    }

    /**
     * Users can download NeTEx blocks data if they have administrator privileges,
     * or if they have editor privileges for this provider
     * or if they have NeTEx blocks viewer privileges for this provider.
     * @param providerId
     */
    public void verifyBlockViewerPrivileges(Long providerId) {
        verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN),
                new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT, providerId),
                new AuthorizationClaim(AuthorizationConstants.ROLE_NETEX_BLOCKS_DATA_VIEW, providerId));
    }

    protected void verifyAtLeastOne(AuthorizationClaim... claims) {
        if (!authorizationEnabled){
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<RoleAssignment> roleAssignments = roleAssignmentExtractor.getRoleAssignmentsForUser(authentication);

        boolean authorized = false;
        for (AuthorizationClaim claim : claims) {
            if (claim.getProviderId() == null) {
                authorized |= roleAssignments.stream().anyMatch(ra -> claim.getRequiredRole().equals(ra.getRole()));
            } else {
                authorized |= hasRoleForProvider(roleAssignments, claim);
            }
        }

        if (!authorized) {
            throw new AccessDeniedException("Insufficient privileges for operation");
        }

    }


    private boolean hasRoleForProvider(List<RoleAssignment> roleAssignments, AuthorizationClaim claim) {

        Provider provider = providerRepository.getProvider(claim.getProviderId());
        if (provider == null) {
            return false;
        }

        return roleAssignments.stream()
                       .filter(ra -> claim.getRequiredRole().equals(ra.r)).anyMatch(ra -> provider.chouetteInfo.xmlns.equals(ra.o));

    }

}
