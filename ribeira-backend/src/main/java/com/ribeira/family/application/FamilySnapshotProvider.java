package com.ribeira.family.application;

import com.ribeira.family.application.FamilyService.FamilyView;
import com.ribeira.family.domain.FamilyRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class FamilySnapshotProvider implements SyncSnapshotProvider {

  private final FamilyRepository families;

  FamilySnapshotProvider(FamilyRepository families) {
    this.families = families;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.FAMILY;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return families.findByOrganizationIdAndIdIn(organizationId, ids).stream()
        .map(FamilyView::of)
        .collect(Collectors.toMap(FamilyView::id, v -> (Object) v));
  }
}
