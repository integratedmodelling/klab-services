import { createApp, h } from "vue";
import ResourcesReview from "../../../../../klab.services.resources.server/src/main/webui/extensions/ResourcesReview.vue";
import type { ReviewView } from "../../../../../klab.services.resources.server/src/main/webui/review-model";
const calls: unknown[] = []; Object.assign(window, { calls });
const view: ReviewView = { revision: 7, canManage: false, identity: "community-user", contributableRoundIds: ["community-1", "community-2"],
  cycles: [1, 2].map(index => ({ id: `cycle-${index}`, sourceStateId: "stage-1",
    candidate: { proposalId: "ontology-review", revisionId: `r${index}`, proposal: { attachmentId: `proposal-${index}`, checksum: "fixture" } },
    rounds: [{ id: `community-${index}`, audience: "COMMUNITY", contributions: index === 1 ? [
      { id: "alice", owner: "Alice", text: "Alice feedback", submitted: true, updatedAt: "2026-11-06T12:00:00Z" },
      { id: "bob", owner: "Bob", text: "Bob feedback", submitted: true, updatedAt: "2026-11-06T12:00:01Z" },
      { id: "own-draft", owner: "community-user", text: "My private draft", submitted: false, updatedAt: "2026-11-06T12:00:02Z" },
      { id: "other-draft", owner: "community-user", text: "My second private draft", submitted: false, updatedAt: "2026-11-06T12:00:03Z" },
    ] : [{ id: "second", owner: "Alice", text: "Second proposal feedback", submitted: true, updatedAt: "2026-11-06T13:00:00Z" }] }] })) };
const context = { api: {
  text: async (path: string) => btoa(JSON.stringify({ proposal: { id: "ontology-review", revision_id: path.endsWith("proposal-1") ? "r1" : "r2" } })),
  get: async (path: string) => {
    if (path.includes("/attachments/")) return btoa(JSON.stringify({ proposal: { id: "ontology-review", revision_id: path.endsWith("proposal-1") ? "r1" : "r2" } }));
    if (path.endsWith("/review-cycles")) return structuredClone(view);
    if (path.includes("?")) return [];
    throw new Error("403 Forbidden");
  },
  request: async (path: string, init: RequestInit) => {
    const command = JSON.parse(String(init.body)); calls.push({ path, command });
    if (location.search.includes("conflict")) throw new Error("409 Conflict");
    const round = view.cycles.find(c => c.id === command.cycleId)!.rounds.find(r => r.id === command.roundId)!;
    view.revision++;
    const contribution = { id: command.contributionId || `new-${view.revision}`, owner: view.identity,
      text: command.text, submitted: command.submit, updatedAt: "2026-11-06T14:00:00Z" };
    if (command.contributionId) round.contributions = round.contributions.map(c => c.id === command.contributionId ? contribution : c);
    else round.contributions.push(contribution);
    return structuredClone(view);
  },
} };
createApp({ render: () => h(ResourcesReview, { context: context as never }) }).mount("#app");
