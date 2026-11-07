<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import type { ReviewCycle, ReviewView } from "../review-model";
import { reviewError } from "@klab-dashboard/services/review-errors";

const props = defineProps<{ context: DashboardContext; flowId: string; sourceStateId?: string;
  proposalAttachmentId?: string; ontologyAttachmentId?: string; candidateDirty?: boolean; candidateLoading?: boolean; stageId?: string }>();
const emit = defineEmits<{ candidate: [value: ReviewCycle]; revision: [value: number]; dirty: [value: boolean]; accessLost: []; view: [value: ReviewView]; stage: [value: string]; changed: [keepStage?: boolean] }>();
const view = ref<ReviewView>(), cycleId = ref(""), roundId = ref(""), contributor = ref("");
const draftId = ref(""), text = ref(""), original = ref(""), busy = ref(false), error = ref("");
const progress = ref("");
const selectionNotice = ref("");
const confirmingSubmission = ref(false), alternativeOpen = ref(false);
const receipt = ref<{ audience: string; proposalId: string; revisionId: string; contributionId?: string }>();
const draftNotice = ref("");
const pendingSubmission = ref<{ cycleId: string; roundId: string; draftId: string; text: string; document: string; ontology: string | null; knownIds: string[]; identity: string }>();
const audienceLabel = computed(() => round.value?.audience === "REVIEWER" ? "Peer review" : "Community review");
const hasContent = computed(() => !!(text.value.trim() || proposalDocument.value.trim()));
const canSaveDraft = () => canContribute.value && hasContent.value && dirty.value && !busy.value && !props.candidateLoading && !pendingSubmission.value;
const readableDate = (value: string) => { const date = new Date(value); return Number.isNaN(date.valueOf()) ? value : new Intl.DateTimeFormat('en', { dateStyle: 'medium', timeStyle: 'short' }).format(date); };
function fingerprintSafe() { return JSON.stringify([text.value, proposalDocument.value, replaceOntology.value, ontologySource.value]); }
const archiveReceipt = ref<{ state: string; commitId?: string; manifestSha256?: string }>();
watch(cycleId, () => { archiveReceipt.value = undefined; });
const proposalDocument = ref(""), ontologySource = ref(""), replaceOntology = ref(false), proposalFilter = ref("");
let generation = 0;
onUnmounted(() => { generation++; });
const endpoint = computed(() => `api/v1/flows/${encodeURIComponent(props.flowId)}/review-cycles`);
const cycle = computed(() => view.value?.cycles.find(c => c.id === cycleId.value));
const round = computed(() => cycle.value?.rounds.find(r => r.id === roundId.value));
const entries = computed(() => round.value?.contributions || []);
const contributors = computed(() => [...new Set(entries.value.map(c => c.owner))].sort());
const proposals = computed(() => entries.value.filter(c => c.proposal));
const filtered = computed(() => entries.value.filter(c => (!contributor.value || c.owner === contributor.value)
  && (!proposalFilter.value || (proposalFilter.value === "comments" ? !c.proposal : c.id === proposalFilter.value))));
const ownDrafts = computed(() => entries.value.filter(c => !c.submitted && c.owner === view.value?.identity));
const canContribute = computed(() => !!round.value && !!view.value?.contributableRoundIds.includes(round.value.id));
const fingerprint = () => JSON.stringify([text.value, proposalDocument.value, replaceOntology.value, ontologySource.value]);
const dirty = computed(() => fingerprint() !== original.value);
watch(dirty, value => emit("dirty", value));
watch([cycleId, roundId, fingerprintSafe], () => { confirmingSubmission.value = false; });
function failed(cause: unknown) {
  const detail = cause instanceof Error ? cause.message : String(cause);
  error.value = reviewError(cause);
  if (/^40[13]\b/.test(detail)) { view.value = undefined; archiveReceipt.value = undefined; receipt.value = undefined; emit("accessLost"); }
}
function updateLocation() {
  const url = new URL(window.location.href);
  if (cycleId.value) url.searchParams.set("cycle", cycleId.value); else url.searchParams.delete("cycle");
  if (roundId.value) url.searchParams.set("round", roundId.value); else url.searchParams.delete("round");
  window.history.replaceState(window.history.state, "", url);
}
function clearDraft() {
  draftId.value = ""; text.value = ""; proposalDocument.value = ""; ontologySource.value = "";
  replaceOntology.value = false; original.value = fingerprint();
}
function resetRoundDraft() { contributor.value = ""; proposalFilter.value = ""; clearDraft(); receipt.value = undefined; draftNotice.value = ""; }
function selectRound() {
  selectionNotice.value = ""; resetRoundDraft(); updateLocation();
  if (round.value?.stageId) emit("stage", round.value.stageId);
  if (cycle.value && round.value) emit("candidate", cycle.value); else emit("accessLost");
}
function selectCycle() {
  const requested = new URL(window.location.href).searchParams;
  const stageRound = view.value?.stages?.find(s => s.id === props.stageId && s.cycleId === cycleId.value)?.roundId;
  const requestedRound = requested.get("cycle") === cycleId.value ? requested.get("round") || stageRound : stageRound;
  const admitted = cycle.value?.rounds.find(r => r.id === requestedRound);
  roundId.value = admitted?.id || (requestedRound ? "" : cycle.value?.rounds.at(-1)?.id || "");
  resetRoundDraft();
  if (requestedRound && !admitted) {
    selectionNotice.value = "The requested iteration is unavailable to this account. Choose an authorized iteration to continue.";
    emit("accessLost"); // Preserve the requested URL; do not silently turn a protected link into another stage.
  } else {
    selectionNotice.value = ""; updateLocation();
    if (round.value?.stageId) emit("stage", round.value.stageId);
    if (cycle.value) emit("candidate", cycle.value);
  }
}
function selectDraft() {
  const draft = ownDrafts.value.find(c => c.id === draftId.value);
  text.value = draft?.text || ""; proposalDocument.value = draft?.proposal?.document || "";
  replaceOntology.value = draft?.proposal?.ontologySource != null; ontologySource.value = draft?.proposal?.ontologySource || "";
  original.value = fingerprint();
  if (proposalDocument.value) alternativeOpen.value = true;
  receipt.value = undefined; draftNotice.value = "";
}
function accept(next: ReviewView) {
  const retainedCycle = next.cycles.find(c => c.id === cycleId.value);
  const contextLost = !!cycleId.value && (!retainedCycle || (!!roundId.value && !retainedCycle.rounds.some(r => r.id === roundId.value)));
  view.value = next; emit("revision", next.revision); emit("view", next);
  if (props.stageId && next.stages && !next.stages.some(s => s.id === props.stageId)) {
    selectionNotice.value = "The requested stage is unavailable to this account. Choose an authorized stage to continue.";
    cycleId.value = ""; roundId.value = ""; emit("accessLost"); return;
  }
  if (contextLost) {
    contributor.value = ""; proposalFilter.value = "";
    selectionNotice.value = "The selected review context is no longer available. Retain or discard private work before choosing an authorized iteration.";
    emit("accessLost"); return; // Keep the draft's original cycle/round IDs; never redirect its contents to another audience.
  }
  if (!next.cycles.some(c => c.id === cycleId.value)) {
    const requested = new URL(window.location.href).searchParams.get("cycle") || next.stages?.find(s => s.id === props.stageId)?.cycleId;
    if (requested && !next.cycles.some(c => c.id === requested)) {
      cycleId.value = ""; roundId.value = "";
      selectionNotice.value = "The requested review cycle is unavailable to this account. Choose an authorized cycle to continue.";
      emit("accessLost"); return;
    }
    cycleId.value = next.cycles.find(c => c.id === requested)?.id || next.cycles.at(-1)?.id || ""; selectCycle();
  }
  if (!contributors.value.includes(contributor.value)) contributor.value = "";
}
async function refresh() {
  const current = generation;
  busy.value = true; error.value = ""; progress.value = "Loading review permissions and next steps...";
  try { const next = await props.context.api.get<ReviewView>(endpoint.value); if (current === generation) { accept(next); reconcileSubmission(next, true); } }
  catch (cause) { if (current === generation) failed(cause); } finally { if (current === generation) busy.value = false; }
}
async function command(operation: string, extra: Record<string, unknown> = {}) {
  if (!view.value || busy.value || props.candidateLoading) return;
  const current = generation;
  busy.value = true; error.value = "";
  draftNotice.value = "";
  progress.value = operation === "OPEN_CYCLE" ? "Starting peer review with the selected candidate..." : "Saving review changes...";
  try {
    const next = await props.context.api.request<ReviewView>(endpoint.value, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expectedRevision: view.value.revision, operation, cycleId: cycleId.value || null,
        roundId: roundId.value || null, ...extra }),
    });
    if (current !== generation) return;
    accept(next);
    if (["OPEN_CYCLE", "ENTER_COALESCING", "TERMINATE"].includes(operation)) emit("changed");
    if (operation === "OPEN_CYCLE") { cycleId.value = next.cycles.at(-1)?.id || ""; selectCycle(); }
    if (operation === "OPEN_ROUND") { roundId.value = cycle.value?.rounds.at(-1)?.id || ""; selectRound(); emit("changed", true); }
    if (operation === "CLOSE_ROUND") emit("changed", true);
    if (operation === "SAVE_CONTRIBUTION") {
      if (extra.submit) { reconcileSubmission(next); }
      else {
        draftId.value = typeof extra.contributionId === "string" ? extra.contributionId : ownDrafts.value.at(-1)?.id || draftId.value;
        original.value = fingerprint();
        draftNotice.value = "Private draft saved. It has not been shared with other participants.";
      }
    }
  } catch (cause) {
    if (current === generation) {
      const detail = cause instanceof Error ? cause.message : String(cause);
      if (/^4\d\d\b/.test(detail) && !/^408\b/.test(detail)) pendingSubmission.value = undefined;
      failed(cause);
    }
  } finally { if (current === generation) busy.value = false; }
}
function openCycle() {
  return command("OPEN_CYCLE", { sourceStateId: props.sourceStateId, proposalAttachmentId: props.proposalAttachmentId,
    ontologyAttachmentId: props.ontologyAttachmentId || null });
}
function save(submit: boolean) {
  if (!canContribute.value || !hasContent.value || busy.value || props.candidateLoading || pendingSubmission.value) return;
  if (submit && !confirmingSubmission.value) { confirmingSubmission.value = true; return; }
  if (submit) pendingSubmission.value = { cycleId: cycleId.value, roundId: roundId.value, draftId: draftId.value,
    text: text.value, document: proposalDocument.value.trim() ? proposalDocument.value : "", ontology: replaceOntology.value ? ontologySource.value : null,
    knownIds: entries.value.map(item => item.id), identity: view.value?.identity || "" };
  confirmingSubmission.value = false;
  return command("SAVE_CONTRIBUTION", { contributionId: draftId.value || null, text: text.value, submit,
    proposal: proposalDocument.value.trim() ? { document: proposalDocument.value, ontologySource: replaceOntology.value ? ontologySource.value : null } : null });
}
function reconcileSubmission(next: ReviewView, checked = false) {
  const pending = pendingSubmission.value;
  if (!pending) return;
  const submittedCycle = next.cycles.find(item => item.id === pending.cycleId);
  const submittedRound = submittedCycle?.rounds.find(item => item.id === pending.roundId);
  // Never clear uncertain private work unless the authoritative response identifies this exact contribution.
  const submitted = submittedRound?.contributions.find(item => item.submitted && item.owner === pending.identity
    && (pending.draftId ? item.id === pending.draftId : !pending.knownIds.includes(item.id))
    && item.text === pending.text && (item.proposal?.document || "") === pending.document
    && (item.proposal?.ontologySource ?? null) === (pending.document ? pending.ontology : null));
  if (submitted && submittedCycle && submittedRound) {
    receipt.value = { audience: submittedRound.audience === 'REVIEWER' ? 'Peer review' : 'Community review',
      proposalId: submittedCycle.candidate.proposalId, revisionId: submittedCycle.candidate.revisionId, contributionId: submitted.id };
    clearDraft(); pendingSubmission.value = undefined; alternativeOpen.value = false;
  } else if (checked && submittedRound) {
    pendingSubmission.value = undefined;
    draftNotice.value = "No matching submitted contribution was found. Your draft is retained. Review it before explicitly trying to share again.";
  }
}
function discardDraft() { clearDraft(); confirmingSubmission.value = false; }
async function saveDraft() { if (canSaveDraft()) await save(false); return !dirty.value; }
async function archive(retry: boolean) {
  const current = generation, selected = cycleId.value;
  if (!cycle.value?.closedAt || !view.value?.canManage) return;
  busy.value = true; error.value = "";
  progress.value = retry ? "Requesting the review archive..." : "Checking the review archive...";
  try {
    const path = `${endpoint.value}/${encodeURIComponent(selected)}/archive`;
    const receipt = retry ? await props.context.api.request<typeof archiveReceipt.value>(path, { method: "POST" })
      : await props.context.api.get<typeof archiveReceipt.value>(path);
    if (current === generation && selected === cycleId.value) archiveReceipt.value = receipt;
  } catch (cause) { if (current === generation) failed(cause); }
  finally { if (current === generation) busy.value = false; }
}
function downloadFeedback() {
  const url = URL.createObjectURL(new Blob([JSON.stringify({ cycleId: cycleId.value, roundId: roundId.value,
    text: text.value, proposal: proposalDocument.value || null, ontologySource: replaceOntology.value ? ontologySource.value : null }, null, 2)],
  { type: "application/json" }));
  const link = document.createElement("a"); link.href = url; link.download = "private-review-feedback.json"; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
watch(() => props.flowId, () => {
  generation++;
  view.value = undefined; cycleId.value = ""; roundId.value = "";
  selectionNotice.value = "";
  contributor.value = ""; proposalFilter.value = ""; clearDraft(); refresh();
}, { immediate: true });
defineExpose({ refresh, saveDraft, canSaveDraft, discardDraft, isBusy: () => busy.value });
</script>

<template>
  <section class="review-rounds" aria-label="Review rounds and contributions">
    <h2>{{ round?.audience === 'REVIEWER' ? 'Peer review' : round?.audience === 'COMMUNITY' ? 'Community review' : 'Review history and next steps' }}</h2>
    <p v-if="cycle" class="review-context">{{ cycle.candidate.proposalId }} · Revision {{ cycle.candidate.revisionId }}<br />{{ audienceLabel }} · {{ round?.closedAt || cycle.closedAt ? 'Closed — read-only' : 'Open' }}</p>
    <p v-if="busy" role="status" aria-live="polite">{{ progress }}</p>
    <p v-if="error" role="alert">{{ error }} Your unsaved feedback has been kept. Refresh rounds before retrying a conflict.</p>
    <p v-if="selectionNotice" role="status">{{ selectionNotice }}</p>
    <p v-if="draftNotice" role="status" class="feedback-notice">{{ draftNotice }}</p>
    <div v-if="receipt" class="feedback-receipt" role="status" aria-live="polite">
      <h3>Feedback shared</h3>
      <p>Your contribution to {{ receipt.proposalId }}, revision {{ receipt.revisionId }}, was shared with the permitted participants in {{ receipt.audience.toLowerCase() }}.</p>
      <p v-if="receipt.contributionId">Contribution: <span class="identifier">{{ receipt.contributionId }}</span></p>
      <p>This submitted record cannot be edited. Later corrections are new contributions.</p>
      <q-btn outline no-caps @click="receipt = undefined">Add another contribution</q-btn>
    </div>
    <div v-if="pendingSubmission && !busy" role="status" class="submission-warning">
      <p>The submission outcome is not yet confirmed. Your text is retained. Check its status before sharing again.</p>
      <q-btn outline no-caps @click="refresh">Check submission status</q-btn>
    </div>

    <fieldset v-if="canContribute || dirty" :disabled="busy || candidateLoading" class="feedback-composer">
      <legend>Your feedback</legend>
      <p v-if="!canContribute" role="status">This iteration no longer accepts your feedback. Your unsaved private work is retained below for download or copying.</p>
      <label v-if="ownDrafts.length">Draft<select v-model="draftId" aria-label="Your contribution draft" :disabled="dirty || !canContribute || !!pendingSubmission" @change="selectDraft">
        <option value="">New contribution</option><option v-for="(item, index) in ownDrafts" :key="item.id" :value="item.id">Draft {{ index + 1 }} · {{ readableDate(item.updatedAt) }}</option>
      </select></label>
      <label>Comment<textarea v-model="text" aria-label="Your feedback" :readonly="!canContribute || !!pendingSubmission || confirmingSubmission" maxlength="16000" rows="7" placeholder="Describe your assessment, questions or suggested changes." /></label>
      <details class="review-disclosure" :open="alternativeOpen" @toggle="alternativeOpen = ($event.target as HTMLDetailsElement).open">
        <summary>Propose an alternative (optional)</summary>
        <p>Use JSON or YAML matching the proposal document schema. A changed proposal needs a fresh revision ID. Incomplete alternatives can be saved privately.</p>
        <label>Alternative proposal JSON or YAML<textarea class="source-editor" v-model="proposalDocument" aria-label="Your alternative proposal" :readonly="!canContribute || !!pendingSubmission || confirmingSubmission" maxlength="4194304" rows="10" /></label>
        <label v-if="proposalDocument.trim()" class="checkbox-label"><input v-model="replaceOntology" :disabled="!canContribute || !!pendingSubmission || confirmingSubmission" type="checkbox" />Include alternative ontology source</label>
        <label v-if="proposalDocument.trim() && replaceOntology">Alternative ontology source<textarea class="source-editor" v-model="ontologySource" aria-label="Your alternative ontology source" :readonly="!canContribute || !!pendingSubmission || confirmingSubmission" maxlength="2097152" rows="7" /></label>
      </details>
      <p class="privacy-note">Drafts are private. Submitted feedback is shared with permitted participants in this round and cannot be edited.</p>
      <section v-if="confirmingSubmission" class="submission-review" aria-labelledby="submission-review-title">
        <h3 id="submission-review-title">Review before sharing</h3>
        <p><strong>{{ cycle?.candidate.proposalId }} · Revision {{ cycle?.candidate.revisionId }}</strong><br />Audience: permitted participants in {{ audienceLabel.toLowerCase() }}.</p>
        <p>This shares your comment<span v-if="proposalDocument.trim()"> and alternative proposal</span> as an immutable contribution. It does not accept or publish the ontology.</p>
        <div class="controls">
          <q-btn unelevated no-caps color="primary" :disable="!canContribute || !!pendingSubmission" @click="save(true)">Share feedback</q-btn>
          <q-btn flat no-caps @click="confirmingSubmission = false">Keep editing</q-btn>
        </div>
      </section>
      <div v-else class="controls">
        <q-btn outline no-caps :disable="!canSaveDraft()" @click="save(false)">Save private draft</q-btn>
        <q-btn unelevated no-caps color="primary" :disable="!canContribute || !hasContent || !!pendingSubmission" @click="save(true)">Submit feedback</q-btn>
      </div>
      <div v-if="dirty" class="controls">
        <q-btn flat no-caps @click="downloadFeedback">Download private feedback draft</q-btn>
        <q-btn v-if="!canContribute" flat no-caps @click="discardDraft">Discard unsaved feedback</q-btn>
      </div>
    </fieldset>

    <div class="controls"><q-btn outline no-caps :disable="busy" @click="refresh">Refresh rounds</q-btn></div>
    <template v-if="view">
      <p v-if="view.phase === 'BOOTSTRAP'">Bootstrap is private to the creating editor. Starting review freezes this exact candidate and opens peer review.</p>
      <p v-if="view.canOpenCycle && (!proposalAttachmentId || candidateDirty)" role="status">{{ candidateDirty ? 'Save or discard your unsaved preparation changes before starting review.' : 'First prepare or upload a proposal and save it in this stage. Then the start-review action becomes available.' }}</p>
      <p v-if="view.phase === 'COALESCING'">Integrate feedback into a fresh revision, then return to peer review or terminate without publication.</p>
      <p v-if="view.phase === 'TERMINATED'">This flow is terminated. Termination is not acceptance or publication. Its review history is read-only.</p>
      <div v-if="view.canOpenCycle || view.canCoalesce || view.canTerminate" class="review-management">
        <h3>Next review step</h3>
        <div class="controls">
          <q-btn v-if="view.canOpenCycle" unelevated no-caps color="primary" :disable="busy || candidateLoading || dirty || candidateDirty || !proposalAttachmentId || !sourceStateId" @click="openCycle">{{ view.phase === 'COALESCING' ? 'Return to peer review' : 'Start peer review' }}</q-btn>
          <q-btn v-if="view.canCoalesce" outline no-caps :disable="busy || candidateLoading || dirty || candidateDirty" @click="command('ENTER_COALESCING')">Coalesce feedback</q-btn>
          <q-btn v-if="view.canTerminate" outline no-caps :disable="busy || candidateLoading || dirty || candidateDirty" @click="command('TERMINATE')">Terminate flow without publication</q-btn>
        </div>
      </div>
      <p v-if="!view.cycles.length">No review cycles have been opened.</p>
      <details v-else class="review-disclosure">
        <summary>Contributions and review history</summary>
        <label>Candidate revision<select v-model="cycleId" aria-label="Feedback proposal" :disabled="busy || dirty || !!pendingSubmission" @change="selectCycle">
          <option v-if="!cycle" value="">Choose an authorized candidate revision</option>
          <option v-for="(item, index) in view.cycles" :key="item.id" :value="item.id">{{ item.candidate.proposalId }} · {{ item.candidate.revisionId }} · Cycle {{ index + 1 }} · {{ item.closedAt ? 'Closed' : 'Open' }}</option>
        </select></label>
        <template v-if="cycle">
          <p v-if="cycle.closedAt">This cycle is closed and its history is immutable.</p>
          <div v-if="view.canOpenRound && !cycle.closedAt" class="controls">
            <q-btn outline no-caps :disable="busy || dirty" @click="command('OPEN_ROUND', { audience: 'REVIEWER' })">Open reviewer round</q-btn>
            <q-btn outline no-caps :disable="busy || dirty" @click="command('OPEN_ROUND', { audience: 'COMMUNITY' })">Open community round</q-btn>
          </div>
          <div v-if="view.canManage && cycle.closedAt" class="controls">
            <q-btn outline no-caps :disable="busy" @click="archive(false)">Check digest receipt</q-btn>
            <q-btn outline no-caps :disable="busy || archiveReceipt?.state === 'COMMITTED'" @click="archive(true)">Retry digest archive</q-btn>
            <p v-if="archiveReceipt" role="status">Digest archive: {{ archiveReceipt.state }}.<template v-if="archiveReceipt.commitId">Local commit <code>{{ archiveReceipt.commitId }}</code>.</template> Detailed feedback remains in the permission-protected workflow.</p>
          </div>
          <label>Review round<select v-model="roundId" aria-label="Feedback iteration" :disabled="busy || dirty || !!pendingSubmission" @change="selectRound">
            <option value="">Choose a round</option><option v-for="(item, index) in cycle.rounds" :key="item.id" :value="item.id">{{ item.audience === 'REVIEWER' ? 'Peer' : 'Community' }} · Round {{ index + 1 }} · {{ item.closedAt ? 'Closed' : 'Open' }}</option>
          </select></label>
          <details class="review-disclosure"><summary>Filter contributions</summary>
            <div class="controls">
              <label>Contributor<select v-model="contributor" aria-label="Feedback contributor" :disabled="busy"><option value="">All contributors</option><option v-for="owner in contributors" :key="owner" :value="owner">{{ owner }}</option></select></label>
              <label>Contribution proposal<select v-model="proposalFilter" aria-label="Contribution proposal" :disabled="busy"><option value="">All proposals and comments</option><option value="comments">Comments only</option><option v-for="item in proposals" :key="item.id" :value="item.id">{{ item.owner }} · {{ item.proposal?.proposalId || 'Incomplete draft' }} · {{ item.proposal?.revisionId || 'Unversioned' }}</option></select></label>
            </div>
          </details>
          <q-btn v-if="view.canManage && round && !round.closedAt && !cycle.closedAt" outline no-caps :disable="busy || dirty" @click="command('CLOSE_ROUND')">Close iteration</q-btn>
          <p v-if="round" role="status">Showing {{ filtered.length }} of {{ entries.length }} visible contributions. Submitted feedback is shared within this iteration; drafts remain private.</p>
          <article v-for="item in filtered" :key="item.id" class="contribution">
            <header><strong>{{ item.owner }}</strong> · {{ item.submitted ? 'Submitted' : 'Your private draft' }}<br /><time :datetime="item.updatedAt">{{ readableDate(item.updatedAt) }}</time></header>
            <p>{{ item.text }}</p>
            <details v-if="item.proposal" class="review-disclosure"><summary>Alternative {{ item.proposal.proposalId || 'draft' }} · {{ item.proposal.revisionId || 'incomplete' }} · Document schema {{ item.proposal.schemaCheck.status }}</summary>
              <p>Against this round's frozen candidate. Document schema checks do not imply scientific approval.</p>
              <textarea class="source-editor" :value="item.proposal.document" :aria-label="`Proposal by ${item.owner}`" readonly rows="12" />
              <textarea v-if="item.proposal.ontologySource != null" class="source-editor" :value="item.proposal.ontologySource" :aria-label="`Ontology source by ${item.owner}`" readonly rows="7" /><p v-else>Retains the candidate's ontology source.</p>
            </details>
          </article>
        </template>
      </details>
    </template>
  </section>
</template>
<style scoped>
.review-rounds { color: #17283b; font-size: 1rem; line-height: 1.55; min-width: 0; }
h2 { text-wrap: balance; font-size: 1.375rem; line-height: 1.3; font-weight: 600; margin: 0 0 16px; }
h3 { text-wrap: balance; font-size: 1.125rem; line-height: 1.4; font-weight: 600; margin: 0 0 12px; }
p, header, summary { overflow-wrap: anywhere; }
p { text-wrap: pretty; }
.review-context { color: #53667b; margin-block-end: 24px; }
.controls { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-block: 16px; }
label { display: grid; gap: 8px; min-width: 0; max-width: 100%; margin-block: 16px; }
.controls label { flex: 1 1 160px; }
input, select, textarea { padding: 12px; color: #17283b; background: #fff; border: 1px solid #91a1b1; border-radius: 4px; font: inherit; width: 100%; min-width: 0; max-width: 100%; caret-color: #2459a8; box-sizing: border-box; }
textarea { resize: vertical; }
select { text-overflow: ellipsis; }
textarea:focus-visible, select:focus-visible, input:focus-visible, summary:focus-visible { outline: 3px solid #2459a8; outline-offset: 3px; }
::selection { background: #dce9fa; color: #17283b; }
.checkbox-label { display: flex; align-items: start; gap: 8px; }
.checkbox-label input { width: auto; margin-block-start: 6px; }
.source-editor { font: .875rem/1.6 monospace; background: #f7f9fc; }
.feedback-composer { border: 0; padding: 0; margin: 24px 0; min-width: 0; }
.feedback-composer legend { font-size: 1.125rem; font-weight: 600; margin-block-end: 8px; }
.privacy-note { font-size: .875rem; color: #53667b; }
.review-disclosure { border-block-start: 1px solid #cad5e2; padding-block: 16px; margin-block: 16px; min-width: 0; }
.review-disclosure > summary { padding-block: 8px; min-height: 44px; cursor: pointer; font-weight: 600; display: list-item; list-style: disclosure-closed inside; }
.review-disclosure[open] > summary { list-style-type: disclosure-open; }
.review-disclosure > summary::marker { color: #53667b; }
.contribution { padding-block: 24px; border-block-start: 1px solid #cad5e2; }
.contribution p { white-space: pre-wrap; overflow-wrap: anywhere; }
.contribution time { font-size: .875rem; color: #53667b; }
.feedback-receipt, .feedback-notice { padding: 16px; color: #183f35; background: #edf7f3; margin-block: 16px; border-radius: 4px; }
.submission-review { background: #f7f9fc; padding: 16px; border: 1px solid #cad5e2; }
.submission-warning { padding: 16px; color: #805c1d; background: #fff7e5; }
.identifier { overflow-wrap: anywhere; }
[role="alert"] { color: #8c3030; background: #fbecec; padding: 16px; border-radius: 4px; }
@media (max-width: 600px) { .controls .q-btn { min-height: 44px; } }
</style>
