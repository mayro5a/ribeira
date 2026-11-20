package com.ribeira.shared.sync;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Supplies the current state of entities for the pull feed. Each module that publishes changes
 * implements it for its own entity types, so the sync module never reads other modules' tables.
 */
public interface SyncSnapshotProvider {

  boolean supports(SyncEntityType type);

  /**
   * Snapshots keyed by entity ID, already restricted to the given organization. Entities that no
   * longer exist are simply absent.
   */
  Map<UUID, Object> snapshots(SyncEntityType type, UUID organizationId, Collection<UUID> ids);
}
