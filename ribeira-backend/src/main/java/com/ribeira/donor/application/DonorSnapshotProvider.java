package com.ribeira.donor.application;

import com.ribeira.donor.application.DonorService.DonorView;
import com.ribeira.donor.domain.DonorRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DonorSnapshotProvider implements SyncSnapshotProvider {

  private final DonorRepository donors;

  DonorSnapshotProvider(DonorRepository donors) {
    this.donors = donors;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.DONOR;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return donors.findByOrganizationIdAndIdIn(organizationId, ids).stream()
        .map(DonorView::of)
        .collect(Collectors.toMap(DonorView::id, Function.identity()));
  }
}
