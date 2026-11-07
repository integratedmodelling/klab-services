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
  expect(calls[0].command).toMatchObject({schemaId:"bootstrap",assetUrn:"test.created",assetType:"ONTOLOGY"});
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
