"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Feedback";
import { TextField } from "@/components/ui/Field";
import { useApp } from "@/features/app/AppProvider";
import { describeError, NetworkError } from "@/lib/api/client";

export default function LoginPage() {
  const { auth, setSession, unlock } = useApp();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const session = await auth.login(email, password);
      setSession(session);
      if (session.pin) {
        unlock();
        router.replace("/inicio/");
      } else {
        router.replace("/desbloquear/");
      }
    } catch (err) {
      setError(
        err instanceof NetworkError
          ? "Sem conexão. A primeira entrada em um aparelho precisa de internet."
          : describeError(err),
      );
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6">
      <p className="text-sm font-semibold uppercase tracking-wide text-river-600">Ribeira</p>
      <h1 className="mb-1 text-3xl font-bold text-river-800">Entrar</h1>
      <p className="mb-6 text-stone-600">
        Gestão de doações de alimentos. Depois da primeira entrada, o aparelho funciona sem
        internet.
      </p>
      {error ? (
        <Notice tone="danger" title="Não foi possível entrar">
          {error}
        </Notice>
      ) : null}
      <form onSubmit={submit} noValidate>
        <TextField
          label="E-mail"
          type="email"
          autoComplete="username"
          inputMode="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          label="Senha"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <Button type="submit" block loading={loading} disabled={!email || !password}>
          Entrar
        </Button>
      </form>
      <p className="mt-6 text-sm text-stone-600">
        Este aparelho será registrado na sua organização. Ela pode revogar o acesso quando ele se
        conectar.
      </p>
    </main>
  );
}
