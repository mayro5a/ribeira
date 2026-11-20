package com.ribeira.organization.domain;

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
@Table(name = "organizations")
public class Organization extends AssignedIdEntity {

  @Column(nullable = false, length = 160)
  private String name;

  @Column(nullable = false)
  private boolean active = true;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Organization() {}

  public Organization(UUID id, String name) {
    super(id);
    rename(name);
  }

  public void rename(String newName) {
    if (newName == null || newName.isBlank()) {
      throw new IllegalArgumentException("Organization name is required");
    }
    this.name = newName.trim();
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }

  public long getVersion() {
    return version;
  }
}
