package com.ribeira.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * Base for expected application errors. {@link #code()} is a stable identifier clients may rely on;
 * the message must be safe to show to end users (Portuguese, no internal details).
 */
public abstract class ApiException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  protected ApiException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }
}
