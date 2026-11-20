package com.ribeira.monitoring.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface EnvironmentalReadingRepository extends JpaRepository<EnvironmentalReading, UUID> {

  @Query(
      """
      select r from EnvironmentalReading r
      where r.communityId = :communityId
        and (:locationId is null or r.storageLocationId = :locationId)
      """)
  Page<EnvironmentalReading> search(UUID communityId, UUID locationId, Pageable pageable);

  List<EnvironmentalReading> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

  @Query(
      """
      select r.storageLocationId as locationId, max(r.recordedAt) as lastInspection
      from EnvironmentalReading r
      where r.storageLocationId in :locationIds and r.inspection = true
      group by r.storageLocationId
      """)
  List<LastInspection> lastInspections(Collection<UUID> locationIds);

  interface LastInspection {
    UUID getLocationId();

    Instant getLastInspection();
  }
}
