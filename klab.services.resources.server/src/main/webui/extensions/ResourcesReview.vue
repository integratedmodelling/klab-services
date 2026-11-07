<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import ReviewCyclesPanel from "./ReviewCyclesPanel.vue";
import type { ReviewCycle, ReviewView } from "../review-model";
import ProposalReader from "@klab-dashboard/components/ProposalReader.vue";
import { reviewError } from "@klab-dashboard/services/review-errors";

const props = defineProps<{ context: DashboardContext }>();
interface Attachment { id: string; type: string; fileName: string; mediaType: string; createdBy?: string }
interface Stage { id: string; title?: string; description?: string; schemaId: string; status: string; attachments: Attachment[] }
interface Upload { type: string; fileName: string; mediaType: string; content: string; expectedRevision?: number }
interface Flow { owner?: string; workflowVersion?: string; id: string; assetUrn: string; revision: number; states: Record<string, Stage>; currentStateIds: string[] }
interface Action { id: string; label: string; revision: number; missing: { name: string; javaType?: string }[] }
const flows = ref<Flow[]>([]), flowId = ref(""), stageId = ref("");
const flow = ref<Flow>(), actions = ref<Action[]>([]), error = ref("");
const pendingOperations = ref<{ id: number; message: string; kind: string }[]>([]);
const busy = computed(() => pendingOperations.value.length > 0);
const progress = computed(() => pendingOperations.value[0]?.message || "");
const preparing = computed(() => pendingOperations.value.some(operation => operation.kind === "prepare"));
const savingDetails = computed(() => pendingOperations.value.some(operation => operation.kind === "details"));
const uncertainPreparation = ref(false);
let operationSequence = 0;
function beginOperation(message: string, kind = "load") {
  if (!busy.value) { error.value = ""; notice.value = ""; }
  const id = ++operationSequence;
  pendingOperations.value.push({ id, message, kind });
  return () => { pendingOperations.value = pendingOperations.value.filter(operation => operation.id !== id); };
}
const actionError = ref("");
const flowAttempted = ref(false), feedbackDirty = ref(false), reviewedCycle = ref<ReviewCycle>();
const reviewedOntologySource = ref("");
let reviewCandidateGeneration = 0;
let disposed = false;
const selectedProposal = ref(""), selectedContext = ref(""), proposalText = ref(""), originalText = ref(""), contextText = ref("");
const values = ref<Record<string, string | number>>({});
const reviewView = ref<ReviewView>(), creation = ref<{canCreate: boolean; version?: string}>(), newAsset = ref("");
const newTitle = ref(""), newDescription = ref(""), newProposal = ref<File>(), newReferences = ref<File[]>([]);
const creatingAnother = ref(false), creationInputKey = ref(0), notice = ref("");
const editTitle = ref(""), editDescription = ref(""), originalDetails = ref("");
const detailsRevision = ref(0);
const uploadFiles = ref<File[]>([]), uploadKind = ref("supporting-material"), uploadInputKey = ref(0);
const preparationNote = ref(""), uncertainUpload = ref(false);
const pendingUpload = ref<{ payload: Upload; file: File; note: boolean; knownIds: string[] }>();
const detailsDirty = computed(() => JSON.stringify([editTitle.value, editDescription.value]) !== originalDetails.value && !!stage.value);
const editorDirty = computed(() => dirty.value || detailsDirty.value || uploadFiles.value.length > 0 || !!preparationNote.value.trim());
const showCreation = computed(() => creation.value?.canCreate && (!flowId.value || creatingAnother.value || !!uncertainCreation.value));
const creationDirty = computed(() => showCreation.value && !!(newAsset.value || newTitle.value || newDescription.value || newProposal.value || newReferences.value.length));
const selectedContextAttachment = computed(() => contextAttachments.value.find(item => item.id === selectedContext.value));
const textContext = computed(() => !selectedContextAttachment.value || /^(text\/|application\/(json|.*\+json|.*yaml|.*ontology))/.test(selectedContextAttachment.value.mediaType));
const panelKey = ref(0);
const reviewPanel = ref<InstanceType<typeof ReviewCyclesPanel>>();
const sourceOpen = ref(false), materialsOpen = ref(false);
const preparationBaseline = ref("{}");
const preparationDirty = computed(() => JSON.stringify(values.value) !== preparationBaseline.value);
const navigationDirty = computed(() => editorDirty.value || feedbackDirty.value || creationDirty.value || preparationDirty.value);
const writing = computed(() => pendingOperations.value.some(operation => operation.kind !== "load"));
let unregisterNavigationGuard: (() => void) | undefined;
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
  const finish = beginOperation("Checking existing reviews for your saved bootstrap...");
  try {
    flows.value = await props.context.api.get<Flow[]>("api/v1/flows?includeClosed=true");
    creationReconciled.value = true;
    if (!matchingCreations.value.length) {
      pendingCreation("");
      error.value = "No matching flow appeared in the refreshed list. Check the list before explicitly trying creation again; the earlier response was uncertain.";
    }
  } catch (cause) { failed(cause); } finally { finish(); }
}
async function openRecoveredFlow(id: string) { flowId.value = id; pendingCreation(""); resetCreation(); await changeFlow(); notice.value = "Recovered your saved private bootstrap. Check its files before continuing."; }
function resetCreation() {
  newAsset.value = ""; newTitle.value = ""; newDescription.value = ""; newProposal.value = undefined; newReferences.value = [];
  creatingAnother.value = false; creationInputKey.value++;
}
function startAnother() { if (busy.value || keepDraft()) return; resetCreation(); creatingAnother.value = true; notice.value = ""; }
function filesFrom(event: Event): File[] { return Array.from((event.target as HTMLInputElement).files || []); }
async function uploadPayload(file: File, type: string): Promise<Upload> {
  const limit = type === "candidate-ontology" ? 2 : type === "bootstrap-proposal" ? 4 : 16;
  if (file.size > limit * 1024 * 1024) throw new Error(`${file.name} exceeds ${limit} MiB. Choose a smaller file.`);
  const bytes = new Uint8Array(await file.arrayBuffer()), chunks: string[] = [];
  for (let i = 0; i < bytes.length; i += 8192) chunks.push(String.fromCharCode(...bytes.subarray(i, i + 8192)));
  return { type, fileName: file.name, mediaType: type === "bootstrap-proposal" ? "application/vnd.klab.proposal+yaml"
    : type === "candidate-ontology" ? "application/vnd.klab.ontology" : file.type || "application/octet-stream", content: btoa(chunks.join("")) };
}

const routedStage = computed(() => reviewView.value?.stages?.find(s => s.id === stageId.value));
const stageTitle = computed(() => ({ bootstrap: "Creator bootstrap", "peer-review": "Peer review", "community-review": "Community review", coalescing: "Editor coalescing", terminated: "Terminated flow" } as Record<string, string>)[routedStage.value?.schemaId || stage.value?.schemaId || ""] || "Ontology review");
const stage = computed(() => flow.value?.states[stageId.value]);
const proposals = computed(() => stage.value?.attachments.filter(a => a.mediaType === "application/vnd.klab.proposal+yaml") || []);
const contextAttachments = computed(() => stage.value?.attachments.filter(a => a.mediaType !== "application/vnd.klab.proposal+yaml") || []);
const dirty = computed(() => proposalText.value !== originalText.value);
const localProposal = computed(() => proposals.value.some(p => p.id === selectedProposal.value));
const pendingCandidateSave = computed(() => canEdit.value && !!proposalText.value && !localProposal.value);
const canEdit = computed(() => !!routedStage.value?.canEdit && stage.value?.status === "OPEN");
const unavailableContext = computed(() => actions.value.some(action => action.missing.some(input => ["ontology", "project"].includes(input.name))));
const visibleActions = computed(() => actions.value.filter(action => !action.missing.some(input => ["ontology", "project"].includes(input.name)) && (action.id !== "prepare-review-draft" ||
  !stage.value?.attachments.some(item => ["application/vnd.klab.proposal+yaml", "application/vnd.klab.ontology"].includes(item.mediaType)))));
const endpoint = computed(() => `api/v1/flows/${encodeURIComponent(flowId.value)}`);
const stageEndpoint = computed(() => `${endpoint.value}/states/${encodeURIComponent(stageId.value)}`);
function failed(cause: unknown) {
  if (disposed) return;
  error.value = reviewError(cause);
}
function routeStage(id: string) {
  if (disposed) return;
  if (["bootstrap", "coalescing", "terminated"].includes(routedStage.value?.schemaId || "")) return;
  stageId.value = id;
  const url = new URL(window.location.href); url.searchParams.set("stage", id); window.history.replaceState(window.history.state, "", url);
}
async function changeFlow() {
  if (disposed) return;
  resetCreation();
  stageId.value = ""; reviewView.value = undefined;
  const url = new URL(window.location.href); for (const name of ["stage", "cycle", "round"]) url.searchParams.delete(name);
  if (!flowId.value) { url.searchParams.delete("flow"); flow.value = undefined; flowAttempted.value = false; }
  window.history.replaceState(window.history.state, "", url); panelKey.value++; await loadFlow();
}
async function changeStage() {
  if (disposed) return;
  const url = new URL(window.location.href); url.searchParams.delete("cycle"); url.searchParams.delete("round");
  url.searchParams.set("stage", stageId.value); window.history.replaceState(window.history.state, "", url);
  panelKey.value++; await loadFlow();
}
async function flowChanged(keepStage = false) {
  if (disposed) return;
  if (!keepStage) stageId.value = reviewView.value?.editableStageId || reviewView.value?.stages?.filter(s => s.open).at(-1)?.id || reviewView.value?.stages?.at(-1)?.id || "";
  if (!keepStage) { const url = new URL(window.location.href); url.searchParams.delete("cycle"); url.searchParams.delete("round"); window.history.replaceState(window.history.state, "", url); }
  await loadFlow(); panelKey.value++;
}
async function createFlow() {
  if (!showCreation.value || uncertainCreation.value || !newAsset.value.trim() || !newTitle.value.trim() || busy.value || keepDraft()) return;
  const finish = beginOperation("Creating your private bootstrap and loading its preparation tools...", "create");
  try {
    const attachments = await Promise.all([...(newProposal.value ? [uploadPayload(newProposal.value, "bootstrap-proposal")] : []),
      ...newReferences.value.map(file => uploadPayload(file, "supporting-material"))]);
    pendingCreation(newAsset.value.trim());
    const created = await props.context.api.request<Flow>("api/v1/flows/initialize?workflowId=ontology-review-pilot", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ initialState: { schemaId: "bootstrap", assetUrn: newAsset.value.trim(), assetType: "ONTOLOGY",
        title: newTitle.value.trim(), description: newDescription.value.trim() }, attachments }),
    });
    if (disposed) return; // Keep the recovery marker; accepted writes are not canceled on navigation.
    pendingCreation("");
    flows.value.push(created); flowId.value = created.id; stageId.value = created.currentStateIds[0] || ""; resetCreation();
    reviewView.value = undefined;
    const url = new URL(window.location.href); url.searchParams.delete("cycle"); url.searchParams.delete("round"); window.history.replaceState(window.history.state, "", url);
    panelKey.value++;
    if (await loadFlow()) notice.value = "Private bootstrap created. Prepare a proposal or edit your uploaded proposal, then start peer review when it is ready.";
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    if (/^4\d\d\b/.test(message) && !message.startsWith("408")) pendingCreation("");
    failed(cause);
  } finally { finish(); }
}
function inputLabel(name: string): string {
  return ({ proposal_id: "Proposal ID", revision_id: "Revision ID", domain: "Domain", purpose: "Purpose",
    requested_tier: "Requested tier", target_community: "Target community" } as Record<string, string>)[name] || name.replaceAll("_", " ");
}
function keepDraft(): boolean {
  if (!editorDirty.value && !feedbackDirty.value) return false;
  error.value = "Save or explicitly discard your stage details, selected files, proposal edits or feedback before changing the view."; return true;
}
function reviewAccessLost() {
  if (canEdit.value) return; // Preserve an editor's own editable candidate for recovery.
  reviewCandidateGeneration++; reviewedCycle.value = undefined; flow.value = undefined; actions.value = [];
  proposalText.value = ""; originalText.value = ""; selectedProposal.value = ""; selectedContext.value = ""; contextText.value = ""; reviewedOntologySource.value = "";
}
async function reviewCandidate(cycle: ReviewCycle) {
  if (disposed) return;
  reviewedCycle.value = cycle;
  if ((canEdit.value || ["bootstrap", "coalescing"].includes(stage.value?.schemaId || "")) && selectedProposal.value) return; // Keep the editable draft intact while inspecting feedback.
  if (dirty.value) return;
  const generation = ++reviewCandidateGeneration;
  const finish = beginOperation("Loading the frozen review candidate...");
  try {
    const path = `${endpoint.value}/review-cycles/${encodeURIComponent(cycle.id)}/attachments/`;
    const [encoded, source] = await Promise.all([
      attachmentBase64(path + encodeURIComponent(cycle.candidate.proposal.attachmentId)),
      cycle.candidate.ontology ? attachmentBase64(path + encodeURIComponent(cycle.candidate.ontology.attachmentId)) : Promise.resolve(""),
    ]);
    if (generation !== reviewCandidateGeneration) return;
    const text = new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(atob(encoded), c => c.charCodeAt(0)));
    proposalText.value = text;
    originalText.value = proposalText.value; selectedProposal.value = cycle.candidate.proposal.attachmentId;
    contextText.value = source ? new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(atob(source), c => c.charCodeAt(0))) : "";
    reviewedOntologySource.value = contextText.value;
  } catch (cause) { if (generation === reviewCandidateGeneration) failed(cause); }
  finally { finish(); }
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
  const finish = beginOperation("Loading the proposal draft...");
  try {
    const text = selectedProposal.value ? await attachmentText(selectedProposal.value) : "";
    if (generation !== reviewCandidateGeneration) return;
    proposalText.value = text;
    originalText.value = proposalText.value;
    return true;
  }
  catch (cause) { if (generation === reviewCandidateGeneration) failed(cause); }
  finally { finish(); }
  return false;
}
async function loadContext() {
  const generation = reviewCandidateGeneration;
  const finish = beginOperation("Loading stage materials...");
  try {
    const text = selectedContext.value && textContext.value ? await attachmentText(selectedContext.value) : "";
    if (generation === reviewCandidateGeneration) contextText.value = text;
    return generation === reviewCandidateGeneration;
  }
  catch (cause) { if (generation === reviewCandidateGeneration) failed(cause); }
  finally { finish(); }
  return false;
}
async function loadFlow() {
  if (disposed || keepDraft() || !flowId.value) return false;
  flowAttempted.value = false; reviewedCycle.value = undefined;
  reviewCandidateGeneration++;
  const finish = beginOperation("Loading stage details and preparation actions. Please wait...");
  actionError.value = ""; actions.value = [];
  try {
    const next = await props.context.api.get<Flow>(endpoint.value);
    if (disposed) return false;
    flow.value = next;
    if (!stageId.value) stageId.value = flow.value.currentStateIds[0] || Object.keys(flow.value.states)[0] || "";
    if (!stage.value) throw new Error("The requested stage is unavailable to this account. Choose an authorized stage.");
    editTitle.value = stage.value.title || ""; editDescription.value = stage.value.description || "";
    originalDetails.value = JSON.stringify([editTitle.value, editDescription.value]);
    detailsRevision.value = flow.value.revision;
    const link = new URL(window.location.href); link.searchParams.set("flow", flowId.value); link.searchParams.set("stage", stageId.value);
    window.history.replaceState(window.history.state, "", link);
    if (stage.value.status === "OPEN") {
      try {
        const discovered = await props.context.api.get<Action[]>(`${stageEndpoint.value}/actions`);
        if (disposed) return false;
        actions.value = discovered;
      }
      catch (cause) { const message = cause instanceof Error ? cause.message : String(cause);
        if (!message.startsWith("403")) actionError.value = `Could not load preparation actions: ${message}. Use Refresh to retry.`; }
    }
    if (disposed) return false;
    for (const action of actions.value) for (const input of action.missing) {
      if (!(input.name in values.value)) values.value[input.name] = input.javaType === "Integer" || input.javaType === "java.lang.Integer" ? 1 : "";
    }
    preparationBaseline.value = JSON.stringify(values.value);
    selectedProposal.value = proposals.value.at(-1)?.id || "";
    selectedContext.value = contextAttachments.value[0]?.id || "";
    const proposalLoaded = await loadProposal(), contextLoaded = await loadContext();
    if (disposed) return false;
    if (canEdit.value && stage.value.schemaId === "coalescing" && !selectedProposal.value && reviewView.value?.cycles.length) await reviewCandidate(reviewView.value.cycles.at(-1)!);
    if (proposalLoaded && contextLoaded) uncertainPreparation.value = false;
    return proposalLoaded && contextLoaded;
  } catch (cause) {
    flow.value = undefined; proposalText.value = ""; originalText.value = ""; contextText.value = "";
    if (!(cause instanceof Error && cause.message.startsWith("403"))) failed(cause);
  } finally { finish(); flowAttempted.value = true; }
  return false;
}
async function execute(action: Action) {
  if (busy.value || uncertainPreparation.value || keepDraft() || !flow.value) return;
  const finish = beginOperation("Preparing review draft from the ontology. This can take a little while; stay on this page...", "prepare");
  try {
    const parameters = Object.fromEntries(action.missing.map(input => [input.name, values.value[input.name]]));
    await props.context.api.request(`${stageEndpoint.value}/actions/${encodeURIComponent(action.id)}`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expectedRevision: flow.value.revision, parameters }),
    });
    if (disposed) return;
    if (await loadFlow()) notice.value = "Proposal prepared and saved in this stage. Review the candidate draft below, save any edits, then choose Start cycle from selected proposal to begin peer review.";
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    uncertainPreparation.value = !/^4\d\d\b/.test(message) || message.startsWith("408");
    failed(cause);
  } finally { finish(); }
}
async function saveProposal() {
  if (busy.value || !canEdit.value || (!dirty.value && !pendingCandidateSave.value) || feedbackDirty.value || detailsDirty.value || uploadFiles.value.length || preparationNote.value.trim()) return;
  const finish = beginOperation("Saving your proposal draft and reloading the candidate...", "save");
  try {
    const bytes = new TextEncoder().encode(proposalText.value);
    if (bytes.byteLength > 4 * 1024 * 1024) throw new Error("Proposal exceeds 4 MiB.");
    const chunks: string[] = [];
    for (let i = 0; i < bytes.length; i += 8192) chunks.push(String.fromCharCode(...bytes.subarray(i, i + 8192)));
    await props.context.api.request(`${stageEndpoint.value}/attachments`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expectedRevision: flow.value?.revision, type: "bootstrap-proposal", fileName: `proposal-draft-${Date.now()}.yaml`,
        mediaType: "application/vnd.klab.proposal+yaml", content: btoa(chunks.join("")) }),
    });
    flow.value = await props.context.api.get<Flow>(endpoint.value);
    if (stage.value?.schemaId === "coalescing" && reviewedCycle.value?.candidate.ontology && reviewedOntologySource.value
        && !stage.value.attachments.some(a => a.type === "candidate-ontology")) {
      const source = new TextEncoder().encode(reviewedOntologySource.value), parts: string[] = [];
      for (let i = 0; i < source.length; i += 8192) parts.push(String.fromCharCode(...source.subarray(i, i + 8192)));
      await props.context.api.request(`${stageEndpoint.value}/attachments`, {method: "POST", headers: {"Content-Type":"application/json"},
        body: JSON.stringify({expectedRevision: flow.value.revision, type:"candidate-ontology", fileName:"candidate.kwv", mediaType:"application/vnd.klab.ontology", content:btoa(parts.join(""))})});
    }
    originalText.value = proposalText.value;
    if (await loadFlow()) notice.value = "New proposal draft saved. Earlier attachments are unchanged. You can start peer review when the candidate is ready.";
  } catch (cause) { failed(cause); } finally { finish(); }
}
async function saveDetails() {
  if (busy.value || !canEdit.value || !detailsDirty.value || !editTitle.value.trim()) return;
  const finish = beginOperation("Saving stage title and description...", "details");
  try {
    const updated = await props.context.api.request<Flow>(stageEndpoint.value, { method: "PATCH", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expectedRevision: detailsRevision.value, title: editTitle.value.trim(), description: editDescription.value.trim() }) });
    flow.value = updated; editTitle.value = stage.value?.title || ""; editDescription.value = stage.value?.description || "";
    originalDetails.value = JSON.stringify([editTitle.value, editDescription.value]); detailsRevision.value = updated.revision;
    await reviewPanel.value?.refresh();
    notice.value = "Stage title and description saved. You can now continue preparing your review draft.";
  } catch (cause) { failed(cause); } finally { finish(); }
}
function discardDetails() {
  [editTitle.value, editDescription.value] = JSON.parse(originalDetails.value);
}
async function saveUploads(note = false) {
  if (busy.value || uncertainUpload.value || !canEdit.value || dirty.value || detailsDirty.value || feedbackDirty.value) return;
  const finish = beginOperation(note ? "Saving your preparation note..." : "Uploading selected files and refreshing stage materials...", "upload");
  try {
    const files = note ? [new File([preparationNote.value], `preparation-note-${Date.now()}.txt`, { type: "text/plain" })] : [...uploadFiles.value];
    for (const file of files) {
      const payload = await uploadPayload(file, note ? "supporting-material" : uploadKind.value);
      pendingUpload.value = { payload, file, note, knownIds: stage.value?.attachments.map(item => item.id) || [] };
      uncertainUpload.value = true;
      await props.context.api.request(`${stageEndpoint.value}/attachments`, { method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ...payload, expectedRevision: flow.value?.revision }) });
      uncertainUpload.value = false;
      pendingUpload.value = undefined;
      if (note) preparationNote.value = ""; else uploadFiles.value = uploadFiles.value.filter(item => item !== file);
      flow.value = await props.context.api.get<Flow>(endpoint.value);
    }
    uploadInputKey.value++;
    if (await loadFlow()) notice.value = note ? "Preparation note saved to this stage." : "Files saved to this stage. Proposal uploads can now be edited or selected for review.";
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : String(cause);
    if (/^4\d\d\b/.test(message) && !message.startsWith("408")) { uncertainUpload.value = false; pendingUpload.value = undefined; }
    failed(cause);
  } finally { finish(); }
}
async function checkUploadedFiles() {
  if (busy.value) return;
  const finish = beginOperation("Checking which stage files were saved...");
  try { flow.value = await props.context.api.get<Flow>(endpoint.value);
    const pending = pendingUpload.value;
    let found = false;
    if (pending) for (const item of stage.value?.attachments || []) {
      if (!pending.knownIds.includes(item.id) && item.type === pending.payload.type && item.fileName === pending.payload.fileName
          && (await attachmentBase64(`${endpoint.value}/attachments/${encodeURIComponent(item.id)}`)) === pending.payload.content) { found = true; break; }
    }
    if (found && pending) {
      if (pending.note && preparationNote.value === await pending.file.text()) preparationNote.value = "";
      else uploadFiles.value = uploadFiles.value.filter(file => file !== pending.file);
      uploadInputKey.value++;
    }
    uncertainUpload.value = false; pendingUpload.value = undefined;
    if (!editorDirty.value) await loadFlow();
    notice.value = found ? "The saved file was found. It will not be uploaded again. Check the saved file list below."
      : "No matching saved file was found. Your selected file or note is retained; check the list before explicitly retrying.";
  } catch (cause) { failed(cause); } finally { finish(); }
}
async function downloadContext() {
  if (!selectedContextAttachment.value) return;
  try {
    const item = selectedContextAttachment.value, encoded = await attachmentBase64(`${endpoint.value}/attachments/${encodeURIComponent(item.id)}`);
    const url = URL.createObjectURL(new Blob([Uint8Array.from(atob(encoded), c => c.charCodeAt(0))], { type: item.mediaType }));
    const link = document.createElement("a"); link.href = url; link.download = item.fileName; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (cause) { failed(cause); }
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (navigationDirty.value || writing.value || reviewPanel.value?.isBusy()) { event.preventDefault(); event.returnValue = ""; }
}
onUnmounted(() => {
  disposed = true;
  reviewCandidateGeneration++;
  window.removeEventListener("beforeunload", beforeUnload);
  unregisterNavigationGuard?.();
});
function canSaveBeforeLeave() {
  if (busy.value || creationDirty.value || preparationDirty.value || uploadFiles.value.length || preparationNote.value.trim()) return false;
  if (feedbackDirty.value) return !editorDirty.value && !!reviewPanel.value?.canSaveDraft();
  if (detailsDirty.value) return !dirty.value && !!editTitle.value.trim();
  return dirty.value && canEdit.value;
}
async function saveBeforeLeave() {
  if (!canSaveBeforeLeave()) return false;
  if (feedbackDirty.value) await reviewPanel.value?.saveDraft();
  else if (detailsDirty.value) await saveDetails();
  else if (dirty.value) await saveProposal();
  await nextTick();
  return !navigationDirty.value;
}
function discardBeforeLeave() {
  discardProposalEdits();
  if (stage.value) discardDetails();
  uploadFiles.value = []; uploadInputKey.value++; preparationNote.value = "";
  resetCreation(); values.value = JSON.parse(preparationBaseline.value);
  reviewPanel.value?.discardDraft(); feedbackDirty.value = false;
}
function discardProposalEdits() {
  proposalText.value = originalText.value;
  if (!originalText.value) selectedProposal.value = "";
}
function downloadDraft() {
  let json = false;
  try { JSON.parse(proposalText.value); json = true; } catch { /* Preserve YAML as supplied. */ }
  const url = URL.createObjectURL(new Blob([proposalText.value], { type: json ? "application/json" : "application/yaml" }));
  const link = document.createElement("a"); link.href = url; link.download = json ? "proposal-draft.json" : "proposal-draft.yaml"; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
function workspaceHref(id: string) {
  const url = new URL(window.location.href); url.hash = id; return url.href;
}
function jumpToWorkspace(id: string) {
  const target = document.getElementById(id);
  if (!target) return;
  window.history.replaceState(window.history.state, "", workspaceHref(id));
  target.focus({ preventScroll: true });
  target.scrollIntoView({ block: "start", behavior: "auto" });
}
onMounted(async () => {
  unregisterNavigationGuard = props.context.registerNavigationGuard?.({
    isDirty: () => navigationDirty.value || writing.value || !!reviewPanel.value?.isBusy(),
    isBusy: () => busy.value || !!reviewPanel.value?.isBusy(),
    canSave: canSaveBeforeLeave, save: saveBeforeLeave, discard: discardBeforeLeave,
  });
  window.addEventListener("beforeunload", beforeUnload);
  restorePendingCreation();
  const params = new URL(window.location.href).searchParams;
  flowId.value = params.get("flow") || ""; stageId.value = params.get("stage") || "";
  const finish = beginOperation("Loading your reviews and available preparation tools...");
  try {
    try { creation.value = await props.context.api.get<{canCreate: boolean; version?: string}>("api/v1/workflows/ontology-review-pilot/creation"); } catch { /* Creation stays unavailable until authorized by the server. */ }
    try { flows.value = await props.context.api.get<Flow[]>("api/v1/flows?includeClosed=true"); }
    catch (cause) { failed(cause); }
    if (flowId.value) await loadFlow();
  } finally { finish(); }
});
</script>

<template>
  <section class="ontology-review" aria-label="Ontology review stage">
    <header class="review-heading">
      <h1>{{ stageTitle }}</h1>
      <p v-if="flowId">{{ flow?.assetUrn || reviewedCycle?.candidate.proposalId }}<span v-if="reviewedCycle"> · Revision {{ reviewedCycle.candidate.revisionId }}</span></p>
      <p v-if="routedStage && !routedStage.open" role="status">Historical stage. The candidate and submitted contributions are read-only.</p>
      <p v-else-if="canEdit">{{ stage?.schemaId === 'coalescing' ? 'Integrate feedback into a new revision, then return to review.' : 'Prepare your candidate privately, then open it for peer review.' }}</p>
      <p v-else-if="flowId">Read the frozen candidate and add feedback to the selected review round.</p>
      <p v-else>Choose a review, or prepare a new candidate privately.</p>
    </header>
    <p v-if="busy" class="progress-message" role="status" aria-live="polite" aria-atomic="true"><span class="progress-spinner" aria-hidden="true"></span>{{ progress }}</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="notice && !busy" class="success-message" role="status">{{ notice }}</p>
    <p v-if="uncertainPreparation && !busy" role="status">Preparation has an unknown outcome. Use Refresh to check for a saved proposal before trying preparation again. Your preparation fields are retained.</p>
    <p v-if="canEdit && actionError" role="alert">{{ actionError }}</p>
    <p v-if="canEdit && unavailableContext" role="alert">The target ontology or its project could not be resolved for automatic preparation. Check that this ontology URN belongs to an accessible project, then refresh. You can still upload a prepared proposal.</p>

    <details :open="!flowId" class="review-disclosure">
      <summary>{{ flowId ? 'Other reviews and stages' : 'Choose a review' }}</summary>
      <div class="controls">
        <label>Review<select aria-label="Flow" v-model="flowId" :disabled="busy || editorDirty || feedbackDirty || creationDirty || preparationDirty" @change="changeFlow">
          <option value="">Choose a review</option><option v-for="item in flows" :key="item.id" :value="item.id">{{ Object.values(item.states).find(s => s.title)?.title || item.assetUrn }} · {{ item.assetUrn }}</option>
        </select></label>
        <label v-if="reviewView?.stages?.length">Stage<select aria-label="Stage" v-model="stageId" :disabled="busy || editorDirty || feedbackDirty || creationDirty || preparationDirty" @change="changeStage">
          <option v-for="(item, index) in reviewView?.stages" :key="item.id" :value="item.id">{{ flow?.states[item.id]?.title || item.schemaId }} · Stage {{ index + 1 }} · {{ item.open ? 'Open' : 'Historical' }}</option>
        </select></label>
        <q-btn outline no-caps :disable="busy || editorDirty || feedbackDirty || !flowId" @click="loadFlow">Refresh</q-btn>
        <q-btn v-if="creation?.canCreate && !showCreation" flat no-caps :disable="busy || editorDirty || feedbackDirty" @click="startAnother">Start another ontology review</q-btn>
      </div>
      <p v-if="stage">{{ stage.title || stage.schemaId }} · {{ stage.status }} · revision {{ flow?.revision }}</p>
      <p v-if="flow?.workflowVersion">Workflow version {{ flow.workflowVersion }}.</p>
    </details>

    <fieldset v-if="showCreation" :disabled="busy || editorDirty || feedbackDirty" class="preparation-section">
      <legend>Start an ontology review</legend>
      <p>Your preparation is private. Reviewers gain access only after you start review.</p>
      <div class="preparation-fields">
        <label>Ontology URN<input :disabled="!!uncertainCreation" v-model="newAsset" placeholder="project.ontology" /></label>
        <label>Review title<input :disabled="!!uncertainCreation" v-model="newTitle" maxlength="500" placeholder="What is this review about?" required /></label>
      </div>
      <label>Review description<textarea class="prose-input" v-model="newDescription" maxlength="16000" placeholder="Describe the goals, scope and questions for this review." /></label>
      <details class="review-disclosure"><summary>Add initial files (optional)</summary>
        <div :key="creationInputKey" class="controls">
          <label>Initial proposal file (optional)<input type="file" accept=".json,.yaml,.yml" :disabled="!!uncertainCreation" @change="newProposal = filesFrom($event)[0]" /></label>
          <label>Reference files (optional)<input type="file" multiple :disabled="!!uncertainCreation" @change="newReferences = filesFrom($event)" /></label>
        </div>
        <p>Proposal JSON or YAML: up to 4 MiB. Reference files: up to 16 MiB each. Files and details are saved together.</p>
      </details>
      <div class="controls">
        <q-btn unelevated no-caps color="primary" :disable="!newAsset.trim() || !newTitle.trim() || !!uncertainCreation" @click="createFlow">Create private bootstrap</q-btn>
        <q-btn v-if="(creatingAnother || creationDirty) && !uncertainCreation" flat no-caps @click="resetCreation">{{ creatingAnother ? 'Cancel new review' : 'Discard new review draft' }}</q-btn>
      </div>
      <div v-if="uncertainCreation && !busy" role="status">
        <p>Creation of {{ uncertainCreation }} has an unknown outcome. Check existing reviews before creating it again.</p>
        <q-btn outline no-caps @click="reconcileCreation">Refresh existing flows</q-btn>
        <q-btn v-for="item in matchingCreations" :key="item.id" outline no-caps @click="openRecoveredFlow(item.id)">Open existing bootstrap {{ item.id }}</q-btn>
      </div>
    </fieldset>

    <template v-if="canEdit">
      <ol class="preparation-path" aria-label="Bootstrap preparation steps">
        <li :aria-current="!selectedProposal ? 'step' : undefined">Prepare details and materials</li>
        <li>Prepare or upload a candidate</li><li :aria-current="selectedProposal ? 'step' : undefined">Inspect, save and start review</li>
      </ol>
      <details class="review-disclosure" :open="!selectedProposal">
        <summary>1. Review details and materials</summary>
        <fieldset :disabled="busy" class="preparation-section">
          <legend>Stage details</legend>
          <label>Stage title<input v-model="editTitle" maxlength="500" /></label>
          <label>Stage description<textarea class="prose-input" v-model="editDescription" maxlength="16000" /></label>
          <div class="controls"><q-btn outline no-caps :disable="!detailsDirty || !editTitle.trim()" @click="saveDetails">{{ savingDetails ? 'Saving stage details...' : 'Save stage details' }}</q-btn>
            <q-btn v-if="detailsDirty" flat no-caps @click="discardDetails">Discard stage detail edits</q-btn></div>
          <p v-if="detailsDirty" role="status">Unsaved stage details. Save or discard these edits before preparing a review draft.</p>
          <details class="review-disclosure" :open="materialsOpen" @toggle="materialsOpen = ($event.target as HTMLDetailsElement).open">
            <summary>Reference files, proposal uploads and private notes</summary>
            <div class="controls">
              <label>File purpose<select aria-label="File purpose" v-model="uploadKind" :disabled="uploadFiles.length > 0 || uncertainUpload">
                <option value="supporting-material">Reference material</option><option value="bootstrap-proposal">Proposal JSON or YAML</option>
                <option value="candidate-ontology" :disabled="stage?.attachments.some(a => a.type === 'candidate-ontology')">Candidate ontology source</option>
              </select></label>
              <label>Stage files<input :key="uploadInputKey" type="file" :multiple="uploadKind === 'supporting-material'" :disabled="uncertainUpload" @change="uploadFiles = filesFrom($event)" /></label>
              <q-btn outline no-caps :disable="!uploadFiles.length || dirty || detailsDirty || feedbackDirty || uncertainUpload || !!preparationNote.trim()" @click="saveUploads()">Upload selected files</q-btn>
              <q-btn v-if="uploadFiles.length && !uncertainUpload" flat no-caps @click="uploadFiles = []; uploadInputKey++">Discard selected files</q-btn>
            </div>
            <p v-if="uncertainUpload && !busy" role="status">The upload has an unknown outcome. Check saved files before uploading again. <q-btn outline no-caps @click="checkUploadedFiles">Check saved files</q-btn></p>
            <p>Files and notes remain in this editorial stage. Review shares only the frozen proposal and selected ontology source.</p>
            <ul v-if="stage?.attachments.length" aria-label="Saved stage files"><li v-for="item in stage.attachments" :key="item.id">{{ item.fileName }} ({{ item.type }})</li></ul>
            <label>Preparation note<textarea class="prose-input" v-model="preparationNote" maxlength="16000" placeholder="Add a private editorial note or question about the materials." /></label>
            <div class="controls"><q-btn outline no-caps :disable="!preparationNote.trim() || dirty || detailsDirty || feedbackDirty || uploadFiles.length > 0 || uncertainUpload" @click="saveUploads(true)">Save preparation note</q-btn>
              <q-btn v-if="preparationNote && !uncertainUpload" flat no-caps @click="preparationNote = ''">Discard preparation note</q-btn></div>
          </details>
        </fieldset>
      </details>
      <fieldset v-for="action in visibleActions" :key="action.id" :disabled="busy || uncertainPreparation || editorDirty || feedbackDirty" :aria-busy="preparing" class="preparation-section">
        <legend>2. Prepare your candidate</legend>
        <p>Use the current ontology, review annotations and optional project BOOTSTRAP.md to prepare an editable proposal. You can upload an existing proposal in Review details and materials instead.</p>
        <div class="preparation-fields"><label v-for="input in action.missing" :key="input.name">{{ inputLabel(input.name) }}
          <input v-if="input.javaType?.endsWith('Integer')" v-model.number="values[input.name]" type="number" min="1" />
          <input v-else v-model="values[input.name]" :placeholder="input.name === 'revision_id' ? 'For example, r1' : input.name === 'proposal_id' ? 'A stable identifier for this proposal' : undefined" required />
        </label></div>
        <q-btn unelevated no-caps color="primary" :disable="action.missing.some(input => values[input.name] === '' || values[input.name] === undefined)" @click="execute(action)">{{ preparing ? 'Preparing review draft...' : action.label }}</q-btn>
        <q-btn v-if="preparationDirty" flat no-caps @click="values = JSON.parse(preparationBaseline)">Discard preparation inputs</q-btn>
      </fieldset>
    </template>

    <nav v-if="flowAttempted && flowId" class="workspace-jumps" aria-label="Review workspace"><a :href="workspaceHref('review-candidate')" @click.prevent="jumpToWorkspace('review-candidate')">Read candidate</a><a :href="workspaceHref('review-feedback')" @click.prevent="jumpToWorkspace('review-feedback')">Feedback and next steps</a></nav>
    <div v-if="flowAttempted && flowId" class="review-workspace">
      <section id="review-candidate" class="candidate-panel" tabindex="-1">
        <header class="workspace-heading"><h2>{{ canEdit ? '3. Inspect your candidate' : 'Reviewed candidate' }}</h2><span v-if="dirty" role="status">Unsaved source changes</span></header>
        <p v-if="stage?.description && !canEdit">{{ stage.description }}</p>
        <template v-if="proposalText">
          <ProposalReader :source="proposalText" />
          <div class="controls"><q-btn outline no-caps @click="downloadDraft">Download draft</q-btn></div>
          <details class="review-disclosure" :open="sourceOpen" @toggle="sourceOpen = ($event.target as HTMLDetailsElement).open">
            <summary>{{ canEdit ? 'Edit exact proposal source' : 'Exact proposal source' }}</summary>
            <label v-if="canEdit && proposals.length">Proposal<select v-model="selectedProposal" aria-label="Proposal attachment" :disabled="busy || editorDirty" @change="loadProposal">
              <option value="">No proposal selected</option><option v-for="item in proposals" :key="item.id" :value="item.id">{{ item.fileName }} · {{ item.createdBy }}</option>
            </select></label>
            <textarea class="source-editor" v-model="proposalText" aria-label="Candidate proposal source" :readonly="!canEdit" :disabled="busy || !selectedProposal" spellcheck="false" />
            <div v-if="canEdit" class="controls"><q-btn unelevated no-caps color="primary" :disable="busy || (!dirty && !pendingCandidateSave) || detailsDirty || uploadFiles.length > 0 || !!preparationNote.trim() || feedbackDirty" @click="saveProposal">Save new draft attachment</q-btn>
              <q-btn v-if="dirty" flat no-caps :disable="busy" @click="discardProposalEdits">Discard unsaved proposal edits</q-btn></div>
            <p v-if="pendingCandidateSave && !dirty" role="status">Save this seed from the previous candidate into this stage before returning to review, or terminate without saving.</p>
            <p v-if="dirty" role="status">Unsaved edits. Saving keeps earlier attachments intact.</p>
            <p v-if="canEdit">Changed proposals need a fresh revision ID and a record of the revision they supersede. JSON and YAML are accepted; document validation does not imply scientific approval.</p>
          </details>
        </template>
        <div v-else class="candidate-empty">
          <h3>{{ canEdit ? 'No candidate yet' : 'Candidate unavailable' }}</h3>
          <p>{{ canEdit ? 'Prepare a review draft above, or upload an existing proposal in Review details and materials.' : 'Choose an authorized review round to load its frozen candidate.' }}</p>
          <p v-if="canEdit && !visibleActions.length" role="status">Automatic preparation is unavailable for this stage. Upload a proposal file to continue, or ask the service administrator to check the preparation action.</p>
        </div>
        <details v-if="stage" class="review-disclosure"><summary>Comments and source context</summary>
          <label>Attachment<select v-model="selectedContext" aria-label="Context attachment" :disabled="busy" @change="loadContext">
            <option value="">No context selected</option><option v-for="item in contextAttachments" :key="item.id" :value="item.id">{{ item.fileName }} · {{ item.createdBy }}</option>
          </select></label>
          <q-btn v-if="selectedContext" outline no-caps :disable="busy" @click="downloadContext">Download selected material</q-btn>
          <textarea v-if="textContext" class="source-editor" :value="contextText" aria-label="Source context and comments" readonly spellcheck="false" />
          <p v-else>Download this file to view it in its original format.</p>
        </details>
        <details v-else-if="contextText" class="review-disclosure"><summary>Reviewed ontology source</summary><pre class="source-preview">{{ contextText }}</pre></details>
      </section>
      <section id="review-feedback" class="feedback-panel" tabindex="-1">
        <ReviewCyclesPanel ref="reviewPanel" :context="context" :flow-id="flowId" :key="panelKey" :stage-id="stageId" :source-state-id="reviewView?.editableStageId"
          :proposal-attachment-id="canEdit && localProposal ? selectedProposal : undefined"
          :ontology-attachment-id="stage?.attachments.find(a => a.type === 'candidate-ontology')?.id"
          :candidate-dirty="editorDirty" :candidate-loading="busy" @view="reviewView = $event" @stage="routeStage" @changed="flowChanged" @candidate="reviewCandidate" @dirty="feedbackDirty = $event" @access-lost="reviewAccessLost" />
      </section>
    </div>
  </section>
</template>
<style scoped>
.ontology-review { --q-primary: #2459a8; color: #17283b; max-width: 1400px; margin-inline: auto; padding: 24px; font-size: 1rem; line-height: 1.55; }
.ontology-review, .ontology-review * { box-sizing: border-box; }
.review-heading { margin-block-end: 32px; max-width: 72ch; }
h1 { text-wrap: balance; font-size: clamp(1.625rem, 3vw, 2.25rem); font-weight: 600; line-height: 1.2; margin: 0 0 16px; }
h2 { text-wrap: balance; font-size: 1.375rem; line-height: 1.3; margin: 0 0 24px; font-weight: 600; }
h3 { text-wrap: balance; font-size: 1.125rem; line-height: 1.4; font-weight: 600; margin: 0 0 8px; }
p { max-width: 72ch; overflow-wrap: anywhere; text-wrap: pretty; }
.controls { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin-block: 16px; }
label { display: grid; gap: 8px; min-width: 0; max-width: 100%; }
.controls label { flex: 1 1 200px; }
input, select, textarea { width: 100%; max-width: 100%; min-width: 0; padding: 12px; color: #17283b; background: #fff; border: 1px solid #91a1b1; border-radius: 4px; font: inherit; caret-color: #2459a8; }
select { text-overflow: ellipsis; }
input:disabled, select:disabled, textarea:disabled { opacity: .65; }
input:focus-visible, select:focus-visible, textarea:focus-visible, summary:focus-visible, a:focus-visible { outline: 3px solid #2459a8; outline-offset: 3px; }
::selection { background: #dce9fa; color: #17283b; }
textarea { resize: vertical; }
.prose-input { min-height: 112px; }
.source-editor { min-height: 280px; margin-block: 16px; font: .875rem/1.6 monospace; background: #f7f9fc; tab-size: 2; }
.preparation-section { min-width: 0; border: 0; padding: 0; margin-block: 24px 32px; }
.preparation-section legend { font-size: 1.25rem; font-weight: 600; margin-block-end: 16px; }
.preparation-section > label { margin-block: 16px; }
.preparation-fields { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px 24px; margin-block: 24px; }
.preparation-path { display: flex; flex-wrap: wrap; gap: 16px 32px; padding-inline-start: 24px; margin-block: 32px; color: #53667b; }
.preparation-path [aria-current] { color: #17283b; font-weight: 600; }
.review-disclosure { border-block-start: 1px solid #cad5e2; padding-block: 16px; margin-block: 16px; min-width: 0; }
.review-disclosure > summary { cursor: pointer; font-weight: 600; padding-block: 8px; min-height: 44px; overflow-wrap: anywhere; display: list-item; list-style: disclosure-closed inside; }
.review-disclosure[open] > summary { list-style-type: disclosure-open; }
.review-disclosure > summary::marker { color: #53667b; }
.review-workspace { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(0, 1fr); gap: 48px; align-items: start; margin-block-start: 24px; }
.review-workspace > section { min-width: 0; scroll-margin-block-start: 100px; }
.feedback-panel { border-inline-start: 1px solid #cad5e2; padding-inline-start: 32px; }
.workspace-jumps { display: flex; flex-wrap: wrap; gap: 24px; margin-block: 24px; }
.workspace-jumps a { color: #2459a8; text-underline-offset: 4px; min-height: 44px; display: inline-flex; align-items: center; }
.workspace-heading { display: flex; flex-wrap: wrap; gap: 8px 24px; align-items: baseline; }
.candidate-empty { padding-block: 24px; }
.source-preview { white-space: pre-wrap; overflow-wrap: anywhere; font-size: .875rem; }
.progress-message, .success-message, [role="alert"] { padding: 16px; border-radius: 4px; margin-block: 16px; }
[role="alert"] { color: #8c3030; background: #fbecec; }
.progress-message, .success-message { color: #183f35; background: #edf7f3; }
.progress-message { display: flex; align-items: center; gap: 12px; position: sticky; top: calc(var(--klab-header-height, 0px) + 16px); z-index: 2; max-height: 30vh; overflow: auto; }
.progress-spinner { flex: 0 0 16px; height: 16px; border: 2px solid #91a1b1; border-top-color: #2459a8; border-radius: 50%; animation: review-progress 1s linear infinite; }
@keyframes review-progress { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) { .progress-spinner { animation: none; } }
@media (max-width: 1000px) { .review-workspace { grid-template-columns: minmax(0, 1fr); gap: 32px; } .feedback-panel { border-inline-start: 0; padding-inline-start: 0; border-block-start: 1px solid #cad5e2; padding-block-start: 32px; } }
@media (max-width: 600px) { .ontology-review { padding: 16px 8px; } .preparation-fields { grid-template-columns: minmax(0, 1fr); } .ontology-review :deep(.q-btn) { min-height: 44px; } }
</style>
