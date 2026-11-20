package com.ribeira.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

/** Base for HTTP-level integration tests against the real application and PostgreSQL. */
@IntegrationTest
public abstract class ApiIntegrationTest {

  @Autowired protected TestRestTemplate rest;
  @Autowired protected ObjectMapper mapper;
  @Autowired protected Fixtures fixtures;
  @Autowired protected MutableClock clock;

  protected ApiClient api;

  @BeforeEach
  void setUpClient() {
    api = new ApiClient(rest, mapper);
  }
}
