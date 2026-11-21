<script setup lang="ts">
import "../styles/theme.css";
import "../knowledge/knowledge.css";
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import type { ServiceApi } from "../services/api";
import { conceptLabel, conceptDocumentation, graphRelations, defaultGraphRelations, loadNeighborhood, semanticColor, WorldviewCache,
  type Concept, type Declaration, type Neighborhood, type WorldviewCatalog } from "../knowledge/model";
import { layoutChart, toSprotty, type FlowChart } from "../flowchart/model";
import { createViewer } from "../flowchart/viewer";
import WorldviewTree from "./WorldviewTree.vue";
import ObservableCard from "./ObservableCard.vue";
import { documentationMarkdown } from "../knowledge/markdown";

const props = withDefaults(defineProps<{
  api: ServiceApi; focus?: string; catalog?: WorldviewCatalog; disabled?: boolean;
  /** Disable the tree and its toggle for graph-only embedding. */
  showTree?: boolean;
  /** Host container to include the composer in full-screen mode. Defaults to this browser. */
  fullscreenTarget?: HTMLElement;
}>(), { showTree: true });
const emit = defineEmits<{ select: [Concept]; loaded: [WorldviewCatalog]; error: [Error] }>();
const id = `worldview-${crypto.randomUUID()}`, host = ref<HTMLElement>();
const root = ref<HTMLElement>();
const expanded = ref(new Set<string>()), detailsOpen = ref(false), fullscreen = ref(false);
const tooltip = shallowRef<{ concept: Concept; x: number; y: number }>();
let hoverTimer: ReturnType<typeof setTimeout> | undefined;
function keepTooltip() { clearTimeout(hoverTimer); }
function hoverConcept(urn: string | undefined, event: MouseEvent) {
  clearTimeout(hoverTimer);
  const concept = snapshot.value?.concepts.find(c => c.urn === urn);
  if (!concept || !conceptDocumentation(concept)) { hoverTimer = setTimeout(() => { tooltip.value = undefined; }, 100); return; }
  const x = Math.max(12, Math.min(event.clientX + 14, window.innerWidth - Math.min(360, window.innerWidth - 24) - 12));
  const y = Math.max(12, Math.min(event.clientY + 14, window.innerHeight - 260));
  tooltip.value = { concept, x, y };
}
function toggleBranch(urn: string, open: boolean) {
  if (expanded.value.has(urn) === open) return;
  const next = new Set(expanded.value); if (open) next.add(urn); else next.delete(urn); expanded.value = next;
}
function expandTree(open: boolean) {
  treeQuery.value = "";
  expanded.value = open ? new Set([...(catalog.value?.ontologies.map(o => o.urn) || []), ...allDeclarations.value.map(d => d.urn)]) : new Set();
}
let fullscreenElement: HTMLElement | undefined, fallbackFullscreen = false, previousOverflow = "";
let inertSiblings: { element: HTMLElement; previous: boolean }[] = [];
function leaveFullscreen() {
  fullscreenElement?.classList.remove("knowledge-fullscreen");
  if (fallbackFullscreen) document.body.style.overflow = previousOverflow;
  for (const { element, previous } of inertSiblings) element.inert = previous;
  inertSiblings = [];
  fallbackFullscreen = false; fullscreen.value = false;
}
function fullscreenChanged() {
  if (document.fullscreenElement === fullscreenElement) fullscreen.value = true;
  else if (!fallbackFullscreen) leaveFullscreen();
}
async function toggleFullscreen() {
  if (fullscreen.value) {
    if (document.fullscreenElement === fullscreenElement) await document.exitFullscreen();
    leaveFullscreen(); return;
  }
  const target = props.fullscreenTarget || root.value; if (!target) return;
  fullscreenElement = target;
  if (target.requestFullscreen) {
    try { await target.requestFullscreen(); target.classList.add("knowledge-fullscreen"); fullscreen.value = true; return; }
    catch { /* Embedded browsers can deny native fullscreen; use the available app viewport. */ }
  }
  previousOverflow = document.body.style.overflow; document.body.style.overflow = "hidden";
  for (let branch: HTMLElement | null = target; branch?.parentElement; branch = branch.parentElement) {
    for (const sibling of branch.parentElement.children) if (sibling !== branch && sibling instanceof HTMLElement) {
      inertSiblings.push({ element: sibling, previous: sibling.inert }); sibling.inert = true;
    }
  }
  fallbackFullscreen = true; target.classList.add("knowledge-fullscreen"); fullscreen.value = true;
}
function escapeFullscreen(event: KeyboardEvent) {
  if (event.key === "Escape") { tooltip.value = undefined; if (fallbackFullscreen && !event.defaultPrevented) leaveFullscreen(); }
}
const catalog = shallowRef<WorldviewCatalog>(), snapshot = shallowRef<Neighborhood>();
const focus = ref(""), busy = ref(false), error = ref(""), treeQuery = ref("");
const selectedUrn = ref("");
const depth = ref(1), relations = ref([...defaultGraphRelations]);
const history = ref<string[]>([]), treeVisible = ref(true);
const relationChooser = ref<HTMLDetailsElement>();
function placeRelations() {
  const chooser = relationChooser.value;
  const menu = chooser?.querySelector<HTMLElement>("div");
  if (!chooser?.open || !menu) return;
  const left = chooser.getBoundingClientRect().left;
  const width = menu.getBoundingClientRect().width;
  menu.style.left = `${Math.max(16, Math.min(left, window.innerWidth - width - 16)) - left}px`;
  menu.style.maxHeight = `${Math.max(120, Math.min(600, window.innerHeight - menu.getBoundingClientRect().top - 16))}px`;
}
const selected = computed(() => snapshot.value?.concepts.find(c => c.urn === (selectedUrn.value || focus.value)));
const selectedRelationships = computed(() => (snapshot.value?.links || []).flatMap(link => {
  if (link.source !== selected.value?.urn && link.target !== selected.value?.urn) return [];
  const incoming = link.target === selected.value?.urn;
  const concept = snapshot.value?.concepts.find(c => c.urn === (incoming ? link.source : link.target));
  return concept ? [{ label: link.label, concept, incoming }] : [];
}));
const allDeclarations = computed(() => {
  const result: Declaration[] = [];
  function walk(nodes: Declaration[]) { for (const node of nodes) { result.push(node); walk(node.children); } }
  catalog.value?.ontologies.forEach(o => walk(o.children)); return result;
});
const filtered = computed(() => allDeclarations.value.filter(d => `${d.label} ${d.urn}`.toLowerCase().includes(treeQuery.value.toLowerCase())));
let viewer: ReturnType<typeof createViewer> | undefined, observer: ResizeObserver | undefined;
let controller: AbortController | undefined, catalogController: AbortController | undefined, generation = 0, disposed = false;
let cache = new WorldviewCache();
const treeSelection = computed(() => allDeclarations.value.find(declaration => declaration.urn === focus.value ||
  cache.concepts.get(declaration.urn)?.urn === focus.value)?.urn || focus.value);
async function initialize(invalidate = false) {
  if (invalidate) { cache = new WorldviewCache(); generation++; controller?.abort(); }
  catalogController?.abort(); const request = new AbortController(); catalogController = request;
  const timeout = setTimeout(() => request.abort(), 30000);
  error.value = ""; busy.value = true;
  try {
    // Source contracts also contribute graph layers when the tree is hidden.
    const data = props.catalog || await props.api.request<WorldviewCatalog>("api/v1/worldview/explorer", { signal: request.signal });
    if (disposed || request.signal.aborted) return;
    catalog.value = data; if (data) emit("loaded", data);
    focus.value = props.focus || focus.value || allDeclarations.value.find(d => d.type.includes("SUBJECT"))?.urn || allDeclarations.value[0]?.urn || "";
    await refresh();
  } catch (cause) { if (!disposed && catalogController === request) report(cause); }
  finally { clearTimeout(timeout); if (!disposed && catalogController === request) busy.value = false; }
}
function report(cause: unknown) {
  const failure = cause instanceof Error ? cause : new Error(String(cause));
  error.value = failure.name === "AbortError" ? "The worldview request timed out. Retry to load it again." : failure.message;
  emit("error", failure);
}
async function refresh() {
  tooltip.value = undefined;
  const ticket = ++generation; controller?.abort();
  const request = new AbortController(); controller = request;
  const timeout = setTimeout(() => request.abort(), 45000);
  busy.value = true; error.value = "";
  try {
    if (!focus.value || !viewer) return;
    const result = await loadNeighborhood(props.api, focus.value, depth.value, relations.value, request.signal, cache, catalog.value);
    if (disposed || ticket !== generation) return;
    // Source declarations can resolve to canonical aliases (e.g. imod:Subject -> odo:Subject).
    focus.value = result.concepts[0].urn;
    const chart: FlowChart = { root: { id: `${id}-root`, children: result.concepts.map(c => ({
      id: c.urn, labels: [{ text: conceptLabel(c) }, { text: c.urn }], width: 180, height: 64,
    })), edges: result.links.map((edge, index) => ({ id: `${id}-edge-${index}`, sources: [edge.source], targets: [edge.target], labels: [{ text: edge.label }] })) } };
    const laid = await layoutChart(chart, request.signal);
    if (disposed || ticket !== generation) return;
    const { model } = toSprotty(laid);
    // The viewport is the container; do not draw a redundant box around the worldview.
    model.children = model.children?.filter(n => n.id !== chart.root.id);
    for (const node of model.children || []) {
      const concept = result.concepts.find(c => c.urn === node.id);
      if (concept) Object.assign(node, { cssClasses: [`semantic-${semanticColor(concept.type)}`, ...(concept.urn === focus.value ? ["focus"] : [])] });
    }
    await viewer.source.setModel(model);
    if (disposed || ticket !== generation) return;
    snapshot.value = result; selectedUrn.value = focus.value; await viewer.fit();
    const concept = result.concepts.find(c => c.urn === focus.value);
    if (concept) emit("select", concept);
  } catch (cause) { if (!disposed && ticket === generation) report(cause); }
  finally { clearTimeout(timeout); request.abort(); if (!disposed && ticket === generation) busy.value = false; }
}
function center(urn: string, remember = true) {
  if (props.disabled || urn === focus.value) return;
  if (remember && focus.value) history.value.push(focus.value);
  focus.value = urn; void refresh();
}
function back() { const previous = history.value.pop(); if (previous) center(previous, false); }
watch(() => props.focus, urn => { if (urn) center(urn); });
watch(() => [props.catalog, props.api], () => { void initialize(true); });
watch(() => props.showTree, show => { if (show && !catalog.value) void initialize(); });
watch([depth, relations], () => { void refresh(); }, { deep: true });
watch([detailsOpen, treeVisible, fullscreen], async () => { await nextTick(); if (!disposed) void viewer?.fit(); });
onMounted(() => {
  window.addEventListener("resize", placeRelations);
  document.addEventListener("fullscreenchange", fullscreenChanged);
  window.addEventListener("keydown", escapeFullscreen);
  viewer = createViewer(id, urn => { if (snapshot.value?.concepts.some(c => c.urn === urn)) selectedUrn.value = urn; }, hoverConcept,
    urn => { if (snapshot.value?.concepts.some(c => c.urn === urn)) center(urn); });
  observer = new ResizeObserver(() => { if (!disposed && !busy.value) void viewer?.fit(); });
  // Sprotty replaces the canvas host during rendering. Observe our stable Vue-owned frame.
  if (root.value) observer.observe(root.value);
  void initialize();
});
onBeforeUnmount(() => {
  disposed = true; generation++; controller?.abort(); catalogController?.abort(); observer?.disconnect(); viewer?.dispose();
  window.removeEventListener("resize", placeRelations); window.removeEventListener("keydown", escapeFullscreen);
  document.removeEventListener("fullscreenchange", fullscreenChanged); clearTimeout(hoverTimer);
  if (fullscreenElement && document.fullscreenElement === fullscreenElement) void document.exitFullscreen().catch(() => {});
  leaveFullscreen();
});
defineExpose({ center, refresh, fit: () => viewer?.fit() });
</script>

<template>
  <section ref="root" class="worldview-browser knowledge-ui" aria-label="Worldview browser" :aria-busy="busy">
    <div class="worldview-toolbar">
      <div class="worldview-actions" role="group" aria-label="Graph actions">
      <button v-if="showTree" type="button" class="worldview-icon" aria-label="Ontologies" title="Show or hide ontologies" :disabled="disabled" :aria-expanded="treeVisible" @click="treeVisible = !treeVisible">
        <svg viewBox="0 0 24 24" aria-hidden="true"><rect x="3" y="4" width="18" height="16" rx="2" /><path d="M9 4v16M5.5 8h1M5.5 12h1M5.5 16h1" /></svg>
      </button>
      <button type="button" class="worldview-icon" aria-label="Back" title="Back" :disabled="!history.length || disabled" @click="back"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m11 5-7 7 7 7M4 12h16" /></svg></button>
      <button type="button" class="worldview-icon" aria-label="Home" title="Home" :disabled="!allDeclarations.length || disabled" @click="center(allDeclarations.find(d => d.type.includes('SUBJECT'))?.urn || allDeclarations[0].urn)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 10 9-7 9 7M5 9v12h5v-7h4v7h5V9" /></svg></button>
      <button type="button" class="worldview-icon" aria-label="Reload" title="Reload worldview" :disabled="busy || disabled" @click="initialize(true)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 7v5h-5M20 12a8 8 0 1 0-2 5M20 12l-3-4" /></svg></button>
      <button type="button" class="worldview-icon" aria-label="Fit graph" title="Fit graph" :disabled="busy || !snapshot || disabled" @click="viewer?.fit()"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M8 3H3v5m13-5h5v5m0 8v5h-5M3 16v5h5" /><rect x="8" y="8" width="8" height="8" rx="1" /></svg></button>
      <button type="button" class="worldview-icon" :disabled="!selected || disabled" :aria-label="detailsOpen ? 'Hide details' : 'Show details'" :title="detailsOpen ? 'Hide concept details' : 'Show concept details'" :aria-expanded="detailsOpen" :aria-controls="`${id}-details`" @click="detailsOpen = !detailsOpen">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 4h16v16H4zM4 14h16" /><path :d="detailsOpen ? 'm9 8 3 3 3-3' : 'm9 11 3-3 3 3'" /></svg>
      </button>
      <button type="button" class="worldview-icon" :disabled="disabled" :aria-label="fullscreen ? 'Exit full screen' : 'Enter full screen'" :title="fullscreen ? 'Exit full screen' : 'Enter full screen'" :aria-pressed="fullscreen" @click="toggleFullscreen">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path :d="fullscreen ? 'M3 8h5V3m8 0v5h5M21 16h-5v5M8 21v-5H3' : 'M8 3H3v5m13-5h5v5m0 8v5h-5M3 16v5h5'" /></svg>
      </button>
      </div>
      <label class="worldview-depth">Depth <select aria-label="Depth" v-model.number="depth" :disabled="disabled"><option v-for="n in 4" :key="n" :value="n">{{ n }}</option></select></label>
      <div class="worldview-options">
        <label v-if="snapshot?.concepts.length" class="worldview-dropdown worldview-focus"><span>Focus</span><select :value="focus" :disabled="busy || disabled" aria-label="Focus concept" @change="center(($event.target as HTMLSelectElement).value)">
          <option v-for="concept in snapshot.concepts" :key="concept.urn" :value="concept.urn">{{ conceptLabel(concept) }}</option>
        </select><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg></label>
        <details ref="relationChooser" class="worldview-relations" @toggle="placeRelations"><summary class="worldview-dropdown">Relations<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m7 10 5 5 5-5" /></svg></summary><div>

          <label v-for="relation in graphRelations" :key="relation.id" :title="'description' in relation ? relation.description : relation.label"><input v-model="relations" type="checkbox" :value="relation.id" :disabled="disabled" />{{ relation.label }}</label>
        </div></details>
      </div>
    </div>
    <div v-if="error" role="alert" class="knowledge-error">{{ error }} <button type="button" :disabled="disabled || busy" @click="initialize()">Retry</button></div>
    <div class="worldview-body" :class="{ 'without-tree': !showTree || !treeVisible }">
      <aside v-if="showTree && treeVisible" class="worldview-sidebar" aria-label="Worldview declarations">
        <div class="worldview-sidebar-header">
          <label class="worldview-search"><span>Filter declarations</span><input v-model="treeQuery" type="search" placeholder="Name or namespace" :disabled="disabled" /></label>
          <div class="worldview-tree-actions">
            <button type="button" aria-label="Collapse tree" title="Collapse tree" :disabled="disabled" @click="expandTree(false)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 10 6 6 6-6M6 4l6 6 6-6" transform="rotate(180 12 12)" /></svg></button>
            <button type="button" aria-label="Expand tree" title="Expand tree" :disabled="disabled" @click="expandTree(true)"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 10 6 6 6-6M6 4l6 6 6-6" /></svg></button>
          </div>
        </div>
        <div class="worldview-tree-scroll">
        <WorldviewTree v-if="treeQuery" :nodes="filtered.map(d => ({ ...d, children: [] }))" :selected="treeSelection" :disabled="disabled" :expanded="expanded" @select="center" @toggle="toggleBranch" />
        <details v-for="ontology in treeQuery ? [] : catalog?.ontologies || []" :key="ontology.urn" :open="expanded.has(ontology.urn)" @toggle="toggleBranch(ontology.urn, ($event.currentTarget as HTMLDetailsElement).open)" class="worldview-ontology">
          <summary><svg class="ontology-chevron" viewBox="0 0 16 16" aria-hidden="true"><path d="m6 3 5 5-5 5" /></svg>{{ ontology.urn }} <span>{{ ontology.children.length }}</span></summary>
          <WorldviewTree :nodes="ontology.children" :selected="treeSelection" :disabled="disabled" :expanded="expanded" @select="center" @toggle="toggleBranch" />
        </details>
        <p v-if="treeQuery && !filtered.length">No matching declarations.</p>
        </div>
      </aside>
      <div class="worldview-stage">
        <div v-if="busy" role="status" class="worldview-message">Loading concept neighborhood…</div>
        <div v-else-if="!focus" class="worldview-message">No worldview declarations are loaded.</div>
        <div :id="id" ref="host" class="worldview-canvas" tabindex="0" aria-label="Concept graph. Drag to pan; scroll to zoom." />
        <div :id="`${id}-hidden`" class="worldview-hidden" /><div :id="`${id}-popup`" />
        <div v-if="selected" class="worldview-inspector" :class="{ expanded: detailsOpen }">
          <div class="worldview-inspector-summary">
          <strong :style="{ color: `var(--semantic-${semanticColor(selected.type)})` }">{{ conceptLabel(selected) }}</strong>
          <code>{{ selected.urn }}</code><span>{{ selected.type?.join(' · ').toLowerCase() }}</span>
          </div>
          <ObservableCard v-if="detailsOpen" :id="`${id}-details`" :concept="selected" :relationships="selectedRelationships" @select="center" />
        </div>
      </div>
    </div>
    <footer class="worldview-footer"><span>{{ snapshot?.concepts.length || 0 }} concepts · {{ snapshot?.links.length || 0 }} relations<span v-if="snapshot?.truncated"> · Limited to 150 concepts; reduce depth or relations</span></span><span>Scroll to zoom · Drag to pan · Click to inspect · Double-click to recenter</span></footer>
    <div v-if="tooltip" class="worldview-tooltip" role="tooltip" :style="{ left: `${tooltip.x}px`, top: `${tooltip.y}px` }" @mouseenter="keepTooltip" @mouseleave="tooltip = undefined">
      <strong>{{ conceptLabel(tooltip.concept) }}</strong><div class="concept-documentation" v-html="documentationMarkdown(conceptDocumentation(tooltip.concept))" />
    </div>
  </section>
</template>

<style>
.worldview-browser { background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); min-width: 0; }
.worldview-toolbar { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; padding: 8px; border-bottom: 1px solid var(--klab-border); }
.worldview-actions { display: flex; gap: 4px; align-items: center; }
.worldview-options { display: flex; gap: 6px; align-items: center; margin-left: auto; min-width: 0; }
.worldview-dropdown { display: flex; align-items: center; gap: 8px; min-height: 32px; box-sizing: border-box; padding: 5px 8px; background: var(--klab-surface); border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius); cursor: pointer; }
.worldview-dropdown svg { width: 16px; height: 16px; flex: none; fill: none; stroke: currentColor; stroke-width: 1.7; pointer-events: none; }
.worldview-dropdown:hover { background: var(--klab-selection); }
.worldview-focus { position: relative; min-width: 0; }
.worldview-focus > span { color: var(--klab-muted); }
.worldview-toolbar .worldview-focus select { position: static; border: 0; padding: 0; min-height: 0; max-width: 180px; width: 100%; min-width: 0; appearance: none; background: transparent; cursor: pointer; }
.worldview-focus:focus-within { outline: 2px solid var(--klab-action); outline-offset: 2px; }
.worldview-focus select:focus-visible { outline: none; }
.worldview-relations { position: relative; }
.worldview-relations summary { list-style: none; }
.worldview-relations summary::-webkit-details-marker { display: none; }
.worldview-relations summary::marker { content: ''; }
.worldview-relations[open] > summary { background: var(--klab-selection); }
.worldview-relations[open] > summary svg { transform: rotate(180deg); }
.worldview-relations > div { position: absolute; top: 100%; left: 0; z-index: 12; width: min(320px, calc(100vw - 48px)); max-height: min(70vh, 600px); overflow-y: auto; padding: 8px; background: var(--klab-surface); border: 1px solid var(--klab-border); box-shadow: var(--klab-shadow); }
.worldview-relations label { display: flex; gap: 8px; padding: 5px; align-items: center; }
.worldview-toolbar > .worldview-depth { display: flex; align-items: center; gap: 6px; margin-left: 4px; color: var(--klab-muted); }
.worldview-depth select { color: var(--klab-ink); }
.worldview-body { display: grid; grid-template-columns: 245px minmax(0, 1fr); height: clamp(420px, 66vh, 900px); min-height: 0; }
.worldview-body.without-tree { grid-template-columns: minmax(0, 1fr); }
.worldview-sidebar { display: flex; flex-direction: column; min-height: 0; overflow: hidden; border-right: 1px solid var(--klab-border); }
.worldview-sidebar-header { display: grid; grid-template-columns: 1fr auto; gap: 8px; padding: 10px 12px; flex: none; border-bottom: 1px solid var(--klab-border); }
.worldview-search { display: contents; color: var(--klab-muted); }
.worldview-search > span { align-self: center; }
.worldview-search > input { grid-row: 2; grid-column: 1 / -1; width: 100%; }
.worldview-tree-actions { display: flex; gap: 4px; grid-row: 1; grid-column: 2; }
.worldview-tree-actions button, .worldview-icon { padding: 4px !important; width: 32px; flex: none; display: inline-grid; place-items: center; }
.worldview-tree-actions svg, .worldview-icon svg { width: 18px; height: 18px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; }
.worldview-icon[aria-expanded="true"] { background: var(--klab-selection); }
.worldview-tree-scroll { min-height: 0; flex: 1; overflow: auto; padding: 6px; }
.worldview-ontology > summary { display: flex; gap: 6px; align-items: center; list-style: none; cursor: pointer; padding: 8px; font-weight: 600; overflow-wrap: anywhere; }
.worldview-ontology > summary::-webkit-details-marker { display: none; }
.worldview-ontology > summary::marker { content: ''; }
.ontology-chevron { width: 16px; height: 16px; flex: 0 0 16px; color: var(--klab-muted); fill: none; stroke: currentColor; stroke-width: 2; }
.worldview-ontology[open] > summary > .ontology-chevron { transform: rotate(90deg); }
.worldview-ontology > summary span { color: var(--klab-muted); margin-left: auto; font-weight: 400; }
.worldview-stage { min-width: 0; min-height: 0; position: relative; display: flex; flex-direction: column; background: var(--klab-background); }
.worldview-canvas { flex: 1; min-height: 160px; overflow: hidden; }
.worldview-canvas > svg { display: block; width: 100%; height: 100%; }
.worldview-hidden { position: absolute; visibility: hidden; width: 0; height: 0; overflow: hidden; }
.worldview-canvas .sprotty-node { fill: var(--klab-surface); stroke: var(--klab-field-border); stroke-width: 1.3; rx: 4; }
.worldview-canvas .focus .sprotty-node, .worldview-canvas .sprotty-node.selected { stroke: var(--klab-action); stroke-width: 2.5; }
.worldview-canvas .sprotty-label { fill: var(--node-ink, var(--klab-ink)); stroke: none; font: 13px var(--klab-font); }
.worldview-canvas .semantic-QUALITY { --node-ink: var(--semantic-QUALITY); }
.worldview-canvas .semantic-SUBJECT { --node-ink: var(--semantic-SUBJECT); }
.worldview-canvas .semantic-PROCESS { --node-ink: var(--semantic-PROCESS); }
.worldview-canvas .semantic-TRAIT { --node-ink: var(--semantic-TRAIT); }
.worldview-canvas .semantic-ROLE { --node-ink: var(--semantic-ROLE); }
.worldview-canvas .semantic-RELATIONSHIP { --node-ink: var(--semantic-RELATIONSHIP); }
.worldview-canvas .semantic-EVENT { --node-ink: var(--semantic-EVENT); }
.worldview-canvas .semantic-EXTENT { --node-ink: var(--semantic-EXTENT); }
.worldview-canvas .semantic-CONFIGURATION { --node-ink: var(--semantic-CONFIGURATION); }
.worldview-canvas [class*="semantic-"] .sprotty-label:first-of-type { font-weight: 600; }
.worldview-canvas .sprotty-edge { stroke: var(--klab-muted); stroke-width: 1.2; }
.worldview-canvas .sprotty-edge .arrow { fill: var(--klab-muted); }
.worldview-canvas .sprotty-edge .sprotty-label { font-size: 11px; fill: var(--klab-muted); }
.worldview-inspector { flex: none; min-height: 0; border-top: 1px solid var(--klab-border); background: var(--klab-surface); overflow-wrap: anywhere; }
.worldview-inspector.expanded { display: flex; flex-direction: column; max-height: 60%; }
.worldview-inspector-summary { display: flex; gap: 8px 16px; align-items: baseline; flex-wrap: wrap; padding: 8px 12px; flex: none; }
.worldview-inspector-summary code, .worldview-inspector-summary span { color: var(--klab-muted); font-size: 12px; }
.worldview-inspector > .observable-card { min-height: 0; overflow: auto; border-top: 1px solid var(--klab-border); }
.worldview-tooltip { position: fixed; z-index: 100; width: min(360px, calc(100vw - 24px)); max-height: 240px; overflow: auto; padding: 12px; background: var(--klab-surface); color: var(--klab-ink); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); box-shadow: var(--klab-shadow); }
.knowledge-fullscreen { position: fixed !important; inset: 0 !important; z-index: 6000; width: 100% !important; max-width: none !important; height: 100dvh !important; margin: 0 !important; padding: 12px; background: var(--klab-background); display: flex !important; flex-direction: column; min-height: 0; }
.knowledge-fullscreen.worldview-browser, .knowledge-fullscreen > .worldview-browser { flex: 1; display: flex; flex-direction: column; min-height: 0; }
.knowledge-fullscreen .worldview-body { flex: 1; height: auto; min-height: 0; }
.knowledge-fullscreen .worldview-stage { height: auto; }
.knowledge-fullscreen .worldview-sidebar { flex-shrink: 0; }
.worldview-message { position: absolute; z-index: 1; top: 12px; left: 12px; padding: 8px 12px; background: var(--klab-surface); border: 1px solid var(--klab-border); pointer-events: none; }
.worldview-footer { display: flex; flex-wrap: wrap; gap: 6px 20px; justify-content: space-between; padding: 8px 12px; color: var(--klab-muted); border-top: 1px solid var(--klab-border); font-size: 12px; }
@media (max-width: 760px) { .worldview-body { grid-template-columns: 180px minmax(0, 1fr); } }
@media (max-width: 620px) { .worldview-options { justify-content: flex-end; width: 100%; } .worldview-focus { flex: 0 1 auto; max-width: calc(100% - 112px); } }
@media (pointer: coarse) { .worldview-icon { width: 36px; } .worldview-dropdown { min-height: 44px; } }
@media (max-width: 520px) { .worldview-body { display: flex; height: auto; min-height: 480px; flex-direction: column; } .worldview-sidebar { max-height: 210px; border-right: 0; border-bottom: 1px solid var(--klab-border); } .worldview-stage { height: 440px; } .worldview-toolbar { gap: 4px; } }
</style>
