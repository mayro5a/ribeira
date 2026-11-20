package com.ribeira.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ribeira.inventory.domain.FoodBatch.NewBatch;
import com.ribeira.inventory.domain.InventoryEnums.BatchStatus;
import com.ribeira.inventory.domain.InventoryEnums.ExpirationStatus;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.exception.ApiException;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.StateConflictException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FoodBatchTest {

  private static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
  private static final UUID USER = UUID.randomUUID();

  @Test
  void receivingCreatesEntryMovementWithFullBalance() {
    FoodBatch.Received received = receive("10", UnitOfMeasure.KILOGRAM, "Feira solidária");

    assertThat(received.batch().getAvailableQuantity()).isEqualByComparingTo("10");
    assertThat(received.entry().getType()).isEqualTo(MovementType.ENTRY);
    assertThat(received.entry().getQuantityDelta()).isEqualByComparingTo("10");
    assertThat(received.entry().getBalanceAfter()).isEqualByComparingTo("10");
  }

  @Test
  void distributionOfThreeFromTenLeavesSeven() {
    FoodBatch batch = receive("10", UnitOfMeasure.UNIT, "Origem").batch();

    StockMovement movement = withdraw(batch, MovementType.DISTRIBUTION, "3");

    assertThat(batch.getAvailableQuantity()).isEqualByComparingTo("7");
    assertThat(movement.getQuantityDelta()).isEqualByComparingTo("-3");
    assertThat(movement.getBalanceAfter()).isEqualByComparingTo("7");
  }

  @Test
  void insufficientBalanceIsAConflictAndChangesNothing() {
    FoodBatch batch = receive("2", UnitOfMeasure.UNIT, "Origem").batch();

    assertThatThrownBy(() -> withdraw(batch, MovementType.DISTRIBUTION, "3"))
        .isInstanceOf(StateConflictException.class)
        .extracting(e -> ((ApiException) e).code())
        .isEqualTo(FoodBatch.INSUFFICIENT_STOCK);
    assertThat(batch.getAvailableQuantity()).isEqualByComparingTo("2");
  }

  @Test
  void withdrawingEverythingDepletesTheBatch() {
    FoodBatch batch = receive("1.500", UnitOfMeasure.KILOGRAM, "Origem").batch();

    withdraw(batch, MovementType.LOSS, "1.5");

    assertThat(batch.getStatus()).isEqualTo(BatchStatus.DEPLETED);
  }

  @Test
  void blockedBatchCannotBeDistributedButCanRegisterLosses() {
    FoodBatch batch = receive("5", UnitOfMeasure.UNIT, "Origem").batch();
    batch.block("Embalagens estufadas");

    assertThatThrownBy(() -> withdraw(batch, MovementType.DISTRIBUTION, "1"))
        .isInstanceOf(StateConflictException.class);
    withdraw(batch, MovementType.LOSS, "1");
    assertThat(batch.getAvailableQuantity()).isEqualByComparingTo("4");
    assertThat(batch.getStatus()).isEqualTo(BatchStatus.BLOCKED);
  }

  @Test
  void adjustmentRequiresJustificationAndCannotGoNegative() {
    FoodBatch batch = receive("5", UnitOfMeasure.UNIT, "Origem").batch();

    assertThatThrownBy(
            () -> batch.adjust(UUID.randomUUID(), new BigDecimal("-1"), " ", USER, NOW, NOW))
        .isInstanceOf(BusinessRuleException.class);
    assertThatThrownBy(
            () ->
                batch.adjust(UUID.randomUUID(), new BigDecimal("-6"), "Recontagem", USER, NOW, NOW))
        .isInstanceOf(StateConflictException.class);

    StockMovement movement =
        batch.adjust(UUID.randomUUID(), new BigDecimal("-2"), "Recontagem semanal", USER, NOW, NOW);
    assertThat(movement.getJustification()).isEqualTo("Recontagem semanal");
    assertThat(batch.getAvailableQuantity()).isEqualByComparingTo("3");
  }

  @Test
  void entryWithoutIdentifiableOriginIsRejected() {
    assertThatThrownBy(() -> receive("1", UnitOfMeasure.UNIT, "  "))
        .isInstanceOf(BusinessRuleException.class)
        .hasMessageContaining("origem");
  }

  @Test
  void countableUnitsRejectFractions() {
    assertThatThrownBy(() -> receive("1.5", UnitOfMeasure.PACKAGE, "Origem"))
        .isInstanceOf(BusinessRuleException.class);
  }

  @Test
  void expirationDateOnlyWhenKnown() {
    NewBatch withoutDate = spec("1", UnitOfMeasure.UNIT, "Origem", ExpirationStatus.KNOWN, null);
    assertThatThrownBy(() -> FoodBatch.receive(withoutDate, UUID.randomUUID(), NOW))
        .isInstanceOf(BusinessRuleException.class);

    NewBatch notApplicableWithDate =
        spec("1", UnitOfMeasure.UNIT, "Origem", ExpirationStatus.NOT_APPLICABLE, LocalDate.now());
    assertThatThrownBy(() -> FoodBatch.receive(notApplicableWithDate, UUID.randomUUID(), NOW))
        .isInstanceOf(BusinessRuleException.class);
  }

  private static StockMovement withdraw(FoodBatch batch, MovementType type, String quantity) {
    return batch.withdraw(
        UUID.randomUUID(), type, new BigDecimal(quantity), UUID.randomUUID(), USER, NOW, NOW);
  }

  private static FoodBatch.Received receive(String quantity, UnitOfMeasure unit, String origin) {
    return FoodBatch.receive(
        spec(quantity, unit, origin, ExpirationStatus.UNKNOWN, null), UUID.randomUUID(), NOW);
  }

  static NewBatch spec(
      String quantity,
      UnitOfMeasure unit,
      String origin,
      ExpirationStatus expiration,
      LocalDate date) {
    return new NewBatch(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        unit,
        new BigDecimal(quantity),
        null,
        null,
        null,
        origin,
        NOW,
        expiration,
        date,
        null,
        USER);
  }
}
