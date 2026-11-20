package com.ribeira.auth.application;

import com.ribeira.shared.config.SecurityProperties;
import com.ribeira.shared.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Limits failed logins per (client address, e-mail) in a sliding window. In-memory, so the limit is
 * per instance; adequate for the single-instance MVP deployment (docs/security.md).
 */
@Component
public class LoginRateLimiter {

  private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
  private final SecurityProperties properties;
  private final Clock clock;

  public LoginRateLimiter(SecurityProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock;
  }

  public void checkAllowed(String key) {
    Deque<Instant> attempts = failures.get(key);
    if (attempts == null) {
      return;
    }
    synchronized (attempts) {
      prune(attempts);
      if (attempts.size() >= properties.loginMaxFailures()) {
        throw new TooManyAttemptsException();
      }
    }
  }

  public void recordFailure(String key) {
    Deque<Instant> attempts = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (attempts) {
      prune(attempts);
      attempts.addLast(Instant.now(clock));
    }
  }

  public void reset(String key) {
    failures.remove(key);
  }

  private void prune(Deque<Instant> attempts) {
    Instant threshold = Instant.now(clock).minus(properties.loginFailureWindow());
    while (!attempts.isEmpty() && attempts.peekFirst().isBefore(threshold)) {
      attempts.pollFirst();
    }
  }

  public static class TooManyAttemptsException extends ApiException {
    TooManyAttemptsException() {
      super(
          HttpStatus.TOO_MANY_REQUESTS,
          "TOO_MANY_LOGIN_ATTEMPTS",
          "Muitas tentativas de login. Aguarde alguns minutos e tente novamente.");
    }
  }
}
