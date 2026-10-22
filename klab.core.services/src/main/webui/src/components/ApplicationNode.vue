<script setup lang="ts">
import {computed, ref, watch} from "vue";
import {layoutStyle, type UiNode, type WidgetInput} from "../services/application-ui";
const props = defineProps<{node: UiNode; disabled?: boolean; theme?: string}>();
const emit = defineEmits<{input: [event: WidgetInput]}>();
const p = computed(() => props.node.properties);
const blocked = computed(() => props.disabled || Boolean(p.value.disabled) || Boolean(p.value.invisible));
const draft = ref<string | number>(p.value.value ?? "");
watch(() => p.value.value, value => { draft.value = value ?? ""; });
function input(name: string, value: unknown): void {
  if (!blocked.value && props.node.events.includes(name)) emit("input", {nodeId: props.node.id, name, value});
}
</script>

<template>
  <template v-if="!p.hidden">
    <q-dialog v-if="node.type === 'dialog' || node.type === 'modal'"
      :model-value="true" :persistent="blocked || node.type === 'modal' || p.dismissible === false"
      :no-esc-dismiss="p.closeonescape === false" :no-backdrop-dismiss="p.closeonoutside === false"
      @hide="input('close', null)">
      <q-card class="application-ui application-dialog" :data-theme="theme" :style="layoutStyle(p)">
        <q-card-section v-if="p.title" class="text-h6">{{ p.title }}</q-card-section>
        <q-card-section>
          <ApplicationNode v-for="child in node.children" :key="child.id" :node="child" :disabled="blocked" :theme="theme" @input="emit('input', $event)" />
        </q-card-section>
        <q-card-actions v-if="p.dismissible !== false" align="right"><q-btn flat label="Close" :disable="blocked" @click="input('close', null)" /></q-card-actions>
      </q-card>
    </q-dialog>
    <div v-else :data-node-id="node.id" :data-testid="p.testid" :class="['application-node', p.class]"
      :style="layoutStyle(p)" :role="p.role" :aria-label="p.role ? p.label : undefined" :aria-disabled="blocked || undefined">
      <span v-if="node.type === 'text'">{{ p.label }}</span>
      <q-btn v-else-if="node.type === 'button'" no-caps :label="String(p.label ?? '')" :disable="blocked"
        color="primary" @click="input('click', null)" />
      <q-checkbox v-else-if="node.type === 'checkbox'" :model-value="Boolean(p.value)" :label="p.label" :disable="blocked" :dark="theme === 'dark'"
        @update:model-value="input('change', $event)" />
      <q-input v-else-if="node.type === 'input' || node.type === 'number'" v-model="draft"
        :type="node.type === 'number' ? 'number' : 'text'" :label="p.label" :disable="blocked"
        :min="p.min" :max="p.max" :maxlength="16384" :debounce="300" :dark="theme === 'dark'" outlined
        @update:model-value="input('change', node.type === 'number' ? Number($event) : String($event ?? ''))" />
      <q-select v-else-if="node.type === 'select'" :model-value="p.value" :options="p.options || []"
        :label="p.label" :disable="blocked" :dark="theme === 'dark'" outlined @update:model-value="input('change', $event)" />
      <q-linear-progress v-else-if="node.type === 'progress'" :value="Number(p.value)" :aria-label="p.label || 'Progress'" />
      <template v-else>
        <h2 v-if="p.title">{{ p.title }}</h2>
        <ApplicationNode v-for="child in node.children" :key="child.id" :node="child" :disabled="blocked" :theme="theme" @input="emit('input', $event)" />
      </template>
      <q-tooltip v-if="p.tooltip">{{ p.tooltip }}</q-tooltip>
    </div>
  </template>
</template>
