package com.ribeira.shared.security;

import java.util.Objects;
import java.util.UUID;

/** The authenticated user performing an operation, always bound to one organization. */
public record Actor(UUID userId, UUID organizationId) {

  public Actor {
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(organizationId, "organizationId");
  }
}
