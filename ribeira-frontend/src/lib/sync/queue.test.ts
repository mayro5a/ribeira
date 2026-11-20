import { RibeiraDatabase, getOrCreateDeviceId } from "@/lib/db/database";
import { buildOperation, type QueuedOperation } from "./operations";
import { enqueue, nextSendable, recoverInterruptedOperations, summarizeQueue } from "./queue";
import { markSending } from "./state-machine";

const community = "2b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const user = "1b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";

function lossOperation(deviceId: string, now = new Date()): QueuedOperation {
  return buildOperation(
    {
      operationType: "LOSS_RECORD",
      communityId: community,
      deviceId,
      userId: user,
      payload: {
        lossId: crypto.randomUUID(),
        batchId: crypto.randomUUID(),
        quantity: "2.000",
        occurredAt: now.toISOString(),
        reason: "PESTS",
      },
    },
    now,
  );
}

describe("operation queue (IndexedDB)", () => {
  let dbName: string;

  beforeEach(() => {
    dbName = `test-${crypto.randomUUID()}`;
  });

  it("keeps operations after the database is closed and reopened", async () => {
    const first = new RibeiraDatabase(dbName);
    const deviceId = await getOrCreateDeviceId(first);
    const op = await enqueue(first, lossOperation(deviceId));
    first.close();

    const reopened = new RibeiraDatabase(dbName);
    const stored = await reopened.operations.where("operationId").equals(op.operationId).first();
    expect(stored?.syncStatus).toBe("PENDING");
    expect(await getOrCreateDeviceId(reopened)).toBe(deviceId);
    reopened.close();
  });

  it("rolls back the operation when a write in the same transaction fails", async () => {
    const db = new RibeiraDatabase(dbName);
    const op = lossOperation("0b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f");
    await expect(
      enqueue(db, op, {
        tables: [db.meta],
        write: async () => {
          throw new Error("disk full");
        },
      }),
    ).rejects.toThrow("disk full");
    expect(await db.operations.count()).toBe(0);
    db.close();
  });

  it("rejects the same operationId twice", async () => {
    const db = new RibeiraDatabase(dbName);
    const op = lossOperation("0b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f");
    await enqueue(db, op);
    await expect(enqueue(db, op)).rejects.toThrow();
    db.close();
  });

  it("sends in creation order and waits for the oldest in backoff", async () => {
    const db = new RibeiraDatabase(dbName);
    const device = "0b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
    const a = await enqueue(db, lossOperation(device));
    const b = await enqueue(db, lossOperation(device));
    const now = Date.now();

    expect((await nextSendable(db, now, 10)).map((op) => op.operationId)).toEqual([
      a.operationId,
      b.operationId,
    ]);

    await db.operations.put({ ...a, nextAttemptAt: now + 60_000 });
    expect(await nextSendable(db, now, 10)).toEqual([]);
    db.close();
  });

  it("recovers operations stuck in SENDING after a crash", async () => {
    const db = new RibeiraDatabase(dbName);
    const op = await enqueue(db, lossOperation("0b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f"));
    await db.operations.put(markSending(op, new Date()));

    expect(await recoverInterruptedOperations(db, new Date())).toBe(1);
    expect((await summarizeQueue(db)).pending).toBe(1);
    db.close();
  });
});
