import type { SyncReport } from "@/lib/sync/engine";
import type { QueueSummary } from "@/lib/sync/queue";

export type ConnectionKind =
  "offline" | "syncing" | "pending" | "synced" | "failed" | "conflicts" | "session-expired";

export interface ConnectionState {
  kind: ConnectionKind;
  label: string;
  tone: "neutral" | "info" | "success" | "warning" | "danger";
  detail: string;
}

/**
 * Estado exibido ao usuário (docs/synchronization.md §5). Derivado do resultado
 * real da última tentativa — não de `navigator.onLine` — e da fila local.
 */
export function deriveConnectionState(input: {
  running: boolean;
  authenticated: boolean;
  browserOnline: boolean;
  lastReport?: SyncReport;
  queue: QueueSummary;
}): ConnectionState {
  const { running, lastReport, queue } = input;
  const waiting = queue.pending + queue.sending;
  if (running) {
    return {
      kind: "syncing",
      label: "Sincronizando",
      tone: "info",
      detail: "Enviando e recebendo dados.",
    };
  }
  if (queue.conflict > 0) {
    return {
      kind: "conflicts",
      label: "Com conflitos",
      tone: "danger",
      detail: `${queue.conflict} operação(ões) aguardando revisão.`,
    };
  }
  if (lastReport?.status === "unauthenticated") {
    return {
      kind: "session-expired",
      label: "Entre novamente",
      tone: "warning",
      detail: "A sessão expirou. Os dados continuam salvos no aparelho.",
    };
  }
  const offline =
    !input.browserOnline ||
    lastReport?.status === "offline" ||
    (!input.authenticated && !lastReport);
  if (offline) {
    return {
      kind: "offline",
      label: "Offline",
      tone: "neutral",
      detail:
        waiting > 0
          ? `${waiting} operação(ões) salvas no aparelho.`
          : "Trabalhando com os dados do aparelho.",
    };
  }
  if (queue.failed > 0 || lastReport?.status === "error" || lastReport?.status === "revoked") {
    return {
      kind: "failed",
      label: "Com falha",
      tone: "danger",
      detail: lastReport?.message ?? `${queue.failed} operação(ões) não aceitas.`,
    };
  }
  if (waiting > 0) {
    return {
      kind: "pending",
      label: "Pendente",
      tone: "warning",
      detail: `${waiting} operação(ões) aguardando envio.`,
    };
  }
  return {
    kind: "synced",
    label: "Sincronizado",
    tone: "success",
    detail: "Tudo confirmado pelo servidor.",
  };
}
