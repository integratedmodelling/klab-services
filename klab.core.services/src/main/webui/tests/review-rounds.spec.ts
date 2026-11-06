import { test, expect } from "@playwright/test";

test("dashboard transport reads the server's plain Base64 attachment response", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1");
  await page.route("**/fixture-attachment", async route => {
    expect(route.request().headers().accept).toBe("text/plain");
    await route.fulfill({ status: 200, contentType: "text/plain", body: "AQID" });
  });
  const text = await page.evaluate(async () => {
    const module = await import(new URL("/src/services/api.ts", window.location.origin).href);
    return module.serviceApi.text("fixture-attachment");
  });
  expect(text).toBe("AQID");
});

test("community round view works without stage access and filters by proposal and contributor", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r2"/);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveAttribute("readonly");
  await expect(page.getByRole("button", { name: "Open reviewer round" })).toHaveCount(0);
  await expect(page.getByLabel("Feedback iteration").locator("option")).toHaveCount(2);
  await page.getByLabel("Feedback proposal").selectOption("cycle-1");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r1"/);
  expect(page.url()).toContain("cycle=cycle-1"); expect(page.url()).toContain("round=community-1");
  await page.reload();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r1"/);
  await expect(page.getByText("Alice feedback", { exact: true })).toBeVisible();
  await expect(page.getByText("Bob feedback", { exact: true })).toBeVisible();
  await page.getByLabel("Feedback contributor").selectOption("Alice");
  await expect(page.getByText("Showing 1 of 4 visible contributions.", { exact: false })).toBeVisible();
  await expect(page.getByText("Bob feedback", { exact: true })).toHaveCount(0);
  await page.getByLabel("Feedback proposal").selectOption("cycle-2");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r2"/);
  await expect(page.getByText("Second proposal feedback", { exact: true })).toBeVisible();
});

test("private drafts resume and submitting uses the displayed revision without replacing history", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1");
  await page.getByLabel("Feedback proposal").selectOption("cycle-1");
  await page.getByLabel("Your contribution draft").selectOption("own-draft");
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("My private draft");
  await page.getByLabel("Your feedback", { exact: true }).fill("Revised private draft");
  await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Save private draft", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save private draft", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Submit feedback", exact: true }).click();
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("");
  await expect(page.getByLabel("Your contribution draft").locator("option")).toHaveCount(2);
  await expect(page.getByText("My second private draft", { exact: true })).toBeVisible();
  const calls = await page.evaluate(() => (window as unknown as { calls: unknown[] }).calls);
  expect(calls[0]).toMatchObject({ command: { operation: "SAVE_CONTRIBUTION", expectedRevision: 7, contributionId: "own-draft", submit: false } });
  expect(calls[1]).toMatchObject({ command: { expectedRevision: 8, contributionId: "own-draft", submit: true } });
  await expect(page.getByText("Alice feedback", { exact: true })).toBeVisible();
});

test("conflicts keep unsaved feedback and never automatically retry", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1&conflict");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r2"/);
  await page.getByLabel("Your feedback", { exact: true }).fill("Keep this feedback");
  await page.getByRole("button", { name: "Submit feedback", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("409 Conflict");
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("Keep this feedback");
  const calls = await page.evaluate(() => (window as unknown as { calls: unknown[] }).calls);
  expect(calls).toHaveLength(1);
});
