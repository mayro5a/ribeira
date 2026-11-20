"use client";

import { useLiveQuery } from "dexie-react-hooks";
import { Button } from "@/components/ui/Button";
import { Badge, Card, Notice, PageHeader } from "@/components/ui/Feedback";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useQueryParam } from "@/features/app/useQueryParam";
import { useFamilies, useFoods, useProjectedBatches } from "@/features/data/hooks";
import { formatDateTime, SYNC_STATUS_LABELS, SYNC_STATUS_TONES } from "@/lib/domain/labels";
import { formatQuantity, fromDecimalString } from "@/lib/domain/quantity";
import type { PayloadOf } from "@/lib/sync/operations";

export default function ReceiptPage() {
  return (
    <Protected>
      <Receipt />
    </Protected>
  );
}

/** Resumo imprimível da distribuição, a partir do registro confirmado ou local. */
function Receipt() {
  const { db, session } = useApp();
  const id = useQueryParam("id");
  const foods = useFoods();
  const families = useFamilies();
  const batches = useProjectedBatches() ?? [];
  const confirmed = useLiveQuery(() => (id ? db.distributions.get(id) : undefined), [db, id]);
  const local = useLiveQuery(
    async () =>
      id
        ? (
            await db.operations
              .where("syncStatus")
              .anyOf("PENDING", "SENDING", "SYNCED", "FAILED", "CONFLICT")
              .toArray()
          ).find(
            (op) =>
              op.operationType === "DISTRIBUTION_CREATE" &&
              (op.payload as PayloadOf<"DISTRIBUTION_CREATE">).distributionId === id,
          )
        : undefined,
    [db, id],
  );
  const community = useLiveQuery(
    () =>
      (confirmed?.communityId ?? local?.communityId)
        ? db.communities.get((confirmed?.communityId ?? local?.communityId)!)
        : undefined,
    [db, confirmed, local],
  );

  if (id === undefined) return null;
  const payload = local?.payload as PayloadOf<"DISTRIBUTION_CREATE"> | undefined;
  const status = confirmed ? "CONFIRMED" : local?.syncStatus;
  if (!confirmed && !payload) {
    return <Notice tone="warning">Distribuição não encontrada neste aparelho.</Notice>;
  }
  const familyIds = confirmed?.familyIds ?? payload!.familyIds;
  const date = confirmed?.distributedAt ?? payload!.distributedAt;
  const lines = confirmed
    ? confirmed.items.map((i) => ({
        foodId: i.foodId,
        quantity: formatQuantity(fromDecimalString(i.quantity), i.unit),
      }))
    : payload!.items.map((i) => {
        const batch = batches.find((b) => b.id === i.batchId);
        return {
          foodId: batch?.foodId ?? "",
          quantity: batch ? formatQuantity(fromDecimalString(i.quantity), batch.unit) : i.quantity,
        };
      });

  return (
    <>
      <PageHeader
        title="Comprovante de distribuição"
        action={
          <Button variant="secondary" onClick={() => window.print()}>
            Imprimir
          </Button>
        }
      />
      <Card>
        <p className="text-sm text-stone-600">Comunidade</p>
        <p className="mb-2 font-semibold">{community?.name ?? "—"}</p>
        <p className="text-sm text-stone-600">Data</p>
        <p className="mb-2 font-semibold">{formatDateTime(date)}</p>
        <p className="text-sm text-stone-600">Famílias (código)</p>
        <p className="mb-2 font-semibold">
          {familyIds.map((f) => families.find((x) => x.id === f)?.code ?? "—").join(", ")}
        </p>
        <p className="text-sm text-stone-600">Alimentos</p>
        <ul className="mb-2 list-disc pl-5">
          {lines.map((line, index) => (
            <li key={index}>
              {foods.get(line.foodId)?.name ?? "Alimento"}: {line.quantity}
            </li>
          ))}
        </ul>
        <p className="text-sm text-stone-600">Registrado por</p>
        <p className="mb-2 font-semibold">{session?.displayName}</p>
        {status ? (
          <Badge tone={SYNC_STATUS_TONES[status]}>{SYNC_STATUS_LABELS[status]}</Badge>
        ) : null}
        {status !== "CONFIRMED" ? (
          <p className="mt-2 text-sm text-stone-600">
            Comprovante provisório: a distribuição ainda não foi confirmada pelo servidor.
          </p>
        ) : null}
        <p className="mt-2 text-xs text-stone-500">Identificador: {id}</p>
      </Card>
    </>
  );
}
