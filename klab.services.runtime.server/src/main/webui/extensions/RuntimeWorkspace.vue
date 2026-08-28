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
    <q-chip icon="verified_user" color="teal-9" text-color="teal-1">{{ context.auth.username }}</q-chip>
  </q-card>
</template>

<style scoped>
.workspace-stub { min-height: 55vh; display: grid; place-content: center; justify-items: center; padding: 40px; color: #edf7f3; text-align: center; background: rgba(14, 53, 47, .78); border: 1px solid rgba(172, 235, 215, .1); border-radius: 16px; }
h2 { margin: 18px 0 8px; font-size: 28px; } p { max-width: 620px; margin: 0 0 18px; color: #9ebdb4; }
</style>
