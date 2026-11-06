<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";

const props = defineProps<{ context: DashboardContext }>();
interface Attachment { id: string; type: string; fileName: string; mediaType: string; createdBy?: string }
interface Stage { id: string; title?: string; schemaId: string; status: string; attachments: Attachment[] }
interface Flow { id: string; assetUrn: string; revision: number; states: Record<string, Stage>; currentStateIds: string[] }
interface Action { id: string; label: string; revision: number; missing: { name: string; javaType?: string }[] }
const flows = ref<Flow[]>([]), flowId = ref(""), stageId = ref("");
const flow = ref<Flow>(), actions = ref<Action[]>([]), busy = ref(false), error = ref("");
const selectedProposal = ref(""), selectedContext = ref(""), proposalText = ref(""), originalText = ref(""), contextText = ref("");
const values = ref<Record<string, string | number>>({});
const stage = computed(() => flow.value?.states[stageId.value]);
const proposals = computed(() => stage.value?.attachments.filter(a => a.mediaType === "application/vnd.klab.proposal+yaml") || []);
const contextAttachments = computed(() => stage.value?.attachments.filter(a => a.mediaType !== "application/vnd.klab.proposal+yaml") || []);
const dirty = computed(() => proposalText.value !== originalText.value);
const canEdit = computed(() => actions.value.length > 0 && stage.value?.status === "OPEN");
const visibleActions = computed(() => actions.value.filter(action => action.id !== "prepare-review-draft" ||
  !stage.value?.attachments.some(item => ["application/vnd.klab.proposal+yaml", "application/vnd.klab.ontology"].includes(item.mediaType))));
const endpoint = computed(() => `api/v1/flows/${encodeURIComponent(flowId.value)}`);
const stageEndpoint = computed(() => `${endpoint.value}/states/${encodeURIComponent(stageId.value)}`);
function failed(cause: unknown) { error.value = cause instanceof Error ? cause.message : String(cause); }
function inputLabel(name: string): string {
  return ({ proposal_id: "Proposal ID", revision_id: "Revision ID", domain: "Domain", purpose: "Purpose",
    requested_tier: "Requested tier", target_community: "Target community" } as Record<string, string>)[name] || name.replaceAll("_", " ");
}
function keepDraft(): boolean {
  if (!dirty.value) return false;
  error.value = "Save or download your edited proposal before changing the view."; return true;
}
async function attachmentText(id: string): Promise<string> {
  const encoded = await props.context.api.get<string>(`${endpoint.value}/attachments/${encodeURIComponent(id)}`);
  return new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(atob(encoded), c => c.charCodeAt(0)));
}
async function loadProposal() {
  busy.value = true; error.value = "";
  try {
    const text = selectedProposal.value ? await attachmentText(selectedProposal.value) : "";
    try { proposalText.value = JSON.stringify(JSON.parse(text), null, 2); }
    catch { proposalText.value = text; }
    originalText.value = proposalText.value;
  }
  catch (cause) { failed(cause); } finally { busy.value = false; }
}
async function loadContext() {
  busy.value = true; error.value = "";
  try { contextText.value = selectedContext.value ? await attachmentText(selectedContext.value) : ""; }
  catch (cause) { failed(cause); } finally { busy.value = false; }
}
async function loadFlow() {
  if (keepDraft() || !flowId.value) return;
  busy.value = true; error.value = ""; actions.value = [];
  try {
    flow.value = await props.context.api.get<Flow>(endpoint.value);
    if (!flow.value.states[stageId.value]) stageId.value = flow.value.currentStateIds[0] || Object.keys(flow.value.states)[0] || "";
    if (!stage.value) throw new Error("This flow has no accessible stage.");
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
  } catch (cause) { flow.value = undefined; failed(cause); } finally { busy.value = false; }
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
  if (!canEdit.value || !dirty.value) return;
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
    originalText.value = proposalText.value; await loadFlow();
  } catch (cause) { failed(cause); } finally { busy.value = false; }
}
function downloadDraft() {
  const url = URL.createObjectURL(new Blob([proposalText.value], { type: "application/json" }));
  const link = document.createElement("a"); link.href = url; link.download = "proposal-draft.json"; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
onMounted(async () => {
  const params = new URL(window.location.href).searchParams;
  flowId.value = params.get("flow") || ""; stageId.value = params.get("stage") || "";
  try { flows.value = await props.context.api.get<Flow[]>("api/v1/flows?includeClosed=true"); }
  catch (cause) { failed(cause); }
  if (flowId.value) await loadFlow();
});
</script>

<template>
  <section class="ontology-review" aria-label="Ontology review stage">
    <h1>Ontology review</h1>
    <p>Prepare the candidate from authored review annotations and project BOOTSTRAP.md. Review its scope and evidence before opening review.</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="controls">
      <label>Flow <select v-model="flowId" :disabled="busy || dirty" @change="loadFlow">
        <option value="">Choose a flow</option>
        <option v-for="item in flows" :key="item.id" :value="item.id">{{ item.assetUrn }} · {{ item.id }}</option>
      </select></label>
      <label v-if="flow">Stage <select v-model="stageId" :disabled="busy || dirty" @change="loadFlow">
        <option v-for="item in flow.states" :key="item.id" :value="item.id">{{ item.title || item.schemaId }} · {{ item.status }}</option>
      </select></label>
      <button :disabled="busy || dirty || !flowId" @click="loadFlow">Refresh</button>
    </div>
    <p v-if="stage">{{ flow?.assetUrn }} · {{ stage.title || stage.schemaId }} · {{ stage.status }} · revision {{ flow?.revision }}</p>
    <fieldset v-for="action in visibleActions" :key="action.id" :disabled="busy || dirty">
      <legend>{{ action.label }}</legend>
      <div class="action-inputs"><label v-for="input in action.missing" :key="input.name">{{ inputLabel(input.name) }}
        <input v-if="input.javaType?.endsWith('Integer')" v-model.number="values[input.name]" type="number" min="1" />
        <input v-else v-model="values[input.name]" required />
      </label></div>
      <button :disabled="action.missing.some(input => values[input.name] === '' || values[input.name] === undefined)" @click="execute(action)">{{ action.label }}</button>
    </fieldset>
    <div v-if="stage" class="face-to-face">
      <section><h2>Current candidate draft</h2>
        <label>Proposal <select v-model="selectedProposal" aria-label="Proposal attachment" :disabled="busy || dirty" @change="loadProposal">
          <option value="">No proposal selected</option>
          <option v-for="item in proposals" :key="item.id" :value="item.id">{{ item.fileName }} · {{ item.createdBy }}</option>
        </select></label>
        <textarea v-model="proposalText" aria-label="Candidate proposal JSON" :readonly="!canEdit" :disabled="busy || !selectedProposal" spellcheck="false" />
        <div class="controls"><button :disabled="busy || !canEdit || !dirty" @click="saveProposal">Save new draft attachment</button>
          <button :disabled="!proposalText" @click="downloadDraft">Download draft</button></div>
        <p v-if="dirty" role="status">Unsaved edits. Saving keeps earlier attachments intact.</p>
      </section>
      <section><h2>Comments and source context</h2>
        <label>Attachment <select v-model="selectedContext" aria-label="Context attachment" :disabled="busy" @change="loadContext">
          <option value="">No context selected</option>
          <option v-for="item in contextAttachments" :key="item.id" :value="item.id">{{ item.fileName }} · {{ item.createdBy }}</option>
        </select></label>
        <textarea :value="contextText" aria-label="Source context and comments" readonly spellcheck="false" />
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
