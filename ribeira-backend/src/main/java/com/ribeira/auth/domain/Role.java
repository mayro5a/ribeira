package com.ribeira.auth.domain;

import com.ribeira.shared.security.Permission;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A named set of permissions. System roles (no organization) are shared and immutable; each
 * organization may define its own roles.
 */
@Entity
@Table(name = "roles")
public class Role {

  @Id private UUID id;

  @Column(name = "organization_id", updatable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 48, updatable = false)
  private String code;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(length = 255)
  private String description;

  @Column(nullable = false, updatable = false)
  private boolean system;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission", length = 48)
  @Enumerated(EnumType.STRING)
  private Set<Permission> permissions = new HashSet<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected Role() {}

  public static Role custom(
      UUID organizationId,
      String code,
      String name,
      String description,
      Set<Permission> permissions) {
    Role role = new Role();
    role.id = UUID.randomUUID();
    role.organizationId = organizationId;
    role.code = code;
    role.system = false;
    role.update(name, description, permissions);
    return role;
  }

  public void update(String newName, String newDescription, Set<Permission> newPermissions) {
    if (system) {
      throw new IllegalStateException("System roles are immutable");
    }
    this.name = newName.trim();
    this.description = newDescription;
    this.permissions.clear();
    this.permissions.addAll(newPermissions);
  }

  public boolean isUsableBy(UUID organization) {
    return system || organization.equals(organizationId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isSystem() {
    return system;
  }

  public Set<Permission> getPermissions() {
    return permissions.isEmpty() ? EnumSet.noneOf(Permission.class) : EnumSet.copyOf(permissions);
  }
}
