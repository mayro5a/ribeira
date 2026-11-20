package com.ribeira.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.ribeira.inventory.application.StockLedger;
import com.ribeira.inventory.application.StockLedger.Withdrawal;
import com.ribeira.inventory.domain.InventoryEnums.MovementType;
import com.ribeira.shared.exception.StateConflictException;
import com.ribeira.support.ApiIntegrationTest;
import com.ribeira.support.Fixtures;
import com.ribeira.support.Fixtures.TestUser;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class InventoryIT extends ApiIntegrationTest {

  @Autowired StockLedger ledger;
  @Autowired TransactionTemplate transaction;
  @Autowired JdbcTemplate jdbc;

  private UUID organization;
  private UUID community;
  private UUID rice;
  private UUID location;
  private TestUser stockKeeper;
  private String token;

  @BeforeEach
  void setUp() {
    organization = fixtures.organization();
    community = fixtures.community(organization);
    rice = fixtures.food(organization, "KILOGRAM");
    location = fixtures.storageLocation(organization, community);
    stockKeeper = fixtures.member(organization, community, Fixtures.STOCK_KEEPER);
    token = api.login(stockKeeper);
  }

  @Test
  void donationCreatesTraceableBatchesWithEntryMovements() {
    UUID batchA = UUID.randomUUID();
    UUID batchB = UUID.randomUUID();

    var response =
        api.post("/api/v1/donations", token, donation(Map.of(batchA, "6.5", batchB, "3.5")));

    assertThat(response.status()).isEqualTo(201);
    assertThat(response.body().path("batches")).hasSize(2);

    JsonNode batch = api.get("/api/v1/inventory/batches/" + batchA, token).body();
    assertThat(batch.path("availableQuantity").asText()).isEqualTo("6.500");
    assertThat(batch.path("donationId").asText())
        .isEqualTo(response.body().path("donation").path("id").asText());

    JsonNode movements =
        api.get(
                "/api/v1/inventory/movements?communityId=" + community + "&batchId=" + batchA,
                token)
            .body();
    assertThat(movements.path("content").get(0).path("type").asText()).isEqualTo("ENTRY");
    assertThat(movements.path("content").get(0).path("performedBy").asText())
        .isEqualTo(stockKeeper.id().toString());

    JsonNode summary = api.get("/api/v1/communities/" + community + "/inventory", token).body();
    assertThat(summary.get(0).path("available").asText()).isEqualTo("10.000");
    assertThat(api.get("/api/v1/inventory/reconciliation?communityId=" + community, token).body())
        .isEmpty();
  }

  @Test
  void donationRulesAreEnforced() {
    Map<String, Object> mismatched = donation(Map.of(UUID.randomUUID(), "1"));
    item(mismatched).put("quantity", "2");
    assertThat(api.post("/api/v1/donations", token, mismatched).code())
        .isEqualTo("BATCH_SPLIT_MISMATCH");

    Map<String, Object> withoutOrigin = donation(Map.of(UUID.randomUUID(), "1"));
    withoutOrigin.remove("originDescription");
    assertThat(api.post("/api/v1/donations", token, withoutOrigin).code())
        .isEqualTo("ORIGIN_REQUIRED");

    Map<String, Object> wrongUnit = donation(Map.of(UUID.randomUUID(), "1"));
    item(wrongUnit).put("unit", "LITER");
    assertThat(api.post("/api/v1/donations", token, wrongUnit).code()).isEqualTo("UNIT_MISMATCH");

    Map<String, Object> tooPrecise = donation(Map.of(UUID.randomUUID(), "1.0001"));
    item(tooPrecise).put("quantity", "1.0001");
    assertThat(api.post("/api/v1/donations", token, tooPrecise).code())
        .isEqualTo("INVALID_QUANTITY_PRECISION");
  }

  @Test
  void duplicateDonationIdIsRejectedWithoutDoubleEntry() {
    Map<String, Object> body = donation(Map.of(UUID.randomUUID(), "2"));
    assertThat(api.post("/api/v1/donations", token, body).status()).isEqualTo(201);

    var again = api.post("/api/v1/donations", token, body);

    assertThat(again.status()).isEqualTo(409);
    assertThat(again.code()).isEqualTo("DUPLICATE_ID");
    JsonNode summary = api.get("/api/v1/communities/" + community + "/inventory", token).body();
    assertThat(summary.get(0).path("available").asText()).isEqualTo("2.000");
  }

  @Test
  void adjustmentNeedsJustificationAndCannotMakeBalanceNegative() {
    UUID batch = UUID.randomUUID();
    api.post("/api/v1/donations", token, donation(Map.of(batch, "5")));

    var unjustified =
        api.post(
            "/api/v1/inventory/movements",
            token,
            Map.of(
                "movementId",
                UUID.randomUUID(),
                "batchId",
                batch,
                "quantityDelta",
                "-1",
                "justification",
                ""));
    assertThat(unjustified.status()).isEqualTo(400);

    var negative =
        api.post(
            "/api/v1/inventory/movements",
            token,
            Map.of(
                "movementId",
                UUID.randomUUID(),
                "batchId",
                batch,
                "quantityDelta",
                "-6",
                "justification",
                "Recontagem mensal"));
    assertThat(negative.code()).isEqualTo("INSUFFICIENT_STOCK");

    var valid =
        api.post(
            "/api/v1/inventory/movements",
            token,
            Map.of(
                "movementId",
                UUID.randomUUID(),
                "batchId",
                batch,
                "quantityDelta",
                "-1.250",
                "justification",
                "Recontagem mensal"));
    assertThat(valid.status()).isEqualTo(201);
    assertThat(valid.body().path("balanceAfter").asText()).isEqualTo("3.750");
    assertThat(api.get("/api/v1/inventory/reconciliation?communityId=" + community, token).body())
        .isEmpty();
  }

  @Test
  void movementsAreAppendOnlyInTheDatabase() {
    UUID batch = UUID.randomUUID();
    api.post("/api/v1/donations", token, donation(Map.of(batch, "1")));

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE stock_movements SET quantity_delta = 99 WHERE batch_id = ?", batch))
        .hasMessageContaining("append-only");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM stock_movements WHERE batch_id = ?", batch))
        .hasMessageContaining("append-only");
  }

  @Test
  void viewerCannotRegisterDonations() {
    TestUser viewer = fixtures.member(organization, community, Fixtures.VIEWER);

    var response =
        api.post("/api/v1/donations", api.login(viewer), donation(Map.of(UUID.randomUUID(), "1")));

    assertThat(response.status()).isEqualTo(403);
  }

  @Test
  void batchesOfOtherOrganizationsAreInvisible() {
    UUID batch = UUID.randomUUID();
    api.post("/api/v1/donations", token, donation(Map.of(batch, "1")));
    UUID otherOrganization = fixtures.organization();
    String outsider = api.login(fixtures.admin(otherOrganization));

    assertThat(api.get("/api/v1/inventory/batches/" + batch, outsider).status()).isEqualTo(404);
  }

  /** Acceptance: two concurrent withdrawals of 4 from a balance of 5 → at most one succeeds. */
  @Test
  void concurrentWithdrawalsNeverMakeTheBalanceNegative() throws Exception {
    UUID batch = UUID.randomUUID();
    api.post("/api/v1/donations", token, donation(Map.of(batch, "5")));

    CountDownLatch start = new CountDownLatch(1);
    Callable<Boolean> withdrawFour =
        () -> {
          start.await();
          try {
            transaction.executeWithoutResult(
                s ->
                    ledger.withdraw(
                        community,
                        List.of(new Withdrawal(batch, new BigDecimal("4"))),
                        MovementType.DISTRIBUTION,
                        UUID.randomUUID(),
                        stockKeeper.id(),
                        Instant.now()));
            return true;
          } catch (StateConflictException e) {
            return false;
          }
        };
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results = new ArrayList<>();
      results.add(pool.submit(withdrawFour));
      results.add(pool.submit(withdrawFour));
      start.countDown();
      long successes = 0;
      for (Future<Boolean> result : results) {
        successes += result.get() ? 1 : 0;
      }
      assertThat(successes).isEqualTo(1);
    } finally {
      pool.shutdown();
    }
    BigDecimal balance =
        jdbc.queryForObject(
            "SELECT available_quantity FROM food_batches WHERE id = ?", BigDecimal.class, batch);
    assertThat(balance).isEqualByComparingTo("1");
    assertThat(api.get("/api/v1/inventory/reconciliation?communityId=" + community, token).body())
        .isEmpty();
  }

  @Test
  void multiBatchWithdrawalIsAllOrNothing() {
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    api.post("/api/v1/donations", token, donation(Map.of(first, "5", second, "1")));

    assertThatThrownBy(
            () ->
                transaction.executeWithoutResult(
                    s ->
                        ledger.withdraw(
                            community,
                            List.of(
                                new Withdrawal(first, new BigDecimal("2")),
                                new Withdrawal(second, new BigDecimal("3"))),
                            MovementType.DISTRIBUTION,
                            UUID.randomUUID(),
                            stockKeeper.id(),
                            Instant.now())))
        .isInstanceOf(StateConflictException.class);

    BigDecimal firstBalance =
        jdbc.queryForObject(
            "SELECT available_quantity FROM food_batches WHERE id = ?", BigDecimal.class, first);
    assertThat(firstBalance).isEqualByComparingTo("5");
  }

  private Map<String, Object> donation(Map<UUID, String> batches) {
    BigDecimal total =
        batches.values().stream().map(BigDecimal::new).reduce(BigDecimal.ZERO, BigDecimal::add);
    List<Map<String, Object>> splits = new ArrayList<>();
    batches.forEach(
        (id, quantity) -> {
          Map<String, Object> split = new HashMap<>();
          split.put("batchId", id);
          split.put("quantity", quantity);
          split.put("storageLocationId", location);
          split.put("expirationStatus", "KNOWN");
          split.put("expirationDate", "2027-01-31");
          splits.add(split);
        });
    Map<String, Object> item = new HashMap<>();
    item.put("itemId", UUID.randomUUID());
    item.put("foodId", rice);
    item.put("quantity", total.toPlainString());
    item.put("unit", "KILOGRAM");
    item.put("batches", splits);
    Map<String, Object> body = new HashMap<>();
    body.put("communityId", community);
    body.put("donationId", UUID.randomUUID());
    body.put("originDescription", "Campanha fictícia de arrecadação");
    body.put("receivedAt", Instant.now().toString());
    body.put("items", new ArrayList<>(List.of(item)));
    return body;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> item(Map<String, Object> donation) {
    return ((List<Map<String, Object>>) donation.get("items")).get(0);
  }
}
