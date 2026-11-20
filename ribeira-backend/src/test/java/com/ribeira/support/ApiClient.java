package com.ribeira.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ribeira.support.Fixtures.TestUser;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Thin JSON client over the running application for integration tests. */
public class ApiClient {

  private final TestRestTemplate rest;
  private final ObjectMapper mapper;

  public ApiClient(TestRestTemplate rest, ObjectMapper mapper) {
    this.rest = rest;
    this.mapper = mapper;
  }

  public String login(TestUser user) {
    ResponseEntity<String> response =
        rest.postForEntity(
            "/api/v1/auth/login",
            json(
                null,
                "{\"email\":\"" + user.email() + "\",\"password\":\"" + Fixtures.PASSWORD + "\"}"),
            String.class);
    if (!response.getStatusCode().is2xxSuccessful()) {
      throw new IllegalStateException("Login failed: " + response.getBody());
    }
    return read(response.getBody()).get("accessToken").asText();
  }

  public Response get(String path, String token) {
    return exchange(HttpMethod.GET, path, token, null);
  }

  public Response post(String path, String token, Object body) {
    return exchange(HttpMethod.POST, path, token, body);
  }

  public Response put(String path, String token, Object body) {
    return exchange(HttpMethod.PUT, path, token, body);
  }

  public Response exchange(HttpMethod method, String path, String token, Object body) {
    try {
      String payload =
          body == null ? null : body instanceof String s ? s : mapper.writeValueAsString(body);
      ResponseEntity<String> response =
          rest.exchange(path, method, json(token, payload), String.class);
      return new Response(
          response.getStatusCode().value(), response.getHeaders(), read(response.getBody()));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalArgumentException(e);
    }
  }

  private HttpEntity<String> json(String token, String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return new HttpEntity<>(body, headers);
  }

  private JsonNode read(String body) {
    try {
      return body == null || body.isBlank() ? mapper.nullNode() : mapper.readTree(body);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Invalid JSON: " + body, e);
    }
  }

  public record Response(int status, HttpHeaders headers, JsonNode body) {
    public String code() {
      return body.path("code").asText();
    }
  }
}
