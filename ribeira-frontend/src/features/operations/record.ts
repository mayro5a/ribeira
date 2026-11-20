import type { LocalSession } from "@/lib/auth/session";
import { type RibeiraDatabase, getOrCreateDeviceId } from "@/lib/db/database";
import {
  buildOperation,
  type OperationType,
  type PayloadOf,
  type QueuedOperation,
} from "@/lib/sync/operations";
import { enqueue } from "@/lib/sync/queue";

/**
 * Caso de uso comum a todos os registros offline: valida o payload, grava a
 * operação na fila (IndexedDB) e devolve-a como PENDING. O envio acontece
 * depois, pelo motor de sincronização.
 */
export async function recordOperation<T extends OperationType>(
  db: RibeiraDatabase,
  session: LocalSession,
  communityId: string,
  operationType: T,
  payload: PayloadOf<T>,
): Promise<QueuedOperation<T>> {
  const deviceId = await getOrCreateDeviceId(db);
  const operation = buildOperation({
    operationType,
    communityId,
    deviceId,
    userId: session.userId,
    payload,
  });
  return (await enqueue(db, operation)) as QueuedOperation<T>;
}
