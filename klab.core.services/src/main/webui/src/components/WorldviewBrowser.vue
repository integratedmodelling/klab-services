<script setup lang="ts">
import "../styles/theme.css";
import "../knowledge/knowledge.css";
import { computed, onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import type { ServiceApi } from "../services/api";
import { conceptLabel, graphRelations, defaultGraphRelations, loadNeighborhood, semanticColor, WorldviewCache,
  type Concept, type Declaration, type Neighborhood, type WorldviewCatalog } from "../knowledge/model";
import { layoutChart, toSprotty, type FlowChart } from "../flowchart/model";
import { createViewer } from "../flowchart/viewer";
import WorldviewTree from "./WorldviewTree.vue";

const props = withDefaults(defineProps<{
  api: ServiceApi; focus?: string; catalog?: WorldviewCatalog; disabled?: boolean;
  /** Disable the tree and its toggle for graph-only embedding. */
  showTree?: boolean;
}>(), { showTree: true });
const emit = defineEmits<{ select: [Concept]; loaded: [WorldviewCatalog]; error: [Error] }>();
const id = `worldview-${crypto.randomUUID()}`, host = ref<HTMLElement>();
const catalog = shallowRef<WorldviewCatalog>(), snapshot = shallowRef<Neighborhood>();
const focus = ref(""), busy = ref(false), error = ref(""), treeQuery = ref("");
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
const selected = computed(() => snapshot.value?.concepts.find(c => c.urn === focus.value));
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
    snapshot.value = result; await viewer.fit();
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
onMounted(() => {
  window.addEventListener("resize", placeRelations);
  viewer = createViewer(id, urn => { if (snapshot.value?.concepts.some(c => c.urn === urn)) center(urn); });
  observer = new ResizeObserver(() => { if (!busy.value) void viewer?.fit(); });
  if (host.value) observer.observe(host.value);
  void initialize();
});
onBeforeUnmount(() => { disposed = true; generation++; controller?.abort(); catalogController?.abort(); observer?.disconnect(); viewer?.dispose(); window.removeEventListener("resize", placeRelations); });
defineExpose({ center, refresh, fit: () => viewer?.fit() });
</script>

<template>
  <section class="worldview-browser knowledge-ui" aria-label="Worldview browser" :aria-busy="busy">
    <div class="worldview-toolbar">
      <button v-if="showTree" type="button" :disabled="disabled" :aria-expanded="treeVisible" @click="treeVisible = !treeVisible">Ontologies</button>
      <button type="button" :disabled="!history.length || disabled" @click="back">Back</button>
      <button type="button" :disabled="!allDeclarations.length || disabled" @click="center(allDeclarations.find(d => d.type.includes('SUBJECT'))?.urn || allDeclarations[0].urn)">Home</button>
      <label>Depth <select v-model.number="depth" :disabled="disabled"><option v-for="n in 4" :key="n" :value="n">{{ n }}</option></select></label>
      <details ref="relationChooser" class="worldview-relations" @toggle="placeRelations"><summary>Relations</summary><div>
        <label v-for="relation in graphRelations" :key="relation.id" :title="'description' in relation ? relation.description : relation.label"><input v-model="relations" type="checkbox" :value="relation.id" :disabled="disabled" />{{ relation.label }}</label>
      </div></details>
      <button type="button" :disabled="busy || disabled" @click="initialize(true)">Reload</button>
      <button type="button" :disabled="busy || !snapshot || disabled" @click="viewer?.fit()">Fit graph</button>
      <label v-if="snapshot?.concepts.length">Focus <select :value="focus" :disabled="busy || disabled" aria-label="Focus concept" @change="center(($event.target as HTMLSelectElement).value)">
        <option v-for="concept in snapshot.concepts" :key="concept.urn" :value="concept.urn">{{ conceptLabel(concept) }}</option>
      </select></label>
    </div>
    <div v-if="error" role="alert" class="knowledge-error">{{ error }} <button type="button" :disabled="disabled || busy" @click="initialize()">Retry</button></div>
    <div class="worldview-body" :class="{ 'without-tree': !showTree || !treeVisible }">
      <aside v-if="showTree && treeVisible" class="worldview-sidebar" aria-label="Worldview declarations">
        <label class="worldview-search">Filter declarations<input v-model="treeQuery" type="search" placeholder="Name or namespace" :disabled="disabled" /></label>
        <WorldviewTree v-if="treeQuery" :nodes="filtered.map(d => ({ ...d, children: [] }))" :selected="treeSelection" :disabled="disabled" @select="center" />
        <details v-for="ontology in treeQuery ? [] : catalog?.ontologies || []" :key="ontology.urn" open class="worldview-ontology">
          <summary><svg class="ontology-chevron" viewBox="0 0 16 16" aria-hidden="true"><path d="m6 3 5 5-5 5" /></svg>{{ ontology.urn }} <span>{{ ontology.children.length }}</span></summary>
          <WorldviewTree :nodes="ontology.children" :selected="treeSelection" :disabled="disabled" @select="center" />
        </details>
        <p v-if="treeQuery && !filtered.length">No matching declarations.</p>
      </aside>
      <div class="worldview-stage">
        <div v-if="busy" role="status" class="worldview-message">Loading concept neighborhood…</div>
        <div v-else-if="!focus" class="worldview-message">No worldview declarations are loaded.</div>
        <div :id="id" ref="host" class="worldview-canvas" tabindex="0" aria-label="Concept graph. Drag to pan; scroll to zoom." />
        <div :id="`${id}-hidden`" class="worldview-hidden" /><div :id="`${id}-popup`" />
        <div v-if="selected" class="worldview-inspector">
          <strong :style="{ color: `var(--semantic-${semanticColor(selected.type)})` }">{{ conceptLabel(selected) }}</strong>
          <code>{{ selected.urn }}</code><span>{{ selected.type?.join(' · ').toLowerCase() }}</span>
          <p v-if="selected.metadata?.['dc:comment']">{{ selected.metadata['dc:comment'] }}</p>
        </div>
      </div>
    </div>
    <footer class="worldview-footer"><span>{{ snapshot?.concepts.length || 0 }} concepts · {{ snapshot?.links.length || 0 }} relations<span v-if="snapshot?.truncated"> · Limited to 150 concepts; reduce depth or relations</span></span><span>Scroll to zoom · Drag to pan · Select to recenter</span></footer>
  </section>
</template>

<style>
.worldview-browser { background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); min-width: 0; }
.worldview-toolbar { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; padding: 8px; border-bottom: 1px solid var(--klab-border); }
.worldview-toolbar > label { display: flex; gap: 6px; align-items: center; margin: 0 6px; }
.worldview-toolbar select { max-width: 180px; }
.worldview-relations { position: relative; }
.worldview-relations summary { cursor: pointer; padding: 8px; }
.worldview-relations > div { position: absolute; top: 100%; left: 0; z-index: 12; width: min(320px, calc(100vw - 48px)); max-height: min(70vh, 600px); overflow-y: auto; padding: 8px; background: var(--klab-surface); border: 1px solid var(--klab-border); box-shadow: var(--klab-shadow); }
.worldview-relations label { display: flex; gap: 8px; padding: 5px; align-items: center; }
.worldview-body { display: grid; grid-template-columns: 245px minmax(0, 1fr); height: clamp(420px, 66vh, 900px); min-height: 0; }
.worldview-body.without-tree { grid-template-columns: minmax(0, 1fr); }
.worldview-sidebar { overflow: auto; border-right: 1px solid var(--klab-border); padding: 12px 6px; }
.worldview-search { display: grid; gap: 6px; margin: 0 6px 14px; color: var(--klab-muted); }
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
.worldview-inspector { display: flex; gap: 8px 16px; align-items: baseline; flex-wrap: wrap; border-top: 1px solid var(--klab-border); padding: 10px 14px; background: var(--klab-surface); overflow-wrap: anywhere; }
.worldview-inspector code, .worldview-inspector span { color: var(--klab-muted); font-size: 12px; }
.worldview-inspector p { flex-basis: 100%; margin: 0; max-height: 80px; overflow: auto; }
.worldview-message { position: absolute; z-index: 1; top: 12px; left: 12px; padding: 8px 12px; background: var(--klab-surface); border: 1px solid var(--klab-border); pointer-events: none; }
.worldview-footer { display: flex; flex-wrap: wrap; gap: 6px 20px; justify-content: space-between; padding: 8px 12px; color: var(--klab-muted); border-top: 1px solid var(--klab-border); font-size: 12px; }
@media (max-width: 760px) { .worldview-body { grid-template-columns: 180px minmax(0, 1fr); } }
@media (max-width: 520px) { .worldview-body { display: flex; height: auto; min-height: 480px; flex-direction: column; } .worldview-sidebar { max-height: 210px; border-right: 0; border-bottom: 1px solid var(--klab-border); } .worldview-stage { height: 440px; } .worldview-toolbar { gap: 4px; } }
</style>
