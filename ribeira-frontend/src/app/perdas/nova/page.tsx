"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice, PageHeader } from "@/components/ui/Feedback";
import { SelectField, TextAreaField, TextField } from "@/components/ui/Field";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useFoods, useProjectedBatches } from "@/features/data/hooks";
import { recordOperation } from "@/features/operations/record";
import { LOSS_REASONS, type LossReason } from "@/lib/api/schemas";
import {
  formatDate,
  fromLocalInputValue,
  LOSS_REASON_LABELS,
  toLocalInputValue,
} from "@/lib/domain/labels";
import {
  formatQuantity,
  parseQuantity,
  toDecimalString,
  UNIT_LABELS,
  validateQuantityForUnit,
} from "@/lib/domain/quantity";

export default function NewLossPage() {
  return (
    <Protected>
      <NewLoss />
    </Protected>
  );
}

function NewLoss() {
  const { db, session, communityId, can, requestSync } = useApp();
  const router = useRouter();
  const foods = useFoods();
  const batches = (useProjectedBatches() ?? []).filter((b) => b.status !== "DEPLETED");
  const [batchId, setBatchId] = useState("");
  const [quantity, setQuantity] = useState("");
  const [reason, setReason] = useState<LossReason | "">("");
  const [description, setDescription] = useState("");
  const [occurredAt, setOccurredAt] = useState(() => toLocalInputValue(new Date()));
  const [errors, setErrors] = useState<Record<string, string>>({});

  if (!can("LOSS_WRITE")) {
    return (
      <Notice tone="warning" title="Sem permissão">
        Seu perfil não registra perdas nesta comunidade.
      </Notice>
    );
  }
  const batch = batches.find((b) => b.id === batchId);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found: Record<string, string> = {};
    if (!batch) found.batch = "Selecione o lote afetado.";
    if (!reason) found.reason = "Selecione o motivo.";
    if (batch) {
      const invalid = validateQuantityForUnit(quantity, batch.unit);
      if (invalid) found.quantity = invalid;
      else if (parseQuantity(quantity) > batch.available)
        found.quantity = `Maior que o saldo (${formatQuantity(batch.available, batch.unit)}).`;
    }
    setErrors(found);
    if (Object.keys(found).length || !session || !communityId || !batch || !reason) return;
    await recordOperation(db, session, communityId, "LOSS_RECORD", {
      lossId: crypto.randomUUID(),
      batchId: batch.id,
      quantity: toDecimalString(parseQuantity(quantity)),
      occurredAt: fromLocalInputValue(occurredAt),
      reason,
      description: description.trim() || undefined,
    });
    void requestSync();
    router.push("/perdas/");
  }

  return (
    <>
      <PageHeader
        title="Registrar perda"
        description="Gera uma saída de estoque do lote escolhido."
      />
      <Notice tone="info">
        O Ribeira registra o que foi observado e não avalia se um alimento é próprio para consumo.
        Em caso de dúvida, peça inspeção a uma pessoa responsável.
      </Notice>
      <form onSubmit={submit} noValidate>
        <SelectField
          label="Lote afetado"
          value={batchId}
          onChange={(e) => setBatchId(e.target.value)}
          error={errors.batch}
          required
        >
          <option value="">Selecione</option>
          {batches.map((b) => (
            <option key={b.id} value={b.id}>
              {foods.get(b.foodId)?.name ?? "Alimento"} — {formatQuantity(b.available, b.unit)}
              {b.expirationDate ? ` · val. ${formatDate(b.expirationDate)}` : ""}
            </option>
          ))}
        </SelectField>
        <TextField
          label={`Quantidade perdida${batch ? ` (${UNIT_LABELS[batch.unit].short})` : ""}`}
          inputMode="decimal"
          value={quantity}
          onChange={(e) => setQuantity(e.target.value)}
          error={errors.quantity}
          required
        />
        <SelectField
          label="Motivo"
          value={reason}
          onChange={(e) => setReason(e.target.value as LossReason)}
          error={errors.reason}
          required
        >
          <option value="">Selecione</option>
          {LOSS_REASONS.map((r) => (
            <option key={r} value={r}>
              {LOSS_REASON_LABELS[r]}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Quando ocorreu"
          type="datetime-local"
          value={occurredAt}
          onChange={(e) => setOccurredAt(e.target.value)}
        />
        <TextAreaField
          label="Descrição (opcional)"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={1000}
        />
        <Button type="submit" block>
          Registrar perda
        </Button>
      </form>
    </>
  );
}
