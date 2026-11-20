package com.ribeira.alert.application;

import com.ribeira.alert.domain.Alert;
import com.ribeira.alert.domain.Alert.AlertStatus;
import com.ribeira.alert.domain.Alert.AlertType;
import com.ribeira.alert.domain.Alert.Severity;
import com.ribeira.alert.domain.AlertRepository;
import com.ribeira.food.application.FoodService;
import com.ribeira.food.application.FoodService.FoodInfo;
import com.ribeira.inventory.application.InventoryReadModel;
import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.InventoryViews.LowStockView;
import com.ribeira.inventory.application.InventoryViews.StorageLocationView;
import com.ribeira.inventory.application.StorageLocationService;
import com.ribeira.loss.application.LossService;
import com.ribeira.loss.domain.FoodLossRepository.LossByCommunityAndFood;
import com.ribeira.monitoring.application.MonitoringService;
import com.ribeira.monitoring.application.MonitoringService.ReadingRecorded;
import com.ribeira.monitoring.domain.StorageCondition;
import com.ribeira.shared.security.CommunityDirectory;
import com.ribeira.shared.sync.ChangeTracker;
import com.ribeira.shared.sync.SyncEntityType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Explicit, transparent alert rules (RF09). No machine learning: every alert states which rule and
 * which configured limit produced it, and recommends inspection by a responsible person.
 */
@Component
public class AlertRules {

  static final String INSPECTION_ADVICE = " Recomenda-se inspeção por pessoa responsável.";
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
  private static final int MAX_WARNING_DAYS = 365;
  private static final Logger log = LoggerFactory.getLogger(AlertRules.class);

  private final AlertRepository alerts;
  private final InventoryReadModel inventory;
  private final StorageLocationService locations;
  private final FoodService foods;
  private final LossService losses;
  private final MonitoringService monitoring;
  private final CommunityDirectory communities;
  private final ChangeTracker changes;
  private final AlertProperties properties;
  private final TransactionTemplate transaction;
  private final Clock clock;

  public AlertRules(
      AlertRepository alerts,
      InventoryReadModel inventory,
      StorageLocationService locations,
      FoodService foods,
      LossService losses,
      MonitoringService monitoring,
      CommunityDirectory communities,
      ChangeTracker changes,
      AlertProperties properties,
      TransactionTemplate transaction,
      Clock clock) {
    this.alerts = alerts;
    this.inventory = inventory;
    this.locations = locations;
    this.foods = foods;
    this.losses = losses;
    this.monitoring = monitoring;
    this.communities = communities;
    this.changes = changes;
    this.properties = properties;
    this.transaction = transaction;
    this.clock = clock;
  }

  /** Hourly evaluation of every community, each in its own transaction. */
  @Scheduled(cron = "${ribeira.alerts.cron:0 5 * * * *}")
  public void evaluateAll() {
    for (CommunityDirectory.CommunityRef ref : communities.activeCommunities()) {
      try {
        transaction.executeWithoutResult(
            s -> evaluateCommunity(ref.organizationId(), ref.communityId()));
      } catch (RuntimeException e) {
        log.error("Alert evaluation failed for community {}", ref.communityId(), e);
      }
    }
  }

  /** Runs the periodic rules for one community; returns how many alerts were opened. */
  @Transactional(propagation = Propagation.MANDATORY)
  public int evaluateCommunity(UUID organizationId, UUID communityId) {
    int opened = 0;
    opened += expiringBatches(organizationId, communityId);
    opened += lowStock(organizationId, communityId);
    opened += lossIncrease(organizationId, communityId);
    opened += overdueInspections(organizationId, communityId);
    return opened;
  }

  /** Runs inside the transaction that recorded the reading. */
  @EventListener
  public void onReadingRecorded(ReadingRecorded event) {
    Set<String> categories =
        foods
            .infoOf(event.organizationId(), inventory.foodIdsStoredAt(event.storageLocationId()))
            .values()
            .stream()
            .map(f -> f.category().name())
            .collect(Collectors.toSet());
    List<String> deviations = new ArrayList<>();
    for (StorageCondition condition :
        monitoring.applicableConditions(
            event.organizationId(), event.storageLocationId(), categories)) {
      condition
          .deviationsOf(event.temperature(), event.humidity())
          .forEach(d -> deviations.add(d + " (referência: " + condition.getGuidanceSource() + ")"));
    }
    if (!deviations.isEmpty()) {
      open(
          event.organizationId(),
          event.communityId(),
          AlertType.READING_OUT_OF_RANGE,
          Severity.WARNING,
          "STORAGE_LOCATION",
          event.storageLocationId(),
          "READING_OUT_OF_RANGE:" + event.storageLocationId(),
          "Leitura fora do intervalo configurado: "
              + String.join("; ", deviations)
              + "."
              + INSPECTION_ADVICE);
    }
  }

  private int expiringBatches(UUID organizationId, UUID communityId) {
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    List<BatchView> candidates =
        inventory.expiringUntil(List.of(communityId), today.plusDays(MAX_WARNING_DAYS));
    Map<UUID, FoodInfo> foodInfo =
        foods.infoOf(organizationId, candidates.stream().map(BatchView::foodId).toList());
    int opened = 0;
    for (BatchView batch : candidates) {
      FoodInfo food = foodInfo.get(batch.foodId());
      if (food == null
          || batch.expirationDate().isAfter(today.plusDays(food.expirationWarningDays()))) {
        continue;
      }
      long days = ChronoUnit.DAYS.between(today, batch.expirationDate());
      boolean expired = days < 0;
      String message =
          expired
              ? "Lote de "
                  + food.name()
                  + " com validade vencida em "
                  + DATE.format(batch.expirationDate())
                  + ". Separe o lote antes de qualquer uso."
                  + INSPECTION_ADVICE
              : "Lote de "
                  + food.name()
                  + " vence em "
                  + DATE.format(batch.expirationDate())
                  + " ("
                  + days
                  + " dia(s)). Priorize o uso deste lote.";
      opened +=
          open(
              organizationId,
              communityId,
              AlertType.EXPIRING_SOON,
              expired ? Severity.CRITICAL : Severity.WARNING,
              "BATCH",
              batch.id(),
              "EXPIRING_SOON:" + batch.id(),
              message);
    }
    return opened;
  }

  private int lowStock(UUID organizationId, UUID communityId) {
    int opened = 0;
    for (LowStockView low : inventory.lowStock(organizationId, List.of(communityId))) {
      opened +=
          open(
              organizationId,
              communityId,
              AlertType.LOW_STOCK,
              Severity.WARNING,
              "FOOD",
              low.foodId(),
              "LOW_STOCK:" + communityId + ":" + low.foodId(),
              "Estoque de "
                  + low.foodName()
                  + " abaixo do mínimo: "
                  + low.available().toPlainString()
                  + " de "
                  + low.minimumQuantity().toPlainString()
                  + ".");
    }
    return opened;
  }

  private int lossIncrease(UUID organizationId, UUID communityId) {
    Instant now = Instant.now(clock);
    Duration window = Duration.ofDays(properties.lossWindowDays());
    Map<UUID, LossByCommunityAndFood> previous = new HashMap<>();
    losses
        .totalsByCommunityAndFood(
            List.of(communityId), now.minus(window.multipliedBy(2)), now.minus(window))
        .forEach(l -> previous.put(l.getFoodId(), l));
    List<LossByCommunityAndFood> recent =
        losses.totalsByCommunityAndFood(List.of(communityId), now.minus(window), now);
    Map<UUID, FoodInfo> foodInfo =
        foods.infoOf(
            organizationId, recent.stream().map(LossByCommunityAndFood::getFoodId).toList());
    int opened = 0;
    for (LossByCommunityAndFood current : recent) {
      LossByCommunityAndFood before = previous.get(current.getFoodId());
      BigDecimal baseline = before == null ? BigDecimal.ZERO : before.getQuantity();
      boolean increased =
          current.getOccurrences() >= properties.lossMinOccurrences()
              && current
                      .getQuantity()
                      .compareTo(
                          baseline.multiply(BigDecimal.valueOf(properties.lossIncreaseFactor())))
                  > 0;
      if (!increased) {
        continue;
      }
      FoodInfo food = foodInfo.get(current.getFoodId());
      opened +=
          open(
              organizationId,
              communityId,
              AlertType.LOSS_INCREASE,
              Severity.WARNING,
              "FOOD",
              current.getFoodId(),
              "LOSS_INCREASE:" + communityId + ":" + current.getFoodId(),
              "Perdas de "
                  + (food == null ? "alimento" : food.name())
                  + " aumentaram nos últimos "
                  + properties.lossWindowDays()
                  + " dias ("
                  + current.getQuantity().toPlainString()
                  + " contra "
                  + baseline.toPlainString()
                  + " no período anterior). Verifique as condições de armazenamento.");
    }
    return opened;
  }

  private int overdueInspections(UUID organizationId, UUID communityId) {
    List<StorageLocationView> active = locations.activeIn(communityId);
    if (active.isEmpty()) {
      return 0;
    }
    List<StorageCondition> rules = monitoring.inspectionRules(organizationId);
    Map<UUID, Instant> lastInspection =
        monitoring.lastInspections(active.stream().map(StorageLocationView::id).toList());
    Instant now = Instant.now(clock);
    int opened = 0;
    for (StorageLocationView location : active) {
      Integer maxDays = strictestInspectionInterval(organizationId, location.id(), rules);
      if (maxDays == null) {
        continue;
      }
      Instant last = lastInspection.get(location.id());
      if (last != null && last.isAfter(now.minus(Duration.ofDays(maxDays)))) {
        continue;
      }
      opened +=
          open(
              organizationId,
              communityId,
              AlertType.INSPECTION_OVERDUE,
              Severity.INFO,
              "STORAGE_LOCATION",
              location.id(),
              "INSPECTION_OVERDUE:" + location.id(),
              "O local "
                  + location.name()
                  + " está sem inspeção registrada há mais de "
                  + maxDays
                  + " dia(s)."
                  + INSPECTION_ADVICE);
    }
    return opened;
  }

  private Integer strictestInspectionInterval(
      UUID organizationId, UUID locationId, List<StorageCondition> rules) {
    Set<String> categories =
        foods.infoOf(organizationId, inventory.foodIdsStoredAt(locationId)).values().stream()
            .map(f -> f.category().name())
            .collect(Collectors.toSet());
    return rules.stream()
        .filter(
            r ->
                locationId.equals(r.getStorageLocationId())
                    || (r.getFoodCategory() != null && categories.contains(r.getFoodCategory())))
        .map(StorageCondition::getMaxDaysWithoutInspection)
        .min(Integer::compareTo)
        .orElse(null);
  }

  private int open(
      UUID organizationId,
      UUID communityId,
      AlertType type,
      Severity severity,
      String subjectType,
      UUID subjectId,
      String dedupKey,
      String message) {
    if (alerts.existsByDedupKeyAndStatus(dedupKey, AlertStatus.OPEN)) {
      return 0;
    }
    Alert alert =
        alerts.save(
            new Alert(
                organizationId,
                communityId,
                type,
                severity,
                subjectType,
                subjectId,
                dedupKey,
                message,
                Instant.now(clock)));
    changes.record(organizationId, communityId, SyncEntityType.ALERT, alert.getId());
    return 1;
  }
}
