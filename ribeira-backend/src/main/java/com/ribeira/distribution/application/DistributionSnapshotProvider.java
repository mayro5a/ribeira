package com.ribeira.distribution.application;

import com.ribeira.distribution.domain.DistributionRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DistributionSnapshotProvider implements SyncSnapshotProvider {

  private final DistributionRepository distributions;

  DistributionSnapshotProvider(DistributionRepository distributions) {
    this.distributions = distributions;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.DISTRIBUTION;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return distributions.findWithItems(organizationId, ids).stream()
        .map(DistributionView::of)
        .collect(Collectors.toMap(DistributionView::id, v -> (Object) v));
  }
}
