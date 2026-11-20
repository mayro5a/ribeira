package com.ribeira.alert.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.shared.exception.StateConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Something that needs human attention. Alerts never assert that food is unsafe; they recommend an
 * inspection and stay open until a person records what was found.
 */
@Entity
@Table(name = "alerts")
public class Alert extends AssignedIdEntity {

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32, updatable = false)
  private AlertType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  private Severity severity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private AlertStatus status = AlertStatus.OPEN;

  @Column(name = "subject_type", nullable = false, length = 32, updatable = false)
  private String subjectType;

  @Column(name = "subject_id", updatable = false)
  private UUID subjectId;

  /** At most one open alert per key (partial unique index). */
  @Column(name = "dedup_key", nullable = false, length = 160, updatable = false)
  private String dedupKey;

  @Column(nullable = false, length = 1000, updatable = false)
  private String message;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "resolved_at")
  private Instant resolvedAt;

  @Column(name = "resolved_by")
  private UUID resolvedBy;

  @Column(name = "resolution_notes", length = 1000)
  private String resolutionNotes;

  @Column(name = "inspection_performed")
  private Boolean inspectionPerformed;

  @Version private long version;

  protected Alert() {}

  public Alert(
      UUID organizationId,
      UUID communityId,
      AlertType type,
      Severity severity,
      String subjectType,
      UUID subjectId,
      String dedupKey,
      String message,
      Instant createdAt) {
    super(UUID.randomUUID());
    this.organizationId = organizationId;
    this.communityId = communityId;
    this.type = type;
    this.severity = severity;
    this.subjectType = subjectType;
    this.subjectId = subjectId;
    this.dedupKey = dedupKey;
    this.message = message;
    this.createdAt = createdAt;
  }

  public void resolve(UUID userId, String notes, boolean inspected, Instant when) {
    if (status == AlertStatus.RESOLVED) {
      throw new StateConflictException("ALERT_ALREADY_RESOLVED", "O alerta já foi resolvido.");
    }
    this.status = AlertStatus.RESOLVED;
    this.resolvedBy = userId;
    this.resolutionNotes = notes;
    this.inspectionPerformed = inspected;
    this.resolvedAt = when;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public AlertType getType() {
    return type;
  }

  public Severity getSeverity() {
    return severity;
  }

  public AlertStatus getStatus() {
    return status;
  }

  public String getSubjectType() {
    return subjectType;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public String getMessage() {
    return message;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public String getResolutionNotes() {
    return resolutionNotes;
  }

  public Boolean getInspectionPerformed() {
    return inspectionPerformed;
  }

  public enum AlertType {
    EXPIRING_SOON,
    LOW_STOCK,
    LOSS_INCREASE,
    READING_OUT_OF_RANGE,
    INSPECTION_OVERDUE
  }

  public enum Severity {
    INFO,
    WARNING,
    CRITICAL
  }

  public enum AlertStatus {
    OPEN,
    RESOLVED
  }
}
