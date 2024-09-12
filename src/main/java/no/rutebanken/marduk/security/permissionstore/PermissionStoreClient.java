package no.rutebanken.marduk.security.permissionstore;

import java.util.Collection;
import java.util.Set;

public interface PermissionStoreClient {
  /**
   * Return the permissions for a given user.
   */
  Collection<PermissionStorePermission> getPermissions(
    String subject,
    String authority,
    int application
  );

  /**
   * Return the application id for a given application name.
   */
  int getApplicationId(PermissionStoreApplication permissionStoreApplication);

  /**
   * Register the responsibility types used by the application (should be called at application startup)
   */
  void registerPermissions(Set<PermissionStoreResponsibilityType> permissionStoreResponsibilityTypes, int applicationId);
}
