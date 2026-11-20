import { buildOperation, type QueuedOperation } from "./operations";
import {
  applyPushResult,
  backoffDelay,
  InvalidTransitionError,
  isProjected,
  markSending,
  markTransportFailure,
  MAX_ATTEMPTS,
  MAX_DELAY_MS,
  recoverInterrupted,
  retryManually,
  returnToQueue,
} from "./state-machine";

const now = new Date("2026-11-20T12:00:00Z");
const ids = {
  device: "0b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
  user: "1b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
  community: "2b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
  batch: "3b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
  loss: "4b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
};

function pendingLoss(): QueuedOperation {
  return buildOperation(
    {
      operationType: "LOSS_RECORD",
      communityId: ids.community,
      deviceId: ids.device,
      userId: ids.user,
      payload: {
        lossId: ids.loss,
        batchId: ids.batch,
        quantity: "1.500",
        occurredAt: now.toISOString(),
        reason: "HUMIDITY",
      },
    },
    now,
  );
}

describe("sync state machine", () => {
  it("creates operations as PENDING with a device-generated UUID", () => {
    const op = pendingLoss();
    expect(op.syncStatus).toBe("PENDING");
    expect(op.operationId).toMatch(/^[0-9a-f-]{36}$/);
    expect(op.entityType).toBe("LOSS");
    expect(op.schemaVersion).toBe(1);
  });

  it("refuses to enqueue an invalid payload", () => {
    expect(() =>
      buildOperation({
        operationType: "LOSS_RECORD",
        communityId: ids.community,
        deviceId: ids.device,
        userId: ids.user,
        payload: {
          lossId: ids.loss,
          batchId: ids.batch,
          quantity: "-1.000",
          occurredAt: now.toISOString(),
          reason: "HUMIDITY",
        },
      }),
    ).toThrow();
  });

  it("only marks SYNCED after server confirmation", () => {
    const sending = markSending(pendingLoss(), now);
    expect(sending.syncStatus).toBe("SENDING");
    const synced = applyPushResult(
      sending,
      { operationId: sending.operationId, outcome: "APPLIED", serverSeq: 42 },
      now,
    );
    expect(synced.syncStatus).toBe("SYNCED");
    expect(synced.serverSeq).toBe(42);
  });

  it("treats DUPLICATE as confirmation (response lost after server commit)", () => {
    const sending = markSending(pendingLoss(), now);
    const result = applyPushResult(
      sending,
      { operationId: sending.operationId, outcome: "DUPLICATE", serverSeq: 7 },
      now,
    );
    expect(result.syncStatus).toBe("SYNCED");
  });

  it("keeps conflicts for review and lets a later check resolve them", () => {
    const sending = markSending(pendingLoss(), now);
    const conflict = applyPushResult(
      sending,
      {
        operationId: sending.operationId,
        outcome: "CONFLICT",
        errorCode: "INSUFFICIENT_STOCK",
        message: "Saldo insuficiente",
        conflictId: "5b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
      },
      now,
    );
    expect(conflict.syncStatus).toBe("CONFLICT");
    expect(conflict.lastError?.code).toBe("INSUFFICIENT_STOCK");

    const resolved = applyPushResult(
      conflict,
      { operationId: conflict.operationId, outcome: "DUPLICATE", serverSeq: 99 },
      now,
    );
    expect(resolved.syncStatus).toBe("SYNCED");
  });

  it("marks rejected and discarded operations as permanently FAILED", () => {
    const sending = markSending(pendingLoss(), now);
    const rejected = applyPushResult(
      sending,
      { operationId: sending.operationId, outcome: "REJECTED", errorCode: "ACCESS_DENIED" },
      now,
    );
    expect(rejected.syncStatus).toBe("FAILED");
    expect(rejected.failureKind).toBe("REJECTED");
    expect(() => retryManually(rejected, now)).toThrow(InvalidTransitionError);
  });

  it("returns to PENDING with backoff after a network failure", () => {
    const sending = markSending(pendingLoss(), now);
    const failed = markTransportFailure(sending, { code: "NETWORK", message: "x" }, now, () => 0.5);
    expect(failed.syncStatus).toBe("PENDING");
    expect(failed.attempts).toBe(1);
    expect(failed.nextAttemptAt).toBe(now.getTime() + 2_000);
  });

  it("gives up after MAX_ATTEMPTS and allows a manual retry", () => {
    let op: QueuedOperation = pendingLoss();
    for (let i = 0; i < MAX_ATTEMPTS; i++) {
      op = markTransportFailure(
        markSending({ ...op, syncStatus: "PENDING" }, now),
        { code: "NETWORK", message: "x" },
        now,
      );
    }
    expect(op.syncStatus).toBe("FAILED");
    expect(op.failureKind).toBe("TRANSPORT");
    const retried = retryManually(op, now);
    expect(retried.syncStatus).toBe("PENDING");
    expect(retried.attempts).toBe(0);
  });

  it("does not count an expired session as an attempt", () => {
    const back = returnToQueue(markSending(pendingLoss(), now), now);
    expect(back.syncStatus).toBe("PENDING");
    expect(back.attempts).toBe(0);
  });

  it("recovers operations interrupted while SENDING", () => {
    const sending = markSending(pendingLoss(), now);
    expect(recoverInterrupted(sending, now).syncStatus).toBe("PENDING");
  });

  it("caps backoff delay", () => {
    expect(backoffDelay(30, () => 0.999)).toBeLessThan(MAX_DELAY_MS);
  });

  it("projects synced operations only until the local cursor reaches serverSeq", () => {
    const synced: QueuedOperation = { ...pendingLoss(), syncStatus: "SYNCED", serverSeq: 10 };
    expect(isProjected(synced, 9)).toBe(true);
    expect(isProjected(synced, 10)).toBe(false);
    expect(isProjected({ ...synced, syncStatus: "CONFLICT" }, 0)).toBe(false);
    expect(isProjected(pendingLoss(), 1000)).toBe(true);
  });
});
