package com.ribeira.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.ribeira.community.application.CommunityService;
import com.ribeira.shared.exception.ApiException;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.CommunityDirectory;
import com.ribeira.sync.application.OperationHandlers.Handler;
import com.ribeira.sync.application.OperationHandlers.OperationRejectedException;
import com.ribeira.sync.domain.Device;
import com.ribeira.sync.domain.SyncConflict;
import com.ribeira.sync.domain.SyncConflict.ConflictKind;
import com.ribeira.sync.domain.SyncConflictRepository;
import com.ribeira.sync.domain.SyncOperation;
import com.ribeira.sync.domain.SyncOperation.OperationEnvelope;
import com.ribeira.sync.domain.SyncOperation.OperationStatus;
import com.ribeira.sync.domain.SyncOperationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Applies offline operations idempotently (docs/synchronization.md section 3). Each operation runs
 * in its own transaction together with its {@code sync_operations} record, so a failure never
 * affects the others and a lost response can be recovered by resending.
 */
@Service
public class SyncPushService {

  public static final int MAX_OPERATIONS_PER_PUSH = 25;
  private static final Logger log = LoggerFactory.getLogger(SyncPushService.class);

  private final SyncOperationRepository operations;
  private final SyncConflictRepository conflicts;
  private final OperationHandlers handlers;
  private final DeviceService devices;
  private final CommunityService communities;
  private final CommunityDirectory communityDirectory;
  private final ChangeLogRecorder changeLog;
  private final AccessControl accessControl;
  private final ObjectMapper canonicalMapper;
  private final TransactionTemplate newTransaction;
  private final Clock clock;

  public SyncPushService(
      SyncOperationRepository operations,
      SyncConflictRepository conflicts,
      OperationHandlers handlers,
      DeviceService devices,
      CommunityService communities,
      CommunityDirectory communityDirectory,
      ChangeLogRecorder changeLog,
      AccessControl accessControl,
      ObjectMapper mapper,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.operations = operations;
    this.conflicts = conflicts;
    this.handlers = handlers;
    this.devices = devices;
    this.communities = communities;
    this.communityDirectory = communityDirectory;
    this.changeLog = changeLog;
    this.accessControl = accessControl;
    this.canonicalMapper =
        mapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    this.newTransaction = new TransactionTemplate(transactionManager);
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  public List<PushResult> push(UUID deviceId, List<IncomingOperation> incoming) {
    Actor actor = accessControl.member();
    Device device = newTransaction.execute(s -> devices.requireActiveDevice(actor, deviceId));
    List<PushResult> results = new ArrayList<>(incoming.size());
    for (IncomingOperation operation : incoming) {
      results.add(
          process(actor, device, envelopeOf(device.getId(), operation), operation.payload()));
    }
    return results;
  }

  private PushResult process(
      Actor actor, Device device, OperationEnvelope envelope, JsonNode payload) {
    Optional<SyncOperation> existing = operations.findById(envelope.operationId());
    if (existing.isPresent()) {
      return existing.get().getOrganizationId().equals(actor.organizationId())
          ? replay(existing.get(), envelope)
          : reused(envelope);
    }
    boolean ownCommunity =
        communityDirectory
            .organizationOf(envelope.communityId())
            .map(actor.organizationId()::equals)
            .orElse(false);
    if (!ownCommunity) {
      // Cannot be stored (it would reference another tenant); the device keeps it as FAILED.
      return new PushResult(
          envelope.operationId(),
          Outcome.REJECTED,
          null,
          "COMMUNITY_NOT_FOUND",
          "Comunidade inexistente ou de outra organização.",
          null);
    }
    try {
      Long seq =
          newTransaction.execute(
              status -> {
                Handler<?> handler =
                    handlers.resolve(
                        envelope.operationType(), envelope.entityType(), envelope.schemaVersion());
                if (handler.changesStock()) {
                  requirePrimaryDevice(envelope.communityId(), device.getId());
                }
                handlers.execute(handler, payload, envelope.communityId());
                long serverSeq = changeLog.flush().orElse(0L);
                SyncOperation record =
                    new SyncOperation(
                        envelope, actor.organizationId(), actor.userId(), Instant.now(clock));
                record.applied(serverSeq, Instant.now(clock));
                operations.save(record);
                return serverSeq;
              });
      return PushResult.applied(envelope.operationId(), seq);
    } catch (DataIntegrityViolationException e) {
      // Another request committed the same operation concurrently.
      return operations
          .findById(envelope.operationId())
          .map(stored -> replay(stored, envelope))
          .orElseThrow(() -> e);
    } catch (RuntimeException e) {
      Optional<Classified> failure = classify(e);
      if (failure.isEmpty()) {
        throw e; // Unexpected or transient: the whole request fails and the device retries.
      }
      return recordFailure(actor, envelope, failure.get());
    }
  }

  private PushResult recordFailure(Actor actor, OperationEnvelope envelope, Classified failure) {
    try {
      return newTransaction.execute(
          status -> {
            Instant now = Instant.now(clock);
            SyncOperation record =
                new SyncOperation(envelope, actor.organizationId(), actor.userId(), now);
            record.failed(failure.status(), failure.code(), failure.message(), now);
            operations.save(record);
            SyncConflict conflict =
                conflicts.save(
                    new SyncConflict(
                        record,
                        failure.status() == OperationStatus.CONFLICT
                            ? ConflictKind.CONFLICT
                            : ConflictKind.REJECTED,
                        failure.code(),
                        failure.message(),
                        now));
            log.info(
                "Sync operation {} stored as {} ({})",
                envelope.operationId(),
                failure.status(),
                failure.code());
            return PushResult.failed(envelope.operationId(), failure, conflict.getId());
          });
    } catch (DataIntegrityViolationException e) {
      return operations
          .findById(envelope.operationId())
          .map(stored -> replay(stored, envelope))
          .orElseThrow(() -> e);
    }
  }

  /** Same ID again: report the stored outcome, never re-execute (RN08). */
  private PushResult replay(SyncOperation stored, OperationEnvelope envelope) {
    if (!stored.getPayloadHash().equals(envelope.payloadHash())) {
      return reused(envelope);
    }
    UUID conflictId =
        conflicts.findByOperationId(stored.getOperationId()).map(SyncConflict::getId).orElse(null);
    Outcome outcome =
        switch (stored.getStatus()) {
          case APPLIED -> Outcome.DUPLICATE;
          case CONFLICT -> Outcome.CONFLICT;
          case REJECTED -> Outcome.REJECTED;
          case DISCARDED -> Outcome.DISCARDED;
        };
    return new PushResult(
        stored.getOperationId(),
        outcome,
        stored.getServerSeq(),
        stored.getErrorCode(),
        stored.getErrorMessage(),
        conflictId);
  }

  private static PushResult reused(OperationEnvelope envelope) {
    return new PushResult(
        envelope.operationId(),
        Outcome.REJECTED,
        null,
        "OPERATION_ID_REUSED",
        "Identificador de operação já usado com outro conteúdo.",
        null);
  }

  private void requirePrimaryDevice(UUID communityId, UUID deviceId) {
    communities
        .primaryDeviceOf(communityId)
        .filter(primary -> !primary.equals(deviceId))
        .ifPresent(
            primary -> {
              throw new StateConflictException(
                  "DEVICE_NOT_PRIMARY",
                  "Somente o aparelho principal da comunidade pode alterar o estoque offline.");
            });
  }

  /**
   * State-dependent failures become CONFLICT (they may succeed after review or a later sync); rule
   * and permission violations become REJECTED. Anything else is not classified.
   */
  static Optional<Classified> classify(RuntimeException e) {
    if (e instanceof StateConflictException conflict) {
      return Optional.of(
          new Classified(OperationStatus.CONFLICT, conflict.code(), conflict.getMessage()));
    }
    if (e instanceof ResourceNotFoundException missing) {
      return Optional.of(
          new Classified(OperationStatus.CONFLICT, "REFERENCE_NOT_FOUND", missing.getMessage()));
    }
    if (e instanceof ApiException api && !api.status().is5xxServerError()) {
      return Optional.of(new Classified(OperationStatus.REJECTED, api.code(), api.getMessage()));
    }
    if (e instanceof AccessDeniedException) {
      return Optional.of(
          new Classified(
              OperationStatus.REJECTED, "ACCESS_DENIED", "Sem permissão para a operação."));
    }
    return Optional.empty();
  }

  OperationEnvelope envelopeOf(UUID deviceId, IncomingOperation operation) {
    if (!deviceId.equals(operation.deviceId())) {
      throw new OperationRejectedException(
          "DEVICE_MISMATCH", "A operação pertence a outro aparelho.");
    }
    String payloadJson = write(canonicalMapper, operation.payload());
    Map<String, Object> hashed = new LinkedHashMap<>();
    hashed.put("communityId", operation.communityId().toString());
    hashed.put("operationType", operation.operationType());
    hashed.put("schemaVersion", operation.schemaVersion());
    hashed.put("payload", canonicalMapper.convertValue(operation.payload(), Object.class));
    return new OperationEnvelope(
        operation.operationId(),
        deviceId,
        operation.communityId(),
        operation.entityType(),
        operation.operationType(),
        operation.schemaVersion(),
        operation.createdAt(),
        payloadJson,
        sha256(write(canonicalMapper, hashed)));
  }

  private static String write(ObjectMapper mapper, Object value) {
    try {
      return mapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new OperationRejectedException("VALIDATION_FAILED", "Payload não serializável.");
    }
  }

  private static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public record IncomingOperation(
      UUID operationId,
      UUID deviceId,
      UUID communityId,
      String entityType,
      String operationType,
      Instant createdAt,
      int schemaVersion,
      JsonNode payload) {}

  public enum Outcome {
    APPLIED,
    DUPLICATE,
    REJECTED,
    CONFLICT,
    DISCARDED
  }

  record Classified(OperationStatus status, String code, String message) {}

  public record PushResult(
      UUID operationId,
      Outcome outcome,
      Long serverSeq,
      String errorCode,
      String message,
      UUID conflictId) {

    static PushResult applied(UUID operationId, Long seq) {
      return new PushResult(operationId, Outcome.APPLIED, seq, null, null, null);
    }

    static PushResult failed(UUID operationId, Classified failure, UUID conflictId) {
      return new PushResult(
          operationId,
          failure.status() == OperationStatus.CONFLICT ? Outcome.CONFLICT : Outcome.REJECTED,
          null,
          failure.code(),
          failure.message(),
          conflictId);
    }
  }
}
