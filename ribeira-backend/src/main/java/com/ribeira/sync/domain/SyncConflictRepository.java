package com.ribeira.sync.domain;

import com.ribeira.sync.domain.SyncConflict.ConflictStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SyncConflictRepository extends JpaRepository<SyncConflict, UUID> {

  Optional<SyncConflict> findByOperationId(UUID operationId);

  @Query(
      """
      select c from SyncConflict c
      where c.communityId in :communityIds and (:status is null or c.status = :status)
      order by c.createdAt desc
      """)
  List<SyncConflict> search(Collection<UUID> communityIds, ConflictStatus status);

  long countByCommunityIdInAndStatus(Collection<UUID> communityIds, ConflictStatus status);
}
