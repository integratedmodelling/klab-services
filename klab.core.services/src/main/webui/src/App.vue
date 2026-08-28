<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import CapabilitiesPanel from "./components/CapabilitiesPanel.vue";
import ExtensionPanel from "./components/ExtensionPanel.vue";
import LoginControl from "./components/LoginControl.vue";
import StatusGrid from "./components/StatusGrid.vue";
import { authState, initializeAuthentication } from "./services/auth";
import { serviceApi } from "./services/api";
import type { DashboardConfiguration, DashboardContext } from "./types";

const config = ref<DashboardConfiguration | null>(null);
const status = ref<Record<string, unknown> | null>(null);
const capabilities = ref<Record<string, unknown> | null>(null);
const loading = ref(true);
const refreshing = ref(false);
const error = ref("");
let refreshTimer: number | undefined;

const context = reactive({
  api: serviceApi,
  auth: authState,
  get config() { return config.value!; },
  get status() { return status.value; },
  get capabilities() { return capabilities.value; },
  refresh,
}) as DashboardContext;

const advisories = computed(() =>
  Array.isArray(status.value?.advisories) ? status.value.advisories : [],
);

onMounted(async () => {
  try {
    config.value = await serviceApi.get<DashboardConfiguration>("public/ui/config");
    document.title = config.value.title;
    await initializeAuthentication(config.value.authentication);
    await refresh();
    refreshTimer = window.setInterval(refresh, 10_000);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "The dashboard could not be loaded";
  } finally {
    loading.value = false;
  }
});

onBeforeUnmount(() => window.clearInterval(refreshTimer));

async function refresh(): Promise<void> {
  refreshing.value = true;
  try {
    const [newStatus, newCapabilities] = await Promise.all([
      serviceApi.get("public/status"),
      serviceApi.get("public/capabilities"),
    ]);
    status.value = newStatus;
    capabilities.value = newCapabilities;
    error.value = "";
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "Service data is unavailable";
  } finally {
    refreshing.value = false;
  }
}
</script>

<template>
  <q-layout view="hHh lpR fFf" class="dashboard-shell">
    <q-header class="dashboard-header">
      <q-toolbar class="header-inner">
        <img v-if="config" :src="config.logoUrl" alt="k.LAB logo" class="brand-logo" />
        <div class="brand-copy">
          <div class="brand-kicker">INTEGRATED MODELLING</div>
          <q-toolbar-title>{{ config?.title || "k.LAB service" }}</q-toolbar-title>
        </div>
        <q-space />
        <nav v-if="config?.links.length" class="header-links" aria-label="Service links">
          <a
            v-for="link in config.links"
            :key="link.href"
            :href="link.href"
            :target="link.external ? '_blank' : undefined"
            :rel="link.external ? 'noreferrer' : undefined"
          >{{ link.label }}</a>
        </nav>
        <LoginControl :auth="authState" />
      </q-toolbar>
    </q-header>

    <q-page-container>
      <q-page class="dashboard-page">
        <main class="dashboard-main">
          <section class="intro-row">
            <div>
              <div class="service-type">{{ config?.serviceType || "SERVICE" }}</div>
              <h1>{{ config?.subtitle || "Service overview" }}</h1>
              <p>Live operational telemetry and the functions advertised by this endpoint.</p>
            </div>
            <q-btn
              flat
              round
              color="secondary"
              icon="refresh"
              aria-label="Refresh dashboard"
              :loading="refreshing"
              @click="refresh"
            />
          </section>

          <q-banner v-if="error" rounded class="error-banner" inline-actions>
            <template #avatar><q-icon name="cloud_off" /></template>
            {{ error }}
            <template #action><q-btn flat label="Retry" @click="refresh" /></template>
          </q-banner>

          <StatusGrid :status="status" :loading="loading" />

          <q-banner v-for="(advisory, index) in advisories" :key="index" rounded class="advisory-banner">
            <template #avatar><q-icon name="info" /></template>
            {{ (advisory as Record<string, unknown>).message || advisory }}
          </q-banner>

          <section class="dashboard-content">
            <CapabilitiesPanel :capabilities="capabilities" :loading="loading" />
            <ExtensionPanel
              v-for="panel in config?.panels || []"
              :key="panel.id"
              :panel="panel"
              :context="context"
            />
          </section>
        </main>
        <footer class="dashboard-footer">
          <span>k.LAB service interface</span>
          <span>Public telemetry · authenticated operations</span>
        </footer>
      </q-page>
    </q-page-container>
  </q-layout>
</template>
