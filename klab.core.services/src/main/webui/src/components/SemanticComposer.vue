<script setup lang="ts">
import "../styles/theme.css";
import "../knowledge/knowledge.css";
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, useId } from "vue";
import type { ServiceApi } from "../services/api";
import { post, type SemanticMatch, type SemanticMode, type SemanticResponse } from "../knowledge/model";

const props = withDefaults(defineProps<{
  api: ServiceApi;
  authorities?: { localId: string; provider?: { searchable?: boolean } }[];
  disabled?: boolean;
}>(), { authorities: () => [] });
const emit = defineEmits<{ select: [SemanticResponse]; change: [SemanticResponse]; error: [Error] }>();
const id = useId(), root = ref<HTMLElement>(), input = ref<HTMLInputElement>();
const query = ref(""), opened = ref(false), busy = ref(false), uncertain = ref(false), error = ref("");
const state = shallowRef<SemanticResponse>(), matches = shallowRef<SemanticMatch[]>([]);
const selected = ref(0), source = ref(""), filter = ref("all");
const ready = ref(false);
const restartNeeded = ref(false);
let requestId = 0, searchId = 0, revision = 0, disposed = false;
let timer: ReturnType<typeof setTimeout> | undefined;
let active: AbortController | undefined;
let queue: Promise<void> = Promise.resolve();
const rows = computed(() => matches.value.filter(m => filter.value === "all" ||
  (filter.value === "concepts" ? m.matchType === "CONCEPT" : m.matchType !== "CONCEPT")));
const canSubmit = computed(() => !props.disabled && !busy.value && !uncertain.value && !query.value.trim() && !!state.value?.declaration);
const current = computed(() => rows.value[selected.value]);
function tokenText(token: SemanticResponse["code"][number], index: number): string {
  const code = state.value?.code || [];
  const before = index > 0 && code[index - 1].needsWhitespaceAfter && token.needsWhitespaceBefore ? " " : "";
  const after = index === code.length - 1 && token.needsWhitespaceAfter ? " " : "";
  return before + token.value + after;
}

function submit() { if (canSubmit.value && state.value) { opened.value = false; emit("select", state.value); } }
function schedule() {
  revision++; ready.value = false; matches.value = []; selected.value = 0;
  clearTimeout(timer); opened.value = true;
  timer = setTimeout(() => { void search(); }, 350);
}
async function search() {
  const version = revision, text = query.value, authority = source.value;
  if (authority && !text.trim()) { matches.value = []; return; }
  if (!authority && !uncertain.value && !state.value?.acceptsValue && /^[\p{L}\p{N}]$/u.test(text)) return;
  await enqueue("TOKEN", version, text);
  if (!authority || version !== revision || disposed || uncertain.value) return;
  busy.value = true; ready.value = false;
  const controller = new AbortController(); active = controller;
  const timeout = setTimeout(() => controller.abort(), 30000);
  try {
    const result = await post<{ status: string; matches: { id: string; label: string; description: string }[] }>(props.api,
      "authority/search", { authority, query: text, offset: 0, limit: 25 }, controller.signal);
    if (version !== revision || disposed) return;
    if (result.status !== "OK") throw new Error(`Authority search: ${result.status.toLowerCase()}`);
    matches.value = result.matches.map(m => ({ id: m.id, name: m.label || m.id, description: m.description, matchType: "IDENTITY" }));
    ready.value = true;
  } catch (cause) { if (!disposed && version === revision) fail(cause); }
  finally { clearTimeout(timeout); if (!disposed) busy.value = false; }
}
function fail(cause: unknown) {
  const failure = cause instanceof Error ? cause : new Error(String(cause));
  error.value = failure.name === "AbortError" ? "The Reasoner timed out. Retry to recover the confirmed expression." : failure.message;
  emit("error", failure);
}
function enqueue(mode: SemanticMode, version = revision, text = query.value, match?: SemanticMatch): Promise<void> {
  const operation = async () => {
    if (disposed || props.disabled || (mode === "TOKEN" && version !== revision)) return;
    busy.value = true; ready.value = false; error.value = "";
    const controller = new AbortController(); active = controller;
    const timeout = setTimeout(() => controller.abort(), 30000);
    const session = searchId;
    try {
      const response = await post<SemanticResponse>(props.api, "semanticSearch", {
        searchId, requestId: ++requestId, searchMode: mode, queryString: text, maxResults: 25,
        selectedMatchId: match?.id, matchesRequestId: state.value?.requestId || 0,
        ...(mode === "IDENTITY" ? { authority: source.value, identityCode: match?.id } : {}),
      }, controller.signal);
      if (disposed) {
        if (response.searchId) void release(response.searchId);
        return;
      }
      searchId = response.searchId;
      if (!response.searchId && response.errors?.length) {
        // Expired sessions cannot authorize an edit or silently replace the visible expression.
        restartNeeded.value = true; uncertain.value = true;
        matches.value = []; error.value = response.errors.join(" · "); return;
      }
      state.value = response; uncertain.value = false;
      restartNeeded.value = false;
      error.value = (response.errors || []).join(" · "); emit("change", response);
      if (version === revision) {
        if (mode !== "TOKEN" && !response.errors?.length) { query.value = ""; opened.value = false; }
        if (mode === "IDENTITY" && !response.errors?.length) { source.value = ""; filter.value = "all"; }
        matches.value = source.value ? [] : response.matches || [];
        ready.value = !source.value; selected.value = 0;
      }
    } catch (cause) {
      // Never replay an uncertain edit. Recover with a read-only TOKEN request.
      uncertain.value ||= mode !== "TOKEN" || !session;
      if (!disposed) fail(cause);
    } finally { clearTimeout(timeout); if (!disposed) busy.value = false; }
  };
  queue = queue.then(operation, operation);
  return queue;
}
async function choose(match = current.value) {
  if (!match || !ready.value || busy.value || uncertain.value || props.disabled) return;
  clearTimeout(timer);
  await enqueue(match.matchType === "IDENTITY" ? "IDENTITY" : "SELECT", revision, "", match);
  await nextTick(); input.value?.focus();
}
function key(event: KeyboardEvent) {
  if (event.isComposing || props.disabled) return;
  const field = event.target as HTMLInputElement;
  const atEnd = field.selectionStart === query.value.length && field.selectionEnd === query.value.length;
  const boundary = field.selectionStart === 0 && field.selectionEnd === 0;
  if (event.key === "Escape") { event.preventDefault(); opened.value = false; return; }
  if (["ArrowDown", "ArrowUp"].includes(event.key)) {
    event.preventDefault(); opened.value = true;
    selected.value = Math.max(0, Math.min(rows.value.length - 1, selected.value + (event.key === "ArrowDown" ? 1 : -1)));
    void nextTick(() => document.getElementById(`${id}-option-${selected.value}`)?.scrollIntoView({ block: "nearest" })); return;
  }
  if (event.repeat && ["Enter", " ", "(", ")", "Backspace"].includes(event.key)) { if (event.key !== "Backspace" || boundary) event.preventDefault(); return; }
  if (event.key === "Enter") {
    event.preventDefault();
    if (event.ctrlKey || event.metaKey) submit();
    else if (atEnd && query.value.trim() && !busy.value && !uncertain.value) {
      if (state.value?.acceptsValue && !source.value) void enqueue("VALUE");
      else void choose();
    }
  } else if (event.key === " " && atEnd && !state.value?.acceptsValue &&
    current.value && [current.value.name, current.value.id].some(s => s.toLowerCase() === query.value.toLowerCase()) && ready.value) {
    event.preventDefault(); void choose();
  } else if (event.key === "Backspace" && boundary && !query.value && state.value?.canUndo && !busy.value && !uncertain.value) {
    event.preventDefault(); revision++; clearTimeout(timer); void enqueue("UNDO");
  } else if (!source.value && !state.value?.acceptsValue && !busy.value && !query.value &&
    ((event.key === "(" && state.value?.canOpenScope) || (event.key === ")" && state.value?.canCloseScope))) {
    event.preventDefault(); revision++; clearTimeout(timer); void enqueue(event.key === "(" ? "OPEN_SCOPE" : "CLOSE_SCOPE");
  }
}
async function release(session: number) {
  try { await post(props.api, "semanticSearch", { searchId: session, requestId: ++requestId, cancelSearch: true }); } catch { /* Expiry also releases abandoned sessions. */ }
}
function retry() {
  if (restartNeeded.value) { state.value = undefined; restartNeeded.value = false; }
  revision++; opened.value = true; input.value?.focus(); void search();
}
function closeOutside(event: FocusEvent) { if (!root.value?.contains(event.relatedTarget as Node)) opened.value = false; }
onBeforeUnmount(() => { disposed = true; clearTimeout(timer); active?.abort(); if (searchId) void release(searchId); });
defineExpose({ focus: () => input.value?.focus(), submit });
</script>

<template>
  <div ref="root" class="semantic-composer knowledge-ui" @focusout="closeOutside" @keydown.esc="opened = false">
    <div class="semantic-field" :class="{ 'has-error': error }">
      <label class="semantic-label" :for="id">Concept</label>
      <div class="semantic-expression">
        <span v-for="(token, index) in state?.code || []" :key="index" :title="token.description"
          :style="{ color: `var(--semantic-${token.color}, var(--klab-ink))`, fontWeight: token.font?.includes('BOLD') ? '700' : undefined,
            fontStyle: token.font?.includes('ITALIC') ? 'italic' : undefined }">{{ tokenText(token, index) }}</span>
        <input :id="id" ref="input" v-model="query" role="combobox" aria-label="Compose a concept"
          :aria-expanded="opened" :aria-controls="`${id}-results`" :aria-activedescendant="opened && current ? `${id}-option-${selected}` : undefined"
          aria-autocomplete="list" autocomplete="off" :disabled="disabled" placeholder="Find a concept or compose an expression…"
          @input="schedule" @keydown="key" />
      </div>
      <button v-if="error" type="button" :disabled="busy || disabled" @click="retry">{{ restartNeeded ? 'Restart' : 'Retry' }}</button>
      <button type="button" aria-label="Explore confirmed expression" title="Explore confirmed expression (Ctrl+Enter)" :disabled="!canSubmit" @click="submit">{{ busy ? '…' : '→' }}</button>
    </div>
    <div v-if="error" role="alert" class="knowledge-error">{{ error }}</div>
    <div v-if="opened" class="semantic-overlay">
      <div class="semantic-choosers">
        <label>Source <select v-model="source" :disabled="busy || disabled" @change="schedule">
          <option value="">Concepts / operators</option>
          <option v-for="binding in authorities.filter(a => a.provider?.searchable)" :key="binding.localId" :value="binding.localId">{{ binding.localId }}</option>
        </select></label>
        <label v-if="!source">Show <select v-model="filter" @change="selected = 0"><option value="all">All matches</option><option value="concepts">Concepts</option><option value="operators">Operators</option></select></label>
        <button type="button" aria-label="Close suggestions" @click="opened = false">×</button>
      </div>
      <div v-if="busy" role="status" class="semantic-status">Asking the Reasoner…</div>
      <div v-else-if="!rows.length" class="semantic-status">{{ state?.acceptsValue ? 'Enter a literal value and press Enter.' : 'No matches. Type a concept name or namespace:name.' }}</div>
      <ul :id="`${id}-results`" role="listbox" aria-label="Semantic suggestions" class="semantic-results">
        <li v-for="(match, index) in rows" :id="`${id}-option-${index}`" :key="match.id" role="option" :aria-selected="index === selected"
          @mousemove="selected = index" @mousedown.prevent="choose(match)">
          <strong>{{ match.name }}</strong><span>{{ match.id }}</span><small>{{ match.matchType.toLowerCase().replaceAll('_', ' ') }}</small>
        </li>
      </ul>
      <div v-if="current?.description || state?.description" class="semantic-documentation">{{ current?.description || state?.description }}</div>
    </div>
  </div>
</template>

<style scoped>
.semantic-composer { position: relative; min-width: 0; }
.semantic-label { padding-left: 8px; color: var(--klab-muted); }
.semantic-field { display: flex; gap: 6px; align-items: center; padding: 4px; border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius); background: var(--klab-surface); }
.semantic-field:focus-within { outline: 2px solid var(--klab-action); outline-offset: 1px; }
.semantic-field.has-error { border-color: var(--klab-danger); }
.semantic-expression { flex: 1; min-width: 0; display: flex; overflow-x: auto; align-items: center; padding-left: 8px; white-space: pre; font-family: var(--klab-mono); }
.semantic-expression input { flex: 1; min-width: 190px; border: 0; outline: none; font-family: inherit; }
.semantic-expression input::placeholder { color: var(--klab-muted); }
.semantic-field > button { min-width: 34px; }
.semantic-overlay { position: absolute; z-index: 20; top: calc(100% + 6px); left: 0; right: 0; max-height: min(62vh, 460px); overflow: auto; background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); box-shadow: var(--klab-shadow); }
.semantic-choosers { display: flex; gap: 16px; flex-wrap: wrap; align-items: center; padding: 8px 12px; border-bottom: 1px solid var(--klab-border); }
.semantic-choosers label { display: flex; gap: 8px; align-items: center; color: var(--klab-muted); }
.semantic-choosers > button { margin-left: auto; }
.semantic-results { margin: 0; padding: 0; list-style: none; max-height: 250px; overflow: auto; }
.semantic-results li { display: grid; grid-template-columns: minmax(100px, 1fr) minmax(100px, 1.5fr) 100px; gap: 12px; padding: 8px 12px; cursor: pointer; }
.semantic-results li[aria-selected="true"] { background: var(--klab-selection); }
.semantic-results li span, .semantic-results li small { color: var(--klab-muted); overflow-wrap: anywhere; }
.semantic-documentation, .semantic-status { padding: 12px; color: var(--klab-muted); white-space: pre-wrap; }
.semantic-documentation { border-top: 1px solid var(--klab-border); }
@media (max-width: 600px) { .semantic-results li { grid-template-columns: 1fr 1fr; } .semantic-results small { display: none; } .semantic-choosers { gap: 6px; } .semantic-choosers select { max-width: 200px; } }
</style>
