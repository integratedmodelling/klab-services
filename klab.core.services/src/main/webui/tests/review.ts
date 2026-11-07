import { createApp, h } from "vue";
import ResourcesReview from "../../../../../klab.services.resources.server/src/main/webui/extensions/ResourcesReview.vue";
const calls: unknown[] = [];
Object.assign(window, { calls });
const recoveredFixture = JSON.parse(sessionStorage.getItem("review-fixture-created") || "null");
const state = { id: "stage-1", title: recoveredFixture?.title || "", description: recoveredFixture?.description || "", schemaId: "bootstrap", status: "OPEN", attachments: [] as Record<string, string>[] };
const flow = { owner: "editor", id: recoveredFixture?.id || "flow-1", assetUrn: recoveredFixture?.assetUrn || "test.ontology", revision: 3, states: { "stage-1": state } as Record<string, typeof state>, currentStateIds: ["stage-1"] };
const content: Record<string, string> = {};
let droppedUpload = false;
function syncDetails() {
  if (!location.search.includes("shared-details")) return;
  const shared = JSON.parse(localStorage.getItem("review-fixture-details") || "null");
  if (shared) { state.title = shared.title; state.description = shared.description; flow.revision = shared.revision; }
}
let phase = "BOOTSTRAP", cycles: any[] = [], stages: any[] = [{ id: "stage-1", schemaId: "bootstrap", open: true, canEdit: true }];
function review() { const manager = !location.search.includes("reader"); return { revision: flow.revision, cycles, phase,
 canManage: manager, identity: "editor", contributableRoundIds: [], stages: stages.map(s => ({...s, canEdit: s.canEdit && manager})),
 editableStageId: stages.find(s => s.open && s.canEdit)?.id, canOpenCycle: manager && ["BOOTSTRAP", "COALESCING"].includes(phase),
 canCoalesce: manager && phase === "REVIEW", canTerminate: manager && phase === "COALESCING", canOpenRound: manager && phase === "REVIEW" }; }
const context = { auth: { authenticated:true, username:new URL(location.href).searchParams.get("user") || "editor" }, api: {
  text: async (path: string) => btoa(content[path.split("/").at(-1)!]),
  get: async (path: string) => {
    syncDetails();
    if (path.endsWith("/creation")) return {canCreate: !location.search.includes("reader")};
    if (path.endsWith("/review-cycles")) return structuredClone(review());
    if (path.includes("/attachments/")) return btoa(content[path.split("/").at(-1)!]);
    if (path.endsWith("/actions")) {
      if (location.search.includes("reader")) throw new Error("403 Forbidden");
      if (location.search.includes("action-failed")) throw new Error("503 Service unavailable");
      return [{ id: "prepare-review-draft", label: "Prepare review draft", revision: flow.revision,
        missing: (location.search.includes("context-missing") ? ["ontology", "project"] : ["proposal_id", "revision_id", "domain", "purpose", "target_community"]).map(name => ({ name, javaType: "String" }))
          .concat([{ name: "requested_tier", javaType: "Integer" }]) }];
    }
    return structuredClone(path.includes("?") ? [flow] : flow);
  },
  request: async (path: string, init: RequestInit) => {
    const command = JSON.parse(String(init.body)); calls.push({ path, command });
    if (location.search.includes("conflict")) throw new Error("409 Conflict");
    if (path.endsWith("/attachments") && location.search.includes("upload-dropped") && !droppedUpload) { droppedUpload = true; throw new Error("500 Connection interrupted before save"); }
    if (path === "api/v1/flows/initialize?workflowId=ontology-review-pilot") {
      flow.assetUrn = command.initialState.assetUrn; flow.id="flow-new";
      state.title = command.initialState.title; state.description = command.initialState.description;
      for (const upload of command.attachments) { const id = `initial-${state.attachments.length + 1}`; content[id] = atob(upload.content); state.attachments.push({ id, ...upload, createdBy:"editor" }); }
      sessionStorage.setItem("review-fixture-created",JSON.stringify({id:flow.id,assetUrn:flow.assetUrn,title:state.title,description:state.description}));
      if (location.search.includes("creation-interrupted")) { await new Promise(resolve=>setTimeout(resolve,250)); throw new Error("500 Connection interrupted after save"); } return structuredClone(flow);
    }
    if (init.method === "PATCH") {
      syncDetails();
      if (command.expectedRevision !== flow.revision) throw new Error("409 Conflict");
      const selected = flow.states[path.split("/states/")[1]];
      selected.title = command.title; selected.description = command.description; flow.revision++;
      if (location.search.includes("shared-details")) localStorage.setItem("review-fixture-details",JSON.stringify({title:selected.title,description:selected.description,revision:flow.revision}));
      return structuredClone(flow);
    }
    if (path.endsWith("/review-cycles")) {
      if (command.expectedRevision !== flow.revision) throw new Error("409 Conflict");
      if (command.operation === "OPEN_CYCLE") {
        stages.forEach(s => s.open = false); Object.values(flow.states).forEach(s => s.status = "CLOSED");
        const id = `peer-${cycles.length + 1}`, cycleId = `cycle-${cycles.length + 1}`;
        cycles.push({id: cycleId, sourceStateId: command.sourceStateId, candidate: {proposalId: "test-proposal", revisionId: `r${cycles.length+1}`, proposal: {attachmentId: command.proposalAttachmentId, checksum: "test"}, ontology: command.ontologyAttachmentId ? {attachmentId:command.ontologyAttachmentId,checksum:"source-test"} : undefined}, rounds: [{id, stageId:id, audience:"REVIEWER", contributions: []}]});
        stages.push({id, schemaId:"peer-review", cycleId, roundId:id, open:true, canEdit:false});
        flow.states[id] = {id, title:"", description:"", schemaId:"peer-review", status:"OPEN", attachments:[]}; phase = "REVIEW"; flow.currentStateIds=[id];
      } else if (command.operation === "OPEN_ROUND") {
        const c=cycles.find(c=>c.id===command.cycleId), id=`round-${c.rounds.length+1}`;
        c.rounds.push({id,stageId:id,audience:command.audience,contributions:[]});
        const schemaId=command.audience === "COMMUNITY" ? "community-review" : "peer-review";
        stages.push({id,schemaId,cycleId:c.id,roundId:id,open:true,canEdit:false});
        flow.states[id]={id,title:"",description:"",schemaId,status:"OPEN",attachments:[]};flow.currentStateIds.push(id);
      } else if (command.operation === "CLOSE_ROUND") {
        const r=cycles.find(c=>c.id===command.cycleId).rounds.find((r:any)=>r.id===command.roundId);
        r.closedAt="now"; stages.find(s=>s.id===r.stageId).open=false;flow.states[r.stageId].status="CLOSED";
        flow.currentStateIds=flow.currentStateIds.filter(id=>id!==r.stageId);
      } else if (command.operation === "ENTER_COALESCING") {
        stages.forEach(s => s.open = false); Object.values(flow.states).forEach(s => s.status = "CLOSED");
        cycles.forEach(c => {c.closedAt="now";c.rounds.forEach((r:any)=>r.closedAt="now");});
        const id=`coalescing-${cycles.length}`; stages.push({id,schemaId:"coalescing",open:true,canEdit:true});
        flow.states[id]={id,title:"",description:"",schemaId:"coalescing",status:"OPEN",attachments:[]}; flow.currentStateIds=[id]; phase="COALESCING";
      } else if (command.operation === "TERMINATE") { stages.forEach(s => s.open=false);Object.values(flow.states).forEach(s=>s.status="CLOSED");phase="TERMINATED";flow.currentStateIds=[]; }
      flow.revision++; return structuredClone(review());
    }
    if (path.includes("/actions/")) {
      content["proposal-1"] = JSON.stringify({ proposal: { id: "test-proposal", revision_id: "r1", status: "draft" } });
      content["comments-1"] = JSON.stringify({ comments: [] });
      state.attachments.push({ id: "proposal-1", type: "bootstrap-proposal", fileName: "r1-proposal.json", mediaType: "application/vnd.klab.proposal+yaml", createdBy: "editor" },
        { id: "comments-1", type: "bootstrap-comments", fileName: "r1-comments.json", mediaType: "application/vnd.klab.comments+json", createdBy: "editor" });
      if (location.search.includes("source-context")) {
        content["source-1"] = "namespace test.ontology;\n";
        state.attachments.push({id:"source-1",type:"candidate-ontology",fileName:"candidate.kwv",mediaType:"application/vnd.klab.ontology",createdBy:"editor"});
      }
    } else {
      const selected = flow.states[path.split("/states/")[1].split("/")[0]];
      const id = command.type === "bootstrap-proposal" ? `proposal-${Object.keys(content).filter(id=>id.startsWith('proposal-')).length+1}` : `material-${Object.keys(content).length+1}`;
      content[id] = atob(command.content);
      selected.attachments.push({ id, ...command, createdBy: "editor" });
      if (location.search.includes("upload-interrupted")) throw new Error("500 Connection interrupted after upload");
    }
    flow.revision++; return flow;
  },
} };
createApp({ render: () => h(ResourcesReview, { context: context as never }) }).mount("#app");
