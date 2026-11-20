package com.ribeira.sync.domain;

import com.ribeira.shared.domain.AssignedIdEntity;
import com.ribeira.sync.domain.SyncConflict.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "sync_conflict_events")
public class SyncConflictEvent extends AssignedIdEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "conflict_id", nullable = false)
  private SyncConflict conflict;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 24)
  private EventType eventType;

  @Column(length = 1000)
  private String message;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected SyncConflictEvent() {}

  SyncConflictEvent(
      SyncConflict conflict, EventType eventType, String message, UUID actor, Instant occurredAt) {
    super(UUID.randomUUID());
    this.conflict = conflict;
    this.eventType = eventType;
    this.message =
        message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
    this.actorUserId = actor;
    this.occurredAt = occurredAt;
  }

  public EventType getEventType() {
    return eventType;
  }

  public String getMessage() {
    return message;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
