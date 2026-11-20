package com.ribeira.inventory.application;

import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.InventoryViews.MovementView;
import com.ribeira.inventory.application.InventoryViews.StorageLocationView;
import com.ribeira.inventory.domain.FoodBatchRepository;
import com.ribeira.inventory.domain.StockMovementRepository;
import com.ribeira.inventory.domain.StorageLocationRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class InventorySnapshotProvider implements SyncSnapshotProvider {

  private static final Set<SyncEntityType> SUPPORTED =
      Set.of(SyncEntityType.BATCH, SyncEntityType.MOVEMENT, SyncEntityType.STORAGE_LOCATION);

  private final FoodBatchRepository batches;
  private final StockMovementRepository movements;
  private final StorageLocationRepository locations;

  InventorySnapshotProvider(
      FoodBatchRepository batches,
      StockMovementRepository movements,
      StorageLocationRepository locations) {
    this.batches = batches;
    this.movements = movements;
    this.locations = locations;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return SUPPORTED.contains(type);
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return switch (type) {
      case BATCH ->
          batches.findByOrganizationIdAndIdIn(organizationId, ids).stream()
              .map(BatchView::of)
              .collect(Collectors.toMap(BatchView::id, v -> (Object) v));
      case MOVEMENT ->
          movements.findByOrganizationIdAndIdIn(organizationId, ids).stream()
              .map(MovementView::of)
              .collect(Collectors.toMap(MovementView::id, v -> (Object) v));
      case STORAGE_LOCATION ->
          locations.findByOrganizationIdAndIdIn(organizationId, ids).stream()
              .map(StorageLocationView::of)
              .collect(Collectors.toMap(StorageLocationView::id, v -> (Object) v));
      default -> throw new IllegalArgumentException("Unsupported type " + type);
    };
  }
}
