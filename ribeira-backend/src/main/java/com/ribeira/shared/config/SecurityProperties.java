package com.ribeira.shared.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security settings, all supplied by environment variables (see .env.example). The application
 * refuses to start with a missing or short JWT secret instead of falling back to a default.
 */
@ConfigurationProperties("ribeira.security")
public record SecurityProperties(
    String jwtSecret,
    Duration accessTokenTtl,
    Duration refreshTokenTtl,
    boolean cookieSecure,
    List<String> corsAllowedOrigins,
    int loginMaxFailures,
    Duration loginFailureWindow) {

  public static final int MIN_SECRET_BYTES = 32;

  public SecurityProperties {
    if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "RIBEIRA_JWT_SECRET must be set with at least "
              + MIN_SECRET_BYTES
              + " bytes (generate with: openssl rand -base64 48)");
    }
    accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
    refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
    corsAllowedOrigins = corsAllowedOrigins == null ? List.of() : List.copyOf(corsAllowedOrigins);
    loginMaxFailures = loginMaxFailures <= 0 ? 5 : loginMaxFailures;
    loginFailureWindow = loginFailureWindow == null ? Duration.ofMinutes(15) : loginFailureWindow;
  }
}
