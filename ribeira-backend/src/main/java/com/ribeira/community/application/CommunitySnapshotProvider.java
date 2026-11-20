package com.ribeira.community.application;

import com.ribeira.community.domain.CommunityRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class CommunitySnapshotProvider implements SyncSnapshotProvider {

  private final CommunityRepository communities;

  CommunitySnapshotProvider(CommunityRepository communities) {
    this.communities = communities;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.COMMUNITY;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return communities.findByOrganizationIdAndIdInOrderByName(organizationId, ids).stream()
        .map(CommunityView::of)
        .collect(Collectors.toMap(CommunityView::id, Function.identity()));
  }
}
