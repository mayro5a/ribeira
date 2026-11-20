package com.ribeira.food.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.domain.UnitOfMeasure;
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
 * Catalog entry shared by all communities of an organization. Its unit is fixed after creation:
 * changing it would silently reinterpret every recorded quantity.
 */
@Entity
@Table(name = "foods")
public class Food extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 120)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private FoodCategory category;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private UnitOfMeasure unit;

  @Column(length = 500)
  private String description;

  @Column(name = "storage_guidance", length = 1000)
  private String storageGuidance;

  @Column(name = "expiration_warning_days", nullable = false)
  private int expirationWarningDays;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Food() {}

  public Food(UUID id, UUID organizationId, UnitOfMeasure unit, FoodDetails details) {
    super(id);
    this.organizationId = organizationId;
    this.unit = unit;
    update(details);
  }

  public void update(FoodDetails details) {
    this.name = details.name().trim();
    this.category = details.category();
    this.description = details.description();
    this.storageGuidance = details.storageGuidance();
    this.expirationWarningDays = details.expirationWarningDays();
    this.active = details.active();
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getName() {
    return name;
  }

  public FoodCategory getCategory() {
    return category;
  }

  public UnitOfMeasure getUnit() {
    return unit;
  }

  public String getDescription() {
    return description;
  }

  public String getStorageGuidance() {
    return storageGuidance;
  }

  public int getExpirationWarningDays() {
    return expirationWarningDays;
  }

  public boolean isActive() {
    return active;
  }

  public record FoodDetails(
      String name,
      FoodCategory category,
      String description,
      String storageGuidance,
      int expirationWarningDays,
      boolean active) {}
}
