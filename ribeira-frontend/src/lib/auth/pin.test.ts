// @vitest-environment node
import { hashPin, validatePin, verifyPin } from "./pin";

describe("local PIN", () => {
  it("verifies the right PIN and rejects a wrong one", async () => {
    const stored = await hashPin("4821", 1_000);

    expect(await verifyPin("4821", stored)).toBe(true);
    expect(await verifyPin("4822", stored)).toBe(false);
    expect(stored.hash).not.toContain("4821");
  });

  it("uses a random salt per PIN", async () => {
    const a = await hashPin("1234", 1_000);
    const b = await hashPin("1234", 1_000);
    expect(a.salt).not.toBe(b.salt);
    expect(a.hash).not.toBe(b.hash);
  });

  it("accepts only 4 to 8 digits", () => {
    expect(validatePin("123")).not.toBeNull();
    expect(validatePin("12a4")).not.toBeNull();
    expect(validatePin("123456")).toBeNull();
  });
});
