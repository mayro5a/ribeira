package com.ribeira.sync.application;

import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.sync.domain.Device;
import com.ribeira.sync.domain.SyncOperation.OperationStatus;
import com.ribeira.sync.domain.SyncOperationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncStatusService {

  private final DeviceService devices;
  private final SyncPullService pull;
  private final SyncConflictService conflicts;
  private final SyncOperationRepository operations;
  private final AccessControl accessControl;
  private final Clock clock;

  public SyncStatusService(
      DeviceService devices,
      SyncPullService pull,
      SyncConflictService conflicts,
      SyncOperationRepository operations,
      AccessControl accessControl,
      Clock clock) {
    this.devices = devices;
    this.pull = pull;
    this.conflicts = conflicts;
    this.operations = operations;
    this.accessControl = accessControl;
    this.clock = clock;
  }

  @Transactional
  public SyncStatus status(UUID deviceId) {
    Actor actor = accessControl.member();
    Device device = devices.requireActiveDevice(actor, deviceId);
    return new SyncStatus(
        device.getId(),
        device.getStatus().name(),
        pull.checkpointOf(device.getId()),
        pull.currentPosition(actor.organizationId()),
        operations.countByDeviceIdAndStatus(device.getId(), OperationStatus.CONFLICT),
        conflicts.countOpen(accessControl.communitiesWith(Permission.SYNC_CONFLICT_RESOLVE)),
        Instant.now(clock));
  }

  /**
   * @param lastConfirmedCursor last cursor the device confirmed in a pull
   * @param serverCursor latest feed position of the organization
   * @param deviceConflicts operations sent by this device that await review
   * @param openConflictsToReview open conflicts the user may resolve
   */
  public record SyncStatus(
      UUID deviceId,
      String deviceStatus,
      Long lastConfirmedCursor,
      long serverCursor,
      long deviceConflicts,
      long openConflictsToReview,
      Instant serverTime) {}
}
