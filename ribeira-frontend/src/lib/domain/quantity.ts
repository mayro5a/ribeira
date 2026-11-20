/**
 * Quantidades de estoque como inteiros de milésimos (BigInt), espelhando o
 * `numeric(14,3)` do servidor. Nunca usar `number` para somar estoque: ponto
 * flutuante acumula erro (0.1 + 0.2 !== 0.3).
 */

export const UNITS = ["KILOGRAM", "LITER", "UNIT", "PACKAGE"] as const;
export type UnitOfMeasure = (typeof UNITS)[number];

export const UNIT_LABELS: Record<UnitOfMeasure, { singular: string; short: string }> = {
  KILOGRAM: { singular: "quilograma", short: "kg" },
  LITER: { singular: "litro", short: "L" },
  UNIT: { singular: "unidade", short: "un" },
  PACKAGE: { singular: "pacote", short: "pct" },
};

/** Unidades contáveis não aceitam frações. */
const INTEGER_UNITS: ReadonlySet<UnitOfMeasure> = new Set(["UNIT", "PACKAGE"]);

export const SCALE = 3;
const FACTOR = 10n ** BigInt(SCALE);
const MAX_INTEGER_DIGITS = 11; // numeric(14,3)
const DECIMAL_PATTERN = /^(\d+)(?:[.,](\d{1,3}))?$/;

/** Quantidade em milésimos da unidade. */
export type Milli = bigint & { readonly __brand: "Milli" };

export class QuantityError extends Error {}

export function parseQuantity(input: string): Milli {
  const trimmed = input.trim();
  const match = DECIMAL_PATTERN.exec(trimmed);
  if (!match) {
    throw new QuantityError("Informe um número com até 3 casas decimais.");
  }
  const integerPart = match[1] ?? "0";
  if (integerPart.replace(/^0+/, "").length > MAX_INTEGER_DIGITS) {
    throw new QuantityError("Quantidade grande demais.");
  }
  const fraction = (match[2] ?? "").padEnd(SCALE, "0");
  return (BigInt(integerPart) * FACTOR + BigInt(fraction)) as Milli;
}

/** Formato canônico trocado com o servidor: sempre 3 casas, ponto decimal. */
export function toDecimalString(value: Milli): string {
  const negative = value < 0n;
  const abs = negative ? -value : value;
  const integer = abs / FACTOR;
  const fraction = (abs % FACTOR).toString().padStart(SCALE, "0");
  return `${negative ? "-" : ""}${integer}.${fraction}`;
}

export function fromDecimalString(value: string): Milli {
  const negative = value.startsWith("-");
  const parsed = parseQuantity(negative ? value.slice(1) : value);
  return (negative ? -parsed : parsed) as Milli;
}

export function formatQuantity(value: Milli, unit: UnitOfMeasure): string {
  const decimal = toDecimalString(value)
    .replace(/\.?0+$/, "")
    .replace(".", ",");
  return `${decimal || "0"} ${UNIT_LABELS[unit].short}`;
}

export function add(a: Milli, b: Milli): Milli {
  return (a + b) as Milli;
}

export function subtract(a: Milli, b: Milli): Milli {
  return (a - b) as Milli;
}

export const ZERO = 0n as Milli;

/** Valida a quantidade digitada para a unidade; retorna mensagem de erro ou null. */
export function validateQuantityForUnit(input: string, unit: UnitOfMeasure): string | null {
  let value: Milli;
  try {
    value = parseQuantity(input);
  } catch (error) {
    return error instanceof QuantityError ? error.message : "Quantidade inválida.";
  }
  if (value <= 0n) {
    return "A quantidade deve ser maior que zero.";
  }
  if (INTEGER_UNITS.has(unit) && value % FACTOR !== 0n) {
    return `Para ${UNIT_LABELS[unit].singular}, informe um número inteiro.`;
  }
  return null;
}
