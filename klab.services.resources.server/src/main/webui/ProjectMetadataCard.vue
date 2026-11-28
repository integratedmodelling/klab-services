<script setup lang="ts">
import { computed, h, nextTick, ref, useId, watch } from "vue";
import MetadataCard from "@klab-dashboard/components/MetadataCard.vue";
import type { Metadata, MetadataCardOptions } from "@klab-dashboard/components/metadata-card";

const props = defineProps<{ editable: boolean }>();
const metadata = defineModel<Metadata>({ required: true });
const id = useId();
const dialog = ref<HTMLDialogElement>();
const keyInput = ref<HTMLInputElement>();
const field = ref<{ oldKey?: string; key: string; type: ValueType; text: string; readOnly: boolean }>();
const error = ref("");
type ValueType = "Text" | "Number" | "Boolean" | "Null" | "List" | "Object";
const types: ValueType[] = ["Text", "Number", "Boolean", "Null", "List", "Object"];

function typeOf(value: unknown): ValueType {
  if (value === null) return "Null";
  if (Array.isArray(value)) return "List";
  if (typeof value === "boolean") return "Boolean";
  if (typeof value === "number") return "Number";
  return typeof value === "object" ? "Object" : "Text";
}

async function openField(key?: string, readOnly = false) {
  if (!props.editable && !readOnly) return;
  const value = key === undefined ? "" : metadata.value[key];
  const type = typeOf(value);
  field.value = { oldKey: key, key: key ?? "", type, readOnly,
    text: type === "List" || type === "Object" ? JSON.stringify(value, null, 2) : String(value ?? "") };
  error.value = "";
  await nextTick();
  dialog.value?.showModal();
  if (!readOnly) keyInput.value?.focus();
}
watch(() => props.editable, editable => { if (!editable && !field.value?.readOnly) dialog.value?.close(); });

const options = computed<MetadataCardOptions>(() => ({
  title: "Project metadata",
  emptyTitle: "No project metadata.",
  pathTree: true,
  editHandler: props.editable ? (key, _oldValue, value) => {
    if (!props.editable) return false;
    metadata.value = { ...metadata.value, [key]: value };
    return true;
  } : undefined,
  complexValueRenderer: (key, value) => {
    const label = Array.isArray(value) ? `List · ${value.length} ${value.length === 1 ? "item" : "items"}`
      : `Object · ${Object.keys(value as object).length} fields`;
    return h("button", { type: "button", class: "project-metadata-view", "aria-label": `View value for ${key}`,
      onClick: () => void openField(key, true) }, label);
  },
}));

function changeType(event: Event) {
  if (!field.value) return;
  const type = (event.target as HTMLSelectElement).value as ValueType;
  field.value.type = type;
  field.value.text = ({ Text: "", Number: "0", Boolean: "false", Null: "", List: "[]", Object: "{}" })[type];
  error.value = "";
}

function saveField() {
  if (!props.editable || !field.value || field.value.readOnly) return;
  const { key, oldKey, type, text } = field.value;
  if (key !== oldKey && Object.hasOwn(metadata.value, key)) {
    error.value = "A field with this key already exists.";
    keyInput.value?.focus();
    return;
  }
  let value: unknown = text;
  if (type === "Null") value = null;
  else if (type !== "Text") {
    try {
      value = JSON.parse(text);
      if (typeOf(value) !== type || type === "Number" && !Number.isFinite(value)) throw new Error();
    } catch {
      error.value = ({
        Number: "Enter a finite number.", Boolean: "Choose true or false.",
        List: "Enter a valid JSON array.", Object: "Enter a valid JSON object.",
      })[type];
      return;
    }
  }
  const entries = Object.entries(metadata.value);
  if (oldKey === undefined) entries.push([key, value]);
  else {
    const index = entries.findIndex(([name]) => name === oldKey);
    if (index === -1) return;
    entries[index] = [key, value];
  }
  metadata.value = Object.fromEntries(entries);
  dialog.value?.close();
}

function removeField() {
  const key = field.value?.oldKey;
  if (!props.editable || key === undefined || field.value?.readOnly) return;
  metadata.value = Object.fromEntries(Object.entries(metadata.value).filter(([name]) => name !== key));
  dialog.value?.close();
}
</script>

<template>
  <div class="project-metadata">
    <MetadataCard :metadata="metadata" :options="options" :disabled="!editable">
      <template v-if="editable" #actions>
        <button type="button" class="project-metadata-add" @click="openField()">
          <q-icon name="add" size="17px" aria-hidden="true"/> Add field
        </button>
      </template>
      <template v-if="editable" #row-actions="{ entryKey, value }">
        <button type="button" class="project-metadata-manage" :aria-label="'Manage field ' + entryKey"
                :title="value !== null && typeof value === 'object' ? 'Edit or remove field' : 'Rename, change type, or remove field'"
                @click="openField(entryKey)">
          <q-icon name="more_horiz" size="18px" aria-hidden="true"/>
        </button>
      </template>
    </MetadataCard>
    <dialog ref="dialog" class="project-metadata-dialog" :aria-labelledby="id + '-title'" @keydown.esc.stop @close="field = undefined">
      <template v-if="field">
        <header>
          <h4 :id="id + '-title'">{{ field.readOnly ? field.key || "(empty)" : field.oldKey === undefined ? "Add metadata field" : "Edit metadata field" }}</h4>
          <button type="button" aria-label="Close metadata field" @click="dialog?.close()">
            <q-icon name="close" size="18px" aria-hidden="true"/>
          </button>
        </header>
        <pre v-if="field.readOnly" tabindex="0">{{ field.text }}</pre>
        <div v-else class="project-metadata-fields" @input="error = ''">
          <label>Key
            <input ref="keyInput" v-model="field.key" spellcheck="false" @keydown.enter.prevent="saveField"/>
          </label>
          <label>Value type
            <select :value="field.type" @change="changeType">
              <option v-for="type in types" :key="type">{{ type }}</option>
            </select>
          </label>
          <label v-if="field.type === 'Boolean'">Value
            <select v-model="field.text"><option>false</option><option>true</option></select>
          </label>
          <label v-else-if="field.type === 'Number'">Value
            <input v-model="field.text" inputmode="decimal" @keydown.enter.prevent="saveField"/>
          </label>
          <label v-else-if="field.type !== 'Null'">{{ field.type === "Text" ? "Value" : "Value (JSON)" }}
            <textarea v-model="field.text" :rows="field.type === 'Text' ? 3 : 6" spellcheck="false"/>
          </label>
          <p v-if="error" class="project-metadata-error" role="alert">{{ error }}</p>
          <footer>
            <button v-if="field.oldKey !== undefined" type="button" class="project-metadata-remove" @click="removeField">Remove field</button>
            <button type="button" @click="dialog?.close()">Cancel</button>
            <button type="button" class="project-metadata-apply" @click="saveField">{{ field.oldKey === undefined ? "Add field" : "Apply changes" }}</button>
          </footer>
        </div>
      </template>
    </dialog>
  </div>
</template>

<style scoped>
.project-metadata { --metadata-max-height: min(18rem, 45dvh); max-width: 68ch; margin-block: 16px; }
.project-metadata .project-metadata-add, .project-metadata .project-metadata-manage,
.project-metadata :deep(.project-metadata-view) { min-height: 32px; border-color: transparent; background: transparent; }
.project-metadata .project-metadata-manage { width: 32px; padding: 0 4px; color: var(--klab-muted); }
.project-metadata :deep(.project-metadata-view) {
  min-width: 0; display: block; padding-inline: 2px; overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap; color: var(--klab-action); text-decoration: underline; text-underline-offset: .16em;
}
.project-metadata :is(.project-metadata-add, .project-metadata-manage):hover,
.project-metadata :deep(.project-metadata-view:hover) { background: var(--klab-selection); }
.project-metadata-dialog {
  width: min(440px, calc(100vw - 32px)); max-height: calc(100dvh - 32px); padding: 0;
  color: var(--klab-ink); background: var(--klab-surface);
  border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius);
}
.project-metadata-dialog::backdrop { background: var(--klab-overlay); }
.project-metadata-dialog header {
  display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 8px 12px;
  background: var(--klab-surface-alt); border-bottom: 1px solid var(--klab-border);
}
.project-metadata-dialog h4 { margin: 0; font: 600 14px/1.4 var(--klab-font); overflow-wrap: anywhere; }
.project-metadata-dialog pre {
  margin: 0; padding: 12px; color: inherit; white-space: pre-wrap; overflow-wrap: anywhere; font: 13px/1.5 var(--klab-mono);
}
.project-metadata-fields { display: grid; gap: 10px; padding: 12px; }
.project-metadata-fields label { display: grid; gap: 4px; }
.project-metadata-fields textarea {
  min-width: 0; width: 100%; padding: 8px; resize: vertical; color: inherit; background: var(--klab-surface);
  border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius); font: 13px/1.5 var(--klab-mono);
}
.project-metadata-fields textarea:focus-visible { outline: 2px solid var(--klab-action); outline-offset: 2px; }
.project-metadata-fields footer { display: flex; flex-wrap: wrap; justify-content: end; gap: 6px; }
.project-metadata-fields .project-metadata-remove { margin-inline-end: auto; color: var(--klab-danger); }
.project-metadata-fields .project-metadata-apply {
  background: var(--klab-action); color: var(--klab-action-ink); border-color: var(--klab-action);
}
.project-metadata-fields .project-metadata-apply:hover { color: var(--klab-action); }
.project-metadata-error { color: var(--klab-danger); margin: 0; }
@media (pointer: coarse) {
  .project-metadata :is(.project-metadata-add, .project-metadata-manage),
  .project-metadata :deep(.project-metadata-view) { min-height: 44px; }
  .project-metadata .project-metadata-manage { min-width: 44px; }
}
</style>
