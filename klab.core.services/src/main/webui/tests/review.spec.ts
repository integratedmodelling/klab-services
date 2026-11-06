import { test, expect } from "@playwright/test";

async function prepare(page: import("@playwright/test").Page) {
  for (const name of ["Proposal ID", "Revision ID", "Domain", "Purpose", "Target community"]) await page.getByLabel(name, { exact: true }).fill(name === "Revision ID" ? "r1" : "test");
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/test-proposal/);
}
test("stage URL exposes explicit editor action with revision and preserves authored drafts", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1");
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toBeDisabled();
  await prepare(page);
  const calls = await page.evaluate(() => (window as unknown as { calls: unknown[] }).calls);
  expect(calls[0]).toMatchObject({ path: "api/v1/flows/flow-1/states/stage-1/actions/prepare-review-draft", command: { expectedRevision: 3, parameters: { requested_tier: 1, revision_id: "r1" } } });
  expect(page.url()).toContain("stage=stage-1");
  const candidate = page.getByLabel("Candidate proposal JSON");
  await candidate.fill('{"proposal":{"id":"test-proposal","revision_id":"r2","status":"draft"}}');
  await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toHaveCount(0);
  await page.getByRole("button", { name: "Save new draft attachment" }).click();
  await expect(candidate).toHaveValue(/"r2"/);
  await expect(page.getByLabel("Proposal attachment", { exact: true }).locator("option")).toHaveCount(3);
  await page.getByLabel("Proposal attachment", { exact: true }).selectOption("proposal-1");
  await expect(candidate).toHaveValue(/"r1"/);
});
test("read access alone offers no editor action and request conflicts remain visible", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&reader");
  await expect(page.getByRole("heading", { name: "Current candidate draft" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveAttribute("readonly");
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&conflict");
  for (const name of ["Proposal ID", "Revision ID", "Domain", "Purpose", "Target community"]) await page.getByLabel(name, { exact: true }).fill("test");
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await expect(page.getByRole("alert")).toHaveText("409 Conflict");
});
