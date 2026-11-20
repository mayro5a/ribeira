package com.ribeira.alert.application;

import com.ribeira.alert.application.AlertService.AlertView;
import com.ribeira.alert.domain.AlertRepository;
import com.ribeira.shared.sync.SyncEntityType;
import com.ribeira.shared.sync.SyncSnapshotProvider;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AlertSnapshotProvider implements SyncSnapshotProvider {

  private final AlertRepository alerts;

  AlertSnapshotProvider(AlertRepository alerts) {
    this.alerts = alerts;
  }

  @Override
  public boolean supports(SyncEntityType type) {
    return type == SyncEntityType.ALERT;
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, Object> snapshots(
      SyncEntityType type, UUID organizationId, Collection<UUID> ids) {
    return alerts.findByOrganizationIdAndIdIn(organizationId, ids).stream()
        .map(AlertView::of)
        .collect(Collectors.toMap(AlertView::id, v -> (Object) v));
  }
}
