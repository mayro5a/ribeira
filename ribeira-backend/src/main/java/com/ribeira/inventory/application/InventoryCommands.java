package com.ribeira.inventory.application;

import com.ribeira.inventory.domain.InventoryEnums.ExpirationStatus;
import com.ribeira.shared.domain.UnitOfMeasure;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Inputs of the inventory use cases. The same records are accepted by the REST API and by the
 * synchronization payloads (docs/synchronization.md section 2.1).
 */
public final class InventoryCommands {

  private InventoryCommands() {}

  /** BATCH_ENTRY: stock that did not come from a registered donation. */
  public record BatchEntry(
      @NotNull UUID communityId,
      @NotNull UUID batchId,
      @NotNull UUID foodId,
      @NotNull @Positive BigDecimal quantity,
      @NotNull UnitOfMeasure unit,
      @NotBlank @Size(max = 200) String originDescription,
      @NotNull Instant receivedAt,
      UUID storageLocationId,
      @NotNull ExpirationStatus expirationStatus,
      LocalDate expirationDate,
      @Size(max = 500) String notes) {}

  /** STOCK_ADJUSTMENT. {@code communityId} is optional on REST and checked when present. */
  public record Adjustment(
      UUID communityId,
      @NotNull UUID movementId,
      @NotNull UUID batchId,
      @NotNull BigDecimal quantityDelta,
      @NotBlank @Size(min = 5, max = 500) String justification) {}
}
