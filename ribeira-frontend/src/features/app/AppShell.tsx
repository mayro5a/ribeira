"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { type ReactNode, useEffect } from "react";
import { useLiveQuery } from "dexie-react-hooks";
import { Badge } from "@/components/ui/Feedback";
import { useApp } from "./AppProvider";

const NAV = [
  { href: "/inicio/", label: "Início", icon: "⌂" },
  { href: "/estoque/", label: "Estoque", icon: "▤" },
  { href: "/doacoes/", label: "Doações", icon: "＋" },
  { href: "/distribuicoes/", label: "Distribuir", icon: "➜" },
  { href: "/mais/", label: "Mais", icon: "☰" },
];

/**
 * Exige sessão local desbloqueada. Sem sessão → login; sessão sem PIN ou
 * bloqueada → desbloqueio; sessão offline vencida sem conexão → login.
 */
export function Protected({ children }: { children: ReactNode }) {
  const { session, unlocked, offlineExpired: expired } = useApp();
  const router = useRouter();

  useEffect(() => {
    if (session === null || expired) {
      router.replace("/entrar/");
    } else if (session && !unlocked) {
      router.replace("/desbloquear/");
    }
  }, [session, unlocked, expired, router]);

  if (!session || !unlocked || expired) {
    return (
      <p className="p-6 text-center text-stone-600" role="status">
        Carregando…
      </p>
    );
  }
  return <AppShell>{children}</AppShell>;
}

function AppShell({ children }: { children: ReactNode }) {
  const { connection, db, communityId } = useApp();
  const pathname = usePathname();
  const community = useLiveQuery(
    () => (communityId ? db.communities.get(communityId) : undefined),
    [db, communityId],
  );
  return (
    <div className="mx-auto flex min-h-dvh max-w-3xl flex-col">
      <a href="#conteudo" className="sr-only focus:not-sr-only focus:p-3">
        Pular para o conteúdo
      </a>
      <header className="sticky top-0 z-10 flex items-center justify-between gap-3 border-b border-stone-200 bg-white/95 px-4 py-3 backdrop-blur">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-wide text-river-600">Ribeira</p>
          <p className="truncate font-semibold text-stone-900">
            {community?.name ?? "Selecione a comunidade"}
          </p>
        </div>
        <Link
          href="/sincronizacao/"
          className="shrink-0 rounded-full focus-visible:outline-3"
          aria-label={`Sincronização: ${connection.label}. ${connection.detail}`}
        >
          <Badge tone={connection.tone}>{connection.label}</Badge>
        </Link>
      </header>
      <main id="conteudo" className="flex-1 px-4 pb-28 pt-4">
        {children}
      </main>
      <nav
        aria-label="Navegação principal"
        className="fixed inset-x-0 bottom-0 z-10 border-t border-stone-200 bg-white"
      >
        <ul className="mx-auto grid max-w-3xl grid-cols-5">
          {NAV.map((item) => {
            const active = pathname?.startsWith(item.href.slice(0, -1)) ?? false;
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={`flex min-h-16 flex-col items-center justify-center text-xs font-semibold ${active ? "text-river-700" : "text-stone-600"}`}
                >
                  <span aria-hidden="true" className="text-xl leading-none">
                    {item.icon}
                  </span>
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
    </div>
  );
}
