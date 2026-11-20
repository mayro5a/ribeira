import type { Batch } from "@/lib/api/schemas";
import { buildOperation, type QueuedOperation } from "@/lib/sync/operations";
import { fefoOrder, projectBatches, stockByFood } from "./projection";
import { toDecimalString } from "./quantity";

const community = "2b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const food = "3b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const user = "1b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const device = "0b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const family = "4b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";

function snapshot(id: string, available: string, extra: Partial<Batch> = {}): Batch {
  return {
    id,
    communityId: community,
    foodId: food,
    initialQuantity: "10.000",
    availableQuantity: available,
    unit: "KILOGRAM",
    receivedAt: "2026-11-01T10:00:00Z",
    expirationStatus: "UNKNOWN",
    status: "AVAILABLE",
    ...extra,
  };
}

let seq = 0;
function op<T extends QueuedOperation["operationType"]>(
  operationType: T,
  payload: Parameters<typeof buildOperation<T>>[0]["payload"],
  overrides: Partial<QueuedOperation> = {},
): QueuedOperation {
  return {
    ...buildOperation({
      operationType,
      communityId: community,
      deviceId: device,
      userId: user,
      payload,
    }),
    localSeq: ++seq,
    ...overrides,
  };
}

function distribution(batchId: string, quantity: string) {
  return op("DISTRIBUTION_CREATE", {
    distributionId: crypto.randomUUID(),
    distributedAt: new Date().toISOString(),
    familyIds: [family],
    items: [{ itemId: crypto.randomUUID(), batchId, quantity }],
  });
}

describe("local stock projection", () => {
  const batchId = crypto.randomUUID();

  it("subtracts pending distributions from the confirmed balance", () => {
    const [batch] = projectBatches(
      [snapshot(batchId, "10.000")],
      [distribution(batchId, "3.000")],
      0,
    );

    expect(toDecimalString(batch!.available)).toBe("7.000");
    expect(toDecimalString(batch!.pendingDelta)).toBe("-3.000");
  });

  it("ignores conflicted and failed operations", () => {
    const conflicted = { ...distribution(batchId, "3.000"), syncStatus: "CONFLICT" as const };
    const [batch] = projectBatches([snapshot(batchId, "10.000")], [conflicted], 0);
    expect(toDecimalString(batch!.available)).toBe("10.000");
  });

  it("keeps applying a synced operation until the cursor reaches its serverSeq", () => {
    const synced = {
      ...distribution(batchId, "3.000"),
      syncStatus: "SYNCED" as const,
      serverSeq: 50,
    };
    expect(
      toDecimalString(projectBatches([snapshot(batchId, "10.000")], [synced], 49)[0]!.available),
    ).toBe("7.000");
    expect(
      toDecimalString(projectBatches([snapshot(batchId, "7.000")], [synced], 50)[0]!.available),
    ).toBe("7.000");
  });

  it("creates provisional batches from an offline donation and lets later operations use them", () => {
    const newBatch = crypto.randomUUID();
    const donation = op("DONATION_RECEIVE", {
      donationId: crypto.randomUUID(),
      originDescription: "Campanha fictícia",
      receivedAt: new Date().toISOString(),
      items: [
        {
          itemId: crypto.randomUUID(),
          foodId: food,
          quantity: "5.000",
          unit: "KILOGRAM",
          batches: [{ batchId: newBatch, quantity: "5.000", expirationStatus: "UNKNOWN" }],
        },
      ],
    });

    const batches = projectBatches([], [donation, distribution(newBatch, "2.000")], 0);

    expect(batches).toHaveLength(1);
    expect(batches[0]!.provisional).toBe(true);
    expect(toDecimalString(batches[0]!.available)).toBe("3.000");
  });

  it("marks batches depleted and aggregates stock by food", () => {
    const other = crypto.randomUUID();
    const batches = projectBatches(
      [snapshot(batchId, "2.000"), snapshot(other, "4.500")],
      [distribution(batchId, "2.000")],
      0,
    );
    expect(batches.find((b) => b.id === batchId)!.status).toBe("DEPLETED");

    const [stock] = stockByFood(batches);
    expect(toDecimalString(stock!.available)).toBe("4.500");
    expect(stock!.batchCount).toBe(1);
    expect(stock!.hasProvisional).toBe(false);
  });

  it("suggests FEFO order only among available batches with stock", () => {
    const soon = snapshot(crypto.randomUUID(), "1.000", {
      expirationStatus: "KNOWN",
      expirationDate: "2026-12-01",
    });
    const later = snapshot(crypto.randomUUID(), "1.000", {
      expirationStatus: "KNOWN",
      expirationDate: "2027-02-01",
    });
    const unknown = snapshot(crypto.randomUUID(), "1.000");
    const empty = snapshot(crypto.randomUUID(), "0.000", {
      expirationStatus: "KNOWN",
      expirationDate: "2026-11-21",
    });

    const order = fefoOrder(projectBatches([unknown, later, empty, soon], [], 0)).map((b) => b.id);

    expect(order).toEqual([soon.id, later.id, unknown.id]);
  });
});
