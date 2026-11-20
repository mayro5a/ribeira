package com.ribeira.sync.application;

import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Buffers changes per transaction and writes them to {@code change_log} right before commit
 * (ADR-0003). The organization counter row is locked as the last step of the transaction, which
 * makes feed order equal to commit order and avoids gaps between pull pages.
 */
@Component
public class ChangeLogRecorder implements ChangeTracker {

  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;
  private final Clock clock;

  public ChangeLogRecorder(JdbcTemplate jdbc, EntityManager entityManager, Clock clock) {
    this.jdbc = jdbc;
    this.entityManager = entityManager;
    this.clock = clock;
  }

  @Override
  public void record(UUID organizationId, UUID communityId, SyncEntityType type, UUID entityId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Changes must be recorded inside a transaction");
    }
    currentBuffer().add(new Change(organizationId, communityId, type, entityId));
  }

  /**
   * Writes the changes recorded so far and returns the highest feed position assigned in this
   * transaction. Used by sync push to tell the device where the operation's effects are.
   */
  public OptionalLong flush() {
    Buffer buffer = (Buffer) TransactionSynchronizationManager.getResource(this);
    if (buffer == null) {
      return OptionalLong.empty();
    }
    buffer.write();
    return buffer.highestSeq;
  }

  private Buffer currentBuffer() {
    Buffer buffer = (Buffer) TransactionSynchronizationManager.getResource(this);
    if (buffer == null) {
      buffer = new Buffer();
      TransactionSynchronizationManager.bindResource(this, buffer);
      TransactionSynchronizationManager.registerSynchronization(buffer);
    }
    return buffer;
  }

  private record Change(UUID organizationId, UUID communityId, SyncEntityType type, UUID id) {
    Object key() {
      return List.of(type, id);
    }
  }

  private final class Buffer implements TransactionSynchronization {

    /** Deduplicated: an entity changed several times in one transaction is published once. */
    private final Map<Object, Change> pending = new LinkedHashMap<>();

    private OptionalLong highestSeq = OptionalLong.empty();

    void add(Change change) {
      pending.remove(change.key());
      pending.put(change.key(), change);
    }

    void write() {
      if (pending.isEmpty()) {
        return;
      }
      // Flush entity writes first so the counter really is the last lock taken.
      entityManager.flush();
      Map<UUID, List<Change>> byOrganization = new LinkedHashMap<>();
      pending
          .values()
          .forEach(
              c ->
                  byOrganization
                      .computeIfAbsent(c.organizationId(), k -> new ArrayList<>())
                      .add(c));
      pending.clear();
      Timestamp now = Timestamp.from(Instant.now(clock));
      byOrganization.forEach(
          (organizationId, changes) -> writeForOrganization(organizationId, changes, now));
    }

    private void writeForOrganization(UUID organizationId, List<Change> changes, Timestamp now) {
      long last =
          jdbc.queryForObject(
              """
              INSERT INTO sync_counters (organization_id, last_seq) VALUES (?, ?)
              ON CONFLICT (organization_id)
              DO UPDATE SET last_seq = sync_counters.last_seq + EXCLUDED.last_seq
              RETURNING last_seq
              """,
              Long.class,
              organizationId,
              (long) changes.size());
      long first = last - changes.size() + 1;
      List<Object[]> rows = new ArrayList<>(changes.size());
      for (int i = 0; i < changes.size(); i++) {
        Change change = changes.get(i);
        rows.add(
            new Object[] {
              organizationId,
              first + i,
              change.communityId(),
              change.type().name(),
              change.id(),
              now
            });
      }
      jdbc.batchUpdate(
          """
          INSERT INTO change_log (organization_id, seq, community_id, entity_type, entity_id,
                                  changed_at)
          VALUES (?, ?, ?, ?, ?, ?)
          """,
          rows);
      long current = highestSeq.orElse(0L);
      highestSeq = OptionalLong.of(Math.max(current, last));
    }

    @Override
    public void beforeCommit(boolean readOnly) {
      write();
    }

    @Override
    public void afterCompletion(int status) {
      TransactionSynchronizationManager.unbindResourceIfPossible(ChangeLogRecorder.this);
    }
  }
}
