package com.ribeira.alert.application;

import com.ribeira.alert.domain.Alert;
import com.ribeira.alert.domain.Alert.AlertStatus;
import com.ribeira.alert.domain.Alert.AlertType;
import com.ribeira.alert.domain.Alert.Severity;
import com.ribeira.alert.domain.AlertRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {

  private final AlertRepository alerts;
  private final AlertRules rules;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;
  private final Clock clock;

  public AlertService(
      AlertRepository alerts,
      AlertRules rules,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes,
      Clock clock) {
    this.alerts = alerts;
    this.rules = rules;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page<AlertView> list(UUID communityId, AlertStatus status, Pageable pageable) {
    accessControl.requireCommunity(communityId, Permission.COMMUNITY_READ);
    return alerts.search(communityId, status, pageable).map(AlertView::of);
  }

  /** Records the inspection outcome; the alert itself is kept for history. */
  @Transactional
  public AlertView resolve(UUID id, String notes, boolean inspectionPerformed) {
    Actor actor = accessControl.actor();
    Alert alert =
        alerts
            .findById(id)
            .filter(a -> a.getOrganizationId().equals(actor.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Alerta", id));
    accessControl.requireCommunity(alert.getCommunityId(), Permission.ALERT_RESOLVE);
    alert.resolve(actor.userId(), notes, inspectionPerformed, Instant.now(clock));
    audit.record(
        actor.organizationId(),
        alert.getCommunityId(),
        actor.userId(),
        "ALERT_RESOLVED",
        "ALERT",
        id,
        Map.of("inspectionPerformed", inspectionPerformed));
    changes.record(actor.organizationId(), alert.getCommunityId(), SyncEntityType.ALERT, id);
    return AlertView.of(alert);
  }

  /** On-demand evaluation (the scheduler also runs every hour). */
  @Transactional
  public int evaluate(UUID communityId) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.ALERT_RESOLVE);
    return rules.evaluateCommunity(actor.organizationId(), communityId);
  }

  @Transactional(readOnly = true)
  public long countOpen(Collection<UUID> communityIds) {
    return communityIds.isEmpty()
        ? 0
        : alerts.countByCommunityIdInAndStatus(communityIds, AlertStatus.OPEN);
  }

  public record AlertView(
      UUID id,
      UUID communityId,
      AlertType type,
      Severity severity,
      AlertStatus status,
      String subjectType,
      UUID subjectId,
      String message,
      Instant createdAt,
      Instant resolvedAt,
      String resolutionNotes) {
    static AlertView of(Alert a) {
      return new AlertView(
          a.getId(),
          a.getCommunityId(),
          a.getType(),
          a.getSeverity(),
          a.getStatus(),
          a.getSubjectType(),
          a.getSubjectId(),
          a.getMessage(),
          a.getCreatedAt(),
          a.getResolvedAt(),
          a.getResolutionNotes());
    }
  }
}
