import Dexie, { type EntityTable, type Table } from "dexie";
import type {
  Alert,
  Batch,
  Community,
  Distribution,
  Donation,
  Donor,
  Family,
  Food,
  Loss,
  Movement,
  Reading,
  StorageLocation,
} from "@/lib/api/schemas";
import type { QueuedOperation } from "@/lib/sync/operations";
import type { LocalSession } from "@/lib/auth/session";

export const DATABASE_NAME = "ribeira";

/** Pares chave/valor de controle: deviceId, cursor de sincronização etc. */
export interface MetaEntry {
  key: string;
  value: unknown;
}

export const META_KEYS = {
  deviceId: "deviceId",
  syncCursor: "syncCursor",
  lastSyncAt: "lastSyncAt",
  lastSyncError: "lastSyncError",
  selectedCommunityId: "selectedCommunityId",
} as const;

/**
 * Banco local. As tabelas de entidades guardam **snapshots confirmados pelo
 * servidor** (recebidos no pull). O que ainda não foi confirmado vive apenas
 * na fila `operations` e é combinado na leitura pela projeção local.
 *
 * Mudanças de esquema: sempre adicionar uma nova `version(n)` com `upgrade`
 * quando houver transformação de dados; nunca alterar uma versão publicada.
 */
export class RibeiraDatabase extends Dexie {
  meta!: Table<MetaEntry, string>;
  session!: Table<LocalSession, string>;
  operations!: EntityTable<QueuedOperation, "localSeq">;
  communities!: EntityTable<Community, "id">;
  foods!: EntityTable<Food, "id">;
  donors!: EntityTable<Donor, "id">;
  storageLocations!: EntityTable<StorageLocation, "id">;
  families!: EntityTable<Family, "id">;
  donations!: EntityTable<Donation, "id">;
  batches!: EntityTable<Batch, "id">;
  movements!: EntityTable<Movement, "id">;
  distributions!: EntityTable<Distribution, "id">;
  losses!: EntityTable<Loss, "id">;
  readings!: EntityTable<Reading, "id">;
  alerts!: EntityTable<Alert, "id">;

  constructor(name: string = DATABASE_NAME) {
    super(name);
    this.version(1).stores({
      meta: "key",
      session: "key",
      operations: "++localSeq, &operationId, syncStatus, communityId, [communityId+syncStatus]",
      communities: "id",
      foods: "id, name",
      donors: "id, name",
      storageLocations: "id, communityId",
      families: "id, communityId, [communityId+code]",
      donations: "id, communityId, receivedAt",
      batches: "id, communityId, foodId, [communityId+foodId]",
      movements: "id, communityId, batchId, occurredAt",
      distributions: "id, communityId, distributedAt",
      losses: "id, communityId, batchId, occurredAt",
      readings: "id, communityId, storageLocationId, recordedAt",
      alerts: "id, communityId, status",
    });
  }

  /** Tabelas que guardam snapshots do servidor (apagadas no logout/revogação). */
  get snapshotTables() {
    return [
      this.communities,
      this.foods,
      this.donors,
      this.storageLocations,
      this.families,
      this.donations,
      this.batches,
      this.movements,
      this.distributions,
      this.losses,
      this.readings,
      this.alerts,
    ];
  }
}

let instance: RibeiraDatabase | null = null;

export function getDatabase(): RibeiraDatabase {
  instance ??= new RibeiraDatabase();
  return instance;
}

export async function getMeta<T>(db: RibeiraDatabase, key: string): Promise<T | undefined> {
  const entry = await db.meta.get(key);
  return entry?.value as T | undefined;
}

export async function setMeta(db: RibeiraDatabase, key: string, value: unknown): Promise<void> {
  await db.meta.put({ key, value });
}

/** Identidade estável do aparelho, criada uma única vez. */
export async function getOrCreateDeviceId(db: RibeiraDatabase): Promise<string> {
  return db.transaction("rw", db.meta, async () => {
    const existing = await getMeta<string>(db, META_KEYS.deviceId);
    if (existing) {
      return existing;
    }
    const deviceId = crypto.randomUUID();
    await setMeta(db, META_KEYS.deviceId, deviceId);
    return deviceId;
  });
}

export async function getSyncCursor(db: RibeiraDatabase): Promise<number> {
  return (await getMeta<number>(db, META_KEYS.syncCursor)) ?? 0;
}
