package com.ribeira.donation.domain;

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
@Table(name = "donation_items")
public class DonationItem extends AssignedIdEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "donation_id", nullable = false, updatable = false)
  private Donation donation;

  @Column(name = "food_id", nullable = false, updatable = false)
  private UUID foodId;

  @Column(nullable = false, updatable = false)
  private BigDecimal quantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private UnitOfMeasure unit;

  @Column(length = 500, updatable = false)
  private String notes;

  @Column(nullable = false, updatable = false)
  private int position;

  protected DonationItem() {}

  DonationItem(
      UUID id,
      Donation donation,
      UUID foodId,
      BigDecimal quantity,
      UnitOfMeasure unit,
      String notes,
      int position) {
    super(id);
    this.donation = donation;
    this.foodId = foodId;
    this.quantity = quantity;
    this.unit = unit;
    this.notes = notes;
    this.position = position;
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

  public String getNotes() {
    return notes;
  }
}
