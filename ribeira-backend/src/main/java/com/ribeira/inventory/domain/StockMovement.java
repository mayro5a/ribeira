package com.ribeira.inventory.domain;

import com.ribeira.inventory.domain.InventoryEnums.MovementSource;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Ledger entry. Append-only: there is no setter, and a database trigger rejects updates. */
@Entity
@Immutable
@Table(name = "stock_movements")
public class StockMovement extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false)
  private UUID communityId;

  @Column(name = "batch_id", nullable = false)
  private UUID batchId;

  @Column(name = "food_id", nullable = false)
  private UUID foodId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private MovementType type;

  @Column(name = "quantity_delta", nullable = false, precision = 14, scale = 3)
  private BigDecimal quantityDelta;

  @Column(name = "balance_after", nullable = false, precision = 14, scale = 3)
  private BigDecimal balanceAfter;

  @Enumerated(EnumType.STRING)
  @Column(name = "source_type", nullable = false, length = 24)
  private MovementSource sourceType;

  @Column(name = "source_id")
  private UUID sourceId;

  @Column(length = 500)
  private String justification;

  @Column(name = "performed_by", nullable = false)
  private UUID performedBy;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  protected StockMovement() {}

  StockMovement(
      UUID id,
      FoodBatch batch,
      MovementType type,
      BigDecimal quantityDelta,
      MovementSource sourceType,
      UUID sourceId,
      String justification,
      UUID performedBy,
      Instant occurredAt,
      Instant recordedAt) {
    super(id);
    this.organizationId = batch.getOrganizationId();
    this.communityId = batch.getCommunityId();
    this.batchId = batch.getId();
    this.foodId = batch.getFoodId();
    this.type = type;
    this.quantityDelta = quantityDelta;
    this.balanceAfter = batch.getAvailableQuantity();
    this.sourceType = sourceType;
    this.sourceId = sourceId;
    this.justification = justification;
    this.performedBy = performedBy;
    this.occurredAt = occurredAt;
    this.recordedAt = recordedAt;
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

  public MovementType getType() {
    return type;
  }

  public BigDecimal getQuantityDelta() {
    return quantityDelta;
  }

  public BigDecimal getBalanceAfter() {
    return balanceAfter;
  }

  public MovementSource getSourceType() {
    return sourceType;
  }

  public UUID getSourceId() {
    return sourceId;
  }

  public String getJustification() {
    return justification;
  }

  public UUID getPerformedBy() {
    return performedBy;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
