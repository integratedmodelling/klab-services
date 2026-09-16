<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { QBtn } from "quasar";
import FlowChartViewer from "@klab-dashboard/components/FlowChartViewer.vue";
import type { FlowChart } from "@klab-dashboard/flowchart/model";
import type { DashboardContext } from "@klab-dashboard/types";
import { accessToken } from "@klab-dashboard/services/auth";

const props = defineProps<{ context: DashboardContext }>();
interface Workflow { id: string; name?: string; description?: string; version?: string }
const workflows = ref<Workflow[]>([]), selected = ref("");
const busy = ref(false), downloading = ref(false), error = ref("");
const workflow = computed(() => workflows.value.find(item => item.id === selected.value));
const url = computed(() => selected.value ? `/api/v1/workflows/${encodeURIComponent(selected.value)}/flowchart` : "");
async function load(url: string, signal: AbortSignal): Promise<FlowChart> {
  return props.context.api.request<FlowChart>(url, { signal });
}
async function refresh() {
  busy.value = true; error.value = "";
  try {
    workflows.value = await props.context.api.get<Workflow[]>("/api/v1/workflows");
    if (!workflows.value.some(item => item.id === selected.value)) selected.value = workflows.value[0]?.id || "";
  } catch (cause) { error.value = cause instanceof Error ? cause.message : String(cause); }
  finally { busy.value = false; }
}
async function download() {
  const endpoint = url.value;
  if (!endpoint) return;
  downloading.value = true; error.value = "";
  try {
    const token = await accessToken();
    const headers = new Headers({ Accept: "image/png" });
    if (token) headers.set("Authorization", `Bearer ${token}`);
    const response = await fetch(`${endpoint}.png`, { headers });
    if (!response.ok) throw new Error(`Cannot download PNG (${response.status})`);
    const link = document.createElement("a"), objectUrl = URL.createObjectURL(await response.blob());
    link.href = objectUrl; link.download = "workflow.png"; link.click();
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
  } catch (cause) { error.value = cause instanceof Error ? cause.message : String(cause); }
  finally { downloading.value = false; }
}
onMounted(refresh);
</script>

<template>
  <section class="workflow-browser">
    <header class="workflow-controls">
      <label>Workflow
        <select v-model="selected" :disabled="busy || !workflows.length">
          <option v-if="!workflows.length" value="">{{ busy ? 'Loading workflows…' : 'No workflows available' }}</option>
          <option v-for="item in workflows" :key="item.id" :value="item.id">{{ item.name || item.id }}{{ item.version ? ` · ${item.version}` : '' }}</option>
        </select>
      </label>
      <q-btn outline no-caps label="Refresh" :loading="busy" @click="refresh" />
      <q-btn color="primary" no-caps label="Download PNG" :disable="!selected" :loading="downloading" @click="download" />
    </header>
    <p v-if="workflow?.description">{{ workflow.description }}</p>
    <p v-if="error" role="alert" class="workflow-error">{{ error }}</p>
    <FlowChartViewer :url="url" :layout="false" :load="load" />
  </section>
</template>

<style scoped>
.workflow-controls { display: flex; gap: 14px; align-items: end; flex-wrap: wrap; margin-bottom: 18px; }
.workflow-controls label { display: grid; gap: 6px; flex: 1; min-width: 220px; }
.workflow-controls select { border: 1px solid #70999d; background: #fff; color: #1d3f49; border-radius: 6px; padding: 10px; }
.workflow-error { color: #edaaaa; }
</style>
