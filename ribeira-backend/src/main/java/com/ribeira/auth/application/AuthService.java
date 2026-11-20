package com.ribeira.auth.application;

import com.ribeira.auth.domain.RefreshToken;
import com.ribeira.auth.domain.RefreshToken.RevocationReason;
import com.ribeira.auth.domain.RefreshTokenRepository;
import com.ribeira.auth.domain.User;
import com.ribeira.auth.domain.UserRepository;
import com.ribeira.auth.infrastructure.TokenService;
import com.ribeira.auth.infrastructure.TokenService.AccessToken;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.ApiException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.AccessGrants;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.CommunityDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  /**
   * Two tabs refreshing at the same time is normal; a token rotated moments ago is answered with a
   * retryable error instead of being treated as theft.
   */
  static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository users;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokens;
  private final LoginRateLimiter rateLimiter;
  private final AccessGrantsLoader grantsLoader;
  private final CommunityDirectory communities;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final Clock clock;
  private final String dummyHash;

  public AuthService(
      UserRepository users,
      RefreshTokenRepository refreshTokens,
      PasswordEncoder passwordEncoder,
      TokenService tokens,
      LoginRateLimiter rateLimiter,
      AccessGrantsLoader grantsLoader,
      CommunityDirectory communities,
      AccessControl accessControl,
      AuditRecorder audit,
      Clock clock) {
    this.users = users;
    this.refreshTokens = refreshTokens;
    this.passwordEncoder = passwordEncoder;
    this.tokens = tokens;
    this.rateLimiter = rateLimiter;
    this.grantsLoader = grantsLoader;
    this.communities = communities;
    this.accessControl = accessControl;
    this.audit = audit;
    this.clock = clock;
    // Used to spend the same time on unknown e-mails as on wrong passwords.
    this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Transactional
  public Session login(String email, String password, String clientAddress) {
    String normalizedEmail = User.normalizeEmail(email);
    String limiterKey = clientAddress + "|" + normalizedEmail;
    rateLimiter.checkAllowed(limiterKey);

    Optional<User> found = users.findByEmail(normalizedEmail);
    boolean passwordMatches =
        passwordEncoder.matches(password, found.map(User::getPasswordHash).orElse(dummyHash));
    if (found.isEmpty() || !passwordMatches) {
      rateLimiter.recordFailure(limiterKey);
      throw new AuthException("INVALID_CREDENTIALS", "E-mail ou senha incorretos.");
    }
    User user = found.get();
    if (!user.isActive()) {
      throw new AuthException("USER_INACTIVE", "Usuário bloqueado ou desativado.");
    }
    rateLimiter.reset(limiterKey);
    Instant now = Instant.now(clock);
    user.recordLogin(now);
    audit.record(
        user.getOrganizationId(), null, user.getId(), "LOGIN", "USER", user.getId(), Map.of());
    return openSession(user, UUID.randomUUID(), now);
  }

  /**
   * Rotates the refresh token. Presenting an already-rotated token outside the grace window means
   * it was copied, so the whole chain is revoked.
   */
  @Transactional(noRollbackFor = AuthException.class)
  public Session refresh(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new AuthException("INVALID_REFRESH_TOKEN", "Sessão expirada. Faça login novamente.");
    }
    Instant now = Instant.now(clock);
    RefreshToken current =
        refreshTokens
            .findForUpdateByTokenHash(TokenService.hash(rawToken))
            .orElseThrow(
                () ->
                    new AuthException(
                        "INVALID_REFRESH_TOKEN", "Sessão expirada. Faça login novamente."));
    if (current.isRevoked()) {
      boolean recentRotation =
          current.getRevokedReason() == RevocationReason.ROTATED
              && current.getRevokedAt().plus(ROTATION_GRACE).isAfter(now);
      if (recentRotation) {
        throw new AuthException(
            "REFRESH_TOKEN_ROTATED", "Sessão renovada em outra aba. Tente novamente.");
      }
      revokeChain(current.getChainId(), RevocationReason.REUSE_DETECTED, now);
      log.warn("Refresh token reuse detected; chain revoked for user {}", current.getUserId());
      throw new AuthException("INVALID_REFRESH_TOKEN", "Sessão expirada. Faça login novamente.");
    }
    if (current.isExpired(now)) {
      throw new AuthException("INVALID_REFRESH_TOKEN", "Sessão expirada. Faça login novamente.");
    }
    User user =
        users
            .findById(current.getUserId())
            .filter(User::isActive)
            .orElseThrow(
                () -> new AuthException("USER_INACTIVE", "Usuário bloqueado ou desativado."));
    current.revoke(RevocationReason.ROTATED, now);
    return openSession(user, current.getChainId(), now);
  }

  @Transactional
  public void logout(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return;
    }
    refreshTokens
        .findForUpdateByTokenHash(TokenService.hash(rawToken))
        .ifPresent(
            token -> revokeChain(token.getChainId(), RevocationReason.LOGOUT, Instant.now(clock)));
  }

  @Transactional(readOnly = true)
  public ProfileView currentProfile() {
    Actor actor = accessControl.actor();
    User user =
        users
            .findById(actor.userId())
            .orElseThrow(
                () -> new AuthException("USER_INACTIVE", "Usuário bloqueado ou desativado."));
    return profileOf(user, accessControl.grants());
  }

  /** Called when a user is blocked or disabled so that no refresh can extend their session. */
  @Transactional
  public void revokeAllSessions(UUID userId) {
    Instant now = Instant.now(clock);
    refreshTokens
        .findByUserIdAndRevokedAtIsNull(userId)
        .forEach(t -> t.revoke(RevocationReason.USER_DISABLED, now));
  }

  @Scheduled(cron = "0 17 3 * * *")
  @Transactional
  public void purgeExpiredRefreshTokens() {
    int removed = refreshTokens.deleteExpiredBefore(Instant.now(clock).minus(Duration.ofDays(1)));
    log.info("Purged {} expired refresh tokens", removed);
  }

  private Session openSession(User user, UUID chainId, Instant now) {
    String rawRefresh = tokens.newRefreshTokenValue();
    Instant refreshExpiresAt = tokens.refreshExpiry(now);
    refreshTokens.save(
        new RefreshToken(
            user.getId(), chainId, TokenService.hash(rawRefresh), now, refreshExpiresAt));
    AccessToken access = tokens.issueAccessToken(user.getId(), user.getOrganizationId(), now);
    AccessGrants grants = grantsLoader.grantsOf(user);
    return new Session(access, rawRefresh, refreshExpiresAt, profileOf(user, grants));
  }

  private ProfileView profileOf(User user, AccessGrants grants) {
    return ProfileView.of(
        user.getId(),
        user.getDisplayName(),
        user.getEmail(),
        grants,
        communities.communityIdsOf(user.getOrganizationId()));
  }

  private void revokeChain(UUID chainId, RevocationReason reason, Instant now) {
    refreshTokens.findByChainIdAndRevokedAtIsNull(chainId).forEach(t -> t.revoke(reason, now));
  }

  public record Session(
      AccessToken accessToken,
      String refreshToken,
      Instant refreshExpiresAt,
      ProfileView profile) {}

  public static class AuthException extends ApiException {
    public AuthException(String code, String message) {
      super(HttpStatus.UNAUTHORIZED, code, message);
    }
  }
}
