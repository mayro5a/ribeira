"use client";

import { ButtonLink } from "@/components/ui/Button";
import { Notice, PageHeader } from "@/components/ui/Feedback";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useQueryParam } from "@/features/app/useQueryParam";
import {
  useCommunityOperations,
  useCommunityTable,
  useDonors,
  useFoods,
} from "@/features/data/hooks";
import { RecordList, type RecordRow } from "@/features/operations/RecordList";
import { unconfirmed } from "@/lib/domain/projection";
import { formatDateTime } from "@/lib/domain/labels";
import { formatQuantity, fromDecimalString } from "@/lib/domain/quantity";

export default function DonationsPage() {
  return (
    <Protected>
      <Donations />
    </Protected>
  );
}

function Donations() {
  const { can } = useApp();
  const donations = useCommunityTable("donations");
  const operations = useCommunityOperations();
  const foods = useFoods();
  const donors = useDonors();
  const saved = Boolean(useQueryParam("salvo"));

  const describe = (
    items: { foodId: string; quantity: string; unit: Parameters<typeof formatQuantity>[1] }[],
  ) =>
    items
      .map(
        (i) =>
          `${foods.get(i.foodId)?.name ?? "Alimento"} ${formatQuantity(fromDecimalString(i.quantity), i.unit)}`,
      )
      .join(", ");
  const origin = (donorId?: string | null, description?: string | null) =>
    donors.find((d) => d.id === donorId)?.name ?? description ?? "Origem não informada";

  const rows: RecordRow[] = [
    ...donations.map((d) => ({
      id: d.id,
      title: origin(d.donorId, d.originDescription),
      subtitle: `${formatDateTime(d.receivedAt)} · ${describe(d.items)}`,
      at: d.receivedAt,
      status: "CONFIRMED" as const,
    })),
    ...unconfirmed(
      operations,
      "DONATION_RECEIVE",
      new Set(donations.map((d) => d.id)),
      (p) => p.donationId,
    ).map((op) => ({
      id: op.operationId,
      title: origin(op.payload.donorId, op.payload.originDescription),
      subtitle: `${formatDateTime(op.payload.receivedAt)} · ${describe(op.payload.items)}`,
      at: op.payload.receivedAt,
      status: op.syncStatus,
      error: op.lastError?.message,
    })),
  ];

  return (
    <>
      <PageHeader
        title="Doações"
        action={
          can("DONATION_WRITE") ? (
            <ButtonLink href="/doacoes/nova/">Nova doação</ButtonLink>
          ) : undefined
        }
      />
      {saved ? (
        <Notice tone="success" title="Doação salva no aparelho">
          Ela será enviada automaticamente quando houver conexão.
        </Notice>
      ) : null}
      <RecordList rows={rows} empty="Nenhuma doação registrada" />
    </>
  );
}
