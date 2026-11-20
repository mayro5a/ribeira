import type { Batch, ExpirationStatus } from "@/lib/api/schemas";
import type { PayloadOf, QueuedOperation, SyncStatus } from "@/lib/sync/operations";
import { isProjected } from "@/lib/sync/state-machine";
import { add, fromDecimalString, type Milli, type UnitOfMeasure, ZERO } from "./quantity";

/**
 * Projeção local (docs/synchronization.md §5): saldo exibido = snapshot
 * confirmado pelo servidor + efeitos das operações ainda não refletidas nele.
 * Tudo o que vem de operações locais é marcado como provisório.
 */

export interface ProjectedBatch {
  id: string;
  communityId: string;
  foodId: string;
  unit: UnitOfMeasure;
  storageLocationId?: string | null;
  donationId?: string | null;
  originDescription?: string | null;
  receivedAt: string;
  expirationStatus: ExpirationStatus;
  expirationDate?: string | null;
  status: Batch["status"];
  initial: Milli;
  available: Milli;
  /** Criado por operação local ainda não confirmada. */
  provisional: boolean;
  /** Soma dos efeitos locais pendentes sobre o saldo confirmado. */
  pendingDelta: Milli;
}

export function projectBatches(
  snapshots: Batch[],
  operations: QueuedOperation[],
  cursor: number,
): ProjectedBatch[] {
  const batches = new Map<string, ProjectedBatch>();
  for (const batch of snapshots) {
    batches.set(batch.id, fromSnapshot(batch));
  }
  const effective = operations
    .filter((op) => isProjected(op, cursor))
    .sort((a, b) => (a.localSeq ?? 0) - (b.localSeq ?? 0));

  for (const op of effective) {
    for (const created of createdBatches(op)) {
      if (!batches.has(created.id)) {
        batches.set(created.id, created);
      }
    }
  }
  for (const op of effective) {
    for (const [batchId, delta] of balanceEffects(op)) {
      const batch = batches.get(batchId);
      if (batch && !batch.provisional) {
        batch.available = add(batch.available, delta);
        batch.pendingDelta = add(batch.pendingDelta, delta);
      } else if (batch) {
        batch.available = add(batch.available, delta);
      }
    }
  }
  for (const batch of batches.values()) {
    if (batch.status !== "BLOCKED") {
      batch.status = batch.available === ZERO ? "DEPLETED" : "AVAILABLE";
    }
  }
  return [...batches.values()];
}

/** FEFO como sugestão: validade conhecida primeiro (mais próxima antes), depois as demais. */
export function fefoOrder(batches: ProjectedBatch[]): ProjectedBatch[] {
  return batches
    .filter((b) => b.status === "AVAILABLE" && b.available > ZERO)
    .sort((a, b) => {
      const aKnown = a.expirationStatus === "KNOWN" ? 0 : 1;
      const bKnown = b.expirationStatus === "KNOWN" ? 0 : 1;
      if (aKnown !== bKnown) return aKnown - bKnown;
      const byDate = (a.expirationDate ?? "").localeCompare(b.expirationDate ?? "");
      return byDate !== 0 ? byDate : a.receivedAt.localeCompare(b.receivedAt);
    });
}

export interface FoodStock {
  foodId: string;
  unit: UnitOfMeasure;
  available: Milli;
  batchCount: number;
  nextExpiration?: string;
  hasProvisional: boolean;
}

export function stockByFood(batches: ProjectedBatch[]): FoodStock[] {
  const byFood = new Map<string, FoodStock>();
  for (const batch of batches) {
    if (batch.status === "DEPLETED") continue;
    const current = byFood.get(batch.foodId) ?? {
      foodId: batch.foodId,
      unit: batch.unit,
      available: ZERO,
      batchCount: 0,
      hasProvisional: false,
    };
    current.available = add(current.available, batch.available);
    current.batchCount += 1;
    current.hasProvisional ||= batch.provisional || batch.pendingDelta !== ZERO;
    if (
      batch.expirationStatus === "KNOWN" &&
      batch.expirationDate &&
      (!current.nextExpiration || batch.expirationDate < current.nextExpiration)
    ) {
      current.nextExpiration = batch.expirationDate;
    }
    byFood.set(batch.foodId, current);
  }
  return [...byFood.values()];
}

/** Registro de histórico combinando confirmados e locais, com o estado de sincronização. */
export interface LocalRecord<T> {
  value: T;
  syncStatus: SyncStatus | "CONFIRMED";
  operationId?: string;
}

/** Operações de um tipo que ainda não aparecem nos snapshots confirmados. */
export function unconfirmed<T extends QueuedOperation["operationType"]>(
  operations: QueuedOperation[],
  type: T,
  confirmedIds: Set<string>,
  idOf: (payload: PayloadOf<T>) => string,
): QueuedOperation<T>[] {
  return operations.filter(
    (op): op is QueuedOperation<T> =>
      op.operationType === type &&
      op.syncStatus !== "SYNCED" &&
      !confirmedIds.has(idOf(op.payload as PayloadOf<T>)),
  );
}

function fromSnapshot(batch: Batch): ProjectedBatch {
  return {
    id: batch.id,
    communityId: batch.communityId,
    foodId: batch.foodId,
    unit: batch.unit,
    storageLocationId: batch.storageLocationId,
    donationId: batch.donationId,
    originDescription: batch.originDescription,
    receivedAt: batch.receivedAt,
    expirationStatus: batch.expirationStatus,
    expirationDate: batch.expirationDate,
    status: batch.status,
    initial: fromDecimalString(batch.initialQuantity),
    available: fromDecimalString(batch.availableQuantity),
    provisional: false,
    pendingDelta: ZERO,
  };
}

function createdBatches(op: QueuedOperation): ProjectedBatch[] {
  if (op.operationType === "DONATION_RECEIVE") {
    const payload = op.payload as PayloadOf<"DONATION_RECEIVE">;
    return payload.items.flatMap((item) =>
      item.batches.map((b) =>
        provisionalBatch(op.communityId, {
          id: b.batchId,
          foodId: item.foodId,
          unit: item.unit,
          quantity: b.quantity,
          receivedAt: payload.receivedAt,
          storageLocationId: b.storageLocationId,
          expirationStatus: b.expirationStatus,
          expirationDate: b.expirationDate,
          donationId: payload.donationId,
          originDescription: payload.originDescription,
        }),
      ),
    );
  }
  if (op.operationType === "BATCH_ENTRY") {
    const p = op.payload as PayloadOf<"BATCH_ENTRY">;
    return [
      provisionalBatch(op.communityId, {
        id: p.batchId,
        foodId: p.foodId,
        unit: p.unit,
        quantity: p.quantity,
        receivedAt: p.receivedAt,
        storageLocationId: p.storageLocationId,
        expirationStatus: p.expirationStatus,
        expirationDate: p.expirationDate,
        originDescription: p.originDescription,
      }),
    ];
  }
  return [];
}

function balanceEffects(op: QueuedOperation): [string, Milli][] {
  switch (op.operationType) {
    case "DISTRIBUTION_CREATE": {
      const p = op.payload as PayloadOf<"DISTRIBUTION_CREATE">;
      return p.items.map((i) => [i.batchId, negate(fromDecimalString(i.quantity))]);
    }
    case "LOSS_RECORD": {
      const p = op.payload as PayloadOf<"LOSS_RECORD">;
      return [[p.batchId, negate(fromDecimalString(p.quantity))]];
    }
    case "STOCK_ADJUSTMENT": {
      const p = op.payload as PayloadOf<"STOCK_ADJUSTMENT">;
      return [[p.batchId, fromDecimalString(p.quantityDelta)]];
    }
    default:
      return [];
  }
}

function provisionalBatch(
  communityId: string,
  b: {
    id: string;
    foodId: string;
    unit: UnitOfMeasure;
    quantity: string;
    receivedAt: string;
    storageLocationId?: string;
    expirationStatus: ExpirationStatus;
    expirationDate?: string;
    donationId?: string;
    originDescription?: string;
  },
): ProjectedBatch {
  const quantity = fromDecimalString(b.quantity);
  return {
    id: b.id,
    communityId,
    foodId: b.foodId,
    unit: b.unit,
    storageLocationId: b.storageLocationId,
    donationId: b.donationId,
    originDescription: b.originDescription,
    receivedAt: b.receivedAt,
    expirationStatus: b.expirationStatus,
    expirationDate: b.expirationDate,
    status: "AVAILABLE",
    initial: quantity,
    available: quantity,
    provisional: true,
    pendingDelta: ZERO,
  };
}

function negate(value: Milli): Milli {
  return -value as Milli;
}
