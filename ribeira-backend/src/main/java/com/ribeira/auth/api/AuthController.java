package com.ribeira.auth.api;

import com.ribeira.auth.application.AuthService;
import com.ribeira.auth.application.AuthService.Session;
import com.ribeira.auth.application.ProfileView;
import com.ribeira.shared.config.SecurityProperties;
import com.ribeira.shared.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticação")
public class AuthController {

  static final String REFRESH_COOKIE = "ribeira_refresh";
  static final String COOKIE_PATH = "/api/v1/auth";
  static final String CSRF_HEADER = "X-Requested-With";

  private final AuthService authService;
  private final SecurityProperties properties;
  private final Clock clock;

  public AuthController(AuthService authService, SecurityProperties properties, Clock clock) {
    this.authService = authService;
    this.properties = properties;
    this.clock = clock;
  }

  @PostMapping("/login")
  @Operation(
      summary = "Autentica com e-mail e senha",
      description =
          "Retorna o token de acesso (15 min) e define o cookie HttpOnly de renovação."
              + " Erros: 401 INVALID_CREDENTIALS, 401 USER_INACTIVE, 429 TOO_MANY_LOGIN_ATTEMPTS.")
  public ResponseEntity<SessionResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    Session session = authService.login(request.email(), request.password(), http.getRemoteAddr());
    return withSession(session);
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Renova a sessão usando o cookie de renovação",
      description =
          "Exige o cabeçalho X-Requested-With (proteção CSRF). O cookie é rotacionado a cada uso;"
              + " reuso de um cookie antigo revoga a sessão.")
  public ResponseEntity<SessionResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      @RequestHeader(name = CSRF_HEADER, required = false) String requestedWith) {
    requireCsrfHeader(requestedWith);
    return withSession(authService.refresh(refreshToken));
  }

  @PostMapping("/logout")
  @Operation(summary = "Encerra a sessão e revoga o cookie de renovação")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
      @RequestHeader(name = CSRF_HEADER, required = false) String requestedWith) {
    requireCsrfHeader(requestedWith);
    authService.logout(refreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
        .build();
  }

  @GetMapping("/me")
  @Operation(summary = "Perfil e permissões efetivas do usuário autenticado")
  public ProfileView me() {
    return authService.currentProfile();
  }

  private ResponseEntity<SessionResponse> withSession(Session session) {
    Duration maxAge = Duration.between(Instant.now(clock), session.refreshExpiresAt());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie(session.refreshToken(), maxAge).toString())
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .body(
            new SessionResponse(
                session.accessToken().value(),
                "Bearer",
                session.accessToken().expiresAt(),
                session.profile()));
  }

  private ResponseCookie refreshCookie(String value, Duration maxAge) {
    return ResponseCookie.from(REFRESH_COOKIE, value)
        .httpOnly(true)
        .secure(properties.cookieSecure())
        .sameSite("Strict")
        .path(COOKIE_PATH)
        .maxAge(maxAge)
        .build();
  }

  /**
   * A custom header cannot be sent cross-origin without a CORS preflight, so its presence proves
   * the request came from an allowed origin.
   */
  private static void requireCsrfHeader(String value) {
    if (value == null || value.isBlank()) {
      throw new ForbiddenException("CSRF_HEADER_MISSING", "Requisição sem cabeçalho obrigatório.");
    }
  }

  public record LoginRequest(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 200) String password) {}

  public record SessionResponse(
      String accessToken, String tokenType, Instant expiresAt, ProfileView profile) {}
}
