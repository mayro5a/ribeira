import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

export default defineConfig([
  ...nextVitals,
  ...nextTs,
  {
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "no-console": ["warn", { allow: ["warn", "error"] }],
    },
  },
  {
    // Scripts de build rodam no terminal: saída no console é esperada.
    files: ["scripts/**"],
    rules: { "no-console": "off" },
  },
  globalIgnores([
    ".next/**",
    "out/**",
    "coverage/**",
    "next-env.d.ts",
    "public/sw.js",
    "playwright-report/**",
    "test-results/**",
  ]),
]);
