package com.ribeira.food.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodRepository extends JpaRepository<Food, UUID> {

  List<Food> findByOrganizationIdOrderByName(UUID organizationId);

  List<Food> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

  Optional<Food> findByIdAndOrganizationId(UUID id, UUID organizationId);

  boolean existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
      UUID organizationId, String name, UUID id);
}
