package com.ribeira.monitoring.application;

import com.ribeira.monitoring.application.MonitoringService.ReadingView;
import com.ribeira.monitoring.domain.EnvironmentalReadingRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class ReadingSnapshotProvider implements SyncSnapshotProvider {

  private final EnvironmentalReadingRepository readings;

  ReadingSnapshotProvider(EnvironmentalReadingRepository readings) {
    this.readings = readings;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.READING;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return readings.findByOrganizationIdAndIdIn(organizationId, ids).stream()
        .map(ReadingView::of)
        .collect(Collectors.toMap(ReadingView::id, v -> (Object) v));
  }
}
