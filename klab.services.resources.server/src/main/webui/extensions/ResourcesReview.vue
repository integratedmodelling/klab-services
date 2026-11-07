<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import ReviewCyclesPanel from "./ReviewCyclesPanel.vue";
import type { ReviewCycle, ReviewView } from "../review-model";

const props = defineProps<{ context: DashboardContext }>();
interface Attachment { id: string; type: string; fileName: string; mediaType: string; createdBy?: string }
interface Stage { id: string; title?: string; schemaId: string; status: string; attachments: Attachment[] }
interface Flow { owner?: string; id: string; assetUrn: string; revision: number; states: Record<string, Stage>; currentStateIds: string[] }
interface Action { id: string; label: string; revision: number; missing: { name: string; javaType?: string }[] }
const flows = ref<Flow[]>([]), flowId = ref(""), stageId = ref("");
const flow = ref<Flow>(), actions = ref<Action[]>([]), busy = ref(false), error = ref("");
const flowAttempted = ref(false), feedbackDirty = ref(false), reviewedCycle = ref<ReviewCycle>();
let reviewCandidateGeneration = 0;
const selectedProposal = ref(""), selectedContext = ref(""), proposalText = ref(""), originalText = ref(""), contextText = ref("");
const values = ref<Record<string, string | number>>({});
const reviewView = ref<ReviewView>(), creation = ref<{canCreate: boolean}>(), newAsset = ref("");
const panelKey = ref(0);
const uncertainCreation = ref(""), creationReconciled = ref(false);
const creationRecoveryKey = computed(() => props.context.auth?.authenticated && props.context.auth.username
  ? `klab-ontology-review-pending-creation:${window.location.pathname}:${encodeURIComponent(props.context.auth.username)}` : "");
const knownCreationIds = ref<string[]>([]);
function restorePendingCreation() {
  uncertainCreation.value = ""; knownCreationIds.value = []; creationReconciled.value = false;
  if (!creationRecoveryKey.value) return;
  try {
    const saved = JSON.parse(sessionStorage.getItem(creationRecoveryKey.value) || "null");
    if (saved && typeof saved.asset === "string" && Array.isArray(saved.knownIds) && saved.knownIds.every((id: unknown) => typeof id === "string")) {
      uncertainCreation.value = saved.asset; knownCreationIds.value = saved.knownIds;
    }
  } catch { /* A malformed or unavailable recovery record grants no access. */ }
}
watch(creationRecoveryKey, restorePendingCreation);
function pendingCreation(asset: string) {
  uncertainCreation.value = asset; creationReconciled.value = false;
  knownCreationIds.value = asset ? flows.value.map(f => f.id) : [];
  if (!creationRecoveryKey.value) return;
  try { if (asset) sessionStorage.setItem(creationRecoveryKey.value, JSON.stringify({asset, knownIds: knownCreationIds.value}));
    else sessionStorage.removeItem(creationRecoveryKey.value); } catch { /* In-memory protection still applies. */ }
}
const matchingCreations = computed(() => creationReconciled.value ? flows.value.filter(f => f.assetUrn === uncertainCreation.value
  && !knownCreationIds.value.includes(f.id) && f.owner === props.context.auth?.username
  && Object.values(f.states).some(s => s.schemaId === "bootstrap" && s.status === "OPEN")) : []);
async function reconcileCreation() {
  if (busy.value) return;
  busy.value = true; error.value = "";
  try {
    flows.value = await props.context.api.get<Flow[]>("api/v1/flows?includeClosed=true");
    creationReconciled.value = true;
    if (!matchingCreations.value.length) {
      pendingCreation("");
      error.value = "No matching flow appeared in the refreshed list. Check the list before explicitly trying creation again; the earlier response was uncertain.";
    }
  } catch (cause) { failed(cause); } finally { busy.value = false; }
}
async function openRecoveredFlow(id: string) { flowId.value = id; pendingCreation(""); await changeFlow(); }

const routedStage = computed(() => reviewView.value?.stages?.find(s => s.id === stageId.value));
const stageTitle = computed(() => ({ bootstrap: "Creator bootstrap", "peer-review": "Peer review", "community-review": "Community review", coalescing: "Editor coalescing", terminated: "Terminated flow" } as Record<string, string>)[routedStage.value?.schemaId || stage.value?.schemaId || ""] || "Ontology review");
const stage = computed(() => flow.value?.states[stageId.value]);
const proposals = computed(() => stage.value?.attachments.filter(a => a.mediaType === "application/vnd.klab.proposal+yaml") || []);
const contextAttachments = computed(() => stage.value?.attachments.filter(a => a.mediaType !== "application/vnd.klab.proposal+yaml") || []);
const dirty = computed(() => proposalText.value !== originalText.value);
const localProposal = computed(() => proposals.value.some(p => p.id === selectedProposal.value));
const pendingCandidateSave = computed(() => canEdit.value && !!proposalText.value && !localProposal.value);
const canEdit = computed(() => !!routedStage.value?.canEdit && stage.value?.status === "OPEN");
const visibleActions = computed(() => actions.value.filter(action => action.id !== "prepare-review-draft" ||
  !stage.value?.attachments.some(item => ["application/vnd.klab.proposal+yaml", "application/vnd.klab.ontology"].includes(item.mediaType))));
const endpoint = computed(() => `api/v1/flows/${encodeURIComponent(flowId.value)}`);
const stageEndpoint = computed(() => `${endpoint.value}/states/${encodeURIComponent(stageId.value)}`);
function failed(cause: unknown) {
  error.value = cause instanceof Error ? cause.message : String(cause);
  if (error.value.startsWith("409")) error.value += " The flow changed. Your text is retained; save it, or download then discard unsaved edits before refreshing and retrying.";
}
function routeStage(id: string) {
  if (["bootstrap", "coalescing", "terminated"].includes(routedStage.value?.schemaId || "")) return;
  stageId.value = id;
  const url = new URL(window.location.href); url.searchParams.set("stage", id); window.history.replaceState({}, "", url);
}
async function changeFlow() {
  stageId.value = ""; reviewView.value = undefined;
  const url = new URL(window.location.href); for (const name of ["stage", "cycle", "round"]) url.searchParams.delete(name);
  window.history.replaceState({}, "", url); panelKey.value++; await loadFlow();
}
async function changeStage() {
  const url = new URL(window.location.href); url.searchParams.delete("cycle"); url.searchParams.delete("round");
  url.searchParams.set("stage", stageId.value); window.history.replaceState({}, "", url);
  panelKey.value++; await loadFlow();
}
async function flowChanged(keepStage = false) {
  if (!keepStage) stageId.value = reviewView.value?.editableStageId || reviewView.value?.stages?.filter(s => s.open).at(-1)?.id || reviewView.value?.stages?.at(-1)?.id || "";
  if (!keepStage) { const url = new URL(window.location.href); url.searchParams.delete("cycle"); url.searchParams.delete("round"); window.history.replaceState({}, "", url); }
  await loadFlow(); panelKey.value++;
}
async function createFlow() {
  if (uncertainCreation.value || !creation.value?.canCreate || !newAsset.value.trim() || busy.value || keepDraft()) return;
  busy.value = true; error.value = "";
  pendingCreation(newAsset.value.trim());
  try {
    const created = await props.context.api.request<Flow>("api/v1/flows?workflowId=ontology-review-pilot", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ schemaId: "bootstrap", assetUrn: newAsset.value.trim(), assetType: "ONTOLOGY", title: "Creator bootstrap" }),
    });
    pendingCreation("");
    flows.value.push(created); flowId.value = created.id; stageId.value = created.currentStateIds[0] || "";
    reviewView.value = undefined;
    const url = new URL(window.location.href); url.searchParams.delete("cycle"); url.searchParams.delete("round"); window.history.replaceState({}, "", url);
    panelKey.value++; await loadFlow();
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    if (/^4\d\d\b/.test(message) && !message.startsWith("408")) pendingCreation("");
    failed(cause);
  } finally { busy.value = false; }
}
function inputLabel(name: string): string {
  return ({ proposal_id: "Proposal ID", revision_id: "Revision ID", domain: "Domain", purpose: "Purpose",
    requested_tier: "Requested tier", target_community: "Target community" } as Record<string, string>)[name] || name.replaceAll("_", " ");
}
function keepDraft(): boolean {
  if (!dirty.value && !feedbackDirty.value) return false;
  error.value = "Save your feedback or save your edited proposal before changing the view. You can also download and explicitly discard unsaved edits."; return true;
}
function reviewRevision(revision: number) { if (flow.value) flow.value.revision = revision; }
function reviewAccessLost() {
  if (canEdit.value) return; // Preserve an editor's own editable candidate for recovery.
  reviewCandidateGeneration++; reviewedCycle.value = undefined; flow.value = undefined; actions.value = [];
  busy.value = false;
  proposalText.value = ""; originalText.value = ""; selectedProposal.value = ""; selectedContext.value = ""; contextText.value = "";
}
async function reviewCandidate(cycle: ReviewCycle) {
  reviewedCycle.value = cycle;
  if ((canEdit.value || ["bootstrap", "coalescing"].includes(stage.value?.schemaId || "")) && selectedProposal.value) return; // Keep the editable draft intact while inspecting feedback.
  if (dirty.value) return;
  const generation = ++reviewCandidateGeneration;
  busy.value = true;
  try {
    const path = `${endpoint.value}/review-cycles/${encodeURIComponent(cycle.id)}/attachments/`;
    const [encoded, source] = await Promise.all([
      attachmentBase64(path + encodeURIComponent(cycle.candidate.proposal.attachmentId)),
      cycle.candidate.ontology ? attachmentBase64(path + encodeURIComponent(cycle.candidate.ontology.attachmentId)) : Promise.resolve(""),
    ]);
    if (generation !== reviewCandidateGeneration) return;
    const text = new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(atob(encoded), c => c.charCodeAt(0)));
    try { proposalText.value = JSON.stringify(JSON.parse(text), null, 2); } catch { proposalText.value = text; }
    originalText.value = proposalText.value; selectedProposal.value = cycle.candidate.proposal.attachmentId;
    contextText.value = source ? new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(atob(source), c => c.charCodeAt(0))) : "";
  } catch (cause) { if (generation === reviewCandidateGeneration) failed(cause); }
  finally { if (generation === reviewCandidateGeneration) busy.value = false; }
}
async function attachmentText(id: string): Promise<string> {
  const encoded = await attachmentBase64(`${endpoint.value}/attachments/${encodeURIComponent(id)}`);
  return new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(atob(encoded), c => c.charCodeAt(0)));
}
async function attachmentBase64(path: string): Promise<string> {
  if (!props.context.api.text) return props.context.api.get<string>(path);
  const body = (await props.context.api.text(path)).trim();
  return body.startsWith('"') ? JSON.parse(body) as string : body;
}
async function loadProposal() {
  const generation = reviewCandidateGeneration;
  busy.value = true; error.value = "";
  try {
    const text = selectedProposal.value ? await attachmentText(selectedProposal.value) : "";
    if (generation !== reviewCandidateGeneration) return;
    try { proposalText.value = JSON.stringify(JSON.parse(text), null, 2); }
    catch { proposalText.value = text; }
    originalText.value = proposalText.value;
  }
  catch (cause) { if (generation === reviewCandidateGeneration) failed(cause); }
  finally { if (generation === reviewCandidateGeneration) busy.value = false; }
}
async function loadContext() {
  const generation = reviewCandidateGeneration;
  busy.value = true; error.value = "";
  try {
    const text = selectedContext.value ? await attachmentText(selectedContext.value) : "";
    if (generation === reviewCandidateGeneration) contextText.value = text;
  }
  catch (cause) { if (generation === reviewCandidateGeneration) failed(cause); }
  finally { if (generation === reviewCandidateGeneration) busy.value = false; }
}
async function loadFlow() {
  if (keepDraft() || !flowId.value) return;
  flowAttempted.value = false; reviewedCycle.value = undefined;
  reviewCandidateGeneration++;
  busy.value = true; error.value = ""; actions.value = [];
  try {
    flow.value = await props.context.api.get<Flow>(endpoint.value);
    if (!stageId.value) stageId.value = flow.value.currentStateIds[0] || Object.keys(flow.value.states)[0] || "";
    if (!stage.value) throw new Error("The requested stage is unavailable to this account. Choose an authorized stage.");
    const link = new URL(window.location.href); link.searchParams.set("flow", flowId.value); link.searchParams.set("stage", stageId.value);
    window.history.replaceState({}, "", link);
    if (stage.value.status === "OPEN") {
      try { actions.value = await props.context.api.get<Action[]>(`${stageEndpoint.value}/actions`); }
      catch { /* Read access does not imply permission to run editor actions. Server enforces both. */ }
    }
    for (const action of actions.value) for (const input of action.missing) {
      if (!(input.name in values.value)) values.value[input.name] = input.javaType === "Integer" || input.javaType === "java.lang.Integer" ? 1 : "";
    }
    selectedProposal.value = proposals.value.at(-1)?.id || "";
    selectedContext.value = contextAttachments.value[0]?.id || "";
    await loadProposal(); await loadContext();
    if (canEdit.value && stage.value.schemaId === "coalescing" && !selectedProposal.value && reviewView.value?.cycles.length) await reviewCandidate(reviewView.value.cycles.at(-1)!);
  } catch (cause) {
    flow.value = undefined; proposalText.value = ""; originalText.value = ""; contextText.value = "";
    if (!(cause instanceof Error && cause.message.startsWith("403"))) failed(cause);
  } finally { busy.value = false; flowAttempted.value = true; }
}
async function execute(action: Action) {
  if (keepDraft() || !flow.value) return;
  busy.value = true; error.value = "";
  try {
    const parameters = Object.fromEntries(action.missing.map(input => [input.name, values.value[input.name]]));
    await props.context.api.request(`${stageEndpoint.value}/actions/${encodeURIComponent(action.id)}`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expectedRevision: flow.value.revision, parameters }),
    });
    await loadFlow();
  } catch (cause) { failed(cause); } finally { busy.value = false; }
}
async function saveProposal() {
  if (!canEdit.value || (!dirty.value && !pendingCandidateSave.value) || feedbackDirty.value) return;
  busy.value = true; error.value = "";
  try {
    JSON.parse(proposalText.value);
    const bytes = new TextEncoder().encode(proposalText.value);
    if (bytes.byteLength > 4 * 1024 * 1024) throw new Error("Proposal exceeds 4 MiB.");
    const chunks: string[] = [];
    for (let i = 0; i < bytes.length; i += 8192) chunks.push(String.fromCharCode(...bytes.subarray(i, i + 8192)));
    await props.context.api.request(`${stageEndpoint.value}/attachments`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ type: "bootstrap-proposal", fileName: `proposal-draft-${Date.now()}.json`,
        mediaType: "application/vnd.klab.proposal+yaml", content: btoa(chunks.join("")) }),
    });
    if (stage.value?.schemaId === "coalescing" && reviewedCycle.value?.candidate.ontology && contextText.value
        && !stage.value.attachments.some(a => a.type === "candidate-ontology")) {
      const source = new TextEncoder().encode(contextText.value), parts: string[] = [];
      for (let i = 0; i < source.length; i += 8192) parts.push(String.fromCharCode(...source.subarray(i, i + 8192)));
      await props.context.api.request(`${stageEndpoint.value}/attachments`, {method: "POST", headers: {"Content-Type":"application/json"},
        body: JSON.stringify({type:"candidate-ontology", fileName:"candidate.kwv", mediaType:"application/vnd.klab.ontology", content:btoa(parts.join(""))})});
    }
    originalText.value = proposalText.value; await loadFlow();
  } catch (cause) { failed(cause); } finally { busy.value = false; }
}
function discardProposalEdits() {
  proposalText.value = originalText.value;
  if (!originalText.value) selectedProposal.value = "";
}
function downloadDraft() {
  const url = URL.createObjectURL(new Blob([proposalText.value], { type: "application/json" }));
  const link = document.createElement("a"); link.href = url; link.download = "proposal-draft.json"; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
onMounted(async () => {
  restorePendingCreation();
  const params = new URL(window.location.href).searchParams;
  flowId.value = params.get("flow") || ""; stageId.value = params.get("stage") || "";
  try { creation.value = await props.context.api.get<{canCreate: boolean}>("api/v1/workflows/ontology-review-pilot/creation"); } catch { /* Creation stays unavailable until authorized by the server. */ }
  try { flows.value = await props.context.api.get<Flow[]>("api/v1/flows?includeClosed=true"); }
  catch (cause) { failed(cause); }
  if (flowId.value) await loadFlow();
});
</script>

<template>
  <section class="ontology-review" aria-label="Ontology review stage">
    <h1>{{ stageTitle }}</h1>
    <p v-if="routedStage?.schemaId === 'bootstrap'">Only the creating editor can prepare this initial candidate from authored annotations and BOOTSTRAP.md.</p>
    <p v-else-if="routedStage?.schemaId === 'coalescing'">Integrate submitted feedback into a fresh revision. Return to review when ready, or terminate without accepting or publishing.</p>
    <p v-else>Review the frozen candidate and its evidence. Peer and community feedback have separate stages and access.</p>
    <p v-if="routedStage && !routedStage.open" role="status">Historical stage. The candidate and submitted contributions are read-only.</p>
    <fieldset v-if="creation?.canCreate" :disabled="busy || dirty || feedbackDirty">
      <legend>Start an ontology review</legend>
      <label>Ontology URN<input :disabled="!!uncertainCreation" v-model="newAsset" placeholder="project.ontology" /></label>
      <button :disabled="!newAsset.trim() || !!uncertainCreation" @click="createFlow">Create private bootstrap</button>
      <div v-if="uncertainCreation" role="status">
        <p>Creation of {{ uncertainCreation }} has an unknown outcome. Do not create it again until you refresh existing flows and check for the saved bootstrap.</p>
        <button @click="reconcileCreation">Refresh existing flows</button>
        <button v-for="item in matchingCreations" :key="item.id" @click="openRecoveredFlow(item.id)">Open existing bootstrap {{ item.id }}</button>
      </div>
    </fieldset>
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="controls">
      <label>Flow <select v-model="flowId" :disabled="busy || dirty || feedbackDirty" @change="changeFlow">
        <option value="">Choose a flow</option>
        <option v-for="item in flows" :key="item.id" :value="item.id">{{ item.assetUrn }} · {{ item.id }}</option>
      </select></label>
      <label v-if="reviewView?.stages?.length">Stage <select aria-label="Stage" v-model="stageId" :disabled="busy || dirty || feedbackDirty" @change="changeStage">
        <option v-for="item in reviewView?.stages" :key="item.id" :value="item.id">{{ item.schemaId }} · {{ item.open ? "Open" : "Historical" }}</option>
      </select></label>
      <button :disabled="busy || dirty || feedbackDirty || !flowId" @click="loadFlow">Refresh</button>
    </div>
    <p v-if="stage">{{ flow?.assetUrn }} · {{ stage.title || stage.schemaId }} · {{ stage.status }} · revision {{ flow?.revision }}</p>
    <fieldset v-for="action in (canEdit ? visibleActions : [])" :key="action.id" :disabled="busy || dirty || feedbackDirty">
      <legend>{{ action.label }}</legend>
      <div class="action-inputs"><label v-for="input in action.missing" :key="input.name">{{ inputLabel(input.name) }}
        <input v-if="input.javaType?.endsWith('Integer')" v-model.number="values[input.name]" type="number" min="1" />
        <input v-else v-model="values[input.name]" required />
      </label></div>
      <button :disabled="action.missing.some(input => values[input.name] === '' || values[input.name] === undefined)" @click="execute(action)">{{ action.label }}</button>
    </fieldset>
    <div v-if="flowAttempted && flowId" class="face-to-face">
      <section><h2>{{ canEdit ? 'Current candidate draft' : 'Reviewed candidate' }}</h2>
        <label v-if="canEdit">Proposal <select v-model="selectedProposal" aria-label="Proposal attachment" :disabled="busy || dirty" @change="loadProposal">
          <option value="">No proposal selected</option>
          <option v-for="item in proposals" :key="item.id" :value="item.id">{{ item.fileName }} · {{ item.createdBy }}</option>
        </select></label>
        <textarea v-model="proposalText" aria-label="Candidate proposal JSON" :readonly="!canEdit" :disabled="busy || !selectedProposal" spellcheck="false" />
        <div class="controls"><button :disabled="busy || !canEdit || (!dirty && !pendingCandidateSave) || feedbackDirty" @click="saveProposal">Save new draft attachment</button>
          <button :disabled="!proposalText" @click="downloadDraft">Download draft</button>
          <button v-if="dirty" :disabled="busy" @click="discardProposalEdits">Discard unsaved proposal edits</button></div>
        <p v-if="pendingCandidateSave && !dirty" role="status">This is an unchanged seed from the previous candidate. Save it into this stage before returning to review, or terminate without saving.</p>
        <p v-if="dirty" role="status">Unsaved edits. Saving keeps earlier attachments intact.</p>
        <p v-if="canEdit">For an integrated proposal, use a fresh revision ID and record the revision it supersedes before starting another cycle.</p>
      </section>
      <section>
        <ReviewCyclesPanel :context="context" :flow-id="flowId" :key="panelKey" :stage-id="stageId" :source-state-id="reviewView?.editableStageId"
          :proposal-attachment-id="canEdit && localProposal ? selectedProposal : undefined"
          :ontology-attachment-id="stage?.attachments.find(a => a.type === 'candidate-ontology')?.id"
          :candidate-dirty="dirty" :candidate-loading="busy" @view="reviewView = $event" @stage="routeStage" @changed="flowChanged" @candidate="reviewCandidate" @revision="reviewRevision" @dirty="feedbackDirty = $event" @access-lost="reviewAccessLost" />
        <details v-if="stage"><summary>Comments and source context</summary>
        <label>Attachment <select v-model="selectedContext" aria-label="Context attachment" :disabled="busy" @change="loadContext">
          <option value="">No context selected</option>
          <option v-for="item in contextAttachments" :key="item.id" :value="item.id">{{ item.fileName }} · {{ item.createdBy }}</option>
        </select></label>
        <textarea :value="contextText" aria-label="Source context and comments" readonly spellcheck="false" />
        </details>
        <details v-else-if="contextText"><summary>Reviewed ontology source</summary><pre>{{ contextText }}</pre></details>
      </section>
    </div>
  </section>
</template>

<style scoped>
.ontology-review { max-width: 1500px; margin: auto; }
.controls, .action-inputs { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 14px 0; }
label { display: grid; gap: 6px; min-width: 160px; }
input, select, button { padding: 8px; color: inherit; background: transparent; border: 1px solid #70999d; border-radius: 4px; }
button { cursor: pointer; } button:disabled { cursor: default; opacity: .55; }
fieldset { border: 1px solid #70999d; margin: 20px 0; }
.face-to-face { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; }
.face-to-face section { min-width: 0; }
textarea { width: 100%; height: 55vh; min-height: 280px; margin-top: 12px; padding: 12px; color: inherit; background: #10272a; border: 1px solid #70999d; font-family: monospace; }
[role="alert"] { color: #ffb0b0; }
@media (max-width: 850px) { .face-to-face { grid-template-columns: 1fr; } }
</style>
