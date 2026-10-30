<script setup lang="ts">
import {computed, inject, ref, watch} from "vue";
import type {UiNode, WidgetInput} from "../services/application-ui";
import {applicationFilesKey, markdown, safeSource} from "../services/application-widgets";
const props = defineProps<{node: UiNode; disabled?: boolean; theme?: string}>();
const emit = defineEmits<{input: [event: WidgetInput]}>();
const p = computed(() => props.node.properties);
const type = computed(() => props.node.type);
const blocked = computed(() => Boolean(props.disabled || p.value.readonly || p.value.loading));
const draft = ref<any>(p.value.value ?? "");
watch(() => p.value.value, value => { draft.value = value ?? ""; });
const files = inject(applicationFilesKey, undefined);
const transferError = ref("");
const busy = ref(false);
const mediaElement = ref<HTMLMediaElement>();
watch(blocked, value => { if (value) mediaElement.value?.pause(); });
const options = computed<string[]>(() => p.value.options || []);
const columns = computed(() => (p.value.columns || []).map((field: string) => ({name: field, label: field, field, sortable: true, align: "left" as const})));
const pagination = computed(() => ({page: p.value.page || 1, rowsPerPage: p.value.pagesize || 10,
  sortBy: p.value.sort || null, descending: Boolean(p.value.descending)}));
function input(name: string, value: unknown): void {
  if (!blocked.value && props.node.events.includes(name)) emit("input", {nodeId: props.node.id, name, value});
}
function tablePage(value: {page: number; sortBy: string | null; descending: boolean}): void {
  if (value.sortBy && (value.sortBy !== p.value.sort || value.descending !== Boolean(p.value.descending)))
    input("sort", {column: value.sortBy, descending: value.descending});
  if (value.page !== (p.value.page || 1)) input("page", value.page);
}
async function upload(file: File | null): Promise<void> {
  if (!file || blocked.value || !files) return;
  transferError.value = "";
  if (file.size > (p.value.maxbytes || 4194304)) { transferError.value = "File exceeds the upload limit"; return; }
  busy.value = true;
  try { input("change", await files.upload(props.node.id, file)); }
  catch (error) { transferError.value = String(error); }
  finally { busy.value = false; }
}
async function download(): Promise<void> {
  if (blocked.value || !files || !p.value.value?.id) return;
  try { await files.download(p.value.value.id); input("click", null); }
  catch (error) { transferError.value = String(error); }
}
function playback(name: string, event: Event): void {
  const media = event.target as HTMLMediaElement;
  if (blocked.value) { if (!media.paused) media.pause(); return; }
  input(name, media.currentTime);
}
</script>

<template>
  <q-btn v-if="['iconbutton', 'link', 'submit', 'reset', 'download'].includes(type)" no-caps
    :label="type === 'iconbutton' ? undefined : p.label" :aria-label="p.label" :icon="p.icon"
    :flat="type === 'link' || p.flat" :round="type === 'iconbutton' || p.round" :outline="p.outlined"
    :color="p.color || 'primary'" :size="p.size" :dense="p.dense" :disable="blocked || (type === 'download' && !p.value?.id)" :loading="p.loading"
    :href="type === 'link' && !blocked ? safeSource(p.href) : undefined" :target="type === 'link' && p.href ? '_blank' : undefined" rel="noopener noreferrer"
    @click="type === 'download' ? download() : input(type === 'submit' ? 'submit' : type === 'reset' ? 'reset' : 'click', null)" />
  <q-toggle v-else-if="type === 'switch'" :model-value="Boolean(p.value)" :label="p.label" :disable="blocked" :dense="p.dense" :color="p.color" :dark="theme === 'dark'" @update:model-value="input('change', $event)" />
  <div v-else-if="type === 'radio'" role="radiogroup" :aria-label="p.label">
    <q-radio v-for="option in options" :key="option" :val="option" :label="option" :model-value="p.value" :disable="blocked" :dense="p.dense" :color="p.color" :dark="theme === 'dark'" @update:model-value="input('change', $event)" />
  </div>
  <q-select v-else-if="type === 'multiselect'" multiple :model-value="p.value || []" :options="options" :label="p.label"
    :disable="blocked" :dense="p.dense" :color="p.color" :dark="theme === 'dark'" :outlined="p.outlined !== false"
    :hint="p.hint" :error="Boolean(p.error)" :error-message="p.error" @update:model-value="input('change', $event)" />
  <q-input v-else-if="['textarea', 'date', 'time'].includes(type)" v-model="draft" :type="type as 'textarea' | 'date' | 'time'"
    :label="p.label" :disable="blocked" :dense="p.dense" :color="p.color" :dark="theme === 'dark'" :outlined="p.outlined !== false"
    :placeholder="p.placeholder" :maxlength="p.maxlength ?? 16384" :debounce="p.debounce ?? 300"
    :hint="p.hint" :error="Boolean(p.error)" :error-message="p.error" :required="p.required"
    @update:model-value="input('change', String($event ?? ''))" />
  <div v-else-if="type === 'color'">
    <label>{{ p.label }}</label><q-color :model-value="p.value || '#000000'" format-model="hex" no-header no-footer
      :disable="blocked" :dark="theme === 'dark'" :aria-label="p.label" @change="input('change', $event)" />
  </div>
  <div v-else-if="type === 'slider' || type === 'range'">
    <label>{{ p.label }}</label>
    <q-slider v-if="type === 'slider'" :model-value="p.value" :min="p.min ?? 0" :max="p.max ?? 100" :step="p.step ?? 1" label
      :aria-label="p.label" :disable="blocked" :color="p.color" @change="input('change', $event)" />
    <q-range v-else :model-value="p.value" :min="p.min ?? 0" :max="p.max ?? 100" :step="p.step ?? 1" label
      :aria-label="p.label" :disable="blocked" :color="p.color" @change="input('change', $event)" />
  </div>
  <q-list v-else-if="type === 'list'" :aria-label="p.label" bordered>
    <q-item v-for="item in p.items" :key="item.id" clickable :disable="blocked" :active="p.value === item.id"
      @click="input('select', item.id)" @dblclick="input('activate', item.id)" @keydown.enter.prevent="input('activate', item.id)">
      <q-item-section>{{ item.label }}</q-item-section>
    </q-item>
  </q-list>
  <q-table v-else-if="type === 'table'" :inert="blocked || undefined" :title="p.label" :rows="p.items" :columns="columns" row-key="id"
    :pagination="pagination" :rows-per-page-options="[p.pagesize || 10]" :dark="theme === 'dark'" :dense="p.dense"
    :selected="p.items.filter((row: any) => row.id === p.value)" selection="single"
    @update:selected="!blocked && input('select', $event[0]?.id || '')" @update:pagination="tablePage"
    @keydown.enter.prevent="p.value && input('activate', p.value)"
    @row-click="(_event: Event, row: any) => input('select', row.id)" @row-dblclick="(_event: Event, row: any) => input('activate', row.id)" />
  <q-tree v-else-if="type === 'tree'" :inert="blocked || undefined" :nodes="p.items" node-key="id" :selected="p.value ?? ''" :expanded="p.expanded || []"
    :no-nodes-label="p.label" :dark="theme === 'dark'" :class="{'disabled': blocked}" :aria-label="p.label"
    @update:selected="input('select', $event ?? '')" @update:expanded="input('expand', $event)">
    <template #default-header="slot">
      <span tabindex="0" role="button" :aria-disabled="blocked"
        @keydown.enter.stop.prevent="input('activate', slot.node.id)" @dblclick.stop="input('activate', slot.node.id)">{{ slot.node.label }}</span>
    </template>
  </q-tree>
  <q-tabs v-else-if="type === 'tabs'" :model-value="p.value" :aria-label="p.label" @update:model-value="input('change', $event)">
    <q-tab v-for="option in options" :key="option" :name="option" :label="option" :disable="blocked" />
  </q-tabs>
  <q-stepper v-else-if="type === 'stepper'" :model-value="p.value" :dark="theme === 'dark'" :header-nav="!blocked"
    :aria-label="p.label" @update:model-value="input('change', $event)">
    <q-step v-for="(option, index) in options" :key="option" :name="option" :title="option" :prefix="index + 1" :disable="blocked" />
  </q-stepper>
  <q-pagination v-else-if="type === 'pagination'" :model-value="p.value" :min="p.min ?? 1" :max="p.max" :disable="blocked" :color="p.color"
    :aria-label="p.label" @update:model-value="input('change', $event)" />
  <q-breadcrumbs v-else-if="type === 'breadcrumbs'" :aria-label="p.label">
    <q-breadcrumbs-el v-for="option in options" :key="option" :label="option" tabindex="0" role="button" :aria-current="option === p.value ? 'page' : undefined"
      :aria-disabled="blocked" @click="input('change', option)" @keydown.enter.prevent="input('change', option)" @keydown.space.prevent="input('change', option)" />
  </q-breadcrumbs>
  <div v-else-if="type === 'markdown'" v-html="markdown(String(p.value ?? p.label ?? ''))" />
  <q-img v-else-if="type === 'image'" :src="safeSource(p.src)" :alt="p.alt || p.label" />
  <q-icon v-else-if="type === 'icon'" :name="p.icon || p.label" :size="p.size" :color="p.color" role="img" :aria-label="p.label" />
  <q-separator v-else-if="type === 'separator'" :dark="theme === 'dark'" />
  <q-badge v-else-if="type === 'badge'" :color="p.color" :label="p.value ?? p.label" />
  <q-spinner v-else-if="type === 'spinner'" :color="p.color" :size="p.size" role="status" :aria-label="p.label" />
  <q-file v-else-if="type === 'upload'" :model-value="null" :label="p.label" :accept="p.accept" :max-file-size="p.maxbytes || 4194304"
    :disable="blocked || busy || !files" :loading="busy" :dark="theme === 'dark'" :outlined="p.outlined !== false" :hint="p.value?.name || p.hint"
    @update:model-value="upload($event as File | null)" />
  <component :is="type" ref="mediaElement" v-else-if="type === 'audio' || type === 'video'" :src="safeSource(p.src)" :controls="p.controls !== false && !blocked"
    :loop="p.loop" :muted="p.muted" preload="metadata" :aria-label="p.label" @play="playback('play', $event)" @pause="playback('pause', $event)" @ended="playback('ended', $event)" />
  <span v-if="transferError" role="alert">{{ transferError }}</span>
</template>
