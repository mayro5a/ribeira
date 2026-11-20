package com.ribeira.donation.application;

import com.ribeira.donation.domain.DonationRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class DonationSnapshotProvider implements SyncSnapshotProvider {

  private final DonationRepository donations;

  DonationSnapshotProvider(DonationRepository donations) {
    this.donations = donations;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.DONATION;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return donations.findWithItems(organizationId, ids).stream()
        .map(DonationView::of)
        .collect(Collectors.toMap(DonationView::id, v -> (Object) v));
  }
}
