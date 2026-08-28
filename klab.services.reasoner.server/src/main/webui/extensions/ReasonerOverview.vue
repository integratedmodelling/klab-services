<script setup lang="ts">
import { computed } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";

const props = defineProps<{ context: DashboardContext }>();
const componentCount = computed(() =>
  Array.isArray(props.context.capabilities?.components)
    ? props.context.capabilities.components.length
    : 0,
);
</script>

<template>
  <div class="stub-extension">
    <q-icon name="psychology_alt" size="34px" color="secondary" />
    <div>
      <strong>Semantic reasoning is ready for specialized tools.</strong>
      <p>{{ componentCount }} installed components are currently advertised. Replace this stub with namespace, ontology, and concept inspection workflows.</p>
    </div>
  </div>
</template>

<style scoped>
.stub-extension { display: flex; gap: 16px; align-items: start; }
strong { font-size: 15px; }
p { margin: 5px 0 0; color: #9ebdb4; font-size: 13px; line-height: 1.55; }
</style>
