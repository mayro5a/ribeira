package com.ribeira.shared.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implemented by the community module; answers tenant ownership questions for authorization. */
public interface CommunityDirectory {

  Optional<UUID> organizationOf(UUID communityId);

  List<UUID> communityIdsOf(UUID organizationId);
}
