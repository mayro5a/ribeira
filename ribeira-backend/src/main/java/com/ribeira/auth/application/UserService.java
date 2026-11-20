package com.ribeira.auth.application;

import com.ribeira.auth.domain.Role;
import com.ribeira.auth.domain.RoleRepository;
import com.ribeira.auth.domain.User;
import com.ribeira.auth.domain.UserRepository;
import com.ribeira.auth.domain.UserStatus;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.CommunityDirectory;
import com.ribeira.shared.security.Permission;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  static final int MIN_PASSWORD_LENGTH = 10;

  private final UserRepository users;
  private final RoleRepository roles;
  private final PasswordEncoder passwordEncoder;
  private final AccessControl accessControl;
  private final CommunityDirectory communities;
  private final AuthService authService;
  private final AuditRecorder audit;

  public UserService(
      UserRepository users,
      RoleRepository roles,
      PasswordEncoder passwordEncoder,
      AccessControl accessControl,
      CommunityDirectory communities,
      AuthService authService,
      AuditRecorder audit) {
    this.users = users;
    this.roles = roles;
    this.passwordEncoder = passwordEncoder;
    this.accessControl = accessControl;
    this.communities = communities;
    this.authService = authService;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<UserView> list() {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    return users.findByOrganizationIdOrderByDisplayName(actor.organizationId()).stream()
        .map(UserView::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public UserView get(UUID id) {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    return UserView.of(findInOrganization(actor, id));
  }

  @Transactional
  public UserView create(NewUser command) {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    String email = User.normalizeEmail(command.email());
    if (users.existsByEmail(email)) {
      throw new StateConflictException("EMAIL_IN_USE", "Este e-mail já está em uso.");
    }
    validatePassword(command.password());
    validateAccess(actor.organizationId(), command.organizationRoleIds(), command.communityRoles());
    User user =
        new User(
            UUID.randomUUID(),
            actor.organizationId(),
            email,
            command.displayName(),
            passwordEncoder.encode(command.password()));
    user.assignAccess(command.organizationRoleIds(), command.communityRoles());
    users.save(user);
    record(actor, "USER_CREATED", user.getId(), Map.of());
    return UserView.of(user);
  }

  @Transactional
  public UserView update(UUID id, String displayName, UserStatus status) {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    User user = findInOrganization(actor, id);
    if (actor.userId().equals(id) && status != UserStatus.ACTIVE) {
      throw new BusinessRuleException(
          "SELF_LOCKOUT", "Você não pode bloquear ou desativar o próprio usuário.");
    }
    user.rename(displayName);
    if (user.getStatus() != status) {
      user.changeStatus(status);
      if (status != UserStatus.ACTIVE) {
        authService.revokeAllSessions(id);
      }
      record(actor, "USER_STATUS_CHANGED", id, Map.of("status", status));
    }
    return UserView.of(user);
  }

  @Transactional
  public UserView assignAccess(
      UUID id, Set<UUID> organizationRoleIds, Map<UUID, UUID> communityRoles) {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    User user = findInOrganization(actor, id);
    validateAccess(actor.organizationId(), organizationRoleIds, communityRoles);
    user.assignAccess(organizationRoleIds, communityRoles);
    record(
        actor,
        "USER_ACCESS_CHANGED",
        id,
        Map.of("organizationRoles", organizationRoleIds, "communityRoles", communityRoles));
    return UserView.of(user);
  }

  /** Administrative reset; e-mail based recovery is out of the MVP scope. */
  @Transactional
  public void resetPassword(UUID id, String newPassword) {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    User user = findInOrganization(actor, id);
    validatePassword(newPassword);
    user.changePasswordHash(passwordEncoder.encode(newPassword));
    authService.revokeAllSessions(id);
    record(actor, "USER_PASSWORD_RESET", id, Map.of());
  }

  /** First administrator of a new organization (bootstrap only, no authenticated actor). */
  @Transactional
  public UUID createInitialAdministrator(
      UUID organizationId, String email, String displayName, String password) {
    validatePassword(password);
    Role admin =
        roles
            .findBySystemTrueAndCode("ORG_ADMIN")
            .orElseThrow(() -> new IllegalStateException("ORG_ADMIN role missing"));
    User user =
        new User(
            UUID.randomUUID(),
            organizationId,
            email,
            displayName,
            passwordEncoder.encode(password));
    user.assignAccess(Set.of(admin.getId()), Map.of());
    users.save(user);
    audit.record(
        organizationId, null, null, "BOOTSTRAP_ADMIN_CREATED", "USER", user.getId(), Map.of());
    return user.getId();
  }

  private void validateAccess(
      UUID organizationId, Set<UUID> organizationRoleIds, Map<UUID, UUID> communityRoles) {
    Set<UUID> requestedRoles = new java.util.HashSet<>(organizationRoleIds);
    requestedRoles.addAll(communityRoles.values());
    List<Role> found = roles.findAllById(requestedRoles);
    boolean allUsable =
        found.size() == requestedRoles.size()
            && found.stream().allMatch(r -> r.isUsableBy(organizationId));
    if (!allUsable) {
      throw new BusinessRuleException("INVALID_ROLE", "Papel inexistente ou de outra organização.");
    }
    for (UUID communityId : communityRoles.keySet()) {
      boolean ownCommunity =
          communities.organizationOf(communityId).map(organizationId::equals).orElse(false);
      if (!ownCommunity) {
        throw new ResourceNotFoundException("Comunidade", communityId);
      }
    }
  }

  static void validatePassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new BusinessRuleException(
          "WEAK_PASSWORD", "A senha deve ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres.");
    }
  }

  private User findInOrganization(Actor actor, UUID id) {
    return users
        .findByIdAndOrganizationId(id, actor.organizationId())
        .orElseThrow(() -> new ResourceNotFoundException("Usuário", id));
  }

  private void record(Actor actor, String action, UUID userId, Map<String, ?> details) {
    audit.record(actor.organizationId(), null, actor.userId(), action, "USER", userId, details);
  }

  public record NewUser(
      String email,
      String displayName,
      String password,
      Set<UUID> organizationRoleIds,
      Map<UUID, UUID> communityRoles) {}

  public record UserView(
      UUID id,
      String email,
      String displayName,
      UserStatus status,
      Set<UUID> organizationRoleIds,
      Map<UUID, UUID> communityRoles,
      Instant lastLoginAt) {

    static UserView of(User user) {
      return new UserView(
          user.getId(),
          user.getEmail(),
          user.getDisplayName(),
          user.getStatus(),
          user.getOrganizationRoleIds(),
          user.getCommunityRoles(),
          user.getLastLoginAt());
    }
  }
}
