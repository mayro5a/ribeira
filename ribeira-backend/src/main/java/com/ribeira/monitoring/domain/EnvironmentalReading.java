package com.ribeira.monitoring.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** A manual (or, in the future, sensor) observation of a storage location. Never edited. */
@Entity
@Immutable
@Table(name = "environmental_readings")
public class EnvironmentalReading extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false)
  private UUID communityId;

  @Column(name = "storage_location_id", nullable = false)
  private UUID storageLocationId;

  private BigDecimal temperature;

  private BigDecimal humidity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ReadingSource source;

  @Column(nullable = false)
  private boolean inspection;

  @Column(length = 1000)
  private String notes;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Column(name = "recorded_by", nullable = false)
  private UUID recordedBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected EnvironmentalReading() {}

  public EnvironmentalReading(
      UUID id,
      UUID organizationId,
      UUID communityId,
      UUID storageLocationId,
      BigDecimal temperature,
      BigDecimal humidity,
      ReadingSource source,
      boolean inspection,
      String notes,
      Instant recordedAt,
      UUID recordedBy,
      Instant createdAt) {
    super(id);
    if (temperature == null && humidity == null && !inspection) {
      throw new BusinessRuleException(
          "READING_EMPTY", "Informe uma medição ou registre a inspeção.");
    }
    this.organizationId = organizationId;
    this.communityId = communityId;
    this.storageLocationId = storageLocationId;
    this.temperature = temperature;
    this.humidity = humidity;
    this.source = source;
    this.inspection = inspection;
    this.notes = notes;
    this.recordedAt = recordedAt;
    this.recordedBy = recordedBy;
    this.createdAt = createdAt;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getStorageLocationId() {
    return storageLocationId;
  }

  public BigDecimal getTemperature() {
    return temperature;
  }

  public BigDecimal getHumidity() {
    return humidity;
  }

  public ReadingSource getSource() {
    return source;
  }

  public boolean isInspection() {
    return inspection;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public enum ReadingSource {
    MANUAL,
    /** Reserved for future sensor integration (out of the MVP scope). */
    SENSOR
  }
}
