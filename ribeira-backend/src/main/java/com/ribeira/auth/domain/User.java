package com.ribeira.auth.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "users")
public class User extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  /** Organization-wide roles: apply to every community of the organization. */
  @ElementCollection
  @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Column(name = "role_id")
  private Set<UUID> organizationRoleIds = new HashSet<>();

  /** One role per community. */
  @ElementCollection
  @CollectionTable(name = "community_users", joinColumns = @JoinColumn(name = "user_id"))
  @MapKeyColumn(name = "community_id")
  @Column(name = "role_id")
  private Map<UUID, UUID> communityRoles = new HashMap<>();

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected User() {}

  public User(UUID id, UUID organizationId, String email, String displayName, String passwordHash) {
    super(id);
    this.organizationId = organizationId;
    this.email = normalizeEmail(email);
    rename(displayName);
    this.passwordHash = passwordHash;
  }

  public static String normalizeEmail(String email) {
    return email.trim().toLowerCase(java.util.Locale.ROOT);
  }

  public void rename(String newDisplayName) {
    this.displayName = newDisplayName.trim();
  }

  public void changePasswordHash(String newHash) {
    this.passwordHash = newHash;
  }

  public void changeStatus(UserStatus newStatus) {
    this.status = newStatus;
  }

  public void assignAccess(Set<UUID> organizationRoles, Map<UUID, UUID> rolesByCommunity) {
    this.organizationRoleIds.clear();
    this.organizationRoleIds.addAll(organizationRoles);
    this.communityRoles.clear();
    this.communityRoles.putAll(rolesByCommunity);
  }

  public void recordLogin(Instant when) {
    this.lastLoginAt = when;
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }

  public Set<UUID> getOrganizationRoleIds() {
    return Set.copyOf(organizationRoleIds);
  }

  public Map<UUID, UUID> getCommunityRoles() {
    return Map.copyOf(communityRoles);
  }
}
