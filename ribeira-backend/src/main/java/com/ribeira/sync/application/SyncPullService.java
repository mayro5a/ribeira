package com.ribeira.sync.application;

import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import com.ribeira.sync.domain.Device;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serves the change feed after a server-issued cursor (docs/synchronization.md section 4). Only
 * entities of communities the user may read are delivered; families additionally need FAMILY_READ.
 */
@Service
public class SyncPullService {

  public static final int DEFAULT_LIMIT = 200;
  public static final int MAX_LIMIT = 500;

  private final JdbcTemplate jdbc;
  private final List<SyncSnapshotProvider> providers;
  private final DeviceService devices;
  private final AccessControl accessControl;
  private final Clock clock;

  public SyncPullService(
      JdbcTemplate jdbc,
      List<SyncSnapshotProvider> providers,
      DeviceService devices,
      AccessControl accessControl,
      Clock clock) {
    this.jdbc = jdbc;
    this.providers = providers;
    this.devices = devices;
    this.accessControl = accessControl;
    this.clock = clock;
  }

  @Transactional
  public PullResponse pull(UUID deviceId, String cursor, Integer requestedLimit) {
    Actor actor = accessControl.member();
    Device device = devices.requireActiveDevice(actor, deviceId);
    long after = parseCursor(cursor);
    int limit = requestedLimit == null ? DEFAULT_LIMIT : Math.clamp(requestedLimit, 1, MAX_LIMIT);

    List<FeedEntry> entries =
        jdbc.query(
            """
            SELECT seq, community_id, entity_type, entity_id FROM change_log
            WHERE organization_id = ? AND seq > ? ORDER BY seq LIMIT ?
            """,
            (rs, i) ->
                new FeedEntry(
                    rs.getLong("seq"),
                    rs.getObject("community_id", UUID.class),
                    SyncEntityType.valueOf(rs.getString("entity_type")),
                    rs.getObject("entity_id", UUID.class)),
            actor.organizationId(),
            after,
            limit + 1);
    boolean hasMore = entries.size() > limit;
    List<FeedEntry> page = hasMore ? entries.subList(0, limit) : entries;

    Set<UUID> readable = accessControl.communitiesWith(Permission.COMMUNITY_READ);
    Set<UUID> familyReadable = accessControl.communitiesWith(Permission.FAMILY_READ);
    // Keep only the latest entry of each entity within the page; data is the current snapshot.
    Map<List<Object>, FeedEntry> latest = new LinkedHashMap<>();
    for (FeedEntry entry : page) {
      boolean visible =
          entry.communityId() == null
              ? true
              : entry.type() == SyncEntityType.FAMILY
                  ? familyReadable.contains(entry.communityId())
                  : readable.contains(entry.communityId());
      if (visible) {
        List<Object> key = List.of(entry.type(), entry.entityId());
        latest.remove(key);
        latest.put(key, entry);
      }
    }
    Map<SyncEntityType, Map<UUID, Object>> snapshots = loadSnapshots(actor, latest.values());
    List<Change> changes = new ArrayList<>();
    for (FeedEntry entry : latest.values()) {
      Object data = snapshots.getOrDefault(entry.type(), Map.of()).get(entry.entityId());
      if (data != null) {
        changes.add(
            new Change(entry.seq(), entry.type(), entry.entityId(), entry.communityId(), data));
      }
    }
    long next = page.isEmpty() ? after : page.getLast().seq();
    saveCheckpoint(device.getId(), after);
    return new PullResponse(changes, Long.toString(next), hasMore, Instant.now(clock));
  }

  /** Highest feed position of the organization, used by the status endpoint. */
  @Transactional(readOnly = true)
  public long currentPosition(UUID organizationId) {
    Long seq =
        jdbc.query(
            "SELECT last_seq FROM sync_counters WHERE organization_id = ?",
            rs -> rs.next() ? rs.getLong(1) : 0L,
            organizationId);
    return seq == null ? 0 : seq;
  }

  @Transactional(readOnly = true)
  public Long checkpointOf(UUID deviceId) {
    return jdbc.query(
        "SELECT last_cursor FROM sync_checkpoints WHERE device_id = ?",
        rs -> rs.next() ? rs.getLong(1) : null,
        deviceId);
  }

  private Map<SyncEntityType, Map<UUID, Object>> loadSnapshots(
      Actor actor, Iterable<FeedEntry> entries) {
    Map<SyncEntityType, List<UUID>> idsByType = new EnumMap<>(SyncEntityType.class);
    entries.forEach(
        e -> idsByType.computeIfAbsent(e.type(), t -> new ArrayList<>()).add(e.entityId()));
    Map<SyncEntityType, Map<UUID, Object>> result = new EnumMap<>(SyncEntityType.class);
    idsByType.forEach(
        (type, ids) ->
            result.put(
                type,
                providers.stream()
                    .filter(p -> p.supports(type))
                    .findFirst()
                    .orElseThrow(
                        () -> new IllegalStateException("No snapshot provider for " + type))
                    .snapshots(type, actor.organizationId(), ids)));
    return result;
  }

  /** The cursor a device sends is the last position it confirmed it has applied. */
  private void saveCheckpoint(UUID deviceId, long confirmed) {
    jdbc.update(
        """
        INSERT INTO sync_checkpoints (device_id, last_cursor, updated_at) VALUES (?, ?, ?)
        ON CONFLICT (device_id) DO UPDATE SET last_cursor = EXCLUDED.last_cursor,
                                              updated_at = EXCLUDED.updated_at
        """,
        deviceId,
        confirmed,
        Timestamp.from(Instant.now(clock)));
  }

  private static long parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return 0;
    }
    try {
      long value = Long.parseLong(cursor);
      if (value < 0) {
        throw new NumberFormatException();
      }
      return value;
    } catch (NumberFormatException e) {
      throw new BusinessRuleException("INVALID_CURSOR", "Cursor de sincronização inválido.");
    }
  }

  private record FeedEntry(long seq, UUID communityId, SyncEntityType type, UUID entityId) {}

  public record Change(
      long seq, SyncEntityType entityType, UUID entityId, UUID communityId, Object data) {}

  public record PullResponse(
      List<Change> changes, String nextCursor, boolean hasMore, Instant serverTime) {}
}
