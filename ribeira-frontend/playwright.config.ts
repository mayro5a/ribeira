import { defineConfig, devices } from "@playwright/test";

/**
 * Testes ponta a ponta contra a stack completa (docker compose): nginx servindo
 * o export estático com Service Worker e o backend com dados fictícios.
 * Pré-requisito: `docker compose up -d --build --wait` com RIBEIRA_DEMO_DATA_ENABLED=true.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    locale: "pt-BR",
    timezoneId: "America/Manaus",
  },
  projects: [{ name: "android", use: { ...devices["Pixel 5"] } }],
});
