package com.ribeira.report.application;

import com.ribeira.alert.application.AlertService;
import com.ribeira.distribution.application.DistributionService;
import com.ribeira.distribution.application.DistributionService.DistributionStats;
import com.ribeira.donation.application.DonationService;
import com.ribeira.donation.application.DonationService.DonationStats;
import com.ribeira.food.application.FoodService;
import com.ribeira.food.application.FoodService.FoodInfo;
import com.ribeira.inventory.application.InventoryReadModel;
import com.ribeira.inventory.application.InventoryViews.BatchView;
import com.ribeira.inventory.application.InventoryViews.InventoryItemView;
import com.ribeira.loss.application.LossService;
import com.ribeira.loss.application.LossService.LossStats;
import com.ribeira.loss.domain.FoodLoss.LossReason;
import com.ribeira.shared.domain.UnitOfMeasure;
import com.ribeira.shared.exception.BusinessRuleException;
import com.ribeira.shared.security.AccessControl;
import com.ribeira.shared.security.Actor;
import com.ribeira.shared.security.Permission;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated indicators. Only counts and quantities are returned: no family codes, names or other
 * personal data ever appear in reports or dashboards.
 */
@Service
public class ReportService {

  static final Duration DEFAULT_PERIOD = Duration.ofDays(30);
  static final Duration MAX_PERIOD = Duration.ofDays(366);
  private static final int ATTENTION_DAYS = 30;

  private final AccessControl accessControl;
  private final InventoryReadModel inventory;
  private final DonationService donations;
  private final DistributionService distributions;
  private final LossService losses;
  private final AlertService alerts;
  private final FoodService foods;
  private final Clock clock;

  public ReportService(
      AccessControl accessControl,
      InventoryReadModel inventory,
      DonationService donations,
      DistributionService distributions,
      LossService losses,
      AlertService alerts,
      FoodService foods,
      Clock clock) {
    this.accessControl = accessControl;
    this.inventory = inventory;
    this.donations = donations;
    this.distributions = distributions;
    this.losses = losses;
    this.alerts = alerts;
    this.foods = foods;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<CommunityInventory> inventory(UUID communityId) {
    Actor actor = accessControl.member();
    return scope(communityId).stream()
        .sorted()
        .map(id -> new CommunityInventory(id, inventory.summary(actor.organizationId(), id)))
        .toList();
  }

  @Transactional(readOnly = true)
  public DonationReport donations(UUID communityId, Instant from, Instant to) {
    Period period = period(from, to);
    DonationStats stats = donations.stats(scope(communityId), period.from(), period.to());
    return new DonationReport(
        period,
        stats.donations(),
        totals(
            stats.totalsByFood().stream()
                .map(t -> new Total(t.getFoodId(), t.getUnit(), t.getQuantity(), 0))
                .toList()));
  }

  @Transactional(readOnly = true)
  public DistributionReport distributions(UUID communityId, Instant from, Instant to) {
    Period period = period(from, to);
    DistributionStats stats = distributions.stats(scope(communityId), period.from(), period.to());
    return new DistributionReport(
        period,
        stats.distributions(),
        stats.familiesServed(),
        totals(
            stats.totalsByFood().stream()
                .map(t -> new Total(t.getFoodId(), t.getUnit(), t.getQuantity(), 0))
                .toList()));
  }

  @Transactional(readOnly = true)
  public LossReport losses(UUID communityId, Instant from, Instant to) {
    Period period = period(from, to);
    LossStats stats = losses.stats(scope(communityId), period.from(), period.to());
    Map<LossReason, Long> byReason = new HashMap<>();
    stats.byReason().forEach(r -> byReason.put(r.getReason(), r.getOccurrences()));
    return new LossReport(
        period,
        totals(
            stats.byFood().stream()
                .map(
                    t -> new Total(t.getFoodId(), t.getUnit(), t.getQuantity(), t.getOccurrences()))
                .toList()),
        byReason);
  }

  @Transactional(readOnly = true)
  public OrganizationDashboard organizationDashboard(Instant from, Instant to) {
    Set<UUID> communities = accessControl.communitiesWith(Permission.REPORT_READ);
    Period period = period(from, to);
    DonationStats donationStats = donations.stats(communities, period.from(), period.to());
    DistributionStats distributionStats =
        distributions.stats(communities, period.from(), period.to());
    return new OrganizationDashboard(
        period,
        communities.size(),
        donationStats.donations(),
        distributionStats.distributions(),
        distributionStats.familiesServed(),
        losses(null, period.from(), period.to()),
        alerts.countOpen(communities),
        consolidatedStock(communities));
  }

  @Transactional(readOnly = true)
  public CommunityDashboard communityDashboard(UUID communityId) {
    Actor actor = accessControl.requireCommunity(communityId, Permission.STOCK_READ);
    Instant now = Instant.now(clock);
    Period period = new Period(now.minus(DEFAULT_PERIOD), now);
    LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    List<BatchView> attention =
        inventory.expiringUntil(List.of(communityId), today.plusDays(ATTENTION_DAYS));
    return new CommunityDashboard(
        communityId,
        period,
        inventory.summary(actor.organizationId(), communityId),
        attention,
        donations.stats(List.of(communityId), period.from(), period.to()).donations(),
        distributions.stats(List.of(communityId), period.from(), period.to()).distributions(),
        losses.stats(List.of(communityId), period.from(), period.to()).byFood().stream()
            .mapToLong(l -> l.getOccurrences())
            .sum(),
        alerts.countOpen(List.of(communityId)));
  }

  private List<FoodTotal> consolidatedStock(Collection<UUID> communities) {
    Actor actor = accessControl.actor();
    Map<List<Object>, BigDecimal> sum = new HashMap<>();
    for (UUID community : communities) {
      for (InventoryItemView item : inventory.summary(actor.organizationId(), community)) {
        sum.merge(List.of(item.foodId(), item.unit()), item.available(), BigDecimal::add);
      }
    }
    return totals(
        sum.entrySet().stream()
            .map(
                e ->
                    new Total(
                        (UUID) e.getKey().get(0),
                        (UnitOfMeasure) e.getKey().get(1),
                        e.getValue(),
                        0))
            .toList());
  }

  /**
   * A specific community (requires REPORT_READ there) or every community the user may report on.
   */
  private Set<UUID> scope(UUID communityId) {
    if (communityId != null) {
      accessControl.requireCommunity(communityId, Permission.REPORT_READ);
      return Set.of(communityId);
    }
    return accessControl.communitiesWith(Permission.REPORT_READ);
  }

  private Period period(Instant from, Instant to) {
    Instant end = to == null ? Instant.now(clock) : to;
    Instant start = from == null ? end.minus(DEFAULT_PERIOD) : from;
    if (!start.isBefore(end) || Duration.between(start, end).compareTo(MAX_PERIOD) > 0) {
      throw new BusinessRuleException(
          "INVALID_PERIOD", "Período inválido: use até 366 dias, com início antes do fim.");
    }
    return new Period(start, end);
  }

  private List<FoodTotal> totals(List<Total> rows) {
    Map<UUID, FoodInfo> info =
        foods.infoOf(
            accessControl.actor().organizationId(), rows.stream().map(Total::foodId).toList());
    Function<Total, String> name =
        t -> info.containsKey(t.foodId()) ? info.get(t.foodId()).name() : null;
    return rows.stream()
        .map(t -> new FoodTotal(t.foodId(), name.apply(t), t.unit(), t.quantity(), t.occurrences()))
        .sorted(Comparator.comparing(FoodTotal::quantity).reversed())
        .toList();
  }

  private record Total(UUID foodId, UnitOfMeasure unit, BigDecimal quantity, long occurrences) {}

  public record Period(Instant from, Instant to) {}

  public record FoodTotal(
      UUID foodId, String foodName, UnitOfMeasure unit, BigDecimal quantity, long occurrences) {}

  public record CommunityInventory(UUID communityId, List<InventoryItemView> items) {}

  public record DonationReport(Period period, long donations, List<FoodTotal> byFood) {}

  public record DistributionReport(
      Period period, long distributions, long familiesServed, List<FoodTotal> byFood) {}

  public record LossReport(Period period, List<FoodTotal> byFood, Map<LossReason, Long> byReason) {}

  public record OrganizationDashboard(
      Period period,
      int communities,
      long donations,
      long distributions,
      long familiesServed,
      LossReport losses,
      long openAlerts,
      List<FoodTotal> stock) {}

  public record CommunityDashboard(
      UUID communityId,
      Period period,
      List<InventoryItemView> stock,
      List<BatchView> batchesNeedingAttention,
      long donations,
      long distributions,
      long lossRecords,
      long openAlerts) {}
}
