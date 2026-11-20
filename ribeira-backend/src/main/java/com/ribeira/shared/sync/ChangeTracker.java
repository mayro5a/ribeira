package com.ribeira.shared.sync;

import java.util.UUID;

/**
 * Records that an entity changed in the current transaction so devices receive it on pull. Business
 * modules call this after every write of a synchronized entity; the sync module assigns feed
 * positions right before commit (ADR-0003).
 *
 * @param communityId {@code null} for organization-wide entities (foods, donors)
 */
public interface ChangeTracker {

  void record(UUID organizationId, UUID communityId, SyncEntityType type, UUID entityId);
}
