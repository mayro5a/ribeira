"use client";

import { useLiveQuery } from "dexie-react-hooks";
import { useEffect, useState } from "react";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { Badge, Card, Notice, PageHeader, Stat } from "@/components/ui/Feedback";
import { TextField } from "@/components/ui/Field";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { describeError } from "@/lib/api/client";
import { META_KEYS } from "@/lib/db/database";
import {
  formatDateTime,
  OPERATION_LABELS,
  SYNC_STATUS_LABELS,
  SYNC_STATUS_TONES,
} from "@/lib/domain/labels";
import { summarizeQueue } from "@/lib/sync/queue";
import { canRetryManually, retryManually } from "@/lib/sync/state-machine";

const conflictSchema = z.object({
  id: z.uuid(),
  kind: z.enum(["CONFLICT", "REJECTED"]),
  status: z.string(),
  reasonCode: z.string(),
  message: z.string(),
  createdAt: z.string(),
  operation: z.object({ operationType: z.string(), createdAt: z.string() }),
});
type Conflict = z.infer<typeof conflictSchema>;

export default function SyncPage() {
  return (
    <Protected>
      <SyncPanel />
    </Protected>
  );
}

function SyncPanel() {
  const { db, connection, syncing, requestSync, lastReport, can } = useApp();
  const queue = useLiveQuery(() => summarizeQueue(db), [db]);
  const lastSyncAt = useLiveQuery(
    async () => (await db.meta.get(META_KEYS.lastSyncAt))?.value as string | undefined,
    [db],
  );
  const outstanding = useLiveQuery(
    async () =>
      (await db.operations.toArray()).filter((op) => op.syncStatus !== "SYNCED").reverse(),
    [db],
  );

  async function exportQueue() {
    const operations = await db.operations.toArray();
    const blob = new Blob(
      [JSON.stringify({ exportedAt: new Date().toISOString(), operations }, null, 2)],
      {
        type: "application/json",
      },
    );
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `ribeira-fila-${new Date().toISOString().slice(0, 10)}.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <>
      <PageHeader title="Sincronização" description={connection.detail} />
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Badge tone={connection.tone}>{connection.label}</Badge>
        <span className="text-sm text-stone-600">
          Última sincronização: {lastSyncAt ? formatDateTime(lastSyncAt) : "nunca"}
        </span>
      </div>
      {lastReport?.status === "revoked" ? (
        <Notice tone="danger" title="Aparelho revogado">
          Os dados baixados foram apagados. As operações não enviadas continuam aqui e podem ser
          exportadas.
        </Notice>
      ) : null}
      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Aguardando envio" value={(queue?.pending ?? 0) + (queue?.sending ?? 0)} />
        <Stat label="Confirmadas" value={queue?.synced ?? 0} />
        <Stat label="Em revisão" value={queue?.conflict ?? 0} />
        <Stat label="Não aceitas" value={queue?.failed ?? 0} />
      </div>
      <div className="mb-6 grid gap-3 sm:grid-cols-2">
        <Button onClick={() => void requestSync()} loading={syncing}>
          Sincronizar agora
        </Button>
        <Button variant="secondary" onClick={exportQueue}>
          Exportar fila (arquivo)
        </Button>
      </div>

      <Card>
        <h2 className="mb-2 text-lg font-bold">Operações não confirmadas</h2>
        {!outstanding?.length ? <p className="text-stone-600">Nenhuma operação pendente.</p> : null}
        <ul className="divide-y divide-stone-100">
          {outstanding?.map((op) => (
            <li key={op.operationId} className="py-3">
              <div className="flex items-center justify-between gap-2">
                <span>
                  {OPERATION_LABELS[op.operationType]}
                  <span className="block text-xs text-stone-500">
                    {formatDateTime(op.createdAt)} · tentativas: {op.attempts}
                  </span>
                </span>
                <Badge tone={SYNC_STATUS_TONES[op.syncStatus]}>
                  {SYNC_STATUS_LABELS[op.syncStatus]}
                </Badge>
              </div>
              {op.lastError ? (
                <p className="mt-1 text-sm text-clay-600">{op.lastError.message}</p>
              ) : null}
              {canRetryManually(op) ? (
                <Button
                  variant="ghost"
                  onClick={async () => {
                    await db.operations.put(retryManually(op, new Date()));
                    void requestSync();
                  }}
                >
                  Tentar novamente
                </Button>
              ) : null}
            </li>
          ))}
        </ul>
      </Card>
      {can("SYNC_CONFLICT_RESOLVE") ? <ConflictReview /> : null}
    </>
  );
}

/** Revisão de conflitos (online). A operação original nunca é apagada. */
function ConflictReview() {
  const { auth, requestSync } = useApp();
  const [conflicts, setConflicts] = useState<Conflict[] | null>(null);
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  const [version, setVersion] = useState(0);

  useEffect(() => {
    let active = true;
    auth.api
      .get("/api/v1/sync/conflicts", { schema: z.array(conflictSchema) })
      .then((loaded) => {
        if (!active) return;
        setConflicts(loaded);
        setError(null);
      })
      .catch((err: unknown) => {
        if (active) setError(describeError(err));
      });
    return () => {
      active = false;
    };
  }, [auth, version]);

  async function resolve(id: string, decision: "RETRY" | "DISCARD") {
    try {
      await auth.api.post(`/api/v1/sync/conflicts/${id}/resolution`, {
        decision,
        notes: notes[id],
      });
      setVersion((v) => v + 1);
      void requestSync();
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <Card>
      <h2 className="mb-2 text-lg font-bold">Conflitos para revisão</h2>
      {error ? <Notice tone="warning">{error}</Notice> : null}
      {conflicts?.length === 0 ? <p className="text-stone-600">Nenhum conflito aberto.</p> : null}
      <ul className="space-y-4">
        {conflicts?.map((c) => (
          <li key={c.id} className="rounded-xl bg-stone-50 p-3">
            <p className="font-semibold">
              {OPERATION_LABELS[c.operation.operationType] ?? c.operation.operationType} ·{" "}
              {c.kind === "CONFLICT" ? "conflito" : "rejeitada"}
            </p>
            <p className="text-sm">{c.message}</p>
            <p className="text-xs text-stone-500">
              Registrada no aparelho em {formatDateTime(c.operation.createdAt)}
            </p>
            <TextField
              label="Justificativa"
              value={notes[c.id] ?? ""}
              onChange={(e) => setNotes((n) => ({ ...n, [c.id]: e.target.value }))}
            />
            <div className="flex flex-wrap gap-2">
              <Button variant="secondary" onClick={() => resolve(c.id, "RETRY")}>
                Reaplicar
              </Button>
              <Button
                variant="danger"
                disabled={!notes[c.id]?.trim()}
                onClick={() => resolve(c.id, "DISCARD")}
              >
                Descartar
              </Button>
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}
