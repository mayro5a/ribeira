"use client";

import Link from "next/link";
import { ButtonLink } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Feedback";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useCommunityOperations, useCommunityTable } from "@/features/data/hooks";
import { RecordList, type RecordRow } from "@/features/operations/RecordList";
import { unconfirmed } from "@/lib/domain/projection";
import { formatDateTime } from "@/lib/domain/labels";

export default function DistributionsPage() {
  return (
    <Protected>
      <Distributions />
    </Protected>
  );
}

function Distributions() {
  const { can } = useApp();
  const distributions = useCommunityTable("distributions");
  const operations = useCommunityOperations();

  const receipt = (id: string) => (
    <Link
      href={`/distribuicoes/comprovante/?id=${id}`}
      className="mt-2 inline-block min-h-12 py-3 font-semibold text-river-700 underline"
    >
      Ver comprovante
    </Link>
  );

  const rows: RecordRow[] = [
    ...distributions.map((d) => ({
      id: d.id,
      title: `${d.familyIds.length} família(s) · ${d.items.length} item(ns)`,
      subtitle: formatDateTime(d.distributedAt),
      at: d.distributedAt,
      status: "CONFIRMED" as const,
      action: receipt(d.id),
    })),
    ...unconfirmed(
      operations,
      "DISTRIBUTION_CREATE",
      new Set(distributions.map((d) => d.id)),
      (p) => p.distributionId,
    ).map((op) => ({
      id: op.operationId,
      title: `${op.payload.familyIds.length} família(s) · ${op.payload.items.length} item(ns)`,
      subtitle: formatDateTime(op.payload.distributedAt),
      at: op.payload.distributedAt,
      status: op.syncStatus,
      error: op.lastError?.message,
      action: receipt(op.payload.distributionId),
    })),
  ];

  return (
    <>
      <PageHeader
        title="Distribuições"
        action={
          can("DISTRIBUTION_WRITE") ? (
            <ButtonLink href="/distribuicoes/nova/">Nova distribuição</ButtonLink>
          ) : undefined
        }
      />
      <RecordList rows={rows} empty="Nenhuma distribuição registrada" />
    </>
  );
}
