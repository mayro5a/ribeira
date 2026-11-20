package com.ribeira.monitoring.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StorageConditionRepository extends JpaRepository<StorageCondition, UUID> {

  List<StorageCondition> findByOrganizationId(UUID organizationId);

  @Query(
      """
      select c from StorageCondition c
      where c.organizationId = :organizationId and c.active = true
        and (c.storageLocationId = :locationId or c.foodCategory in :categories)
      """)
  List<StorageCondition> findApplicable(
      UUID organizationId, UUID locationId, Collection<String> categories);

  @Query(
      """
      select c from StorageCondition c
      where c.organizationId = :organizationId and c.active = true
        and c.maxDaysWithoutInspection is not null
      """)
  List<StorageCondition> findWithInspectionRule(UUID organizationId);
}
