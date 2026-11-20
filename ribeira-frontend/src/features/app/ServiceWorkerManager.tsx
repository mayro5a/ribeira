"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/Button";

/**
 * Registra o Service Worker (somente em produção) e oferece a atualização
 * quando uma nova versão termina de instalar. Também pede armazenamento
 * persistente, reduzindo o risco de o navegador apagar o IndexedDB.
 */
export function ServiceWorkerManager() {
  const [waiting, setWaiting] = useState<ServiceWorker | null>(null);

  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) {
      return;
    }
    void navigator.storage?.persist?.();
    let reloading = false;
    const onControllerChange = () => {
      if (!reloading) {
        reloading = true;
        window.location.reload();
      }
    };
    navigator.serviceWorker.addEventListener("controllerchange", onControllerChange);
    navigator.serviceWorker
      .register("/sw.js", { scope: "/" })
      .then((registration) => {
        if (registration.waiting && navigator.serviceWorker.controller) {
          setWaiting(registration.waiting);
        }
        registration.addEventListener("updatefound", () => {
          const installing = registration.installing;
          installing?.addEventListener("statechange", () => {
            if (installing.state === "installed" && navigator.serviceWorker.controller) {
              setWaiting(installing);
            }
          });
        });
      })
      .catch((error: unknown) => console.error("Falha ao registrar o Service Worker", error));
    return () =>
      navigator.serviceWorker.removeEventListener("controllerchange", onControllerChange);
  }, []);

  if (!waiting) {
    return null;
  }
  return (
    <div
      role="status"
      className="fixed inset-x-0 top-0 z-50 flex items-center justify-between gap-3 bg-river-800 p-3 text-white"
    >
      <span>Nova versão do Ribeira disponível.</span>
      <Button variant="secondary" onClick={() => waiting.postMessage({ type: "SKIP_WAITING" })}>
        Atualizar agora
      </Button>
    </div>
  );
}
