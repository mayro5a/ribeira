package com.ribeira.loss.application;

import com.ribeira.inventory.application.InventoryViews.Withdrawn;
import com.ribeira.inventory.application.StockLedger;
import com.ribeira.inventory.application.StockLedger.Withdrawal;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.loss.domain.FoodLoss;
import com.ribeira.loss.domain.FoodLoss.LossReason;
import com.ribeira.loss.domain.FoodLoss.LossStatus;
import com.ribeira.loss.domain.FoodLossRepository;
import com.ribeira.loss.domain.FoodLossRepository.LossByCommunityAndFood;
import com.ribeira.loss.domain.FoodLossRepository.LossByFood;
import com.ribeira.loss.domain.FoodLossRepository.LossByReason;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records losses. The system only records what people observed: it never declares food unsafe; it
 * recommends inspection by a responsible person (RF08).
 */
@Service
public class LossService {

  private final FoodLossRepository losses;
  private final StockLedger ledger;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;
  private final Clock clock;

  public LossService(
      FoodLossRepository losses,
      StockLedger ledger,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes,
      Clock clock) {
    this.losses = losses;
    this.ledger = ledger;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
    this.clock = clock;
  }

  /** The loss and its LOSS movement are written in the same transaction. */
  @Transactional
  public LossView record(RecordLoss command) {
    Actor actor = accessControl.requireCommunity(command.communityId(), Permission.LOSS_WRITE);
    if (losses.existsById(command.lossId())) {
      throw new StateConflictException("DUPLICATE_ID", "Perda já registrada: " + command.lossId());
    }
    Withdrawn withdrawn =
        ledger
            .withdraw(
                command.communityId(),
                List.of(new Withdrawal(command.batchId(), command.quantity())),
                MovementType.LOSS,
                command.lossId(),
                actor.userId(),
                command.occurredAt())
            .getFirst();
    FoodLoss loss =
        losses.save(
            new FoodLoss(
                command.lossId(),
                actor.organizationId(),
                command.communityId(),
                withdrawn.batchId(),
                withdrawn.foodId(),
                withdrawn.quantity(),
                withdrawn.unit(),
                command.occurredAt(),
                command.reason(),
                command.description(),
                withdrawn.movementId(),
                actor.userId()));
    publish(actor, loss, "LOSS_RECORDED", Map.of("reason", command.reason()));
    return LossView.of(loss);
  }

  @Transactional
  public LossView changeStatus(UUID id, LossStatus status) {
    Actor actor = accessControl.actor();
    FoodLoss loss =
        losses
            .findById(id)
            .filter(l -> l.getOrganizationId().equals(actor.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Perda", id));
    accessControl.requireCommunity(loss.getCommunityId(), Permission.LOSS_WRITE);
    loss.changeStatus(status);
    publish(actor, loss, "LOSS_STATUS_CHANGED", Map.of("status", status));
    return LossView.of(loss);
  }

  @Transactional(readOnly = true)
  public Page<LossView> list(
      UUID communityId, LossReason reason, Instant from, Instant to, Pageable pageable) {
    accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    return losses
        .search(
            communityId,
            reason,
            from == null ? Instant.EPOCH : from,
            to == null ? Instant.now(clock).plusSeconds(86_400) : to,
            pageable)
        .map(LossView::of);
  }

  /** Aggregates for reports and alert rules; the caller restricts communities. */
  @Transactional(readOnly = true)
  public LossStats stats(Collection<UUID> communityIds, Instant from, Instant to) {
    if (communityIds.isEmpty()) {
      return new LossStats(List.of(), List.of());
    }
    return new LossStats(
        losses.totalsByFood(communityIds, from, to), losses.countsByReason(communityIds, from, to));
  }

  @Transactional(readOnly = true)
  public List<LossByCommunityAndFood> totalsByCommunityAndFood(
      Collection<UUID> communityIds, Instant from, Instant to) {
    return communityIds.isEmpty()
        ? List.of()
        : losses.totalsByCommunityAndFood(communityIds, from, to);
  }

  private void publish(Actor actor, FoodLoss loss, String action, Map<String, ?> details) {
    audit.record(
        actor.organizationId(),
        loss.getCommunityId(),
        actor.userId(),
        action,
        "LOSS",
        loss.getId(),
        details);
    changes.record(
        actor.organizationId(), loss.getCommunityId(), SyncEntityType.LOSS, loss.getId());
  }

  /** LOSS_RECORD, shared by REST and synchronization. */
  public record RecordLoss(
      @NotNull UUID communityId,
      @NotNull UUID lossId,
      @NotNull UUID batchId,
      @NotNull @Positive BigDecimal quantity,
      @NotNull Instant occurredAt,
      @NotNull LossReason reason,
      @Size(max = 1000) String description) {}

  public record LossStats(List<LossByFood> byFood, List<LossByReason> byReason) {}

  public record LossView(
      UUID id,
      UUID communityId,
      UUID batchId,
      UUID foodId,
      BigDecimal quantity,
      UnitOfMeasure unit,
      Instant occurredAt,
      LossReason reason,
      String description,
      LossStatus status,
      UUID recordedBy) {

    static LossView of(FoodLoss l) {
      return new LossView(
          l.getId(),
          l.getCommunityId(),
          l.getBatchId(),
          l.getFoodId(),
          l.getQuantity(),
          l.getUnit(),
          l.getOccurredAt(),
          l.getReason(),
          l.getDescription(),
          l.getStatus(),
          l.getRecordedBy());
    }
  }
}
