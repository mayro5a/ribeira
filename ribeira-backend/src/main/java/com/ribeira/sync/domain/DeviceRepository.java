package com.ribeira.sync.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

  List<Device> findByOrganizationIdOrderByLastSeenAtDesc(UUID organizationId);
}
