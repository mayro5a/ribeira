package com.ribeira.loss.domain;

import com.ribeira.loss.domain.FoodLoss.LossReason;
import com.ribeira.shared.domain.UnitOfMeasure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FoodLossRepository extends JpaRepository<FoodLoss, UUID> {

  @Query(
      """
      select l from FoodLoss l
      where l.communityId = :communityId and (:reason is null or l.reason = :reason)
        and l.occurredAt >= :from and l.occurredAt < :to
      """)
  Page<FoodLoss> search(
      UUID communityId, LossReason reason, Instant from, Instant to, Pageable pageable);

  List<FoodLoss> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

  @Query(
      """
      select l.foodId as foodId, l.unit as unit, sum(l.quantity) as quantity, count(l) as occurrences
      from FoodLoss l
      where l.communityId in :communityIds and l.occurredAt >= :from and l.occurredAt < :to
      group by l.foodId, l.unit
      """)
  List<LossByFood> totalsByFood(Collection<UUID> communityIds, Instant from, Instant to);

  @Query(
      """
      select l.reason as reason, count(l) as occurrences
      from FoodLoss l
      where l.communityId in :communityIds and l.occurredAt >= :from and l.occurredAt < :to
      group by l.reason
      """)
  List<LossByReason> countsByReason(Collection<UUID> communityIds, Instant from, Instant to);

  @Query(
      """
      select l.communityId as communityId, l.foodId as foodId, l.unit as unit,
             sum(l.quantity) as quantity, count(l) as occurrences
      from FoodLoss l
      where l.communityId in :communityIds and l.occurredAt >= :from and l.occurredAt < :to
      group by l.communityId, l.foodId, l.unit
      """)
  List<LossByCommunityAndFood> totalsByCommunityAndFood(
      Collection<UUID> communityIds, Instant from, Instant to);

  interface LossByFood {
    UUID getFoodId();

    UnitOfMeasure getUnit();

    BigDecimal getQuantity();

    long getOccurrences();
  }

  interface LossByReason {
    LossReason getReason();

    long getOccurrences();
  }

  interface LossByCommunityAndFood {
    UUID getCommunityId();

    UUID getFoodId();

    UnitOfMeasure getUnit();

    BigDecimal getQuantity();

    long getOccurrences();
  }
}
