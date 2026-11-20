package com.ribeira.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ribeira.support.ApiIntegrationTest;
import com.ribeira.support.Fixtures;
import com.ribeira.support.Fixtures.TestUser;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AuthenticationIT extends ApiIntegrationTest {

  private TestUser stockKeeper;
  private UUID community;

  @BeforeEach
  void setUp() {
    UUID organization = fixtures.organization();
    community = fixtures.community(organization);
    stockKeeper = fixtures.member(organization, community, Fixtures.STOCK_KEEPER);
  }

  @Test
  void loginReturnsShortLivedTokenProfileAndHttpOnlyRefreshCookie() {
    ResponseEntity<String> response = login(stockKeeper.email(), Fixtures.PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String cookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(cookie)
        .startsWith("ribeira_refresh=")
        .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");
    JsonNode body = read(response);
    assertThat(body.path("accessToken").asText()).isNotBlank();
    assertThat(body.path("profile").path("communities").get(0).path("communityId").asText())
        .isEqualTo(community.toString());
    assertThat(body.path("profile").path("communities").get(0).path("permissions").toString())
        .contains("STOCK_WRITE")
        .doesNotContain("USER_MANAGE");
    assertThat(response.getBody()).doesNotContain("passwordHash", "$2a$", "{bcrypt}");
  }

  @Test
  void wrongPasswordIsRejectedWithoutRevealingWhichFieldFailed() {
    ResponseEntity<String> response = login(stockKeeper.email(), "senha-errada-000");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(read(response).path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
    assertThat(read(login("ninguem@teste.ribeira.local", "x")).path("code").asText())
        .isEqualTo("INVALID_CREDENTIALS");
  }

  @Test
  void repeatedFailuresAreRateLimited() {
    for (int i = 0; i < 3; i++) {
      login(stockKeeper.email(), "senha-errada-000");
    }
    ResponseEntity<String> response = login(stockKeeper.email(), Fixtures.PASSWORD);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(read(response).path("code").asText()).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
  }

  @Test
  void refreshRequiresCsrfHeaderAndRotatesTheCookie() {
    String firstCookie = refreshCookieOf(login(stockKeeper.email(), Fixtures.PASSWORD));

    assertThat(refresh(firstCookie, false).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> rotated = refresh(firstCookie, true);
    assertThat(rotated.getStatusCode()).isEqualTo(HttpStatus.OK);
    String secondCookie = refreshCookieOf(rotated);
    assertThat(secondCookie).isNotEqualTo(firstCookie);
    assertThat(refresh(secondCookie, true).getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void reusingAnOldRefreshTokenRevokesTheWholeSession() {
    String first = refreshCookieOf(login(stockKeeper.email(), Fixtures.PASSWORD));
    String second = refreshCookieOf(refresh(first, true));

    // Within the grace window a concurrent tab gets a retryable error.
    assertThat(read(refresh(first, true)).path("code").asText()).isEqualTo("REFRESH_TOKEN_ROTATED");

    clock.advance(Duration.ofSeconds(11));
    assertThat(read(refresh(first, true)).path("code").asText()).isEqualTo("INVALID_REFRESH_TOKEN");
    // The legitimate token of the same chain is revoked too.
    assertThat(refresh(second, true).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void logoutRevokesRefreshToken() {
    String cookie = refreshCookieOf(login(stockKeeper.email(), Fixtures.PASSWORD));
    HttpHeaders headers = cookieHeaders(cookie, true);

    rest.postForEntity("/api/v1/auth/logout", new HttpEntity<>(null, headers), String.class);

    assertThat(refresh(cookie, true).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void protectedEndpointsRequireValidToken() {
    var anonymous = api.get("/api/v1/auth/me", null);
    assertThat(anonymous.status()).isEqualTo(401);
    assertThat(anonymous.code()).isEqualTo("UNAUTHENTICATED");

    assertThat(api.get("/api/v1/auth/me", "not-a-jwt").status()).isEqualTo(401);
  }

  @Test
  void accessTokenExpiresAfterFifteenMinutes() {
    String token = api.login(stockKeeper);
    assertThat(api.get("/api/v1/auth/me", token).status()).isEqualTo(200);

    clock.advance(Duration.ofMinutes(16));

    assertThat(api.get("/api/v1/auth/me", token).status()).isEqualTo(401);
  }

  private ResponseEntity<String> login(String email, String password) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.postForEntity(
        "/api/v1/auth/login",
        new HttpEntity<>(
            "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", headers),
        String.class);
  }

  private ResponseEntity<String> refresh(String cookie, boolean withCsrfHeader) {
    return rest.postForEntity(
        "/api/v1/auth/refresh",
        new HttpEntity<>(null, cookieHeaders(cookie, withCsrfHeader)),
        String.class);
  }

  private HttpHeaders cookieHeaders(String cookie, boolean withCsrfHeader) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, cookie);
    if (withCsrfHeader) {
      headers.add("X-Requested-With", "ribeira");
    }
    return headers;
  }

  private static String refreshCookieOf(ResponseEntity<String> response) {
    String header = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(header).isNotNull();
    return header.substring(0, header.indexOf(';'));
  }

  private JsonNode read(ResponseEntity<String> response) {
    try {
      return mapper.readTree(response.getBody());
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
