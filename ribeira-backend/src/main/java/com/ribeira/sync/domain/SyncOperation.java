package com.ribeira.sync.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Idempotency record of an offline operation (RN08). Stored in the same transaction as its business
 * effects; the original payload is kept for review and retries.
 */
@Entity
@Table(name = "sync_operations")
public class SyncOperation {

  @Id
  @Column(name = "operation_id")
  private UUID operationId;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "community_id", nullable = false, updatable = false)
  private UUID communityId;

  @Column(name = "device_id", nullable = false, updatable = false)
  private UUID deviceId;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "entity_type", nullable = false, updatable = false, length = 24)
  private String entityType;

  @Column(name = "operation_type", nullable = false, updatable = false, length = 32)
  private String operationType;

  @Column(name = "schema_version", nullable = false, updatable = false)
  private int schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, updatable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "payload_hash", nullable = false, updatable = false, length = 64)
  private String payloadHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private OperationStatus status;

  @Column(name = "error_code", length = 64)
  private String errorCode;

  @Column(name = "error_message", length = 1000)
  private String errorMessage;

  @Column(name = "server_seq")
  private Long serverSeq;

  @Column(name = "client_created_at", nullable = false, updatable = false)
  private Instant clientCreatedAt;

  @Column(name = "received_at", nullable = false, updatable = false)
  private Instant receivedAt;

  @Column(name = "processed_at", nullable = false)
  private Instant processedAt;

  @Version private long version;

  protected SyncOperation() {}

  public SyncOperation(OperationEnvelope envelope, UUID organizationId, UUID userId, Instant now) {
    this.operationId = envelope.operationId();
    this.organizationId = organizationId;
    this.communityId = envelope.communityId();
    this.deviceId = envelope.deviceId();
    this.userId = userId;
    this.entityType = envelope.entityType();
    this.operationType = envelope.operationType();
    this.schemaVersion = envelope.schemaVersion();
    this.payload = envelope.payloadJson();
    this.payloadHash = envelope.payloadHash();
    this.clientCreatedAt = envelope.createdAt();
    this.receivedAt = now;
    this.processedAt = now;
  }

  public void applied(long seq, Instant now) {
    this.status = OperationStatus.APPLIED;
    this.serverSeq = seq;
    this.errorCode = null;
    this.errorMessage = null;
    this.processedAt = now;
  }

  public void failed(OperationStatus failure, String code, String message, Instant now) {
    if (failure != OperationStatus.CONFLICT && failure != OperationStatus.REJECTED) {
      throw new IllegalArgumentException("Not a failure status: " + failure);
    }
    this.status = failure;
    this.errorCode = code;
    this.errorMessage = truncate(message);
    this.processedAt = now;
  }

  public void discarded(Instant now) {
    this.status = OperationStatus.DISCARDED;
    this.processedAt = now;
  }

  private static String truncate(String message) {
    return message == null || message.length() <= 1000 ? message : message.substring(0, 1000);
  }

  public UUID getOperationId() {
    return operationId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCommunityId() {
    return communityId;
  }

  public UUID getDeviceId() {
    return deviceId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getEntityType() {
    return entityType;
  }

  public String getOperationType() {
    return operationType;
  }

  public int getSchemaVersion() {
    return schemaVersion;
  }

  public String getPayload() {
    return payload;
  }

  public String getPayloadHash() {
    return payloadHash;
  }

  public OperationStatus getStatus() {
    return status;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Long getServerSeq() {
    return serverSeq;
  }

  public Instant getClientCreatedAt() {
    return clientCreatedAt;
  }

  public enum OperationStatus {
    APPLIED,
    REJECTED,
    CONFLICT,
    DISCARDED
  }

  /** A validated operation as received, with its canonical payload and hash. */
  public record OperationEnvelope(
      UUID operationId,
      UUID deviceId,
      UUID communityId,
      String entityType,
      String operationType,
      int schemaVersion,
      Instant createdAt,
      String payloadJson,
      String payloadHash) {}
}
