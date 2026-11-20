package com.ribeira.community.application;

import com.ribeira.community.domain.CommunityRepository;
import com.ribeira.shared.security.CommunityDirectory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Kept apart from {@link CommunityService} so that authorization does not depend on it. */
@Component
class CommunityDirectoryAdapter implements CommunityDirectory {

  private final CommunityRepository communities;

  CommunityDirectoryAdapter(CommunityRepository communities) {
    this.communities = communities;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> organizationOf(UUID communityId) {
    return communities.findOrganizationIdById(communityId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> communityIdsOf(UUID organizationId) {
    return communities.findIdsByOrganizationId(organizationId);
  }
}
