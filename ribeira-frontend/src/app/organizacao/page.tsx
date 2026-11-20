"use client";

import { useEffect, useState } from "react";
import { z } from "zod";
import { Card, Notice, PageHeader, Stat } from "@/components/ui/Feedback";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { describeError } from "@/lib/api/client";
import { LOSS_REASON_LABELS } from "@/lib/domain/labels";
import { formatQuantity, fromDecimalString } from "@/lib/domain/quantity";
import { lossReasonSchema, unitSchema } from "@/lib/api/schemas";

const total = z.object({
  foodId: z.uuid(),
  foodName: z.string().nullish(),
  unit: unitSchema,
  quantity: z.string(),
  occurrences: z.number(),
});
const dashboardSchema = z.object({
  communities: z.number(),
  donations: z.number(),
  distributions: z.number(),
  familiesServed: z.number(),
  openAlerts: z.number(),
  stock: z.array(total),
  losses: z.object({ byFood: z.array(total), byReason: z.record(lossReasonSchema, z.number()) }),
});
type Dashboard = z.infer<typeof dashboardSchema>;

export default function OrganizationPage() {
  return (
    <Protected>
      <OrganizationDashboard />
    </Protected>
  );
}

/** Indicadores consolidados (somente agregados, sem dados pessoais). Exige conexão. */
function OrganizationDashboard() {
  const { auth } = useApp();
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    auth.api
      .get("/api/v1/dashboard/organization", { schema: dashboardSchema })
      .then(setData)
      .catch((err: unknown) => setError(describeError(err)));
  }, [auth]);

  return (
    <>
      <PageHeader
        title="Painel da organização"
        description="Últimos 30 dias, comunidades em que você pode ver relatórios."
      />
      {error ? (
        <Notice tone="warning" title="Painel indisponível">
          {error} Este painel precisa de conexão.
        </Notice>
      ) : null}
      {!data && !error ? <p role="status">Carregando…</p> : null}
      {data ? (
        <>
          <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-3">
            <Stat label="Comunidades" value={data.communities} />
            <Stat label="Doações recebidas" value={data.donations} />
            <Stat label="Distribuições" value={data.distributions} />
            <Stat label="Famílias atendidas" value={data.familiesServed} />
            <Stat label="Alertas abertos" value={data.openAlerts} />
          </div>
          <Card>
            <h2 className="mb-2 text-lg font-bold">Estoque consolidado</h2>
            <ul className="divide-y divide-stone-100">
              {data.stock.map((s) => (
                <li key={`${s.foodId}-${s.unit}`} className="flex justify-between py-2">
                  <span>{s.foodName ?? "Alimento"}</span>
                  <span className="font-semibold">
                    {formatQuantity(fromDecimalString(s.quantity), s.unit)}
                  </span>
                </li>
              ))}
            </ul>
          </Card>
          <Card>
            <h2 className="mb-2 text-lg font-bold">Perdas por motivo</h2>
            {Object.keys(data.losses.byReason).length === 0 ? (
              <p className="text-stone-600">Nenhuma perda no período.</p>
            ) : null}
            <ul>
              {Object.entries(data.losses.byReason).map(([reason, count]) => (
                <li key={reason} className="flex justify-between py-1">
                  <span>{LOSS_REASON_LABELS[reason as keyof typeof LOSS_REASON_LABELS]}</span>
                  <span className="font-semibold">{count}</span>
                </li>
              ))}
            </ul>
          </Card>
        </>
      ) : null}
    </>
  );
}
