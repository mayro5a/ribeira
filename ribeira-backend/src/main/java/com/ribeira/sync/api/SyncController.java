package com.ribeira.sync.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ribeira.sync.application.DeviceService;
import com.ribeira.sync.application.DeviceService.DeviceView;
import com.ribeira.sync.application.SyncConflictService;
import com.ribeira.sync.application.SyncConflictService.ConflictView;
import com.ribeira.sync.application.SyncConflictService.Decision;
import com.ribeira.sync.application.SyncPullService;
import com.ribeira.sync.application.SyncPullService.PullResponse;
import com.ribeira.sync.application.SyncPushService;
import com.ribeira.sync.application.SyncPushService.IncomingOperation;
import com.ribeira.sync.application.SyncPushService.PushResult;
import com.ribeira.sync.application.SyncStatusService;
import com.ribeira.sync.application.SyncStatusService.SyncStatus;
import com.ribeira.sync.domain.SyncConflict.ConflictStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Sincronização", description = "Contrato em docs/synchronization.md")
public class SyncController {

  static final String DEVICE_HEADER = "X-Device-Id";

  private final SyncPushService pushService;
  private final SyncPullService pullService;
  private final SyncConflictService conflictService;
  private final DeviceService deviceService;
  private final SyncStatusService statusService;

  public SyncController(
      SyncPushService pushService,
      SyncPullService pullService,
      SyncConflictService conflictService,
      DeviceService deviceService,
      SyncStatusService statusService) {
    this.pushService = pushService;
    this.pullService = pullService;
    this.conflictService = conflictService;
    this.deviceService = deviceService;
    this.statusService = statusService;
  }

  @PostMapping("/api/v1/devices")
  @Operation(summary = "Registra (ou confirma) o aparelho após o login")
  public DeviceView registerDevice(@Valid @RequestBody RegisterDeviceRequest request) {
    return deviceService.register(request.deviceId(), request.name());
  }

  @GetMapping("/api/v1/devices")
  @Operation(summary = "Aparelhos da organização (DEVICE_MANAGE)")
  public List<DeviceView> devices() {
    return deviceService.list();
  }

  @PostMapping("/api/v1/devices/{id}/revoke")
  @Operation(
      summary = "Revoga um aparelho (DEVICE_MANAGE)",
      description = "Efetiva no próximo contato do aparelho com o servidor.")
  public DeviceView revoke(@PathVariable UUID id) {
    return deviceService.revoke(id);
  }

  @PostMapping("/api/v1/sync/push")
  @Operation(
      summary = "Envia operações offline (até 25), com resultado individual",
      description =
          "Resultados: APPLIED, DUPLICATE, CONFLICT, REJECTED, DISCARDED. Erros da requisição"
              + " inteira: 401, 403 DEVICE_REVOKED/DEVICE_NOT_REGISTERED, 400, 5xx (reenviar).")
  public PushResponse push(@Valid @RequestBody PushRequest request) {
    List<IncomingOperation> incoming =
        request.operations().stream()
            .map(
                o ->
                    new IncomingOperation(
                        o.operationId(),
                        o.deviceId(),
                        o.communityId(),
                        o.entityType(),
                        o.operationType(),
                        o.createdAt(),
                        o.schemaVersion(),
                        o.payload()))
            .toList();
    return new PushResponse(pushService.push(request.deviceId(), incoming));
  }

  @GetMapping("/api/v1/sync/pull")
  @Operation(summary = "Alterações posteriores ao cursor confirmado pelo aparelho")
  public PullResponse pull(
      @RequestHeader(DEVICE_HEADER) UUID deviceId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) @Min(1) @Max(500) Integer limit) {
    return pullService.pull(deviceId, cursor, limit);
  }

  @GetMapping("/api/v1/sync/status")
  @Operation(summary = "Estado de sincronização do aparelho")
  public SyncStatus status(@RequestHeader(DEVICE_HEADER) UUID deviceId) {
    return statusService.status(deviceId);
  }

  @GetMapping("/api/v1/sync/conflicts")
  @Operation(summary = "Conflitos e rejeições para revisão (SYNC_CONFLICT_RESOLVE)")
  public List<ConflictView> conflicts(
      @RequestParam(required = false, defaultValue = "OPEN") ConflictStatus status) {
    return conflictService.list(status);
  }

  @PostMapping("/api/v1/sync/conflicts/{id}/resolution")
  @ResponseStatus(HttpStatus.OK)
  @Operation(
      summary = "Resolve conflito: RETRY reaplica a operação; DISCARD exige justificativa",
      description = "A operação original e o histórico de tentativas são preservados.")
  public ConflictView resolve(
      @PathVariable UUID id, @Valid @RequestBody ResolutionRequest request) {
    return conflictService.resolve(id, request.decision(), request.notes());
  }

  public record RegisterDeviceRequest(
      @NotNull UUID deviceId, @NotBlank @Size(max = 120) String name) {}

  public record PushRequest(
      @NotNull UUID deviceId,
      @NotEmpty @Size(max = SyncPushService.MAX_OPERATIONS_PER_PUSH)
          List<@Valid @NotNull OperationRequest> operations) {}

  public record OperationRequest(
      @NotNull UUID operationId,
      @NotNull UUID deviceId,
      @NotNull UUID communityId,
      @NotBlank @Size(max = 24) String entityType,
      @NotBlank @Size(max = 32) String operationType,
      @NotNull Instant createdAt,
      @Min(1) int schemaVersion,
      @NotNull JsonNode payload) {}

  public record PushResponse(List<PushResult> results) {}

  public record ResolutionRequest(@NotNull Decision decision, @Size(max = 1000) String notes) {}
}
