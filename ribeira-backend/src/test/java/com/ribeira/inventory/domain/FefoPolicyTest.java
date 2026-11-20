package com.ribeira.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.ribeira.inventory.domain.InventoryEnums.ExpirationStatus;
import com.ribeira.shared.domain.UnitOfMeasure;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FefoPolicyTest {

  @Test
  void knownExpirationsComeFirstSoonestFirstThenTheRest() {
    FoodBatch unknown = batch(ExpirationStatus.UNKNOWN, null);
    FoodBatch later = batch(ExpirationStatus.KNOWN, LocalDate.of(2027, 3, 1));
    FoodBatch sooner = batch(ExpirationStatus.KNOWN, LocalDate.of(2026, 12, 1));
    FoodBatch noExpiry = batch(ExpirationStatus.NOT_APPLICABLE, null);

    List<FoodBatch> order = FefoPolicy.suggestOrder(List.of(unknown, later, noExpiry, sooner));

    assertThat(order.subList(0, 2)).containsExactly(sooner, later);
    assertThat(order.subList(2, 4)).containsExactlyInAnyOrder(unknown, noExpiry);
  }

  @Test
  void blockedAndEmptyBatchesAreNotSuggested() {
    FoodBatch blocked = batch(ExpirationStatus.KNOWN, LocalDate.of(2026, 12, 1));
    blocked.block("Inspeção");

    assertThat(FefoPolicy.suggestOrder(List.of(blocked))).isEmpty();
  }

  private static FoodBatch batch(ExpirationStatus status, LocalDate date) {
    return FoodBatch.receive(
            FoodBatchTest.spec("1", UnitOfMeasure.UNIT, "Origem", status, date),
            UUID.randomUUID(),
            Instant.now())
        .batch();
  }
}
