import { deriveConnectionState } from "./connection-state";

const empty = { pending: 0, sending: 0, synced: 0, failed: 0, conflict: 0 };

describe("connection indicator", () => {
  it("reports offline from a real failed attempt even if the browser says online", () => {
    const state = deriveConnectionState({
      running: false,
      authenticated: true,
      browserOnline: true,
      lastReport: { status: "offline", pushed: 0, pulled: 0 },
      queue: { ...empty, pending: 2 },
    });
    expect(state.kind).toBe("offline");
    expect(state.detail).toContain("2");
  });

  it("never shows synced while operations are waiting", () => {
    const state = deriveConnectionState({
      running: false,
      authenticated: true,
      browserOnline: true,
      lastReport: { status: "ok", pushed: 0, pulled: 0 },
      queue: { ...empty, pending: 1 },
    });
    expect(state.kind).toBe("pending");
  });

  it("prioritizes conflicts", () => {
    const state = deriveConnectionState({
      running: false,
      authenticated: true,
      browserOnline: true,
      lastReport: { status: "ok", pushed: 1, pulled: 0 },
      queue: { ...empty, conflict: 1 },
    });
    expect(state.label).toBe("Com conflitos");
  });

  it("shows synced only when everything was confirmed", () => {
    const state = deriveConnectionState({
      running: false,
      authenticated: true,
      browserOnline: true,
      lastReport: { status: "ok", pushed: 1, pulled: 3 },
      queue: { ...empty, synced: 4 },
    });
    expect(state.kind).toBe("synced");
  });
});
