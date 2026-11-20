package com.ribeira.family.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FamilyRepository extends JpaRepository<Family, UUID> {

  List<Family> findByCommunityIdOrderByCode(UUID communityId);

  List<Family> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

  boolean existsByCommunityIdAndCodeIgnoreCaseAndIdNot(UUID communityId, String code, UUID id);
}
