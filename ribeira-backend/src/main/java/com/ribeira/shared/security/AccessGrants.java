package com.ribeira.shared.security;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What a user may do, loaded fresh per request so that blocking a user or changing a role takes
 * effect immediately (ADR-0005).
 *
 * @param organizationPermissions granted by organization-wide roles; apply to every community of
 *     the organization
 * @param communityPermissions granted by the user's role in each specific community
 */
public record AccessGrants(
    UUID userId,
    UUID organizationId,
    boolean active,
    Set<Permission> organizationPermissions,
    Map<UUID, Set<Permission>> communityPermissions) {

  public AccessGrants {
    organizationPermissions = Set.copyOf(organizationPermissions);
    communityPermissions = Map.copyOf(communityPermissions);
  }

  /** Effective permissions in a community that is known to belong to the organization. */
  public Set<Permission> permissionsIn(UUID communityId) {
    EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
    result.addAll(organizationPermissions);
    result.addAll(communityPermissions.getOrDefault(communityId, Collections.emptySet()));
    return result;
  }

  /**
   * Organization-scoped catalogs (foods, donors) are usable by anyone holding the permission in an
   * organization role or in any community role.
   */
  public boolean hasAnywhere(Permission permission) {
    return organizationPermissions.contains(permission)
        || communityPermissions.values().stream().anyMatch(p -> p.contains(permission));
  }
}
