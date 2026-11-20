"use client";

import { useSyncExternalStore } from "react";

function subscribe(onChange: () => void) {
  window.addEventListener("popstate", onChange);
  return () => window.removeEventListener("popstate", onChange);
}

/**
 * Lê um parâmetro da URL no cliente. Em export estático as rotas com parâmetro
 * usam query string (ADR-0006) e o HTML pré-gerado não os conhece: durante a
 * geração estática o valor é `undefined`.
 */
export function useQueryParam(name: string): string | null | undefined {
  return useSyncExternalStore(
    subscribe,
    () => new URLSearchParams(window.location.search).get(name),
    () => undefined,
  );
}
