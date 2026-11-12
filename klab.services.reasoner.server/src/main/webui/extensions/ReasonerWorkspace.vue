<script setup lang="ts">
import { ref, shallowRef } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import SemanticComposer from "@klab-dashboard/components/SemanticComposer.vue";
import WorldviewBrowser from "@klab-dashboard/components/WorldviewBrowser.vue";
import type { SemanticResponse, WorldviewCatalog } from "@klab-dashboard/knowledge/model";

defineProps<{ context: DashboardContext }>();
const catalog = shallowRef<WorldviewCatalog>(), focus = ref("");
function explore(response: SemanticResponse) {
  const concept = response.observable?.semantics || response.currentConcept;
  if (concept?.urn) focus.value = concept.urn;
}
</script>

<template>
  <div class="reasoner-workspace">
    <SemanticComposer :api="context.api" :authorities="catalog?.authorities" @select="explore" @change="explore" />
    <WorldviewBrowser :api="context.api" :focus="focus" @loaded="catalog = $event" />
  </div>
</template>

<style scoped>
.reasoner-workspace { display: grid; gap: 12px; min-width: 0; }
</style>
