<script setup lang="ts">
import { computed, nextTick, ref, useId, useTemplateRef, watch, type VNode } from "vue";
import "../styles/theme.css";
import type { Metadata, MetadataCardOptions } from "./metadata-card";

const props = defineProps<{
  metadata?: Metadata | null;
  options?: MetadataCardOptions;
  extended?: boolean;
  disabled?: boolean;
}>();
const id = useId();
const pathTree = ref(false);
const collapsed = ref(new Set<string>());
const accepted = ref(new Map<string, { source: unknown; value: unknown }>());
const editing = ref<{ entry: Entry; text: string; checked: boolean; error: string }>();
const editControl = useTemplateRef<(HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement)[]>("editControl");
let editTrigger: HTMLElement | undefined;
const viewer = ref<HTMLDialogElement>();
const viewed = ref<Entry>();
const inlineLimit = computed(() => Math.max(16, props.options?.inlineStringLimit ?? 64));
const popupSize = computed(() => {
  const [width, height] = props.options?.stringPopupSize ?? [360, 220];
  return { "--popup-width": Math.max(220, width) + "px", "--popup-height": Math.max(120, height) + "px" };
});

interface Entry {
  key: string;
  value: unknown;
  kind: "string" | "number" | "boolean" | "null" | "complex";
  text: string;
  rendered?: VNode | null;
  choices?: readonly string[];
}
interface Branch {
  label: string;
  path: string;
  children: Map<string, Branch>;
  entry?: Entry;
}
type Row = Omit<Branch, "children"> & { depth: number; childCount: number };

function stringify(value: unknown) {
  if (value && typeof value === "object") {
    try { return JSON.stringify(value) ?? String(value); }
    catch { return Object.prototype.toString.call(value); }
  }
  return String(value);
}
const entries = computed<Entry[]>(() => {
  const result: Entry[] = [];
  for (const [key, source] of Object.entries(props.metadata ?? {})) {
    const edit = accepted.value.get(key);
    const value = edit && Object.is(edit.source, source) ? edit.value : source;
    const type = typeof value;
    const kind = value === null ? "null" : type === "string" || type === "number" || type === "boolean" ? type : "complex";
    const rendered = kind === "complex" ? props.options?.complexValueRenderer?.(key, value) : undefined;
    if (kind === "complex" && !rendered && props.options?.unsupportedValuePolicy !== "SHOW_AS_STRING") continue;
    const choices = kind === "string" ? props.options?.enumValues?.[key] : undefined;
    result.push({ key, value, kind, rendered, text: rendered ? "" : stringify(value),
      choices: Array.isArray(choices) && choices.length ? choices : undefined });
  }
  return result;
});
const tree = computed(() => {
  const root = new Map<string, Branch>();
  if (!pathTree.value) return root;
  for (const entry of entries.value) {
    let children = root;
    const parts = entry.key.split(".");
    let path = "";
    for (const [index, part] of parts.entries()) {
      path += (index ? "." : "") + part;
      let branch = children.get(part);
      if (!branch) {
        branch = { label: part.trim() ? part : "(empty)", path, children: new Map() };
        children.set(part, branch);
      }
      if (index === parts.length - 1) branch.entry = entry;
      children = branch.children;
    }
  }
  return root;
});
const collapsible = computed(() => {
  const paths: string[] = [];
  function collect(branches: Map<string, Branch>) {
    for (const branch of branches.values()) {
      if (branch.children.size) paths.push(branch.path);
      collect(branch.children);
    }
  }
  collect(tree.value);
  return paths;
});
const allCollapsed = computed(() => collapsible.value.length > 0 && collapsible.value.every(key => collapsed.value.has(key)));
const rows = computed<Row[]>(() => {
  if (!pathTree.value) return entries.value.map(entry => ({
    label: entry.key || "(empty)", path: entry.key, entry, childCount: 0, depth: 0,
  }));
  const result: Row[] = [];
  function append(branches: Map<string, Branch>, depth: number) {
    const ordered = [...branches.values()].sort((a, b) => a.label < b.label ? -1 : Number(a.label > b.label));
    for (const branch of ordered) {
      const { children, ...row } = branch;
      result.push({ ...row, childCount: children.size, depth });
      if (!collapsed.value.has(branch.path)) append(children, depth + 1);
    }
  }
  append(tree.value, 0);
  return result;
});

function setPathTree(enabled: boolean) {
  finishEdit(false);
  collapsed.value.clear();
  pathTree.value = enabled;
}
watch(() => props.options?.pathTree, value => setPathTree(value ?? false), { immediate: true });
watch(() => props.metadata, () => {
  accepted.value.clear();
  finishEdit();
  viewer.value?.close();
});
watch(() => props.disabled, disabled => { if (disabled) finishEdit(); });

function toggleBranch(key: string) {
  if (!collapsed.value.delete(key)) collapsed.value.add(key);
  if (editing.value && !rows.value.some(row => row.entry?.key === editing.value?.entry.key)) finishEdit(false);
}
function toggleAll() {
  finishEdit(false);
  collapsed.value = new Set(allCollapsed.value ? [] : collapsible.value);
}
function isLong(entry: Entry) {
  return entry.kind === "string" && (entry.text.length > inlineLimit.value || entry.text.includes("\n"));
}
function preview(entry: Entry) {
  const line = entry.text.split(/\r?\n/, 1)[0];
  return isLong(entry) ? line.slice(0, inlineLimit.value - 1) + "…" : entry.text;
}
const RenderValue = ({ node }: { node: VNode }) => node;

async function showString(entry: Entry) {
  viewed.value = entry;
  await nextTick();
  viewer.value?.showModal();
}
async function startEdit(entry: Entry, event: MouseEvent) {
  if (props.disabled || !props.options?.editHandler || entry.kind === "complex") return;
  editTrigger = event.currentTarget as HTMLElement;
  editing.value = { entry, text: entry.kind === "null" ? "" : entry.text, checked: entry.value === true, error: "" };
  await nextTick();
  // The control and its trigger both live in the repeated rows.
  const control = editControl.value?.[0];
  control?.focus();
  if ((control instanceof HTMLInputElement && control.type === "text") || control instanceof HTMLTextAreaElement) control.select();
}
function finishEdit(restoreFocus = true) {
  if (!editing.value) return;
  editing.value = undefined;
  const trigger = editTrigger;
  editTrigger = undefined;
  if (restoreFocus) void nextTick(() => trigger?.focus());
}
function confirmEdit() {
  const edit = editing.value;
  if (!edit || props.disabled) return;
  const { entry, text, checked } = edit;
  let value: unknown = text;
  if (entry.kind === "boolean") value = checked;
  else if (entry.kind === "number") {
    const input = text.trim();
    value = Number(input);
    if (!/^[+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:e[+-]?\d+)?$/i.test(input) || !Number.isFinite(value)) {
      edit.error = "Enter a finite number.";
      return;
    }
  }
  if (!Object.is(entry.value, value)) {
    const source = props.metadata?.[entry.key];
    if (!props.options?.editHandler?.(entry.key, entry.value, value)) {
      edit.error = "This change was declined.";
      return;
    }
    accepted.value.set(entry.key, { source, value });
  }
  finishEdit();
}
</script>

<template>
  <section class="metadata-card" :class="{ 'metadata-extended': extended }" :aria-labelledby="id + '-title'">
    <header class="metadata-header">
      <h4 :id="id + '-title'">{{ options?.title ?? "Parameters" }}</h4>
      <div class="metadata-actions">
        <slot name="actions"/>
        <button v-if="pathTree" type="button" class="metadata-icon"
                :aria-label="allCollapsed ? 'Expand all paths' : 'Collapse all paths'"
                :title="allCollapsed ? 'Expand all paths' : 'Collapse all paths'"
                :disabled="!collapsible.length" @click="toggleAll">
          <q-icon :name="allCollapsed ? 'unfold_more' : 'unfold_less'" size="18px" aria-hidden="true"/>
        </button>
        <button type="button" class="metadata-icon" :aria-label="pathTree ? 'Switch to flat view' : 'Switch to tree view'"
                :title="pathTree ? 'Switch to flat view' : 'Switch to tree view'" @click="setPathTree(!pathTree)">
          <q-icon :name="pathTree ? 'view_list' : 'account_tree'" size="18px" aria-hidden="true"/>
        </button>
      </div>
    </header>
    <p v-if="!rows.length" class="metadata-empty">{{ options?.emptyTitle ?? "Nothing to show" }}</p>
    <dl v-else class="metadata-rows" :class="{ 'metadata-tree': pathTree }">
      <div v-for="row in rows" :key="row.path" class="metadata-row">
        <dt class="metadata-key" :class="{ 'metadata-leaf': !row.childCount }"
            :style="{ '--indent': Math.min(row.depth, 6) * 9 + 'px' }">
          <button v-if="row.childCount" type="button" class="metadata-icon metadata-branch"
                  :aria-expanded="!collapsed.has(row.path)" :aria-label="(collapsed.has(row.path) ? 'Expand ' : 'Collapse ') + (row.path || '(empty)')"
                  @click="toggleBranch(row.path)">
            <q-icon :name="collapsed.has(row.path) ? 'chevron_right' : 'expand_more'" size="18px" aria-hidden="true"/>
          </button>
          <span :title="row.path">{{ row.label }}</span>
        </dt>
        <dd v-if="row.entry" class="metadata-value">
          <div v-if="editing && editing.entry.key === row.entry.key" class="metadata-editor"
               @keydown.esc.stop.prevent="finishEdit()">
            <select v-if="row.entry.choices" ref="editControl" v-model="editing.text"
                    :aria-label="'Value for ' + row.entry.key" :aria-describedby="editing.error ? id + '-error' : undefined">
              <option v-if="!row.entry.choices.includes(row.entry.text)" :value="row.entry.text">{{ row.entry.text }}</option>
              <option v-for="choice in row.entry.choices" :key="choice" :value="choice">{{ choice }}</option>
            </select>
            <label v-else-if="row.entry.kind === 'boolean'" class="metadata-boolean-control">
              <input ref="editControl" v-model="editing.checked" type="checkbox" role="switch"
                     :aria-label="'Value for ' + row.entry.key" @keydown.enter.prevent="confirmEdit"/>
              {{ editing.checked }}
            </label>
            <textarea v-else-if="row.entry.text.includes('\n')" ref="editControl" v-model="editing.text" rows="3"
                      :aria-label="'Value for ' + row.entry.key" @keydown.enter.exact.prevent="confirmEdit"/>
            <input v-else ref="editControl" v-model="editing.text" type="text"
                   :inputmode="row.entry.kind === 'number' ? 'decimal' : undefined"
                   :aria-label="'Value for ' + row.entry.key" :aria-invalid="editing.error ? true : undefined"
                   :aria-describedby="editing.error ? id + '-error' : undefined" @keydown.enter.prevent="confirmEdit"/>
            <button type="button" class="metadata-icon" title="Apply value" aria-label="Apply value" @click="confirmEdit">
              <q-icon name="check" size="18px" aria-hidden="true"/>
            </button>
            <button type="button" class="metadata-icon" title="Cancel edit" aria-label="Cancel edit" @click="finishEdit()">
              <q-icon name="close" size="18px" aria-hidden="true"/>
            </button>
            <p v-if="editing.error" :id="id + '-error'" class="metadata-error" role="alert">{{ editing.error }}</p>
          </div>
          <template v-else>
            <RenderValue v-if="row.entry.rendered" :node="row.entry.rendered"/>
            <button v-else-if="isLong(row.entry)" type="button" class="metadata-preview metadata-truncate"
                    :aria-label="'View full value for ' + row.entry.key" @click="showString(row.entry)">
              {{ preview(row.entry) }}
            </button>
            <span v-else class="metadata-text" :class="'metadata-' + row.entry.kind"
                  :title="row.entry.kind === 'complex' ? 'Unsupported value: ' + row.entry.text : row.entry.text">
              {{ row.entry.text === "" ? '""' : row.entry.text }}
            </span>
            <div v-if="$slots['row-actions'] || options?.editHandler && row.entry.kind !== 'complex'" class="metadata-row-actions">
              <button v-if="options?.editHandler && row.entry.kind !== 'complex'" type="button" class="metadata-icon"
                      :disabled="disabled" :aria-label="'Edit value for ' + row.entry.key" title="Edit value"
                      @click="startEdit(row.entry, $event)">
                <q-icon name="edit" size="16px" aria-hidden="true"/>
              </button>
              <slot name="row-actions" :entry-key="row.entry.key" :value="row.entry.value"/>
            </div>
          </template>
        </dd>
        <dd v-else class="metadata-group">{{ row.childCount }} nested</dd>
      </div>
    </dl>
    <dialog ref="viewer" class="metadata-viewer" :style="popupSize" :aria-labelledby="id + '-viewer-title'"
            @keydown.esc.stop @close="viewed = undefined">
      <header class="metadata-header">
        <h4 :id="id + '-viewer-title'">{{ viewed?.key || "(empty)" }}</h4>
        <button type="button" class="metadata-icon" aria-label="Close full value" title="Close" autofocus @click="viewer?.close()">
          <q-icon name="close" size="18px" aria-hidden="true"/>
        </button>
      </header>
      <pre tabindex="0">{{ viewed?.text }}</pre>
    </dialog>
  </section>
</template>

<style scoped>
.metadata-card { min-width: 0; border: 1px solid var(--klab-border); border-radius: var(--klab-radius);
  background: var(--klab-surface); color: var(--klab-ink); font: 14px/1.45 var(--klab-font); }
.metadata-header, .metadata-actions, .metadata-key, .metadata-value {
  display: flex; align-items: center; gap: 6px; min-width: 0;
}
.metadata-header { justify-content: space-between; flex-wrap: wrap; padding: 6px 10px;
  background: var(--klab-surface-alt); border-bottom: 1px solid var(--klab-border); }
.metadata-header h4 { margin: 0; font: 600 14px/1.4 var(--klab-font); overflow-wrap: anywhere; }
.metadata-rows { margin: 0; max-height: var(--metadata-max-height, 176px);
  overflow: auto; scrollbar-color: var(--klab-field-border) var(--klab-surface); }
.metadata-extended .metadata-rows { max-height: none; }
.metadata-row { display: grid; grid-template-columns: minmax(0, min(38%, 150px)) minmax(0, 1fr);
  align-items: center; gap: 8px; padding: 3px 10px; min-height: 36px; }
.metadata-tree .metadata-row { grid-template-columns: minmax(0, min(43%, 182px)) minmax(0, 1fr); }
.metadata-row + .metadata-row { border-top: 1px solid var(--klab-border); }
.metadata-key { padding-inline-start: min(var(--indent), 24%); font-weight: 600; }
.metadata-tree .metadata-leaf::before { content: ""; flex: 0 0 24px; }
.metadata-key span { overflow-wrap: anywhere; }
.metadata-value, .metadata-group { margin: 0; }
.metadata-truncate { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: pre; }
.metadata-text { min-width: 0; overflow-wrap: anywhere; white-space: pre-wrap; }
.metadata-row-actions { display: flex; align-items: center; gap: 4px; flex: none; margin-inline-start: auto; }
.metadata-group, .metadata-null, .metadata-empty { color: var(--klab-muted); }
.metadata-empty { margin: 0; padding: 12px 10px; }
.metadata-number { color: var(--klab-action); font-variant-numeric: tabular-nums; }
.metadata-boolean { color: var(--klab-success); }
.metadata-complex, .metadata-error { color: var(--klab-danger); }
.metadata-editor { display: flex; align-items: start; flex-wrap: wrap; gap: 4px; width: 100%; }
.metadata-editor :is(input[type="text"], select, textarea) {
  flex: 1 1 80px; min-width: 0; width: 100%; color: inherit; background: var(--klab-surface);
  border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius); padding: 4px 6px;
  font: inherit; min-height: 32px; caret-color: var(--klab-action);
}
.metadata-editor textarea { resize: vertical; }
.metadata-boolean-control { display: inline-flex; gap: 6px; align-items: center; min-height: 32px; }
.metadata-boolean-control input { accent-color: var(--klab-action); }
.metadata-error { margin: 0; flex-basis: 100%; }
.metadata-card .metadata-icon, .metadata-card .metadata-preview {
  min-height: 32px; border: 1px solid transparent; border-radius: var(--klab-radius);
  padding: 0 4px; color: var(--klab-muted); background: transparent; cursor: pointer; font: inherit;
}
.metadata-card .metadata-icon { display: inline-grid; place-items: center; flex: none; min-width: 32px; }
.metadata-card .metadata-branch { min-width: 24px; padding-inline: 0; }
.metadata-card .metadata-preview { display: block; text-align: start; padding: 0 2px;
  color: var(--klab-action); text-decoration: underline; text-underline-offset: .16em; }
.metadata-card :is(.metadata-icon, .metadata-preview):hover:not(:disabled) {
  background: var(--klab-selection); color: var(--klab-ink);
}
.metadata-card .metadata-icon:disabled { opacity: .5; cursor: default; }
.metadata-card :is(button, input, select, textarea, pre):focus-visible { outline: 2px solid var(--klab-action); outline-offset: -2px; }
.metadata-card ::selection { color: var(--klab-ink); background: var(--klab-selection); }
.metadata-viewer { width: min(var(--popup-width), calc(100vw - 32px)); padding: 0; color: inherit;
  background: var(--klab-surface); border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius); }
.metadata-viewer::backdrop { background: var(--klab-overlay); }
.metadata-viewer pre { margin: 0; padding: 12px; max-height: min(var(--popup-height), 65dvh);
  overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; }
@media (pointer: coarse) {
  .metadata-card :is(.metadata-icon, .metadata-preview) { min-height: 44px; }
  .metadata-card .metadata-icon:not(.metadata-branch) { min-width: 44px; }
}
</style>
