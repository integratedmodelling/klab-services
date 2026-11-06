import { createApp, h } from "vue";
import ResourcesReview from "../../../../../klab.services.resources.server/src/main/webui/extensions/ResourcesReview.vue";
const calls: unknown[] = [];
Object.assign(window, { calls });
const state = { id: "stage-1", schemaId: "editing", status: "OPEN", attachments: [] as Record<string, string>[] };
const flow = { id: "flow-1", assetUrn: "test.ontology", revision: 3, states: { "stage-1": state }, currentStateIds: ["stage-1"] };
const content: Record<string, string> = {};
const context = { api: {
  get: async (path: string) => {
    if (path.includes("/attachments/")) return btoa(content[path.split("/").at(-1)!]);
    if (path.endsWith("/actions")) {
      if (location.search.includes("reader")) throw new Error("403 Forbidden");
      return [{ id: "prepare-review-draft", label: "Prepare review draft", revision: flow.revision,
        missing: ["proposal_id", "revision_id", "domain", "purpose", "target_community"].map(name => ({ name, javaType: "String" }))
          .concat([{ name: "requested_tier", javaType: "Integer" }]) }];
    }
    return structuredClone(path.includes("?") ? [flow] : flow);
  },
  request: async (path: string, init: RequestInit) => {
    const command = JSON.parse(String(init.body)); calls.push({ path, command });
    if (location.search.includes("conflict")) throw new Error("409 Conflict");
    if (path.includes("/actions/")) {
      content["proposal-1"] = JSON.stringify({ proposal: { id: "test-proposal", revision_id: "r1", status: "draft" } });
      content["comments-1"] = JSON.stringify({ comments: [] });
      state.attachments.push({ id: "proposal-1", type: "bootstrap-proposal", fileName: "r1-proposal.json", mediaType: "application/vnd.klab.proposal+yaml", createdBy: "editor" },
        { id: "comments-1", type: "bootstrap-comments", fileName: "r1-comments.json", mediaType: "application/vnd.klab.comments+json", createdBy: "editor" });
    } else {
      content["proposal-2"] = atob(command.content);
      state.attachments.push({ id: "proposal-2", ...command, createdBy: "editor" });
    }
    flow.revision++; return flow;
  },
} };
createApp({ render: () => h(ResourcesReview, { context: context as never }) }).mount("#app");
