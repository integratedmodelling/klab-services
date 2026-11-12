<script setup lang="ts">
import { computed } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";

const props = defineProps<{ context: DashboardContext }>();
const sessions = computed(() => Number(props.context.status?.knownSessionCount ?? 0));
</script>

<template>
  <q-card flat class="workspace-stub">
    <q-icon name="monitor_heart" size="52px" color="secondary" />
    <h2>Runtime session monitor extension point</h2>
    <p>{{ sessions }} sessions are currently known. Replace this stub with session and observation tooling.</p>
    <q-chip icon="verified_user" outline color="primary">{{ context.auth.username }}</q-chip>
  </q-card>
</template>

<style scoped>
.workspace-stub { min-height: 320px; display: grid; place-content: start; justify-items: start; padding: 32px; color: var(--klab-ink); text-align: left; background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); }
h2 { margin: 18px 0 8px; font-size: 21px; } p { max-width: 620px; margin: 0 0 18px; color: var(--klab-muted); }
</style>
