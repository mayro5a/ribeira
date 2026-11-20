package com.ribeira.auth.application;

import com.ribeira.auth.domain.Role;
import com.ribeira.auth.domain.RoleRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lets an organization tailor access beyond the system roles. */
@Service
public class RoleService {

  private final RoleRepository roles;
  private final AccessControl accessControl;
  private final AuditRecorder audit;

  public RoleService(RoleRepository roles, AccessControl accessControl, AuditRecorder audit) {
    this.roles = roles;
    this.accessControl = accessControl;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public List<RoleView> list() {
    Actor actor = accessControl.actor();
    return roles.findUsableBy(actor.organizationId()).stream().map(RoleView::of).toList();
  }

  @Transactional
  public RoleView create(String code, String name, String description, Set<Permission> grants) {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    if (roles.existsByOrganizationIdAndCode(actor.organizationId(), code)) {
      throw new StateConflictException("ROLE_CODE_IN_USE", "Já existe um papel com este código.");
    }
    Role role = roles.save(Role.custom(actor.organizationId(), code, name, description, grants));
    audit.record(
        actor.organizationId(),
        null,
        actor.userId(),
        "ROLE_CREATED",
        "ROLE",
        role.getId(),
        Map.of("permissions", grants));
    return RoleView.of(role);
  }

  @Transactional
  public RoleView update(UUID id, String name, String description, Set<Permission> grants) {
    Actor actor = accessControl.requireOrganization(Permission.USER_MANAGE);
    Role role =
        roles
            .findById(id)
            .filter(r -> r.isUsableBy(actor.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Papel", id));
    if (role.isSystem()) {
      throw new BusinessRuleException(
          "SYSTEM_ROLE_IMMUTABLE", "Papéis padrão do sistema não podem ser alterados.");
    }
    role.update(name, description, grants);
    audit.record(
        actor.organizationId(),
        null,
        actor.userId(),
        "ROLE_UPDATED",
        "ROLE",
        id,
        Map.of("permissions", grants));
    return RoleView.of(role);
  }

  public record RoleView(
      UUID id,
      String code,
      String name,
      String description,
      boolean system,
      Set<Permission> permissions) {

    static RoleView of(Role role) {
      return new RoleView(
          role.getId(),
          role.getCode(),
          role.getName(),
          role.getDescription(),
          role.isSystem(),
          role.getPermissions());
    }
  }
}
