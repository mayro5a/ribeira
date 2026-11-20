package com.ribeira.distribution.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DistributionRepository extends JpaRepository<Distribution, UUID> {

  @Query(
      """
      select d.id from Distribution d
      where d.communityId = :communityId
        and d.distributedAt >= :from and d.distributedAt < :to
        and (:familyId is null or :familyId member of d.familyIds)
      """)
  Page<UUID> findIds(UUID communityId, UUID familyId, Instant from, Instant to, Pageable pageable);

  @Query(
      "select distinct d from Distribution d left join fetch d.items"
          + " where d.organizationId = :organizationId and d.id in :ids")
  List<Distribution> findWithItems(UUID organizationId, Collection<UUID> ids);

  @Query(
      """
      select count(d) from Distribution d
      where d.communityId in :communityIds and d.distributedAt >= :from and d.distributedAt < :to
      """)
  long countIn(Collection<UUID> communityIds, Instant from, Instant to);

  @Query(
      value =
          """
          select count(distinct df.family_id) from distribution_families df
          join distributions d on d.id = df.distribution_id
          where d.community_id in (:communityIds) and d.distributed_at >= :from and d.distributed_at < :to
          """,
      nativeQuery = true)
  long countFamiliesServed(Collection<UUID> communityIds, Instant from, Instant to);

  @Query(
      """
      select i.foodId as foodId, i.unit as unit, sum(i.quantity) as quantity
      from DistributionItem i join i.distribution d
      where d.communityId in :communityIds and d.distributedAt >= :from and d.distributedAt < :to
      group by i.foodId, i.unit
      """)
  List<QuantityByFood> totalsByFood(Collection<UUID> communityIds, Instant from, Instant to);

  interface QuantityByFood {
    UUID getFoodId();

    com.ribeira.shared.domain.UnitOfMeasure getUnit();

    BigDecimal getQuantity();
  }
}
