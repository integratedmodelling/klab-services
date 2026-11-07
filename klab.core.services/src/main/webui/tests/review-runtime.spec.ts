import { test, expect, type APIRequestContext } from "@playwright/test";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

const headers = { "X-Review-Fixture-Principal": "editor" };
const exactOntology = "ontology test.ontology;\nthing Sample;\n";
const receipts: unknown[] = [];
function api() {
  const url = process.env.KLAB_REVIEW_E2E_API || "";
  expect(url).toMatch(/^http:\/\/127\.0\.0\.1:\d+$/);
  return url;
}
async function saved(request: APIRequestContext, id: string, stageId: string, proposalId: string) {
  const response = await request.get(api() + "/api/v1/flows/" + id, { headers });
  expect(response.status()).toBe(200);
  const flow = await response.json(), stage = flow.states[stageId];
  expect(stage.attachments).toHaveLength(5);
  const proposal = stage.attachments.find((a: any) => a.type === "bootstrap-proposal");
  const ontology = stage.attachments.find((a: any) => a.type === "candidate-ontology");
  expect(proposal).toBeTruthy(); expect(ontology).toBeTruthy();
  async function bytes(attachmentId: string) {
    const result = await request.get(api() + "/api/v1/flows/" + id + "/attachments/" + attachmentId, { headers });
    expect(result.status()).toBe(200);
    const text = await result.text();
    return Buffer.from(text.startsWith('"') ? JSON.parse(text) : text, "base64").toString("utf-8");
  }
  const document = JSON.parse(await bytes(proposal.id));
  expect(document.proposal.id).toBe(proposalId);
  expect(await bytes(ontology.id)).toBe(exactOntology);
  expect(stage.attachments.filter((a: any) => a.type === "bootstrap-comments")).toHaveLength(1);
  return { flowId: id, stageId, revision: flow.revision, artifactCount: stage.attachments.length, proposalId };
}

test("browser scope discovers and executes the genuine preparation action through the editor form", async ({ page, request }) => {
  const failures: string[] = [];
  page.on("response", response => { if (response.url().startsWith(api()) && response.status() >= 500) failures.push(response.url()); });
  await page.goto("/tests/review-e2e.html?actor=editor");
  await page.getByLabel("Ontology URN", { exact: true }).fill("test.ontology");
  await page.getByLabel("Review title", { exact: true }).fill("Real runtime browser preparation");
  await page.getByLabel("Review description", { exact: true }).fill("Exercise authenticated host service lookup, production behavior parser and compiled action.");
  await page.getByRole("button", { name: "Create private bootstrap", exact: true }).click();
  await expect(page.getByLabel("Proposal ID", { exact: true })).toBeVisible();
  await page.getByLabel("Stage title", { exact: true }).fill("Saved runtime preparation details");
  await page.getByLabel("Stage description", { exact: true }).fill("Revision-checked details saved through the actual controller before preparing.");
  await page.getByRole("button", { name: "Save stage details", exact: true }).click();
  await expect(page.getByRole("status").filter({ hasText: "Stage title and description saved" })).toBeVisible();
  for (const [name, value] of [["Proposal ID", "browser-proposal"], ["Revision ID", "browser-r1"],
      ["Domain", "Wetland terminology"], ["Purpose", "Review the current ontology"],
      ["Requested tier", "1"], ["Target community", "Ontology reviewers"]]) {
    await page.getByLabel(name, { exact: true }).fill(value);
  }
  let releasePreparation!: () => void, preparationRequests = 0;
  const preparationGate = new Promise<void>(resolve => { releasePreparation = resolve; });
  await page.route("**/api/v1/flows/*/states/*/actions/prepare-review-draft", async route => {
    preparationRequests++;
    await preparationGate;
    await route.continue(); // No canned response: the actual runtime still executes the action.
  });
  await page.getByRole("button", { name: "Prepare review draft", exact: true }).click();
  try {
    await expect(page.getByRole("status").filter({ hasText: "Preparing review draft from the ontology" })).toBeVisible();
    await expect(page.getByRole("button", { name: "Preparing review draft...", exact: true })).toBeDisabled();
    await expect(page.getByLabel("Stage title", { exact: true })).toBeDisabled();
    await page.getByRole("button", { name: "Preparing review draft...", exact: true }).dispatchEvent("click");
    await expect.poll(() => preparationRequests).toBe(1);
  } finally { releasePreparation(); }
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/browser-r1/);
  await expect(page.getByText("Could not load preparation actions", { exact: false })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Start cycle from selected proposal", exact: true })).toBeEnabled();
  await expect(page.getByRole("status").filter({ hasText: "Proposal prepared and saved in this stage" })).toBeVisible();
  expect(preparationRequests).toBe(1);
  const url = new URL(page.url()), id = url.searchParams.get("flow")!, stageId = url.searchParams.get("stage")!;
  const receipt = await saved(request, id, stageId, "browser-proposal");
  await page.reload();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/browser-r1/);
  await expect(page.getByLabel("Stage title", { exact: true })).toHaveValue("Saved runtime preparation details");
  await expect(page.getByLabel("Stage description", { exact: true })).toHaveValue("Revision-checked details saved through the actual controller before preparing.");
  expect((await saved(request, id, stageId, "browser-proposal")).revision).toBe(receipt.revision);
  expect(failures).toEqual([]);
  if (process.env.KLAB_REVIEW_E2E_REPORT) await page.screenshot({ path: join(dirname(process.env.KLAB_REVIEW_E2E_REPORT), "preparation-real-browser.png"), fullPage: true });
  receipts.push({ ...receipt, path: "browser details PATCH -> preparation form -> authenticated scope -> actual controller/runtime", preparationRequests, repeatedPreparation: false });
});

test("web bootstrap opens an actual IDE-like prepared candidate without rerunning preparation", async ({ page, request }) => {
  const result = await request.post(api() + "/fixture/native-prepare", { headers });
  expect(result.status()).toBe(200);
  const prepared = await result.json();
  await page.goto("/tests/review-e2e.html?actor=editor&flow=" + prepared.flowId + "&stage=" + prepared.stageId);
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/native-r1/);
  await expect(page.getByText("Could not load preparation actions", { exact: false })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Prepare review draft", exact: true })).toHaveCount(0);
  const receipt = await saved(request, prepared.flowId, prepared.stageId, "native-proposal");
  expect(receipt.revision).toBe(prepared.revision);
  await page.getByRole("button", { name: "Refresh", exact: true }).click();
  await expect(page.getByLabel("Candidate proposal JSON")).toHaveValue(/native-r1/);
  expect((await saved(request, prepared.flowId, prepared.stageId, "native-proposal")).revision).toBe(prepared.revision);
  receipts.push({ ...receipt, path: "advertised IDE-like request -> actual controller/runtime -> web open", repeatedPreparation: false });
});

test.afterAll(() => {
  if (process.env.KLAB_REVIEW_E2E_REPORT) writeFileSync(process.env.KLAB_REVIEW_E2E_REPORT, JSON.stringify({
    result: receipts.length === 2 ? "passed" : "incomplete",
    authentication: "Real HubWebAuthentication implementation with a synthetic Hub profile response; no live IdP or production account.",
    behavior: "Production parser, component registry, compiler, controller and manager; no canned preparation action or proposal.",
    receipts
  }, null, 2));
});
