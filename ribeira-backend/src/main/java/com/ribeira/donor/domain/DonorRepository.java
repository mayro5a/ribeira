package com.ribeira.donor.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DonorRepository extends JpaRepository<Donor, UUID> {

  List<Donor> findByOrganizationIdOrderByName(UUID organizationId);

  List<Donor> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

  Optional<Donor> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
