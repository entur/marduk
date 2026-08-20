package no.rutebanken.marduk.security;

import org.rutebanken.helper.organisation.authorization.AuthorizationService;

/**
 * Reads the privileges of the authenticated user off the Spring Security context.
 *
 * <p>Used to also rebuild that context from the request's bearer token, because platform-http ran REST
 * routes on a worker thread that had none. The API is Spring MVC now and every check runs on the request
 * thread, where the security filter chain has already put the authentication in place.
 */
public class DefaultMardukAuthorizationService implements MardukAuthorizationService {

    private final AuthorizationService<Long> authorizationService;

    public DefaultMardukAuthorizationService(AuthorizationService<Long> authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public void verifyAdministratorPrivileges() {
        authorizationService.validateRouteDataAdmin();
    }

    @Override
    public void verifyRouteDataEditorPrivileges(Long providerId) {
        authorizationService.validateEditRouteData(providerId);
    }

    @Override
    public void verifyBlockViewerPrivileges(Long providerId) {
        authorizationService.validateViewBlockData(providerId);
    }
}
