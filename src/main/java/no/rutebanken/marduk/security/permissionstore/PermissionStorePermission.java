package no.rutebanken.marduk.security.permissionstore;

public record PermissionStorePermission(
  String operation,
  String responsibilityType,
  String responsibilityKey
) {}
