<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from "vue";
import {layoutStyle, type UiNode, type WidgetInput} from "../services/application-ui";
const props = defineProps<{node: UiNode; disabled?: boolean; theme?: string}>();
const emit = defineEmits<{input: [event: WidgetInput]}>();
const p = computed(() => props.node.properties);
const motionPreference = window.matchMedia("(prefers-reduced-motion: reduce)");
const reducedMotion = ref(motionPreference.matches);
function motionChanged(): void { reducedMotion.value = motionPreference.matches; }
motionPreference.addEventListener("change", motionChanged);
onBeforeUnmount(() => motionPreference.removeEventListener("change", motionChanged));
const blocked = computed(() => props.disabled || Boolean(p.value.disabled) || Boolean(p.value.invisible) || Boolean(p.value.loading));
const transition = computed(() => reducedMotion.value ? 'none' : ({none: 'none', fade: 'fade', slide: 'slide-up', scale: 'scale'}[String(p.value.animation || 'scale')] || 'none'));
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
      :transition-show="transition" :transition-hide="transition" :transition-duration="p.duration ?? 300"
      @hide="input('close', null)">
      <q-card class="application-ui application-dialog" :data-theme="theme" :style="layoutStyle(p)">
        <q-card-section v-if="p.title" class="text-h6">{{ p.title }}</q-card-section>
        <q-card-section>
          <ApplicationNode v-for="child in node.children" :key="child.id" :node="child" :disabled="blocked" :theme="theme" @input="emit('input', $event)" />
        </q-card-section>
        <q-card-actions v-if="p.dismissible !== false" align="right"><q-btn flat label="Close" :disable="blocked" @click="input('close', null)" /></q-card-actions>
      </q-card>
    </q-dialog>
    <div v-else :data-node-id="node.id" :data-ui-tag="p.tag" :data-testid="p.testid" :class="['application-node', p.class]"
      :style="layoutStyle(p)" :role="p.role" :tabindex="p.tabindex" :aria-description="p.description" :aria-label="p.role ? p.label : undefined" :aria-disabled="blocked || undefined">
      <span v-if="node.type === 'text'">{{ p.label }}</span>
      <q-btn v-else-if="node.type === 'button'" no-caps :label="String(p.label ?? '')" :disable="blocked || p.readonly"
        :color="p.color || 'primary'" :icon="p.icon" :dense="p.dense" :flat="p.flat" :outline="p.outlined" :round="p.round" :loading="p.loading" :size="p.size"
        @click="input('click', null)" />
      <q-checkbox v-else-if="node.type === 'checkbox'" :model-value="Boolean(p.value)" :label="p.label" :disable="blocked || p.readonly" :dark="theme === 'dark'" :dense="p.dense" :color="p.color"
        @update:model-value="input('change', $event)" />
      <q-input v-else-if="node.type === 'input' || node.type === 'number'" v-model="draft"
        :type="node.type === 'number' ? 'number' : (p.type || 'text')" :label="p.label" :disable="blocked" :readonly="p.readonly"
        :min="p.min" :max="p.max" :step="p.step" :maxlength="p.maxlength ?? 16384" :debounce="p.debounce ?? 300" :dark="theme === 'dark'" :outlined="p.outlined !== false"
        :placeholder="p.placeholder" :hint="p.hint" :error="Boolean(p.error)" :error-message="p.error" :dense="p.dense" :color="p.color" :required="p.required"
        @update:model-value="input('change', node.type === 'number' ? Number($event) : String($event ?? ''))" />
      <q-select v-else-if="node.type === 'select'" :model-value="p.value" :options="p.options || []"
        :label="p.label" :disable="blocked" :readonly="p.readonly" :dark="theme === 'dark'" :outlined="p.outlined !== false" :dense="p.dense" :color="p.color"
        :hint="p.hint" :error="Boolean(p.error)" :error-message="p.error" @update:model-value="input('change', $event)" />
      <q-linear-progress v-else-if="node.type === 'progress'" :value="Number(p.value)" :indeterminate="p.indeterminate" :color="p.color" :aria-label="p.label || 'Progress'" />
      <template v-else>
        <h2 v-if="p.title">{{ p.title }}</h2>
        <ApplicationNode v-for="child in node.children" :key="child.id" :node="child" :disabled="blocked" :theme="theme" @input="emit('input', $event)" />
      </template>
      <q-tooltip v-if="p.tooltip">{{ p.tooltip }}</q-tooltip>
    </div>
  </template>
</template>
