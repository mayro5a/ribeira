package com.ribeira.donation.application;

import com.ribeira.donation.domain.Donation;
import com.ribeira.donation.domain.DonationItem;
import com.ribeira.donation.domain.DonationRepository;
import com.ribeira.donor.application.DonorService;
import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.StockLedger;
import com.ribeira.inventory.domain.FoodBatch.NewBatch;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
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
public class DonationService {

  private final DonationRepository donations;
  private final DonorService donors;
  private final StockLedger ledger;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;
  private final Clock clock;

  public DonationService(
      DonationRepository donations,
      DonorService donors,
      StockLedger ledger,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes,
      Clock clock) {
    this.donations = donations;
    this.donors = donors;
    this.ledger = ledger;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
    this.clock = clock;
  }

  /**
   * Registers the donation, its items and one or more traceable batches per item, each with its
   * ENTRY movement — all in one transaction.
   */
  @Transactional
  public DonationReceipt receive(ReceiveDonation command) {
    Actor actor = accessControl.requireCommunity(command.communityId(), Permission.DONATION_WRITE);
    if (donations.existsById(command.donationId())) {
      throw new StateConflictException(
          "DUPLICATE_ID", "Doação já registrada: " + command.donationId());
    }
    if (command.donorId() != null) {
      donors.requireActive(actor.organizationId(), command.donorId());
    }
    requireDistinctIds(command);

    Donation donation =
        new Donation(
            command.donationId(),
            actor.organizationId(),
            command.communityId(),
            command.donorId(),
            command.originDescription(),
            command.receivedAt(),
            command.notes(),
            actor.userId(),
            Instant.now(clock));
    List<DonationItem> items = new ArrayList<>();
    for (ReceiveDonation.Item item : command.items()) {
      requireSplitMatchesItem(item);
      items.add(
          donation.addItem(
              item.itemId(), item.foodId(), item.quantity(), item.unit(), item.notes()));
    }
    // Items must exist before batches reference them (no JPA association across modules).
    donations.saveAndFlush(donation);

    List<BatchView> batches = new ArrayList<>();
    for (int i = 0; i < command.items().size(); i++) {
      ReceiveDonation.Item item = command.items().get(i);
      for (ReceiveDonation.BatchSplit split : item.batches()) {
        batches.add(
            ledger.receive(
                new NewBatch(
                    split.batchId(),
                    actor.organizationId(),
                    command.communityId(),
                    item.foodId(),
                    item.unit(),
                    split.quantity(),
                    split.storageLocationId(),
                    donation.getId(),
                    items.get(i).getId(),
                    null,
                    command.receivedAt(),
                    split.expirationStatus(),
                    split.expirationDate(),
                    split.notes(),
                    actor.userId())));
      }
    }
    changes.record(
        actor.organizationId(), command.communityId(), SyncEntityType.DONATION, donation.getId());
    audit.record(
        actor.organizationId(),
        command.communityId(),
        actor.userId(),
        "DONATION_RECEIVED",
        "DONATION",
        donation.getId(),
        Map.of("items", items.size(), "batches", batches.size()));
    return new DonationReceipt(DonationView.of(donation), batches);
  }

  @Transactional(readOnly = true)
  public Page<DonationView> list(UUID communityId, Instant from, Instant to, Pageable pageable) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    Page<UUID> ids =
        donations.findIds(
            communityId,
            from == null ? Instant.EPOCH : from,
            to == null ? Instant.now(clock).plusSeconds(86_400) : to,
            pageable);
    List<DonationView> content =
        donations.findWithItems(actor.organizationId(), ids.getContent()).stream()
            .sorted(Comparator.comparing(d -> ids.getContent().indexOf(d.getId())))
            .map(DonationView::of)
            .toList();
    return new PageImpl<>(content, pageable, ids.getTotalElements());
  }

  @Transactional(readOnly = true)
  public DonationView get(UUID id) {
    Actor actor = accessControl.actor();
    Donation donation =
        donations.findWithItems(actor.organizationId(), List.of(id)).stream()
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Doação", id));
    accessControl.requireCommunity(donation.getCommunityId(), Permission.STOCK_READ);
    return DonationView.of(donation);
  }

  private static void requireSplitMatchesItem(ReceiveDonation.Item item) {
    BigDecimal total =
        item.batches().stream()
            .map(ReceiveDonation.BatchSplit::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (total.compareTo(item.quantity()) != 0) {
      throw new BusinessRuleException(
          "BATCH_SPLIT_MISMATCH",
          "A soma das quantidades dos lotes deve ser igual à quantidade do item.");
    }
  }

  private static void requireDistinctIds(ReceiveDonation command) {
    Set<UUID> seen = new HashSet<>();
    seen.add(command.donationId());
    for (ReceiveDonation.Item item : command.items()) {
      boolean distinct = seen.add(item.itemId());
      for (ReceiveDonation.BatchSplit split : item.batches()) {
        distinct &= seen.add(split.batchId());
      }
      if (!distinct) {
        throw new BusinessRuleException("DUPLICATE_ID", "Identificadores repetidos na doação.");
      }
    }
  }

  public record DonationReceipt(DonationView donation, List<BatchView> batches) {}
}
