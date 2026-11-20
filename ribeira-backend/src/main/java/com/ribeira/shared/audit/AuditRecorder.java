package com.ribeira.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail inside the caller's transaction, so an audited change and its audit entry
 * commit or roll back together. Details must never contain personal data, passwords or tokens.
 */
@Component
public class AuditRecorder {

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public AuditRecorder(JdbcClient jdbc, ObjectMapper objectMapper, Clock clock) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      UUID organizationId,
      UUID communityId,
      UUID actorUserId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, ?> details) {
    jdbc.sql(
            """
            INSERT INTO audit_logs (id, organization_id, community_id, actor_user_id, action,
                                    entity_type, entity_id, details, occurred_at)
            VALUES (:id, :org, :community, :actor, :action, :entityType, :entityId,
                    CAST(:details AS jsonb), :occurredAt)
            """)
        .param("id", UUID.randomUUID())
        .param("org", organizationId)
        .param("community", communityId)
        .param("actor", actorUserId)
        .param("action", action)
        .param("entityType", entityType)
        .param("entityId", entityId)
        .param("details", toJson(details))
        .param("occurredAt", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
        .update();
  }

  private String toJson(Map<String, ?> details) {
    if (details == null || details.isEmpty()) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(details);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Audit details are not serializable", e);
    }
  }
}
