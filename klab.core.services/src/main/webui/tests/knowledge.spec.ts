import { test, expect } from "@playwright/test";
import { fixtureResponse, catalog as fixtureCatalog } from "./knowledge-fixtures";
import { readFile } from "node:fs/promises";
import { homedir } from "node:os";
import { join } from "node:path";
import { graphRelations, loadNeighborhood, WorldviewCache, conceptLabel, conceptDocumentation, type Concept, type WorldviewCatalog } from "../src/knowledge/model";
import { documentationMarkdown } from "../src/knowledge/markdown";
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

test("composer opens on focus before typing and reopens on a click after a choice", async ({ page }) => {
  await page.goto("/tests/knowledge.html");
  const input = page.getByRole("combobox", { name: "Compose a concept" });
  await expect(page.locator(".semantic-overlay")).toHaveCount(0);
  await input.click();
  await expect(page.locator(".semantic-overlay")).toBeVisible();
  await expect(input).toHaveValue("");
  await expect(page.getByRole("listbox")).toContainText("Landscape");
  await input.fill("Landscape");
  await expect(page.getByRole("listbox").getByRole("option")).toHaveCount(1);
  await page.getByRole("listbox").getByRole("option").click();
  await expect(page.locator(".semantic-overlay")).toHaveCount(0);
  await expect(input).toBeFocused();
  await input.click(); await expect(page.locator(".semantic-overlay")).toBeVisible();
  await input.press("Escape"); await expect(page.locator(".semantic-overlay")).toHaveCount(0);
});

test("ontologies start collapsed, tree controls expand/reset, and the search stays above the scroller", async ({ page }) => {
  const catalog = structuredClone(fixtureCatalog);
  catalog.ontologies.push({ urn: "additional", children: Array.from({ length: 100 }, (_, index) => ({ urn: `additional:C${index}`, label: `Concept ${index}`, type: ["SUBJECT"], children: [] })) });
  await page.route("**/api/v1/worldview/explorer", route => route.fulfill({ json: catalog }));
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".worldview-ontology")).toHaveCount(2);
  await expect(page.locator(".worldview-sidebar details[open]")).toHaveCount(0);
  await page.getByRole("button", { name: "Expand tree", exact: true }).click();
  await expect(page.locator(".worldview-ontology[open]")).toHaveCount(2);
  await expect(page.locator(".worldview-tree details[open]")).toHaveCount(2);
  const input = page.getByRole("searchbox", { name: "Filter declarations" });
  const initial = (await input.boundingBox())!;
  const scroller = page.locator(".worldview-tree-scroll");
  await scroller.evaluate(element => { element.scrollTop = 1200; });
  expect(await scroller.evaluate(element => element.scrollTop)).toBeGreaterThan(500);
  expect((await input.boundingBox())!.y).toBe(initial.y);
  await page.getByRole("button", { name: "Collapse tree", exact: true }).click();
  await expect(page.locator(".worldview-sidebar details[open]")).toHaveCount(0);
  await expect(page.locator(".worldview-ontology summary").last()).toBeVisible();
});

test("full screen contains the composer and graph and restores the normal layout", async ({ page }) => {
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  const before = (await page.locator(".worldview-browser").boundingBox())!;
  await page.getByRole("button", { name: "Enter full screen", exact: true }).click();
  await expect(page.locator(".reasoner-workspace")).toHaveClass(/knowledge-fullscreen/);
  expect(await page.evaluate(() => {
    const target = document.fullscreenElement || document.querySelector('.knowledge-fullscreen');
    return target?.contains(document.querySelector('.semantic-composer')) && target?.contains(document.querySelector('.worldview-browser'));
  })).toBe(true);
  const box = (await page.locator(".reasoner-workspace").boundingBox())!;
  expect(box.y).toBe(0); expect(box.height).toBe(await page.evaluate(() => innerHeight));
  await page.screenshot({ path: "test-results/knowledge-fullscreen.png" });
  await page.getByRole("button", { name: "Exit full screen", exact: true }).click();
  await expect(page.locator(".reasoner-workspace")).not.toHaveClass(/knowledge-fullscreen/);
  expect((await page.locator(".worldview-browser").boundingBox())!.height).toBeCloseTo(before.height, 0);
});

test("embedded-browser full screen fallback exits with Escape and restores scrolling", async ({ page }) => {
  await page.addInitScript(() => { Object.defineProperty(HTMLElement.prototype, "requestFullscreen", { configurable: true, value: undefined }); });
  await page.goto("/tests/knowledge.html");
  const overflow = await page.evaluate(() => document.body.style.overflow);
  await page.getByRole("button", { name: "Enter full screen", exact: true }).click();
  await expect(page.locator(".reasoner-workspace")).toHaveClass(/knowledge-fullscreen/);
  await page.keyboard.press("Escape");
  await expect(page.locator(".reasoner-workspace")).not.toHaveClass(/knowledge-fullscreen/);
  expect(await page.evaluate(() => document.body.style.overflow)).toBe(overflow);
});

for (const theme of ["light", "dark"]) test(`metadata labels, rendered documentation and observable details share the loaded snapshot in ${theme}`, async ({ page }) => {
  await page.setViewportSize({ width: theme === "dark" ? 390 : 1440, height: 900 });
  const urn = "k.derived:K_DERIVED_000000123";
  const concept = { urn, name: "K_DERIVED_000000123", type: ["QUALITY", "ABSTRACT"], metadata: { delegate: {
    "klab:displayLabel": "Forest canopy cover", "rdfs:comment": "**Canopy coverage** in a landscape.\n\n- Measured as a percentage\n- See `earth:Forest`\n\n<img src=x onerror=alert(1)>", "dc:source": "Community worldview",
  } }, notifications: [{ message: "Abstract quality declaration" }] };
  let calls = 0; page.on("request", r => { if (r.url().includes("/api/v1/")) calls++; });
  await page.route("**/api/v1/resolve/concept", route => route.fulfill({ json: concept }));
  await page.route("**/api/v1/roles?*", route => route.fulfill({ json: [{ urn: "earth:Habitat", name: "Habitat", type: ["ROLE"], metadata: { "dc:label": "Habitat role" } }] }));
  await page.goto(`/tests/knowledge.html?standalone&graph-only&theme=${theme}`);
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  const node = page.locator(".worldview-canvas .focus");
  await expect(node).toContainText("Forest canopy cover"); await expect(node).toContainText(urn);
  const before = calls;
  await page.getByRole("button", { name: "Show details", exact: true }).click();
  const card = page.getByRole("article", { name: "Observable details" });
  await expect(card.getByRole("heading", { name: "Forest canopy cover" })).toBeVisible();
  await expect(card.locator(".concept-documentation strong")).toHaveText("Canopy coverage");
  await expect(card.locator(".concept-documentation li")).toHaveCount(2);
  await expect(card).toContainText("quality / abstract"); await expect(card).toContainText("Community worldview");
  await expect(card).toContainText("Abstract quality declaration"); await expect(card.getByRole("button", { name: "Habitat role" })).toBeVisible();
  await node.locator("rect").hover({ position: { x: 8, y: 8 } });
  await expect(page.getByRole("tooltip").locator(".concept-documentation strong")).toHaveText("Canopy coverage");
  await expect.poll(async () => {
    const canvas = (await page.locator('.worldview-canvas').boundingBox())!;
    const bounds = (await node.boundingBox())!;
    return bounds.y >= canvas.y && bounds.y + bounds.height <= canvas.y + canvas.height;
  }).toBe(true);
  await page.screenshot({ path: `test-results/knowledge-details-${theme}.png`, fullPage: true });
  await expect(page.locator(".concept-documentation img")).toHaveCount(0);
  expect(calls).toBe(before);
});

test("single clicking inspects locally; double clicking recenters the graph", async ({ page }) => {
  let calls = 0; page.on("request", r => { if (r.url().includes("/api/v1/")) calls++; });
  await page.goto("/tests/knowledge.html?standalone");
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  const forest = page.locator(".worldview-canvas g.semantic-SUBJECT").filter({ hasText: "earth:Forest" });
  const before = calls;
  await forest.click();
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Forest");
  await expect(page.getByRole("combobox", { name: "Focus concept" })).toHaveValue("earth:Landscape");
  expect(calls).toBe(before);
  await forest.dblclick();
  await expect(page.getByRole("combobox", { name: "Focus concept" })).toHaveValue("earth:Forest");
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  expect(calls).toBeGreaterThan(before);
});

test("metadata fallbacks and documentation Markdown never render source HTML or executable links", () => {
  const concept: Concept = { urn: "test:Internal", name: "Internal", metadata: { delegate: { "rdfs:label": "Readable concept", "rdfs:comment": "Documentation" } } };
  expect(conceptLabel(concept)).toBe("Readable concept"); expect(conceptDocumentation(concept)).toBe("Documentation");
  const html = documentationMarkdown('## Detail\n\n**Bold** and *italic*.\n\n1. First\n2. Second\n\n```\n<img onerror=alert(1)>\n```\n\n[unsafe](javascript:alert(1)) [link](https://example.org)');
  expect(html).toContain('<h3>Detail</h3>'); expect(html).toContain('<ol>'); expect(html).toContain('<strong>Bold</strong>');
  expect(html).not.toContain('<img'); expect(html).not.toContain('href="javascript:'); expect(html).toContain('href="https://example.org"');
  expect(documentationMarkdown('- \n\n1. ')).toBe('<p>- </p>\n<p>1. </p>');
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

test("relationship switches retain the IDE families with corrected causal labels and quantitative within", async ({ page }) => {
  const ide = await readFile(new URL("../../../../../../klab-ide/src/main/java/org/integratedmodelling/klab/ide/components/WorldviewGraphModel.java", import.meta.url), "utf8").catch(() => "");
  test.skip(!ide, "The sibling IDE source is needed for this parity check");
  const expected = [...ide.matchAll(/\b([A-Z_]+)\(\s*"([^"]+)",\s*(true|false)/g)]
    .map(match => ({ id: match[1], label: match[1] === "CAUSED" ? "Causing" : match[1] === "CAUSANT" ? "Caused by" : match[2], selected: match[3] === "true" }));
  expect(expected).toHaveLength(30);
  expect(graphRelations.filter(r => r.id !== "QUANTITATIVE_WITHIN").map(({ id, label, selected }) => ({ id, label, selected }))).toEqual(expected);
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".worldview-inspector code")).toHaveText("earth:Subject");
  await page.locator(".worldview-relations summary").click();
  await expect(page.locator('.worldview-relations input[type="checkbox"]')).toHaveCount(31);
  await expect(page.getByRole("checkbox", { name: "Within: quantitative identities", exact: true })).toBeChecked();
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
  for (const endpoint of ["parents", "children", "described", "identities", "attributes", "realms", "roles", "inherent", "adjacent", "caused", "causant", "compresent", "goal", "cooccurent", "relativeTo", "relationshipSources", "relationshipTargets"])
    expect(calls).toContain(`api/v1/${endpoint}?direct=true`);
  expect(calls).not.toContain("api/v1/operands?direct=true");
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

test("quantitative within preserves ordered authority identities on a composed concept", async () => {
  const quantity: Concept = { urn: "derived:Quantity", type: ["QUALITY"], metadata: { delegate: {
    "urn:klab:semantics:quantitative-portion-identity": "CHEMISTRY:Oxygen",
    "urn:klab:semantics:quantitative-whole-identity": "CHEMISTRY:Air",
  } } };
  const cache = new WorldviewCache();
  [quantity, { urn: "CHEMISTRY:Oxygen", type: ["IDENTITY", "AUTHORITY_IDENTITY"] },
    { urn: "CHEMISTRY:Air", type: ["IDENTITY", "AUTHORITY_IDENTITY"] }].forEach(c => cache.concept(c));
  const request: ServiceApi["request"] = async () => { throw new Error("Reuse cached authority identities"); };
  const api: ServiceApi = { request, get: request };
  const graph = await loadNeighborhood(api, quantity.urn, 1, ["QUANTITATIVE_WITHIN", "CONTAINS"], new AbortController().signal, cache);
  expect(graph.links).toEqual([
    { source: quantity.urn, target: "CHEMISTRY:Oxygen", label: "portion identity (quantitative within)" },
    { source: quantity.urn, target: "CHEMISTRY:Air", label: "whole identity (quantitative within)" },
  ]);
  expect(graph.availableRelations).toEqual(["QUANTITATIVE_WITHIN"]);
  quantity.metadata = { "urn:klab:semantics:quantitative-portion-identity": "CHEMISTRY:Oxygen" };
  expect((await loadNeighborhood(api, quantity.urn, 1, ["QUANTITATIVE_WITHIN"], new AbortController().signal, cache)).links).toEqual([]);
});

test("every accessor edge follows its semantic role, including both causal directions", async () => {
  const cases = [
    ["DESCRIBES", "described", "describes"], ["IDENTITIES", "identities", "identity"],
    ["ATTRIBUTES", "attributes", "attribute"], ["REALMS", "realms", "realm"], ["ROLES", "roles", "role"],
    ["INHERENT", "inherent", "of"], ["CAUSED", "caused", "causing"], ["CAUSANT", "causant", "caused by"],
    ["ADJACENT", "adjacent", "adjacent to"], ["COMPRESENT", "compresent", "with"], ["GOAL", "goal", "for"],
    ["COOCCURRENT", "cooccurent", "during"], ["RELATIVE", "relativeTo", "relative to"],
    ["SOURCE", "relationshipSources", "links source"], ["TARGET", "relationshipTargets", "links target"],
    ["IMPLIES", "impliedRoles", "implies"], ["OPERANDS", "operands", "operand"], ["APPLIES", "applicable", "applies to"],
  ];
  const semanticRoles = await readFile(new URL("../../../../../klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/SemanticRole.java", import.meta.url), "utf8");
  expect(semanticRoles).toContain('CAUSED(true, "causing")'); expect(semanticRoles).toContain('CAUSANT(true, "caused by")');
  for (const [relation, endpoint, label] of cases) {
    const root: Concept = { urn: "test:Owner", type: [relation === "IMPLIES" ? "ROLE" : relation === "OPERANDS" ? "UNION" : "QUALITY"] };
    const target: Concept = { urn: "test:Operand" }, cache = new WorldviewCache(); cache.concept(root);
    const request: ServiceApi["request"] = async path => path.split('/').pop()!.split('?')[0] === endpoint ? [target] as any : [] as any;
    const graph = await loadNeighborhood({ request, get: request }, root.urn, 1, [relation], new AbortController().signal, cache);
    expect(graph.links, relation).toEqual([{ source: root.urn, target: target.urn, label }]);
  }
  const owner: Concept = { urn: "test:Child", type: ["SUBJECT"] }, cache = new WorldviewCache(); cache.concept(owner);
  const request: ServiceApi["request"] = async path => [{ urn: path.includes("children?") ? "test:Grandchild" : "test:Parent" }] as any;
  const graph = await loadNeighborhood({ request, get: request }, owner.urn, 1, ["SUBCLASS"], new AbortController().signal, cache);
  expect(graph.links).toEqual([{ source: owner.urn, target: "test:Parent", label: "is" }, { source: "test:Grandchild", target: owner.urn, label: "is" }]);
});

test("navigation discovers hidden relationships, manual filtering works, and cached Back restores the complete view", async ({ page }) => {
  const quantity: Concept = { urn: "derived:Quantity", name: "Oxygen volume within air", type: ["QUALITY"], metadata: {
    "urn:klab:semantics:quantitative-portion-identity": "CHEMISTRY:Oxygen", "urn:klab:semantics:quantitative-whole-identity": "CHEMISTRY:Air",
  } };
  const cause: Concept = { urn: "test:Cause", name: "Cause", type: ["PROCESS"] };
  let calls = 0; page.on("request", r => { if (r.url().includes("/api/v1/")) calls++; });
  await page.route("**/api/v1/resolve/concept", route => {
    const urn = route.request().postData()!;
    return route.fulfill({ json: urn === quantity.urn ? quantity : urn.startsWith("CHEMISTRY:") ? { urn, type: ["IDENTITY", "AUTHORITY_IDENTITY"] } : fixtureResponse(route.request().url(), urn) });
  });
  await page.route("**/api/v1/causant?*", route => route.fulfill({ json: route.request().postDataJSON().urn === quantity.urn ? cause : null }));
  await page.route("**/api/v1/semanticSearch", route => {
    const body = route.request().postDataJSON(), response = fixtureResponse(route.request().url(), body);
    return route.fulfill({ json: { ...response, matches: [{ id: quantity.urn, name: quantity.name, matchType: "CONCEPT" }],
      ...(body.searchMode === "SELECT" ? { declaration: quantity.urn, currentConcept: quantity, code: [{ value: quantity.urn }] } : {}) } });
  });
  await page.goto("/tests/knowledge.html");
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  await page.locator(".worldview-relations summary").click();
  await page.getByRole("checkbox", { name: "Within: quantitative identities", exact: true }).uncheck();
  await page.locator(".worldview-relations summary").click();
  const input = page.getByRole("combobox", { name: "Compose a concept" }); await input.fill("Oxygen");
  await page.getByRole("option", { name: /Oxygen volume/ }).click();
  await expect(page.locator(".worldview-inspector code")).toHaveText(quantity.urn);
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(4);
  await page.locator(".worldview-relations summary").click();
  const within = page.getByRole("checkbox", { name: "Within: quantitative identities", exact: true });
  const causedBy = page.getByRole("checkbox", { name: "Caused by", exact: true });
  await expect(within).toBeChecked(); await expect(causedBy).toBeChecked();
  const beforeFilter = calls; await causedBy.uncheck();
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(3); expect(calls).toBe(beforeFilter);
  await page.locator(".worldview-relations summary").click();
  await page.getByRole("combobox", { name: "Focus concept" }).selectOption("CHEMISTRY:Air");
  await expect(page.locator(".worldview-browser")).toHaveAttribute("aria-busy", "false");
  const beforeBack = calls; await page.getByRole("button", { name: "Back", exact: true }).click();
  await expect(page.locator(".worldview-canvas .sprotty-node")).toHaveCount(4); expect(calls).toBe(beforeBack);
  await page.locator(".worldview-relations summary").click(); await expect(causedBy).toBeChecked();
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
  await page.getByRole("button", { name: "Expand tree", exact: true }).click();
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
