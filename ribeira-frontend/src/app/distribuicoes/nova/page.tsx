"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useMemo, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, Notice, PageHeader } from "@/components/ui/Feedback";
import { CheckboxField, SelectField, TextAreaField, TextField } from "@/components/ui/Field";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useFamilies, useFoods, useProjectedBatches } from "@/features/data/hooks";
import { type DraftItem, validateDistribution } from "@/features/distribution/validation";
import { recordOperation } from "@/features/operations/record";
import { fefoOrder } from "@/lib/domain/projection";
import { formatDate, fromLocalInputValue, toLocalInputValue } from "@/lib/domain/labels";
import { formatQuantity, parseQuantity, toDecimalString, UNIT_LABELS } from "@/lib/domain/quantity";

export default function NewDistributionPage() {
  return (
    <Protected>
      <NewDistribution />
    </Protected>
  );
}

function NewDistribution() {
  const { db, session, communityId, can, requestSync } = useApp();
  const router = useRouter();
  const families = useFamilies().filter((f) => f.status === "ACTIVE");
  const foods = useFoods();
  const projected = useProjectedBatches();
  const batches = useMemo(() => projected ?? [], [projected]);
  const available = useMemo(() => fefoOrder(batches), [batches]);
  const [familyIds, setFamilyIds] = useState<string[]>([]);
  const [familyFilter, setFamilyFilter] = useState("");
  const [items, setItems] = useState<DraftItem[]>([
    { key: crypto.randomUUID(), batchId: "", quantity: "" },
  ]);
  const [distributedAt, setDistributedAt] = useState(() => toLocalInputValue(new Date()));
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<{ form: string | null; items: Record<string, string> }>({
    form: null,
    items: {},
  });
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  if (!can("DISTRIBUTION_WRITE")) {
    return (
      <Notice tone="warning" title="Sem permissão">
        Seu perfil não registra distribuições nesta comunidade.
      </Notice>
    );
  }

  const label = (batchId: string) => {
    const b = batches.find((x) => x.id === batchId);
    if (!b) return "";
    const validity = b.expirationDate ? ` · val. ${formatDate(b.expirationDate)}` : "";
    return `${foods.get(b.foodId)?.name ?? "Alimento"} — ${formatQuantity(b.available, b.unit)}${validity}${b.provisional ? " (não confirmado)" : ""}`;
  };

  function review(event: FormEvent) {
    event.preventDefault();
    const result = validateDistribution(items, familyIds, batches);
    setErrors(result);
    setConfirming(result.form === null);
  }

  async function save() {
    if (!session || !communityId) return;
    setSaving(true);
    const distributionId = crypto.randomUUID();
    try {
      await recordOperation(db, session, communityId, "DISTRIBUTION_CREATE", {
        distributionId,
        distributedAt: fromLocalInputValue(distributedAt),
        notes: notes.trim() || undefined,
        familyIds,
        items: items.map((i) => ({
          itemId: crypto.randomUUID(),
          batchId: i.batchId,
          quantity: toDecimalString(parseQuantity(i.quantity)),
        })),
      });
      void requestSync();
      router.push(`/distribuicoes/comprovante/?id=${distributionId}`);
    } finally {
      setSaving(false);
    }
  }

  const visibleFamilies = families.filter((f) =>
    f.code.toLowerCase().includes(familyFilter.toLowerCase()),
  );

  return (
    <>
      <PageHeader
        title="Nova distribuição"
        description="O saldo é conferido no aparelho e confirmado pelo servidor na sincronização."
      />
      {errors.form ? <Notice tone="danger">{errors.form}</Notice> : null}
      <form onSubmit={review} noValidate>
        <Card>
          <h2 className="mb-2 font-bold">Famílias atendidas ({familyIds.length})</h2>
          <TextField
            label="Buscar pelo código"
            type="search"
            value={familyFilter}
            onChange={(e) => setFamilyFilter(e.target.value)}
          />
          <fieldset className="max-h-72 overflow-y-auto">
            <legend className="sr-only">Famílias</legend>
            {visibleFamilies.map((f) => (
              <CheckboxField
                key={f.id}
                label={f.code}
                hint={`${f.memberCount} integrante(s)`}
                checked={familyIds.includes(f.id)}
                onChange={(e) =>
                  setFamilyIds((current) =>
                    e.target.checked ? [...current, f.id] : current.filter((id) => id !== f.id),
                  )
                }
              />
            ))}
            {families.length === 0 ? (
              <p className="text-stone-600">Nenhuma família disponível neste aparelho.</p>
            ) : null}
          </fieldset>
        </Card>
        {items.map((item, index) => {
          const batch = batches.find((b) => b.id === item.batchId);
          return (
            <Card key={item.key}>
              <h2 className="mb-2 font-bold">Alimento {index + 1}</h2>
              <SelectField
                label="Lote"
                hint="Lista em ordem sugerida de validade; escolha outro se necessário."
                value={item.batchId}
                onChange={(e) =>
                  setItems((c) =>
                    c.map((i) => (i.key === item.key ? { ...i, batchId: e.target.value } : i)),
                  )
                }
                error={errors.items[item.key] && !item.batchId ? errors.items[item.key] : undefined}
              >
                <option value="">Selecione</option>
                {available.map((b) => (
                  <option key={b.id} value={b.id}>
                    {label(b.id)}
                  </option>
                ))}
              </SelectField>
              <TextField
                label={`Quantidade${batch ? ` (${UNIT_LABELS[batch.unit].short})` : ""}`}
                inputMode="decimal"
                value={item.quantity}
                onChange={(e) =>
                  setItems((c) =>
                    c.map((i) => (i.key === item.key ? { ...i, quantity: e.target.value } : i)),
                  )
                }
                error={item.batchId ? errors.items[item.key] : undefined}
              />
              {items.length > 1 ? (
                <Button
                  variant="ghost"
                  onClick={() => setItems((c) => c.filter((i) => i.key !== item.key))}
                >
                  Remover
                </Button>
              ) : null}
            </Card>
          );
        })}
        <Button
          variant="secondary"
          block
          className="mb-4"
          onClick={() =>
            setItems((c) => [...c, { key: crypto.randomUUID(), batchId: "", quantity: "" }])
          }
        >
          Adicionar alimento
        </Button>
        <TextField
          label="Data e hora"
          type="datetime-local"
          value={distributedAt}
          onChange={(e) => setDistributedAt(e.target.value)}
        />
        <TextAreaField
          label="Observações"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          maxLength={1000}
        />
        {!confirming ? (
          <Button type="submit" block>
            Revisar distribuição
          </Button>
        ) : null}
      </form>
      {confirming ? (
        <Card className="border-river-600">
          <h2 className="mb-2 text-lg font-bold">Confirme a distribuição</h2>
          <p className="mb-2">{familyIds.length} família(s)</p>
          <ul className="mb-4 list-disc pl-5">
            {items.map((i) => (
              <li key={i.key}>
                {label(i.batchId).split(" — ")[0]}: {i.quantity}
              </li>
            ))}
          </ul>
          <div className="flex gap-2">
            <Button onClick={save} loading={saving}>
              Confirmar
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              Voltar
            </Button>
          </div>
        </Card>
      ) : null}
    </>
  );
}
