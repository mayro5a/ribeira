package com.ribeira.auth.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Opaque refresh token, stored only as a SHA-256 hash. Tokens rotate on every use; all tokens
 * descending from one login share a {@code chainId} so reuse of an old token can revoke the whole
 * chain (ADR-0005).
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends AssignedIdEntity {

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "chain_id", nullable = false, updatable = false)
  private UUID chainId;

  @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false, updatable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "revoked_reason", length = 24)
  private RevocationReason revokedReason;

  protected RefreshToken() {}

  public RefreshToken(
      UUID userId, UUID chainId, String tokenHash, Instant createdAt, Instant expiresAt) {
    super(UUID.randomUUID());
    this.userId = userId;
    this.chainId = chainId;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public void revoke(RevocationReason reason, Instant when) {
    if (revokedAt == null) {
      this.revokedAt = when;
      this.revokedReason = reason;
    }
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  public boolean isExpired(Instant now) {
    return !now.isBefore(expiresAt);
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getChainId() {
    return chainId;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public RevocationReason getRevokedReason() {
    return revokedReason;
  }

  public enum RevocationReason {
    ROTATED,
    LOGOUT,
    REUSE_DETECTED,
    USER_DISABLED
  }
}
