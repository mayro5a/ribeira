package com.ribeira.donor.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** Source of donations. Contact data is intentionally not modeled (data minimization). */
@Entity
@Table(name = "donors")
public class Donor extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DonorType type;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 500)
  private String notes;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Donor() {}

  public Donor(UUID id, UUID organizationId, DonorType type, String name, String notes) {
    super(id);
    this.organizationId = organizationId;
    update(type, name, notes, true);
  }

  public void update(DonorType newType, String newName, String newNotes, boolean isActive) {
    this.type = newType;
    this.name = newName.trim();
    this.notes = newNotes;
    this.active = isActive;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public DonorType getType() {
    return type;
  }

  public String getName() {
    return name;
  }

  public String getNotes() {
    return notes;
  }

  public boolean isActive() {
    return active;
  }

  public enum DonorType {
    INDIVIDUAL,
    ORGANIZATION
  }
}
