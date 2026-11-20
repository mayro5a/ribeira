package com.ribeira.family.domain;

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

/**
 * A beneficiary household, identified only by a community code (LGPD data minimization). Names,
 * documents and addresses are deliberately not modeled.
 */
@Entity
@Table(name = "families")
public class Family extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(nullable = false, length = 40)
  private String code;

  @Column(name = "member_count", nullable = false)
  private int memberCount;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private FamilyStatus status;

  @Column(length = 500)
  private String notes;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Family() {}

  public Family(UUID id, UUID organizationId, UUID communityId, FamilyDetails details) {
    super(id);
    this.organizationId = organizationId;
    this.communityId = communityId;
    update(details);
  }

  public void update(FamilyDetails details) {
    this.code = details.code().trim();
    this.memberCount = details.memberCount();
    this.status = details.status();
    this.notes = details.notes();
  }

  public boolean isActive() {
    return status == FamilyStatus.ACTIVE;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public String getCode() {
    return code;
  }

  public int getMemberCount() {
    return memberCount;
  }

  public FamilyStatus getStatus() {
    return status;
  }

  public String getNotes() {
    return notes;
  }

  public enum FamilyStatus {
    ACTIVE,
    INACTIVE
  }

  public record FamilyDetails(String code, int memberCount, FamilyStatus status, String notes) {}
}
