package com.ribeira.inventory.application;

import com.ribeira.food.application.FoodService;
import com.ribeira.inventory.application.InventoryCommands.Adjustment;
import com.ribeira.inventory.application.InventoryCommands.BatchEntry;
import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.InventoryViews.DivergenceView;
import com.ribeira.inventory.application.InventoryViews.InventoryItemView;
import com.ribeira.inventory.application.InventoryViews.LowStockView;
import com.ribeira.inventory.application.InventoryViews.MovementView;
import com.ribeira.inventory.domain.FefoPolicy;
import com.ribeira.inventory.domain.FoodBatch;
import com.ribeira.inventory.domain.FoodBatch.NewBatch;
import com.ribeira.inventory.domain.FoodBatchRepository;
import com.ribeira.inventory.domain.InventoryEnums.BatchStatus;
import com.ribeira.inventory.domain.StockMinimum;
import com.ribeira.inventory.domain.StockMinimumRepository;
import com.ribeira.inventory.domain.StockMovementRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inventory use cases exposed through REST and synchronization. */
@Service
public class InventoryService {

  private final FoodBatchRepository batches;
  private final StockMovementRepository movements;
  private final StockMinimumRepository minimums;
  private final StockLedger ledger;
  private final InventoryReadModel readModel;
  private final FoodService foods;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;
  private final Clock clock;

  public InventoryService(
      FoodBatchRepository batches,
      StockMovementRepository movements,
      StockMinimumRepository minimums,
      StockLedger ledger,
      InventoryReadModel readModel,
      FoodService foods,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes,
      Clock clock) {
    this.batches = batches;
    this.movements = movements;
    this.minimums = minimums;
    this.ledger = ledger;
    this.readModel = readModel;
    this.foods = foods;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<InventoryItemView> summary(UUID communityId) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    return readModel.summary(actor.organizationId(), communityId);
  }

  @Transactional(readOnly = true)
  public Page<BatchView> batches(
      UUID communityId, UUID foodId, BatchStatus status, Pageable pageable) {
    accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    return batches.search(communityId, foodId, status, pageable).map(BatchView::of);
  }

  @Transactional(readOnly = true)
  public BatchView batch(UUID batchId) {
    FoodBatch batch = findVisible(batchId);
    accessControl.requireCommunity(batch.getCommunityId(), Permission.STOCK_READ);
    return BatchView.of(batch);
  }

  /** BATCH_ENTRY: registers stock with an identifiable origin other than a donation. */
  @Transactional
  public BatchView registerEntry(BatchEntry command) {
    Actor actor = accessControl.requireCommunity(command.communityId(), Permission.STOCK_WRITE);
    BatchView batch =
        ledger.receive(
            new NewBatch(
                command.batchId(),
                actor.organizationId(),
                command.communityId(),
                command.foodId(),
                command.unit(),
                command.quantity(),
                command.storageLocationId(),
                null,
                null,
                command.originDescription(),
                command.receivedAt(),
                command.expirationStatus(),
                command.expirationDate(),
                command.notes(),
                actor.userId()));
    audit.record(
        actor.organizationId(),
        command.communityId(),
        actor.userId(),
        "STOCK_ENTRY_REGISTERED",
        "BATCH",
        batch.id(),
        Map.of("quantity", batch.initialQuantity(), "unit", batch.unit()));
    return batch;
  }

  @Transactional
  public MovementView adjust(Adjustment command) {
    FoodBatch batch = findVisible(command.batchId());
    if (command.communityId() != null && !command.communityId().equals(batch.getCommunityId())) {
      throw new ResourceNotFoundException("Lote", command.batchId());
    }
    Actor actor = accessControl.requireCommunity(batch.getCommunityId(), Permission.STOCK_ADJUST);
    MovementView movement =
        ledger.adjust(
            batch.getCommunityId(),
            command.movementId(),
            command.batchId(),
            command.quantityDelta(),
            command.justification(),
            actor.userId());
    audit.record(
        actor.organizationId(),
        batch.getCommunityId(),
        actor.userId(),
        "STOCK_ADJUSTED",
        "BATCH",
        batch.getId(),
        Map.of("quantityDelta", movement.quantityDelta()));
    return movement;
  }

  @Transactional
  public BatchView changeStatus(UUID batchId, BatchStatus target, String reason) {
    FoodBatch visible = findVisible(batchId);
    Actor actor = accessControl.requireCommunity(visible.getCommunityId(), Permission.STOCK_WRITE);
    FoodBatch batch = ledger.lockForUpdate(visible.getCommunityId(), batchId);
    switch (target) {
      case BLOCKED -> batch.block(reason);
      case AVAILABLE -> batch.release();
      case DEPLETED ->
          throw new BusinessRuleException(
              "INVALID_STATUS", "Lotes ficam esgotados apenas por movimentações.");
    }
    audit.record(
        actor.organizationId(),
        batch.getCommunityId(),
        actor.userId(),
        "BATCH_STATUS_CHANGED",
        "BATCH",
        batchId,
        Map.of("status", target));
    changes.record(actor.organizationId(), batch.getCommunityId(), SyncEntityType.BATCH, batchId);
    return BatchView.of(batch);
  }

  @Transactional(readOnly = true)
  public Page<MovementView> movements(UUID communityId, UUID batchId, Pageable pageable) {
    accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    return movements.search(communityId, batchId, pageable).map(MovementView::of);
  }

  @Transactional(readOnly = true)
  public List<BatchView> expiring(UUID communityId, int days) {
    accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    LocalDate limit = LocalDate.now(clock.withZone(ZoneOffset.UTC)).plusDays(days);
    return readModel.expiringUntil(List.of(communityId), limit);
  }

  @Transactional(readOnly = true)
  public List<LowStockView> lowStock(UUID communityId) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    return readModel.lowStock(actor.organizationId(), List.of(communityId));
  }

  /** FEFO is a suggestion; the distributor may pick other batches (RN14). */
  @Transactional(readOnly = true)
  public List<BatchView> fefoSuggestion(UUID communityId, UUID foodId) {
    accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    return FefoPolicy.suggestOrder(
            batches.findByCommunityIdAndFoodIdAndStatus(communityId, foodId, BatchStatus.AVAILABLE))
        .stream()
        .map(BatchView::of)
        .toList();
  }

  /** Batches whose balance differs from their ledger. Expected to be empty. */
  @Transactional(readOnly = true)
  public List<DivergenceView> reconciliation(UUID communityId) {
    accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    return batches.findDivergences(communityId).stream()
        .map(d -> new DivergenceView(d.getBatchId(), d.getMaterialized(), d.getLedger()))
        .toList();
  }

  @Transactional
  public void setMinimum(UUID communityId, UUID foodId, BigDecimal minimum) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.STOCK_WRITE);
    UnitOfMeasure unit = foods.requireUsable(actor.organizationId(), foodId).unit();
    BigDecimal value =
        minimum.signum() == 0
            ? minimum.setScale(UnitOfMeasure.SCALE)
            : unit.requirePositive(minimum);
    Instant now = Instant.now(clock);
    minimums
        .findById(new StockMinimum.Key(communityId, foodId))
        .ifPresentOrElse(
            existing -> existing.change(value, now),
            () ->
                minimums.save(
                    new StockMinimum(actor.organizationId(), communityId, foodId, value, now)));
    audit.record(
        actor.organizationId(),
        communityId,
        actor.userId(),
        "STOCK_MINIMUM_SET",
        "FOOD",
        foodId,
        Map.of("minimum", value));
  }

  private FoodBatch findVisible(UUID batchId) {
    Actor actor = accessControl.actor();
    return batches
        .findById(batchId)
        .filter(b -> b.getOrganizationId().equals(actor.organizationId()))
        .orElseThrow(() -> new ResourceNotFoundException("Lote", batchId));
  }
}
