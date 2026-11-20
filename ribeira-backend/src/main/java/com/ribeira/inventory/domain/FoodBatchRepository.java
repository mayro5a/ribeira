package com.ribeira.inventory.domain;

import com.ribeira.inventory.domain.InventoryEnums.BatchStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface FoodBatchRepository extends JpaRepository<FoodBatch, UUID> {

  /**
   * {@code SELECT ... FOR UPDATE} in ascending ID order, so concurrent multi-batch withdrawals
   * always lock in the same order and cannot deadlock (ADR-0004).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select b from FoodBatch b where b.id in :ids order by b.id")
  List<FoodBatch> lockAllById(Collection<UUID> ids);

  List<FoodBatch> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

  @Query(
      """
      select b from FoodBatch b
      where b.communityId = :communityId
        and (:foodId is null or b.foodId = :foodId)
        and (:status is null or b.status = :status)
      """)
  Page<FoodBatch> search(UUID communityId, UUID foodId, BatchStatus status, Pageable pageable);

  List<FoodBatch> findByCommunityIdAndFoodIdAndStatus(
      UUID communityId, UUID foodId, BatchStatus status);

  @Query(
      """
      select b from FoodBatch b
      where b.communityId in :communityIds and b.status = 'AVAILABLE'
        and b.expirationDate is not null and b.expirationDate <= :limit
        and b.availableQuantity > 0
      order by b.expirationDate
      """)
  List<FoodBatch> findExpiringUntil(Collection<UUID> communityIds, LocalDate limit);

  @Query(
      """
      select b.foodId as foodId, b.unit as unit, sum(b.availableQuantity) as available,
             count(b) as batchCount, min(b.expirationDate) as nextExpiration
      from FoodBatch b
      where b.communityId = :communityId and b.status <> 'DEPLETED'
      group by b.foodId, b.unit
      """)
  List<StockByFood> summarize(UUID communityId);

  @Query(
      """
      select b.communityId as communityId, b.foodId as foodId, sum(b.availableQuantity) as available
      from FoodBatch b
      where b.communityId in :communityIds and b.status = 'AVAILABLE'
      group by b.communityId, b.foodId
      """)
  List<AvailableByCommunityAndFood> availableByCommunityAndFood(Collection<UUID> communityIds);

  /** Batches whose materialized balance differs from the sum of their ledger entries. */
  @Query(
      """
      select b.id as batchId, b.availableQuantity as materialized, coalesce(sum(m.quantityDelta), 0) as ledger
      from FoodBatch b left join StockMovement m on m.batchId = b.id
      where b.communityId = :communityId
      group by b.id, b.availableQuantity
      having b.availableQuantity <> coalesce(sum(m.quantityDelta), 0)
      """)
  List<Divergence> findDivergences(UUID communityId);

  interface StockByFood {
    UUID getFoodId();

    com.ribeira.shared.domain.UnitOfMeasure getUnit();

    BigDecimal getAvailable();

    long getBatchCount();

    LocalDate getNextExpiration();
  }

  interface AvailableByCommunityAndFood {
    UUID getCommunityId();

    UUID getFoodId();

    BigDecimal getAvailable();
  }

  interface Divergence {
    UUID getBatchId();

    BigDecimal getMaterialized();

    BigDecimal getLedger();
  }
}
