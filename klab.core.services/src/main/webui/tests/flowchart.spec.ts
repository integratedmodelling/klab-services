import { test, expect } from "@playwright/test";
import { readFileSync } from "node:fs";
const chart = JSON.parse(readFileSync(new URL("./workflow.json", import.meta.url), "utf8"));
const raw = JSON.parse(readFileSync(new URL("./workflow-raw.json", import.meta.url), "utf8"));

for (const server of [false, true]) test(`keeps nested contents clickable after container selection (${server ? "server" : "browser"} layout)`, async ({ page }) => {
  const nested = { root: {
    id: "outer", width: 650, height: 450, metadata: { name: "Outer container" },
    children: [{ ...structuredClone(chart.root), x: 40, y: 60 }],
  } };
  await page.route("**/chart", route => route.fulfill({ json: nested }));
  await page.goto(`/tests/flowchart.html${server ? "?server" : ""}`);
  const nodes = page.locator(".flowchart-canvas .sprotty-node");
  const inspector = page.locator(".flowchart-inspector pre");
  await expect(nodes).toHaveCount(6);
  const outer = nodes.nth(0);
  const container = nodes.nth(1);
  // Click exposed backgrounds, then the contents, using real hit testing.
  for (let attempt = 0; attempt < 2; attempt++) {
    await outer.click({ position: { x: 12, y: 12 } });
    await expect(inspector).toContainText("Outer container");
    await container.click({ position: { x: 12, y: 12 } });
    await expect(inspector).toContainText('"workflowId": "review"');
    await page.locator(".flowchart-canvas .leaf").filter({ hasText: "Review" }).locator("rect.sprotty-node").click();
    await expect(inspector).toContainText("Work on Review");
    await outer.click({ position: { x: 12, y: 12 } });
    await page.locator('.flowchart-canvas g[id$="state:6:Review:in"] .sprotty-port').click();
    await expect(page.locator(".flowchart-inspector select")).toHaveValue("state:6:Review:in");
  }
});

for (const server of [false, true]) test(`renders ${server ? "Java" : "browser"} ELK layout and inspects metadata`, async ({ page }) => {
  const errors: string[] = []; page.on("pageerror", error => errors.push(error.message));
  await page.route("**/chart", route => route.fulfill({ json: server ? chart : raw }));
  await page.goto(`/tests/flowchart.html${server ? "?server" : ""}`);
  await expect(page.locator(".flowchart-canvas .sprotty-node")).toHaveCount(5);
  await expect(page.locator(".flowchart-canvas .sprotty-edge")).toHaveCount(6);
  await expect(page.locator(".flowchart-canvas")).toContainText("Resource review");
  await page.locator(".flowchart-inspector select").selectOption("state:6:Review");
  await expect(page.locator(".flowchart-inspector pre")).toContainText("Work on Review");
  await page.getByRole("button", { name: "Fit diagram", exact: true }).click();
  await page.screenshot({ path: `test-results/workflow-${server ? "server" : "client"}.png` });
  expect(errors).toEqual([]);
});

test("handles URL changes, errors, unmounting and multiple instances", async ({ page }) => {
  const errors: string[] = []; page.on("pageerror", error => errors.push(error.message));
  await page.route("**/chart", route => route.fulfill({ json: chart }));
  const other = structuredClone(chart); other.root.labels[0].text = "Another workflow";
  await page.route("**/other", route => route.fulfill({ json: other }));
  await page.route("**/missing", route => route.fulfill({ status: 404 }));
  await page.goto("/tests/flowchart.html?server&two");
  await expect(page.locator(".flowchart-canvas svg")).toHaveCount(2);
  await page.getByRole("button", { name: "Change URL" }).click();
  await expect(page.locator(".flowchart-canvas").first()).toContainText("Another workflow");
  await page.getByRole("button", { name: "Fail URL" }).click();
  await expect(page.getByRole("alert")).toContainText("404");
  await page.getByRole("button", { name: "Toggle", exact: true }).click();
  await expect(page.locator(".flowchart-canvas")).toHaveCount(1);
  await page.getByRole("button", { name: "Toggle", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("404");
  expect(errors).toEqual([]);
});

test("Resources workflow page lists definitions and changes the diagram", async ({ page }) => {
  await page.route("**/api/v1/workflows", route => route.fulfill({ json: [
    { id: "review", name: "Resource review", description: "Review a resource" },
    { id: "other", name: "Other workflow" },
  ] }));
  await page.route("**/api/v1/workflows/*/flowchart", route => {
    const diagram = structuredClone(chart);
    if (route.request().url().includes("/other/")) diagram.root.labels[0].text = "Other workflow";
    return route.fulfill({ json: diagram });
  });
  await page.goto("/tests/flowchart.html?workflows");
  await expect(page.locator(".flowchart-canvas")).toContainText("Resource review");
  await page.locator(".workflow-controls select").selectOption("other");
  await expect(page.locator(".flowchart-canvas")).toContainText("Other workflow");
});
