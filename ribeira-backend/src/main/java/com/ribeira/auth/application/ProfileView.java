package com.ribeira.auth.application;

import com.ribeira.shared.security.AccessGrants;
import com.ribeira.shared.security.Permission;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * What the device stores for offline use (ADR-0005): identity and effective permissions per
 * community. Never contains password hashes or tokens.
 */
public record ProfileView(
    UUID userId,
    UUID organizationId,
    String displayName,
    String email,
    Set<Permission> organizationPermissions,
    List<CommunityPermissions> communities) {

  public record CommunityPermissions(UUID communityId, Set<Permission> permissions) {}

  static ProfileView of(
      UUID userId,
      String displayName,
      String email,
      AccessGrants grants,
      List<UUID> organizationCommunityIds) {
    Stream<UUID> visible =
        grants.organizationPermissions().isEmpty()
            ? grants.communityPermissions().keySet().stream()
            : organizationCommunityIds.stream();
    List<CommunityPermissions> communities =
        visible
            .distinct()
            .map(id -> new CommunityPermissions(id, grants.permissionsIn(id)))
            .filter(c -> !c.permissions().isEmpty())
            .sorted(Comparator.comparing(CommunityPermissions::communityId))
            .toList();
    return new ProfileView(
        userId,
        grants.organizationId(),
        displayName,
        email,
        grants.organizationPermissions(),
        communities);
  }
}
