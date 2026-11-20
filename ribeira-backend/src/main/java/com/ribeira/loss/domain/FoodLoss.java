package com.ribeira.loss.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.domain.UnitOfMeasure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A recorded loss. The quantity and batch never change after registration (its ledger movement is
 * immutable); only the investigation status evolves.
 */
@Entity
@Table(name = "food_losses")
public class FoodLoss extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "food_id", nullable = false, updatable = false)
  private UUID foodId;

  @Column(nullable = false, updatable = false)
  private BigDecimal quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private UnitOfMeasure unit;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, updatable = false)
  private LossReason reason;

  @Column(length = 1000, updatable = false)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private LossStatus status = LossStatus.REGISTERED;

  @Column(name = "movement_id", nullable = false, updatable = false)
  private UUID movementId;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected FoodLoss() {}

  public FoodLoss(
      UUID id,
      UUID organizationId,
      UUID communityId,
      UUID batchId,
      UUID foodId,
      BigDecimal quantity,
      UnitOfMeasure unit,
      Instant occurredAt,
      LossReason reason,
      String description,
      UUID movementId,
      UUID recordedBy) {
    super(id);
    this.organizationId = organizationId;
    this.communityId = communityId;
    this.batchId = batchId;
    this.foodId = foodId;
    this.quantity = quantity;
    this.unit = unit;
    this.occurredAt = occurredAt;
    this.reason = reason;
    this.description = description;
    this.movementId = movementId;
    this.recordedBy = recordedBy;
  }

  public void changeStatus(LossStatus newStatus) {
    this.status = newStatus;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getBatchId() {
    return batchId;
  }

  public UUID getFoodId() {
    return foodId;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public UnitOfMeasure getUnit() {
    return unit;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public LossReason getReason() {
    return reason;
  }

  public String getDescription() {
    return description;
  }

  public LossStatus getStatus() {
    return status;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public enum LossReason {
    HUMIDITY,
    HEAT_OR_SUN_EXPOSURE,
    DAMAGED_PACKAGING,
    EXPIRATION,
    PESTS,
    TRANSPORT,
    SUSPECTED_CONTAMINATION,
    OTHER,
    UNIDENTIFIED
  }

  public enum LossStatus {
    REGISTERED,
    UNDER_REVIEW,
    CLOSED
  }
}
