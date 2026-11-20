import type { ProjectedBatch } from "@/lib/domain/projection";
import {
  add,
  parseQuantity,
  type Milli,
  UNIT_LABELS,
  validateQuantityForUnit,
  ZERO,
  formatQuantity,
} from "@/lib/domain/quantity";

export interface DraftItem {
  key: string;
  batchId: string;
  quantity: string;
}

/**
 * Validação local da distribuição contra o saldo projetado. Evita erros
 * óbvios no campo; o servidor revalida tudo ao sincronizar (RN02).
 */
export function validateDistribution(
  items: DraftItem[],
  familyIds: string[],
  batches: ProjectedBatch[],
): { form: string | null; items: Record<string, string> } {
  const errors: Record<string, string> = {};
  if (familyIds.length === 0) {
    return { form: "Selecione ao menos uma família.", items: errors };
  }
  if (items.length === 0) {
    return { form: "Adicione ao menos um alimento.", items: errors };
  }
  const requested = new Map<string, Milli>();
  for (const item of items) {
    const batch = batches.find((b) => b.id === item.batchId);
    if (!batch) {
      errors[item.key] = "Selecione o lote.";
      continue;
    }
    const invalid = validateQuantityForUnit(item.quantity, batch.unit);
    if (invalid) {
      errors[item.key] = invalid;
      continue;
    }
    const total = add(requested.get(batch.id) ?? ZERO, parseQuantity(item.quantity));
    requested.set(batch.id, total);
    if (batch.status === "BLOCKED") {
      errors[item.key] = "Lote bloqueado para inspeção.";
    } else if (total > batch.available) {
      errors[item.key] =
        `Saldo insuficiente: disponível ${formatQuantity(batch.available, batch.unit)}.`;
    }
  }
  return {
    form: Object.keys(errors).length ? "Corrija os itens destacados." : null,
    items: errors,
  };
}

export function unitShort(batch: ProjectedBatch): string {
  return UNIT_LABELS[batch.unit].short;
}
