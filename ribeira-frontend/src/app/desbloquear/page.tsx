"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";
import { Notice } from "@/components/ui/Feedback";
import { TextField } from "@/components/ui/Field";
import { useApp } from "@/features/app/AppProvider";
import { useQueryParam } from "@/features/app/useQueryParam";
import { hashPin, validatePin, verifyPin } from "@/lib/auth/pin";

const MAX_FAILED_ATTEMPTS = 5;

/** Aceita somente caminhos internos, evitando redirecionamento para outro site. */
function safeReturnPath(path: string | null | undefined): string {
  return path && path.startsWith("/") && !path.startsWith("//") ? path : "/inicio/";
}

/** Desbloqueio local por PIN; no primeiro acesso, cria o PIN. */
export default function UnlockPage() {
  const { session, db, unlock, setSession, logout } = useApp();
  const router = useRouter();
  const [pin, setPin] = useState("");
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [failures, setFailures] = useState(0);
  const [busy, setBusy] = useState(false);
  const creating = session ? !session.pin : false;
  const returnTo = useQueryParam("voltar");

  useEffect(() => {
    if (session === null) router.replace("/entrar/");
  }, [session, router]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!session) return;
    setError(null);
    const invalid = validatePin(pin);
    if (invalid) {
      setError(invalid);
      return;
    }
    setBusy(true);
    try {
      if (creating) {
        if (pin !== confirmation) {
          setError("Os PINs não conferem.");
          return;
        }
        const updated = { ...session, pin: await hashPin(pin) };
        await db.session.put(updated);
        setSession(updated);
      } else if (!session.pin || !(await verifyPin(pin, session.pin))) {
        const count = failures + 1;
        setFailures(count);
        if (count >= MAX_FAILED_ATTEMPTS) {
          await logout();
          router.replace("/entrar/");
          return;
        }
        setError(`PIN incorreto. Restam ${MAX_FAILED_ATTEMPTS - count} tentativa(s).`);
        return;
      }
      unlock();
      router.replace(safeReturnPath(returnTo));
    } finally {
      setBusy(false);
    }
  }

  if (!session) return null;

  return (
    <main className="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6">
      <h1 className="mb-1 text-3xl font-bold text-river-800">
        {creating ? "Crie um PIN" : "Desbloquear"}
      </h1>
      <p className="mb-6 text-stone-600">
        {creating
          ? "O PIN protege o aplicativo neste aparelho quando ele for reaberto, inclusive sem internet."
          : `Olá, ${session.displayName}. Digite seu PIN para continuar.`}
      </p>
      {error ? <Notice tone="danger">{error}</Notice> : null}
      <form onSubmit={submit} noValidate>
        <TextField
          label="PIN (4 a 8 dígitos)"
          type="password"
          inputMode="numeric"
          autoComplete={creating ? "new-password" : "current-password"}
          value={pin}
          onChange={(e) => setPin(e.target.value.replace(/\D/g, ""))}
          required
        />
        {creating ? (
          <TextField
            label="Confirme o PIN"
            type="password"
            inputMode="numeric"
            autoComplete="new-password"
            value={confirmation}
            onChange={(e) => setConfirmation(e.target.value.replace(/\D/g, ""))}
            required
          />
        ) : null}
        <Button type="submit" block loading={busy}>
          {creating ? "Salvar PIN" : "Desbloquear"}
        </Button>
      </form>
      <Button
        variant="ghost"
        className="mt-4"
        onClick={async () => {
          await logout();
          router.replace("/entrar/");
        }}
      >
        Entrar com outra conta
      </Button>
    </main>
  );
}
