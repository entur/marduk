package no.rutebanken.marduk.security;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.repository.ProviderRepository;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthorizationService {

    @Autowired
    private ProviderRepository providerRepository;

    @Autowired
    private RoleAssignmentExtractor roleAssignmentExtractor;


    public void verifyAtLeastOne(AuthorizationClaim... claims) {
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
