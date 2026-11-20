package com.ribeira.inventory.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A place where a community keeps food (warehouse, room, boat hold). */
@Entity
@Table(name = "storage_locations")
public class StorageLocation extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected StorageLocation() {}

  public StorageLocation(
      UUID id, UUID organizationId, UUID communityId, String name, String description) {
    super(id);
    this.organizationId = organizationId;
    this.communityId = communityId;
    update(name, description, true);
  }

  public void update(String newName, String newDescription, boolean isActive) {
    this.name = newName.trim();
    this.description = newDescription;
    this.active = isActive;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isActive() {
    return active;
  }
}
