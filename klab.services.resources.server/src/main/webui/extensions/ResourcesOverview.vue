<script setup lang="ts">
import { computed } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";

const props = defineProps<{ context: DashboardContext }>();
const transportGroups = computed(() =>
  Object.keys((props.context.capabilities?.importSchemata as Record<string, unknown>) || {}).length,
);
</script>

<template>
  <div class="stub-extension">
    <q-icon name="inventory_2" size="34px" color="secondary" />
    <div>
      <strong>{{ transportGroups }} resource transport groups advertised.</strong>
      <p>This service panel can grow into catalog browsing, resource validation, and import/export controls.</p>
    </div>
  </div>
</template>

<style scoped>
.stub-extension { display: flex; gap: 16px; align-items: start; }
strong { font-size: 15px; }
p { margin: 5px 0 0; color: #9ebdb4; font-size: 13px; line-height: 1.55; }
</style>
