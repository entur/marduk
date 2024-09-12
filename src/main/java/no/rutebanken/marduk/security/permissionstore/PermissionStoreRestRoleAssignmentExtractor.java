package no.rutebanken.marduk.security.permissionstore;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.rutebanken.helper.organisation.RoleAssignment;
import org.rutebanken.helper.organisation.RoleAssignmentExtractor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class PermissionStoreRestRoleAssignmentExtractor
  implements RoleAssignmentExtractor {

  private static final int UNINITIALIZED_ID = -1;
  private static final String ROR_OPERATION_PREFIX = "ror-";
  private static final String ROR_RESPONSIBILITY_TYPE_SCOPE = "scope";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final PermissionStoreClient permissionStoreClient;
  private final String application;
  private final Map<String, List<RoleAssignment>> permissionCache;

  private final Set<String> operations;

  private int applicationId = UNINITIALIZED_ID;

  public PermissionStoreRestRoleAssignmentExtractor(
          PermissionStoreClient permissionStoreClient,
          String application, Set<String> operations
  ) {
    this.permissionStoreClient = permissionStoreClient;
    this.application = application;
      this.operations = operations;
      this.permissionCache = Collections.synchronizedMap(new HashMap<>());
  }

  @Override
  public List<RoleAssignment> getRoleAssignmentsForUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    return getRoleAssignmentsForUser(auth);
  }

  // 1272
  @Override
  public List<RoleAssignment> getRoleAssignmentsForUser(Authentication authentication) {
    Jwt principal = (Jwt) authentication.getPrincipal();
    return permissionCache.computeIfAbsent(
      principal.getSubject(),
      subject ->
        permissionStoreClient
          .getPermissions(subject, "partner", getApplicationId())
          .stream()
          .filter(permission -> permission.operation().startsWith(ROR_OPERATION_PREFIX))
          .filter(permission ->
            ROR_RESPONSIBILITY_TYPE_SCOPE.equals(permission.responsibilityType())
          )
          .map(permission -> parse(permission.responsibilityKey()))
          .toList()
    );
  }

  private synchronized int getApplicationId() {
    if (applicationId == UNINITIALIZED_ID) {
      applicationId =
        permissionStoreClient.getApplicationId(
          new PermissionStoreApplication(application, 0)
        );

      Set<PermissionStoreResponsibilityType> permissionStorePermissionSet = operations.stream()
              .map(operation -> new PermissionStoreResponsibilityType(ROR_RESPONSIBILITY_TYPE_SCOPE, ROR_OPERATION_PREFIX + operation, "endre"))
              .collect(Collectors.toUnmodifiableSet());

      permissionStoreClient.registerPermissions(permissionStorePermissionSet, applicationId);

    }
    return applicationId;
  }

  private static RoleAssignment parse(String roleAssignment) {
    try {
      return MAPPER.readValue(roleAssignment, RoleAssignment.class);
    } catch (IOException e) {
      throw new IllegalArgumentException(
        "Exception while parsing role assignments from JSON",
        e
      );
    }
  }
}
