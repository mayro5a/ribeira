package com.ribeira.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Threshold below which a community's stock of a food raises a LOW_STOCK alert. */
@Entity
@Table(name = "stock_minimums")
public class StockMinimum {

  @EmbeddedId private Key id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "minimum_quantity", nullable = false)
  private BigDecimal minimumQuantity;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected StockMinimum() {}

  public StockMinimum(
      UUID organizationId, UUID communityId, UUID foodId, BigDecimal minimum, Instant now) {
    this.id = new Key(communityId, foodId);
    this.organizationId = organizationId;
    change(minimum, now);
  }

  public void change(BigDecimal minimum, Instant now) {
    this.minimumQuantity = minimum;
    this.updatedAt = now;
  }

  public UUID getCommunityId() {
    return id.communityId;
  }

  public UUID getFoodId() {
    return id.foodId;
  }

  public BigDecimal getMinimumQuantity() {
    return minimumQuantity;
  }

  @Embeddable
  public static class Key implements Serializable {
    @Column(name = "community_id")
    private UUID communityId;

    @Column(name = "food_id")
    private UUID foodId;

    protected Key() {}

    public Key(UUID communityId, UUID foodId) {
      this.communityId = communityId;
      this.foodId = foodId;
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Key key
          && communityId.equals(key.communityId)
          && foodId.equals(key.foodId);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(communityId, foodId);
    }
  }
}
