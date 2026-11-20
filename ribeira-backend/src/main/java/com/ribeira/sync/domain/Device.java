package com.ribeira.sync.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A browser installation identified by a UUID generated on the device. Revocation takes effect on
 * the next contact with the server; it cannot reach a device that stays offline.
 */
@Entity
@Table(name = "devices")
public class Device extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(name = "last_user_id", nullable = false)
  private UUID lastUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private DeviceStatus status = DeviceStatus.ACTIVE;

  @Column(name = "registered_at", nullable = false, updatable = false)
  private Instant registeredAt;

  @Column(name = "last_seen_at", nullable = false)
  private Instant lastSeenAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_by")
  private UUID revokedBy;

  @Version private long version;

  protected Device() {}

  public Device(UUID id, UUID organizationId, String name, UUID userId, Instant now) {
    super(id);
    this.organizationId = organizationId;
    this.name = name;
    this.lastUserId = userId;
    this.registeredAt = now;
    this.lastSeenAt = now;
  }

  public void seen(UUID userId, Instant now) {
    this.lastUserId = userId;
    this.lastSeenAt = now;
  }

  public void rename(String newName) {
    this.name = newName;
  }

  public void revoke(UUID by, Instant now) {
    this.status = DeviceStatus.REVOKED;
    this.revokedBy = by;
    this.revokedAt = now;
  }

  public boolean isActive() {
    return status == DeviceStatus.ACTIVE;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getName() {
    return name;
  }

  public UUID getLastUserId() {
    return lastUserId;
  }

  public DeviceStatus getStatus() {
    return status;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public enum DeviceStatus {
    ACTIVE,
    REVOKED
  }
}
