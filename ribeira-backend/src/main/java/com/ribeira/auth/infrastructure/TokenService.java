package com.ribeira.auth.infrastructure;

import com.ribeira.shared.config.SecurityProperties;
import com.ribeira.shared.security.AccessControl;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/** Creates access tokens (JWT) and opaque refresh tokens. */
@Component
public class TokenService {

  private static final String ISSUER = com.ribeira.shared.security.SecurityConfig.ISSUER;
  private static final int REFRESH_TOKEN_BYTES = 32;

  private final JwtEncoder jwtEncoder;
  private final SecurityProperties properties;
  private final SecureRandom random = new SecureRandom();

  public TokenService(JwtEncoder jwtEncoder, SecurityProperties properties) {
    this.jwtEncoder = jwtEncoder;
    this.properties = properties;
  }

  /** Permissions are intentionally not embedded; they are loaded per request. */
  public AccessToken issueAccessToken(UUID userId, UUID organizationId, Instant now) {
    Instant expiresAt = now.plus(properties.accessTokenTtl());
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(userId.toString())
            .claim(AccessControl.ORGANIZATION_CLAIM, organizationId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .build();
    String value =
        jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new AccessToken(value, expiresAt);
  }

  public String newRefreshTokenValue() {
    byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  public Instant refreshExpiry(Instant now) {
    return now.plus(properties.refreshTokenTtl());
  }

  public record AccessToken(String value, Instant expiresAt) {}
}
