package com.ribeira.inventory.domain;

import com.ribeira.inventory.domain.InventoryEnums.BatchStatus;
import com.ribeira.inventory.domain.InventoryEnums.ExpirationStatus;
import java.util.Comparator;
import java.util.List;

/**
 * First Expired, First Out as a <em>suggestion</em> (RN14). Only batches with a known expiry are
 * ordered by date; the rest follow, oldest received first. People may choose differently when the
 * condition of the food calls for it — the application never enforces this order.
 */
public final class FefoPolicy {

  private static final Comparator<FoodBatch> ORDER =
      Comparator.comparing((FoodBatch b) -> b.getExpirationStatus() != ExpirationStatus.KNOWN)
          .thenComparing(
              FoodBatch::getExpirationDate, Comparator.nullsLast(Comparator.naturalOrder()))
          .thenComparing(FoodBatch::getReceivedAt);

  private FefoPolicy() {}

  public static List<FoodBatch> suggestOrder(List<FoodBatch> batches) {
    return batches.stream()
        .filter(b -> b.getStatus() == BatchStatus.AVAILABLE)
        .filter(b -> b.getAvailableQuantity().signum() > 0)
        .sorted(ORDER)
        .toList();
  }
}
