import { revealReviewTools } from "./review-tools";
import { test, expect, type Page } from "@playwright/test";
import { writeFileSync } from "node:fs";
import { dirname, join } from "node:path";

test("four simulated principals review, integrate, close and restart through real UI/native workflow APIs", async ({ browser, request }) => {
  const api = process.env.KLAB_REVIEW_E2E_API!;
  let flow = "", stage = "";
  expect(api).toMatch(/^http:\/\/127\.0\.0\.1:\d+$/);
  async function view(actor = "editor"): Promise<any> {
    const response = await request.get(`${api}/api/v1/flows/${flow}/review-cycles`, { headers: { "X-Review-Fixture-Principal": actor } });
    expect(response.status()).toBe(200); return response.json();
  }
  const contexts = await Promise.all(Array.from({ length: 4 }, () => browser.newContext()));
  try {
    const [editor, alice, bob, citizen] = await Promise.all(contexts.map(c => c.newPage()));
    async function visit(page: Page, actor: string, cycle?: string, round?: string) {
      const routedStage = round ? (await view(actor)).cycles.find((c:any) => c.id === cycle).rounds.find((r:any) => r.id === round).stageId : stage;
      await page.goto(`/tests/review-e2e.html?actor=${actor}&flow=${flow}&stage=${routedStage}${cycle ? `&cycle=${cycle}&round=${round}` : ""}`);
      await revealReviewTools(page);
    }
    async function refresh(page: Page) {
      await revealReviewTools(page);
      await page.getByRole("button", { name: "Refresh rounds", exact: true }).click();
      await expect(page.getByRole("button", { name: "Refresh rounds", exact: true })).toBeEnabled();
    }
    await editor.goto("/tests/review-e2e.html?actor=editor");
    await revealReviewTools(editor);
    await expect(editor.getByLabel("Flow", {exact:true}).locator("option")).toHaveCount(1);
    await revealReviewTools(editor);
    await editor.getByLabel("Ontology URN", {exact:true}).fill("test.ontology");
    await revealReviewTools(editor);
    await editor.getByLabel("Review title", {exact:true}).fill("Native ontology editor review");
    await revealReviewTools(editor);
    await editor.getByLabel("Review description", {exact:true}).fill("Prepare the ontology with authored references and editor rationale before peer review.");
    await revealReviewTools(editor);
    await editor.getByLabel("Reference files (optional)", {exact:true}).setInputFiles({name:"review-reference.md",mimeType:"text/markdown",buffer:Buffer.from("# Native editor reference\nReference evidence uploaded from the bootstrap form.")});
    if (process.env.KLAB_REVIEW_E2E_REPORT) await editor.screenshot({path:join(dirname(process.env.KLAB_REVIEW_E2E_REPORT),"editor-journey-filled-bootstrap.png"),fullPage:true});
    await revealReviewTools(editor);
    await editor.getByRole("button", {name:"Create private bootstrap",exact:true}).click();
    await expect(editor.getByRole("button", {name:"Create private bootstrap",exact:true})).toHaveCount(0);
    await expect(editor.getByLabel("Stage title", {exact:true})).toHaveValue("Native ontology editor review");
    flow = new URL(editor.url()).searchParams.get("flow")!; stage = new URL(editor.url()).searchParams.get("stage")!;
    expect(flow).toBeTruthy(); expect(stage).toBeTruthy();
    await expect(editor.getByLabel("Saved stage files")).toContainText("review-reference.md");
    // Starting another flow is explicit, and cancelling it must not create a duplicate or lose this stage.
    await revealReviewTools(editor);
    await editor.getByRole("button", {name:"Start another ontology review",exact:true}).click();
    await expect(editor.getByLabel("Review title", {exact:true})).toHaveValue("");
    await revealReviewTools(editor);
    await editor.getByRole("button", {name:"Cancel new review",exact:true}).click();
    await expect(editor.getByRole("button", {name:"Create private bootstrap",exact:true})).toHaveCount(0);
    await revealReviewTools(editor);
    await editor.getByLabel("Stage title", {exact:true}).fill("Native ontology editor preparation");
    await revealReviewTools(editor);
    await editor.getByLabel("Stage description", {exact:true}).fill("Edited bootstrap scope, preserved through preparation and reload.");
    await revealReviewTools(editor);
    await editor.getByRole("button", {name:"Save stage details",exact:true}).click();
    await expect(editor.getByRole("button", {name:"Save stage details",exact:true})).toBeDisabled();
    await revealReviewTools(editor);
    await editor.getByLabel("Preparation note", {exact:true}).fill("Bootstrap editor rationale from native form");
    await revealReviewTools(editor);
    await editor.getByRole("button", {name:"Save preparation note",exact:true}).click();
    await expect(editor.getByLabel("Preparation note", {exact:true})).toHaveValue("");
    for (const name of ["Proposal ID", "Revision ID", "Domain", "Purpose", "Target community"])
      await revealReviewTools(editor);
      await editor.getByLabel(name, { exact: true }).fill(name === "Proposal ID" ? "test-proposal" : name === "Revision ID" ? "test-r1" : "Fixture review");
    await revealReviewTools(editor);
    await editor.getByRole("button", { name: "Prepare review draft", exact: true }).click();
    await expect(editor.getByLabel("Candidate proposal source")).toHaveValue(/test-r1/);
    const original = JSON.parse(await editor.getByLabel("Candidate proposal source").inputValue());
    await expect(editor.getByLabel("Stage title", {exact:true})).toHaveValue("Native ontology editor preparation");
    await expect(editor.getByLabel("Stage description", {exact:true})).toHaveValue("Edited bootstrap scope, preserved through preparation and reload.");
    // Upload a real proposal file through the saved bootstrap, without a programmatic candidate seed.
    await revealReviewTools(editor);
    await editor.getByLabel("File purpose", {exact:true}).selectOption("bootstrap-proposal");
    await revealReviewTools(editor);
    await editor.getByLabel("Stage files", {exact:true}).setInputFiles({name:"editor-proposal.json",mimeType:"application/json",buffer:Buffer.from(JSON.stringify(original))});
    await revealReviewTools(editor);
    await editor.getByRole("button", {name:"Upload selected files",exact:true}).click();
    await expect(editor.getByLabel("Saved stage files")).toContainText("editor-proposal.json");
    await expect(editor.getByLabel("Proposal attachment", {exact:true}).locator("option:checked")).toContainText("editor-proposal.json");
    await editor.reload();
    await revealReviewTools(editor);
    await expect(editor.getByLabel("Stage title", {exact:true})).toHaveValue("Native ontology editor preparation");
    await expect(editor.getByLabel("Candidate proposal source")).toHaveValue(/test-r1/);
    await expect(editor.getByRole("button", {name:"Create private bootstrap",exact:true})).toHaveCount(0);
    if (process.env.KLAB_REVIEW_E2E_REPORT) await editor.screenshot({path:join(dirname(process.env.KLAB_REVIEW_E2E_REPORT),"editor-journey-saved-bootstrap.png"),fullPage:true});
    await revealReviewTools(editor);
    await editor.getByRole("button", { name: "Start peer review", exact: true }).click();
    await expect(editor.getByRole("button", { name: "Open reviewer round", exact: true })).toBeVisible();
    await expect(editor.getByLabel("Feedback iteration").locator("option")).toHaveCount(2);
    await revealReviewTools(editor);
    await editor.getByRole("button", { name: "Open community round", exact: true }).click();
    await expect(editor.getByLabel("Feedback iteration").locator("option")).toHaveCount(3);
    let state = await view(); const cycle1 = state.cycles[0], peer = cycle1.rounds[0], community = cycle1.rounds[1];
    await Promise.all([visit(alice, "reviewer-a", cycle1.id, peer.id), visit(bob, "reviewer-b", cycle1.id, peer.id), visit(citizen, "community", cycle1.id, community.id)]);
    for (const page of [alice, bob, citizen]) await expect(page.getByLabel("Candidate proposal source")).toHaveValue(/test-r1/);
    await expect(citizen.getByLabel("Feedback iteration").locator("option")).toHaveCount(2);
    await expect(citizen.getByRole("button", { name: "Open reviewer round" })).toHaveCount(0);
    const alternative = structuredClone(original); alternative.proposal.revision_id = "test-r2-a";
    alternative.proposal.supersedes_revision = "test-r1"; alternative.proposal.title = "Reviewer A alternative";
    await revealReviewTools(alice);
    await alice.getByLabel("Your feedback", { exact: true }).fill("Reviewer A private draft");
    await revealReviewTools(alice);
    await alice.getByLabel("Your alternative proposal", { exact: true }).fill(JSON.stringify(alternative));
    await revealReviewTools(alice);
    await alice.getByRole("button", { name: "Save private draft", exact: true }).click();
    await expect(alice.getByRole("button", { name: "Save private draft", exact: true })).toBeDisabled();
    expect(JSON.stringify(await view())).not.toContain("Reviewer A private draft");
    expect(JSON.stringify(await view("reviewer-b"))).not.toContain("Reviewer A private draft");
    await revealReviewTools(alice);
    await alice.getByLabel("Your feedback", { exact: true }).fill("Reviewer A submitted feedback");
    await revealReviewTools(alice);
    await alice.getByRole("button", { name: "Submit feedback", exact: true }).click();
    await alice.getByRole('button', {name: 'Share feedback', exact: true}).click();
    await expect(alice.getByLabel("Your feedback", { exact: true })).toHaveValue("");
    await revealReviewTools(bob);
    await bob.getByLabel("Your feedback", { exact: true }).fill("Reviewer B submitted feedback");
    await revealReviewTools(bob);
    await bob.getByRole("button", { name: "Submit feedback", exact: true }).click();
    await bob.getByRole('button', {name: 'Share feedback', exact: true}).click();
    await expect(bob.getByRole("alert")).toContainText("409");
    await expect(bob.getByLabel("Your feedback", { exact: true })).toHaveValue("Reviewer B submitted feedback");
    await revealReviewTools(bob);
    await refresh(bob); await bob.getByRole("button", { name: "Submit feedback", exact: true }).click();
    await bob.getByRole('button', {name: 'Share feedback', exact: true}).click();
    await expect(bob.getByLabel("Your feedback", { exact: true })).toHaveValue("");
    await revealReviewTools(citizen);
    await refresh(citizen); await citizen.getByLabel("Your feedback", { exact: true }).fill("Community contribution");
    await revealReviewTools(citizen);
    await citizen.getByRole("button", { name: "Submit feedback", exact: true }).click();
    await citizen.getByRole('button', {name: 'Share feedback', exact: true}).click();
    await expect(citizen.getByLabel("Your feedback", { exact: true })).toHaveValue("");
    expect(JSON.stringify(await view("community"))).not.toContain("Reviewer A submitted feedback");
    expect(JSON.stringify(await view("community"))).not.toContain("Reviewer B submitted feedback");
    await revealReviewTools(editor);
    await refresh(editor); await editor.getByLabel("Feedback iteration").selectOption(peer.id);
    await expect(editor.getByText("Reviewer A submitted feedback", { exact: true })).toBeVisible();
    await expect(editor.getByText("Reviewer B submitted feedback", { exact: true })).toBeVisible();
    await revealReviewTools(editor);
    await editor.getByLabel("Feedback contributor").selectOption("reviewer-a");
    await expect(editor.getByText("Reviewer B submitted feedback", { exact: true })).toHaveCount(0);
    state = await view(); const proposal = state.cycles[0].rounds[0].contributions.find((c: any) => c.owner === "reviewer-a");
    expect(state.cycles[0].rounds[0].contributions.map((c: any) => c.owner).sort()).toEqual(["reviewer-a", "reviewer-b"]);
    await revealReviewTools(editor);
    await editor.getByLabel("Contribution proposal", { exact: true }).selectOption(proposal.id);
    await revealReviewTools(editor);
    await editor.getByText("Alternative test-proposal", { exact: false }).click();
    await expect(editor.getByLabel("Proposal by reviewer-a", { exact: true })).toHaveValue(/test-r2-a/);
    await expect(editor.getByLabel("Candidate proposal source")).toHaveValue(/test-r1/);
    await revealReviewTools(editor);
    await editor.getByRole("button", { name: "Coalesce feedback", exact: true }).click();
    await expect(editor.getByText("This cycle is closed and its history is immutable.", { exact: false })).toBeVisible();
    const frozen = (await view()).cycles[0];
    const integrated = structuredClone(original); integrated.proposal.revision_id = "test-r2-integrated";
    integrated.proposal.supersedes_revision = "test-r1"; integrated.proposal.title = "Integrated after peer and community feedback";
    await revealReviewTools(editor);
    await editor.getByLabel("Candidate proposal source").fill(JSON.stringify(integrated));
    await revealReviewTools(editor);
    await editor.getByRole("button", { name: "Save new draft attachment", exact: true }).click();
    await expect(editor.getByLabel("Proposal attachment", { exact: true }).locator("option")).toHaveCount(2);
    await expect(editor.getByLabel("Candidate proposal source")).toHaveValue(/test-r2-integrated/);
    await revealReviewTools(editor);
    await editor.getByRole("button", { name: "Return to peer review", exact: true }).click();
    await expect(editor.getByLabel("Feedback proposal").locator("option")).toHaveCount(2);
    state = await view(); const cycle2 = state.cycles[1], secondPeer = cycle2.rounds[0];
    await visit(alice, "reviewer-a", cycle2.id, secondPeer.id);
    await expect(alice.getByLabel("Candidate proposal source")).toHaveValue(/test-r2-integrated/);
    await revealReviewTools(alice);
    await alice.getByLabel("Your feedback", { exact: true }).fill("Second cycle review");
    await revealReviewTools(alice);
    await alice.getByRole("button", { name: "Submit feedback", exact: true }).click();
    await alice.getByRole('button', {name: 'Share feedback', exact: true}).click();
    await expect(alice.getByLabel("Your feedback", { exact: true })).toHaveValue("");
    await revealReviewTools(editor);
    await refresh(editor); await editor.getByRole("button", { name: "Coalesce feedback", exact: true }).click();
    await expect(editor.getByText("This cycle is closed and its history is immutable.", { exact: false })).toBeVisible();
    expect((await view()).cycles[0]).toEqual(frozen);
    const unauthorized = await request.post(`${api}/api/v1/flows/${flow}/review-cycles`, {
      headers: { "X-Review-Fixture-Principal": "community" }, data: { expectedRevision: (await view()).revision, operation: "OPEN_ROUND", cycleId: cycle2.id, audience: "REVIEWER" } });
    expect(unauthorized.status()).toBe(403);
    const late = await request.post(`${api}/api/v1/flows/${flow}/review-cycles`, {
      headers: { "X-Review-Fixture-Principal": "reviewer-a" }, data: { expectedRevision: (await view()).revision, operation: "SAVE_CONTRIBUTION", cycleId: cycle1.id, roundId: peer.id, text: "late overwrite", submit: true } });
    expect(late.status()).toBe(409);
    const restart = await request.post(`${api}/fixture/restart`, { headers: { "X-Review-Fixture-Principal": "editor" } }); expect(restart.status()).toBe(200);
    expect((await view()).cycles[0]).toEqual(frozen);
    await citizen.reload(); await expect(citizen.getByText("Community contribution", { exact: true })).toBeVisible();
    await revealReviewTools(citizen);
    await expect(citizen.getByLabel("Candidate proposal source")).toHaveValue(/test-r1/);
    await expect(citizen.getByText("Reviewer A submitted feedback", { exact: true })).toHaveCount(0);
    await editor.reload(); await expect(editor.getByLabel("Feedback proposal").locator("option")).toHaveCount(2);
    await revealReviewTools(editor);
    await expect(editor.getByLabel("Candidate proposal source")).toHaveValue(/test-r2-integrated/);
    const beforeTermination = await view();
    expect(beforeTermination.phase).toBe("COALESCING");
    const frozenCycles = structuredClone(beforeTermination.cycles);
    // An unchanged automatic seed does not require saving or discarding to terminate.
    await expect(editor.getByRole("button", { name: "Return to peer review", exact: true })).toBeDisabled();
    await revealReviewTools(editor);
    await editor.getByRole("button", { name: "Terminate flow without publication", exact: true }).click();
    await expect(editor.getByText("This flow is terminated.", { exact: false })).toBeVisible();
    const terminated = await view();
    expect(terminated.phase).toBe("TERMINATED");
    expect(terminated.cycles).toEqual(frozenCycles);
    expect(terminated.cycles[0]).toEqual(frozen);
    expect(terminated.canOpenCycle).toBe(false); expect(terminated.canCoalesce).toBe(false);
    expect(terminated.canTerminate).toBe(false); expect(terminated.canOpenRound).toBe(false);
    await expect(editor.getByRole("button", { name: "Return to peer review", exact: true })).toHaveCount(0);
    await expect(editor.getByRole("button", { name: "Terminate flow without publication", exact: true })).toHaveCount(0);
    await editor.reload();
    await revealReviewTools(editor);
    await expect(editor.getByText("This flow is terminated.", { exact: false })).toBeVisible();
    await expect(editor.getByLabel("Candidate proposal source")).toHaveAttribute("readonly");
    await revealReviewTools(editor);
    await editor.getByLabel("Stage", { exact: true }).selectOption(peer.stageId);
    await expect(editor.getByText("Historical stage.", { exact: false })).toBeVisible();
    await expect(editor.getByLabel("Candidate proposal source")).toHaveValue(/test-r1/);
    await expect(editor.getByLabel("Candidate proposal source")).toHaveAttribute("readonly");
    await expect(editor.getByRole("button", { name: "Save new draft attachment", exact: true })).toBeDisabled();
    expect((await view()).cycles).toEqual(frozenCycles);
    const notice1 = await (await request.post(`${api}/fixture/notices`, { headers: { "X-Review-Fixture-Principal": "editor" } })).json();
    expect(notice1.replayed).toBeGreaterThan(0); expect(notice1.dispatched).toBeGreaterThan(0); expect(notice1.accepted).toBeGreaterThan(0);
    const notice2 = await (await request.post(`${api}/fixture/notices`, { headers: { "X-Review-Fixture-Principal": "editor" } })).json();
    expect(notice2.replayed).toBe(0); expect(notice2.dispatched).toBe(0); expect(notice2.accepted).toBe(notice1.accepted);
    if (process.env.KLAB_REVIEW_E2E_REPORT) writeFileSync(process.env.KLAB_REVIEW_E2E_REPORT, JSON.stringify({
      result: "passed", authentication: "Four simulated authenticated scopes on a loopback-only fixture bridge; no IdP login",
      actors: ["editor", "reviewer-a", "reviewer-b", "community"], closedCycles: 2, overlappingAudiences: ["REVIEWER", "COMMUNITY"],
      originalRevision: frozen.candidate.revisionId, integratedRevision: cycle2.candidate.revisionId,
      originalProposalChecksum: frozen.candidate.proposal.checksum, integratedProposalChecksum: cycle2.candidate.proposal.checksum,
      peerContributors: ["reviewer-a", "reviewer-b"], communityContributors: ["community"],
      privateDraftIsolation: true, contributorAndProposalFilters: true, staleRevisionRecovery: true,
      actualBootstrapForm: true, atomicReferenceUpload: true, proposalFileUpload: true, editorPreparationNote: true,
      titledDescribedStage: true, explicitStartAnotherAndCancel: true, creationCount: 1,
      immutableClosureAfterLaterCycleAndRestart: true, nativeStoreAndManagerRestarts: 1,
      finalPhase: "TERMINATED", terminationIsNotPublication: true, historicalReadOnlyAfterTermination: true,
      fakeNotices: notice1, duplicateReplay: notice2, realMail: false, archiveDispatch: false,
    }, null, 2));
  } finally { await Promise.all(contexts.map(c => c.close())); }
});
