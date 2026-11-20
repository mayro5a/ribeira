package com.ribeira.community.application;

import com.ribeira.community.domain.Community;
import com.ribeira.community.domain.Community.CommunityDetails;
import com.ribeira.community.domain.CommunityRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {

  private final CommunityRepository communities;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;

  public CommunityService(
      CommunityRepository communities,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes) {
    this.communities = communities;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
  }

  @Transactional(readOnly = true)
  public List<CommunityView> listAccessible() {
    Set<UUID> ids = accessControl.communitiesWith(Permission.COMMUNITY_READ);
    return communities
        .findByOrganizationIdAndIdInOrderByName(accessControl.actor().organizationId(), ids)
        .stream()
        .map(CommunityView::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public CommunityView get(UUID id) {
    accessControl.requireCommunity(id, Permission.COMMUNITY_READ);
    return CommunityView.of(find(id));
  }

  @Transactional
  public CommunityView create(CommunityDetails details) {
    Actor actor = accessControl.requireOrganization(Permission.COMMUNITY_MANAGE);
    UUID id = UUID.randomUUID();
    ensureUniqueName(actor.organizationId(), details.name(), id);
    Community community = communities.save(new Community(id, actor.organizationId(), details));
    publish(actor, community, "COMMUNITY_CREATED");
    return CommunityView.of(community);
  }

  @Transactional
  public CommunityView update(UUID id, CommunityDetails details) {
    Actor actor = accessControl.requireOrganization(Permission.COMMUNITY_MANAGE);
    Community community = find(id);
    if (!community.getOrganizationId().equals(actor.organizationId())) {
      throw new ResourceNotFoundException("Comunidade", id);
    }
    ensureUniqueName(actor.organizationId(), details.name(), id);
    community.update(details);
    publish(actor, community, "COMMUNITY_UPDATED");
    return CommunityView.of(community);
  }

  /**
   * Restricts stock-changing synchronization to one device (docs/synchronization.md section 3.1). A
   * {@code null} device lifts the restriction.
   */
  @Transactional
  public CommunityView assignPrimaryDevice(UUID id, UUID deviceId) {
    Actor actor = accessControl.requireCommunity(id, Permission.DEVICE_MANAGE);
    Community community = find(id);
    community.assignPrimaryDevice(deviceId);
    audit.record(
        actor.organizationId(),
        id,
        actor.userId(),
        "PRIMARY_DEVICE_ASSIGNED",
        "COMMUNITY",
        id,
        deviceId == null ? Map.of() : Map.of("deviceId", deviceId));
    changes.record(actor.organizationId(), id, SyncEntityType.COMMUNITY, id);
    return CommunityView.of(community);
  }

  /** Internal query for the sync module; authorization is done by the caller. */
  @Transactional(readOnly = true)
  public Optional<UUID> primaryDeviceOf(UUID communityId) {
    return communities.findById(communityId).map(Community::getPrimaryDeviceId);
  }

  private void publish(Actor actor, Community community, String action) {
    audit.record(
        actor.organizationId(),
        community.getId(),
        actor.userId(),
        action,
        "COMMUNITY",
        community.getId(),
        Map.of());
    changes.record(
        actor.organizationId(), community.getId(), SyncEntityType.COMMUNITY, community.getId());
  }

  private void ensureUniqueName(UUID organizationId, String name, UUID id) {
    if (communities.existsByOrganizationIdAndNameIgnoreCaseAndIdNot(
        organizationId, name.trim(), id)) {
      throw new StateConflictException(
          "COMMUNITY_NAME_IN_USE", "Já existe uma comunidade com este nome.");
    }
  }

  private Community find(UUID id) {
    return communities
        .findById(id)
        .orElseThrow(() -> new ResourceNotFoundException("Comunidade", id));
  }
}
