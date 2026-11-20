import { z } from "zod";
import { ApiClient, ApiError, NetworkError, type TokenSource } from "@/lib/api/client";
import { permissionSchema } from "@/lib/api/schemas";
import { type RibeiraDatabase, META_KEYS, getOrCreateDeviceId, setMeta } from "@/lib/db/database";
import { type LocalSession, OFFLINE_SESSION_DAYS } from "./session";

const profileSchema = z.object({
  userId: z.uuid(),
  organizationId: z.uuid(),
  displayName: z.string(),
  email: z.string(),
  organizationPermissions: z.array(permissionSchema),
  communities: z.array(z.object({ communityId: z.uuid(), permissions: z.array(permissionSchema) })),
});
export type Profile = z.infer<typeof profileSchema>;

const sessionResponseSchema = z.object({
  accessToken: z.string(),
  tokenType: z.string(),
  expiresAt: z.string(),
  profile: profileSchema,
});

const DAY_MS = 86_400_000;
const CSRF_HEADER = { "X-Requested-With": "ribeira" };

/**
 * Sessão no navegador (ADR-0005): o token de acesso fica só em memória; o
 * cookie HttpOnly de renovação é gerenciado pelo navegador; o IndexedDB guarda
 * apenas perfil e permissões para uso offline.
 */
export class AuthService implements TokenSource {
  readonly api: ApiClient;
  private token: string | null = null;
  private refreshing: Promise<string | null> | null = null;

  constructor(
    private readonly db: RibeiraDatabase,
    fetchImpl?: typeof fetch,
    private readonly now: () => number = () => Date.now(),
  ) {
    this.api = new ApiClient(this, fetchImpl);
  }

  accessToken(): string | null {
    return this.token;
  }

  get authenticated(): boolean {
    return this.token !== null;
  }

  async login(email: string, password: string): Promise<LocalSession> {
    const response = await this.api.post(
      "/api/v1/auth/login",
      { email, password },
      { schema: sessionResponseSchema, withCredentials: true, skipRefresh: true },
    );
    this.token = response.accessToken;
    const session = await this.persistSession(response.profile);
    await this.registerDevice();
    return session;
  }

  /** Concurrent callers share one refresh request (single flight). */
  refresh(): Promise<string | null> {
    this.refreshing ??= this.doRefresh().finally(() => {
      this.refreshing = null;
    });
    return this.refreshing;
  }

  async logout(): Promise<void> {
    try {
      await this.api.post("/api/v1/auth/logout", undefined, {
        withCredentials: true,
        skipRefresh: true,
        headers: CSRF_HEADER,
      });
    } catch {
      // Offline: a sessão local é encerrada mesmo assim; o cookie expira sozinho.
    }
    this.token = null;
    await this.db.session.delete("current");
  }

  async currentSession(): Promise<LocalSession | undefined> {
    return this.db.session.get("current");
  }

  private async doRefresh(): Promise<string | null> {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const response = await this.api.post("/api/v1/auth/refresh", undefined, {
          schema: sessionResponseSchema,
          withCredentials: true,
          skipRefresh: true,
          headers: CSRF_HEADER,
        });
        this.token = response.accessToken;
        await this.persistSession(response.profile);
        return this.token;
      } catch (error) {
        const rotatedElsewhere =
          error instanceof ApiError && error.code === "REFRESH_TOKEN_ROTATED" && attempt === 0;
        if (rotatedElsewhere) {
          await new Promise((resolve) => setTimeout(resolve, 500));
          continue;
        }
        if (error instanceof NetworkError) {
          return null; // offline: segue com a sessão local
        }
        this.token = null;
        return null;
      }
    }
    return null;
  }

  /**
   * Atualiza perfil e permissões locais e estende a validade offline. Se outro
   * usuário entrar no mesmo aparelho, os dados baixados pelo anterior são
   * apagados e a sincronização recomeça do zero.
   */
  private async persistSession(profile: Profile): Promise<LocalSession> {
    return this.db.transaction(
      "rw",
      [this.db.session, this.db.meta, ...this.db.snapshotTables],
      async () => {
        const existing = await this.db.session.get("current");
        const sameUser = existing?.userId === profile.userId;
        if (existing && !sameUser) {
          await Promise.all(this.db.snapshotTables.map((table) => table.clear()));
          await setMeta(this.db, META_KEYS.syncCursor, 0);
        }
        const session: LocalSession = {
          key: "current",
          userId: profile.userId,
          organizationId: profile.organizationId,
          displayName: profile.displayName,
          email: profile.email,
          organizationPermissions: profile.organizationPermissions,
          communityPermissions: Object.fromEntries(
            profile.communities.map((c) => [c.communityId, c.permissions]),
          ),
          offlineUntil: this.now() + OFFLINE_SESSION_DAYS * DAY_MS,
          pin: sameUser ? existing?.pin : undefined,
        };
        await this.db.session.put(session);
        return session;
      },
    );
  }

  private async registerDevice(): Promise<void> {
    const deviceId = await getOrCreateDeviceId(this.db);
    const name = typeof navigator === "undefined" ? "Aparelho" : navigator.userAgent.slice(0, 110);
    await this.api.post("/api/v1/devices", { deviceId, name });
  }
}
