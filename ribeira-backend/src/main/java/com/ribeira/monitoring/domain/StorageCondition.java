package com.ribeira.monitoring.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Acceptable storage range for a location or a food category (RN15). Every condition must cite the
 * technical guidance it comes from; there is no universal default.
 */
@Entity
@Table(name = "storage_conditions")
public class StorageCondition extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "storage_location_id")
  private UUID storageLocationId;

  /** Name of a food category; kept as text so monitoring does not depend on the food module. */
  @Column(name = "food_category", length = 32)
  private String foodCategory;

  @Column(name = "min_temperature")
  private BigDecimal minTemperature;

  @Column(name = "max_temperature")
  private BigDecimal maxTemperature;

  @Column(name = "min_humidity")
  private BigDecimal minHumidity;

  @Column(name = "max_humidity")
  private BigDecimal maxHumidity;

  @Column(name = "max_days_without_inspection")
  private Integer maxDaysWithoutInspection;

  @Column(name = "guidance_source", nullable = false, length = 500)
  private String guidanceSource;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected StorageCondition() {}

  public StorageCondition(UUID id, UUID organizationId, ConditionSpec spec) {
    super(id);
    this.organizationId = organizationId;
    update(spec);
  }

  public void update(ConditionSpec spec) {
    if (spec.storageLocationId() == null && spec.foodCategory() == null) {
      throw new BusinessRuleException(
          "CONDITION_SCOPE_REQUIRED", "Informe o local ou a categoria de alimento.");
    }
    if (spec.guidanceSource() == null || spec.guidanceSource().isBlank()) {
      throw new BusinessRuleException(
          "GUIDANCE_SOURCE_REQUIRED", "Informe a orientação técnica que fundamenta os limites.");
    }
    requireOrdered(spec.minTemperature(), spec.maxTemperature());
    requireOrdered(spec.minHumidity(), spec.maxHumidity());
    this.storageLocationId = spec.storageLocationId();
    this.foodCategory = spec.foodCategory();
    this.minTemperature = spec.minTemperature();
    this.maxTemperature = spec.maxTemperature();
    this.minHumidity = spec.minHumidity();
    this.maxHumidity = spec.maxHumidity();
    this.maxDaysWithoutInspection = spec.maxDaysWithoutInspection();
    this.guidanceSource = spec.guidanceSource().trim();
    this.active = spec.active();
  }

  /** Human-readable deviations of a reading from this condition; empty when within range. */
  public List<String> deviationsOf(BigDecimal temperature, BigDecimal humidity) {
    List<String> deviations = new ArrayList<>();
    if (temperature != null) {
      if (minTemperature != null && temperature.compareTo(minTemperature) < 0) {
        deviations.add(
            "temperatura "
                + temperature.toPlainString()
                + " °C abaixo de "
                + minTemperature.toPlainString()
                + " °C");
      }
      if (maxTemperature != null && temperature.compareTo(maxTemperature) > 0) {
        deviations.add(
            "temperatura "
                + temperature.toPlainString()
                + " °C acima de "
                + maxTemperature.toPlainString()
                + " °C");
      }
    }
    if (humidity != null) {
      if (minHumidity != null && humidity.compareTo(minHumidity) < 0) {
        deviations.add(
            "umidade "
                + humidity.toPlainString()
                + "% abaixo de "
                + minHumidity.toPlainString()
                + "%");
      }
      if (maxHumidity != null && humidity.compareTo(maxHumidity) > 0) {
        deviations.add(
            "umidade "
                + humidity.toPlainString()
                + "% acima de "
                + maxHumidity.toPlainString()
                + "%");
      }
    }
    return deviations;
  }

  private static void requireOrdered(BigDecimal min, BigDecimal max) {
    if (min != null && max != null && min.compareTo(max) > 0) {
      throw new BusinessRuleException("INVALID_RANGE", "O mínimo não pode ser maior que o máximo.");
    }
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getStorageLocationId() {
    return storageLocationId;
  }

  public String getFoodCategory() {
    return foodCategory;
  }

  public BigDecimal getMinTemperature() {
    return minTemperature;
  }

  public BigDecimal getMaxTemperature() {
    return maxTemperature;
  }

  public BigDecimal getMinHumidity() {
    return minHumidity;
  }

  public BigDecimal getMaxHumidity() {
    return maxHumidity;
  }

  public Integer getMaxDaysWithoutInspection() {
    return maxDaysWithoutInspection;
  }

  public String getGuidanceSource() {
    return guidanceSource;
  }

  public boolean isActive() {
    return active;
  }

  public record ConditionSpec(
      UUID storageLocationId,
      String foodCategory,
      BigDecimal minTemperature,
      BigDecimal maxTemperature,
      BigDecimal minHumidity,
      BigDecimal maxHumidity,
      Integer maxDaysWithoutInspection,
      String guidanceSource,
      boolean active) {}
}
