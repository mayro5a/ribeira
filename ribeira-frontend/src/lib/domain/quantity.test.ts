import {
  add,
  formatQuantity,
  fromDecimalString,
  parseQuantity,
  subtract,
  toDecimalString,
  validateQuantityForUnit,
} from "./quantity";

describe("quantity", () => {
  it("avoids floating point drift", () => {
    const sum = add(parseQuantity("0.1"), parseQuantity("0.2"));
    expect(toDecimalString(sum)).toBe("0.300");
  });

  it("accepts comma as decimal separator", () => {
    expect(toDecimalString(parseQuantity("2,5"))).toBe("2.500");
  });

  it("round-trips the server canonical format, including negatives", () => {
    expect(toDecimalString(fromDecimalString("-1.250"))).toBe("-1.250");
    expect(toDecimalString(subtract(parseQuantity("1"), parseQuantity("3")))).toBe("-2.000");
  });

  it("rejects more than three decimal places", () => {
    expect(() => parseQuantity("1.0001")).toThrow();
  });

  it("formats for display in pt-BR", () => {
    expect(formatQuantity(parseQuantity("12.500"), "KILOGRAM")).toBe("12,5 kg");
    expect(formatQuantity(parseQuantity("3"), "UNIT")).toBe("3 un");
  });

  it("requires whole numbers for countable units", () => {
    expect(validateQuantityForUnit("1.5", "UNIT")).toMatch(/inteiro/);
    expect(validateQuantityForUnit("1.5", "KILOGRAM")).toBeNull();
  });

  it("requires positive quantities", () => {
    expect(validateQuantityForUnit("0", "KILOGRAM")).toMatch(/maior que zero/);
    expect(validateQuantityForUnit("abc", "KILOGRAM")).toMatch(/número/);
  });
});
