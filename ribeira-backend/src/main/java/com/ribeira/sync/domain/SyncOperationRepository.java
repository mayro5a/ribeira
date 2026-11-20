package com.ribeira.sync.domain;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncOperationRepository extends JpaRepository<SyncOperation, UUID> {

  long countByDeviceIdAndStatus(UUID deviceId, SyncOperation.OperationStatus status);
}
