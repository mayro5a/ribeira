package com.ribeira.inventory.application;

import com.ribeira.inventory.application.InventoryViews.StorageLocationView;
import com.ribeira.inventory.domain.StorageLocation;
import com.ribeira.inventory.domain.StorageLocationRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StorageLocationService {

  private final StorageLocationRepository locations;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;

  public StorageLocationService(
      StorageLocationRepository locations,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes) {
    this.locations = locations;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
  }

  @Transactional(readOnly = true)
  public List<StorageLocationView> list(UUID communityId) {
    accessControl.requireCommunity(communityId, Permission.COMMUNITY_READ);
    return locations.findByCommunityIdOrderByName(communityId).stream()
        .map(StorageLocationView::of)
        .toList();
  }

  @Transactional
  public StorageLocationView create(UUID communityId, String name, String description) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.STOCK_WRITE);
    UUID id = UUID.randomUUID();
    ensureUniqueName(communityId, name, id);
    StorageLocation location =
        locations.save(
            new StorageLocation(id, actor.organizationId(), communityId, name, description));
    publish(actor, location, "STORAGE_LOCATION_CREATED");
    return StorageLocationView.of(location);
  }

  @Transactional
  public StorageLocationView update(UUID id, String name, String description, boolean active) {
    Actor actor = accessControl.actor();
    StorageLocation location =
        locations
            .findById(id)
            .filter(l -> l.getOrganizationId().equals(actor.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Local de armazenamento", id));
    accessControl.requireCommunity(location.getCommunityId(), Permission.STOCK_WRITE);
    ensureUniqueName(location.getCommunityId(), name, id);
    location.update(name, description, active);
    publish(actor, location, "STORAGE_LOCATION_UPDATED");
    return StorageLocationView.of(location);
  }

  /** For monitoring: the location must belong to the community. */
  @Transactional(readOnly = true)
  public StorageLocationView requireInCommunity(UUID communityId, UUID locationId) {
    return locations
        .findById(locationId)
        .filter(l -> l.getCommunityId().equals(communityId))
        .map(StorageLocationView::of)
        .orElseThrow(() -> new ResourceNotFoundException("Local de armazenamento", locationId));
  }

  @Transactional(readOnly = true)
  public List<StorageLocationView> activeIn(UUID communityId) {
    return locations.findByCommunityIdOrderByName(communityId).stream()
        .filter(StorageLocation::isActive)
        .map(StorageLocationView::of)
        .toList();
  }

  private void ensureUniqueName(UUID communityId, String name, UUID id) {
    if (locations.existsByCommunityIdAndNameIgnoreCaseAndIdNot(communityId, name.trim(), id)) {
      throw new StateConflictException(
          "STORAGE_LOCATION_NAME_IN_USE", "Já existe um local com este nome na comunidade.");
    }
  }

  private void publish(Actor actor, StorageLocation location, String action) {
    audit.record(
        actor.organizationId(),
        location.getCommunityId(),
        actor.userId(),
        action,
        "STORAGE_LOCATION",
        location.getId(),
        Map.of());
    changes.record(
        actor.organizationId(),
        location.getCommunityId(),
        SyncEntityType.STORAGE_LOCATION,
        location.getId());
  }
}
