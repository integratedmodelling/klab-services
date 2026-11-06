import { test, expect } from "@playwright/test";

test("closed-cycle editor can inspect and explicitly retry a minimal digest receipt", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&cycle=cycle-1&round=community-1&archive");
  await page.getByRole("button", { name: "Check digest receipt" }).click();
  await expect(page.getByText("Digest archive: BLOCKED.")).toBeVisible();
  await page.getByRole("button", { name: "Retry digest archive" }).click();
  await expect(page.getByText("abc123")).toBeVisible();
  await expect(page.getByRole("button", { name: "Retry digest archive" })).toBeDisabled();
});

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

test("alternative proposals resume privately and filter beside the unchanged candidate", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1");
  const source = "ontology test;\n// proposed source";
  const proposal = '{"proposal":{"id":"alternative","revision_id":"r3"}}';
  await page.getByLabel("Your alternative proposal", { exact: true }).fill(proposal);
  await page.getByLabel("Include alternative ontology source").check();
  await page.getByLabel("Your alternative ontology source", { exact: true }).fill(source);
  await page.getByRole("button", { name: "Save private draft", exact: true }).click();
  await expect(page.getByRole("button", { name: "Save private draft", exact: true })).toBeDisabled();
  await page.getByLabel("Your contribution draft").selectOption("");
  await expect(page.getByLabel("Your alternative proposal", { exact: true })).toHaveValue("");
  await page.getByLabel("Your contribution draft").selectOption("new-8");
  await expect(page.getByLabel("Your alternative proposal", { exact: true })).toHaveValue(proposal);
  await expect(page.getByLabel("Your alternative ontology source", { exact: true })).toHaveValue(source);
  await page.getByRole("button", { name: "Submit feedback", exact: true }).click();
  await page.getByLabel("Contribution proposal", { exact: true }).selectOption("new-8");
  await page.getByText("Alternative alternative", { exact: false }).click();
  await expect(page.getByLabel("Proposal by community-user", { exact: true })).toHaveValue(proposal);
  await expect(page.getByLabel("Ontology source by community-user", { exact: true })).toHaveValue(source);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r2"/);
  const calls = await page.evaluate(() => (window as unknown as { calls: unknown[] }).calls);
  expect(calls[1]).toMatchObject({ command: { contributionId: "new-8", submit: true, proposal: { document: proposal, ontologySource: source } } });
});

test("external round closure preserves an unsaved draft for download and explicit discard", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1");
  await page.getByLabel("Your feedback", { exact: true }).fill("Keep feedback when the round closes");
  await page.getByLabel("Your alternative proposal", { exact: true }).fill('{"unfinished":"private"}');
  await page.evaluate(() => (window as unknown as { reviewFixture: { closeRound(id: string): void } }).reviewFixture.closeRound("community-2"));
  await page.getByRole("button", { name: "Refresh rounds", exact: true }).click();
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("Keep feedback when the round closes");
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveAttribute("readonly");
  await expect(page.getByRole("button", { name: "Submit feedback", exact: true })).toBeDisabled();
  const downloadEvent = page.waitForEvent("download");
  await page.getByRole("button", { name: "Download private feedback draft", exact: true }).click();
  const download = await downloadEvent; const stream = await download.createReadStream(); let content = "";
  for await (const chunk of stream!) content += chunk.toString();
  expect(content).toContain("Keep feedback when the round closes"); expect(content).toContain("unfinished");
  await page.getByRole("button", { name: "Discard unsaved feedback", exact: true }).click();
  await expect(page.getByLabel("Feedback proposal")).toBeEnabled();
  await page.getByLabel("Feedback proposal").selectOption("cycle-1");
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("");
});

test("revoked round access clears cached shared feedback and candidate while retaining own unsaved text", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1&cycle=cycle-1&round=community-1");
  await expect(page.getByText("Alice feedback", { exact: true })).toBeVisible();
  await page.getByLabel("Your feedback", { exact: true }).fill("My private work after revocation");
  await page.evaluate(() => (window as unknown as { reviewFixture: { revoke(): void } }).reviewFixture.revoke());
  await page.getByRole("button", { name: "Refresh rounds", exact: true }).click();
  await expect(page.getByText("Alice feedback", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue("");
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("My private work after revocation");
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveAttribute("readonly");
  await expect(page.getByRole("button", { name: "Submit feedback", exact: true })).toBeDisabled();
});

test("a denied refresh invalidates a pending candidate response without leaving the page busy", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1&cycle=cycle-1&round=community-1&delay-candidate");
  await expect.poll(() => page.evaluate(() => (window as unknown as { reviewFixture: { pendingCandidates(): number } }).reviewFixture.pendingCandidates())).toBeGreaterThan(0);
  await page.evaluate(() => (window as unknown as { reviewFixture: { revoke(): void } }).reviewFixture.revoke());
  await page.getByRole("button", { name: "Refresh rounds", exact: true }).click();
  await expect(page.getByText("Alice feedback", { exact: true })).toHaveCount(0);
  await page.evaluate(async () => {
    (window as unknown as { reviewFixture: { releaseCandidates(): void } }).reviewFixture.releaseCandidates();
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
  });
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue("");
  await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeEnabled();
});

test("a partially revoked view cannot move private peer work into an admitted community iteration", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1&cycle=cycle-1&round=community-1&overlap");
  await page.getByLabel("Your feedback", { exact: true }).fill("Private peer work must keep its context");
  await page.evaluate(() => (window as unknown as { reviewFixture: { hideRound(id: string): void } }).reviewFixture.hideRound("community-1"));
  await page.getByRole("button", { name: "Refresh rounds", exact: true }).click();
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveAttribute("readonly");
  await expect(page.getByRole("button", { name: "Submit feedback", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("Private peer work must keep its context");
  await page.getByRole("button", { name: "Discard unsaved feedback", exact: true }).click();
  await page.getByLabel("Feedback iteration").selectOption("public-1");
  await expect(page.getByLabel("Your feedback", { exact: true })).toHaveValue("");
});

test("an unavailable stage deep link never silently opens a different admitted iteration", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1&cycle=cycle-1&round=restricted-stage");
  await expect(page.getByText("The requested iteration is unavailable", { exact: false })).toBeVisible();
  await expect(page.getByText("Alice feedback", { exact: true })).toHaveCount(0);
  expect(page.url()).toContain("round=restricted-stage");
  await page.getByLabel("Feedback iteration").selectOption("community-1");
  await expect(page.getByText("Alice feedback", { exact: true })).toBeVisible();
});

test("an unavailable cycle link requires an explicit admitted selection", async ({ page }) => {
  await page.goto("/tests/review-rounds.html?flow=flow-1&stage=stage-1&cycle=restricted-cycle&round=restricted-stage");
  await expect(page.getByText("The requested review cycle is unavailable", { exact: false })).toBeVisible();
  await expect(page.getByText("Second proposal feedback", { exact: true })).toHaveCount(0);
  expect(page.url()).toContain("cycle=restricted-cycle");
  await page.getByLabel("Feedback proposal").selectOption("cycle-1");
  await expect(page.getByText("Alice feedback", { exact: true })).toBeVisible();
});
