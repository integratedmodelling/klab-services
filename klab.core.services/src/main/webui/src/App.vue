<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import CapabilitiesPanel from "./components/CapabilitiesPanel.vue";
import ExtensionPanel from "./components/ExtensionPanel.vue";
import FullPageExtension from "./components/FullPageExtension.vue";
import LoginControl from "./components/LoginControl.vue";
import StatusGrid from "./components/StatusGrid.vue";
import { authState, initializeAuthentication } from "./services/auth";
import { serviceApi } from "./services/api";
import type { DashboardConfiguration, DashboardContext, FullPageComponent } from "./types";

const config = ref<DashboardConfiguration | null>(null);
const status = ref<Record<string, unknown> | null>(null);
const capabilities = ref<Record<string, unknown> | null>(null);
const loading = ref(true);
const refreshing = ref(false);
const error = ref("");
let refreshTimer: number | undefined;
const pageName = ref(pageNameFromLocation());

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
const selectedPage = computed<FullPageComponent | undefined>(() =>
  config.value?.pages.find((page) => page.name === pageName.value),
);
const isFullPageRoute = computed(() => pageName.value !== null);

onMounted(async () => {
  window.addEventListener("popstate", updateRoute);
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

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer);
  window.removeEventListener("popstate", updateRoute);
});

function pageNameFromLocation(): string | null {
  const basePath = new URL(document.baseURI).pathname;
  const relativePath = window.location.pathname.startsWith(basePath)
    ? window.location.pathname.slice(basePath.length)
    : "";
  const match = /^ui\/([^/]+)\/?$/.exec(relativePath);
  return match ? decodeURIComponent(match[1]) : null;
}

function updateRoute(): void {
  pageName.value = pageNameFromLocation();
}

function navigate(relativePath: string, nextPageName: string | null): void {
  window.history.pushState({}, "", new URL(relativePath, document.baseURI));
  pageName.value = nextPageName;
  window.scrollTo({ top: 0, behavior: "smooth" });
}

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
        <a href="./" class="brand-link" aria-label="Service dashboard" @click.prevent="navigate('./', null)">
          <img v-if="config" :src="config.logoUrl" alt="k.LAB logo" class="brand-logo" />
        </a>
        <div class="brand-copy">
          <div class="brand-kicker">INTEGRATED MODELLING</div>
          <q-toolbar-title>{{ config?.title || "k.LAB service" }}</q-toolbar-title>
        </div>
        <q-space />
        <nav v-if="config && (config.pages.length || config.links.length)" class="header-links" aria-label="Service links">
          <a
            v-for="page in config.pages"
            :key="page.name"
            :href="`ui/${encodeURIComponent(page.name)}`"
            :class="{ 'header-link--active': page.name === pageName }"
            @click.prevent="navigate(`ui/${encodeURIComponent(page.name)}`, page.name)"
          >{{ page.title }}</a>
          <a
            v-for="link in config.links"
            :key="link.href"
            :href="link.href"
            :target="link.external ? '_blank' : undefined"
            :rel="link.external ? 'noreferrer' : undefined"
          >{{ link.label }}</a>
        </nav>
        <q-btn-dropdown
          v-if="config && (config.pages.length || config.links.length)"
          class="header-menu"
          flat
          round
          color="white"
          icon="apps"
          aria-label="Service interfaces"
        >
          <q-list class="account-menu">
            <q-item
              v-for="page in config.pages"
              :key="page.name"
              clickable
              v-close-popup
              :href="`ui/${encodeURIComponent(page.name)}`"
              @click.prevent="navigate(`ui/${encodeURIComponent(page.name)}`, page.name)"
            >
              <q-item-section avatar><q-icon name="web_asset" /></q-item-section>
              <q-item-section>{{ page.title }}</q-item-section>
            </q-item>
            <q-separator v-if="config.pages.length && config.links.length" />
            <q-item
              v-for="link in config.links"
              :key="link.href"
              clickable
              v-close-popup
              :href="link.href"
              :target="link.external ? '_blank' : undefined"
            >
              <q-item-section avatar><q-icon :name="link.external ? 'open_in_new' : 'link'" /></q-item-section>
              <q-item-section>{{ link.label }}</q-item-section>
            </q-item>
          </q-list>
        </q-btn-dropdown>
        <LoginControl :auth="authState" />
      </q-toolbar>
    </q-header>

    <q-page-container>
      <q-page class="dashboard-page">
        <FullPageExtension
          v-if="selectedPage"
          :page="selectedPage"
          :context="context"
        />
        <main v-else-if="isFullPageRoute" class="full-page-main">
          <q-banner rounded class="error-banner" inline-actions>
            The requested service interface <code>{{ pageName }}</code> is not registered.
            <template #action><q-btn flat label="Dashboard" href="./" /></template>
          </q-banner>
        </main>
        <main v-else class="dashboard-main">
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
