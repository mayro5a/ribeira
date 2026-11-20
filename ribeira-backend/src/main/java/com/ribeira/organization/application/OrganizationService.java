package com.ribeira.organization.application;

import com.ribeira.organization.domain.Organization;
import com.ribeira.organization.domain.OrganizationRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final OrganizationRepository organizations;
  private final AccessControl accessControl;
  private final AuditRecorder audit;

  public OrganizationService(
      OrganizationRepository organizations, AccessControl accessControl, AuditRecorder audit) {
    this.organizations = organizations;
    this.accessControl = accessControl;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public OrganizationView get(UUID id) {
    Actor actor = accessControl.actor();
    return OrganizationView.of(findOwn(actor, id));
  }

  @Transactional
  public OrganizationView rename(UUID id, String name) {
    Actor actor = accessControl.requireOrganization(Permission.ORGANIZATION_MANAGE);
    Organization organization = findOwn(actor, id);
    organization.rename(name);
    audit.record(id, null, actor.userId(), "ORGANIZATION_UPDATED", "ORGANIZATION", id, Map.of());
    return OrganizationView.of(organization);
  }

  /** Bootstrap only: creates an organization without an authenticated actor. */
  @Transactional
  public UUID createOrganization(String name) {
    Organization organization = organizations.save(new Organization(UUID.randomUUID(), name));
    return organization.getId();
  }

  @Transactional(readOnly = true)
  public boolean anyExists() {
    return organizations.count() > 0;
  }

  /** Users only ever see their own organization; others look nonexistent. */
  private Organization findOwn(Actor actor, UUID id) {
    if (!actor.organizationId().equals(id)) {
      throw new ResourceNotFoundException("Organização", id);
    }
    return organizations
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Organização", id));
  }

  public record OrganizationView(UUID id, String name, boolean active) {
    static OrganizationView of(Organization o) {
      return new OrganizationView(o.getId(), o.getName(), o.isActive());
    }
  }
}
