package com.ribeira.community.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommunityRepository extends JpaRepository<Community, UUID> {

  List<Community> findByOrganizationIdAndIdInOrderByName(UUID organizationId, Collection<UUID> ids);

  @Query("select c.id from Community c where c.organizationId = :organizationId")
  List<UUID> findIdsByOrganizationId(UUID organizationId);

  @Query("select c.organizationId from Community c where c.id = :id")
  Optional<UUID> findOrganizationIdById(UUID id);

  List<Community> findByActiveTrue();

  boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
      UUID organizationId, String name, UUID id);
}
