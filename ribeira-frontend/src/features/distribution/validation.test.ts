import type { ProjectedBatch } from "@/lib/domain/projection";
import { parseQuantity } from "@/lib/domain/quantity";
import { validateDistribution } from "./validation";

function batch(available: string, extra: Partial<ProjectedBatch> = {}): ProjectedBatch {
  return {
    id: "b1",
    communityId: "c",
    foodId: "f",
    unit: "UNIT",
    receivedAt: "2026-11-01T00:00:00Z",
    expirationStatus: "UNKNOWN",
    status: "AVAILABLE",
    initial: parseQuantity("10"),
    available: parseQuantity(available),
    provisional: false,
    pendingDelta: parseQuantity("0"),
    ...extra,
  };
}

describe("distribution validation", () => {
  it("requires families and items", () => {
    expect(validateDistribution([], [], []).form).toMatch(/família/);
    expect(validateDistribution([], ["fam"], []).form).toMatch(/alimento/);
  });

  it("rejects quantities above the projected balance, summing repeated batches", () => {
    const result = validateDistribution(
      [
        { key: "a", batchId: "b1", quantity: "2" },
        { key: "b", batchId: "b1", quantity: "2" },
      ],
      ["fam"],
      [batch("3")],
    );
    expect(result.items.b).toMatch(/Saldo insuficiente/);
    expect(result.items.a).toBeUndefined();
  });

  it("rejects fractions for countable units and blocked batches", () => {
    expect(
      validateDistribution([{ key: "a", batchId: "b1", quantity: "1.5" }], ["f"], [batch("3")])
        .items.a,
    ).toMatch(/inteiro/);
    expect(
      validateDistribution(
        [{ key: "a", batchId: "b1", quantity: "1" }],
        ["f"],
        [batch("3", { status: "BLOCKED" })],
      ).items.a,
    ).toMatch(/bloqueado/);
  });

  it("accepts a valid distribution", () => {
    const result = validateDistribution(
      [{ key: "a", batchId: "b1", quantity: "3" }],
      ["fam"],
      [batch("3")],
    );
    expect(result.form).toBeNull();
  });
});
