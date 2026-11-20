package com.ribeira.distribution.application;

import com.ribeira.distribution.domain.Distribution;
import com.ribeira.distribution.domain.DistributionRepository;
import com.ribeira.distribution.domain.DistributionRepository.QuantityByFood;
import com.ribeira.family.application.FamilyService;
import com.ribeira.inventory.application.InventoryViews.Withdrawn;
import com.ribeira.inventory.application.StockLedger;
import com.ribeira.inventory.application.StockLedger.Withdrawal;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DistributionService {

  private final DistributionRepository distributions;
  private final FamilyService families;
  private final StockLedger ledger;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;
  private final Clock clock;

  public DistributionService(
      DistributionRepository distributions,
      FamilyService families,
      StockLedger ledger,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes,
      Clock clock) {
    this.distributions = distributions;
    this.families = families;
    this.ledger = ledger;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
    this.clock = clock;
  }

  /**
   * Validates families and balances and removes stock atomically: if any batch lacks balance,
   * nothing is recorded (acceptance criterion "saldo insuficiente").
   */
  @Transactional
  public DistributionView create(CreateDistribution command) {
    Actor actor =
        accessControl.requireCommunity(command.communityId(), Permission.DISTRIBUTION_WRITE);
    if (distributions.existsById(command.distributionId())) {
      throw new StateConflictException(
          "DUPLICATE_ID", "Distribuição já registrada: " + command.distributionId());
    }
    Set<UUID> itemIds = new HashSet<>();
    command.items().forEach(i -> itemIds.add(i.itemId()));
    if (itemIds.size() != command.items().size()) {
      throw new BusinessRuleException("DUPLICATE_ID", "Itens com identificador repetido.");
    }
    families.requireActiveInCommunity(
        actor.organizationId(), command.communityId(), command.familyIds());

    List<Withdrawn> withdrawn =
        ledger.withdraw(
            command.communityId(),
            command.items().stream().map(i -> new Withdrawal(i.batchId(), i.quantity())).toList(),
            MovementType.DISTRIBUTION,
            command.distributionId(),
            actor.userId(),
            command.distributedAt());

    Distribution distribution =
        new Distribution(
            command.distributionId(),
            actor.organizationId(),
            command.communityId(),
            command.distributedAt(),
            command.notes(),
            actor.userId(),
            command.familyIds(),
            Instant.now(clock));
    for (int i = 0; i < command.items().size(); i++) {
      Withdrawn w = withdrawn.get(i);
      distribution.addItem(
          command.items().get(i).itemId(),
          w.batchId(),
          w.foodId(),
          w.quantity(),
          w.unit(),
          w.movementId());
    }
    distributions.save(distribution);
    changes.record(
        actor.organizationId(),
        command.communityId(),
        SyncEntityType.DISTRIBUTION,
        distribution.getId());
    audit.record(
        actor.organizationId(),
        command.communityId(),
        actor.userId(),
        "DISTRIBUTION_REGISTERED",
        "DISTRIBUTION",
        distribution.getId(),
        Map.of("items", withdrawn.size(), "families", command.familyIds().size()));
    return DistributionView.of(distribution);
  }

  @Transactional(readOnly = true)
  public Page<DistributionView> list(
      UUID communityId, UUID familyId, Instant from, Instant to, Pageable pageable) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    if (familyId != null) {
      accessControl.requireCommunity(communityId, Permission.FAMILY_READ);
    }
    Page<UUID> ids =
        distributions.findIds(
            communityId,
            familyId,
            from == null ? Instant.EPOCH : from,
            to == null ? Instant.now(clock).plusSeconds(86_400) : to,
            pageable);
    List<DistributionView> content =
        distributions.findWithItems(actor.organizationId(), ids.getContent()).stream()
            .sorted(Comparator.comparing(d -> ids.getContent().indexOf(d.getId())))
            .map(DistributionView::of)
            .toList();
    return new PageImpl<>(content, pageable, ids.getTotalElements());
  }

  @Transactional(readOnly = true)
  public DistributionView get(UUID id) {
    Actor actor = accessControl.actor();
    Distribution distribution =
        distributions.findWithItems(actor.organizationId(), List.of(id)).stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Distribuição", id));
    accessControl.requireCommunity(distribution.getCommunityId(), Permission.STOCK_READ);
    return DistributionView.of(distribution);
  }

  /** Aggregates for reports; the caller restricts communities to authorized ones. */
  @Transactional(readOnly = true)
  public DistributionStats stats(Collection<UUID> communityIds, Instant from, Instant to) {
    if (communityIds.isEmpty()) {
      return new DistributionStats(0, 0, List.of());
    }
    return new DistributionStats(
        distributions.countIn(communityIds, from, to),
        distributions.countFamiliesServed(communityIds, from, to),
        distributions.totalsByFood(communityIds, from, to));
  }

  public record DistributionStats(
      long distributions, long familiesServed, List<QuantityByFood> totalsByFood) {}
}
