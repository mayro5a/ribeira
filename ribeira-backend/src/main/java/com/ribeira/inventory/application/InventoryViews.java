package com.ribeira.inventory.application;

import com.ribeira.inventory.domain.FoodBatch;
import com.ribeira.inventory.domain.InventoryEnums.BatchStatus;
import com.ribeira.inventory.domain.InventoryEnums.ExpirationStatus;
import com.ribeira.inventory.domain.InventoryEnums.MovementSource;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.inventory.domain.StockMovement;
import com.ribeira.inventory.domain.StorageLocation;
import com.ribeira.shared.domain.UnitOfMeasure;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read models exposed by the inventory module (REST responses and sync snapshots). */
public final class InventoryViews {

  private InventoryViews() {}

  public record BatchView(
      UUID id,
      UUID communityId,
      UUID foodId,
      UUID storageLocationId,
      UUID donationId,
      UUID donationItemId,
      String originDescription,
      BigDecimal initialQuantity,
      BigDecimal availableQuantity,
      UnitOfMeasure unit,
      Instant receivedAt,
      ExpirationStatus expirationStatus,
      LocalDate expirationDate,
      String notes,
      BatchStatus status,
      String statusReason) {

    public static BatchView of(FoodBatch b) {
      return new BatchView(
          b.getId(),
          b.getCommunityId(),
          b.getFoodId(),
          b.getStorageLocationId(),
          b.getDonationId(),
          b.getDonationItemId(),
          b.getOriginDescription(),
          b.getInitialQuantity(),
          b.getAvailableQuantity(),
          b.getUnit(),
          b.getReceivedAt(),
          b.getExpirationStatus(),
          b.getExpirationDate(),
          b.getNotes(),
          b.getStatus(),
          b.getStatusReason());
    }
  }

  public record MovementView(
      UUID id,
      UUID communityId,
      UUID batchId,
      UUID foodId,
      MovementType type,
      BigDecimal quantityDelta,
      BigDecimal balanceAfter,
      MovementSource sourceType,
      UUID sourceId,
      String justification,
      UUID performedBy,
      Instant occurredAt) {

    public static MovementView of(StockMovement m) {
      return new MovementView(
          m.getId(),
          m.getCommunityId(),
          m.getBatchId(),
          m.getFoodId(),
          m.getType(),
          m.getQuantityDelta(),
          m.getBalanceAfter(),
          m.getSourceType(),
          m.getSourceId(),
          m.getJustification(),
          m.getPerformedBy(),
          m.getOccurredAt());
    }
  }

  public record StorageLocationView(
      UUID id, UUID communityId, String name, String description, boolean active) {
    public static StorageLocationView of(StorageLocation l) {
      return new StorageLocationView(
          l.getId(), l.getCommunityId(), l.getName(), l.getDescription(), l.isActive());
    }
  }

  public record InventoryItemView(
      UUID foodId,
      String foodName,
      UnitOfMeasure unit,
      BigDecimal available,
      long batchCount,
      LocalDate nextExpiration,
      BigDecimal minimumQuantity,
      boolean belowMinimum) {}

  public record LowStockView(
      UUID communityId,
      UUID foodId,
      String foodName,
      UnitOfMeasure unit,
      BigDecimal available,
      BigDecimal minimumQuantity) {}

  public record DivergenceView(UUID batchId, BigDecimal materialized, BigDecimal ledger) {}

  /** Result of one withdrawal, so callers can record what was actually removed. */
  public record Withdrawn(
      UUID batchId, UUID foodId, UnitOfMeasure unit, BigDecimal quantity, UUID movementId) {}
}
