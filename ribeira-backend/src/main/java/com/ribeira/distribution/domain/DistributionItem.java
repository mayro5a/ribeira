package com.ribeira.distribution.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.domain.UnitOfMeasure;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "distribution_items")
public class DistributionItem extends AssignedIdEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "distribution_id", nullable = false, updatable = false)
  private Distribution distribution;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "food_id", nullable = false, updatable = false)
  private UUID foodId;

  @Column(nullable = false, updatable = false)
  private BigDecimal quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private UnitOfMeasure unit;

  /** The ledger entry that removed this quantity (one-to-one, RN04). */
  @Column(name = "movement_id", nullable = false, updatable = false)
  private UUID movementId;

  @Column(nullable = false, updatable = false)
  private int position;

  protected DistributionItem() {}

  DistributionItem(
      UUID id,
      Distribution distribution,
      UUID batchId,
      UUID foodId,
      BigDecimal quantity,
      UnitOfMeasure unit,
      UUID movementId,
      int position) {
    super(id);
    this.distribution = distribution;
    this.batchId = batchId;
    this.foodId = foodId;
    this.quantity = quantity;
    this.unit = unit;
    this.movementId = movementId;
    this.position = position;
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

  public UUID getMovementId() {
    return movementId;
  }
}
