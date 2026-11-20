import type { Table } from "dexie";
import { SYNC_ENTITY_SCHEMAS, type SyncEntityType } from "@/lib/api/schemas";
import { META_KEYS, type RibeiraDatabase, setMeta } from "@/lib/db/database";
import type { PullResponse } from "./contracts";

function tableFor(db: RibeiraDatabase, type: SyncEntityType): Table {
  const tables: Record<SyncEntityType, Table> = {
    COMMUNITY: db.communities,
    FOOD: db.foods,
    DONOR: db.donors,
    STORAGE_LOCATION: db.storageLocations,
    FAMILY: db.families,
    DONATION: db.donations,
    BATCH: db.batches,
    MOVEMENT: db.movements,
    DISTRIBUTION: db.distributions,
    LOSS: db.losses,
    READING: db.readings,
    ALERT: db.alerts,
  };
  return tables[type];
}

/**
 * Grava os snapshots da página e o novo cursor na mesma transação: se o app
 * fechar no meio, nem os dados nem o cursor avançam, e a página é pedida de novo.
 * Dados fora do contrato interrompem a sincronização (sem avançar o cursor).
 */
export async function applyPullPage(db: RibeiraDatabase, page: PullResponse): Promise<number> {
  const rows = page.changes.map((change) => ({
    type: change.entityType,
    data: SYNC_ENTITY_SCHEMAS[change.entityType].parse(change.data),
  }));
  await db.transaction("rw", [db.meta, ...db.snapshotTables], async () => {
    for (const row of rows) {
      await tableFor(db, row.type).put(row.data);
    }
    if (page.nextCursor) {
      await setMeta(db, META_KEYS.syncCursor, Number(page.nextCursor));
    }
  });
  return rows.length;
}
