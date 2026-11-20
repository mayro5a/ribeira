package com.ribeira.inventory.application;

import com.ribeira.food.application.FoodService;
import com.ribeira.food.application.FoodService.FoodInfo;
import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.InventoryViews.MovementView;
import com.ribeira.inventory.application.InventoryViews.Withdrawn;
import com.ribeira.inventory.domain.FoodBatch;
import com.ribeira.inventory.domain.FoodBatch.NewBatch;
import com.ribeira.inventory.domain.FoodBatch.Received;
import com.ribeira.inventory.domain.FoodBatchRepository;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.inventory.domain.StockMovement;
import com.ribeira.inventory.domain.StockMovementRepository;
import com.ribeira.inventory.domain.StorageLocation;
import com.ribeira.inventory.domain.StorageLocationRepository;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way to change stock balances. Every method runs inside the caller's transaction ({@code
 * MANDATORY}), so the business record (donation, distribution, loss), the balance and the ledger
 * entry commit together (RN12). Callers are responsible for authorization.
 */
@Component
public class StockLedger {

  private final FoodBatchRepository batches;
  private final StockMovementRepository movements;
  private final StorageLocationRepository locations;
  private final FoodService foods;
  private final ChangeTracker changes;
  private final Clock clock;

  public StockLedger(
      FoodBatchRepository batches,
      StockMovementRepository movements,
      StorageLocationRepository locations,
      FoodService foods,
      ChangeTracker changes,
      Clock clock) {
    this.batches = batches;
    this.movements = movements;
    this.locations = locations;
    this.foods = foods;
    this.changes = changes;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public BatchView receive(NewBatch spec) {
    if (batches.existsById(spec.id())) {
      throw new StateConflictException("DUPLICATE_ID", "Lote já registrado: " + spec.id());
    }
    FoodInfo food = foods.requireUsable(spec.organizationId(), spec.foodId());
    spec.unit().requireSameAs(food.unit());
    if (spec.storageLocationId() != null) {
      requireLocationOf(spec.communityId(), spec.storageLocationId());
    }
    Received received = FoodBatch.receive(spec, UUID.randomUUID(), Instant.now(clock));
    batches.save(received.batch());
    record(movements.save(received.entry()));
    changes.record(
        spec.organizationId(), spec.communityId(), SyncEntityType.BATCH, received.batch().getId());
    return BatchView.of(received.batch());
  }

  /**
   * Removes stock from several batches atomically: either every item is applied or, on the first
   * insufficient balance, the exception rolls back the whole transaction (no partial withdrawal).
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<Withdrawn> withdraw(
      UUID communityId,
      List<Withdrawal> items,
      MovementType type,
      UUID sourceId,
      UUID performedBy,
      Instant occurredAt) {
    if (items.isEmpty()) {
      throw new BusinessRuleException("ITEMS_REQUIRED", "Informe ao menos um lote.");
    }
    Map<UUID, FoodBatch> locked =
        lockInCommunity(communityId, items.stream().map(Withdrawal::batchId).toList());
    Instant now = Instant.now(clock);
    List<Withdrawn> result = new ArrayList<>(items.size());
    for (Withdrawal item : items) {
      FoodBatch batch = locked.get(item.batchId());
      StockMovement movement =
          batch.withdraw(
              UUID.randomUUID(), type, item.quantity(), sourceId, performedBy, occurredAt, now);
      record(movements.save(movement));
      result.add(
          new Withdrawn(
              batch.getId(),
              batch.getFoodId(),
              batch.getUnit(),
              movement.getQuantityDelta().negate(),
              movement.getId()));
    }
    locked
        .values()
        .forEach(
            b ->
                changes.record(
                    b.getOrganizationId(), communityId, SyncEntityType.BATCH, b.getId()));
    return result;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public MovementView adjust(
      UUID communityId,
      UUID movementId,
      UUID batchId,
      BigDecimal delta,
      String justification,
      UUID performedBy) {
    if (movements.existsById(movementId)) {
      throw new StateConflictException("DUPLICATE_ID", "Movimentação já registrada.");
    }
    FoodBatch batch = lockInCommunity(communityId, List.of(batchId)).get(batchId);
    Instant now = Instant.now(clock);
    StockMovement movement = batch.adjust(movementId, delta, justification, performedBy, now, now);
    record(movements.save(movement));
    changes.record(batch.getOrganizationId(), communityId, SyncEntityType.BATCH, batchId);
    return MovementView.of(movement);
  }

  /** Locks a batch for a status change or a move. */
  @Transactional(propagation = Propagation.MANDATORY)
  public FoodBatch lockForUpdate(UUID communityId, UUID batchId) {
    return lockInCommunity(communityId, List.of(batchId)).get(batchId);
  }

  StorageLocation requireLocationOf(UUID communityId, UUID locationId) {
    StorageLocation location =
        locations
            .findById(locationId)
            .filter(l -> l.getCommunityId().equals(communityId))
            .orElseThrow(() -> new ResourceNotFoundException("Local de armazenamento", locationId));
    if (!location.isActive()) {
      throw new BusinessRuleException(
          "STORAGE_LOCATION_INACTIVE", "O local de armazenamento está inativo.");
    }
    return location;
  }

  private Map<UUID, FoodBatch> lockInCommunity(UUID communityId, List<UUID> batchIds) {
    List<UUID> distinct = batchIds.stream().distinct().sorted().toList();
    Map<UUID, FoodBatch> locked =
        batches.lockAllById(distinct).stream()
            .filter(b -> b.getCommunityId().equals(communityId))
            .collect(Collectors.toMap(FoodBatch::getId, Function.identity()));
    for (UUID id : distinct) {
      if (!locked.containsKey(id)) {
        // Batches of other communities are reported exactly like missing ones.
        throw new ResourceNotFoundException("Lote", id);
      }
    }
    return locked;
  }

  private void record(StockMovement movement) {
    changes.record(
        movement.getOrganizationId(),
        movement.getCommunityId(),
        SyncEntityType.MOVEMENT,
        movement.getId());
  }

  public record Withdrawal(UUID batchId, BigDecimal quantity) {}
}
