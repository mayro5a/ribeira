package com.ribeira.shared.security;

import java.util.Optional;
import java.util.UUID;

/** Implemented by the auth module; lets other modules authorize without depending on it. */
public interface AccessGrantsProvider {

  Optional<AccessGrants> load(UUID userId);
}
