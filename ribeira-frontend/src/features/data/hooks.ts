"use client";

import { useLiveQuery } from "dexie-react-hooks";
import { useMemo } from "react";
import type { Table } from "dexie";
import type {
  Alert,
  Distribution,
  Donation,
  Food,
  Loss,
  Movement,
  Reading,
} from "@/lib/api/schemas";
import { META_KEYS } from "@/lib/db/database";
import { projectBatches, type ProjectedBatch } from "@/lib/domain/projection";
import type { QueuedOperation } from "@/lib/sync/operations";
import { useApp } from "@/features/app/AppProvider";

/**
 * Leituras reativas do IndexedDB. Componentes de tela usam estes hooks e não
 * acessam a fila nem as regras de projeção diretamente.
 */

export function useCursor(): number {
  const { db } = useApp();
  return (
    useLiveQuery(
      async () => ((await db.meta.get(META_KEYS.syncCursor))?.value as number | undefined) ?? 0,
      [db],
    ) ?? 0
  );
}

export function useCommunityOperations(): QueuedOperation[] {
  const { db, communityId } = useApp();
  return (
    useLiveQuery(
      () =>
        communityId
          ? db.operations.where("communityId").equals(communityId).sortBy("localSeq")
          : [],
      [db, communityId],
    ) ?? []
  );
}

export function useProjectedBatches(): ProjectedBatch[] | undefined {
  const { db, communityId } = useApp();
  const cursor = useCursor();
  const operations = useCommunityOperations();
  const snapshots = useLiveQuery(
    () => (communityId ? db.batches.where("communityId").equals(communityId).toArray() : []),
    [db, communityId],
  );
  return useMemo(
    () => (snapshots ? projectBatches(snapshots, operations, cursor) : undefined),
    [snapshots, operations, cursor],
  );
}

export function useFoods(): Map<string, Food> {
  const { db } = useApp();
  const foods = useLiveQuery(() => db.foods.orderBy("name").toArray(), [db]);
  return useMemo(() => new Map((foods ?? []).map((f) => [f.id, f])), [foods]);
}

export function useDonors() {
  const { db } = useApp();
  return useLiveQuery(() => db.donors.orderBy("name").toArray(), [db]) ?? [];
}

export function useStorageLocations() {
  const { db, communityId } = useApp();
  return (
    useLiveQuery(
      () =>
        communityId ? db.storageLocations.where("communityId").equals(communityId).toArray() : [],
      [db, communityId],
    ) ?? []
  ).filter((l) => l.active);
}

export function useFamilies() {
  const { db, communityId } = useApp();
  return (
    useLiveQuery(
      () =>
        communityId ? db.families.where("communityId").equals(communityId).sortBy("code") : [],
      [db, communityId],
    ) ?? []
  );
}

export function useCommunities() {
  const { db } = useApp();
  return useLiveQuery(() => db.communities.toArray(), [db]) ?? [];
}

type CommunityTables = {
  donations: Donation;
  distributions: Distribution;
  losses: Loss;
  readings: Reading;
  alerts: Alert;
  movements: Movement;
};

/** Snapshots confirmados de uma tabela, restritos à comunidade selecionada. */
export function useCommunityTable<K extends keyof CommunityTables>(table: K): CommunityTables[K][] {
  const { db, communityId } = useApp();
  return (
    useLiveQuery(async () => {
      if (!communityId) return [];
      const source = db[table] as unknown as Table<CommunityTables[K], string>;
      return source.where("communityId").equals(communityId).toArray();
    }, [db, communityId, table]) ?? []
  );
}
