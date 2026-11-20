package com.ribeira.inventory.domain;

import com.ribeira.inventory.domain.InventoryEnums.BatchStatus;
import com.ribeira.inventory.domain.InventoryEnums.ExpirationStatus;
import com.ribeira.inventory.domain.InventoryEnums.MovementSource;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.StateConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A traceable quantity of one food in one community. The available quantity changes only through
 * methods that return the corresponding {@link StockMovement}, so the balance is always
 * reconstructible from the ledger (RN04, RN12).
 */
@Entity
@Table(name = "food_batches")
public class FoodBatch extends AssignedIdEntity {

  public static final String INSUFFICIENT_STOCK = "INSUFFICIENT_STOCK";

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "food_id", nullable = false, updatable = false)
  private UUID foodId;

  @Column(name = "storage_location_id")
  private UUID storageLocationId;

  @Column(name = "donation_id", updatable = false)
  private UUID donationId;

  @Column(name = "donation_item_id", updatable = false)
  private UUID donationItemId;

  @Column(name = "origin_description", length = 200, updatable = false)
  private String originDescription;

  @Column(name = "initial_quantity", nullable = false, updatable = false)
  private BigDecimal initialQuantity;

  @Column(name = "available_quantity", nullable = false)
  private BigDecimal availableQuantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private UnitOfMeasure unit;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "expiration_status", nullable = false, length = 16)
  private ExpirationStatus expirationStatus;

  @Column(name = "expiration_date")
  private LocalDate expirationDate;

  @Column(length = 500)
  private String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private BatchStatus status;

  @Column(name = "status_reason", length = 500)
  private String statusReason;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected FoodBatch() {}

  private FoodBatch(NewBatch spec) {
    super(spec.id());
    if (spec.donationItemId() == null
        && (spec.originDescription() == null || spec.originDescription().isBlank())) {
      throw new BusinessRuleException(
          "ORIGIN_REQUIRED", "Toda entrada precisa de origem identificável.");
    }
    if ((spec.expirationStatus() == ExpirationStatus.KNOWN) != (spec.expirationDate() != null)) {
      throw new BusinessRuleException(
          "INVALID_EXPIRATION",
          "Informe a data de validade somente quando a validade for conhecida.");
    }
    this.organizationId = spec.organizationId();
    this.communityId = spec.communityId();
    this.foodId = spec.foodId();
    this.storageLocationId = spec.storageLocationId();
    this.donationId = spec.donationId();
    this.donationItemId = spec.donationItemId();
    this.originDescription = spec.originDescription();
    this.unit = spec.unit();
    this.initialQuantity = spec.unit().requirePositive(spec.quantity());
    this.availableQuantity = this.initialQuantity;
    this.receivedAt = spec.receivedAt();
    this.expirationStatus = spec.expirationStatus();
    this.expirationDate = spec.expirationDate();
    this.notes = spec.notes();
    this.status = BatchStatus.AVAILABLE;
    this.createdBy = spec.createdBy();
  }

  /** Creates the batch together with its ENTRY movement. */
  public static Received receive(NewBatch spec, UUID movementId, Instant recordedAt) {
    FoodBatch batch = new FoodBatch(spec);
    MovementSource source =
        spec.donationItemId() != null ? MovementSource.DONATION : MovementSource.MANUAL_ENTRY;
    StockMovement entry =
        new StockMovement(
            movementId,
            batch,
            MovementType.ENTRY,
            batch.initialQuantity,
            source,
            spec.donationId() != null ? spec.donationId() : batch.getId(),
            null,
            spec.createdBy(),
            spec.receivedAt(),
            recordedAt);
    return new Received(batch, entry);
  }

  /**
   * Removes stock for a distribution or a loss. Never lets the balance go negative (RN01, RN02): an
   * insufficient balance is a conflict with the current state, not a malformed request.
   */
  public StockMovement withdraw(
      UUID movementId,
      MovementType type,
      BigDecimal quantity,
      UUID sourceId,
      UUID performedBy,
      Instant occurredAt,
      Instant recordedAt) {
    if (type != MovementType.DISTRIBUTION && type != MovementType.LOSS) {
      throw new IllegalArgumentException("Withdrawals are distributions or losses: " + type);
    }
    BigDecimal amount = unit.requirePositive(quantity);
    if (type == MovementType.DISTRIBUTION && status == BatchStatus.BLOCKED) {
      throw new StateConflictException(
          "BATCH_BLOCKED", "O lote está bloqueado para inspeção e não pode ser distribuído.");
    }
    if (availableQuantity.compareTo(amount) < 0) {
      throw new StateConflictException(
          INSUFFICIENT_STOCK,
          "Saldo insuficiente no lote "
              + getId()
              + ": disponível "
              + availableQuantity.toPlainString()
              + ", solicitado "
              + amount.toPlainString()
              + ".");
    }
    applyDelta(amount.negate());
    MovementSource source =
        type == MovementType.DISTRIBUTION ? MovementSource.DISTRIBUTION : MovementSource.LOSS;
    return new StockMovement(
        movementId,
        this,
        type,
        amount.negate(),
        source,
        sourceId,
        null,
        performedBy,
        occurredAt,
        recordedAt);
  }

  /** Corrects the balance after a recount. Requires a justification (RN10). */
  public StockMovement adjust(
      UUID movementId,
      BigDecimal delta,
      String justification,
      UUID performedBy,
      Instant occurredAt,
      Instant recordedAt) {
    if (justification == null || justification.trim().length() < 5) {
      throw new BusinessRuleException(
          "JUSTIFICATION_REQUIRED", "Ajustes exigem justificativa (mínimo de 5 caracteres).");
    }
    BigDecimal amount = unit.requireNonZero(delta);
    if (availableQuantity.add(amount).signum() < 0) {
      throw new StateConflictException(
          INSUFFICIENT_STOCK, "O ajuste deixaria o saldo do lote negativo.");
    }
    applyDelta(amount);
    return new StockMovement(
        movementId,
        this,
        MovementType.ADJUSTMENT,
        amount,
        MovementSource.ADJUSTMENT,
        movementId,
        justification.trim(),
        performedBy,
        occurredAt,
        recordedAt);
  }

  public void block(String reason) {
    if (reason == null || reason.isBlank()) {
      throw new BusinessRuleException("REASON_REQUIRED", "Informe o motivo do bloqueio.");
    }
    this.status = BatchStatus.BLOCKED;
    this.statusReason = reason.trim();
  }

  public void release() {
    this.statusReason = null;
    this.status = availableQuantity.signum() == 0 ? BatchStatus.DEPLETED : BatchStatus.AVAILABLE;
  }

  public void moveTo(UUID newStorageLocationId) {
    this.storageLocationId = newStorageLocationId;
  }

  private void applyDelta(BigDecimal delta) {
    this.availableQuantity = availableQuantity.add(delta);
    if (status != BatchStatus.BLOCKED) {
      this.status = availableQuantity.signum() == 0 ? BatchStatus.DEPLETED : BatchStatus.AVAILABLE;
    }
  }

  public boolean isExpiredOn(LocalDate date) {
    return expirationStatus == ExpirationStatus.KNOWN && expirationDate.isBefore(date);
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getFoodId() {
    return foodId;
  }

  public UUID getStorageLocationId() {
    return storageLocationId;
  }

  public UUID getDonationId() {
    return donationId;
  }

  public UUID getDonationItemId() {
    return donationItemId;
  }

  public String getOriginDescription() {
    return originDescription;
  }

  public BigDecimal getInitialQuantity() {
    return initialQuantity;
  }

  public BigDecimal getAvailableQuantity() {
    return availableQuantity;
  }

  public UnitOfMeasure getUnit() {
    return unit;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public ExpirationStatus getExpirationStatus() {
    return expirationStatus;
  }

  public LocalDate getExpirationDate() {
    return expirationDate;
  }

  public String getNotes() {
    return notes;
  }

  public BatchStatus getStatus() {
    return status;
  }

  public String getStatusReason() {
    return statusReason;
  }

  public record NewBatch(
      UUID id,
      UUID organizationId,
      UUID communityId,
      UUID foodId,
      UnitOfMeasure unit,
      BigDecimal quantity,
      UUID storageLocationId,
      UUID donationId,
      UUID donationItemId,
      String originDescription,
      Instant receivedAt,
      ExpirationStatus expirationStatus,
      LocalDate expirationDate,
      String notes,
      UUID createdBy) {}

  public record Received(FoodBatch batch, StockMovement entry) {}
}
