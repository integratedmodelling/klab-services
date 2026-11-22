import { expect, test, type Page } from "@playwright/test";

async function dashboard(page: Page, administrator: boolean, importSchemata = {}) {
  // Exercise the real shell and API client with a fixture identity in place of the hub.
  await page.route(/\/src\/services\/auth\.ts(?:\?.*)?$/, route => route.fulfill({
    contentType: "text/javascript",
    body: `
      export const authState = {ready: true, enabled: true, authenticated: true, username: "fixture"};
      export async function initializeAuthentication() {}
      export async function accessToken() { return "webui_fixture"; }
      export function invalidateServiceAuthentication() {}
      export async function login() {}
      export async function logout() {}
    `,
  }));
  await page.route("**/public/ui/config", route => route.fulfill({ json: {
    serviceType: "RUNTIME", title: "k.LAB Runtime service", logoUrl: "klab-logo.svg",
    authentication: { enabled: true }, panels: [], pages: [], links: [], modules: {},
  } }));
  await page.route("**/public/status", route => route.fulfill({ json: { operational: true } }));
  await page.route("**/public/capabilities", route => route.fulfill({
    json: { permissions: administrator ? ["ADMINISTER"] : ["READ"], importSchemata,
      components: [
        { id: "fixture.component", version: { major: 1, minor: 2, build: 3 }, importType: "DEPENDENCY" },
        { id: "fixture.component", version: "2.0.0", importType: "DEPENDENCY" },
        { id: "fixture.builtin", version: "1.0.0", importType: "BUILT_IN" },
      ] },
  }));
  await page.route(/\/ui\/(administration|settings|credentials|import)$/, route => route.fulfill({
    path: "tests/theme.html", contentType: "text/html",
  }));
}

test("service settings save through jobs, leave SMTP passwords write-only, and read logs by cursor", async ({ page }) => {
  test.setTimeout(60_000);
  await dashboard(page, true);
  const writes: unknown[] = [];
  let jobPolls = 0;
  let results = 0;
  await page.route("**/settings?view=editor", route => {
    expect(route.request().headers().authorization).toBe("Bearer webui_fixture");
    return route.fulfill({ json: [
      { name: "MAX_CONCURRENT_SHARD_TASKS", page: "RUNTIME", description: "Maximum active shard tasks",
        kind: "integer", value: 0, configured: false },
      { name: "EMAIL_PASSWORD", page: "EMAIL", description: "SMTP authentication password",
        kind: "password", value: null, configured: true },
      { name: "UPDATE_COMPONENT", page: "SERVICES", description: "Update an installed component",
        kind: "action", value: null, configured: false },
    ] });
  });
  await page.route("**/set/*", route => {
    const request = route.request();
    writes.push(request.headers()["content-type"].startsWith("text/plain") ? request.postData() ?? "" : request.postDataJSON());
    return route.fulfill({ json: writes.length });
  });
  await page.route("**/jobs/status/*", route =>
    route.fulfill({ json: { status: ++jobPolls === 1 ? "WAITING" : "FINISHED" } }));
  await page.route("**/jobs/retrieve/4", route => {
    results++;
    return route.fulfill({ json: { result: false, message: "Component was not found." } });
  });
  await page.route("**/credentials", route =>
    route.request().isNavigationRequest() ? route.fallback() : route.fulfill({ json: [] }));
  await page.route("**/credentials/schemata", route => route.fulfill({ json: [] }));
  await page.route("**/admin/logs/capabilities", route =>
    route.fulfill({ json: { streamIds: ["service"], defaultLimit: 200, sourceZone: "UTC",
      redactionPolicy: "vetted-diagnostics-v1" } }));
  await page.route("**/admin/logs/query", route => {
    const { streamId, cursor, follow, includeMetadata } = route.request().postDataJSON();
    expect(streamId).toBe("service");
    expect(includeMetadata).toBe(true);
    if (cursor === "page-2") {
      expect(follow).toBe(true);
      return route.fulfill({ status: 410 });
    }
    expect(follow).toBe(false);
    return route.fulfill({ json: { caughtUp: !!cursor, nextCursor: cursor ? "page-2" : "page-1", omittedEvents: 0,
      records: [{ eventId: "event-1", level: "INFO", logger: "Runtime", timestamp: "2026-11-22T08:00:00Z",
        message: cursor ? "Service started" : "Message withheld by diagnostic policy", partial: !cursor, truncated: false }] } });
  });

  await page.goto("/tests/theme.html");
  await page.getByRole("link", { name: "Administration", exact: true }).click();
  await expect(page).toHaveURL(/\/ui\/administration$/);
  await page.getByLabel("Max concurrent shard tasks", { exact: true }).fill("8");
  await page.getByRole("button", { name: "Save Max concurrent shard tasks" }).click();
  await expect(page.getByText("Saved.", { exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "Email", exact: true }).click();
  await expect(page.getByLabel("SMTP password", { exact: true })).toHaveValue("");
  await page.getByLabel("SMTP password", { exact: true }).fill("fixture-replacement");
  await page.getByRole("button", { name: "Save SMTP password" }).click();
  await expect(page.getByLabel("SMTP password", { exact: true })).toHaveValue("");
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Clear password" }).click();
  await expect(page.getByText("Password cleared.")).toBeVisible();
  expect(writes).toEqual([8, "fixture-replacement", ""]);
  expect(jobPolls).toBe(4);
  expect(results).toBe(0);

  await page.getByRole("tab", { name: "General" }).click();
  await expect(page.getByText("fixture.component", { exact: true })).toBeVisible();
  await page.getByLabel("Version", { exact: true }).click();
  await page.getByRole("option", { name: "1.2.3", exact: true }).click();
  page.once("dialog", dialog => dialog.accept());
  await page.getByRole("button", { name: "Update component", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Component was not found.");
  expect(writes[3]).toEqual({ component: "fixture.component", version: "1.2.3" });
  expect(results).toBe(1);

  await page.getByRole("tab", { name: "Logs" }).click();
  await expect(page.getByText("Message withheld by diagnostic policy")).toBeVisible();
  await page.getByRole("button", { name: "Load more entries" }).click();
  await expect(page.getByText("Service started")).toBeVisible();
  await expect(page.getByText("Message withheld by diagnostic policy")).toHaveCount(0);
  await page.getByRole("button", { name: "Refresh log" }).click();
  await expect(page.getByRole("alert")).toContainText("Start a new query");
  await page.getByRole("button", { name: "Start new query" }).click();
  await expect(page.getByText("Message withheld by diagnostic policy")).toBeVisible();

  await page.setViewportSize({ width: 320, height: 900 });
  await page.getByRole("button", { name: "Switch to dark theme" }).click();
  for (const tab of ["General", "Email", "Credentials", "Logs", "Import"]) {
    await page.getByRole("tab", { name: tab, exact: true }).click();
    await expect(page.getByRole("tab", { name: tab, exact: true })).toHaveAttribute("aria-selected", "true");
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(320);
  }
  await page.getByRole("button", { name: "Service interfaces" }).click();
  await expect(page.getByRole("link", { name: "Administration", exact: true })).toBeVisible();
  await page.goto("/ui/settings", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("tab", { name: "General", selected: true })).toBeVisible();
  await page.goto("/ui/credentials", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("tab", { name: "Credentials", selected: true })).toBeVisible();
  await expect(page.getByText("No credential sets configured.")).toBeVisible();
});

test("a user without ADMINISTER cannot open administration or its legacy routes", async ({ page }) => {
  await dashboard(page, false);
  let administrativeRequests = 0;
  await page.route(/\/(settings\?view=editor|credentials(?:\/schemata)?|admin\/logs\/.*|import\/.*)$/, route => {
    if (route.request().isNavigationRequest()) return route.fallback();
    administrativeRequests++;
    return route.fulfill({ status: 403 });
  });
  for (const path of ["/ui/administration", "/ui/settings", "/ui/credentials", "/ui/import"]) {
    // Match the service API only, leaving the shell document routes to the dashboard fixture.
    await page.goto(path, { waitUntil: "domcontentloaded" });
    await expect(page.getByText("Administrator permission is required to manage this service.")).toBeVisible();
    await expect(page.getByRole("link", { name: "Administration", exact: true })).toHaveCount(0);
  }
  expect(administrativeRequests).toBe(0);
});

test("imports advertised property and file formats through the service job API", async ({ page }) => {
  await dashboard(page, true, {
    project: [
      { schemaId: "project.git.import", type: "PROPERTIES", description: "Register a Git project",
        properties: { url: { type: "TEXT", optional: false }, workspace: { type: "TEXT", optional: true, defaultValue: "local" } } },
      { schemaId: "project.zip.import", type: "STREAM", description: "Upload a project archive",
        mediaTypes: ["application/zip"], extensions: ["zip"] },
    ],
  });
  const imports: string[] = [];
  await page.route("**/settings?view=editor", route => route.fulfill({ json: [] }));
  await page.route("**/import/**", route => {
    const request = route.request();
    expect(request.headers().authorization).toBe("Bearer webui_fixture");
    const pathname = new URL(request.url()).pathname;
    imports.push(pathname);
    if (pathname.includes("project.git")) {
      expect(request.postDataJSON()).toEqual({ url: "https://example.test/project.git", workspace: "local" });
    } else {
      expect(request.headers()["content-type"]).toMatch(/^multipart\/form-data; boundary=/);
      expect(request.postData()).toContain('name="file"; filename="project.zip"');
    }
    return route.fulfill({ json: imports.length });
  });
  await page.route("**/jobs/status/*", route => route.fulfill({ json: { status: "FINISHED" } }));
  await page.route("**/jobs/retrieve/1", route => route.fulfill({ json: {
    empty: false, results: [{ resourceUrn: "project:fixture" }], notifications: [{ level: "Info", message: "Project registered" }],
  } }));
  await page.route("**/jobs/retrieve/2", route => route.fulfill({ json: {
    empty: true, notifications: [{ level: "Error", message: "Invalid project archive" }],
  } }));
  await page.route("https://files.example.test/project.zip", route => route.fulfill({
    headers: { "Access-Control-Allow-Origin": "*" }, contentType: "application/zip", body: "fixture",
  }));
  await page.route("**/jobs/retrieve/3", route => route.fulfill({ json: {
    empty: false, results: [{ resourceUrn: "project:from-url" }],
  } }));
  await page.goto("/tests/theme.html");
  await page.getByRole("link", { name: "Administration", exact: true }).click();
  await page.getByRole("tab", { name: "Import", exact: true }).click();
  await expect(page).toHaveURL(/\/ui\/administration$/);
  await expect(page.getByRole("link", { name: "Import", exact: true })).toHaveCount(0);
  await page.getByLabel("Import format").click();
  await page.getByRole("option", { name: "project.git.import" }).click();
  await expect(page.getByLabel("Workspace (optional)", { exact: true })).toHaveValue("local");
  await page.getByLabel("URL", { exact: true }).fill("https://example.test/project.git");
  await page.getByRole("button", { name: "Start import" }).click();
  await expect(page.getByText("project:fixture")).toBeVisible();
  await expect(page.getByText("Project registered")).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByLabel("Import format").click();
  await page.getByRole("option", { name: "project.zip.import" }).click();
  await page.locator("input[type=file]").setInputFiles({
    name: "project.zip", mimeType: "application/zip", buffer: Buffer.from("fixture"),
  });
  await page.getByRole("button", { name: "Start import" }).click();
  await expect(page.getByText("Invalid project archive")).toBeVisible();
  await page.getByText("Use a file URL", { exact: true }).click();
  await page.getByLabel("File URL", { exact: true }).fill("https://files.example.test/project.zip");
  await page.getByRole("button", { name: "Start import" }).click();
  await expect(page.getByText("project:from-url")).toBeVisible();
  expect(imports).toEqual(["/import/project.git.import/X:X:X:X", "/import/project.zip.import/X:X:X:X", "/import/project.zip.import/X:X:X:X"]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(390);
  await page.goto("/ui/import", { waitUntil: "domcontentloaded" });
  await expect(page.getByRole("tab", { name: "Import", selected: true })).toBeVisible();
});
