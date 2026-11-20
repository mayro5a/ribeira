package com.ribeira.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real PostgreSQL for integration tests; one container is shared by the cached contexts. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

  public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgresContainer() {
    return new PostgreSQLContainer<>(POSTGRES_IMAGE);
  }

  /** Replaces the system clock so tests can move time forward (token expiry, alert windows). */
  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }

  @Bean
  Fixtures fixtures(JdbcTemplate jdbc, PasswordEncoder encoder) {
    return new Fixtures(jdbc, encoder);
  }
}
