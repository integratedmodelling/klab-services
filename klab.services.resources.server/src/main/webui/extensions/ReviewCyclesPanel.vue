<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import type { ReviewCycle, ReviewView } from "../review-model";

const props = defineProps<{ context: DashboardContext; flowId: string; sourceStateId?: string;
  proposalAttachmentId?: string; ontologyAttachmentId?: string; candidateDirty?: boolean; candidateLoading?: boolean }>();
const emit = defineEmits<{ candidate: [value: ReviewCycle]; revision: [value: number]; dirty: [value: boolean] }>();
const view = ref<ReviewView>(), cycleId = ref(""), roundId = ref(""), contributor = ref("");
const draftId = ref(""), text = ref(""), original = ref(""), busy = ref(false), error = ref("");
const endpoint = computed(() => `api/v1/flows/${encodeURIComponent(props.flowId)}/review-cycles`);
const cycle = computed(() => view.value?.cycles.find(c => c.id === cycleId.value));
const round = computed(() => cycle.value?.rounds.find(r => r.id === roundId.value));
const entries = computed(() => round.value?.contributions || []);
const contributors = computed(() => [...new Set(entries.value.map(c => c.owner))].sort());
const filtered = computed(() => entries.value.filter(c => !contributor.value || c.owner === contributor.value));
const ownDrafts = computed(() => entries.value.filter(c => !c.submitted && c.owner === view.value?.identity));
const canContribute = computed(() => !!round.value && !!view.value?.contributableRoundIds.includes(round.value.id));
const dirty = computed(() => text.value !== original.value);
watch(dirty, value => emit("dirty", value));
function failed(cause: unknown) { error.value = cause instanceof Error ? cause.message : String(cause); }
function updateLocation() {
  const url = new URL(window.location.href);
  if (cycleId.value) url.searchParams.set("cycle", cycleId.value); else url.searchParams.delete("cycle");
  if (roundId.value) url.searchParams.set("round", roundId.value); else url.searchParams.delete("round");
  window.history.replaceState({}, "", url);
}
function selectRound() { contributor.value = ""; draftId.value = ""; text.value = ""; original.value = ""; updateLocation(); }
function selectCycle() {
  const requested = new URL(window.location.href).searchParams;
  const requestedRound = requested.get("cycle") === cycleId.value ? requested.get("round") : null;
  roundId.value = cycle.value?.rounds.find(r => r.id === requestedRound)?.id || cycle.value?.rounds.at(-1)?.id || "";
  selectRound();
  if (cycle.value) emit("candidate", cycle.value);
}
function selectDraft() { text.value = ownDrafts.value.find(c => c.id === draftId.value)?.text || ""; original.value = text.value; }
function accept(next: ReviewView) {
  view.value = next; emit("revision", next.revision);
  if (!next.cycles.some(c => c.id === cycleId.value)) {
    const requested = new URL(window.location.href).searchParams.get("cycle");
    cycleId.value = next.cycles.find(c => c.id === requested)?.id || next.cycles.at(-1)?.id || ""; selectCycle();
  }
  else if (!cycle.value?.rounds.some(r => r.id === roundId.value)) { roundId.value = cycle.value?.rounds.at(-1)?.id || ""; }
  if (!contributors.value.includes(contributor.value)) contributor.value = "";
}
async function refresh() {
  busy.value = true; error.value = "";
  try { accept(await props.context.api.get<ReviewView>(endpoint.value)); }
  catch (cause) { failed(cause); } finally { busy.value = false; }
}
async function command(operation: string, extra: Record<string, unknown> = {}) {
  if (!view.value) return;
  busy.value = true; error.value = "";
  try {
    const next = await props.context.api.request<ReviewView>(endpoint.value, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ expectedRevision: view.value.revision, operation, cycleId: cycleId.value || null,
        roundId: roundId.value || null, ...extra }),
    });
    accept(next);
    if (operation === "OPEN_CYCLE") { cycleId.value = next.cycles.at(-1)?.id || ""; selectCycle(); }
    if (operation === "OPEN_ROUND") { roundId.value = cycle.value?.rounds.at(-1)?.id || ""; selectRound(); }
    if (operation === "SAVE_CONTRIBUTION") {
      if (extra.submit) { draftId.value = ""; text.value = ""; original.value = ""; }
      else {
        draftId.value = typeof extra.contributionId === "string" ? extra.contributionId : ownDrafts.value.at(-1)?.id || draftId.value;
        original.value = text.value;
      }
    }
  } catch (cause) { failed(cause); } finally { busy.value = false; }
}
function openCycle() {
  return command("OPEN_CYCLE", { sourceStateId: props.sourceStateId, proposalAttachmentId: props.proposalAttachmentId,
    ontologyAttachmentId: props.ontologyAttachmentId || null });
}
function save(submit: boolean) { return command("SAVE_CONTRIBUTION", { contributionId: draftId.value || null, text: text.value, submit }); }
watch(() => props.flowId, () => {
  view.value = undefined; cycleId.value = ""; roundId.value = "";
  contributor.value = ""; draftId.value = ""; text.value = ""; original.value = ""; refresh();
}, { immediate: true });
</script>

<template>
  <section class="review-rounds" aria-label="Review rounds and contributions">
    <h2>Rounds and feedback</h2>
    <p v-if="error" role="alert">{{ error }} Your unsaved feedback has been kept. Refresh rounds before retrying a conflict.</p>
    <div class="controls">
      <button :disabled="busy" @click="refresh">Refresh rounds</button>
      <button v-if="view?.canManage" :disabled="busy || dirty || candidateDirty || !proposalAttachmentId || !sourceStateId" @click="openCycle">Start cycle from selected proposal</button>
    </div>
    <template v-if="view">
      <p v-if="!view.cycles.length">No review cycles have been opened.</p>
      <label v-else>Proposal and cycle <select v-model="cycleId" aria-label="Feedback proposal" :disabled="busy || dirty" @change="selectCycle">
        <option v-for="item in view.cycles" :key="item.id" :value="item.id">{{ item.candidate.proposalId }} · {{ item.candidate.revisionId }} · {{ item.id.slice(0, 8) }} · {{ item.closedAt ? 'Closed' : 'Open' }}</option>
      </select></label>
      <template v-if="cycle">
        <p>Feedback concerns proposal {{ cycle.candidate.proposalId }}, revision {{ cycle.candidate.revisionId }}. {{ cycle.closedAt ? 'This cycle is closed and its history is immutable.' : '' }}</p>
        <div v-if="view.canManage && !cycle.closedAt" class="controls">
          <button :disabled="busy || dirty" @click="command('OPEN_ROUND', { audience: 'REVIEWER' })">Open reviewer round</button>
          <button :disabled="busy || dirty" @click="command('OPEN_ROUND', { audience: 'COMMUNITY' })">Open community round</button>
          <button :disabled="busy || dirty" @click="command('CLOSE_CYCLE', { roundId: null })">Close cycle</button>
        </div>
        <div class="controls">
          <label>Iteration <select v-model="roundId" aria-label="Feedback iteration" :disabled="busy || dirty" @change="selectRound">
            <option value="">Choose an iteration</option>
            <option v-for="item in cycle.rounds" :key="item.id" :value="item.id">{{ item.audience === 'REVIEWER' ? 'Reviewer' : 'Community' }} · {{ item.id.slice(0, 8) }} · {{ item.closedAt ? 'Closed' : 'Open' }}</option>
          </select></label>
          <label>Contributor <select v-model="contributor" aria-label="Feedback contributor" :disabled="busy">
            <option value="">All contributors</option><option v-for="owner in contributors" :key="owner" :value="owner">{{ owner }}</option>
          </select></label>
          <button v-if="view.canManage && round && !round.closedAt && !cycle.closedAt" :disabled="busy || dirty" @click="command('CLOSE_ROUND')">Close iteration</button>
        </div>
        <p v-if="round" role="status">Showing {{ filtered.length }} of {{ entries.length }} visible contributions. Submitted feedback is shared within this iteration; drafts remain private.</p>
        <article v-for="item in filtered" :key="item.id" class="contribution">
          <header><strong>{{ item.owner }}</strong> · {{ item.submitted ? 'Submitted' : 'Your private draft' }} · <time>{{ item.updatedAt }}</time></header>
          <p>{{ item.text }}</p>
        </article>
        <fieldset v-if="canContribute" :disabled="busy || candidateLoading">
          <legend>Your feedback</legend>
          <label>Draft <select v-model="draftId" aria-label="Your contribution draft" :disabled="dirty" @change="selectDraft">
            <option value="">New contribution</option><option v-for="item in ownDrafts" :key="item.id" :value="item.id">{{ item.id.slice(0, 8) }} · {{ item.updatedAt }}</option>
          </select></label>
          <textarea v-model="text" aria-label="Your feedback" maxlength="16000" rows="7" />
          <div class="controls"><button :disabled="!text.trim() || !dirty" @click="save(false)">Save private draft</button>
            <button :disabled="!text.trim()" @click="save(true)">Submit feedback</button></div>
          <p>Submitting preserves this text as history. Later corrections are new contributions.</p>
        </fieldset>
      </template>
    </template>
  </section>
</template>

<style scoped>
.controls { display: flex; flex-wrap: wrap; align-items: end; gap: 10px; margin: 12px 0; }
label { display: grid; gap: 5px; } input, select, button, textarea { padding: 8px; color: inherit; background: transparent; border: 1px solid #70999d; border-radius: 4px; }
textarea { width: 100%; margin-top: 12px; } button { cursor: pointer; } button:disabled { opacity: .55; cursor: default; }
.contribution { padding: 12px; margin: 12px 0; border: 1px solid #70999d; } .contribution p { white-space: pre-wrap; overflow-wrap: anywhere; }
[role="alert"] { color: #ffb0b0; }
</style>
