"use client";

import type { ReactNode } from "react";
import { Badge, EmptyState } from "@/components/ui/Feedback";
import { SYNC_STATUS_LABELS, SYNC_STATUS_TONES } from "@/lib/domain/labels";
import type { SyncStatus } from "@/lib/sync/operations";

export interface RecordRow {
  id: string;
  title: ReactNode;
  subtitle?: ReactNode;
  at: string;
  status: SyncStatus | "CONFIRMED";
  error?: string;
  action?: ReactNode;
}

/** Histórico com estado explícito: nada aparece como confirmado sem resposta do servidor. */
export function RecordList({ rows, empty }: { rows: RecordRow[]; empty: string }) {
  if (rows.length === 0) {
    return <EmptyState title={empty} />;
  }
  return (
    <ul className="divide-y divide-stone-100 rounded-2xl border border-stone-200 bg-white">
      {[...rows]
        .sort((a, b) => b.at.localeCompare(a.at))
        .map((row) => (
          <li key={row.id} className="p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="font-semibold">{row.title}</p>
                {row.subtitle ? <p className="text-sm text-stone-600">{row.subtitle}</p> : null}
              </div>
              <Badge tone={SYNC_STATUS_TONES[row.status]}>{SYNC_STATUS_LABELS[row.status]}</Badge>
            </div>
            {row.error ? <p className="mt-1 text-sm text-clay-600">{row.error}</p> : null}
            {row.action}
          </li>
        ))}
    </ul>
  );
}
