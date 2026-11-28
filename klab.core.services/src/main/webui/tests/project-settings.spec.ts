import { expect, test, type Page } from "@playwright/test";

const metadata = {
  "citation": "Original",
  "citation.title": "A description long enough to open in a separate viewer without widening the metadata card.",
  "citation.year": 2026,
  "public": false,
  "missing": null,
  "authors": [{ name: "A. Author" }],
};

async function openProject(page: Page, editable = true, theme = "light") {
  let savedMetadata = metadata;
  const updateRequests: { body: string; path: string }[] = [];
  const requests: string[] = [];
  const lock = { editable, canLock: true, locked: editable, owned: editable };
  await page.route("**/api/v1/workspaces**", async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    requests.push(method + " " + path);
    switch (path.split("/").at(-1)) {
      case "lock": return route.fulfill({ json: lock });
      case "navigation": return route.fulfill({ json: [{ urn: "sample.project", children: [] }] });
      case "project":
        if (method === "PUT") {
          updateRequests.push({ body: request.postData()!, path });
          savedMetadata = request.postDataJSON().settings.metadata;
          return route.fulfill({ json: [] });
        }
        return route.fulfill({ json: { urn: "sample.project", description: "Example project", version: "1.0",
          metadata: savedMetadata, documents: [], lock, administrator: false, snapshot: false,
          canManageRepository: false, settingsRevision: "revision-1" } });
      default: return route.fulfill({ json: [{ urn: "sample", projects: ["sample.project"] }] });
    }
  });
  await page.goto("/tests/project-settings.html?theme=" + theme);
  await page.getByText("sample.project", { exact: true }).click();
  await expect(page.getByRole("heading", { name: "Project settings" })).toBeVisible();
  return { requests, updateRequests };
}

test("saves and reloads the metadata with the existing project settings contract", async ({ page }) => {
  const { requests, updateRequests } = await openProject(page);
  await expect(page.getByRole("button", { name: "Save settings" })).toBeDisabled();
  const start = requests.length;
  const updated = { ...metadata, "citation": "Updated" };
  await page.getByRole("button", { name: "Edit value for citation", exact: true }).click();
  const value = page.getByRole("textbox", { name: "Value for citation", exact: true });
  await expect(value).toBeFocused();
  await value.fill("Updated");
  await value.press("Enter");
  await page.getByRole("button", { name: "Save settings" }).click();
  await expect(page.getByText("Project settings saved.")).toBeVisible();
  await expect.poll(() => requests.slice(start)).toEqual(["PUT /api/v1/workspaces/project", "GET /api/v1/workspaces/project"]);
  const expected = JSON.stringify({ revision: "revision-1", settings: { metadata: updated,
    permissions: null, definedWorldview: null, snapshot: null } });
  expect(updateRequests).toEqual([{ body: expected, path: "/api/v1/workspaces/project" }]);
  expect(Buffer.byteLength(expected)).toBe(325);
  await expect(page.getByText("Updated", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Save settings" })).toBeDisabled();
});

test("browses metadata without marking settings dirty", async ({ page }, info) => {
  const { updateRequests } = await openProject(page);
  const card = page.getByRole("region", { name: "Project metadata" });
  await card.getByRole("button", { name: "Collapse all paths" }).click();
  await expect(card.getByText("year", { exact: true })).toBeHidden();
  await expect(card.getByText("Original", { exact: true })).toBeVisible();
  await card.getByRole("button", { name: "Expand all paths" }).click();
  await expect(card.getByText("year", { exact: true })).toBeVisible();

  const viewer = card.getByRole("button", { name: "View full value for citation.title" });
  await viewer.click();
  const dialog = card.getByRole("dialog", { name: "citation.title" });
  await expect(dialog).toContainText(metadata["citation.title"]);
  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(viewer).toBeFocused();

  await card.getByRole("button", { name: "Switch to flat view" }).click();
  await expect(card.getByText("citation.year", { exact: true })).toBeVisible();
  await card.getByRole("button", { name: "Edit value for citation.year" }).click();
  const year = card.getByRole("textbox", { name: "Value for citation.year" });
  await year.fill("invalid");
  await year.press("Enter");
  await expect(card.getByRole("alert")).toHaveText("Enter a finite number.");
  await year.press("Escape");
  await expect(card.getByText("2026", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "Save settings" })).toBeDisabled();
  expect(updateRequests).toHaveLength(0);
  await page.screenshot({ path: info.outputPath("project-settings-light.png") });
});

test("can add, rename, and remove fields and edit structured values", async ({ page }) => {
  const { updateRequests } = await openProject(page);
  await page.getByRole("button", { name: "Add field", exact: true }).click();
  const add = page.getByRole("dialog", { name: "Add metadata field" });
  await add.getByLabel("Key").fill("citation.subtitle");
  await add.getByLabel("Value", { exact: true }).fill("An example");
  await add.getByRole("button", { name: "Add field" }).click();

  const edit = page.getByRole("dialog", { name: "Edit metadata field" });
  await page.getByRole("button", { name: "Manage field authors" }).click();
  await edit.getByLabel("Key").fill("contributors");
  await edit.getByLabel("Value (JSON)").fill('[{"name":"A. Author"},{"name":"B. Author"}]');
  await edit.getByRole("button", { name: "Apply changes" }).click();
  await page.getByRole("button", { name: "Manage field missing" }).click();
  await edit.getByRole("button", { name: "Remove field" }).click();

  await page.getByRole("button", { name: "Edit value for public" }).click();
  await page.getByRole("switch", { name: "Value for public" }).check();
  await page.getByRole("button", { name: "Apply value" }).click();
  await page.getByRole("button", { name: "Save settings" }).click();
  await expect(page.getByText("Project settings saved.")).toBeVisible();
  expect(updateRequests).toHaveLength(1);
  const result = JSON.parse(updateRequests[0].body).settings.metadata;
  expect(result).toEqual({ "citation": "Original", "citation.title": metadata["citation.title"],
    "citation.year": 2026, "citation.subtitle": "An example", public: true,
    contributors: [{ name: "A. Author" }, { name: "B. Author" }] });
});

test("shows read-only metadata at a narrow viewport", async ({ page }, info) => {
  await page.setViewportSize({ width: 390, height: 850 });
  await openProject(page, false, "dark");
  const card = page.getByRole("region", { name: "Project metadata" });
  await expect(card).toBeVisible();
  await expect(card.getByRole("button", { name: "Add field" })).toHaveCount(0);
  await expect(card.getByRole("button", { name: /Manage field|Edit value for/ })).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth)).toBe(390);
  await card.getByRole("button", { name: "View value for authors" }).click();
  await expect(page.getByRole("dialog", { name: "authors" })).toContainText("A. Author");
  await page.keyboard.press("Escape");
  await page.screenshot({ path: info.outputPath("project-settings-dark.png") });
});

test("the shared card supports custom rendering and callback-controlled enum edits", async ({ page }) => {
  await page.goto("/tests/project-settings.html?standalone");
  const card = page.getByRole("region", { name: "Parameters" });
  await expect(card.getByText("hidden", { exact: true })).toHaveCount(0);
  await expect(card.getByText("Custom value")).toBeVisible();
  await card.getByRole("button", { name: "Edit value for status" }).click();
  const value = card.getByRole("combobox", { name: "Value for status" });
  await value.selectOption("Decline");
  await card.getByRole("button", { name: "Apply value" }).click();
  await expect(card.getByRole("alert")).toHaveText("This change was declined.");
  await value.selectOption("Published");
  await card.getByRole("button", { name: "Apply value" }).click();
  await expect(card.getByText("Published", { exact: true })).toBeVisible();
});
