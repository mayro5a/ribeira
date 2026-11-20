"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/Button";
import { PageHeader } from "@/components/ui/Feedback";
import { Protected } from "@/features/app/AppShell";
import { useApp } from "@/features/app/AppProvider";

export default function MorePage() {
  return (
    <Protected>
      <More />
    </Protected>
  );
}

function More() {
  const { session, lock, logout } = useApp();
  const router = useRouter();
  const isManager = (session?.organizationPermissions.length ?? 0) > 0;
  const links = [
    { href: "/perdas/", label: "Perdas", show: true },
    { href: "/monitoramento/", label: "Monitoramento e alertas", show: true },
    { href: "/sincronizacao/", label: "Sincronização", show: true },
    { href: "/organizacao/", label: "Painel da organização (online)", show: true },
    { href: "/administracao/", label: "Cadastros (online)", show: isManager },
  ];
  return (
    <>
      <PageHeader
        title="Mais opções"
        description={session ? `${session.displayName} · ${session.email}` : undefined}
      />
      <ul className="mb-6 divide-y divide-stone-100 rounded-2xl border border-stone-200 bg-white">
        {links
          .filter((l) => l.show)
          .map((l) => (
            <li key={l.href}>
              <Link
                href={l.href}
                className="flex min-h-14 items-center px-4 font-semibold text-river-800"
              >
                {l.label}
              </Link>
            </li>
          ))}
      </ul>
      <div className="grid gap-3">
        <Button
          variant="secondary"
          onClick={() => {
            lock();
            router.replace("/desbloquear/");
          }}
        >
          Bloquear o aplicativo
        </Button>
        <Button
          variant="ghost"
          onClick={async () => {
            await logout();
            router.replace("/entrar/");
          }}
        >
          Sair desta conta
        </Button>
      </div>
    </>
  );
}
