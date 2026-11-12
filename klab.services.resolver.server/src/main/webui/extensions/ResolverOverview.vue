<script setup lang="ts">
import type { DashboardContext } from "@klab-dashboard/types";

defineProps<{ context: DashboardContext }>();
</script>

<template>
  <div class="stub-extension">
    <q-icon name="account_tree" size="34px" color="secondary" />
    <div>
      <strong>Authenticated resolution workspace</strong>
      <p>The shared context supplies an authenticated API client, live status, capabilities, and a refresh function for future resolution-plan tools.</p>
      <q-chip dense outline color="primary" icon="verified_user">{{ context.auth.username }}</q-chip>
    </div>
  </div>
</template>

<style scoped>
.stub-extension { display: flex; gap: 16px; align-items: start; }
strong { font-size: 16px; }
p { margin: 5px 0 10px; color: var(--klab-muted); font-size: 16px; line-height: 1.55; }
</style>
