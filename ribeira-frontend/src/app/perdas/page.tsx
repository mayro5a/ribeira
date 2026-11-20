"use client";

import { ButtonLink } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Feedback";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import {
  useCommunityOperations,
  useCommunityTable,
  useFoods,
  useProjectedBatches,
} from "@/features/data/hooks";
import { RecordList, type RecordRow } from "@/features/operations/RecordList";
import { unconfirmed } from "@/lib/domain/projection";
import { formatDateTime, LOSS_REASON_LABELS } from "@/lib/domain/labels";
import { formatQuantity, fromDecimalString } from "@/lib/domain/quantity";

export default function LossesPage() {
  return (
    <Protected>
      <Losses />
    </Protected>
  );
}

function Losses() {
  const { can } = useApp();
  const losses = useCommunityTable("losses");
  const operations = useCommunityOperations();
  const foods = useFoods();
  const batches = useProjectedBatches() ?? [];

  const rows: RecordRow[] = [
    ...losses.map((l) => ({
      id: l.id,
      title: `${foods.get(l.foodId)?.name ?? "Alimento"} — ${formatQuantity(fromDecimalString(l.quantity), l.unit)}`,
      subtitle: `${LOSS_REASON_LABELS[l.reason]} · ${formatDateTime(l.occurredAt)}`,
      at: l.occurredAt,
      status: "CONFIRMED" as const,
    })),
    ...unconfirmed(
      operations,
      "LOSS_RECORD",
      new Set(losses.map((l) => l.id)),
      (p) => p.lossId,
    ).map((op) => {
      const batch = batches.find((b) => b.id === op.payload.batchId);
      return {
        id: op.operationId,
        title: `${batch ? foods.get(batch.foodId)?.name : "Alimento"} — ${batch ? formatQuantity(fromDecimalString(op.payload.quantity), batch.unit) : op.payload.quantity}`,
        subtitle: `${LOSS_REASON_LABELS[op.payload.reason]} · ${formatDateTime(op.payload.occurredAt)}`,
        at: op.payload.occurredAt,
        status: op.syncStatus,
        error: op.lastError?.message,
      };
    }),
  ];

  return (
    <>
      <PageHeader
        title="Perdas"
        action={
          can("LOSS_WRITE") ? (
            <ButtonLink href="/perdas/nova/">Registrar perda</ButtonLink>
          ) : undefined
        }
      />
      <RecordList rows={rows} empty="Nenhuma perda registrada" />
    </>
  );
}
