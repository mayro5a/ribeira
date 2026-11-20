"use client";

import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Card, Notice } from "@/components/ui/Feedback";
import { SelectField, TextAreaField, TextField } from "@/components/ui/Field";
import type { Donor, ExpirationStatus, Food, StorageLocation } from "@/lib/api/schemas";
import { EXPIRATION_LABELS, fromLocalInputValue, toLocalInputValue } from "@/lib/domain/labels";
import {
  parseQuantity,
  toDecimalString,
  UNIT_LABELS,
  validateQuantityForUnit,
} from "@/lib/domain/quantity";
import type { PayloadOf } from "@/lib/sync/operations";

interface ItemDraft {
  key: string;
  foodId: string;
  quantity: string;
  storageLocationId: string;
  expirationStatus: ExpirationStatus;
  expirationDate: string;
}

function emptyItem(): ItemDraft {
  return {
    key: crypto.randomUUID(),
    foodId: "",
    quantity: "",
    storageLocationId: "",
    expirationStatus: "KNOWN",
    expirationDate: "",
  };
}

/**
 * Formulário de recebimento. Cada item vira um lote rastreável (o modelo
 * aceita vários lotes por item; a divisão em vários lotes fica para versão futura).
 */
export function DonationForm({
  foods,
  donors,
  locations,
  onSubmit,
}: {
  foods: Food[];
  donors: Donor[];
  locations: StorageLocation[];
  onSubmit: (payload: PayloadOf<"DONATION_RECEIVE">) => Promise<void>;
}) {
  const [donorId, setDonorId] = useState("");
  const [origin, setOrigin] = useState("");
  const [receivedAt, setReceivedAt] = useState(() => toLocalInputValue(new Date()));
  const [notes, setNotes] = useState("");
  const [items, setItems] = useState<ItemDraft[]>([emptyItem()]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const activeFoods = foods.filter((f) => f.active);

  function update(key: string, patch: Partial<ItemDraft>) {
    setItems((current) => current.map((item) => (item.key === key ? { ...item, ...patch } : item)));
  }

  function validate(): Record<string, string> {
    const found: Record<string, string> = {};
    if (!donorId && !origin.trim())
      found.origin = "Informe o doador ou descreva a origem da doação.";
    for (const item of items) {
      const food = foods.find((f) => f.id === item.foodId);
      if (!food) {
        found[`${item.key}-food`] = "Selecione o alimento.";
        continue;
      }
      const invalid = validateQuantityForUnit(item.quantity, food.unit);
      if (invalid) found[`${item.key}-quantity`] = invalid;
      if (item.expirationStatus === "KNOWN" && !item.expirationDate) {
        found[`${item.key}-expiration`] = "Informe a data de validade ou escolha outra opção.";
      }
    }
    return found;
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const found = validate();
    setErrors(found);
    if (Object.keys(found).length > 0) {
      setFormError("Revise os campos destacados.");
      return;
    }
    setFormError(null);
    setSaving(true);
    try {
      await onSubmit({
        donationId: crypto.randomUUID(),
        donorId: donorId || undefined,
        originDescription: origin.trim() || undefined,
        receivedAt: fromLocalInputValue(receivedAt),
        notes: notes.trim() || undefined,
        items: items.map((item) => {
          const food = foods.find((f) => f.id === item.foodId)!;
          const quantity = toDecimalString(parseQuantity(item.quantity));
          return {
            itemId: crypto.randomUUID(),
            foodId: food.id,
            quantity,
            unit: food.unit,
            batches: [
              {
                batchId: crypto.randomUUID(),
                quantity,
                storageLocationId: item.storageLocationId || undefined,
                expirationStatus: item.expirationStatus,
                expirationDate: item.expirationStatus === "KNOWN" ? item.expirationDate : undefined,
              },
            ],
          };
        }),
      });
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Não foi possível salvar.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label="Registrar doação">
      {formError ? <Notice tone="danger">{formError}</Notice> : null}
      <SelectField
        label="Doador"
        value={donorId}
        onChange={(e) => setDonorId(e.target.value)}
        hint="Opcional se a origem for descrita abaixo."
      >
        <option value="">Sem doador cadastrado</option>
        {donors
          .filter((d) => d.active)
          .map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
      </SelectField>
      <TextField
        label="Origem da doação"
        value={origin}
        onChange={(e) => setOrigin(e.target.value)}
        error={errors.origin}
        maxLength={200}
        placeholder="Ex.: campanha da paróquia"
      />
      <TextField
        label="Recebida em"
        type="datetime-local"
        value={receivedAt}
        onChange={(e) => setReceivedAt(e.target.value)}
        required
      />

      {items.map((item, index) => {
        const food = foods.find((f) => f.id === item.foodId);
        return (
          <Card key={item.key}>
            <h2 className="mb-2 font-bold">Item {index + 1}</h2>
            <SelectField
              label="Alimento"
              value={item.foodId}
              onChange={(e) => update(item.key, { foodId: e.target.value })}
              error={errors[`${item.key}-food`]}
              required
            >
              <option value="">Selecione</option>
              {activeFoods.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </SelectField>
            <TextField
              label={`Quantidade${food ? ` (${UNIT_LABELS[food.unit].short})` : ""}`}
              inputMode="decimal"
              value={item.quantity}
              onChange={(e) => update(item.key, { quantity: e.target.value })}
              error={errors[`${item.key}-quantity`]}
              required
            />
            <SelectField
              label="Local de armazenamento"
              value={item.storageLocationId}
              onChange={(e) => update(item.key, { storageLocationId: e.target.value })}
            >
              <option value="">Não informado</option>
              {locations.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.name}
                </option>
              ))}
            </SelectField>
            <SelectField
              label="Validade"
              value={item.expirationStatus}
              onChange={(e) =>
                update(item.key, { expirationStatus: e.target.value as ExpirationStatus })
              }
            >
              {(Object.keys(EXPIRATION_LABELS) as ExpirationStatus[]).map((status) => (
                <option key={status} value={status}>
                  {EXPIRATION_LABELS[status]}
                </option>
              ))}
            </SelectField>
            {item.expirationStatus === "KNOWN" ? (
              <TextField
                label="Data de validade"
                type="date"
                value={item.expirationDate}
                onChange={(e) => update(item.key, { expirationDate: e.target.value })}
                error={errors[`${item.key}-expiration`]}
                required
              />
            ) : null}
            {items.length > 1 ? (
              <Button
                variant="ghost"
                onClick={() => setItems((current) => current.filter((i) => i.key !== item.key))}
              >
                Remover item
              </Button>
            ) : null}
          </Card>
        );
      })}
      <Button
        variant="secondary"
        block
        className="mb-4"
        onClick={() => setItems((current) => [...current, emptyItem()])}
      >
        Adicionar outro alimento
      </Button>
      <TextAreaField
        label="Observações"
        value={notes}
        onChange={(e) => setNotes(e.target.value)}
        maxLength={1000}
      />
      <Button type="submit" block loading={saving}>
        Salvar doação
      </Button>
    </form>
  );
}
