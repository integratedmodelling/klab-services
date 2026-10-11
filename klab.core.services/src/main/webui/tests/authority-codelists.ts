import { createApp, h } from "vue";
import AuthorityCodelists from "../../../../../klab.services.reasoner.server/src/main/webui/extensions/AuthorityCodelists.vue";
const calls: unknown[] = [];
Object.assign(window, { calls });
const proposal = { id: "p1", namespace: "taxonomy.species", alias: "FelisCatus", identity: "3DXV3", status: "PENDING" };
const context = {
  capabilities: { authorityBindings: [{ localId: "TAXA" }], permissions: location.search.includes("reader") ? ["READ"] : ["ADMINISTER"] },
  api: { request: async (_path: string, init: RequestInit) => {
    const command = JSON.parse(String(init.body)); calls.push(command);
    return { revision: 7, policies: { "taxonomy.species": { listId: "species",
      providerDeclared: !location.search.includes("community"), acceptsProposals: !location.search.includes("closed") } },
      codelists: { "taxonomy.species": { entries: [
      { code: "CommunityCat", value: "3DXV3" }, { code: "ManagedCat", value: "3DXV3" }
    ] } },
      proposals: [
        { ...proposal, id: "community", alias: "CommunityCat", approvedAlias: "CommunityCat", status: "ACCEPTED" },
        { ...proposal, id: "managed", alias: "ManagedCat", approvedAlias: "ManagedCat", status: "MANAGED" },
        { ...proposal, status: command.operation === "REVIEW" ? "ACCEPTED" : "PENDING" }] };
  } }
};
createApp({ render: () => h(AuthorityCodelists, { context: context as never }) }).mount("#app");
