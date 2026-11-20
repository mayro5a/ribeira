package com.ribeira.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Transient;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * Entities whose UUID is assigned by the application or by the device (ADR-0002). Implementing
 * {@link Persistable} avoids Spring Data issuing a SELECT before every insert just because the ID
 * is already set.
 */
@MappedSuperclass
public abstract class AssignedIdEntity implements Persistable<UUID> {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Transient private boolean isNew = true;

  protected AssignedIdEntity() {}

  protected AssignedIdEntity(UUID id) {
    this.id = Objects.requireNonNull(id, "id");
  }

  @Override
  public UUID getId() {
    return id;
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostPersist
  @PostLoad
  void markNotNew() {
    this.isNew = false;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    return id != null && id.equals(((AssignedIdEntity) other).id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
