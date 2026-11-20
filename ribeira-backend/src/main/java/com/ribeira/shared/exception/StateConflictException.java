package com.ribeira.shared.exception;

import org.springframework.http.HttpStatus;

/**
 * The request is valid but conflicts with current server state, e.g. insufficient stock or an ID
 * already in use (HTTP 409). During synchronization this becomes a reviewable conflict instead of a
 * rejection.
 */
public class StateConflictException extends ApiException {

  public StateConflictException(String code, String message) {
    super(HttpStatus.CONFLICT, code, message);
  }
}
