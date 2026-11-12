<script setup lang="ts">
import type { DashboardContext } from "@klab-dashboard/types";

defineProps<{ context: DashboardContext }>();
</script>

<template>
  <q-card flat class="workspace-stub">
    <q-icon name="inventory_2" size="52px" color="secondary" />
    <h2>Resource catalog</h2>
    <p>Catalog browsing tools are not registered in this interface.</p>
    <q-btn flat no-caps color="secondary" icon="refresh" label="Refresh service data" @click="context.refresh" />
  </q-card>
</template>

<style scoped>
.workspace-stub { min-height: 320px; display: grid; place-content: start; justify-items: start; padding: 32px; color: var(--klab-ink); text-align: left; background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); }
h2 { margin: 18px 0 8px; font-size: 21px; } p { max-width: 620px; margin: 0 0 18px; color: var(--klab-muted); }
</style>
