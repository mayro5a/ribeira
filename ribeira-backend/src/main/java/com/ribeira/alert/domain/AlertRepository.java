package com.ribeira.alert.domain;

import com.ribeira.alert.domain.Alert.AlertStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

  boolean existsByDedupKeyAndStatus(String dedupKey, AlertStatus status);

  @Query(
      """
      select a from Alert a
      where a.communityId = :communityId and (:status is null or a.status = :status)
      """)
  Page<Alert> search(UUID communityId, AlertStatus status, Pageable pageable);

  long countByCommunityIdInAndStatus(Collection<UUID> communityIds, AlertStatus status);

  List<Alert> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);
}
