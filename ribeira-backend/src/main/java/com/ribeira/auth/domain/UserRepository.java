package com.ribeira.auth.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

  Optional<User> findByEmail(String normalizedEmail);

  boolean existsByEmail(String normalizedEmail);

  List<User> findByOrganizationIdOrderByDisplayName(UUID organizationId);

  Optional<User> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
