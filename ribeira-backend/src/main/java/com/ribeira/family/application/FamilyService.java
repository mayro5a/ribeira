package com.ribeira.family.application;

import com.ribeira.family.domain.Family;
import com.ribeira.family.domain.Family.FamilyDetails;
import com.ribeira.family.domain.Family.FamilyStatus;
import com.ribeira.family.domain.FamilyRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Family data is restricted to users holding FAMILY_READ in the family's community. */
@Service
public class FamilyService {

  private final FamilyRepository families;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;

  public FamilyService(
      FamilyRepository families,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes) {
    this.families = families;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
  }

  @Transactional(readOnly = true)
  public List<FamilyView> list(UUID communityId) {
    accessControl.requireCommunity(communityId, Permission.FAMILY_READ);
    return families.findByCommunityIdOrderByCode(communityId).stream().map(FamilyView::of).toList();
  }

  @Transactional(readOnly = true)
  public FamilyView get(UUID id) {
    Family family = findVisible(id);
    accessControl.requireCommunity(family.getCommunityId(), Permission.FAMILY_READ);
    return FamilyView.of(family);
  }

  @Transactional
  public FamilyView create(UUID communityId, FamilyDetails details) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.FAMILY_WRITE);
    UUID id = UUID.randomUUID();
    ensureUniqueCode(communityId, details.code(), id);
    Family family = families.save(new Family(id, actor.organizationId(), communityId, details));
    publish(actor, family, "FAMILY_REGISTERED");
    return FamilyView.of(family);
  }

  @Transactional
  public FamilyView update(UUID id, FamilyDetails details) {
    Family family = findVisible(id);
    Actor actor = accessControl.requireCommunity(family.getCommunityId(), Permission.FAMILY_WRITE);
    ensureUniqueCode(family.getCommunityId(), details.code(), id);
    family.update(details);
    publish(actor, family, "FAMILY_UPDATED");
    return FamilyView.of(family);
  }

  /** For distributions: every family must be active and belong to the community. */
  @Transactional(readOnly = true)
  public void requireActiveInCommunity(UUID organizationId, UUID communityId, Set<UUID> ids) {
    Map<UUID, Family> found =
        families.findByOrganizationIdAndIdIn(organizationId, ids).stream()
            .filter(f -> f.getCommunityId().equals(communityId))
            .collect(Collectors.toMap(Family::getId, f -> f));
    for (UUID id : ids) {
      Family family = found.get(id);
      if (family == null) {
        throw new ResourceNotFoundException("Família", id);
      }
      if (!family.isActive()) {
        throw new BusinessRuleException(
            "FAMILY_INACTIVE", "A família " + family.getCode() + " está inativa.");
      }
    }
  }

  @Transactional(readOnly = true)
  public Map<UUID, FamilyView> viewsOf(UUID organizationId, Collection<UUID> ids) {
    return families.findByOrganizationIdAndIdIn(organizationId, ids).stream()
        .collect(Collectors.toMap(Family::getId, FamilyView::of));
  }

  private void ensureUniqueCode(UUID communityId, String code, UUID id) {
    if (families.existsByCommunityIdAndCodeIgnoreCaseAndIdNot(communityId, code.trim(), id)) {
      throw new StateConflictException(
          "FAMILY_CODE_IN_USE", "Já existe uma família com este código na comunidade.");
    }
  }

  private Family findVisible(UUID id) {
    Actor actor = accessControl.actor();
    return families
        .findById(id)
        .filter(f -> f.getOrganizationId().equals(actor.organizationId()))
        .orElseThrow(() -> new ResourceNotFoundException("Família", id));
  }

  /** Audit records only the code-free ID; no family data goes into logs. */
  private void publish(Actor actor, Family family, String action) {
    audit.record(
        actor.organizationId(),
        family.getCommunityId(),
        actor.userId(),
        action,
        "FAMILY",
        family.getId(),
        Map.of());
    changes.record(
        actor.organizationId(), family.getCommunityId(), SyncEntityType.FAMILY, family.getId());
  }

  public record FamilyView(
      UUID id, UUID communityId, String code, int memberCount, FamilyStatus status, String notes) {
    static FamilyView of(Family f) {
      return new FamilyView(
          f.getId(),
          f.getCommunityId(),
          f.getCode(),
          f.getMemberCount(),
          f.getStatus(),
          f.getNotes());
    }
  }
}
