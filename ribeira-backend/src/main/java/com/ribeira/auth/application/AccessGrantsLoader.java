package com.ribeira.auth.application;

import com.ribeira.auth.domain.Role;
import com.ribeira.auth.domain.RoleRepository;
import com.ribeira.auth.domain.User;
import com.ribeira.auth.domain.UserRepository;
import com.ribeira.shared.security.AccessGrants;
import com.ribeira.shared.security.AccessGrantsProvider;
import com.ribeira.shared.security.Permission;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AccessGrantsLoader implements AccessGrantsProvider {

  private final UserRepository users;
  private final RoleRepository roles;

  AccessGrantsLoader(UserRepository users, RoleRepository roles) {
    this.users = users;
    this.roles = roles;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AccessGrants> load(UUID userId) {
    return users.findById(userId).map(this::grantsOf);
  }

  AccessGrants grantsOf(User user) {
    Set<UUID> roleIds = new HashSet<>(user.getOrganizationRoleIds());
    roleIds.addAll(user.getCommunityRoles().values());
    Map<UUID, Role> rolesById =
        roles.findAllById(roleIds).stream()
            // A role from another organization never grants anything, even if misassigned.
            .filter(role -> role.isUsableBy(user.getOrganizationId()))
            .collect(Collectors.toMap(Role::getId, Function.identity()));

    Set<Permission> organizationPermissions = EnumSet.noneOf(Permission.class);
    user.getOrganizationRoleIds().stream()
        .map(rolesById::get)
        .filter(java.util.Objects::nonNull)
        .forEach(role -> organizationPermissions.addAll(role.getPermissions()));

    Map<UUID, Set<Permission>> communityPermissions = new HashMap<>();
    user.getCommunityRoles()
        .forEach(
            (communityId, roleId) -> {
              Role role = rolesById.get(roleId);
              if (role != null) {
                communityPermissions.put(communityId, role.getPermissions());
              }
            });
    return new AccessGrants(
        user.getId(),
        user.getOrganizationId(),
        user.isActive(),
        organizationPermissions,
        communityPermissions);
  }
}
