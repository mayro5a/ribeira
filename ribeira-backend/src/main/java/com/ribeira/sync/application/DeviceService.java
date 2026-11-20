package com.ribeira.sync.application;

import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.ForbiddenException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.sync.domain.Device;
import com.ribeira.sync.domain.Device.DeviceStatus;
import com.ribeira.sync.domain.DeviceRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceService {

  public static final String DEVICE_REVOKED = "DEVICE_REVOKED";
  public static final String DEVICE_NOT_REGISTERED = "DEVICE_NOT_REGISTERED";

  private final DeviceRepository devices;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final Clock clock;

  public DeviceService(
      DeviceRepository devices, AccessControl accessControl, AuditRecorder audit, Clock clock) {
    this.devices = devices;
    this.accessControl = accessControl;
    this.audit = audit;
    this.clock = clock;
  }

  /** Called after login. A device ID is bound to one organization for its whole life. */
  @Transactional
  public DeviceView register(UUID deviceId, String name) {
    Actor actor = accessControl.member();
    Instant now = Instant.now(clock);
    Device device =
        devices
            .findById(deviceId)
            .map(
                existing -> {
                  if (!existing.getOrganizationId().equals(actor.organizationId())) {
                    throw new StateConflictException(
                        "DEVICE_ID_IN_USE", "Identificador de aparelho já utilizado.");
                  }
                  requireActive(existing);
                  existing.seen(actor.userId(), now);
                  existing.rename(name);
                  return existing;
                })
            .orElseGet(
                () -> {
                  Device created =
                      devices.save(
                          new Device(deviceId, actor.organizationId(), name, actor.userId(), now));
                  audit.record(
                      actor.organizationId(),
                      null,
                      actor.userId(),
                      "DEVICE_REGISTERED",
                      "DEVICE",
                      deviceId,
                      Map.of());
                  return created;
                });
    return DeviceView.of(device);
  }

  /** Every push and pull goes through here: revoked devices are refused immediately. */
  @Transactional
  public Device requireActiveDevice(Actor actor, UUID deviceId) {
    if (deviceId == null) {
      throw new ForbiddenException(DEVICE_NOT_REGISTERED, "Aparelho não identificado.");
    }
    Device device =
        devices
            .findById(deviceId)
            .filter(d -> d.getOrganizationId().equals(actor.organizationId()))
            .orElseThrow(
                () -> new ForbiddenException(DEVICE_NOT_REGISTERED, "Aparelho não registrado."));
    requireActive(device);
    device.seen(actor.userId(), Instant.now(clock));
    return device;
  }

  @Transactional(readOnly = true)
  public List<DeviceView> list() {
    Actor actor = accessControl.requireOrganization(Permission.DEVICE_MANAGE);
    return devices.findByOrganizationIdOrderByLastSeenAtDesc(actor.organizationId()).stream()
        .map(DeviceView::of)
        .toList();
  }

  @Transactional
  public DeviceView revoke(UUID deviceId) {
    Actor actor = accessControl.requireOrganization(Permission.DEVICE_MANAGE);
    Device device =
        devices
            .findById(deviceId)
            .filter(d -> d.getOrganizationId().equals(actor.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Aparelho", deviceId));
    device.revoke(actor.userId(), Instant.now(clock));
    audit.record(
        actor.organizationId(),
        null,
        actor.userId(),
        "DEVICE_REVOKED",
        "DEVICE",
        deviceId,
        Map.of());
    return DeviceView.of(device);
  }

  private static void requireActive(Device device) {
    if (!device.isActive()) {
      throw new ForbiddenException(
          DEVICE_REVOKED, "Este aparelho foi revogado. Procure a coordenação da organização.");
    }
  }

  public record DeviceView(
      UUID id,
      String name,
      UUID lastUserId,
      DeviceStatus status,
      Instant registeredAt,
      Instant lastSeenAt,
      Instant revokedAt) {
    static DeviceView of(Device d) {
      return new DeviceView(
          d.getId(),
          d.getName(),
          d.getLastUserId(),
          d.getStatus(),
          d.getRegisteredAt(),
          d.getLastSeenAt(),
          d.getRevokedAt());
    }
  }
}
