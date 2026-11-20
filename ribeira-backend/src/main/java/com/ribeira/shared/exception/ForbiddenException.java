package com.ribeira.shared.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends ApiException {

  public static final String CODE = "ACCESS_DENIED";

  public ForbiddenException(String message) {
    super(HttpStatus.FORBIDDEN, CODE, message);
  }

  public ForbiddenException(String code, String message) {
    super(HttpStatus.FORBIDDEN, code, message);
  }
}
