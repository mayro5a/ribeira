package com.ribeira.shared.exception;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Also used when a resource exists in another tenant, so callers cannot probe for IDs they are not
 * allowed to see.
 */
public class ResourceNotFoundException extends ApiException {

  public static final String CODE = "RESOURCE_NOT_FOUND";

  public ResourceNotFoundException(String resourceLabel, UUID id) {
    super(HttpStatus.NOT_FOUND, CODE, resourceLabel + " não encontrado(a): " + id);
  }
}
