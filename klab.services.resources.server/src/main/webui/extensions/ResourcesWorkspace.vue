<script setup lang="ts">
import type { DashboardContext } from "@klab-dashboard/types";

defineProps<{ context: DashboardContext }>();
</script>

<template>
  <q-card flat class="workspace-stub">
    <q-icon name="inventory_2" size="52px" color="secondary" />
    <h2>Resource catalog extension point</h2>
    <p>Replace this full-page stub with catalog browsing, validation, and import/export tools.</p>
    <q-btn flat no-caps color="secondary" icon="refresh" label="Refresh service data" @click="context.refresh" />
  </q-card>
</template>

<style scoped>
.workspace-stub { min-height: 55vh; display: grid; place-content: center; justify-items: center; padding: 40px; color: #edf7f3; text-align: center; background: rgba(14, 53, 47, .78); border: 1px solid rgba(172, 235, 215, .1); border-radius: 16px; }
h2 { margin: 18px 0 8px; font-size: 28px; } p { max-width: 620px; margin: 0 0 18px; color: #9ebdb4; }
</style>
