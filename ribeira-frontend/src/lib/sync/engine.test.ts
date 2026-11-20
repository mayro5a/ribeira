import { ApiClient } from "@/lib/api/client";
import { META_KEYS, RibeiraDatabase, getMeta, getSyncCursor } from "@/lib/db/database";
import { buildOperation, type QueuedOperation } from "./operations";
import { enqueue } from "./queue";
import { synchronize } from "./engine";

const community = "2b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const user = "1b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const otherUser = "9b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";
const food = "3b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f";

type Handler = (url: string, init: RequestInit) => Response | Promise<Response>;

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/** Servidor falso idempotente: lembra as operações aplicadas por ID. */
function fakeServer(options: { conflictFor?: Set<string> } = {}) {
  const applied = new Map<string, number>();
  let seq = 0;
  const requests: string[] = [];
  const handler: Handler = (url, init) => {
    requests.push(url);
    if (url.includes("/sync/push")) {
      const body = JSON.parse(String(init.body)) as { operations: { operationId: string }[] };
      return json(200, {
        results: body.operations.map(({ operationId }) => {
          if (options.conflictFor?.has(operationId)) {
            return {
              operationId,
              outcome: "CONFLICT",
              errorCode: "INSUFFICIENT_STOCK",
              message: "Saldo insuficiente",
              conflictId: crypto.randomUUID(),
            };
          }
          const existing = applied.get(operationId);
          if (existing) return { operationId, outcome: "DUPLICATE", serverSeq: existing };
          applied.set(operationId, ++seq);
          return { operationId, outcome: "APPLIED", serverSeq: seq };
        }),
      });
    }
    if (url.includes("/sync/pull")) {
      return json(200, {
        changes: [
          {
            seq: Math.max(seq, 1),
            entityType: "FOOD",
            entityId: food,
            communityId: null,
            data: {
              id: food,
              name: "Arroz",
              category: "GRAINS_AND_CEREALS",
              unit: "KILOGRAM",
              expirationWarningDays: 7,
              active: true,
            },
          },
        ],
        nextCursor: String(Math.max(seq, 1)),
        hasMore: false,
        serverTime: new Date().toISOString(),
      });
    }
    return json(404, { code: "NOT_FOUND" });
  };
  return { handler, applied, requests };
}

function clientFor(handler: Handler) {
  const fetchImpl = (async (input: RequestInfo | URL, init?: RequestInit) =>
    handler(String(input), init ?? {})) as typeof fetch;
  return new ApiClient({ accessToken: () => "token", refresh: async () => null }, fetchImpl, "");
}

function loss(userId = user): QueuedOperation {
  return buildOperation({
    operationType: "LOSS_RECORD",
    communityId: community,
    deviceId: "0b8a7c43-6a0e-4d2c-9a7d-3f4b5c6d7e8f",
    userId,
    payload: {
      lossId: crypto.randomUUID(),
      batchId: crypto.randomUUID(),
      quantity: "1.000",
      occurredAt: new Date().toISOString(),
      reason: "HUMIDITY",
    },
  });
}

describe("sync engine", () => {
  let db: RibeiraDatabase;

  beforeEach(() => {
    db = new RibeiraDatabase(`engine-${crypto.randomUUID()}`);
  });

  afterEach(() => db.close());

  it("pushes the queue, marks operations synced and pulls changes after the cursor", async () => {
    const server = fakeServer();
    const op = await enqueue(db, loss());

    const report = await synchronize({ db, api: clientFor(server.handler), userId: user });

    expect(report).toMatchObject({ status: "ok", pushed: 1, pulled: 1 });
    const stored = await db.operations.get(op.localSeq!);
    expect(stored?.syncStatus).toBe("SYNCED");
    expect(stored?.serverSeq).toBe(1);
    expect(await getSyncCursor(db)).toBe(1);
    expect(await db.foods.count()).toBe(1);
  });

  it("keeps operations pending with backoff when the network fails", async () => {
    const op = await enqueue(db, loss());
    const offline: Handler = () => {
      throw new TypeError("Failed to fetch");
    };

    const report = await synchronize({ db, api: clientFor(offline), userId: user });

    expect(report.status).toBe("offline");
    const stored = await db.operations.get(op.localSeq!);
    expect(stored?.syncStatus).toBe("PENDING");
    expect(stored?.attempts).toBe(1);
    expect(await getMeta(db, META_KEYS.lastSyncError)).toMatchObject({ status: "offline" });
  });

  it("recovers when the server committed but the response was lost", async () => {
    const server = fakeServer();
    const op = await enqueue(db, loss());
    let first = true;
    const losesFirstResponse: Handler = async (url, init) => {
      const response = await server.handler(url, init);
      if (first && url.includes("/sync/push")) {
        first = false;
        throw new TypeError("connection reset");
      }
      return response;
    };

    await synchronize({ db, api: clientFor(losesFirstResponse), userId: user });
    await db.operations.update(op.localSeq!, { nextAttemptAt: 0 });
    await synchronize({ db, api: clientFor(losesFirstResponse), userId: user });

    expect(server.applied.size).toBe(1);
    expect((await db.operations.get(op.localSeq!))?.syncStatus).toBe("SYNCED");
  });

  it("keeps conflicts out of the queue and learns later that they were resolved", async () => {
    const op = await enqueue(db, loss());
    const conflicting = fakeServer({ conflictFor: new Set([op.operationId]) });
    await synchronize({ db, api: clientFor(conflicting.handler), userId: user });
    expect((await db.operations.get(op.localSeq!))?.syncStatus).toBe("CONFLICT");

    const resolved = fakeServer();
    resolved.applied.set(op.operationId, 7);
    await synchronize({ db, api: clientFor(resolved.handler), userId: user });

    const stored = await db.operations.get(op.localSeq!);
    expect(stored?.syncStatus).toBe("SYNCED");
    expect(stored?.serverSeq).toBe(7);
  });

  it("does not count an expired session as a failed attempt", async () => {
    const op = await enqueue(db, loss());
    const expired: Handler = () =>
      json(401, { code: "UNAUTHENTICATED", detail: "Sessão expirada" });

    const report = await synchronize({ db, api: clientFor(expired), userId: user });

    expect(report.status).toBe("unauthenticated");
    const stored = await db.operations.get(op.localSeq!);
    expect(stored?.syncStatus).toBe("PENDING");
    expect(stored?.attempts).toBe(0);
  });

  it("wipes downloaded data but keeps the queue when the device is revoked", async () => {
    await db.foods.put({
      id: food,
      name: "Arroz",
      category: "GRAINS_AND_CEREALS",
      unit: "KILOGRAM",
      expirationWarningDays: 7,
      active: true,
    });
    await enqueue(db, loss());
    const revoked: Handler = () => json(403, { code: "DEVICE_REVOKED", detail: "Revogado" });

    const report = await synchronize({ db, api: clientFor(revoked), userId: user });

    expect(report.status).toBe("revoked");
    expect(await db.foods.count()).toBe(0);
    expect(await db.operations.count()).toBe(1);
  });

  it("sends only the signed-in user's operations", async () => {
    const server = fakeServer();
    const mine = await enqueue(db, loss(user));
    await enqueue(db, loss(otherUser));

    await synchronize({ db, api: clientFor(server.handler), userId: user });

    expect(server.applied.has(mine.operationId)).toBe(true);
    expect(server.applied.size).toBe(1);
  });
});
