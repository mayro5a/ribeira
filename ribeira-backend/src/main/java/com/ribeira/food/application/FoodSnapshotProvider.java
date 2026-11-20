package com.ribeira.food.application;

import com.ribeira.food.domain.FoodRepository;
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
class FoodSnapshotProvider implements SyncSnapshotProvider {

  private final FoodRepository foods;

  FoodSnapshotProvider(FoodRepository foods) {
    this.foods = foods;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.FOOD;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return foods.findByOrganizationIdAndIdIn(organizationId, ids).stream()
        .map(FoodView::of)
        .collect(Collectors.toMap(FoodView::id, Function.identity()));
  }
}
