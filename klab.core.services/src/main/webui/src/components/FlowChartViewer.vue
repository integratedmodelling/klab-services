<script setup lang="ts">
import "../styles/theme.css";
import { onBeforeUnmount, onMounted, ref, shallowRef, watch } from "vue";
import { layoutChart, toSprotty, type FlowChart, type DiagramSelection } from "../flowchart/model";
import { createViewer } from "../flowchart/viewer";

const props = withDefaults(defineProps<{
  url: string;
  /** Set false when the URL already returns an ELK-layout chart. */
  layout?: boolean;
  /** Supply an authenticated loader when cookies are not sufficient. */
  load?: (url: string, signal: AbortSignal) => Promise<FlowChart>;
}>(), { layout: true });
const emit = defineEmits<{ select: [DiagramSelection]; loaded: [FlowChart]; error: [Error] }>();
const id = `flowchart-${crypto.randomUUID()}`;
const host = ref<HTMLElement>();
const loading = ref(false), error = ref("");
const selection = shallowRef<DiagramSelection>();
const choices = shallowRef<DiagramSelection[]>([]);
let elements = new Map<string, DiagramSelection>();
let viewer: ReturnType<typeof createViewer> | undefined;
let controller: AbortController | undefined;
let observer: ResizeObserver | undefined;
let revision = 0, frame = 0;

function select(elementId: string) {
  selection.value = elements.get(elementId);
  if (selection.value) emit("select", selection.value);
}
async function refresh() {
  const current = ++revision;
  controller?.abort(); controller = new AbortController();
  selection.value = undefined; choices.value = []; elements.clear(); error.value = "";
  if (!viewer) return;
  await viewer.source.setModel({ type: "graph", id: `${id}-empty`, children: [] });
  if (!props.url || current !== revision) { loading.value = false; return; }
  loading.value = true;
  try {
    let chart: FlowChart;
    if (props.load) chart = await props.load(props.url, controller.signal);
    else {
      const response = await fetch(props.url, { signal: controller.signal, credentials: "same-origin",
        headers: { Accept: "application/json" } });
      if (!response.ok) throw new Error(`Cannot load diagram (${response.status})`);
      chart = await response.json();
    }
    if (current !== revision) return;
    if (!chart.root?.id) throw new Error("The URL did not return a FlowChart");
    if (props.layout) chart = await layoutChart(chart, controller.signal);
    if (current !== revision) return;
    const result = toSprotty(chart);
    elements = result.elements; choices.value = [...elements.values()];
    await viewer.source.setModel(result.model);
    if (current !== revision) return;
    await viewer.fit(); emit("loaded", chart);
  } catch (cause) {
    if (current !== revision) return;
    const failure = cause instanceof Error ? cause : new Error(String(cause));
    if (failure.name !== "AbortError") { error.value = failure.message; emit("error", failure); }
  } finally { if (current === revision) loading.value = false; }
}
watch(() => [props.url, props.layout, props.load], refresh);
onMounted(() => {
  viewer = createViewer(id, select);
  observer = new ResizeObserver(() => {
    cancelAnimationFrame(frame);
    frame = requestAnimationFrame(() => { if (!loading.value) void viewer?.fit(); });
  });
  if (host.value) observer.observe(host.value);
  void refresh();
});
onBeforeUnmount(() => {
  revision++; controller?.abort(); observer?.disconnect(); cancelAnimationFrame(frame);
  viewer?.dispose(); viewer = undefined;
});
defineExpose({ refresh, fit: () => viewer?.fit() });
</script>

<template>
  <section class="flowchart-component" aria-label="Process diagram">
    <div class="flowchart-toolbar">
      <span>Scroll to zoom · Drag the canvas to pan · Select an element to inspect</span>
      <button type="button" :disabled="loading || !url" @click="viewer?.fit()">Fit diagram</button>
    </div>
    <div v-if="error" class="flowchart-error" role="alert">{{ error }} <button @click="refresh">Retry</button></div>
    <div v-if="loading" class="flowchart-message" role="status">Loading and arranging diagram…</div>
    <div v-if="!url" class="flowchart-message">Select a process to view its diagram.</div>
    <div :id="id" ref="host" class="flowchart-canvas" tabindex="0" aria-label="Interactive process diagram" />
    <div :id="`${id}-hidden`" class="flowchart-hidden" />
    <div :id="`${id}-popup`" />
    <div v-if="choices.length" class="flowchart-inspector">
      <label>Inspect element
        <select :value="selection?.id || ''" @change="select(($event.target as HTMLSelectElement).value)">
          <option value="">Select a node, port or link</option>
          <option v-for="item in choices" :key="item.id" :value="item.id">{{ item.kind }} — {{ item.id }}</option>
        </select>
      </label>
      <pre v-if="selection">{{ JSON.stringify(selection.metadata, null, 2) }}</pre>
    </div>
  </section>
</template>

<style>
.flowchart-component { color: var(--klab-ink); background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); overflow: hidden; }
.flowchart-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; font-size: 13px; background: var(--klab-surface-alt); }
.flowchart-component button, .flowchart-component select { color: var(--klab-ink); background: var(--klab-surface); border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius); padding: 6px 10px; }
.flowchart-canvas { height: 560px; min-height: 280px; position: relative; }
.flowchart-canvas > svg { width: 100%; height: 100%; display: block; }
.flowchart-hidden { position: absolute; visibility: hidden; width: 0; height: 0; overflow: hidden; }
.flowchart-canvas .sprotty-node { fill: var(--klab-surface-alt); stroke: var(--klab-field-border); stroke-width: 1.4; rx: 6; }
.flowchart-canvas .compound .sprotty-node { fill: var(--klab-surface-alt); }
.flowchart-canvas .sprotty-node.selected { stroke: var(--klab-action); stroke-width: 3; }
.flowchart-canvas .sprotty-port { fill: var(--klab-action); stroke: var(--klab-surface); }
.flowchart-canvas .output .sprotty-port { fill: var(--klab-warning); }
.flowchart-canvas .sprotty-label { stroke: none; fill: var(--klab-ink); font: 14px sans-serif; }
.flowchart-canvas .sprotty-edge { stroke: var(--klab-ink); stroke-width: 1.5; }
.flowchart-canvas .sprotty-edge.selected { stroke: var(--klab-action); stroke-width: 3; }
.flowchart-canvas .sprotty-edge .arrow { fill: var(--klab-ink); }
.flowchart-inspector { border-top: 1px solid var(--klab-border); padding: 12px 16px; }
.flowchart-inspector select { margin-left: 12px; max-width: 100%; }
.flowchart-inspector pre { white-space: pre-wrap; overflow-wrap: anywhere; max-height: 220px; overflow: auto; font-size: 13px; }
.flowchart-message, .flowchart-error { padding: 12px 16px; }
.flowchart-error { color: var(--klab-danger); background: var(--klab-danger-bg); }
@media (max-width: 700px) { .flowchart-toolbar { flex-wrap: wrap; } .flowchart-canvas { height: 400px; } }
</style>
