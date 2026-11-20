"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useApp } from "@/features/app/AppProvider";

export default function HomePage() {
  const { session, unlocked } = useApp();
  const router = useRouter();

  useEffect(() => {
    if (session === undefined) return;
    if (session === null) router.replace("/entrar/");
    else router.replace(unlocked ? "/inicio/" : "/desbloquear/");
  }, [session, unlocked, router]);

  return (
    <p className="p-6 text-center text-stone-600" role="status">
      Abrindo o Ribeira…
    </p>
  );
}
