"use client";

import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Badge, Card, Notice, PageHeader } from "@/components/ui/Feedback";
import { CheckboxField, SelectField, TextAreaField, TextField } from "@/components/ui/Field";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import {
  useCommunityOperations,
  useCommunityTable,
  useStorageLocations,
} from "@/features/data/hooks";
import { recordOperation } from "@/features/operations/record";
import { RecordList, type RecordRow } from "@/features/operations/RecordList";
import { describeError } from "@/lib/api/client";
import { unconfirmed } from "@/lib/domain/projection";
import { formatDateTime } from "@/lib/domain/labels";

export default function MonitoringPage() {
  return (
    <Protected>
      <Monitoring />
    </Protected>
  );
}

function Monitoring() {
  const { can } = useApp();
  return (
    <>
      <PageHeader
        title="Monitoramento"
        description="Temperatura, umidade e inspeções dos locais de armazenamento."
      />
      {can("MONITORING_WRITE") ? <ReadingForm /> : null}
      <Alerts />
      <Readings />
    </>
  );
}

function ReadingForm() {
  const { db, session, communityId, requestSync } = useApp();
  const locations = useStorageLocations();
  const [locationId, setLocationId] = useState("");
  const [temperature, setTemperature] = useState("");
  const [humidity, setHumidity] = useState("");
  const [inspection, setInspection] = useState(false);
  const [notes, setNotes] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    const normalize = (v: string) => v.trim().replace(",", ".") || undefined;
    if (!locationId) return setError("Selecione o local.");
    if (!normalize(temperature) && !normalize(humidity) && !inspection) {
      return setError("Informe uma medição ou marque a inspeção.");
    }
    if (!session || !communityId) return;
    try {
      await recordOperation(db, session, communityId, "READING_RECORD", {
        readingId: crypto.randomUUID(),
        storageLocationId: locationId,
        temperature: normalize(temperature),
        humidity: normalize(humidity),
        inspection,
        notes: notes.trim() || undefined,
        recordedAt: new Date().toISOString(),
      });
    } catch {
      return setError("Valores inválidos: use até duas casas decimais; umidade entre 0 e 100.");
    }
    setError(null);
    setTemperature("");
    setHumidity("");
    setNotes("");
    setInspection(false);
    setSaved(true);
    void requestSync();
  }

  return (
    <Card>
      <h2 className="mb-2 text-lg font-bold">Nova leitura</h2>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {saved ? <Notice tone="success">Leitura salva no aparelho.</Notice> : null}
      <form onSubmit={submit} noValidate>
        <SelectField
          label="Local"
          value={locationId}
          onChange={(e) => setLocationId(e.target.value)}
          required
        >
          <option value="">Selecione</option>
          {locations.map((l) => (
            <option key={l.id} value={l.id}>
              {l.name}
            </option>
          ))}
        </SelectField>
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Temperatura (°C)"
            inputMode="decimal"
            value={temperature}
            onChange={(e) => setTemperature(e.target.value)}
          />
          <TextField
            label="Umidade (%)"
            inputMode="decimal"
            value={humidity}
            onChange={(e) => setHumidity(e.target.value)}
          />
        </div>
        <CheckboxField
          label="Inspeção realizada"
          hint="Marque quando alguém verificou o local e os alimentos."
          checked={inspection}
          onChange={(e) => setInspection(e.target.checked)}
        />
        <TextAreaField
          label="Observações"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          maxLength={1000}
        />
        <Button type="submit" block>
          Salvar leitura
        </Button>
      </form>
    </Card>
  );
}

function Alerts() {
  const { auth, can, requestSync } = useApp();
  const alerts = useCommunityTable("alerts").filter((a) => a.status === "OPEN");
  const [notes, setNotes] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  async function resolve(id: string) {
    setError(null);
    try {
      await auth.api.post(`/api/v1/alerts/${id}/resolve`, {
        notes: notes[id],
        inspectionPerformed: true,
      });
      await requestSync();
    } catch (err) {
      setError(describeError(err));
    }
  }

  return (
    <Card>
      <h2 className="mb-2 text-lg font-bold">Alertas abertos ({alerts.length})</h2>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      {alerts.length === 0 ? <p className="text-stone-600">Nenhum alerta aberto.</p> : null}
      <ul className="space-y-4">
        {alerts.map((a) => (
          <li key={a.id}>
            <Badge
              tone={
                a.severity === "CRITICAL" ? "danger" : a.severity === "WARNING" ? "warning" : "info"
              }
            >
              {a.severity === "CRITICAL"
                ? "Crítico"
                : a.severity === "WARNING"
                  ? "Atenção"
                  : "Informativo"}
            </Badge>
            <p className="mt-1">{a.message}</p>
            <p className="text-xs text-stone-500">{formatDateTime(a.createdAt)}</p>
            {can("ALERT_RESOLVE") ? (
              <div className="mt-2">
                <TextField
                  label="O que foi verificado"
                  value={notes[a.id] ?? ""}
                  onChange={(e) => setNotes((n) => ({ ...n, [a.id]: e.target.value }))}
                  hint="A resolução exige conexão com o servidor."
                />
                <Button
                  variant="secondary"
                  disabled={!notes[a.id]?.trim()}
                  onClick={() => resolve(a.id)}
                >
                  Registrar inspeção e resolver
                </Button>
              </div>
            ) : null}
          </li>
        ))}
      </ul>
    </Card>
  );
}

function Readings() {
  const readings = useCommunityTable("readings");
  const operations = useCommunityOperations();
  const locations = useStorageLocations();
  const name = (id: string) => locations.find((l) => l.id === id)?.name ?? "Local";
  const summary = (t?: string | null, h?: string | null, inspection?: boolean) =>
    [t ? `${t} °C` : null, h ? `${h}%` : null, inspection ? "inspeção" : null]
      .filter(Boolean)
      .join(" · ");

  const rows: RecordRow[] = [
    ...readings.map((r) => ({
      id: r.id,
      title: name(r.storageLocationId),
      subtitle: `${summary(r.temperature, r.humidity, r.inspection)} · ${formatDateTime(r.recordedAt)}`,
      at: r.recordedAt,
      status: "CONFIRMED" as const,
    })),
    ...unconfirmed(
      operations,
      "READING_RECORD",
      new Set(readings.map((r) => r.id)),
      (p) => p.readingId,
    ).map((op) => ({
      id: op.operationId,
      title: name(op.payload.storageLocationId),
      subtitle: `${summary(op.payload.temperature, op.payload.humidity, op.payload.inspection)} · ${formatDateTime(op.payload.recordedAt)}`,
      at: op.payload.recordedAt,
      status: op.syncStatus,
      error: op.lastError?.message,
    })),
  ];
  return (
    <>
      <h2 className="mb-2 mt-6 text-lg font-bold">Histórico de leituras</h2>
      <RecordList rows={rows} empty="Nenhuma leitura registrada" />
    </>
  );
}
