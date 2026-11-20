import type { ExpirationStatus, FoodCategory, LossReason } from "@/lib/api/schemas";
import type { SyncStatus } from "@/lib/sync/operations";

/** Textos da interface em português do Brasil para os vocabulários do domínio. */

export const CATEGORY_LABELS: Record<FoodCategory, string> = {
  GRAINS_AND_CEREALS: "Grãos e cereais",
  BEANS_AND_LEGUMES: "Feijões e leguminosas",
  FLOURS: "Farinhas",
  CANNED_GOODS: "Enlatados",
  DAIRY: "Laticínios",
  MEAT_AND_FISH: "Carnes e peixes",
  FRUITS_AND_VEGETABLES: "Frutas e verduras",
  OILS_AND_FATS: "Óleos e gorduras",
  SUGARS_AND_SWEETS: "Açúcares e doces",
  BEVERAGES: "Bebidas",
  INFANT_FOOD: "Alimentação infantil",
  OTHER: "Outros",
};

export const LOSS_REASON_LABELS: Record<LossReason, string> = {
  HUMIDITY: "Umidade",
  HEAT_OR_SUN_EXPOSURE: "Calor ou exposição solar",
  DAMAGED_PACKAGING: "Embalagem danificada",
  EXPIRATION: "Vencimento",
  PESTS: "Pragas",
  TRANSPORT: "Transporte",
  SUSPECTED_CONTAMINATION: "Contaminação suspeita",
  OTHER: "Outro",
  UNIDENTIFIED: "Não identificado",
};

export const EXPIRATION_LABELS: Record<ExpirationStatus, string> = {
  KNOWN: "Com data de validade",
  NOT_APPLICABLE: "Sem validade (indefinida)",
  UNKNOWN: "Validade não informada",
};

export const SYNC_STATUS_LABELS: Record<SyncStatus | "CONFIRMED", string> = {
  PENDING: "Salvo no aparelho",
  SENDING: "Enviando",
  SYNCED: "Confirmado",
  CONFIRMED: "Confirmado",
  FAILED: "Não aceito",
  CONFLICT: "Em revisão",
};

export const SYNC_STATUS_TONES = {
  PENDING: "warning",
  SENDING: "info",
  SYNCED: "success",
  CONFIRMED: "success",
  FAILED: "danger",
  CONFLICT: "danger",
} as const;

export const OPERATION_LABELS: Record<string, string> = {
  DONATION_RECEIVE: "Doação recebida",
  BATCH_ENTRY: "Entrada de estoque",
  STOCK_ADJUSTMENT: "Ajuste de estoque",
  DISTRIBUTION_CREATE: "Distribuição",
  LOSS_RECORD: "Perda",
  READING_RECORD: "Leitura/inspeção",
};

const dateTime = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" });
const dateOnly = new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeZone: "UTC" });

/** Horário guardado em UTC exibido no fuso do aparelho. */
export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

/** Datas sem horário (validade) não mudam com o fuso. */
export function formatDate(isoDate: string): string {
  return dateOnly.format(new Date(`${isoDate}T00:00:00Z`));
}

/** Valor para <input type="datetime-local"> no horário local. */
export function toLocalInputValue(date: Date): string {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

export function fromLocalInputValue(value: string): string {
  return new Date(value).toISOString();
}

export function daysUntil(isoDate: string, today = new Date()): number {
  const target = Date.parse(`${isoDate}T00:00:00Z`);
  const start = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate());
  return Math.round((target - start) / 86_400_000);
}
