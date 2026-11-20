import type { Permission } from "@/lib/api/schemas";

/**
 * Sessão local usada para operar offline (ADR-0005). Não contém senha nem
 * tokens: apenas o perfil, as permissões por comunidade e o hash do PIN.
 */
export interface LocalSession {
  key: "current";
  userId: string;
  organizationId: string;
  displayName: string;
  email: string;
  /** Permissões efetivas por comunidade, conforme o último contato com o servidor. */
  communityPermissions: Record<string, Permission[]>;
  organizationPermissions: Permission[];
  /** Epoch ms após o qual o uso offline exige reconexão. */
  offlineUntil: number;
  pin?: PinHash;
}

export interface PinHash {
  algorithm: "PBKDF2-SHA256";
  iterations: number;
  salt: string;
  hash: string;
}

export const OFFLINE_SESSION_DAYS = 7;

export function hasPermission(
  session: Pick<LocalSession, "communityPermissions" | "organizationPermissions">,
  communityId: string | null,
  permission: Permission,
): boolean {
  if (session.organizationPermissions.includes(permission)) {
    return true;
  }
  return (
    communityId !== null && (session.communityPermissions[communityId] ?? []).includes(permission)
  );
}
