import { z } from "zod";
import { expirationStatusSchema, lossReasonSchema, unitSchema } from "@/lib/api/schemas";

/**
 * Contrato das operações offline (docs/synchronization.md §2). Cada payload é
 * validado antes de entrar na fila: uma operação malformada nunca deve ser
 * persistida para envio.
 */

export const SCHEMA_VERSION = 1;

const uuid = z.uuid();
const positiveDecimal = z
  .string()
  .regex(/^\d+\.\d{3}$/, "decimal com 3 casas")
  .refine((value) => Number(value) > 0, "deve ser maior que zero");
const signedDecimal = z
  .string()
  .regex(/^-?\d+\.\d{3}$/, "decimal com 3 casas")
  .refine((value) => Number(value) !== 0, "não pode ser zero");
const isoDateTime = z.iso.datetime({ offset: true });

const batchInput = z
  .object({
    batchId: uuid,
    quantity: positiveDecimal,
    storageLocationId: uuid.optional(),
    expirationStatus: expirationStatusSchema,
    expirationDate: z.iso.date().optional(),
    notes: z.string().max(500).optional(),
  })
  .refine((b) => (b.expirationStatus === "KNOWN") === (b.expirationDate !== undefined), {
    message: "Data de validade é obrigatória somente quando a validade é conhecida.",
  });

export const donationReceivePayload = z
  .object({
    donationId: uuid,
    donorId: uuid.optional(),
    originDescription: z.string().trim().min(1).max(200).optional(),
    receivedAt: isoDateTime,
    notes: z.string().max(1000).optional(),
    items: z
      .array(
        z.object({
          itemId: uuid,
          foodId: uuid,
          quantity: positiveDecimal,
          unit: unitSchema,
          notes: z.string().max(500).optional(),
          batches: z.array(batchInput).min(1),
        }),
      )
      .min(1),
  })
  .refine((d) => d.donorId !== undefined || d.originDescription !== undefined, {
    message: "Informe o doador ou a origem da doação.",
  });

export const batchEntryPayload = z
  .object({
    batchId: uuid,
    foodId: uuid,
    quantity: positiveDecimal,
    unit: unitSchema,
    originDescription: z.string().trim().min(1).max(200),
    receivedAt: isoDateTime,
    storageLocationId: uuid.optional(),
    expirationStatus: expirationStatusSchema,
    expirationDate: z.iso.date().optional(),
    notes: z.string().max(500).optional(),
  })
  .refine((b) => (b.expirationStatus === "KNOWN") === (b.expirationDate !== undefined), {
    message: "Data de validade é obrigatória somente quando a validade é conhecida.",
  });

export const stockAdjustmentPayload = z.object({
  movementId: uuid,
  batchId: uuid,
  quantityDelta: signedDecimal,
  justification: z.string().trim().min(5).max(500),
});

export const distributionCreatePayload = z.object({
  distributionId: uuid,
  distributedAt: isoDateTime,
  notes: z.string().max(1000).optional(),
  familyIds: z.array(uuid).min(1),
  items: z.array(z.object({ itemId: uuid, batchId: uuid, quantity: positiveDecimal })).min(1),
});

export const lossRecordPayload = z.object({
  lossId: uuid,
  batchId: uuid,
  quantity: positiveDecimal,
  occurredAt: isoDateTime,
  reason: lossReasonSchema,
  description: z.string().max(1000).optional(),
});

export const readingRecordPayload = z
  .object({
    readingId: uuid,
    storageLocationId: uuid,
    temperature: z
      .string()
      .regex(/^-?\d{1,3}(\.\d{1,2})?$/)
      .optional(),
    humidity: z
      .string()
      .regex(/^\d{1,3}(\.\d{1,2})?$/)
      .optional(),
    inspection: z.boolean(),
    notes: z.string().max(1000).optional(),
    recordedAt: isoDateTime,
  })
  .refine((r) => r.inspection || r.temperature !== undefined || r.humidity !== undefined, {
    message: "Informe uma medição ou marque a inspeção.",
  });

export const OPERATION_DEFINITIONS = {
  DONATION_RECEIVE: { entityType: "DONATION", payload: donationReceivePayload },
  BATCH_ENTRY: { entityType: "BATCH", payload: batchEntryPayload },
  STOCK_ADJUSTMENT: { entityType: "MOVEMENT", payload: stockAdjustmentPayload },
  DISTRIBUTION_CREATE: { entityType: "DISTRIBUTION", payload: distributionCreatePayload },
  LOSS_RECORD: { entityType: "LOSS", payload: lossRecordPayload },
  READING_RECORD: { entityType: "READING", payload: readingRecordPayload },
} as const;

export type OperationType = keyof typeof OPERATION_DEFINITIONS;
export type PayloadOf<T extends OperationType> = z.infer<
  (typeof OPERATION_DEFINITIONS)[T]["payload"]
>;

export const SYNC_STATUSES = ["PENDING", "SENDING", "SYNCED", "FAILED", "CONFLICT"] as const;
export type SyncStatus = (typeof SYNC_STATUSES)[number];

/** Por que uma operação está em FAILED. Só TRANSPORT admite nova tentativa manual. */
export type FailureKind = "REJECTED" | "DISCARDED" | "TRANSPORT";

export interface OperationError {
  code: string;
  message: string;
}

/** Operação na fila local (IndexedDB). */
export interface QueuedOperation<T extends OperationType = OperationType> {
  /** Ordem de criação no aparelho; define a ordem de envio. */
  localSeq?: number;
  operationId: string;
  deviceId: string;
  userId: string;
  communityId: string;
  entityType: (typeof OPERATION_DEFINITIONS)[T]["entityType"];
  operationType: T;
  /** Horário local, apenas para auditoria. */
  createdAt: string;
  schemaVersion: number;
  payload: PayloadOf<T>;
  syncStatus: SyncStatus;
  attempts: number;
  /** Epoch ms; o motor só envia quando `nextAttemptAt <= agora`. */
  nextAttemptAt: number;
  lastError?: OperationError;
  failureKind?: FailureKind;
  conflictId?: string;
  /** Posição do feed que contém os efeitos da operação (após confirmação). */
  serverSeq?: number;
  updatedAt: string;
}

export interface NewOperation<T extends OperationType> {
  operationType: T;
  communityId: string;
  deviceId: string;
  userId: string;
  payload: PayloadOf<T>;
}

/** Valida o payload e monta o envelope em estado PENDING. */
export function buildOperation<T extends OperationType>(
  input: NewOperation<T>,
  now: Date = new Date(),
  newId: () => string = () => crypto.randomUUID(),
): QueuedOperation<T> {
  const definition = OPERATION_DEFINITIONS[input.operationType];
  const payload = definition.payload.parse(input.payload) as PayloadOf<T>;
  const timestamp = now.toISOString();
  return {
    operationId: newId(),
    deviceId: input.deviceId,
    userId: input.userId,
    communityId: input.communityId,
    entityType: definition.entityType,
    operationType: input.operationType,
    createdAt: timestamp,
    schemaVersion: SCHEMA_VERSION,
    payload,
    syncStatus: "PENDING",
    attempts: 0,
    nextAttemptAt: now.getTime(),
    updatedAt: timestamp,
  };
}

/** Envelope enviado ao servidor (sem campos de controle locais). */
export function toWireOperation(op: QueuedOperation) {
  return {
    operationId: op.operationId,
    deviceId: op.deviceId,
    communityId: op.communityId,
    entityType: op.entityType,
    operationType: op.operationType,
    createdAt: op.createdAt,
    schemaVersion: op.schemaVersion,
    payload: op.payload,
  };
}
