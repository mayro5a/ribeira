import { expect, type Page, test } from "@playwright/test";

/**
 * Cenários críticos de aceitação 1, 2 e 7 (prompt §13) no navegador real:
 * registrar offline, fechar/reabrir mantendo os dados, conferir o saldo local
 * e sincronizar ao voltar a conexão.
 */

const PASSWORD = process.env.E2E_DEMO_PASSWORD ?? "demo-ribeira-2026";
const PIN = "4826";
// Único por execução: o teste pode rodar várias vezes contra o mesmo banco.
const origin = `Campanha fictícia E2E ${Date.now()}`;

/** O HTML estático aparece antes da hidratação; interagir antes disso perde a digitação. */
async function gotoHydrated(page: Page, path: string) {
  await page.goto(path);
  await page.waitForLoadState("networkidle");
}

async function signIn(page: Page) {
  await gotoHydrated(page, "/entrar/");
  await page.getByLabel("E-mail").fill("admin@demo.ribeira.local");
  await page.getByLabel("Senha").fill(PASSWORD);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.getByLabel("PIN (4 a 8 dígitos)").fill(PIN);
  await page.getByLabel("Confirme o PIN").fill(PIN);
  await page.getByRole("button", { name: "Salvar PIN" }).click();
  await expect(page.getByRole("heading", { name: /Olá/ })).toBeVisible();
}

/** Abrir uma URL equivale a reabrir o app: pede o PIN e volta para a mesma tela. */
async function openWithPin(page: Page, path: string) {
  await gotoHydrated(page, path);
  await page.getByLabel("PIN (4 a 8 dígitos)").fill(PIN);
  await page.getByRole("button", { name: "Desbloquear" }).click();
  await expect(page).toHaveURL(new RegExp(path.replaceAll("/", "\\/")));
}

async function kilogramsOf(page: Page, food: string): Promise<number> {
  const text = await page.getByRole("button", { name: new RegExp(food) }).innerText();
  const match = /([\d.,]+) kg/.exec(text);
  if (!match?.[1]) throw new Error(`Saldo em kg não encontrado: ${text}`);
  return Number(match[1].replace(/\./g, "").replace(",", "."));
}

function formatKg(value: number): string {
  return value.toLocaleString("pt-BR", { maximumFractionDigits: 3 });
}

async function waitUntilSynced(page: Page) {
  await expect(page.getByRole("link", { name: /Sincronização: Sincronizado/ })).toBeVisible({
    timeout: 30_000,
  });
}

test("registra doação e distribuição offline, sobrevive à reabertura e sincroniza depois", async ({
  page,
  context,
}) => {
  await signIn(page);
  const community = page.getByLabel("Comunidade", { exact: true });
  await expect(community.locator("option", { hasText: "Comunidade Demo Alfa" })).toHaveCount(1);
  await community.selectOption({ label: "Comunidade Demo Alfa" });
  await waitUntilSynced(page);
  // O Service Worker precisa terminar o precache antes de perder a conexão.
  await page.evaluate(async () => {
    await navigator.serviceWorker.ready;
  });

  await context.setOffline(true);

  // Cenário 1: doação offline, aplicativo reaberto sem internet, registro mantido.
  await openWithPin(page, "/doacoes/nova/");
  await page.getByLabel("Origem da doação").fill(origin);
  await page.getByLabel(/^Alimento/).selectOption({ label: "Feijão" });
  await page.getByLabel(/Quantidade/).fill("7");
  await page.getByLabel("Validade", { exact: true }).selectOption("UNKNOWN");
  await page.getByRole("button", { name: "Salvar doação" }).click();
  await expect(page.getByText("Doação salva no aparelho")).toBeVisible();

  await openWithPin(page, "/doacoes/");
  const donation = page.getByRole("listitem").filter({ hasText: origin });
  await expect(donation.getByText("Salvo no aparelho")).toBeVisible();

  // Cenário 2: distribuição offline e atualização local do estoque.
  await page.getByRole("link", { name: "Estoque" }).click();
  const riceBefore = await kilogramsOf(page, "Arroz");
  await page.getByRole("link", { name: "Distribuir" }).click();
  await page.getByRole("link", { name: "Nova distribuição" }).click();
  await page.getByLabel("DEMO-001").check();
  const batch = page.getByLabel(/^Lote/);
  const riceBatch = await batch
    .locator("option", { hasText: "Arroz" })
    .first()
    .getAttribute("value");
  await batch.selectOption(riceBatch!);
  await page.getByLabel(/Quantidade/).fill("2");
  await page.getByRole("button", { name: "Revisar distribuição" }).click();
  await page.getByRole("button", { name: "Confirmar" }).click();
  await expect(page.getByText(/Comprovante provisório/)).toBeVisible();

  await page.getByRole("link", { name: "Estoque" }).click();
  const rice = page.getByRole("button", { name: /Arroz/ });
  await expect(rice).toContainText(`${formatKg(riceBefore - 2)} kg`);
  await expect(rice).toContainText("provisório");

  // Cenário 7: conexão restaurada, fila enviada e alterações recebidas.
  await context.setOffline(false);
  await page.getByRole("link", { name: /Sincronização:/ }).click();
  await page.getByRole("button", { name: "Sincronizar agora" }).click();
  await waitUntilSynced(page);
  await page.getByRole("link", { name: "Doações" }).click();
  await expect(
    page.getByRole("listitem").filter({ hasText: origin }).getByText("Confirmado"),
  ).toBeVisible();
  await page.getByRole("link", { name: "Estoque" }).click();
  await expect(page.getByRole("button", { name: /Arroz/ })).not.toContainText("provisório");
});
