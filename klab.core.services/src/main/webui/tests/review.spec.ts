import { test, expect } from "@playwright/test";

async function prepare(page: import("@playwright/test").Page) {
  for (const name of ["Proposal ID", "Revision ID", "Domain", "Purpose", "Target community"]) await page.getByLabel(name, { exact: true }).fill(name === "Revision ID" ? "r1" : "test");
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/test-proposal/);
}

async function waitingFor(page: import("@playwright/test").Page, step: string) {
  await page.waitForFunction(name => !!(window as any).pendingReviewSteps[name], step);
}
async function release(page: import("@playwright/test").Page, step: string) {
  await waitingFor(page, step);
  await page.evaluate(name => (window as any).releaseReviewStep(name), step);
}
async function fillPreparation(page: import("@playwright/test").Page) {
  for (const name of ["Proposal ID", "Revision ID", "Domain", "Purpose", "Target community"]) {
    await page.getByLabel(name, { exact: true }).fill(name === "Revision ID" ? "r1" : "test");
  }
}

test("invalid requested tiers never submit or mutate and correction prepares exactly once", async ({ page }, testInfo) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&pause=prepare");
  await fillPreparation(page);
  const tier = page.getByLabel("Requested tier", { exact: true });
  const prepare = page.getByRole("button", { name: "Prepare review draft", exact: true });
  const feedback = page.locator("#prepare-review-draft-requested_tier-error");
  for (const invalid of ["0", "-1", "1.5", "", "2147483648"]) {
    await tier.fill(invalid);
    await expect(tier).toHaveAttribute("aria-invalid", "true");
    await expect(feedback).toContainText("Requested tier must be an integer of 1 or greater");
    await expect(prepare).toBeDisabled();
    // Even a dispatched click bypassing the disabled control must not reach the API.
    await prepare.dispatchEvent("click");
    await prepare.dispatchEvent("click");
    await expect(page.getByLabel("Revision ID", { exact: true })).toHaveValue("r1");
    await expect(page.getByLabel("Purpose", { exact: true })).toHaveValue("test");
    expect(await page.evaluate(() => ({ calls: (window as any).calls.length,
      revision: (window as any).reviewFixtureFlow.revision,
      files: (window as any).reviewFixtureFlow.states["stage-1"].attachments.length })))
      .toEqual({ calls: 0, revision: 3, files: 0 });
    await expect(page.getByText("Preparation has an unknown outcome", { exact: false })).toHaveCount(0);
  }
  await page.screenshot({ path: testInfo.outputPath("invalid-requested-tier.png"), fullPage: true });
  await tier.fill("2");
  await expect(tier).toHaveAttribute("aria-invalid", "false");
  await expect(feedback).toHaveCount(0);
  await prepare.click();
  await waitingFor(page, "prepare");
  await page.getByRole("button", { name: "Preparing review draft...", exact: true }).dispatchEvent("click");
  expect(await page.evaluate(() => (window as any).calls)).toMatchObject([{ command: {
    expectedRevision: 3, parameters: { requested_tier: 2, revision_id: "r1", purpose: "test" } } }]);
  await release(page, "prepare");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/test-proposal/);
  expect(await page.evaluate(() => (window as any).calls.length)).toBe(1);
});

test("requested-tier restrictions do not change another action's integer semantics", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&custom-integer-action");
  await page.getByLabel("count", { exact: true }).fill("-2");
  await expect(page.getByRole("button", { name: "Run custom action", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Run custom action", exact: true }).click();
  expect(await page.evaluate(() => (window as any).calls)).toMatchObject([{ command: {
    expectedRevision: 3, parameters: { count: -2 } } }]);
});

test("slow discovery and review permissions show progress instead of an empty bootstrap", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&pause=discovery&pause=review");
  await waitingFor(page, "discovery");
  await expect(page.getByRole("status").filter({ hasText: "Loading your reviews and available preparation tools" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
  await expect(page.getByText("Automatic preparation is unavailable", { exact: false })).toHaveCount(0);
  await release(page, "discovery");
  await waitingFor(page, "review");
  await expect(page.getByRole("status").filter({ hasText: "Loading review permissions and next steps" })).toBeVisible();
  await release(page, "review");
  await expect(page.getByLabel("Proposal ID", { exact: true })).toBeEnabled();
  await expect(page.getByText("Loading your reviews and available preparation tools", { exact: false })).toHaveCount(0);
});

test("preparation stays visibly pending through delayed proposal and context downloads", async ({ page }, testInfo) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&pause=prepare&pause=proposal&pause=context");
  await fillPreparation(page);
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await waitingFor(page, "prepare");
  await expect(page.getByRole("button", { name: "Preparing review draft...", exact: true })).toBeDisabled();
  const progress = page.getByRole("status").filter({ hasText: "Preparing review draft from the ontology" });
  await expect(progress).toBeVisible();
  await expect(progress).toBeInViewport();
  await page.screenshot({ path: testInfo.outputPath("preparation-pending.png"), fullPage: true });
  await expect(page.getByLabel("Proposal ID", { exact: true })).toBeDisabled();
  await expect(page.getByLabel("Stage title", { exact: true })).toBeDisabled();
  // A second dispatched click cannot issue a duplicate mutation while the first is pending.
  await page.getByRole("button", { name: "Preparing review draft...", exact: true }).dispatchEvent("click");
  expect(await page.evaluate(() => (window as any).calls.length)).toBe(1);
  await release(page, "prepare");
  await waitingFor(page, "proposal");
  await expect(progress).toBeVisible();
  await expect(page.getByLabel("Stage title", { exact: true })).toBeDisabled();
  await release(page, "proposal");
  await waitingFor(page, "context");
  await expect(progress).toBeVisible();
  await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Stage title", { exact: true })).toBeDisabled();
  await release(page, "context");
  await expect(page.getByRole("status").filter({ hasText: "Proposal prepared and saved in this stage" })).toBeVisible();
  await expect(page.getByRole("status").filter({ hasText: "Proposal prepared and saved in this stage" })).toBeInViewport();
  await expect(progress).toHaveCount(0);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/test-proposal/);
  await expect(page.getByRole("button", { name: "Start cycle from selected proposal", exact: true })).toBeEnabled();
  expect(await page.evaluate(() => (window as any).calls.length)).toBe(1);
  await page.screenshot({ path: testInfo.outputPath("preparation-ready.png"), fullPage: true });
});

test("slow stage-detail save locks edits through the review refresh and explains the next step", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&pause=details");
  await page.getByLabel("Stage title", { exact: true }).fill("Prepared for review");
  await page.getByLabel("Stage description", { exact: true }).fill("Retain these details during saving");
  await page.evaluate(() => history.replaceState({}, "", location.href + "&pause=review"));
  await page.getByRole("button", { name: "Save stage details", exact: true }).click();
  await waitingFor(page, "details");
  await expect(page.getByRole("status").filter({ hasText: "Saving stage title and description" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Saving stage details...", exact: true })).toBeDisabled();
  await expect(page.getByLabel("Stage title", { exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Saving stage details...", exact: true }).dispatchEvent("click");
  await release(page, "details");
  await waitingFor(page, "review");
  await expect(page.getByLabel("Stage description", { exact: true })).toHaveValue("Retain these details during saving");
  await expect(page.getByLabel("Stage title", { exact: true })).toBeDisabled();
  await release(page, "review");
  await expect(page.getByRole("status").filter({ hasText: "Stage title and description saved" })).toBeVisible();
  await expect(page.getByLabel("Proposal ID", { exact: true })).toBeEnabled();
  expect(await page.evaluate(() => (window as any).calls.length)).toBe(1);
});

test("a failed candidate download remains visible after context loads and is not reported as success", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&proposal-failed");
  await fillPreparation(page);
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("503 Proposal download failed");
  await expect(page.getByRole("status").filter({ hasText: "Proposal prepared and saved" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Refresh", exact: true })).toBeEnabled();
  await page.evaluate(() => history.replaceState({}, "", location.href.replace("&proposal-failed", "")));
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/test-proposal/);
  await expect(page.getByRole("alert")).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).calls.length)).toBe(1);
});

test("an interrupted preparation response retains fields and reconciles before any retry", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&preparation-interrupted");
  await fillPreparation(page);
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("500 Preparation response interrupted");
  await expect(page.getByText("Preparation has an unknown outcome", { exact: false })).toBeVisible();
  await expect(page.getByLabel("Revision ID", { exact: true })).toHaveValue("r1");
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toBeDisabled();
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/test-proposal/);
  await expect(page.getByText("Preparation has an unknown outcome", { exact: false })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Start cycle from selected proposal", exact: true })).toBeEnabled();
  expect(await page.evaluate(() => (window as any).calls.length)).toBe(1);
});

test("delayed preparation conflicts keep the entered parameters and require explicit retry", async ({ page }) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&pause=prepare&conflict");
  await fillPreparation(page);
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await release(page, "prepare");
  await expect(page.getByRole("alert")).toContainText("409 Conflict");
  await expect(page.getByLabel("Revision ID", { exact: true })).toHaveValue("r1");
  await expect(page.getByLabel("Purpose", { exact: true })).toHaveValue("test");
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toBeEnabled();
  await expect(page.getByRole("status").filter({ hasText: "Preparing review draft from the ontology" })).toHaveCount(0);
  expect(await page.evaluate(() => (window as any).calls.length)).toBe(1);
});
for (const step of ["flow", "review", "prepare"]) {
  test(`leaving during delayed ${step} never rewrites the next page or publishes old review state`, async ({ page }) => {
    await page.goto(`/tests/review.html?flow=flow-1&stage=stage-1&pause=${step}`);
    if (step === "prepare") {
      await fillPreparation(page);
      await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
    }
    await waitingFor(page, step);
    await page.evaluate(() => (window as any).leaveReview());
    await release(page, step);
    // Drain the accepted response and its Vue continuations without canceling or retrying it.
    if (step === "prepare") await expect.poll(() => page.evaluate(() => (window as any).reviewFixtureFlow.revision)).toBe(4);
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    await expect(page).toHaveURL(/\/tests\/review.html\?dashboard=other&cycle=keep&round=keep$/);
    await expect(page.getByText("Another dashboard page", { exact: true })).toBeVisible();
    await expect(page.getByLabel("Ontology review stage")).toHaveCount(0);
    expect(await page.evaluate(() => (window as any).calls.length)).toBe(step === "prepare" ? 1 : 0);
  });
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
  await expect(page.getByRole("heading", { name: "Reviewed candidate" })).toBeVisible();
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toHaveCount(0);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveAttribute("readonly");
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&conflict");
  for (const name of ["Proposal ID", "Revision ID", "Domain", "Purpose", "Target community"]) await page.getByLabel(name, { exact: true }).fill("test");
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("409 Conflict");
});

test("creation opens private bootstrap and coalescing makes termination explicit", async ({page}) => {
  await page.goto("/tests/review.html");
  await page.getByLabel("Review title", {exact:true}).fill("Test ontology review");
  await page.getByLabel("Ontology URN").fill("test.created");
  await page.getByRole("button", {name:"Create private bootstrap"}).click();
  await expect(page.getByRole("heading", {name:"Creator bootstrap", exact:true})).toBeVisible();
  await prepare(page);
  await page.getByRole("button", {name:"Start cycle from selected proposal"}).click();
  await expect(page.getByRole("heading", {name:"Peer review", exact:true}).first()).toBeVisible();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveAttribute("readonly");
  await page.getByRole("button", {name:"Coalesce feedback"}).click();
  await expect(page.getByRole("heading", {name:"Editor coalescing", exact:true})).toBeVisible();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/test-proposal/);
  await expect(page.getByRole("button", {name:"Return to peer review"})).toBeDisabled();
  await expect(page.getByRole("button", {name:"Save new draft attachment"})).toBeEnabled();
  await expect(page.getByRole("button", {name:"Discard unsaved proposal edits"})).toHaveCount(0);
  await page.getByRole("button", {name:"Terminate flow without publication"}).click();
  await expect(page.getByText("This flow is terminated.", {exact:false})).toBeVisible();
  await expect(page.getByRole("button", {name:"Start cycle from selected proposal"})).toHaveCount(0);
  const calls = await page.evaluate(() => (window as unknown as { calls:any[] }).calls);
  expect(calls[0].command.initialState).toMatchObject({schemaId:"bootstrap",assetUrn:"test.created",assetType:"ONTOLOGY"});
  expect(calls.filter(c=>c.command.operation).map(c=>c.command.operation)).toEqual(["OPEN_CYCLE","ENTER_COALESCING","TERMINATE"]);
});

test("coalescing returns to a new review stage while bootstrap remains historical", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1"); await prepare(page);
  await page.getByRole("button", {name:"Start cycle from selected proposal"}).click();
  await page.getByRole("button", {name:"Coalesce feedback"}).click();
  await page.getByLabel("Candidate proposal JSON").fill('{"proposal":{"id":"test-proposal","revision_id":"r2"}}');
  await expect(page.getByRole("button", {name:"Terminate flow without publication"})).toBeDisabled();
  await page.getByRole("button", {name:"Save new draft attachment"}).click();
  await page.getByRole("button", {name:"Return to peer review"}).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r2"/);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveAttribute("readonly");
  await page.getByLabel("Stage", {exact:true}).selectOption("stage-1");
  await expect(page.getByText("Historical stage.", {exact:false})).toBeVisible();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r1"/);
  await expect(page.getByRole("button", {name:"Prepare review draft",exact:true})).toHaveCount(0);
});

test("opening and closing optional community stages refreshes flow state without leaving the stage", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1"); await prepare(page);
  await page.getByRole("button", {name:"Start cycle from selected proposal"}).click();
  await page.getByRole("button", {name:"Open community round",exact:true}).click();
  await expect(page.getByRole("heading",{name:"Community review",exact:true}).first()).toBeVisible();
  await expect(page.getByLabel("Stage",{exact:true})).toHaveValue("round-2");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r1"/);
  await page.getByRole("button",{name:"Close iteration",exact:true}).click();
  await expect(page.getByText("Historical stage.",{exact:false})).toBeVisible();
  await expect(page.getByLabel("Stage",{exact:true})).toHaveValue("round-2");
  expect(page.url()).toContain("stage=round-2");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r1"/);
});

test("candidate conflicts retain unsaved edits until explicit discard unlocks refresh", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1"); await prepare(page);
  await page.evaluate(() => history.replaceState({},"",location.href+"&conflict"));
  await page.getByLabel("Candidate proposal JSON").fill('{"proposal":{"revision_id":"keep-r2"}}');
  await page.getByRole("button",{name:"Save new draft attachment"}).click();
  await expect(page.getByRole("alert")).toContainText("409 Conflict");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/keep-r2/);
  await expect(page.getByRole("button",{name:"Refresh",exact:true})).toBeDisabled();
  await page.getByRole("button",{name:"Discard unsaved proposal edits"}).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r1"/);
  await expect(page.getByRole("button",{name:"Refresh",exact:true})).toBeEnabled();
});

test("interrupted creation prevents repeats and reconciles the persisted bootstrap", async ({page}) => {
  await page.goto("/tests/review.html?creation-interrupted");
  await page.getByLabel("Review title", {exact:true}).fill("Test ontology review");
  await page.getByLabel("Ontology URN").fill("test.ontology");
  await page.getByRole("button", {name:"Create private bootstrap"}).click();
  await expect(page.getByRole("button", {name:"Create private bootstrap"})).toBeDisabled();
  await expect(page.getByText("has an unknown outcome",{exact:false})).toBeVisible();
  expect(await page.evaluate(()=>(window as unknown as {calls:any[]}).calls.length)).toBe(1);
  await page.reload();
  await expect(page.getByRole("button", {name:"Create private bootstrap"})).toBeDisabled();
  await page.getByRole("button",{name:"Refresh existing flows",exact:true}).click();
  await page.getByRole("button",{name:"Open existing bootstrap flow-new",exact:true}).click();
  await expect(page.getByRole("heading",{name:"Creator bootstrap",exact:true})).toBeVisible();
  expect(await page.evaluate(()=>(window as unknown as {calls:any[]}).calls.length)).toBe(0);
});

test("pending creation recovery is scoped to the authenticated account", async ({page}) => {
  await page.goto("/tests/review.html?creation-interrupted");
  await page.getByLabel("Review title", {exact:true}).fill("Test ontology review");
  await page.getByLabel("Ontology URN").fill("private.creator.ontology");
  await page.getByRole("button",{name:"Create private bootstrap"}).click();
  await expect(page.getByText("has an unknown outcome",{exact:false})).toBeVisible();
  await page.goto("/tests/review.html?user=another-editor");
  await expect(page.getByText("has an unknown outcome",{exact:false})).toHaveCount(0);
  await expect(page.getByRole("button",{name:"Refresh existing flows",exact:true})).toHaveCount(0);
  await page.goto("/tests/review.html");
  await expect(page.getByText("has an unknown outcome",{exact:false})).toBeVisible();
  await expect(page.getByRole("button",{name:"Create private bootstrap"})).toBeDisabled();
});

test("named bootstrap saves initial proposal and references atomically and requires an explicit new review", async ({page}) => {
  await page.goto("/tests/review.html");
  await page.getByLabel("Ontology URN", {exact:true}).fill("test.uploaded");
  await expect(page.getByRole("button", {name:"Create private bootstrap",exact:true})).toBeDisabled();
  await page.getByLabel("Review title", {exact:true}).fill("Water ontology review");
  await page.getByLabel("Review description", {exact:true}).fill("Review the water concepts using the attached evidence.");
  const proposal = JSON.stringify({proposal:{id:"water",revision_id:"water-r1"}});
  await page.getByLabel("Initial proposal file (optional)", {exact:true}).setInputFiles({name:"water.json",mimeType:"application/json",buffer:Buffer.from(proposal)});
  await page.getByLabel("Reference files (optional)", {exact:true}).setInputFiles({name:"evidence.pdf",mimeType:"application/pdf",buffer:Buffer.from([0,128,255,10])});
  await page.getByRole("button", {name:"Create private bootstrap",exact:true}).click();
  await expect(page.getByRole("button", {name:"Create private bootstrap",exact:true})).toHaveCount(0);
  await expect(page.getByLabel("Stage title", {exact:true})).toHaveValue("Water ontology review");
  await expect(page.getByLabel("Stage description", {exact:true})).toHaveValue("Review the water concepts using the attached evidence.");
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/water-r1/);
  await expect(page.getByRole("button", {name:"Prepare review draft",exact:true})).toHaveCount(0);
  await expect(page.getByRole("button", {name:"Start cycle from selected proposal",exact:true})).toBeEnabled();
  await expect(page.getByRole("alert")).toHaveCount(0);
  const calls = await page.evaluate(() => (window as unknown as {calls:any[]}).calls);
  expect(calls).toHaveLength(1);
  expect(calls[0].path).toContain("/flows/initialize?");
  expect(calls[0].command.initialState).toMatchObject({title:"Water ontology review",description:"Review the water concepts using the attached evidence."});
  expect(calls[0].command.attachments).toEqual([
    {type:"bootstrap-proposal",fileName:"water.json",mediaType:"application/vnd.klab.proposal+yaml",content:Buffer.from(proposal).toString("base64")},
    {type:"supporting-material",fileName:"evidence.pdf",mediaType:"application/pdf",content:Buffer.from([0,128,255,10]).toString("base64")},
  ]);
  await page.getByRole("button", {name:"Start another ontology review",exact:true}).click();
  await expect(page.getByLabel("Review title", {exact:true})).toHaveValue("");
  await expect(page.getByRole("button", {name:"Create private bootstrap",exact:true})).toBeDisabled();
  await page.getByRole("button", {name:"Cancel new review",exact:true}).click();
  await expect(page.getByRole("button", {name:"Create private bootstrap",exact:true})).toHaveCount(0);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/water-r1/);
  expect(await page.evaluate(() => (window as unknown as {calls:any[]}).calls.length)).toBe(1);
});

test("saved preparation details, reference uploads and notes remain usable before a proposal exists", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1");
  await expect(page.getByLabel("Proposal attachment", {exact:true})).toHaveCount(0);
  await expect(page.getByText("No proposal yet.", {exact:false})).toBeVisible();
  await page.getByLabel("Stage title", {exact:true}).fill("Editorial bootstrap");
  await page.getByLabel("Stage description", {exact:true}).fill("Scoped review questions");
  await expect(page.getByRole("button", {name:"Refresh",exact:true})).toBeDisabled();
  await page.getByRole("button", {name:"Save stage details",exact:true}).click();
  await expect(page.getByRole("button", {name:"Save stage details",exact:true})).toBeDisabled();
  await page.getByLabel("Stage files", {exact:true}).setInputFiles({name:"reference.md",mimeType:"text/markdown",buffer:Buffer.from("Evidence from an authored reference.")});
  await page.getByRole("button", {name:"Upload selected files",exact:true}).click();
  await expect(page.getByLabel("Saved stage files")).toContainText("reference.md");
  await page.getByLabel("Preparation note", {exact:true}).fill("Editor rationale before preparing the candidate");
  await page.getByRole("button", {name:"Save preparation note",exact:true}).click();
  await expect(page.getByLabel("Preparation note", {exact:true})).toHaveValue("");
  await prepare(page);
  await expect(page.getByLabel("Stage title", {exact:true})).toHaveValue("Editorial bootstrap");
  await expect(page.getByLabel("Stage description", {exact:true})).toHaveValue("Scoped review questions");
  await expect(page.getByLabel("Saved stage files")).toContainText("reference.md");
  const calls = await page.evaluate(() => (window as unknown as {calls:any[]}).calls);
  expect(calls[0].command).toMatchObject({expectedRevision:3,title:"Editorial bootstrap",description:"Scoped review questions"});
  expect(calls[1].command).toMatchObject({expectedRevision:4,type:"supporting-material",fileName:"reference.md"});
  expect(calls[2].command).toMatchObject({expectedRevision:5,type:"supporting-material",mediaType:"text/plain"});
  expect(Buffer.from(calls[2].command.content,"base64").toString()).toBe("Editor rationale before preparing the candidate");
});

test("stage detail conflicts preserve local work until explicit discard", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&conflict");
  await page.getByLabel("Stage title", {exact:true}).fill("Unsaved title");
  await page.getByRole("button", {name:"Save stage details",exact:true}).click();
  await expect(page.getByRole("alert")).toContainText("409 Conflict");
  await expect(page.getByLabel("Stage title", {exact:true})).toHaveValue("Unsaved title");
  await expect(page.getByRole("button", {name:"Refresh",exact:true})).toBeDisabled();
  await page.getByRole("button", {name:"Discard stage detail edits",exact:true}).click();
  await expect(page.getByRole("button", {name:"Refresh",exact:true})).toBeEnabled();
});

test("uncertain upload blocks repeat submissions until saved files are checked", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&upload-interrupted");
  await page.getByLabel("Stage files", {exact:true}).setInputFiles({name:"saved-reference.md",mimeType:"text/markdown",buffer:Buffer.from("Reference saved before connection interruption")});
  await page.getByRole("button", {name:"Upload selected files",exact:true}).click();
  await expect(page.getByText("The last upload has an unknown outcome.", {exact:false})).toBeVisible();
  await expect(page.getByRole("button", {name:"Upload selected files",exact:true})).toBeDisabled();
  await page.getByRole("button", {name:"Check saved files",exact:true}).click();
  await expect(page.getByLabel("Saved stage files")).toContainText("saved-reference.md");
  await expect(page.getByRole("button", {name:"Upload selected files",exact:true})).toBeDisabled();
  expect(await page.evaluate(() => (window as unknown as {calls:any[]}).calls.length)).toBe(1);
});

test("missing actor context and failed action discovery have actionable diagnostics", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&context-missing");
  await expect(page.getByRole("alert")).toContainText("target ontology or its project");
  await expect(page.getByRole("button", {name:"Prepare review draft",exact:true})).toHaveCount(0);
  await expect(page.getByLabel("ontology", {exact:true})).toHaveCount(0);
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&action-failed");
  await expect(page.getByRole("alert")).toContainText("Could not load preparation actions: 503");
  await expect(page.getByRole("button", {name:"Refresh",exact:true})).toBeEnabled();
});

test("an interrupted note upload keeps unsaved text when reconciliation finds no saved file", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&upload-dropped");
  await page.getByLabel("Preparation note", {exact:true}).fill("Keep my preparation note through a failed connection");
  await page.getByRole("button", {name:"Save preparation note",exact:true}).click();
  await expect(page.getByRole("button", {name:"Save preparation note",exact:true})).toBeDisabled();
  await page.getByRole("button", {name:"Check saved files",exact:true}).click();
  await expect(page.getByText("No matching saved file was found.", {exact:false})).toBeVisible();
  await expect(page.getByLabel("Preparation note", {exact:true})).toHaveValue("Keep my preparation note through a failed connection");
  await page.getByRole("button", {name:"Save preparation note",exact:true}).click();
  await expect(page.getByLabel("Preparation note", {exact:true})).toHaveValue("");
  expect(await page.evaluate(() => (window as unknown as {calls:any[]}).calls.length)).toBe(2);
});

test("discarding an uncreated form re-enables navigation without creating a flow", async ({page}) => {
  await page.goto("/tests/review.html");
  await page.getByLabel("Review title", {exact:true}).fill("Not yet ready");
  await expect(page.getByLabel("Flow", {exact:true})).toBeDisabled();
  await page.getByRole("button", {name:"Discard new review draft",exact:true}).click();
  await expect(page.getByLabel("Review title", {exact:true})).toHaveValue("");
  await expect(page.getByLabel("Flow", {exact:true})).toBeEnabled();
  expect(await page.evaluate(() => (window as unknown as {calls:any[]}).calls.length)).toBe(0);
});

test("refreshing rounds in another tab cannot rebase stale stage details over a newer title", async ({page, context}) => {
  const other = await context.newPage();
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&shared-details");
  await other.goto("/tests/review.html?flow=flow-1&stage=stage-1&shared-details");
  await expect(page.getByLabel("Stage title", {exact:true})).toHaveValue("");
  await other.getByLabel("Stage title", {exact:true}).fill("Newer title from tab B");
  await other.getByLabel("Stage description", {exact:true}).fill("Original shared description");
  await other.getByRole("button", {name:"Save stage details",exact:true}).click();
  await expect(other.getByRole("button", {name:"Save stage details",exact:true})).toBeDisabled();
  // Tab A refreshes only review data. Its stage details still belong to its original snapshot.
  await page.getByRole("button", {name:"Refresh rounds",exact:true}).click();
  await expect(page.getByRole("button", {name:"Refresh rounds",exact:true})).toBeEnabled();
  await page.getByLabel("Stage title", {exact:true}).fill("Old title from tab A");
  await page.getByLabel("Stage description", {exact:true}).fill("My local unsaved description");
  await page.getByRole("button", {name:"Save stage details",exact:true}).click();
  await expect(page.getByRole("alert")).toContainText("409 Conflict");
  await expect(page.getByLabel("Stage description", {exact:true})).toHaveValue("My local unsaved description");
  await other.getByRole("button", {name:"Refresh",exact:true}).click();
  await expect(other.getByLabel("Stage title", {exact:true})).toHaveValue("Newer title from tab B");
  await expect(other.getByLabel("Stage description", {exact:true})).toHaveValue("Original shared description");
  const calls = await page.evaluate(() => (window as unknown as {calls:any[]}).calls);
  expect(calls[0].command.expectedRevision).toBe(3);
  await page.getByRole("button", {name:"Discard stage detail edits",exact:true}).click();
  await page.getByRole("button", {name:"Refresh",exact:true}).click();
  await expect(page.getByLabel("Stage title", {exact:true})).toHaveValue("Newer title from tab B");
  await other.close();
});

test("saving stage details refreshes review revision before immediately starting the existing candidate", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1");
  await prepare(page);
  await page.getByLabel("Stage title", {exact:true}).fill("Ready for peer review");
  await page.getByRole("button", {name:"Save stage details",exact:true}).click();
  await expect(page.getByRole("button", {name:"Start cycle from selected proposal",exact:true})).toBeEnabled();
  await page.getByRole("button", {name:"Start cycle from selected proposal",exact:true}).click();
  await expect(page.getByRole("heading", {name:"Peer review",exact:true}).first()).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
  const calls = await page.evaluate(() => (window as unknown as {calls:any[]}).calls);
  expect(calls.find(item=>item.command.operation === "OPEN_CYCLE").command.expectedRevision).toBe(5);
});

test("coalescing retains the frozen ontology source when the editor previews a different reference", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1&source-context");
  await prepare(page);
  await page.getByRole("button", {name:"Start cycle from selected proposal",exact:true}).click();
  await page.getByRole("button", {name:"Coalesce feedback",exact:true}).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/"r1"/);
  await page.getByLabel("Stage files", {exact:true}).setInputFiles({name:"editor-evidence.txt",mimeType:"text/plain",buffer:Buffer.from("Reference text is not ontology source.")});
  await page.getByRole("button", {name:"Upload selected files",exact:true}).click();
  await expect(page.getByLabel("Saved stage files")).toContainText("editor-evidence.txt");
  await page.getByText("Comments and source context", {exact:true}).click();
  const evidence = await page.getByLabel("Context attachment", {exact:true}).locator("option").filter({hasText:"editor-evidence.txt"}).getAttribute("value");
  await page.getByLabel("Context attachment", {exact:true}).selectOption("");
  await page.getByLabel("Context attachment", {exact:true}).selectOption(evidence!);
  await expect(page.getByLabel("Source context and comments")).toHaveValue("Reference text is not ontology source.");
  await page.getByLabel("Candidate proposal JSON").fill('{"proposal":{"id":"test-proposal","revision_id":"r2"}}');
  await page.getByRole("button", {name:"Save new draft attachment",exact:true}).click();
  await expect(page.getByRole("button", {name:"Return to peer review",exact:true})).toBeEnabled();
  const calls = await page.evaluate(() => (window as unknown as {calls:any[]}).calls);
  const source = calls.find(item=>item.command.type === "candidate-ontology").command;
  expect(Buffer.from(source.content,"base64").toString()).toBe("namespace test.ontology;\n");
});

test("a saved bootstrap accepts a selected proposal file and saves YAML edits without a JSON-only gate", async ({page}) => {
  await page.goto("/tests/review.html?flow=flow-1&stage=stage-1");
  await page.getByLabel("File purpose", {exact:true}).selectOption("bootstrap-proposal");
  await page.getByLabel("Stage files", {exact:true}).setInputFiles({name:"editor-proposal.yaml",mimeType:"application/yaml",buffer:Buffer.from("proposal:\n  id: example\n  revision_id: example-r1\n")});
  await page.getByRole("button", {name:"Upload selected files",exact:true}).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/example-r1/);
  await expect(page.getByRole("button", {name:"Prepare review draft",exact:true})).toHaveCount(0);
  await page.getByLabel("Candidate proposal JSON").fill("proposal:\n  id: example\n  revision_id: example-r2\n");
  await page.getByRole("button", {name:"Save new draft attachment",exact:true}).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/example-r2/);
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.getByLabel("Proposal attachment", {exact:true}).locator("option")).toHaveCount(3);
});
