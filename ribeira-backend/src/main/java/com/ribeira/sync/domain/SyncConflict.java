package com.ribeira.sync.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.exception.StateConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Human review of an operation that could not be applied. Never deleted: the original operation,
 * the reason, every retry and the final decision remain auditable.
 */
@Entity
@Table(name = "sync_conflicts")
public class SyncConflict extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "operation_id", nullable = false, updatable = false)
  private UUID operationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private ConflictKind kind;

  @Column(name = "reason_code", nullable = false, length = 64)
  private String reasonCode;

  @Column(nullable = false, length = 1000)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ConflictStatus status = ConflictStatus.OPEN;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolution_notes", length = 1000)
  private String resolutionNotes;

  @OneToMany(mappedBy = "conflict", cascade = CascadeType.PERSIST)
  @OrderBy("occurredAt")
  private List<SyncConflictEvent> events = new ArrayList<>();

  @Version private long version;

  protected SyncConflict() {}

  public SyncConflict(
      SyncOperation operation, ConflictKind kind, String reasonCode, String message, Instant now) {
    super(UUID.randomUUID());
    this.organizationId = operation.getOrganizationId();
    this.communityId = operation.getCommunityId();
    this.operationId = operation.getOperationId();
    this.kind = kind;
    this.reasonCode = reasonCode;
    this.message = message;
    this.createdAt = now;
    events.add(new SyncConflictEvent(this, EventType.DETECTED, message, null, now));
  }

  public void retryFailed(String code, String failure, UUID actor, Instant now) {
    requireOpen();
    this.reasonCode = code;
    this.message = failure;
    events.add(new SyncConflictEvent(this, EventType.RETRY_FAILED, failure, actor, now));
  }

  public void resolveApplied(UUID actor, String notes, Instant now) {
    requireOpen();
    close(ConflictStatus.RESOLVED_APPLIED, actor, notes, now);
    events.add(new SyncConflictEvent(this, EventType.APPLIED, notes, actor, now));
  }

  public void resolveDiscarded(UUID actor, String notes, Instant now) {
    requireOpen();
    if (notes == null || notes.isBlank()) {
      throw new BusinessRuleException(
          "NOTES_REQUIRED", "Descartar uma operação exige justificativa.");
    }
    close(ConflictStatus.RESOLVED_DISCARDED, actor, notes, now);
    events.add(new SyncConflictEvent(this, EventType.DISCARDED, notes, actor, now));
  }

  private void close(ConflictStatus newStatus, UUID actor, String notes, Instant now) {
    this.status = newStatus;
    this.resolvedBy = actor;
    this.resolutionNotes = notes;
    this.resolvedAt = now;
  }

  private void requireOpen() {
    if (status != ConflictStatus.OPEN) {
      throw new StateConflictException("CONFLICT_ALREADY_RESOLVED", "O conflito já foi resolvido.");
    }
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public ConflictKind getKind() {
    return kind;
  }

  public String getReasonCode() {
    return reasonCode;
  }

  public String getMessage() {
    return message;
  }

  public ConflictStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public UUID getResolvedBy() {
    return resolvedBy;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public List<SyncConflictEvent> getEvents() {
    return List.copyOf(events);
  }

  public enum ConflictKind {
    /** Valid operation incompatible with the central state (balance, missing reference). */
    CONFLICT,
    /** Invalid operation (format, permission, rule independent of state). */
    REJECTED
  }

  public enum ConflictStatus {
    OPEN,
    RESOLVED_APPLIED,
    RESOLVED_DISCARDED
  }

  public enum EventType {
    DETECTED,
    RETRY_FAILED,
    APPLIED,
    DISCARDED
  }
}
