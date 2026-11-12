import { test, expect } from "@playwright/test";
import { fixtureResponse } from "./knowledge-fixtures";
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { graphRelations, loadNeighborhood, WorldviewCache, type Concept, type WorldviewCatalog } from "../src/knowledge/model";
import type { ServiceApi } from "../src/services/api";
test.beforeEach(async ({ page }) => {
  let declaration = "";
  await page.route("**/api/v1/**", async route => {
    const body = route.request().postData();
    const data = body ? (body.startsWith("{") ? JSON.parse(body) : body) : null;
    const path = route.request().url();
    const result = fixtureResponse(path, data, declaration);
    if (path.endsWith("semanticSearch")) declaration = result.declaration;
    await route.fulfill({ json: result });
  });
});

test("composer starts compact, selects a server proposal, colors tokens and recenters the graph", async ({ page }) => {
  const errors: string[] = []; page.on("pageerror", e => errors.push(e.message));
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".semantic-overlay")).toHaveCount(0);
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(2);
  const input = page.getByRole("combobox", { name: "Compose a concept" });
  await input.fill("Landscape");
  await expect(page.getByRole("listbox").getByRole("option", { name: /Landscape/ })).toBeVisible();
  await input.press("Enter");
  await expect(page.locator(".semantic-overlay")).toHaveCount(0);
  await expect(page.locator(".semantic-expression > span")).toHaveText("earth:Landscape ");
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Landscape");
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(6);
  await expect(page.locator(".worldview-canvas .sprotty-edge")).toHaveCount(5);
  await expect(input).toBeFocused();
  await input.press("Backspace");
  await expect(page.locator(".semantic-expression > span")).toHaveCount(0);
  expect(errors).toEqual([]);
});

test("tree navigation, history, search and graph-only component configuration", async ({ page }) => {
  await page.goto("/tests/knowledge.html?standalone&graph-only");
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(6);
  await expect(page.getByRole("button", { name: "Ontologies", exact: true })).toHaveCount(0);
  await expect(page.locator(".worldview-sidebar")).toHaveCount(0);
  await page.getByRole("button", { name: "Configure tree" }).click();
  await expect(page.locator(".worldview-sidebar")).toBeVisible();
  await page.getByRole("searchbox", { name: "Filter declarations" }).fill("Forest");
  await page.locator(".worldview-tree button").click();
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Forest");
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Landscape");
  await page.getByRole("button", { name: "Configure tree" }).click();
  await expect(page.locator(".worldview-sidebar")).toHaveCount(0);
});

test("failed graph load is retryable and unmounted instances release semantic sessions", async ({ page }) => {
  let fail = true;
  await page.route("**/api/v1/parents?*", route => fail ? route.fulfill({ status: 503 }) : route.fulfill({ json: [] }));
  await page.goto("/tests/knowledge.html?standalone");
  await expect(page.getByRole("alert")).toContainText("503");
  fail = false;
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(5);
  const input = page.getByRole("combobox", { name: "Compose a concept" });
  await input.fill("Forest"); await expect(page.getByRole("listbox").getByRole("option", { name: /Forest/ })).toBeVisible();
  const cancel = page.waitForRequest(r => r.postData()?.includes('"cancelSearch":true') === true);
  await page.getByRole("button", { name: "Toggle components" }).click(); await cancel;
  await expect(page.locator(".worldview-canvas")).toHaveCount(0);
});

test("late suggestions cannot replace a newer query, and typing during selection preserves the new query", async ({ page }) => {
  let declaration = "";
  await page.route("**/api/v1/semanticSearch", async route => {
    const body = route.request().postDataJSON();
    if (body.queryString === "Fo" || body.searchMode === "SELECT") await new Promise(resolve => setTimeout(resolve, 600));
    const result = fixtureResponse(route.request().url(), body, declaration);
    declaration = result.declaration;
    await route.fulfill({ json: result });
  });
  await page.goto("/tests/knowledge.html");
  const input = page.getByRole("combobox", { name: "Compose a concept" });
  const pending = page.waitForRequest(r => r.postData()?.includes('"queryString":"Fo"') === true);
  await input.fill("Fo"); await pending; await input.fill("Landscape");
  await expect(page.getByRole("listbox").getByRole("option")).toHaveCount(1);
  await expect(page.getByRole("listbox")).toContainText("Landscape");
  const selecting = page.waitForRequest(r => r.postData()?.includes('"searchMode":"SELECT"') === true);
  await input.press("Enter"); await selecting; await input.fill("Wetland");
  await expect(page.locator(".semantic-expression > span")).toHaveText("earth:Landscape ");
  await expect(input).toHaveValue("Wetland");
  await expect(page.getByRole("listbox")).toContainText("Wetland");
  await expect(page.getByRole("listbox")).not.toContainText("Forest");
});

test("an uncertain selection recovers the committed state without replaying the edit", async ({ page }) => {
  let declaration = "", selections = 0;
  await page.route("**/api/v1/semanticSearch", async route => {
    const body = route.request().postDataJSON();
    const result = fixtureResponse(route.request().url(), body, declaration);
    declaration = result.declaration;
    if (body.searchMode === "SELECT") { selections++; await route.fulfill({ status: 503 }); }
    else await route.fulfill({ json: result });
  });
  await page.goto("/tests/knowledge.html");
  const input = page.getByRole("combobox", { name: "Compose a concept" });
  await input.fill("Forest"); await expect(page.getByRole("listbox")).toContainText("Forest");
  await input.press("Enter");
  await expect(page.getByRole("alert")).toContainText("503");
  await expect(page.getByRole("button", { name: "Explore confirmed expression" })).toBeDisabled();
  await page.getByRole("button", { name: "Retry", exact: true }).click();
  await expect(page.locator(".semantic-expression > span")).toHaveText("earth:Forest ");
  expect(selections).toBe(1);
});

test("authority choices insert an exact canonical code at the current semantic revision", async ({ page }) => {
  await page.goto("/tests/knowledge.html");
  const input = page.getByRole("combobox", { name: "Compose a concept" });
  await input.fill("Forest"); await expect(page.getByRole("listbox")).toContainText("Forest");
  await page.getByRole("combobox", { name: "Source", exact: true }).selectOption("SPECIES");
  await input.fill("Oak"); await expect(page.getByRole("listbox")).toContainText("Oak");
  const insertion = page.waitForRequest(r => r.postData()?.includes('"searchMode":"IDENTITY"') === true);
  await input.press("Enter");
  const body = (await insertion).postDataJSON();
  expect(body.authority).toBe("SPECIES"); expect(body.identityCode).toBe("123");
  expect(body.matchesRequestId).toBeGreaterThan(0);
});

test("expired sessions retain the visible expression until an explicit restart", async ({ page }) => {
  let declaration = "", expired = false;
  await page.route("**/api/v1/semanticSearch", async route => {
    const body = route.request().postDataJSON();
    if (expired && body.searchId) {
      await route.fulfill({ json: { searchId: 0, requestId: body.requestId, errors: ["This search has expired. Start a new expression."] } });
      return;
    }
    if (expired && !body.searchId) declaration = "";
    const result = fixtureResponse(route.request().url(), body, declaration); declaration = result.declaration;
    await route.fulfill({ json: result });
  });
  await page.goto("/tests/knowledge.html");
  const input = page.getByRole("combobox", { name: "Compose a concept" });
  await input.fill("Forest"); await expect(page.getByRole("listbox")).toContainText("Forest"); await input.press("Enter");
  await expect(page.locator(".semantic-expression > span")).toHaveText("earth:Forest ");
  expired = true; await input.fill("Wetland");
  await expect(page.getByRole("alert")).toContainText("expired");
  await expect(page.locator(".semantic-expression > span")).toHaveText("earth:Forest ");
  await expect(page.getByRole("button", { name: "Explore confirmed expression" })).toBeDisabled();
  await page.getByRole("button", { name: "Restart", exact: true }).click();
  await expect(page.locator(".semantic-expression > span")).toHaveCount(0);
  await expect(input).toHaveValue("Wetland");
  await expect(page.getByRole("listbox")).toContainText("Wetland");
});

test("graph-only instances have independent canvases and keyboard navigation", async ({ page }) => {
  const errors: string[] = []; page.on("pageerror", e => errors.push(e.message));
  await page.goto("/tests/knowledge.html?standalone&graph-only&two");
  await expect(page.locator(".worldview-canvas svg")).toHaveCount(2);
  const browser = page.getByRole("region", { name: "Worldview browser" });
  await browser.first().getByRole("combobox", { name: "Focus concept" }).selectOption("earth:Wetland");
  await expect(browser.first().locator(".worldview-inspector code")).toHaveText("earth:Wetland");
  await expect(browser.last().locator(".worldview-inspector code")).toHaveText("earth:Forest");
  expect(errors).toEqual([]);
});

for (const status of [200, 204]) test(`nullable relation HTTP ${status} bodies do not break initial loading`, async ({ page }) => {
  await page.route("**/api/v1/described?*", route => route.fulfill({ status, body: "" }));
  await page.goto("/tests/knowledge.html?standalone");
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(6);
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("cached neighborhoods avoid HTTP requests on Back, including empty relations; Reload invalidates them", async ({ page }) => {
  let calls = 0;
  page.on("request", request => { if (request.url().includes("/api/v1/") && !request.url().endsWith("worldview/explorer")) calls++; });
  await page.route("**/api/v1/described?*", route => route.fulfill({ status: 200, body: "" }));
  await page.goto("/tests/knowledge.html?standalone&graph-only");
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Landscape");
  await page.getByRole("combobox", { name: "Focus concept" }).selectOption("earth:Forest");
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Forest");
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  const afterForest = calls;
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Landscape");
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  expect(calls).toBe(afterForest);
  await page.getByRole("button", { name: "Reload", exact: true }).click();
  await expect.poll(() => calls).toBeGreaterThan(afterForest);
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Landscape");
});

test("independent relation queries overlap rather than waiting for one slow relation", async ({ page }) => {
  let release: (() => void) | undefined;
  const blocked = new Promise<void>(resolve => { release = resolve; });
  await page.route("**/api/v1/parents?*", async route => { await blocked; await route.fulfill({ json: [] }); });
  let rolesRequested = false;
  page.on("request", request => { if (request.url().includes("/roles?")) rolesRequested = true; });
  await page.goto("/tests/knowledge.html?standalone&graph-only");
  try { await expect.poll(() => rolesRequested).toBe(true); }
  finally { release?.(); }
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(5);
});

test("malformed nonempty JSON remains an explicit endpoint error", async ({ page }) => {
  await page.route("**/api/v1/roles?*", route => route.fulfill({ status: 200, body: '{"urn":', contentType: "application/json" }));
  await page.goto("/tests/knowledge.html?standalone");
  await expect(page.getByRole("alert")).toContainText("Invalid JSON from /api/v1/roles?direct=true");
});

test("relationship switches match the IDE labels, order and default status", async ({ page }) => {
  const ide = await readFile(new URL("../../../../../../klab-ide/src/main/java/org/integratedmodelling/klab/ide/components/WorldviewGraphModel.java", import.meta.url), "utf8").catch(() => "");
  test.skip(!ide, "The sibling IDE source is needed for this parity check");
  const expected = [...ide.matchAll(/\b([A-Z_]+)\(\s*"([^"]+)",\s*(true|false)/g)]
    .map(match => ({ id: match[1], label: match[2], selected: match[3] === "true" }));
  expect(expected).toHaveLength(30);
  expect(graphRelations.map(({ id, label, selected }) => ({ id, label, selected }))).toEqual(expected);
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Subject");
  await page.locator(".worldview-relations summary").click();
  await expect(page.locator('.worldview-relations input[type="checkbox"]')).toHaveCount(30);
  for (const relation of expected) {
    const checkbox = page.getByRole("checkbox", { name: relation.label, exact: true });
    if (relation.selected) await expect(checkbox).toBeChecked();
    else await expect(checkbox).not.toBeChecked();
  }
});

for (const width of [320, 390, 1024]) test(`all relationship switches stay reachable at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 844 });
  await page.goto("/tests/knowledge.html");
  await page.locator(".worldview-relations summary").click();
  const menu = page.locator(".worldview-relations > div");
  await expect(menu).toBeVisible();
  await expect.poll(async () => (await menu.boundingBox())!.x).toBeGreaterThanOrEqual(0);
  const box = (await menu.boundingBox())!;
  expect(box.x + box.width).toBeLessThanOrEqual(width);
  await page.getByRole("checkbox", { name: "Logical operands", exact: true }).scrollIntoViewIfNeeded();
  await expect(page.getByRole("checkbox", { name: "Logical operands", exact: true })).toBeInViewport();
});

test("all relation families use the IDE projections and share cached influence queries", async () => {
  const root: Concept = { urn: "test:Quality", type: ["QUALITY"] };
  const calls: string[] = [];
  const kinds = ["AFFECTS", "CREATES", "MARKS", "INCREASES_WITH", "DECREASES_WITH", "DISCRETIZES", "CLASSIFIES"];
  let active = 0, maximum = 0;
  const request: ServiceApi["request"] = async (path, init) => {
    calls.push(path); active++; maximum = Math.max(maximum, active);
    try {
      await new Promise(resolve => setTimeout(resolve, 5));
      if (path.endsWith("resolve/concept")) return root as any;
      if (path.endsWith("influences")) return kinds.map(kind => ({ kind, source: { urn: `test:${kind}Source` }, target: { urn: `test:${kind}Target` } })) as any;
      return [{ urn: `test:${path.split('/').pop()!.split('?')[0]}` }] as any;
    } finally { active--; }
  };
  const api: ServiceApi = { request, get: request }, cache = new WorldviewCache();
  const snapshot = await loadNeighborhood(api, root.urn, 1, graphRelations.map(r => r.id), new AbortController().signal, cache);
  for (const kind of kinds) expect(snapshot.links).toContainEqual({ source: `test:${kind}Source`, target: `test:${kind}Target`, label: kind.toLowerCase().replaceAll('_', ' ') });
  expect(calls.filter(path => path.endsWith("influences"))).toHaveLength(1);
  for (const endpoint of ["parents", "children", "described", "identities", "attributes", "realms", "roles", "inherent", "adjacent", "caused", "causant", "compresent", "goal", "cooccurent", "relativeTo", "relationshipSources", "relationshipTargets", "operands"])
    expect(calls).toContain(`api/v1/${endpoint}?direct=true`);
  expect(calls).toContain("api/v1/applicable"); expect(maximum).toBeLessThanOrEqual(6);
  const count = calls.length;
  await loadNeighborhood(api, root.urn, 1, graphRelations.map(r => r.id), new AbortController().signal, cache);
  expect(calls).toHaveLength(count);
  cache.concept({ urn: "test:Role", type: ["ROLE"] });
  await loadNeighborhood(api, "test:Role", 1, ["APPLIES", "IMPLIES", "INHERENT"], new AbortController().signal, cache);
  expect(calls).toContain("api/v1/impliedRoles?includeRelationshipEndpoints=false");
  expect(snapshot.links).toContainEqual({ source: root.urn, target: "test:inherent", label: "of" });
});

test("declared contracts preserve direction and grouped operands, and reject unsupported containment", async () => {
  const cache = new WorldviewCache();
  const values: Concept[] = [
    { urn: "test:Container", type: ["SUBJECT"] }, { urn: "test:Member", type: ["SUBJECT"] },
    { urn: "test:Event", type: ["EVENT"] }, { urn: "test:Configuration", type: ["CONFIGURATION"] },
    { urn: "(test:A or test:B)", type: ["IDENTITY", "UNION"] }, { urn: "test:Realm", type: ["REALM"] },
  ]; values.forEach(c => cache.concept(c));
  const catalog: WorldviewCatalog = { urn: "test", ontologies: [], authorities: [], declaredLinks: [
    { relation: "CONTAINS", source: values[0].urn, target: values[1].urn, label: "contains (declared)" },
    { relation: "CONTAINS", source: values[0].urn, target: values[2].urn, label: "contains (declared)" },
    { relation: "EMERGENCE", source: values[3].urn, target: values[0].urn, label: "emerges from (declared)" },
    { relation: "REQUIRED_IDENTITIES", source: values[0].urn, target: values[4].urn, label: "requires identity (declared)" },
    { relation: "REQUIRED_REALMS", source: values[0].urn, target: values[5].urn, label: "requires realm (declared)" },
  ] };
  const request: ServiceApi["request"] = async () => { throw new Error("All concepts should already be cached"); };
  const api: ServiceApi = { request, get: request };
  const result = await loadNeighborhood(api, values[0].urn, 1, ["CONTAINS", "EMERGENCE", "REQUIRED_IDENTITIES", "REQUIRED_REALMS"], new AbortController().signal, cache, catalog);
  expect(result.links).toHaveLength(4);
  expect(result.links).toContainEqual({ source: values[3].urn, target: values[0].urn, label: "emerges from (declared)" });
  expect(result.concepts.some(c => c.urn === values[4].urn)).toBe(true);
  expect(result.links.some(link => link.target === values[2].urn)).toBe(false);
});

test("canonical aliases retain graph focus and source tree selection", async ({ page }) => {
  await page.route("**/api/v1/resolve/concept", route => route.fulfill({ json: { urn: "canonical:Subject", name: "Subject", type: ["SUBJECT"] } }));
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".worldview-inspector code")).toHaveText("canonical:Subject");
  await expect(page.locator(".worldview-canvas .focus .sprotty-node")).toHaveCount(1);
  await expect(page.locator('.worldview-tree button[aria-current="true"]')).toHaveText("Subject");
});

for (const theme of ["light", "dark"]) test(`tree chevrons remain visible and toggle branches in ${theme}`, async ({ page }) => {
  await page.goto(`/tests/knowledge.html?theme=${theme}`);
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Subject");
  const branch = page.locator(".worldview-tree details").first();
  const chevron = branch.locator(":scope > summary > svg");
  await expect(chevron).toBeVisible();
  await expect(chevron).toHaveCSS("stroke-width", "2px");
  expect((await chevron.boundingBox())!.width).toBe(16);
  await chevron.click(); await expect(branch).not.toHaveAttribute("open");
  await branch.locator(":scope > summary").focus(); await page.keyboard.press("Enter");
  await expect(branch).toHaveAttribute("open");
  const ontology = page.locator(".worldview-ontology").first();
  await ontology.locator(":scope > summary > svg").click(); await expect(ontology).not.toHaveAttribute("open");
});

test("live Reasoner loads nullable relations and reuses cached neighborhoods", async ({ page }) => {
  test.skip(!process.env.KLAB_LIVE_REASONER, "Opt-in read-only test against an authenticated local Reasoner");
  const secret = (await readFile(join(homedir(), ".klab/services/reasoner/secret.key"), "utf8")).trim();
  const base = process.env.KLAB_LIVE_REASONER!.replace(/\/$/, "");
  let calls = 0, emptyRelations = 0;
  await page.route("**/api/v1/**", async route => {
    const path = route.request().url().split("/api/v1/")[1];
    calls++;
    // Keep the service secret in the test runner; never inject it into the page or artifacts.
    const response = await route.fetch({ url: `${base}/api/v1/${path}`,
      headers: { ...route.request().headers(), "server-key": secret, Authorization: `Bearer ${secret}` } });
    if (response.status() === 200 && (await response.body()).length === 0) emptyRelations++;
    await route.fulfill({ response });
  });
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".worldview-inspector code")).not.toBeEmpty();
  await expect(page.getByRole("alert")).toHaveCount(0);
  const original = await page.locator(".worldview-inspector code").textContent();
  const focus = page.getByRole("combobox", { name: "Focus concept" });
  const options = await focus.locator("option").evaluateAll(nodes => nodes.map(node => (node as HTMLOptionElement).value));
  const another = options.find(urn => urn !== original)!;
  expect(another).toBeTruthy();
  await focus.selectOption(another);
  await expect(page.locator(".worldview-inspector code")).toHaveText(another);
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  const beforeBack = calls;
  await page.getByRole("button", { name: "Back", exact: true }).click();
  await expect(page.locator(".worldview-inspector code")).toHaveText(original!);
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  expect(calls).toBe(beforeBack);
  expect(emptyRelations).toBeGreaterThan(0);
  await expect(page.getByRole("alert")).toHaveCount(0);
});

for (const theme of ["light", "dark"]) for (const width of [1440, 1024, 768, 390, 320]) test(`fits ${theme} at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.goto(`/tests/knowledge.html?theme=${theme}`);
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(2);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole("combobox", { name: "Compose a concept" }).fill("Landscape");
  await expect(page.getByRole("listbox").getByRole("option", { name: /Landscape/ })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
