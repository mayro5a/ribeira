"use client";

import Link from "next/link";
import { ButtonLink } from "@/components/ui/Button";
import { Badge, Card, EmptyState, Notice, PageHeader, Stat } from "@/components/ui/Feedback";
import { SelectField } from "@/components/ui/Field";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import {
  useCommunities,
  useCommunityOperations,
  useCommunityTable,
  useFoods,
  useProjectedBatches,
} from "@/features/data/hooks";
import { stockByFood } from "@/lib/domain/projection";
import {
  daysUntil,
  formatDate,
  formatDateTime,
  OPERATION_LABELS,
  SYNC_STATUS_LABELS,
  SYNC_STATUS_TONES,
} from "@/lib/domain/labels";
import { formatQuantity } from "@/lib/domain/quantity";

const ATTENTION_DAYS = 30;

export default function DashboardPage() {
  return (
    <Protected>
      <CommunityDashboard />
    </Protected>
  );
}

function CommunityDashboard() {
  const { session, communityId, selectCommunity, connection, can } = useApp();
  const communities = useCommunities();
  const batches = useProjectedBatches() ?? [];
  const foods = useFoods();
  const operations = useCommunityOperations();
  const alerts = useCommunityTable("alerts").filter((a) => a.status === "OPEN");
  const distributions = useCommunityTable("distributions");
  const accessible = communities.filter(
    (c) => session?.communityPermissions[c.id] || session?.organizationPermissions.length,
  );

  const stock = stockByFood(batches);
  const attention = batches
    .filter((b) => b.status === "AVAILABLE" && b.expirationStatus === "KNOWN" && b.expirationDate)
    .filter((b) => daysUntil(b.expirationDate!) <= ATTENTION_DAYS)
    .sort((a, b) => a.expirationDate!.localeCompare(b.expirationDate!));
  const waiting = operations.filter((op) => op.syncStatus !== "SYNCED");

  return (
    <>
      <PageHeader title={`Olá, ${session?.displayName ?? ""}`} description={connection.detail} />
      {accessible.length > 1 ? (
        <SelectField
          label="Comunidade"
          value={communityId ?? ""}
          onChange={(e) => selectCommunity(e.target.value)}
        >
          {accessible.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </SelectField>
      ) : null}
      {communities.length === 0 ? (
        <Notice tone="warning" title="Ainda sem dados neste aparelho">
          Conecte-se à internet para baixar os dados da comunidade.
        </Notice>
      ) : null}

      <div className="mb-4 grid grid-cols-2 gap-3">
        {can("DONATION_WRITE") ? (
          <ButtonLink href="/doacoes/nova/">Registrar doação</ButtonLink>
        ) : null}
        {can("DISTRIBUTION_WRITE") ? (
          <ButtonLink href="/distribuicoes/nova/">Distribuir</ButtonLink>
        ) : null}
        {can("LOSS_WRITE") ? (
          <ButtonLink href="/perdas/nova/" variant="secondary">
            Registrar perda
          </ButtonLink>
        ) : null}
        {can("MONITORING_WRITE") ? (
          <ButtonLink href="/monitoramento/" variant="secondary">
            Monitoramento
          </ButtonLink>
        ) : null}
      </div>

      <div className="mb-4 grid grid-cols-2 gap-3 sm:grid-cols-4">
        <Stat label="Alimentos em estoque" value={stock.length} />
        <Stat
          label="Lotes com atenção"
          value={attention.length}
          hint={`vencem em até ${ATTENTION_DAYS} dias`}
        />
        <Stat label="Alertas abertos" value={alerts.length} />
        <Stat label="Aguardando envio" value={waiting.length} />
      </div>

      <Card>
        <h2 className="mb-2 text-lg font-bold">Lotes que precisam de atenção</h2>
        {attention.length === 0 ? (
          <p className="text-stone-600">Nenhum lote com validade próxima.</p>
        ) : (
          <ul className="divide-y divide-stone-100">
            {attention.slice(0, 8).map((b) => {
              const days = daysUntil(b.expirationDate!);
              return (
                <li key={b.id} className="flex items-center justify-between gap-2 py-2">
                  <span>
                    {foods.get(b.foodId)?.name ?? "Alimento"} —{" "}
                    {formatQuantity(b.available, b.unit)}
                  </span>
                  <Badge tone={days < 0 ? "danger" : "warning"}>
                    {days < 0
                      ? `venceu em ${formatDate(b.expirationDate!)}`
                      : `vence ${formatDate(b.expirationDate!)}`}
                  </Badge>
                </li>
              );
            })}
          </ul>
        )}
      </Card>

      {alerts.length > 0 ? (
        <Card>
          <h2 className="mb-2 text-lg font-bold">Alertas</h2>
          <ul className="space-y-2">
            {alerts.slice(0, 5).map((a) => (
              <li key={a.id} className="text-sm">
                <Badge tone={a.severity === "CRITICAL" ? "danger" : "warning"}>
                  {a.severity === "CRITICAL" ? "Crítico" : "Atenção"}
                </Badge>{" "}
                {a.message}
              </li>
            ))}
          </ul>
          <Link
            className="mt-2 inline-block font-semibold text-river-700 underline"
            href="/monitoramento/"
          >
            Ver alertas
          </Link>
        </Card>
      ) : null}

      <Card>
        <h2 className="mb-2 text-lg font-bold">Registros recentes neste aparelho</h2>
        {operations.length === 0 ? (
          <EmptyState title="Nada registrado ainda" />
        ) : (
          <ul className="divide-y divide-stone-100">
            {[...operations]
              .reverse()
              .slice(0, 6)
              .map((op) => (
                <li key={op.operationId} className="flex items-center justify-between gap-2 py-2">
                  <span>
                    {OPERATION_LABELS[op.operationType]}
                    <span className="block text-xs text-stone-500">
                      {formatDateTime(op.createdAt)}
                    </span>
                  </span>
                  <Badge tone={SYNC_STATUS_TONES[op.syncStatus]}>
                    {SYNC_STATUS_LABELS[op.syncStatus]}
                  </Badge>
                </li>
              ))}
          </ul>
        )}
        <p className="mt-2 text-sm text-stone-600">
          Distribuições confirmadas na comunidade: {distributions.length}
        </p>
      </Card>
    </>
  );
}
