"use client";

import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Badge, Card, EmptyState, Notice, PageHeader } from "@/components/ui/Feedback";
import { TextAreaField, TextField } from "@/components/ui/Field";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useFoods, useProjectedBatches, useStorageLocations } from "@/features/data/hooks";
import { recordOperation } from "@/features/operations/record";
import { fefoOrder, type ProjectedBatch, stockByFood } from "@/lib/domain/projection";
import { CATEGORY_LABELS, EXPIRATION_LABELS, formatDate } from "@/lib/domain/labels";
import {
  formatQuantity,
  type Milli,
  parseQuantity,
  subtract,
  toDecimalString,
  UNIT_LABELS,
  ZERO,
} from "@/lib/domain/quantity";

export default function InventoryPage() {
  return (
    <Protected>
      <Inventory />
    </Protected>
  );
}

function Inventory() {
  const batches = useProjectedBatches();
  const foods = useFoods();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState<string | null>(null);

  if (!batches) return <p role="status">Carregando estoque…</p>;
  const items = stockByFood(batches)
    .map((s) => ({ ...s, food: foods.get(s.foodId) }))
    .filter((s) =>
      (s.food?.name ?? "").toLocaleLowerCase("pt-BR").includes(query.toLocaleLowerCase("pt-BR")),
    )
    .sort((a, b) => (a.food?.name ?? "").localeCompare(b.food?.name ?? "", "pt-BR"));

  return (
    <>
      <PageHeader
        title="Estoque"
        description="Saldos confirmados pelo servidor somados aos registros ainda não enviados."
      />
      <TextField
        label="Buscar alimento"
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
      />
      {items.length === 0 ? (
        <EmptyState title="Nenhum alimento em estoque">
          Registre uma doação para criar lotes.
        </EmptyState>
      ) : (
        <ul className="space-y-3">
          {items.map((item) => (
            <li key={item.foodId}>
              <Card className="mb-0">
                <button
                  type="button"
                  className="flex w-full min-h-12 items-center justify-between gap-3 text-left"
                  aria-expanded={open === item.foodId}
                  onClick={() => setOpen(open === item.foodId ? null : item.foodId)}
                >
                  <span>
                    <span className="block text-lg font-semibold">
                      {item.food?.name ?? "Alimento"}
                    </span>
                    <span className="text-sm text-stone-600">
                      {item.food ? CATEGORY_LABELS[item.food.category] : ""} · {item.batchCount}{" "}
                      lote(s)
                      {item.nextExpiration
                        ? ` · próxima validade ${formatDate(item.nextExpiration)}`
                        : ""}
                    </span>
                  </span>
                  <span className="text-right">
                    <span className="block text-xl font-bold">
                      {formatQuantity(item.available, item.unit)}
                    </span>
                    {item.hasProvisional ? <Badge tone="warning">provisório</Badge> : null}
                  </span>
                </button>
                {open === item.foodId ? (
                  <BatchList
                    batches={fefoOrder(batches.filter((b) => b.foodId === item.foodId)).concat(
                      batches.filter((b) => b.foodId === item.foodId && b.status === "BLOCKED"),
                    )}
                  />
                ) : null}
              </Card>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

function BatchList({ batches }: { batches: ProjectedBatch[] }) {
  const locations = useStorageLocations();
  const { can } = useApp();
  const [adjusting, setAdjusting] = useState<string | null>(null);
  return (
    <div className="mt-3 border-t border-stone-100 pt-3">
      <p className="mb-2 text-sm text-stone-600">
        Ordem sugerida de uso (vence primeiro sai primeiro). A escolha final é sua.
      </p>
      <ul className="space-y-2">
        {batches.map((b) => (
          <li key={b.id} className="rounded-xl bg-stone-50 p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-semibold">{formatQuantity(b.available, b.unit)}</span>
              <span className="flex gap-1">
                {b.provisional ? <Badge tone="warning">não confirmado</Badge> : null}
                {b.status === "BLOCKED" ? <Badge tone="danger">bloqueado</Badge> : null}
              </span>
            </div>
            <p className="text-sm text-stone-600">
              {b.expirationStatus === "KNOWN" && b.expirationDate
                ? `Validade ${formatDate(b.expirationDate)}`
                : EXPIRATION_LABELS[b.expirationStatus]}
              {b.storageLocationId
                ? ` · ${locations.find((l) => l.id === b.storageLocationId)?.name ?? "local"}`
                : ""}
            </p>
            {can("STOCK_ADJUST") && !b.provisional ? (
              adjusting === b.id ? (
                <AdjustmentForm batch={b} onDone={() => setAdjusting(null)} />
              ) : (
                <Button variant="ghost" className="mt-1" onClick={() => setAdjusting(b.id)}>
                  Ajustar saldo
                </Button>
              )
            ) : null}
          </li>
        ))}
      </ul>
    </div>
  );
}

function AdjustmentForm({ batch, onDone }: { batch: ProjectedBatch; onDone: () => void }) {
  const { db, session, communityId, requestSync } = useApp();
  const [counted, setCounted] = useState("");
  const [justification, setJustification] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!session || !communityId) return;
    let delta: Milli;
    try {
      // O ajuste registra a diferença entre a contagem física e o saldo atual.
      delta = subtract(parseQuantity(counted), batch.available);
    } catch {
      setError("Informe a quantidade contada (até 3 casas decimais).");
      return;
    }
    if (delta === ZERO) {
      setError("A contagem é igual ao saldo atual.");
      return;
    }
    if (justification.trim().length < 5) {
      setError("A justificativa é obrigatória (mínimo de 5 caracteres).");
      return;
    }
    await recordOperation(db, session, communityId, "STOCK_ADJUSTMENT", {
      movementId: crypto.randomUUID(),
      batchId: batch.id,
      quantityDelta: toDecimalString(delta),
      justification: justification.trim(),
    });
    void requestSync();
    onDone();
  }

  return (
    <form onSubmit={submit} className="mt-2" noValidate>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      <TextField
        label={`Quantidade contada (${UNIT_LABELS[batch.unit].short})`}
        inputMode="decimal"
        value={counted}
        onChange={(e) => setCounted(e.target.value)}
        required
      />
      <TextAreaField
        label="Justificativa"
        value={justification}
        onChange={(e) => setJustification(e.target.value)}
        required
      />
      <div className="flex gap-2">
        <Button type="submit">Salvar ajuste</Button>
        <Button variant="ghost" onClick={onDone}>
          Cancelar
        </Button>
      </div>
    </form>
  );
}
