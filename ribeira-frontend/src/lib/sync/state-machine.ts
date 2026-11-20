import type { PushResult } from "./contracts";
import type { OperationError, QueuedOperation, SyncStatus } from "./operations";

/**
 * Máquina de estados da fila (docs/synchronization.md §5). Funções puras: o
 * motor de sincronização decide quando chamá-las e persiste o resultado.
 */

export const MAX_ATTEMPTS = 8;
export const BASE_DELAY_MS = 2_000;
export const MAX_DELAY_MS = 5 * 60_000;

export class InvalidTransitionError extends Error {
  constructor(from: SyncStatus, action: string) {
    super(`Transição inválida: ${action} a partir de ${from}`);
  }
}

function touch<T extends QueuedOperation>(op: T, now: Date, changes: Partial<QueuedOperation>): T {
  return { ...op, ...changes, updatedAt: now.toISOString() };
}

/** Backoff exponencial com "full jitter": evita que aparelhos reenviem em sincronia. */
export function backoffDelay(attempts: number, random: () => number = Math.random): number {
  const ceiling = Math.min(MAX_DELAY_MS, BASE_DELAY_MS * 2 ** attempts);
  return Math.floor(random() * ceiling);
}

export function markSending<T extends QueuedOperation>(op: T, now: Date): T {
  if (op.syncStatus !== "PENDING") {
    throw new InvalidTransitionError(op.syncStatus, "enviar");
  }
  return touch(op, now, { syncStatus: "SENDING" });
}

/**
 * Aplica o resultado individual do servidor. Vale para operações em envio e
 * para a verificação de conflitos (reenvio de uma operação em CONFLICT, que
 * devolve o estado atual dela sem reexecutá-la).
 */
export function applyPushResult<T extends QueuedOperation>(
  op: T,
  result: PushResult,
  now: Date,
): T {
  if (op.syncStatus !== "SENDING" && op.syncStatus !== "CONFLICT") {
    throw new InvalidTransitionError(op.syncStatus, `resultado ${result.outcome}`);
  }
  const error: OperationError | undefined = result.errorCode
    ? { code: result.errorCode, message: result.message ?? result.errorCode }
    : undefined;

  switch (result.outcome) {
    case "APPLIED":
    case "DUPLICATE":
      return touch(op, now, {
        syncStatus: "SYNCED",
        serverSeq: result.serverSeq ?? undefined,
        lastError: undefined,
        failureKind: undefined,
      });
    case "CONFLICT":
      return touch(op, now, {
        syncStatus: "CONFLICT",
        conflictId: result.conflictId ?? op.conflictId,
        lastError: error,
      });
    case "REJECTED":
      return touch(op, now, { syncStatus: "FAILED", failureKind: "REJECTED", lastError: error });
    case "DISCARDED":
      return touch(op, now, { syncStatus: "FAILED", failureKind: "DISCARDED", lastError: error });
  }
}

/** Falha de transporte (rede, timeout, 5xx): volta à fila com backoff ou desiste. */
export function markTransportFailure<T extends QueuedOperation>(
  op: T,
  error: OperationError,
  now: Date,
  random: () => number = Math.random,
): T {
  if (op.syncStatus === "CONFLICT") {
    return op; // a verificação de conflito falhou; nada muda.
  }
  if (op.syncStatus !== "SENDING") {
    throw new InvalidTransitionError(op.syncStatus, "falha de transporte");
  }
  const attempts = op.attempts + 1;
  if (attempts >= MAX_ATTEMPTS) {
    return touch(op, now, {
      syncStatus: "FAILED",
      failureKind: "TRANSPORT",
      attempts,
      lastError: error,
    });
  }
  return touch(op, now, {
    syncStatus: "PENDING",
    attempts,
    nextAttemptAt: now.getTime() + backoffDelay(attempts, random),
    lastError: error,
  });
}

/**
 * Sessão expirada (401) não conta como tentativa: a operação aguarda o novo
 * login sem se aproximar do limite de tentativas.
 */
export function returnToQueue<T extends QueuedOperation>(op: T, now: Date): T {
  if (op.syncStatus !== "SENDING") {
    throw new InvalidTransitionError(op.syncStatus, "devolver à fila");
  }
  return touch(op, now, { syncStatus: "PENDING" });
}

/** App reaberto com operação presa em SENDING: seguro reenviar (servidor idempotente). */
export function recoverInterrupted<T extends QueuedOperation>(op: T, now: Date): T {
  return op.syncStatus === "SENDING" ? touch(op, now, { syncStatus: "PENDING" }) : op;
}

export function canRetryManually(op: QueuedOperation): boolean {
  return op.syncStatus === "FAILED" && op.failureKind === "TRANSPORT";
}

export function retryManually<T extends QueuedOperation>(op: T, now: Date): T {
  if (!canRetryManually(op)) {
    throw new InvalidTransitionError(op.syncStatus, "nova tentativa manual");
  }
  return touch(op, now, {
    syncStatus: "PENDING",
    attempts: 0,
    nextAttemptAt: now.getTime(),
    failureKind: undefined,
  });
}

/**
 * Se os efeitos da operação devem aparecer na projeção local. Uma operação
 * confirmada continua projetada até o cursor local alcançar o `serverSeq`
 * dela — antes disso os snapshots locais ainda não contêm seus efeitos.
 */
export function isProjected(op: QueuedOperation, localCursor: number): boolean {
  switch (op.syncStatus) {
    case "PENDING":
    case "SENDING":
      return true;
    case "SYNCED":
      return op.serverSeq === undefined ? false : op.serverSeq > localCursor;
    case "CONFLICT":
    case "FAILED":
      return false;
  }
}
