package com.ribeira.inventory.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockMinimumRepository extends JpaRepository<StockMinimum, StockMinimum.Key> {

  @Query("select s from StockMinimum s where s.id.communityId in :communityIds")
  List<StockMinimum> findByCommunityIds(Collection<UUID> communityIds);
}
