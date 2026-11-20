package com.ribeira.inventory.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageLocationRepository extends JpaRepository<StorageLocation, UUID> {

  List<StorageLocation> findByCommunityIdOrderByName(UUID communityId);

  List<StorageLocation> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

  boolean existsByCommunityIdAndNameIgnoreCaseAndIdNot(UUID communityId, String name, UUID id);
}
