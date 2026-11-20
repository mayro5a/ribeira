import { ApiError, type ApiClient, NetworkError } from "@/lib/api/client";
import {
  META_KEYS,
  type RibeiraDatabase,
  getOrCreateDeviceId,
  getSyncCursor,
  setMeta,
} from "@/lib/db/database";
import { applyPullPage } from "./apply-pull";
import {
  MAX_OPERATIONS_PER_PUSH,
  type PushResult,
  pullResponseSchema,
  pushResponseSchema,
} from "./contracts";
import { type QueuedOperation, toWireOperation } from "./operations";
import { nextSendable, recoverInterruptedOperations, saveOperations } from "./queue";
import { applyPushResult, markSending, markTransportFailure, returnToQueue } from "./state-machine";

export type SyncRunStatus = "ok" | "offline" | "unauthenticated" | "revoked" | "error";

export interface SyncReport {
  status: SyncRunStatus;
  pushed: number;
  pulled: number;
  message?: string;
}

export interface SyncDependencies {
  db: RibeiraDatabase;
  api: ApiClient;
  /** Usuário autenticado; só as operações dele são enviadas. */
  userId: string;
  now?: () => Date;
  random?: () => number;
}

const PULL_PAGE_SIZE = 200;
const MAX_PULL_PAGES = 100;

class StopSync extends Error {
  constructor(
    readonly status: SyncRunStatus,
    message: string,
  ) {
    super(message);
  }
}

/**
 * Um ciclo completo: recupera envios interrompidos, envia a fila em ordem,
 * verifica conflitos e baixa alterações a partir do cursor confirmado.
 * Seguro para repetir a qualquer momento: o servidor é idempotente.
 */
export async function synchronize(deps: SyncDependencies): Promise<SyncReport> {
  const now = deps.now ?? (() => new Date());
  const report: SyncReport = { status: "ok", pushed: 0, pulled: 0 };
  try {
    await recoverInterruptedOperations(deps.db, now());
    const deviceId = await getOrCreateDeviceId(deps.db);
    report.pushed = await pushPending(deps, deviceId, now);
    await recheckConflicts(deps, deviceId, now);
    report.pulled = await pullChanges(deps, deviceId);
    await setMeta(deps.db, META_KEYS.lastSyncAt, now().toISOString());
    await setMeta(deps.db, META_KEYS.lastSyncError, null);
  } catch (error) {
    const stop =
      error instanceof StopSync ? error : new StopSync(classify(error), errorMessage(error));
    report.status = stop.status;
    report.message = stop.message;
    await setMeta(deps.db, META_KEYS.lastSyncError, {
      status: stop.status,
      message: stop.message,
      at: now().toISOString(),
    });
    if (stop.status === "revoked") {
      await handleRevocation(deps.db);
    }
  }
  return report;
}

async function pushPending(deps: SyncDependencies, deviceId: string, now: () => Date) {
  let pushed = 0;
  for (;;) {
    const batch = (await nextSendable(deps.db, now().getTime(), MAX_OPERATIONS_PER_PUSH)).filter(
      (op) => op.userId === deps.userId,
    );
    if (batch.length === 0) {
      return pushed;
    }
    const sending = batch.map((op) => markSending(op, now()));
    await saveOperations(deps.db, sending);
    const results = await send(deps, deviceId, sending, now, "push");
    await saveOperations(deps.db, applyResults(sending, results, now));
    pushed += sending.length;
  }
}

/** Reenvia operações em conflito para saber se já foram resolvidas no servidor. */
async function recheckConflicts(deps: SyncDependencies, deviceId: string, now: () => Date) {
  const conflicts = (
    await deps.db.operations.where("syncStatus").equals("CONFLICT").sortBy("localSeq")
  )
    .filter((op) => op.userId === deps.userId)
    .slice(0, MAX_OPERATIONS_PER_PUSH);
  if (conflicts.length === 0) {
    return;
  }
  const results = await send(deps, deviceId, conflicts, now, "recheck");
  await saveOperations(deps.db, applyResults(conflicts, results, now));
}

async function send(
  deps: SyncDependencies,
  deviceId: string,
  operations: QueuedOperation[],
  now: () => Date,
  mode: "push" | "recheck",
): Promise<PushResult[]> {
  try {
    const response = await deps.api.post(
      "/api/v1/sync/push",
      { deviceId, operations: operations.map(toWireOperation) },
      { schema: pushResponseSchema },
    );
    return response.results;
  } catch (error) {
    if (mode === "push") {
      const status = classify(error);
      const restored =
        status === "unauthenticated" || status === "revoked"
          ? operations.map((op) => returnToQueue(op, now()))
          : operations.map((op) =>
              markTransportFailure(
                op,
                { code: errorCode(error), message: errorMessage(error) },
                now(),
                deps.random,
              ),
            );
      await saveOperations(deps.db, restored);
    }
    throw new StopSync(classify(error), errorMessage(error));
  }
}

function applyResults(
  operations: QueuedOperation[],
  results: PushResult[],
  now: () => Date,
): QueuedOperation[] {
  const byId = new Map(results.map((r) => [r.operationId, r]));
  return operations.map((op) => {
    const result = byId.get(op.operationId);
    if (!result) {
      return op.syncStatus === "SENDING"
        ? markTransportFailure(
            op,
            { code: "MISSING_RESULT", message: "Sem resposta para a operação." },
            now(),
          )
        : op;
    }
    return applyPushResult(op, result, now());
  });
}

async function pullChanges(deps: SyncDependencies, deviceId: string): Promise<number> {
  let pulled = 0;
  for (let page = 0; page < MAX_PULL_PAGES; page++) {
    const cursor = await getSyncCursor(deps.db);
    const response = await deps.api
      .get(`/api/v1/sync/pull?cursor=${cursor}&limit=${PULL_PAGE_SIZE}`, {
        schema: pullResponseSchema,
        headers: { "X-Device-Id": deviceId },
      })
      .catch((error: unknown) => {
        throw new StopSync(classify(error), errorMessage(error));
      });
    pulled += await applyPullPage(deps.db, response);
    if (!response.hasMore) {
      break;
    }
  }
  return pulled;
}

/**
 * Aparelho revogado: apaga os dados baixados (inclusive famílias) e mantém só a
 * fila, que pode ser exportada pela tela de sincronização.
 */
async function handleRevocation(db: RibeiraDatabase): Promise<void> {
  await db.transaction("rw", [db.meta, ...db.snapshotTables], async () => {
    await Promise.all(db.snapshotTables.map((table) => table.clear()));
    await setMeta(db, META_KEYS.syncCursor, 0);
  });
}

function classify(error: unknown): SyncRunStatus {
  if (error instanceof NetworkError) return "offline";
  if (error instanceof ApiError) {
    if (error.status === 401) return "unauthenticated";
    if (error.code === "DEVICE_REVOKED") return "revoked";
  }
  return "error";
}

function errorCode(error: unknown): string {
  if (error instanceof ApiError) return error.code;
  if (error instanceof NetworkError) return "NETWORK";
  return "UNEXPECTED";
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  return "Falha desconhecida na sincronização.";
}
