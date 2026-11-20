import { expect, test } from "@playwright/test";

/**
 * Gera as imagens do README a partir da aplicação real com dados fictícios.
 * Execução: SCREENSHOTS=1 npx playwright test screenshots
 */
test.skip(!process.env.SCREENSHOTS, "Somente para atualizar as imagens da documentação");

const PASSWORD = process.env.E2E_DEMO_PASSWORD ?? "demo-ribeira-2026";
const OUT = "../docs/images";

test("captura as telas principais", async ({ page }) => {
  await page.goto("/entrar/");
  await page.waitForLoadState("networkidle");
  await page.screenshot({ path: `${OUT}/login.png` });
  await page.getByLabel("E-mail").fill("admin@demo.ribeira.local");
  await page.getByLabel("Senha").fill(PASSWORD);
  await page.getByRole("button", { name: "Entrar" }).click();
  await page.getByLabel("PIN (4 a 8 dígitos)").fill("4826");
  await page.getByLabel("Confirme o PIN").fill("4826");
  await page.getByRole("button", { name: "Salvar PIN" }).click();
  const community = page.getByLabel("Comunidade", { exact: true });
  await expect(community.locator("option", { hasText: "Comunidade Demo Alfa" })).toHaveCount(1);
  await community.selectOption({ label: "Comunidade Demo Alfa" });
  await expect(page.getByRole("link", { name: /Sincronização: Sincronizado/ })).toBeVisible();
  await page.screenshot({ path: `${OUT}/inicio.png`, fullPage: true });

  await page.getByRole("link", { name: "Estoque" }).click();
  await page.getByRole("button", { name: /Arroz/ }).click();
  await page.screenshot({ path: `${OUT}/estoque.png`, fullPage: true });

  await page.getByRole("link", { name: "Distribuir" }).click();
  await page.getByRole("link", { name: "Nova distribuição" }).click();
  await page.getByLabel("DEMO-002").check();
  await page.screenshot({ path: `${OUT}/distribuicao.png`, fullPage: true });

  await page.getByRole("link", { name: /Sincronização:/ }).click();
  await expect(page.getByRole("heading", { name: "Sincronização" })).toBeVisible();
  await page.screenshot({ path: `${OUT}/sincronizacao.png`, fullPage: true });
});
