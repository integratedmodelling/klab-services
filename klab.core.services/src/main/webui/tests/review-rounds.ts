import { createApp, h } from "vue";
import ResourcesReview from "../../../../../klab.services.resources.server/src/main/webui/extensions/ResourcesReview.vue";
import type { ReviewView } from "../../../../../klab.services.resources.server/src/main/webui/review-model";
const calls: unknown[] = []; Object.assign(window, { calls });
let denied = false;
let holdCandidates = new URL(location.href).searchParams.has("delay-candidate");
const pendingCandidates: (() => void)[] = [];
const view: ReviewView = { revision: 7, canManage: false, identity: "community-user", contributableRoundIds: ["community-1", "community-2"],
  cycles: [1, 2].map(index => ({ id: `cycle-${index}`, sourceStateId: "stage-1",
    candidate: { proposalId: "ontology-review", revisionId: `r${index}`, proposal: { attachmentId: `proposal-${index}`, checksum: "fixture" } },
    rounds: [{ id: `community-${index}`, audience: "COMMUNITY", contributions: index === 1 ? [
      { id: "alice", owner: "Alice", text: "Alice feedback", submitted: true, updatedAt: "2026-11-06T12:00:00Z" },
      { id: "bob", owner: "Bob", text: "Bob feedback", submitted: true, updatedAt: "2026-11-06T12:00:01Z" },
      { id: "own-draft", owner: "community-user", text: "My private draft", submitted: false, updatedAt: "2026-11-06T12:00:02Z" },
      { id: "other-draft", owner: "community-user", text: "My second private draft", submitted: false, updatedAt: "2026-11-06T12:00:03Z" },
    ] : [{ id: "second", owner: "Alice", text: "Second proposal feedback", submitted: true, updatedAt: "2026-11-06T13:00:00Z" }] }] })) };
if (new URL(location.href).searchParams.has("overlap")) {
  view.cycles[0].rounds[0].audience = "REVIEWER";
  view.cycles[0].rounds.push({ id: "public-1", audience: "COMMUNITY", contributions: [] });
  view.contributableRoundIds.push("public-1");
}
if (new URL(location.href).searchParams.has("stages")) {
  view.phase = "REVIEW";
  view.stages = view.cycles.flatMap(c => c.rounds.map(r => {
    r.stageId = r.id;
    return { id:r.id, schemaId:r.audience === "REVIEWER" ? "peer-review" : "community-review", cycleId:c.id, roundId:r.id, open:!r.closedAt, canEdit:false, canContribute:view.contributableRoundIds.includes(r.id) };
  }));
}
const context = { api: {
  text: async (path: string) => {
    if (holdCandidates) await new Promise<void>(resolve => pendingCandidates.push(resolve));
    return btoa(JSON.stringify({ proposal: { id: "ontology-review", revision_id: path.endsWith("proposal-1") ? "r1" : "r2" } }));
  },
  get: async (path: string) => {
    if (path.endsWith("/archive")) return { state: "BLOCKED" };
    if (denied && path.endsWith("/review-cycles")) throw new Error("403 Forbidden");
    if (path.includes("/attachments/")) return btoa(JSON.stringify({ proposal: { id: "ontology-review", revision_id: path.endsWith("proposal-1") ? "r1" : "r2" } }));
    if (path.endsWith("/review-cycles")) return structuredClone(view);
    if (path.includes("?")) return [];
    throw new Error("403 Forbidden");
  },
  request: async (path: string, init: RequestInit) => {
    if (path.endsWith("/archive")) { calls.push({ path, method: init.method }); return { state: "COMMITTED", commitId: "abc123" }; }
    const command = JSON.parse(String(init.body)); calls.push({ path, command });
    if (location.search.includes("conflict")) throw new Error("409 Conflict");
    const round = view.cycles.find(c => c.id === command.cycleId)!.rounds.find(r => r.id === command.roundId)!;
    view.revision++;
    const contribution = { id: command.contributionId || `new-${view.revision}`, owner: view.identity,
      text: command.text, submitted: command.submit, updatedAt: "2026-11-06T14:00:00Z",
      proposal: command.proposal ? { ...command.proposal, documentChecksum: "fixture", proposalId: "alternative", revisionId: "r3", schemaCheck: { status: "PASS" } } : undefined };
    if (command.contributionId) round.contributions = round.contributions.map(c => c.id === command.contributionId ? contribution : c);
    else round.contributions.push(contribution);
    return structuredClone(view);
  },
} };
if (new URL(location.href).searchParams.has("archive")) { view.canManage = true; view.cycles[0].closedAt = "2026-11-06T15:00:00Z"; }
Object.assign(window, { reviewFixture: {
  closeRound(id: string) {
    for (const cycle of view.cycles) for (const round of cycle.rounds) if (round.id === id) round.closedAt = "2026-11-06T15:00:00Z";
    view.contributableRoundIds = view.contributableRoundIds.filter(value => value !== id); view.revision++;
  },
  revoke() { denied = true; },
  hideRound(id: string) {
    for (const cycle of view.cycles) cycle.rounds = cycle.rounds.filter(round => round.id !== id);
    view.contributableRoundIds = view.contributableRoundIds.filter(value => value !== id); view.revision++;
  },
  pendingCandidates() { return pendingCandidates.length; },
  releaseCandidates() { holdCandidates = false; pendingCandidates.splice(0).forEach(resolve => resolve()); },
} });
createApp({ render: () => h(ResourcesReview, { context: context as never }) }).mount("#app");
