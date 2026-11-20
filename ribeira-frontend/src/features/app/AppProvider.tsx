"use client";

import { useLiveQuery } from "dexie-react-hooks";
import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import { AuthService } from "@/lib/auth/auth-service";
import { hasPermission, type LocalSession } from "@/lib/auth/session";
import type { Permission } from "@/lib/api/schemas";
import { META_KEYS, getDatabase, getMeta, setMeta, type RibeiraDatabase } from "@/lib/db/database";
import { type SyncReport, synchronize } from "@/lib/sync/engine";
import { summarizeQueue } from "@/lib/sync/queue";
import { type ConnectionState, deriveConnectionState } from "./connection-state";

const SYNC_INTERVAL_MS = 60_000;

function subscribeOnline(onChange: () => void) {
  window.addEventListener("online", onChange);
  window.addEventListener("offline", onChange);
  return () => {
    window.removeEventListener("online", onChange);
    window.removeEventListener("offline", onChange);
  };
}

interface AppContextValue {
  db: RibeiraDatabase;
  auth: AuthService;
  /** undefined enquanto carrega do IndexedDB; null sem sessão local. */
  session: LocalSession | null | undefined;
  unlocked: boolean;
  /** Sessão offline vencida (mais de 7 dias sem contato com o servidor). */
  offlineExpired: boolean;
  unlock(): void;
  lock(): void;
  setSession(session: LocalSession | null): void;
  logout(): Promise<void>;
  communityId: string | null;
  selectCommunity(id: string): void;
  can(permission: Permission, communityId?: string | null): boolean;
  syncing: boolean;
  lastReport?: SyncReport;
  connection: ConnectionState;
  requestSync(): Promise<void>;
}

const AppContext = createContext<AppContextValue | null>(null);

export function useApp(): AppContextValue {
  const value = useContext(AppContext);
  if (!value) {
    throw new Error("useApp fora de AppProvider");
  }
  return value;
}

/**
 * Estado da aplicação. A sincronização roda ao abrir o app, quando a conexão
 * volta, a cada minuto e logo após cada registro local — nunca dependendo de
 * execução em segundo plano pelo navegador.
 */
export function AppProvider({ children }: { children: ReactNode }) {
  const db = useMemo(() => getDatabase(), []);
  const auth = useMemo(() => new AuthService(db), [db]);
  const [session, setSessionState] = useState<LocalSession | null | undefined>(undefined);
  const [unlocked, setUnlocked] = useState(false);
  const [selectedCommunityId, setSelectedCommunityId] = useState<string | null>(null);
  const [offlineExpired, setOfflineExpired] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [lastReport, setLastReport] = useState<SyncReport>();
  const browserOnline = useSyncExternalStore(
    subscribeOnline,
    () => navigator.onLine,
    () => true,
  );
  const running = useRef<Promise<void> | null>(null);
  const queue = useLiveQuery(() => summarizeQueue(db), [db]) ?? {
    pending: 0,
    sending: 0,
    synced: 0,
    failed: 0,
    conflict: 0,
  };

  useEffect(() => {
    void (async () => {
      const stored = await auth.currentSession();
      setSessionState(stored ?? null);
      setOfflineExpired(stored ? Date.now() > stored.offlineUntil : false);
      const selected = await getMeta<string>(db, META_KEYS.selectedCommunityId);
      setSelectedCommunityId(selected ?? null);
    })();
  }, [auth, db]);

  // A comunidade selecionada precisa estar entre as autorizadas; senão usa a primeira.
  const communityId = useMemo(() => {
    if (!session) return null;
    const available = Object.keys(session.communityPermissions);
    const allowed =
      selectedCommunityId !== null &&
      (available.includes(selectedCommunityId) || session.organizationPermissions.length > 0);
    return allowed ? selectedCommunityId : (available[0] ?? null);
  }, [session, selectedCommunityId]);

  const requestSync = useCallback(async () => {
    if (!session || running.current) {
      return running.current ?? undefined;
    }
    const run = (async () => {
      setSyncing(true);
      try {
        if (!auth.authenticated) {
          await auth.refresh();
          const refreshed = await auth.currentSession();
          if (refreshed) setSessionState(refreshed);
          if (auth.authenticated) setOfflineExpired(false);
        }
        const report = auth.authenticated
          ? await synchronize({ db, api: auth.api, userId: session.userId })
          : { status: "offline" as const, pushed: 0, pulled: 0 };
        setLastReport(report);
      } finally {
        setSyncing(false);
        running.current = null;
      }
    })();
    running.current = run;
    return run;
  }, [auth, db, session]);

  useEffect(() => {
    if (!session || !unlocked) return;
    void requestSync();
    const timer = setInterval(() => void requestSync(), SYNC_INTERVAL_MS);
    const onOnline = () => void requestSync();
    window.addEventListener("online", onOnline);
    return () => {
      clearInterval(timer);
      window.removeEventListener("online", onOnline);
    };
  }, [session, unlocked, requestSync]);

  const selectCommunity = useCallback(
    (id: string) => {
      setSelectedCommunityId(id);
      void setMeta(db, META_KEYS.selectedCommunityId, id);
    },
    [db],
  );

  const can = useCallback(
    (permission: Permission, target: string | null = communityId) =>
      session ? hasPermission(session, target, permission) : false,
    [session, communityId],
  );

  const logout = useCallback(async () => {
    await auth.logout();
    setUnlocked(false);
    setSessionState(null);
  }, [auth]);

  const value: AppContextValue = {
    db,
    auth,
    session,
    unlocked,
    offlineExpired: offlineExpired && !auth.authenticated,
    unlock: () => setUnlocked(true),
    lock: () => setUnlocked(false),
    setSession: setSessionState,
    logout,
    communityId,
    selectCommunity,
    can,
    syncing,
    lastReport,
    connection: deriveConnectionState({
      running: syncing,
      authenticated: auth.authenticated,
      browserOnline,
      lastReport,
      queue,
    }),
    requestSync,
  };

  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}
