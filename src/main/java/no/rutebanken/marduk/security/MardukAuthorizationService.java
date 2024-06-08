package no.rutebanken.marduk.security;

/**
 *  Service that verifies the privileges of the API clients.
 */
public interface MardukAuthorizationService {


    /**
     * Verify that the user has full administrator privileges.
     */
    void verifyAdministratorPrivileges();

    /**
     * Verify that the user can edit route data for a given provider.
     * Users can edit route data if they have administrator privileges,
     * or if it has editor privileges for this provider.
     */
    void verifyRouteDataEditorPrivileges(Long providerId);

    /**
     * Verify that the user can read block data for a given provider.
     * Users can download NeTEx blocks data if they have administrator privileges,
     * or if they have editor privileges for this provider
     * or if they have NeTEx blocks viewer privileges for this provider.
     */
    void verifyBlockViewerPrivileges(Long providerId);
}
