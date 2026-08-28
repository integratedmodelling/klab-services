<script setup lang="ts">
import { computed } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";

const props = defineProps<{ context: DashboardContext }>();
const sessions = computed(() => Number(props.context.status?.knownSessionCount ?? 0));
</script>

<template>
  <div class="stub-extension">
    <q-icon name="memory" size="34px" color="secondary" />
    <div>
      <strong>{{ sessions }} known runtime sessions</strong>
      <p>Use this authenticated panel contract for digital-twin sessions, observations, jobs, and runtime instrumentation.</p>
      <q-btn dense flat no-caps color="secondary" icon="refresh" label="Refresh shared data" @click="context.refresh" />
    </div>
  </div>
</template>

<style scoped>
.stub-extension { display: flex; gap: 16px; align-items: start; }
strong { font-size: 15px; }
p { margin: 5px 0 8px; color: #9ebdb4; font-size: 13px; line-height: 1.55; }
</style>
