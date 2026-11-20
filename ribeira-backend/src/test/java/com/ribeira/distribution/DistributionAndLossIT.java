package com.ribeira.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ribeira.support.ApiIntegrationTest;
import com.ribeira.support.Fixtures;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DistributionAndLossIT extends ApiIntegrationTest {

  private UUID organization;
  private UUID community;
  private UUID beans;
  private UUID location;
  private String admin;
  private String distributor;
  private UUID family;

  @BeforeEach
  void setUp() {
    organization = fixtures.organization();
    community = fixtures.community(organization);
    beans = fixtures.food(organization, "UNIT");
    location = fixtures.storageLocation(organization, community);
    admin = api.login(fixtures.admin(organization));
    distributor = api.login(fixtures.member(organization, community, Fixtures.DISTRIBUTOR));
    family = createFamily("FAM-" + UUID.randomUUID().toString().substring(0, 8), "ACTIVE");
  }

  /** Dado saldo 10, quando distribuir 3, então movimentação gravada e saldo 7. */
  @Test
  void distributingThreeOfTenLeavesSeven() {
    UUID batch = receive("10", null);

    var response = api.post("/api/v1/distributions", distributor, distribution(Map.of(batch, "3")));

    assertThat(response.status()).isEqualTo(201);
    assertThat(availableOf(batch)).isEqualTo("7.000");
    JsonNode movements =
        api.get("/api/v1/inventory/movements?communityId=" + community + "&batchId=" + batch, admin)
            .body()
            .path("content");
    assertThat(movements.findValuesAsText("type")).contains("DISTRIBUTION");
    assertThat(api.get("/api/v1/inventory/reconciliation?communityId=" + community, admin).body())
        .isEmpty();
  }

  /** Dado saldo 2, quando tentar consumir 3, então rejeitada sem nenhuma baixa parcial. */
  @Test
  void insufficientBalanceRejectsWholeDistribution() {
    UUID plenty = receive("10", null);
    UUID scarce = receive("2", null);

    var response =
        api.post(
            "/api/v1/distributions", distributor, distribution(Map.of(plenty, "1", scarce, "3")));

    assertThat(response.status()).isEqualTo(409);
    assertThat(response.code()).isEqualTo("INSUFFICIENT_STOCK");
    assertThat(availableOf(plenty)).isEqualTo("10.000");
    assertThat(availableOf(scarce)).isEqualTo("2.000");
  }

  @Test
  void familiesMustBeActiveAndFromTheSameCommunity() {
    UUID batch = receive("10", null);
    UUID inactive =
        createFamily("INATIVA-" + UUID.randomUUID().toString().substring(0, 6), "INACTIVE");
    Map<String, Object> body = distribution(Map.of(batch, "1"));
    body.put("familyIds", List.of(inactive));
    assertThat(api.post("/api/v1/distributions", distributor, body).code())
        .isEqualTo("FAMILY_INACTIVE");

    UUID otherCommunity = fixtures.community(organization);
    var foreign =
        api.post(
            "/api/v1/communities/" + otherCommunity + "/families",
            admin,
            Map.of("code", "OUTRA-1", "memberCount", 2));
    body.put("familyIds", List.of(foreign.body().path("id").asText()));
    assertThat(api.post("/api/v1/distributions", distributor, body).status()).isEqualTo(404);
    assertThat(availableOf(batch)).isEqualTo("10.000");
  }

  @Test
  void familyDataRequiresFamilyRead() {
    String viewer = api.login(fixtures.member(organization, community, Fixtures.VIEWER));

    assertThat(api.get("/api/v1/communities/" + community + "/families", viewer).status())
        .isEqualTo(403);
    assertThat(api.get("/api/v1/communities/" + community + "/families", distributor).status())
        .isEqualTo(200);
  }

  @Test
  void lossCreatesLossMovementInTheSameTransaction() {
    UUID batch = receive("5", null);
    String stockKeeper = api.login(fixtures.member(organization, community, Fixtures.STOCK_KEEPER));

    var loss =
        api.post(
            "/api/v1/losses",
            stockKeeper,
            Map.of(
                "communityId",
                community,
                "lossId",
                UUID.randomUUID(),
                "batchId",
                batch,
                "quantity",
                "2",
                "occurredAt",
                Instant.now().toString(),
                "reason",
                "HUMIDITY",
                "description",
                "Sacos úmidos no fundo do depósito"));

    assertThat(loss.status()).isEqualTo(201);
    assertThat(loss.body().path("status").asText()).isEqualTo("REGISTERED");
    assertThat(availableOf(batch)).isEqualTo("3.000");

    var tooMuch =
        api.post(
            "/api/v1/losses",
            stockKeeper,
            Map.of(
                "communityId",
                community,
                "lossId",
                UUID.randomUUID(),
                "batchId",
                batch,
                "quantity",
                "4",
                "occurredAt",
                Instant.now().toString(),
                "reason",
                "PESTS"));
    assertThat(tooMuch.code()).isEqualTo("INSUFFICIENT_STOCK");

    var reviewed =
        api.put(
            "/api/v1/losses/" + loss.body().path("id").asText() + "/status",
            stockKeeper,
            Map.of("status", "UNDER_REVIEW"));
    assertThat(reviewed.body().path("status").asText()).isEqualTo("UNDER_REVIEW");
  }

  @Test
  void readingOutsideConfiguredRangeOpensAlertRecommendingInspection() {
    var condition =
        api.post(
            "/api/v1/monitoring/conditions",
            admin,
            Map.of(
                "storageLocationId", location,
                "maxTemperature", "30",
                "maxHumidity", "70",
                "guidanceSource", "Orientação técnica fictícia para teste"));
    assertThat(condition.status()).isEqualTo(201);
    String keeper = api.login(fixtures.member(organization, community, Fixtures.STOCK_KEEPER));

    var reading =
        api.post(
            "/api/v1/monitoring/readings",
            keeper,
            Map.of(
                "communityId",
                community,
                "readingId",
                UUID.randomUUID(),
                "storageLocationId",
                location,
                "temperature",
                "34.5",
                "humidity",
                "60",
                "inspection",
                false,
                "recordedAt",
                Instant.now().toString()));
    assertThat(reading.status()).isEqualTo(201);

    JsonNode alerts = openAlerts();
    assertThat(alerts.findValuesAsText("type")).containsExactly("READING_OUT_OF_RANGE");
    String message = alerts.get(0).path("message").asText();
    assertThat(message).contains("34.5", "inspeção").doesNotContain("impróprio");

    String coordinator = api.login(fixtures.member(organization, community, Fixtures.COORDINATOR));
    var resolved =
        api.post(
            "/api/v1/alerts/" + alerts.get(0).path("id").asText() + "/resolve",
            coordinator,
            Map.of(
                "notes", "Ventilação ajustada; sacos inspecionados", "inspectionPerformed", true));
    assertThat(resolved.body().path("status").asText()).isEqualTo("RESOLVED");
    assertThat(openAlerts()).isEmpty();
  }

  @Test
  void periodicRulesFlagExpiringBatchesAndLowStockOnce() {
    receive("4", LocalDate.now().plusDays(2));
    api.put(
        "/api/v1/communities/" + community + "/stock-minimums/" + beans,
        admin,
        Map.of("minimumQuantity", "10"));

    var first = api.post("/api/v1/alerts/evaluate?communityId=" + community, admin, null);
    var second = api.post("/api/v1/alerts/evaluate?communityId=" + community, admin, null);

    assertThat(first.body().path("opened").asInt()).isEqualTo(2);
    assertThat(second.body().path("opened").asInt()).isZero();
    assertThat(openAlerts().findValuesAsText("type"))
        .containsExactlyInAnyOrder("EXPIRING_SOON", "LOW_STOCK");
  }

  @Test
  void reportsAggregateWithoutPersonalData() {
    UUID batch = receive("10", null);
    api.post("/api/v1/distributions", distributor, distribution(Map.of(batch, "4")));
    String familyCode = api.get("/api/v1/families/" + family, admin).body().path("code").asText();

    var report = api.get("/api/v1/reports/distributions?communityId=" + community, admin);
    var dashboard = api.get("/api/v1/dashboard/organization", admin);
    var communityDashboard = api.get("/api/v1/dashboard/communities/" + community, admin);

    assertThat(report.body().path("distributions").asLong()).isEqualTo(1);
    assertThat(report.body().path("familiesServed").asLong()).isEqualTo(1);
    assertThat(report.body().path("byFood").get(0).path("quantity").asText()).isEqualTo("4.000");
    assertThat(dashboard.status()).isEqualTo(200);
    assertThat(communityDashboard.body().path("stock").get(0).path("available").asText())
        .isEqualTo("6.000");
    for (var response : List.of(report, dashboard, communityDashboard)) {
      assertThat(response.body().toString()).doesNotContain(familyCode);
    }
  }

  private JsonNode openAlerts() {
    return api.get("/api/v1/alerts?status=OPEN&communityId=" + community, admin)
        .body()
        .path("content");
  }

  private UUID createFamily(String code, String status) {
    var response =
        api.post(
            "/api/v1/communities/" + community + "/families",
            admin,
            Map.of("code", code, "memberCount", 4, "status", status));
    assertThat(response.status()).isEqualTo(201);
    return UUID.fromString(response.body().path("id").asText());
  }

  private UUID receive(String quantity, LocalDate expiration) {
    UUID batch = UUID.randomUUID();
    Map<String, Object> body = new HashMap<>();
    body.put("communityId", community);
    body.put("batchId", batch);
    body.put("foodId", beans);
    body.put("quantity", quantity);
    body.put("unit", "UNIT");
    body.put("originDescription", "Compra com recursos de campanha fictícia");
    body.put("receivedAt", Instant.now().toString());
    body.put("storageLocationId", location);
    body.put("expirationStatus", expiration == null ? "UNKNOWN" : "KNOWN");
    if (expiration != null) {
      body.put("expirationDate", expiration.toString());
    }
    assertThat(api.post("/api/v1/inventory/batches", admin, body).status()).isEqualTo(201);
    return batch;
  }

  private String availableOf(UUID batch) {
    return api.get("/api/v1/inventory/batches/" + batch, admin)
        .body()
        .path("availableQuantity")
        .asText();
  }

  private Map<String, Object> distribution(Map<UUID, String> quantities) {
    Map<String, Object> body = new HashMap<>();
    body.put("communityId", community);
    body.put("distributionId", UUID.randomUUID());
    body.put("distributedAt", Instant.now().toString());
    body.put("familyIds", List.of(family));
    body.put(
        "items",
        quantities.entrySet().stream()
            .map(
                e ->
                    Map.of(
                        "itemId",
                        UUID.randomUUID(),
                        "batchId",
                        e.getKey(),
                        "quantity",
                        e.getValue()))
            .toList());
    return body;
  }
}
