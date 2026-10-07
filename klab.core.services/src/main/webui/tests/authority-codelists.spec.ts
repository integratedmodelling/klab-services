import { test, expect } from "@playwright/test";
test("review transmits exact proposal and displayed revision", async ({ page }) => {
  await page.goto("/tests/authority-codelists.html");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await page.getByRole("button", { name: "Review", exact: true }).click();
  await page.getByLabel("Approved alias").fill("DomesticCat");
  await page.getByLabel("Reason or suggested alternative").fill("Use the preferred community name");
  await page.getByRole("button", { name: "Accept", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Pending proposals (0)" })).toBeVisible();
  const calls = await page.evaluate(() => (window as unknown as { calls: unknown[] }).calls);
  expect(calls[1]).toMatchObject({ operation: "REVIEW", authority: "TAXA", namespace: "taxonomy.species",
    proposalId: "p1", expectedRevision: 7, decision: "ACCEPT", approvedAlias: "DomesticCat" });
});
test("reader does not see review controls", async ({ page }) => {
  await page.goto("/tests/authority-codelists.html?reader");
  await expect(page.getByText("Administrator permission is required to review proposals.")).toBeVisible();
  await expect(page.getByRole("button", { name: "Refresh" })).toHaveCount(0);
});

test("configured lists support direct edits while community entries are read-only", async ({ page }) => {
  await page.goto("/tests/authority-codelists.html");
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByLabel("Authority", { exact: true })).toHaveValue("TAXA");
  await expect(page.getByLabel("Codelist", { exact: true })).toHaveValue("taxonomy.species");
  const community = page.getByRole("row").filter({ hasText: "CommunityCat" });
  await expect(community.getByText("Read-only")).toBeVisible();
  await expect(community.getByRole("button")).toHaveCount(0);
  await page.getByLabel("Alias", { exact: true }).fill("NewCat");
  await page.getByLabel("Authority code", { exact: true }).fill("3DXV3");
  await page.getByRole("button", { name: "Add code", exact: true }).click();
  await page.getByRole("row").filter({ hasText: "ManagedCat" }).getByRole("button", { name: "Edit", exact: true }).click();
  await page.getByLabel("Alias", { exact: true }).fill("RenamedCat");
  await page.getByRole("button", { name: "Save changes", exact: true }).click();
  page.on("dialog", dialog => dialog.accept());
  await page.getByRole("row").filter({ hasText: "ManagedCat" }).getByRole("button", { name: "Remove", exact: true }).click();
  const calls = await page.evaluate(() => (window as unknown as { calls: unknown[] }).calls);
  expect(calls).toEqual(expect.arrayContaining([
    expect.objectContaining({ operation: "CREATE", authority: "TAXA", namespace: "taxonomy.species", alias: "NewCat", identity: "3DXV3", expectedRevision: 7 }),
    expect.objectContaining({ operation: "UPDATE", alias: "ManagedCat", approvedAlias: "RenamedCat", expectedRevision: 7 }),
    expect.objectContaining({ operation: "DELETE", alias: "ManagedCat", expectedRevision: 7 })
  ]));
});
