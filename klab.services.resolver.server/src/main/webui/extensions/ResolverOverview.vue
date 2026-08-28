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
      <q-chip dense color="teal-9" text-color="teal-1" icon="verified_user">{{ context.auth.username }}</q-chip>
    </div>
  </div>
</template>

<style scoped>
.stub-extension { display: flex; gap: 16px; align-items: start; }
strong { font-size: 15px; }
p { margin: 5px 0 10px; color: #9ebdb4; font-size: 13px; line-height: 1.55; }
</style>
