package com.ribeira.donor.application;

import com.ribeira.donor.domain.Donor;
import com.ribeira.donor.domain.Donor.DonorType;
import com.ribeira.donor.domain.DonorRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DonorService {

  private final DonorRepository donors;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;

  public DonorService(
      DonorRepository donors,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes) {
    this.donors = donors;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
  }

  @Transactional(readOnly = true)
  public List<DonorView> list() {
    Actor actor = accessControl.member();
    return donors.findByOrganizationIdOrderByName(actor.organizationId()).stream()
        .map(DonorView::of)
        .toList();
  }

  @Transactional
  public DonorView create(DonorType type, String name, String notes) {
    Actor actor = accessControl.requireAnywhere(Permission.DONOR_MANAGE);
    Donor donor =
        donors.save(new Donor(UUID.randomUUID(), actor.organizationId(), type, name, notes));
    publish(actor, donor, "DONOR_CREATED");
    return DonorView.of(donor);
  }

  @Transactional
  public DonorView update(UUID id, DonorType type, String name, String notes, boolean active) {
    Actor actor = accessControl.requireAnywhere(Permission.DONOR_MANAGE);
    Donor donor =
        donors
            .findByIdAndOrganizationId(id, actor.organizationId())
            .orElseThrow(() -> new ResourceNotFoundException("Doador", id));
    donor.update(type, name, notes, active);
    publish(actor, donor, "DONOR_UPDATED");
    return DonorView.of(donor);
  }

  /** For the donation module: the donor must exist in the organization and be active. */
  @Transactional(readOnly = true)
  public void requireActive(UUID organizationId, UUID donorId) {
    Donor donor =
        donors
            .findByIdAndOrganizationId(donorId, organizationId)
            .orElseThrow(() -> new ResourceNotFoundException("Doador", donorId));
    if (!donor.isActive()) {
      throw new BusinessRuleException("DONOR_INACTIVE", "O doador informado está inativo.");
    }
  }

  private void publish(Actor actor, Donor donor, String action) {
    audit.record(
        actor.organizationId(), null, actor.userId(), action, "DONOR", donor.getId(), Map.of());
    changes.record(actor.organizationId(), null, SyncEntityType.DONOR, donor.getId());
  }

  public record DonorView(UUID id, DonorType type, String name, String notes, boolean active) {
    static DonorView of(Donor donor) {
      return new DonorView(
          donor.getId(), donor.getType(), donor.getName(), donor.getNotes(), donor.isActive());
    }
  }
}
