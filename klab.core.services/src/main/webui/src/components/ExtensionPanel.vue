<script setup lang="ts">
import { computed } from "vue";
import extensionComponents from "virtual:klab-dashboard-extensions";
import type { DashboardContext, DashboardPanel } from "../types";

const props = defineProps<{ panel: DashboardPanel; context: DashboardContext }>();
const implementation = computed(() => extensionComponents[props.panel.component]);
</script>

<template>
  <q-card flat class="surface-card extension-card">
    <q-card-section class="section-heading">
      <div>
        <div class="eyebrow">Service extension</div>
        <h2>{{ panel.title }}</h2>
        <p>{{ panel.description }}</p>
      </div>
    </q-card-section>
    <q-separator dark />
    <q-card-section v-if="panel.requiresAuthentication && !context.auth.authenticated" class="locked-panel">
      <q-icon name="lock" size="30px" />
      <div><strong>Sign in to use this panel</strong><span>Authentication adds your k.LAB network identity to service requests.</span></div>
    </q-card-section>
    <q-card-section v-else-if="implementation">
      <component :is="implementation" :context="context" />
    </q-card-section>
    <q-card-section v-else class="missing-panel">
      Component <code>{{ panel.component }}</code> was configured but was not included in this UI build.
    </q-card-section>
  </q-card>
</template>
