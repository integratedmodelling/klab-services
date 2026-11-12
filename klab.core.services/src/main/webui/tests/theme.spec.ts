import { test, expect } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/public/ui/config", route => route.fulfill({ json: {
    serviceType: "RESOURCES", title: "k.LAB Resources service", subtitle: "Resource catalogs and adapters",
    logoUrl: "klab-logo.svg", authentication: { enabled: false, realm: "im", clientId: "test" },
    panels: [], pages: [{ name: "resource-catalog", title: "Resource catalog", description: "Browse service resources", component: "resources-workspace", requiresAuthentication: false }], links: [], modules: {},
  } }));
  await page.route("**/public/status", route => route.fulfill({ json: { operational: true, healthPercentage: 100 } }));
  await page.route("**/public/capabilities", route => route.fulfill({ json: { serviceName: "test", components: [], permissions: [] } }));
});

test("theme follows an explicit choice across reloads, routes and tabs", async ({ page, context }) => {
  await page.goto("/tests/theme.html");
  await page.getByRole("button", { name: "Switch to dark theme" }).click();
  await expect(page.locator("body")).toHaveClass(/body--dark/);
  await page.reload();
  await expect(page.getByRole("button", { name: "Switch to light theme" })).toBeVisible();
  const other = await context.newPage();
  await other.goto("/tests/theme.html");
  await expect(other.getByRole("button", { name: "Switch to light theme" })).toBeVisible();
  await page.getByRole("link", { name: "Resource catalog", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Resource catalog", exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "Switch to light theme" }).click();
  await expect(other.getByRole("button", { name: "Switch to dark theme" })).toBeVisible();
});

test("dashboard controls and navigation fit small screens and retain keyboard focus", async ({ page }) => {
  await page.goto("/tests/theme.html");
  await page.keyboard.press("Tab");
  await expect(page.getByRole("link", { name: "Skip to content" })).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page.locator("#main-content")).toBeFocused();
  await page.getByRole("button", { name: "Switch to dark theme" }).focus();
  await expect(page.getByRole("button", { name: "Switch to dark theme" })).toHaveCSS("outline-style", "solid");
  await expect(page.getByRole("button", { name: "Switch to dark theme" })).toHaveCSS("outline-width", "2px");
  for (const width of [1440, 1024, 820, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.getByRole("button", { name: "Service interfaces" }).click();
  await page.getByRole("link", { name: "Resource catalog", exact: true }).last().click();
  await expect(page.locator(".full-page-heading h1")).toHaveText("Resource catalog");
});

test("theme remains usable when browser storage is unavailable", async ({ page }) => {
  await page.addInitScript(() => { Storage.prototype.getItem = () => { throw new Error("Storage unavailable"); }; Storage.prototype.setItem = () => { throw new Error("Storage unavailable"); }; });
  await page.goto("/tests/theme.html");
  await page.getByRole("button", { name: "Switch to dark theme" }).click();
  await expect(page.getByRole("button", { name: "Switch to light theme" })).toBeVisible();
});
