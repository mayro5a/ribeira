package com.ribeira.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.ribeira.support.ApiIntegrationTest;
import com.ribeira.support.Fixtures;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

class SyncIT extends ApiIntegrationTest {

  private UUID organization;
  private UUID community;
  private UUID food;
  private UUID device;
  private String keeper;
  private String admin;

  @BeforeEach
  void setUp() {
    organization = fixtures.organization();
    community = fixtures.community(organization);
    food = fixtures.food(organization, "KILOGRAM");
    keeper = api.login(fixtures.member(organization, community, Fixtures.STOCK_KEEPER));
    admin = api.login(fixtures.admin(organization));
    device = registerDevice(keeper);
  }

  /** Acceptance 3 and 4: resending (e.g. after a lost response) never duplicates. */
  @Test
  void sameOperationTwiceIsAppliedOnce() {
    UUID batch = UUID.randomUUID();
    Map<String, Object> operation = batchEntry(batch, "5");

    JsonNode first = push(keeper, device, operation).get(0);
    JsonNode second = push(keeper, device, operation).get(0);

    assertThat(first.path("outcome").asText()).isEqualTo("APPLIED");
    assertThat(first.path("serverSeq").asLong()).isPositive();
    assertThat(second.path("outcome").asText()).isEqualTo("DUPLICATE");
    assertThat(second.path("serverSeq").asLong()).isEqualTo(first.path("serverSeq").asLong());
    JsonNode movements =
        api.get(
                "/api/v1/inventory/movements?communityId=" + community + "&batchId=" + batch,
                keeper)
            .body()
            .path("content");
    assertThat(movements).hasSize(1);
  }

  @Test
  void reusingAnOperationIdWithDifferentPayloadIsRejected() {
    UUID batch = UUID.randomUUID();
    Map<String, Object> operation = batchEntry(batch, "5");
    push(keeper, device, operation);

    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) operation.get("payload");
    payload.put("quantity", "6.000");
    JsonNode result = push(keeper, device, operation).get(0);

    assertThat(result.path("outcome").asText()).isEqualTo("REJECTED");
    assertThat(result.path("errorCode").asText()).isEqualTo("OPERATION_ID_REUSED");
  }

  /** Acceptance 5: concurrent offline withdrawals exceeding the balance become a conflict. */
  @Test
  void concurrentOfflineOperationsExceedingBalanceAreKeptAsConflict() {
    UUID batch = UUID.randomUUID();
    push(keeper, device, batchEntry(batch, "5"));
    UUID otherDevice = registerDevice(keeper);

    JsonNode fromFirst = push(keeper, device, loss(batch, "4")).get(0);
    Map<String, Object> conflicting = loss(batch, "4");
    JsonNode fromSecond = push(keeper, otherDevice, conflicting).get(0);

    assertThat(fromFirst.path("outcome").asText()).isEqualTo("APPLIED");
    assertThat(fromSecond.path("outcome").asText()).isEqualTo("CONFLICT");
    assertThat(fromSecond.path("errorCode").asText()).isEqualTo("INSUFFICIENT_STOCK");
    assertThat(available(batch)).isEqualTo("1.000");

    JsonNode conflicts = api.get("/api/v1/sync/conflicts", admin).body();
    assertThat(conflicts.findValuesAsText("id")).contains(fromSecond.path("conflictId").asText());

    // Reviewer recounts (+3) and retries the original operation.
    api.post(
        "/api/v1/inventory/movements",
        admin,
        Map.of(
            "movementId",
            UUID.randomUUID(),
            "batchId",
            batch,
            "quantityDelta",
            "3",
            "justification",
            "Recontagem após conflito"));
    JsonNode resolved =
        api.post(
                "/api/v1/sync/conflicts/" + fromSecond.path("conflictId").asText() + "/resolution",
                admin,
                Map.of("decision", "RETRY", "notes", "Saldo corrigido"))
            .body();
    assertThat(resolved.path("status").asText()).isEqualTo("RESOLVED_APPLIED");
    assertThat(resolved.path("events").findValuesAsText("type"))
        .containsExactly("DETECTED", "APPLIED");
    assertThat(available(batch)).isEqualTo("0.000");

    // The device learns the outcome by resending: now reported as confirmed.
    JsonNode recheck = push(keeper, otherDevice, conflicting).get(0);
    assertThat(recheck.path("outcome").asText()).isEqualTo("DUPLICATE");
  }

  @Test
  void failedRetryStaysOpenAndDiscardRequiresJustification() {
    UUID batch = UUID.randomUUID();
    push(keeper, device, batchEntry(batch, "1"));
    JsonNode conflict = push(keeper, device, loss(batch, "2")).get(0);
    String url = "/api/v1/sync/conflicts/" + conflict.path("conflictId").asText() + "/resolution";

    JsonNode retried = api.post(url, admin, Map.of("decision", "RETRY")).body();
    assertThat(retried.path("status").asText()).isEqualTo("OPEN");
    assertThat(retried.path("events").findValuesAsText("type"))
        .containsExactly("DETECTED", "RETRY_FAILED");

    assertThat(api.post(url, admin, Map.of("decision", "DISCARD")).code())
        .isEqualTo("NOTES_REQUIRED");
    JsonNode discarded =
        api.post(url, admin, Map.of("decision", "DISCARD", "notes", "Perda já registrada em papel"))
            .body();
    assertThat(discarded.path("status").asText()).isEqualTo("RESOLVED_DISCARDED");
    assertThat(push(keeper, device, loss(batch, "2"))).isNotNull();
  }

  @Test
  void invalidPayloadIsRejectedAndPreservedForReview() {
    Map<String, Object> operation = batchEntry(UUID.randomUUID(), "5");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) operation.get("payload");
    payload.remove("originDescription");

    JsonNode result = push(keeper, device, operation).get(0);

    assertThat(result.path("outcome").asText()).isEqualTo("REJECTED");
    assertThat(result.path("errorCode").asText()).isEqualTo("VALIDATION_FAILED");
    JsonNode conflicts = api.get("/api/v1/sync/conflicts", admin).body();
    assertThat(conflicts.findValuesAsText("kind")).contains("REJECTED");
  }

  @Test
  void operationsForUnauthorizedCommunityAreRejected() {
    UUID otherCommunity = fixtures.community(organization);
    Map<String, Object> operation = batchEntry(UUID.randomUUID(), "1");
    operation.put("communityId", otherCommunity);

    JsonNode result = push(keeper, device, operation).get(0);

    assertThat(result.path("outcome").asText()).isEqualTo("REJECTED");
    assertThat(result.path("errorCode").asText()).isEqualTo("ACCESS_DENIED");
  }

  @Test
  void onlyThePrimaryDeviceMayChangeStockWhenOneIsAssigned() {
    api.put(
        "/api/v1/communities/" + community + "/primary-device", admin, Map.of("deviceId", device));
    UUID secondary = registerDevice(keeper);

    JsonNode fromSecondary = push(keeper, secondary, batchEntry(UUID.randomUUID(), "1")).get(0);
    JsonNode fromPrimary = push(keeper, device, batchEntry(UUID.randomUUID(), "1")).get(0);

    assertThat(fromSecondary.path("errorCode").asText()).isEqualTo("DEVICE_NOT_PRIMARY");
    assertThat(fromPrimary.path("outcome").asText()).isEqualTo("APPLIED");
  }

  @Test
  void revokedDeviceCannotPushOrPull() {
    api.post("/api/v1/devices/" + device + "/revoke", admin, null);

    var push =
        api.post(
            "/api/v1/sync/push",
            keeper,
            pushBody(device, List.of(batchEntry(UUID.randomUUID(), "1"))));
    ResponseEntity<String> pull = pull(keeper, device, null);

    assertThat(push.status()).isEqualTo(403);
    assertThat(push.code()).isEqualTo("DEVICE_REVOKED");
    assertThat(pull.getStatusCode().value()).isEqualTo(403);
  }

  /** Acceptance 7: after reconnecting, the device receives changes after its cursor. */
  @Test
  void pullDeliversChangesAfterCursorRespectingPermissions() throws Exception {
    UUID otherCommunity = fixtures.community(organization);
    UUID batch = UUID.randomUUID();
    JsonNode applied = push(keeper, device, batchEntry(batch, "5")).get(0);

    JsonNode first = mapper.readTree(pull(keeper, device, null).getBody());
    assertThat(first.path("changes").findValuesAsText("entityId")).contains(batch.toString());
    assertThat(first.path("changes").findValuesAsText("communityId"))
        .doesNotContain(otherCommunity.toString());
    long cursor = first.path("nextCursor").asLong();
    assertThat(cursor).isGreaterThanOrEqualTo(applied.path("serverSeq").asLong());

    UUID later = UUID.randomUUID();
    push(keeper, device, batchEntry(later, "2"));
    JsonNode second = mapper.readTree(pull(keeper, device, Long.toString(cursor)).getBody());
    assertThat(second.path("changes").findValuesAsText("entityId"))
        .contains(later.toString())
        .doesNotContain(batch.toString());
    JsonNode batchChange = null;
    for (JsonNode change : second.path("changes")) {
      if ("BATCH".equals(change.path("entityType").asText())) {
        batchChange = change;
      }
    }
    assertThat(batchChange).isNotNull();
    assertThat(batchChange.path("data").path("availableQuantity").asText()).isEqualTo("2.000");

    JsonNode status =
        mapper.readTree(
            rest.exchange(
                    "/api/v1/sync/status",
                    HttpMethod.GET,
                    new HttpEntity<>(deviceHeaders(keeper, device)),
                    String.class)
                .getBody());
    assertThat(status.path("lastConfirmedCursor").asLong()).isEqualTo(cursor);
  }

  @Test
  void familiesAreOnlyPulledWithFamilyRead() throws Exception {
    api.post(
        "/api/v1/communities/" + community + "/families",
        admin,
        Map.of("code", "F-" + UUID.randomUUID().toString().substring(0, 6), "memberCount", 3));
    String distributor = api.login(fixtures.member(organization, community, Fixtures.DISTRIBUTOR));
    UUID distributorDevice = registerDevice(distributor);

    JsonNode keeperFeed = mapper.readTree(pull(keeper, device, null).getBody());
    JsonNode distributorFeed =
        mapper.readTree(pull(distributor, distributorDevice, null).getBody());

    assertThat(keeperFeed.path("changes").findValuesAsText("entityType")).doesNotContain("FAMILY");
    assertThat(distributorFeed.path("changes").findValuesAsText("entityType")).contains("FAMILY");
  }

  private UUID registerDevice(String token) {
    UUID id = UUID.randomUUID();
    var response =
        api.post("/api/v1/devices", token, Map.of("deviceId", id, "name", "Celular de teste"));
    assertThat(response.status()).isEqualTo(200);
    return id;
  }

  private JsonNode push(String token, UUID deviceId, Map<String, Object> operation) {
    Map<String, Object> copy = new HashMap<>(operation);
    copy.put("deviceId", deviceId);
    var response = api.post("/api/v1/sync/push", token, pushBody(deviceId, List.of(copy)));
    assertThat(response.status()).as(response.body().toString()).isEqualTo(200);
    return response.body().path("results");
  }

  private Map<String, Object> pushBody(UUID deviceId, List<Map<String, Object>> operations) {
    List<Map<String, Object>> withDevice = new ArrayList<>();
    for (Map<String, Object> op : operations) {
      Map<String, Object> copy = new HashMap<>(op);
      copy.put("deviceId", deviceId);
      withDevice.add(copy);
    }
    return Map.of("deviceId", deviceId, "operations", withDevice);
  }

  private ResponseEntity<String> pull(String token, UUID deviceId, String cursor) {
    String url = "/api/v1/sync/pull" + (cursor == null ? "" : "?cursor=" + cursor);
    return rest.exchange(
        url, HttpMethod.GET, new HttpEntity<>(deviceHeaders(token, deviceId)), String.class);
  }

  private HttpHeaders deviceHeaders(String token, UUID deviceId) {
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(token);
    headers.add("X-Device-Id", deviceId.toString());
    return headers;
  }

  private String available(UUID batch) {
    return api.get("/api/v1/inventory/batches/" + batch, admin)
        .body()
        .path("availableQuantity")
        .asText();
  }

  private Map<String, Object> batchEntry(UUID batchId, String quantity) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("batchId", batchId.toString());
    payload.put("foodId", food.toString());
    payload.put("quantity", quantity);
    payload.put("unit", "KILOGRAM");
    payload.put("originDescription", "Entrada offline fictícia");
    payload.put("receivedAt", Instant.now().toString());
    payload.put("expirationStatus", "UNKNOWN");
    return envelope("BATCH", "BATCH_ENTRY", payload);
  }

  private Map<String, Object> loss(UUID batchId, String quantity) {
    Map<String, Object> payload = new HashMap<>();
    payload.put("lossId", UUID.randomUUID().toString());
    payload.put("batchId", batchId.toString());
    payload.put("quantity", quantity);
    payload.put("occurredAt", Instant.now().toString());
    payload.put("reason", "PESTS");
    return envelope("LOSS", "LOSS_RECORD", payload);
  }

  private Map<String, Object> envelope(
      String entityType, String operationType, Map<String, Object> payload) {
    Map<String, Object> operation = new HashMap<>();
    operation.put("operationId", UUID.randomUUID());
    operation.put("communityId", community);
    operation.put("entityType", entityType);
    operation.put("operationType", operationType);
    operation.put("createdAt", Instant.now().toString());
    operation.put("schemaVersion", 1);
    operation.put("payload", payload);
    return operation;
  }
}
