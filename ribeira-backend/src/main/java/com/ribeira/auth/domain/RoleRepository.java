package com.ribeira.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RoleRepository extends JpaRepository<Role, UUID> {

  /** System roles plus the organization's own roles. */
  @Query(
      "select r from Role r where r.organizationId is null or r.organizationId = :organizationId"
          + " order by r.system desc, r.name")
  List<Role> findUsableBy(UUID organizationId);

  Optional<Role> findBySystemTrueAndCode(String code);

  boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
