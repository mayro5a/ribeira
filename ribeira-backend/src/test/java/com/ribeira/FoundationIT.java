package com.ribeira;

import static org.assertj.core.api.Assertions.assertThat;

import com.ribeira.support.IntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@IntegrationTest
class FoundationIT {

  @Autowired TestRestTemplate rest;
  @Autowired Flyway flyway;

  @Test
  void migrationsRunOnCleanDatabase() {
    assertThat(flyway.info().pending()).isEmpty();
    assertThat(flyway.info().applied()).isNotEmpty();
  }

  @Test
  void healthEndpointIsPublicAndUp() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
  }

  @Test
  void unknownEndpointsAreNotPublic() {
    ResponseEntity<String> response = rest.getForEntity("/api/v1/communities", String.class);

    assertThat(response.getStatusCode().value()).isIn(401, 403);
  }

  @Test
  void responsesCarryCorrelationId() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertThat(response.getHeaders().getFirst("X-Correlation-Id")).isNotBlank();
  }
}
