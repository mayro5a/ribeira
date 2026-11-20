package com.ribeira.sync.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.sync.application.OperationHandlers.Handler;
import com.ribeira.sync.application.SyncPushService.Classified;
import com.ribeira.sync.domain.SyncConflict;
import com.ribeira.sync.domain.SyncConflict.ConflictKind;
import com.ribeira.sync.domain.SyncConflict.ConflictStatus;
import com.ribeira.sync.domain.SyncConflict.EventType;
import com.ribeira.sync.domain.SyncConflictRepository;
import com.ribeira.sync.domain.SyncOperation;
import com.ribeira.sync.domain.SyncOperationRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Human review of operations that could not be applied (docs/synchronization.md section 6). A retry
 * runs the original operation against the current state, under the reviewer's authority.
 */
@Service
public class SyncConflictService {

  private final SyncConflictRepository conflicts;
  private final SyncOperationRepository operations;
  private final OperationHandlers handlers;
  private final ChangeLogRecorder changeLog;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ObjectMapper mapper;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public SyncConflictService(
      SyncConflictRepository conflicts,
      SyncOperationRepository operations,
      OperationHandlers handlers,
      ChangeLogRecorder changeLog,
      AccessControl accessControl,
      AuditRecorder audit,
      ObjectMapper mapper,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.conflicts = conflicts;
    this.operations = operations;
    this.handlers = handlers;
    this.changeLog = changeLog;
    this.accessControl = accessControl;
    this.audit = audit;
    this.mapper = mapper;
    this.transaction = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<ConflictView> list(ConflictStatus status) {
    Set<UUID> communities = accessControl.communitiesWith(Permission.SYNC_CONFLICT_RESOLVE);
    if (communities.isEmpty()) {
      return List.of();
    }
    return conflicts.search(communities, status).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public long countOpen(Set<UUID> communities) {
    return communities.isEmpty()
        ? 0
        : conflicts.countByCommunityIdInAndStatus(communities, ConflictStatus.OPEN);
  }

  public ConflictView resolve(UUID conflictId, Decision decision, String notes) {
    Actor actor = accessControl.actor();
    SyncConflict loaded =
        transaction.execute(
            s ->
                conflicts
                    .findById(conflictId)
                    .filter(c -> c.getOrganizationId().equals(actor.organizationId()))
                    .orElseThrow(() -> new ResourceNotFoundException("Conflito", conflictId)));
    accessControl.requireCommunity(loaded.getCommunityId(), Permission.SYNC_CONFLICT_RESOLVE);
    return decision == Decision.DISCARD
        ? discard(actor, conflictId, notes)
        : retry(actor, conflictId, notes);
  }

  private ConflictView discard(Actor actor, UUID conflictId, String notes) {
    return transaction.execute(
        s -> {
          Instant now = Instant.now(clock);
          SyncConflict conflict = conflicts.findById(conflictId).orElseThrow();
          conflict.resolveDiscarded(actor.userId(), notes, now);
          operations.findById(conflict.getOperationId()).orElseThrow().discarded(now);
          auditDecision(actor, conflict, "SYNC_CONFLICT_DISCARDED");
          return view(conflict);
        });
  }

  private ConflictView retry(Actor actor, UUID conflictId, String notes) {
    try {
      return transaction.execute(
          s -> {
            Instant now = Instant.now(clock);
            SyncConflict conflict = conflicts.findById(conflictId).orElseThrow();
            SyncOperation operation = operations.findById(conflict.getOperationId()).orElseThrow();
            Handler<?> handler =
                handlers.resolve(
                    operation.getOperationType(),
                    operation.getEntityType(),
                    operation.getSchemaVersion());
            handlers.execute(handler, parse(operation.getPayload()), operation.getCommunityId());
            operation.applied(changeLog.flush().orElse(0L), now);
            conflict.resolveApplied(actor.userId(), notes, now);
            auditDecision(actor, conflict, "SYNC_CONFLICT_APPLIED");
            return view(conflict);
          });
    } catch (RuntimeException e) {
      Optional<Classified> failure = SyncPushService.classify(e);
      if (failure.isEmpty()) {
        throw e;
      }
      return transaction.execute(
          s -> {
            SyncConflict conflict = conflicts.findById(conflictId).orElseThrow();
            conflict.retryFailed(
                failure.get().code(), failure.get().message(), actor.userId(), Instant.now(clock));
            return view(conflict);
          });
    }
  }

  private void auditDecision(Actor actor, SyncConflict conflict, String action) {
    audit.record(
        actor.organizationId(),
        conflict.getCommunityId(),
        actor.userId(),
        action,
        "SYNC_OPERATION",
        conflict.getOperationId(),
        Map.of("reasonCode", conflict.getReasonCode()));
  }

  private JsonNode parse(String payload) {
    try {
      return mapper.readTree(payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Stored payload is not JSON", e);
    }
  }

  private ConflictView view(SyncConflict conflict) {
    SyncOperation operation = operations.findById(conflict.getOperationId()).orElseThrow();
    return new ConflictView(
        conflict.getId(),
        conflict.getCommunityId(),
        conflict.getKind(),
        conflict.getStatus(),
        conflict.getReasonCode(),
        conflict.getMessage(),
        conflict.getCreatedAt(),
        conflict.getResolvedAt(),
        conflict.getResolutionNotes(),
        new OperationSummary(
            operation.getOperationId(),
            operation.getOperationType(),
            operation.getDeviceId(),
            operation.getUserId(),
            operation.getClientCreatedAt(),
            parse(operation.getPayload())),
        conflict.getEvents().stream()
            .map(
                e ->
                    new EventView(
                        e.getEventType(), e.getMessage(), e.getActorUserId(), e.getOccurredAt()))
            .toList());
  }

  public enum Decision {
    RETRY,
    DISCARD
  }

  public record OperationSummary(
      UUID operationId,
      String operationType,
      UUID deviceId,
      UUID userId,
      Instant createdAt,
      JsonNode payload) {}

  public record EventView(EventType type, String message, UUID actorUserId, Instant occurredAt) {}

  public record ConflictView(
      UUID id,
      UUID communityId,
      ConflictKind kind,
      ConflictStatus status,
      String reasonCode,
      String message,
      Instant createdAt,
      Instant resolvedAt,
      String resolutionNotes,
      OperationSummary operation,
      List<EventView> events) {}
}
