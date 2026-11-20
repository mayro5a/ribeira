package com.ribeira.monitoring.application;

import com.ribeira.inventory.application.StorageLocationService;
import com.ribeira.monitoring.domain.EnvironmentalReading;
import com.ribeira.monitoring.domain.EnvironmentalReading.ReadingSource;
import com.ribeira.monitoring.domain.EnvironmentalReadingRepository;
import com.ribeira.monitoring.domain.StorageCondition;
import com.ribeira.monitoring.domain.StorageCondition.ConditionSpec;
import com.ribeira.monitoring.domain.StorageConditionRepository;
import com.ribeira.shared.audit.AuditRecorder;
import com.ribeira.shared.exception.ResourceNotFoundException;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MonitoringService {

  private final EnvironmentalReadingRepository readings;
  private final StorageConditionRepository conditions;
  private final StorageLocationService locations;
  private final AccessControl accessControl;
  private final AuditRecorder audit;
  private final ChangeTracker changes;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public MonitoringService(
      EnvironmentalReadingRepository readings,
      StorageConditionRepository conditions,
      StorageLocationService locations,
      AccessControl accessControl,
      AuditRecorder audit,
      ChangeTracker changes,
      ApplicationEventPublisher events,
      Clock clock) {
    this.readings = readings;
    this.conditions = conditions;
    this.locations = locations;
    this.accessControl = accessControl;
    this.audit = audit;
    this.changes = changes;
    this.events = events;
    this.clock = clock;
  }

  /** Records a reading; alert rules react to {@link ReadingRecorded} in the same transaction. */
  @Transactional
  public ReadingView recordReading(RecordReading command) {
    Actor actor =
        accessControl.requireCommunity(command.communityId(), Permission.MONITORING_WRITE);
    locations.requireInCommunity(command.communityId(), command.storageLocationId());
    if (readings.existsById(command.readingId())) {
      throw new StateConflictException(
          "DUPLICATE_ID", "Leitura já registrada: " + command.readingId());
    }
    EnvironmentalReading reading =
        readings.save(
            new EnvironmentalReading(
                command.readingId(),
                actor.organizationId(),
                command.communityId(),
                command.storageLocationId(),
                command.temperature(),
                command.humidity(),
                ReadingSource.MANUAL,
                command.inspection(),
                command.notes(),
                command.recordedAt(),
                actor.userId(),
                Instant.now(clock)));
    changes.record(
        actor.organizationId(), command.communityId(), SyncEntityType.READING, reading.getId());
    audit.record(
        actor.organizationId(),
        command.communityId(),
        actor.userId(),
        "READING_RECORDED",
        "READING",
        reading.getId(),
        Map.of("inspection", command.inspection()));
    events.publishEvent(
        new ReadingRecorded(
            actor.organizationId(),
            command.communityId(),
            command.storageLocationId(),
            reading.getId(),
            command.temperature(),
            command.humidity()));
    return ReadingView.of(reading);
  }

  @Transactional(readOnly = true)
  public Page<ReadingView> readings(UUID communityId, UUID locationId, Pageable pageable) {
    accessControl.requireCommunity(communityId, Permission.COMMUNITY_READ);
    return readings.search(communityId, locationId, pageable).map(ReadingView::of);
  }

  @Transactional(readOnly = true)
  public List<ConditionView> conditions() {
    Actor actor = accessControl.member();
    return conditions.findByOrganizationId(actor.organizationId()).stream()
        .map(ConditionView::of)
        .toList();
  }

  @Transactional
  public ConditionView createCondition(ConditionSpec spec) {
    Actor actor = accessControl.requireOrganization(Permission.COMMUNITY_MANAGE);
    StorageCondition condition =
        conditions.save(new StorageCondition(UUID.randomUUID(), actor.organizationId(), spec));
    recordConditionChange(actor, condition, "STORAGE_CONDITION_CREATED");
    return ConditionView.of(condition);
  }

  @Transactional
  public ConditionView updateCondition(UUID id, ConditionSpec spec) {
    Actor actor = accessControl.requireOrganization(Permission.COMMUNITY_MANAGE);
    StorageCondition condition =
        conditions
            .findById(id)
            .filter(c -> c.getOrganizationId().equals(actor.organizationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Condição de armazenamento", id));
    condition.update(spec);
    recordConditionChange(actor, condition, "STORAGE_CONDITION_UPDATED");
    return ConditionView.of(condition);
  }

  /** For alert rules: conditions of the location itself or of the categories stored there. */
  @Transactional(readOnly = true)
  public List<StorageCondition> applicableConditions(
      UUID organizationId, UUID locationId, Collection<String> categories) {
    return conditions.findApplicable(
        organizationId, locationId, categories.isEmpty() ? List.of("-") : categories);
  }

  @Transactional(readOnly = true)
  public List<StorageCondition> inspectionRules(UUID organizationId) {
    return conditions.findWithInspectionRule(organizationId);
  }

  @Transactional(readOnly = true)
  public Map<UUID, Instant> lastInspections(Collection<UUID> locationIds) {
    if (locationIds.isEmpty()) {
      return Map.of();
    }
    return readings.lastInspections(locationIds).stream()
        .collect(
            Collectors.toMap(
                EnvironmentalReadingRepository.LastInspection::getLocationId,
                EnvironmentalReadingRepository.LastInspection::getLastInspection));
  }

  private void recordConditionChange(Actor actor, StorageCondition condition, String action) {
    audit.record(
        actor.organizationId(),
        null,
        actor.userId(),
        action,
        "STORAGE_CONDITION",
        condition.getId(),
        Map.of("guidanceSource", condition.getGuidanceSource()));
  }

  /** READING_RECORD, shared by REST and synchronization. */
  public record RecordReading(
      @NotNull UUID communityId,
      @NotNull UUID readingId,
      @NotNull UUID storageLocationId,
      @DecimalMin("-50") @DecimalMax("80") BigDecimal temperature,
      @DecimalMin("0") @DecimalMax("100") BigDecimal humidity,
      boolean inspection,
      @Size(max = 1000) String notes,
      @NotNull Instant recordedAt) {}

  public record ReadingRecorded(
      UUID organizationId,
      UUID communityId,
      UUID storageLocationId,
      UUID readingId,
      BigDecimal temperature,
      BigDecimal humidity) {}

  public record ReadingView(
      UUID id,
      UUID communityId,
      UUID storageLocationId,
      BigDecimal temperature,
      BigDecimal humidity,
      ReadingSource source,
      boolean inspection,
      String notes,
      Instant recordedAt,
      UUID recordedBy) {
    static ReadingView of(EnvironmentalReading r) {
      return new ReadingView(
          r.getId(),
          r.getCommunityId(),
          r.getStorageLocationId(),
          r.getTemperature(),
          r.getHumidity(),
          r.getSource(),
          r.isInspection(),
          r.getNotes(),
          r.getRecordedAt(),
          r.getRecordedBy());
    }
  }

  public record ConditionView(
      UUID id,
      UUID storageLocationId,
      String foodCategory,
      BigDecimal minTemperature,
      BigDecimal maxTemperature,
      BigDecimal minHumidity,
      BigDecimal maxHumidity,
      Integer maxDaysWithoutInspection,
      String guidanceSource,
      boolean active) {
    static ConditionView of(StorageCondition c) {
      return new ConditionView(
          c.getId(),
          c.getStorageLocationId(),
          c.getFoodCategory(),
          c.getMinTemperature(),
          c.getMaxTemperature(),
          c.getMinHumidity(),
          c.getMaxHumidity(),
          c.getMaxDaysWithoutInspection(),
          c.getGuidanceSource(),
          c.isActive());
    }
  }
}
