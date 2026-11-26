<script setup lang="ts">
import { computed, ref } from "vue";
import { record, recordId } from "./project-review";
import { fieldLabel, initialField, resolveField, type ProposalEditorConfig, type ProposalField } from "./proposal-schema";

const props = defineProps<{
  modelValue: unknown;
  label: string;
  config: ProposalEditorConfig;
  field?: ProposalField;
  statement?: string;
  locked?: boolean;
  canLocate?: boolean;
}>();
const emit = defineEmits<{ "update:modelValue": [unknown]; locate: [string] }>();
const shape = computed(() => resolveField(props.config, props.field, props.modelValue));
const object = computed(() => record(props.modelValue) ? props.modelValue : undefined);
const items = computed(() => Array.isArray(props.modelValue) ? props.modelValue : undefined);
const variants = computed(() => shape.value.items?.oneOf ?? []);
const variant = ref(0);
const optional = ref("");
const available = computed(() => Object.keys(shape.value.properties ?? {}).filter(key =>
  object.value && !Object.hasOwn(object.value, key)));
const nullable = computed(() => Array.isArray(shape.value.type) && shape.value.type.includes("null"));
const editable = computed(() => !props.locked && shape.value.const === undefined);
const identity = computed(() => recordId(object.value)?.split(":", 1)[0]);
const qualifiedName = computed(() => typeof object.value?.qualified_name === "string" ? object.value.qualified_name : undefined);
function changeItem(index: number, value: unknown) {
  if (!items.value) return;
  emit("update:modelValue", items.value.map((item, i) => i === index ? value : item));
}
function addItem() {
  if (!items.value) return;
  const field = variants.value[variant.value] ?? shape.value.items;
  emit("update:modelValue", [...items.value, initialField(props.config, field, props.statement)]);
}
function variantLabel(field: ProposalField, index: number) {
  const type = resolveField(props.config, field);
  const kind = type.properties?.asset_type?.const;
  return type.title ?? (typeof kind === "string" ? fieldLabel(kind) : `Type ${index + 1}`);
}
function addOptional() {
  if (!optional.value || !object.value) return;
  emit("update:modelValue", { ...object.value, [optional.value]: initialField(props.config, shape.value.properties?.[optional.value]) });
  optional.value = "";
}
function removeProperty(key: string) {
  emit("update:modelValue", Object.fromEntries(Object.entries(object.value ?? {}).filter(([name]) => name !== key)));
}
function changeNumber(event: Event) {
  const number = (event.target as HTMLInputElement).valueAsNumber;
  emit("update:modelValue", Number.isFinite(number) ? number : null);
}
</script>

<template>
  <div class="proposal-field">
    <div v-if="object" class="proposal-field-group">
      <button v-if="qualifiedName && canLocate" type="button" class="proposal-locate" @click="emit('locate', qualifiedName)">Show in editor</button>
      <div v-for="(value, key) in object" :key="key" class="proposal-field-row">
        <details v-if="record(value) || Array.isArray(value)">
          <summary>{{ fieldLabel(key) }} <span v-if="Array.isArray(value)">({{ value.length }})</span></summary>
          <ProposalFieldEditor :model-value="value" :label="fieldLabel(key)" :config="config"
                               :field="shape.properties?.[key]" :statement="statement" :locked="!editable" :can-locate="canLocate"
                               @update:model-value="emit('update:modelValue', { ...object, [key]: $event })" @locate="emit('locate', $event)"/>
        </details>
        <ProposalFieldEditor v-else :model-value="value" :label="fieldLabel(key)" :config="config"
                             :field="shape.properties?.[key]" :locked="!editable || key === identity"
                             @update:model-value="emit('update:modelValue', { ...object, [key]: $event })" @locate="emit('locate', $event)"/>
        <button v-if="editable && shape.properties?.[key] && !shape.required?.includes(key)" type="button"
                :aria-label="'Remove ' + fieldLabel(key)" class="proposal-remove" @click="removeProperty(key)">
          <q-icon name="close" size="16px"/>
        </button>
      </div>
      <div v-if="editable && available.length" class="proposal-field-actions">
        <select v-model="optional" :aria-label="'Optional field for ' + label">
          <option value="">Add an optional field</option>
          <option v-for="key in available" :key="key" :value="key">{{ fieldLabel(key) }}</option>
        </select>
        <button type="button" :disabled="!optional" @click="addOptional">Add field</button>
      </div>
    </div>
    <div v-else-if="items" class="proposal-field-group">
      <p v-if="!items.length" class="proposal-muted">No entries.</p>
      <div v-for="(item, index) in items" :key="recordId(item) ?? index" class="proposal-field-row">
        <details v-if="record(item) || Array.isArray(item)">
          <summary>{{ record(item) ? item.label || item.qualified_name || recordId(item) || 'Entry ' + (index + 1) : 'Entry ' + (index + 1) }}</summary>
          <ProposalFieldEditor :model-value="item" :label="'Entry ' + (index + 1)" :config="config"
                               :field="shape.items" :statement="statement" :locked="!editable" :can-locate="canLocate"
                               @update:model-value="changeItem(index, $event)" @locate="emit('locate', $event)"/>
        </details>
        <ProposalFieldEditor v-else :model-value="item" :label="'Entry ' + (index + 1)" :config="config"
                             :field="shape.items" :locked="!editable" @update:model-value="changeItem(index, $event)"/>
        <button v-if="editable" type="button" :aria-label="'Remove entry ' + (index + 1)"
                class="proposal-remove" @click="emit('update:modelValue', items.filter((_, i) => i !== index))">
          <q-icon name="close" size="16px"/>
        </button>
      </div>
      <div v-if="editable" class="proposal-field-actions">
        <select v-if="variants.length > 1" v-model="variant" :aria-label="label + ' entry type'">
          <option v-for="(choice, index) in variants" :key="index" :value="index">{{ variantLabel(choice, index) }}</option>
        </select>
        <button type="button" @click="addItem">{{ label === "Assets" ? "Add asset" : "Add entry" }}</button>
      </div>
    </div>
    <div v-else class="proposal-field-actions">
      <span v-if="!editable">{{ label }} · {{ modelValue ?? "No value" }}</span>
      <template v-else>
        <label v-if="shape.enum">
          {{ label }}
          <select :value="shape.enum.indexOf(modelValue)" @change="emit('update:modelValue', shape.enum?.[Number(($event.target as HTMLSelectElement).value)])">
            <option v-for="(choice, index) in shape.enum" :key="index" :value="index">{{ choice ?? "No value" }}</option>
          </select>
        </label>
        <label v-else-if="typeof modelValue === 'boolean' || shape.type === 'boolean'
          || Array.isArray(shape.type) && shape.type.includes('boolean')" class="proposal-checkbox">
          <input type="checkbox" :checked="modelValue === true" @change="emit('update:modelValue', ($event.target as HTMLInputElement).checked)"/>
          {{ label }}
        </label>
        <label v-else-if="typeof modelValue === 'number' || shape.type === 'integer' || shape.type === 'number'
          || Array.isArray(shape.type) && shape.type.some(type => type === 'integer' || type === 'number')">
          {{ label }}
          <input type="number" :value="modelValue as number" :min="shape.minimum"
                 :step="shape.type === 'integer' || Array.isArray(shape.type) && shape.type.includes('integer') ? 1 : 'any'"
                 @change="changeNumber"/>
        </label>
        <label v-else>
          {{ label }}
          <textarea :value="String(modelValue ?? '')" rows="2" @change="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"/>
        </label>
        <button v-if="nullable && modelValue !== null" type="button" @click="emit('update:modelValue', null)">Clear</button>
      </template>
    </div>
  </div>
</template>

<style scoped>
.proposal-field { min-width: 0; }
.proposal-field-group { display: grid; gap: 7px; padding-block: 6px; }
.proposal-field-row { display: grid; align-items: start; grid-template-columns: minmax(0, 1fr) auto; gap: 4px; }
.proposal-field-group details { min-width: 0; padding-inline-start: 7px; border-inline-start: 1px solid var(--klab-border); }
.proposal-field-group summary { cursor: pointer; overflow-wrap: anywhere; }
.proposal-field-group summary span, .proposal-muted { color: var(--klab-muted); }
.proposal-field-actions { display: flex; align-items: end; flex-wrap: wrap; gap: 5px; }
.proposal-field-actions label { display: grid; gap: 4px; flex: 1 1 125px; min-width: 0; }
.proposal-field-actions .proposal-checkbox { display: flex; flex-direction: row; align-items: center; }
.proposal-field-actions textarea { width: 100%; padding: 6px; resize: vertical; border-radius: var(--klab-radius);
  border: 1px solid var(--klab-field-border); color: var(--klab-ink); background: var(--klab-surface); font: inherit; }
.proposal-field .proposal-remove { padding: 3px; min-width: 30px; }
.proposal-locate { justify-self: start; }
.proposal-muted { margin: 0; }
</style>
