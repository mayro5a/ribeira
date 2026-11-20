package com.ribeira.community.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "communities")
public class Community extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 160)
  private String name;

  @Column(length = 1000)
  private String description;

  @Column(length = 120)
  private String municipality;

  @Column(name = "approximate_location", length = 255)
  private String approximateLocation;

  @Column(name = "primary_device_id")
  private UUID primaryDeviceId;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Community() {}

  public Community(UUID id, UUID organizationId, CommunityDetails details) {
    super(id);
    this.organizationId = organizationId;
    update(details);
  }

  public void update(CommunityDetails details) {
    this.name = details.name().trim();
    this.description = details.description();
    this.municipality = details.municipality();
    this.approximateLocation = details.approximateLocation();
    this.active = details.active();
  }

  public void assignPrimaryDevice(UUID deviceId) {
    this.primaryDeviceId = deviceId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getMunicipality() {
    return municipality;
  }

  public String getApproximateLocation() {
    return approximateLocation;
  }

  public UUID getPrimaryDeviceId() {
    return primaryDeviceId;
  }

  public boolean isActive() {
    return active;
  }

  public record CommunityDetails(
      String name,
      String description,
      String municipality,
      String approximateLocation,
      boolean active) {}
}
