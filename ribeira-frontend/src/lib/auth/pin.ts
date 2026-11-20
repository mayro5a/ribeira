import type { PinHash } from "./session";

/**
 * PIN de bloqueio local (ADR-0005). Derivado com PBKDF2 via WebCrypto; o PIN
 * em si nunca é gravado. Protege a interface quando o app é reaberto, mas não
 * criptografa o IndexedDB — por isso dados pessoais não são guardados no aparelho.
 */

export const PIN_ITERATIONS = 210_000;
const PIN_PATTERN = /^\d{4,8}$/;

export function validatePin(pin: string): string | null {
  return PIN_PATTERN.test(pin) ? null : "O PIN deve ter de 4 a 8 dígitos.";
}

export async function hashPin(pin: string, iterations = PIN_ITERATIONS): Promise<PinHash> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  return {
    algorithm: "PBKDF2-SHA256",
    iterations,
    salt: toBase64(salt),
    hash: toBase64(await derive(pin, salt, iterations)),
  };
}

export async function verifyPin(pin: string, stored: PinHash): Promise<boolean> {
  const candidate = await derive(pin, fromBase64(stored.salt), stored.iterations);
  return constantTimeEquals(candidate, fromBase64(stored.hash));
}

async function derive(pin: string, salt: Uint8Array, iterations: number): Promise<Uint8Array> {
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(pin), "PBKDF2", false, [
    "deriveBits",
  ]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: salt as BufferSource, iterations },
    key,
    256,
  );
  return new Uint8Array(bits);
}

function constantTimeEquals(a: Uint8Array, b: Uint8Array): boolean {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= (a[i] ?? 0) ^ (b[i] ?? 0);
  }
  return diff === 0;
}

function toBase64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}

function fromBase64(value: string): Uint8Array {
  return Uint8Array.from(atob(value), (c) => c.charCodeAt(0));
}
