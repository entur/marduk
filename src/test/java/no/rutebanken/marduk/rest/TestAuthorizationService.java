package no.rutebanken.marduk.rest;

import org.rutebanken.helper.organisation.authorization.AuthorizationService;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Test implementation of the authorization service that checks that the user is authenticated.
 */
class TestAuthorizationService implements AuthorizationService<Long> {

    @Override
    public boolean isRouteDataAdmin() {
        checkSecurityContext();
        return true;
    }


    @Override
    public boolean isOrganisationAdmin() {
        checkSecurityContext();
        return true;
    }

    @Override
    public boolean canViewAllOrganisationData() {
        checkSecurityContext();
        return true;
    }

    @Override
    public boolean canViewRouteData(Long providerId) {
        checkSecurityContext();
        return true;
    }

    @Override
    public boolean canEditRouteData(Long providerId) {
        checkSecurityContext();
        return true;
    }

    @Override
    public boolean canViewBlockData(Long providerId) {
        checkSecurityContext();
        return true;
    }

    @Override
    public boolean canViewRoleAssignments() {
        checkSecurityContext();
        return true;
    }

    private void checkSecurityContext() {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            throw new IllegalStateException("No security context");
        }
    }
}
