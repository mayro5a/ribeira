"use client";

import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import { z } from "zod";
import { Button } from "@/components/ui/Button";
import { Card, Notice, PageHeader } from "@/components/ui/Feedback";
import { SelectField, TextField } from "@/components/ui/Field";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";
import { useCommunities, useFoods } from "@/features/data/hooks";
import { describeError } from "@/lib/api/client";
import { FOOD_CATEGORIES } from "@/lib/api/schemas";
import { CATEGORY_LABELS } from "@/lib/domain/labels";
import { UNIT_LABELS, UNITS } from "@/lib/domain/quantity";

export default function AdministrationPage() {
  return (
    <Protected>
      <Administration />
    </Protected>
  );
}

/**
 * Cadastros administrativos. São feitos online (docs/synchronization.md §2.1);
 * o resultado chega aos aparelhos pela sincronização.
 */
function Administration() {
  const { can } = useApp();
  return (
    <>
      <PageHeader
        title="Cadastros"
        description="Exige conexão. As alterações chegam aos aparelhos na próxima sincronização."
      />
      <Notice tone="info">
        Não cadastre nomes, documentos ou endereços de pessoas. Famílias são identificadas por
        código.
      </Notice>
      {can("COMMUNITY_MANAGE", null) ? <CommunityForm /> : null}
      {can("FOOD_MANAGE", null) ? <FoodForm /> : null}
      <DonorForm />
      <LocationForm />
      <FamilyForm />
      {can("USER_MANAGE", null) ? <UserForm /> : null}
    </>
  );
}

function useSubmit() {
  const { requestSync } = useApp();
  const [message, setMessage] = useState<{ tone: "success" | "danger"; text: string } | null>(null);
  const run = async (action: () => Promise<unknown>, success: string) => {
    try {
      await action();
      setMessage({ tone: "success", text: success });
      void requestSync();
      return true;
    } catch (err) {
      setMessage({ tone: "danger", text: describeError(err) });
      return false;
    }
  };
  const feedback = message ? <Notice tone={message.tone}>{message.text}</Notice> : null;
  return { run, feedback };
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card>
      <h2 className="mb-3 text-lg font-bold">{title}</h2>
      {children}
    </Card>
  );
}

function CommunityForm() {
  const { auth } = useApp();
  const { run, feedback } = useSubmit();
  const [name, setName] = useState("");
  const [municipality, setMunicipality] = useState("");
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (
      await run(
        () =>
          auth.api.post("/api/v1/communities", { name, municipality: municipality || undefined }),
        "Comunidade cadastrada.",
      )
    ) {
      setName("");
      setMunicipality("");
    }
  };
  return (
    <Section title="Nova comunidade">
      {feedback}
      <form onSubmit={submit}>
        <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextField
          label="Município (opcional)"
          value={municipality}
          onChange={(e) => setMunicipality(e.target.value)}
        />
        <Button type="submit" disabled={!name.trim()}>
          Cadastrar
        </Button>
      </form>
    </Section>
  );
}

function FoodForm() {
  const { auth } = useApp();
  const foods = useFoods();
  const { run, feedback } = useSubmit();
  const [name, setName] = useState("");
  const [category, setCategory] = useState<string>(FOOD_CATEGORIES[0]);
  const [unit, setUnit] = useState<string>(UNITS[0]);
  const [warningDays, setWarningDays] = useState("7");
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (
      await run(
        () =>
          auth.api.post("/api/v1/foods", {
            name,
            category,
            unit,
            expirationWarningDays: Number(warningDays),
          }),
        "Alimento cadastrado.",
      )
    ) {
      setName("");
    }
  };
  return (
    <Section title={`Alimentos (${foods.size})`}>
      {feedback}
      <form onSubmit={submit}>
        <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} required />
        <SelectField
          label="Categoria"
          value={category}
          onChange={(e) => setCategory(e.target.value)}
        >
          {FOOD_CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {CATEGORY_LABELS[c]}
            </option>
          ))}
        </SelectField>
        <SelectField
          label="Unidade de medida"
          hint="Não pode ser alterada depois."
          value={unit}
          onChange={(e) => setUnit(e.target.value)}
        >
          {UNITS.map((u) => (
            <option key={u} value={u}>
              {UNIT_LABELS[u].singular}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Avisar validade com quantos dias de antecedência"
          type="number"
          min={0}
          max={365}
          value={warningDays}
          onChange={(e) => setWarningDays(e.target.value)}
        />
        <Button type="submit" disabled={!name.trim()}>
          Cadastrar
        </Button>
      </form>
    </Section>
  );
}

function DonorForm() {
  const { auth, can } = useApp();
  const { run, feedback } = useSubmit();
  const [name, setName] = useState("");
  const [type, setType] = useState("ORGANIZATION");
  if (!can("DONOR_MANAGE", null) && !can("DONOR_MANAGE")) return null;
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (await run(() => auth.api.post("/api/v1/donors", { name, type }), "Doador cadastrado."))
      setName("");
  };
  return (
    <Section title="Novo doador">
      {feedback}
      <form onSubmit={submit}>
        <SelectField label="Tipo" value={type} onChange={(e) => setType(e.target.value)}>
          <option value="ORGANIZATION">Organização</option>
          <option value="INDIVIDUAL">Pessoa</option>
        </SelectField>
        <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} required />
        <Button type="submit" disabled={!name.trim()}>
          Cadastrar
        </Button>
      </form>
    </Section>
  );
}

function LocationForm() {
  const { auth, can, communityId } = useApp();
  const { run, feedback } = useSubmit();
  const [name, setName] = useState("");
  if (!can("STOCK_WRITE") || !communityId) return null;
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (
      await run(
        () => auth.api.post(`/api/v1/communities/${communityId}/storage-locations`, { name }),
        "Local cadastrado.",
      )
    )
      setName("");
  };
  return (
    <Section title="Novo local de armazenamento (comunidade atual)">
      {feedback}
      <form onSubmit={submit}>
        <TextField label="Nome" value={name} onChange={(e) => setName(e.target.value)} required />
        <Button type="submit" disabled={!name.trim()}>
          Cadastrar
        </Button>
      </form>
    </Section>
  );
}

function FamilyForm() {
  const { auth, can, communityId } = useApp();
  const { run, feedback } = useSubmit();
  const [code, setCode] = useState("");
  const [members, setMembers] = useState("1");
  if (!can("FAMILY_WRITE") || !communityId) return null;
  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (
      await run(
        () =>
          auth.api.post(`/api/v1/communities/${communityId}/families`, {
            code,
            memberCount: Number(members),
          }),
        "Família cadastrada.",
      )
    ) {
      setCode("");
    }
  };
  return (
    <Section title="Nova família (comunidade atual)">
      {feedback}
      <form onSubmit={submit}>
        <TextField
          label="Código comunitário"
          hint="Use o código combinado com a comunidade, nunca o nome."
          value={code}
          onChange={(e) => setCode(e.target.value)}
          required
        />
        <TextField
          label="Quantidade de integrantes"
          type="number"
          min={1}
          max={50}
          value={members}
          onChange={(e) => setMembers(e.target.value)}
        />
        <Button type="submit" disabled={!code.trim()}>
          Cadastrar
        </Button>
      </form>
    </Section>
  );
}

const rolesSchema = z.array(z.object({ id: z.uuid(), name: z.string(), system: z.boolean() }));

function UserForm() {
  const { auth } = useApp();
  const communities = useCommunities();
  const { run, feedback } = useSubmit();
  const [roles, setRoles] = useState<z.infer<typeof rolesSchema>>([]);
  const [form, setForm] = useState({
    email: "",
    displayName: "",
    password: "",
    communityId: "",
    roleId: "",
  });

  useEffect(() => {
    auth.api
      .get("/api/v1/roles", { schema: rolesSchema })
      .then(setRoles)
      .catch(() => setRoles([]));
  }, [auth]);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const ok = await run(
      () =>
        auth.api.post("/api/v1/users", {
          email: form.email,
          displayName: form.displayName,
          password: form.password,
          communityRoles:
            form.communityId && form.roleId ? { [form.communityId]: form.roleId } : {},
        }),
      "Usuário cadastrado.",
    );
    if (ok) setForm({ email: "", displayName: "", password: "", communityId: "", roleId: "" });
  };
  const set = (field: keyof typeof form) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  return (
    <Section title="Novo usuário">
      {feedback}
      <form onSubmit={submit}>
        <TextField
          label="E-mail"
          type="email"
          value={form.email}
          onChange={set("email")}
          required
        />
        <TextField
          label="Nome de exibição"
          value={form.displayName}
          onChange={set("displayName")}
          required
        />
        <TextField
          label="Senha inicial"
          type="password"
          hint="Mínimo de 10 caracteres."
          value={form.password}
          onChange={set("password")}
          required
        />
        <SelectField label="Comunidade" value={form.communityId} onChange={set("communityId")}>
          <option value="">Nenhuma</option>
          {communities.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </SelectField>
        <SelectField label="Papel na comunidade" value={form.roleId} onChange={set("roleId")}>
          <option value="">Selecione</option>
          {roles.map((r) => (
            <option key={r.id} value={r.id}>
              {r.name}
            </option>
          ))}
        </SelectField>
        <Button
          type="submit"
          disabled={!form.email || !form.displayName || form.password.length < 10}
        >
          Cadastrar
        </Button>
      </form>
    </Section>
  );
}
