package com.ribeira.auth.domain;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  /** Locked so that two concurrent refreshes cannot both rotate the same token. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshToken t where t.tokenHash = :tokenHash")
  Optional<RefreshToken> findForUpdateByTokenHash(String tokenHash);

  List<RefreshToken> findByChainIdAndRevokedAtIsNull(UUID chainId);

  List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

  @Modifying
  @Query("delete from RefreshToken t where t.expiresAt < :threshold")
  int deleteExpiredBefore(Instant threshold);
}
