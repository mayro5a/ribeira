package com.ribeira.inventory.application;

import com.ribeira.food.application.FoodService;
import com.ribeira.food.application.FoodService.FoodInfo;
import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.InventoryViews.InventoryItemView;
import com.ribeira.inventory.application.InventoryViews.LowStockView;
import com.ribeira.inventory.domain.FoodBatchRepository;
import com.ribeira.inventory.domain.FoodBatchRepository.AvailableByCommunityAndFood;
import com.ribeira.inventory.domain.StockMinimum;
import com.ribeira.inventory.domain.StockMinimumRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock queries shared by the inventory API, alerts and reports. Performs no authorization: callers
 * must already have restricted the communities to those the user may read.
 */
@Component
public class InventoryReadModel {

  private final FoodBatchRepository batches;
  private final StockMinimumRepository minimums;
  private final FoodService foods;

  public InventoryReadModel(
      FoodBatchRepository batches, StockMinimumRepository minimums, FoodService foods) {
    this.batches = batches;
    this.minimums = minimums;
    this.foods = foods;
  }

  @Transactional(readOnly = true)
  public List<InventoryItemView> summary(UUID organizationId, UUID communityId) {
    var rows = batches.summarize(communityId);
    Map<UUID, BigDecimal> minimumByFood = new HashMap<>();
    minimums
        .findByCommunityIds(List.of(communityId))
        .forEach(m -> minimumByFood.put(m.getFoodId(), m.getMinimumQuantity()));
    Map<UUID, FoodInfo> foodInfo =
        foods.infoOf(organizationId, rows.stream().map(r -> r.getFoodId()).toList());
    return rows.stream()
        .map(
            r -> {
              BigDecimal minimum = minimumByFood.get(r.getFoodId());
              FoodInfo food = foodInfo.get(r.getFoodId());
              return new InventoryItemView(
                  r.getFoodId(),
                  food == null ? null : food.name(),
                  r.getUnit(),
                  r.getAvailable(),
                  r.getBatchCount(),
                  r.getNextExpiration(),
                  minimum,
                  minimum != null && r.getAvailable().compareTo(minimum) < 0);
            })
        .sorted(
            Comparator.comparing(
                InventoryItemView::foodName, Comparator.nullsLast(String::compareToIgnoreCase)))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<BatchView> expiringUntil(Collection<UUID> communityIds, LocalDate limit) {
    if (communityIds.isEmpty()) {
      return List.of();
    }
    return batches.findExpiringUntil(communityIds, limit).stream().map(BatchView::of).toList();
  }

  /** Foods currently stored at a location (used to apply category storage conditions). */
  @Transactional(readOnly = true)
  public List<UUID> foodIdsStoredAt(UUID locationId) {
    return batches.findFoodIdsStoredAt(locationId);
  }

  /** Foods whose available stock is below the configured minimum (only where one is set). */
  @Transactional(readOnly = true)
  public List<LowStockView> lowStock(UUID organizationId, Collection<UUID> communityIds) {
    if (communityIds.isEmpty()) {
      return List.of();
    }
    Map<List<UUID>, BigDecimal> available = new HashMap<>();
    for (AvailableByCommunityAndFood row : batches.availableByCommunityAndFood(communityIds)) {
      available.put(List.of(row.getCommunityId(), row.getFoodId()), row.getAvailable());
    }
    List<StockMinimum> configured = minimums.findByCommunityIds(communityIds);
    Map<UUID, FoodInfo> foodInfo =
        foods.infoOf(organizationId, configured.stream().map(StockMinimum::getFoodId).toList());
    return configured.stream()
        .map(
            m -> {
              BigDecimal current =
                  available.getOrDefault(
                      List.of(m.getCommunityId(), m.getFoodId()), BigDecimal.ZERO);
              FoodInfo food = foodInfo.get(m.getFoodId());
              return new LowStockView(
                  m.getCommunityId(),
                  m.getFoodId(),
                  food == null ? null : food.name(),
                  food == null ? null : food.unit(),
                  current,
                  m.getMinimumQuantity());
            })
        .filter(v -> v.available().compareTo(v.minimumQuantity()) < 0)
        .toList();
  }
}
