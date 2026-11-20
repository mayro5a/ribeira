package com.ribeira.inventory.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

  @Query(
      """
      select m from StockMovement m
      where m.communityId = :communityId and (:batchId is null or m.batchId = :batchId)
      """)
  Page<StockMovement> search(UUID communityId, UUID batchId, Pageable pageable);

  List<StockMovement> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
}
