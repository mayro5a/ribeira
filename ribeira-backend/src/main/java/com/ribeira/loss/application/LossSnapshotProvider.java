package com.ribeira.loss.application;

import com.ribeira.loss.application.LossService.LossView;
import com.ribeira.loss.domain.FoodLossRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class LossSnapshotProvider implements SyncSnapshotProvider {

  private final FoodLossRepository losses;

  LossSnapshotProvider(FoodLossRepository losses) {
    this.losses = losses;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.LOSS;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return losses.findByOrganizationIdAndIdIn(organizationId, ids).stream()
        .map(LossView::of)
        .collect(Collectors.toMap(LossView::id, v -> (Object) v));
  }
}
