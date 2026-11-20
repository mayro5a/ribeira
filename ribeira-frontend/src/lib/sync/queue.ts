import type { Table } from "dexie";
import type { RibeiraDatabase } from "@/lib/db/database";
import type { QueuedOperation } from "./operations";
import { recoverInterrupted } from "./state-machine";

/**
 * Persiste a operação na fila. `alsoWrite` recebe outras tabelas que precisam
 * mudar junto (ex.: rascunhos); tudo ocorre na mesma transação IndexedDB, então
 * ou tudo é gravado ou nada é.
 */
export async function enqueue(
  db: RibeiraDatabase,
  operation: QueuedOperation,
  alsoWrite?: { tables: Table[]; write: () => Promise<void> },
): Promise<QueuedOperation> {
  const tables = [db.operations, ...(alsoWrite?.tables ?? [])];
  return db.transaction("rw", tables, async () => {
    const localSeq = await db.operations.add(operation);
    await alsoWrite?.write();
    return { ...operation, localSeq };
  });
}

/**
 * Próximas operações a enviar, em ordem de criação. Se a mais antiga ainda
 * está em espera (backoff), nenhuma posterior é enviada antes dela: operações
 * seguintes podem depender de entidades criadas pelas anteriores.
 */
export async function nextSendable(
  db: RibeiraDatabase,
  now: number,
  limit: number,
): Promise<QueuedOperation[]> {
  const pending = await db.operations.where("syncStatus").equals("PENDING").sortBy("localSeq");
  const first = pending[0];
  if (!first || first.nextAttemptAt > now) {
    return [];
  }
  return pending.slice(0, limit);
}

export async function saveOperations(
  db: RibeiraDatabase,
  operations: QueuedOperation[],
): Promise<void> {
  await db.operations.bulkPut(operations);
}

/** Executado ao abrir o app: o envio anterior pode ter sido interrompido. */
export async function recoverInterruptedOperations(
  db: RibeiraDatabase,
  now: Date,
): Promise<number> {
  return db.transaction("rw", db.operations, async () => {
    const stuck = await db.operations.where("syncStatus").equals("SENDING").toArray();
    await db.operations.bulkPut(stuck.map((op) => recoverInterrupted(op, now)));
    return stuck.length;
  });
}

export interface QueueSummary {
  pending: number;
  sending: number;
  synced: number;
  failed: number;
  conflict: number;
}

export async function summarizeQueue(db: RibeiraDatabase): Promise<QueueSummary> {
  const all = await db.operations.toArray();
  const count = (status: QueuedOperation["syncStatus"]) =>
    all.filter((op) => op.syncStatus === status).length;
  return {
    pending: count("PENDING"),
    sending: count("SENDING"),
    synced: count("SYNCED"),
    failed: count("FAILED"),
    conflict: count("CONFLICT"),
  };
}
