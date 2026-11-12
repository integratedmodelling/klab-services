<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowReactive, watch } from "vue";
import CredentialsPanel from "./components/CredentialsPanel.vue";
import CapabilitiesPanel from "./components/CapabilitiesPanel.vue";
import ExtensionPanel from "./components/ExtensionPanel.vue";
import FullPageExtension from "./components/FullPageExtension.vue";
import LoginControl from "./components/LoginControl.vue";
import StatusGrid from "./components/StatusGrid.vue";
import ActorApplication from "./components/ActorApplication.vue";
import { authState, initializeAuthentication } from "./services/auth";
import { serviceApi } from "./services/api";
import { theme, toggleTheme } from "./services/theme";
import type { DashboardConfiguration, DashboardContext, FullPageComponent, UnsavedWorkGuard } from "./types";

const config = ref<DashboardConfiguration | null>(null);
const status = ref<Record<string, unknown> | null>(null);
const capabilities = ref<Record<string, unknown> | null>(null);
const loading = ref(true);
const refreshing = ref(false);
const error = ref("");
let refreshTimer: number | undefined;
let refreshGeneration = 0;
const pageName = ref(pageNameFromLocation());
const applicationUrn = ref(applicationFromLocation());
const headerRef = ref<{ $el: HTMLElement }>();
const headerHeight = ref(0);
let headerObserver: ResizeObserver | undefined;
let headerMeasureFrame: number | undefined;
const navigationGuards = shallowReactive(new Set<UnsavedWorkGuard>());
const dirtyGuards = computed(() => [...navigationGuards].filter(guard => guard.isDirty()));
const leaveDialog = ref(false), savingBeforeLeave = ref(false), leaveError = ref("");
const cannotDiscard = computed(() => dirtyGuards.value.some(guard => guard.isBusy()));
const canSaveBeforeLeave = computed(() => dirtyGuards.value.length > 0 && dirtyGuards.value.every(guard => guard.canSave()));
let pendingNavigation: (() => void) | undefined;
const historyKey = "klabNavigationIndex";
let routeIndex = Number(window.history.state?.[historyKey] ?? 0);
let restoringHistory = false, acceptingHistory = false;
window.history.replaceState({ ...window.history.state, [historyKey]: routeIndex }, "");

function requestNavigation(action: () => void) {
  if (!dirtyGuards.value.length) { action(); return; }
  if (leaveDialog.value || savingBeforeLeave.value) return;
  pendingNavigation = action; leaveError.value = ""; leaveDialog.value = true;
}
function stayOnPage() { pendingNavigation = undefined; leaveDialog.value = false; leaveError.value = ""; }
async function leavePage(save: boolean) {
  if (savingBeforeLeave.value || cannotDiscard.value || (save && !canSaveBeforeLeave.value)) return;
  savingBeforeLeave.value = true; leaveError.value = "";
  try {
    for (const guard of dirtyGuards.value) {
      if (save) {
        if (!await guard.save()) { leaveError.value = "Your work could not be saved. Stay here to review the message and try again."; return; }
      } else guard.discard();
    }
    const next = pendingNavigation; pendingNavigation = undefined; leaveDialog.value = false; next?.();
  } catch { leaveError.value = "Your work could not be saved. Stay here to keep your edits."; }
  finally { savingBeforeLeave.value = false; }
}

function applicationFromLocation(): string | null {
  const base = new URL(document.baseURI).pathname;
  const match = /^apps\/([^/]+)\/?$/.exec(window.location.pathname.slice(base.length));
  return match ? decodeURIComponent(match[1]) : null;
}

const context = reactive({
  api: serviceApi,
  auth: authState,
  get config() { return config.value!; },
  get status() { return status.value; },
  get capabilities() { return capabilities.value; },
  refresh,
  registerNavigationGuard(guard: UnsavedWorkGuard) { navigationGuards.add(guard); return () => navigationGuards.delete(guard); },
}) as DashboardContext;

const advisories = computed(() =>
  Array.isArray(status.value?.advisories) ? status.value.advisories : [],
);
const selectedPage = computed<FullPageComponent | undefined>(() =>
  config.value?.pages.find((page) => page.name === pageName.value),
);
const canAdminister = computed(() => authState.authenticated && Array.isArray(capabilities.value?.permissions)
  && capabilities.value.permissions.includes("ADMINISTER"));
const isFullPageRoute = computed(() => pageName.value !== null);
const serviceLabel = computed(() => {
  const type = config.value?.serviceType?.toLowerCase() || "service";
  return type.charAt(0).toUpperCase() + type.slice(1);
});

onMounted(async () => {
  if (headerRef.value?.$el) {
    headerObserver = new ResizeObserver(([entry]) => {
      if (headerMeasureFrame !== undefined) cancelAnimationFrame(headerMeasureFrame);
      headerMeasureFrame = requestAnimationFrame(() => {
        headerHeight.value = entry.target.getBoundingClientRect().height;
      });
    });
    headerObserver.observe(headerRef.value.$el);
  }
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
  headerObserver?.disconnect();
  if (headerMeasureFrame !== undefined) cancelAnimationFrame(headerMeasureFrame);
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

function updateRoute(event?: PopStateEvent): void {
  if (restoringHistory) { restoringHistory = false; return; }
  const nextIndex = Number(event?.state?.[historyKey] ?? routeIndex);
  if (event && dirtyGuards.value.length && !acceptingHistory && nextIndex !== routeIndex) {
    const delta = routeIndex - nextIndex;
    restoringHistory = true;
    window.history.go(delta);
    requestNavigation(() => { acceptingHistory = true; window.history.go(-delta); });
    return;
  }
  acceptingHistory = false;
  routeIndex = nextIndex;
  pageName.value = pageNameFromLocation();
  applicationUrn.value = applicationFromLocation();
}

function navigate(relativePath: string, nextPageName: string | null): void {
  const destination = new URL(relativePath, document.baseURI);
  if (destination.href === window.location.href) return;
  requestNavigation(() => {
    window.history.pushState({ [historyKey]: ++routeIndex }, "", destination);
    pageName.value = nextPageName;
    applicationUrn.value = applicationFromLocation();
    window.scrollTo({ top: 0, behavior: "auto" });
  });
}

async function refresh(): Promise<void> {
  const generation = ++refreshGeneration;
  refreshing.value = true;
  try {
    const [newStatus, newCapabilities] = await Promise.all([
      serviceApi.get("public/status"),
      serviceApi.get("public/capabilities", authState.authenticated),
    ]);
    if (generation !== refreshGeneration) return;
    status.value = newStatus;
    capabilities.value = newCapabilities;
    error.value = "";
  } catch (reason) {
    if (generation !== refreshGeneration) return;
    error.value = reason instanceof Error ? reason.message : "Service data is unavailable";
  } finally {
    if (generation === refreshGeneration) refreshing.value = false;
  }
}

watch(() => authState.authenticated, () => {
  // Drop the previous user's grants immediately and reject any late capabilities response.
  capabilities.value = null;
  refreshGeneration++;
  if (config.value && authState.ready) void refresh();
});
</script>

<template>
  <q-layout view="hHh lpR fFf" class="dashboard-shell" :style="{ '--klab-header-height': `${headerHeight}px` }">
    <a class="skip-link" href="#main-content">Skip to content</a>
    <q-header ref="headerRef" class="dashboard-header">
      <q-toolbar class="header-inner">
        <a href="./" class="brand-link" aria-label="Service dashboard" @click.prevent="navigate('./', null)">
          <img v-if="config" :src="config.logoUrl" alt="k.LAB logo" class="brand-logo" width="44" height="44" />
        </a>
        <div class="brand-copy">
          <div class="brand-kicker">Integrated modelling</div>
          <q-toolbar-title>{{ config?.title || "k.LAB service" }}</q-toolbar-title>
        </div>
        <q-space />
        <nav v-if="config && (config.pages.length || config.links.length)" class="header-links" aria-label="Service links">
          <a
            v-for="page in config.pages"
            :key="page.name"
            :href="`ui/${encodeURIComponent(page.name)}`"
            :class="{ 'header-link--active': page.name === pageName }"
            :aria-current="page.name === pageName ? 'page' : undefined"
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
        <q-btn v-if="canAdminister" class="credentials-link" flat label="Credentials" @click="navigate('ui/credentials', 'credentials')" />
        <q-btn flat round class="theme-toggle" :icon="theme === 'light' ? 'dark_mode' : 'light_mode'" :aria-pressed="theme === 'dark'"
          :aria-label="theme === 'light' ? 'Switch to dark theme' : 'Switch to light theme'" @click="toggleTheme">
          <q-tooltip>{{ theme === 'light' ? 'Dark theme' : 'Light theme' }}</q-tooltip>
        </q-btn>
        <LoginControl :auth="authState" :request-navigation="requestNavigation" />
      </q-toolbar>
    </q-header>

    <q-dialog v-model="leaveDialog" persistent aria-labelledby="unsaved-work-title">
      <q-card class="unsaved-work-dialog">
        <q-card-section>
          <h2 id="unsaved-work-title">Keep your review work?</h2>
          <p>Your changes have not been saved. Save them before leaving, or explicitly discard them.</p>
          <p v-if="cannotDiscard" role="status">An operation is still running. Stay here until its outcome is known.</p>
          <p v-else-if="!canSaveBeforeLeave">Some work needs attention on this page before it can be saved. Stay here to save files, complete required fields, or check an uncertain operation.</p>
          <p v-if="leaveError" role="alert">{{ leaveError }}</p>
        </q-card-section>
        <q-card-actions align="right" class="unsaved-work-actions">
          <q-btn flat no-caps label="Stay here" :disable="savingBeforeLeave" @click="stayOnPage" />
          <q-btn outline no-caps label="Discard and leave" :disable="savingBeforeLeave || cannotDiscard" @click="leavePage(false)" />
          <q-btn unelevated no-caps color="primary" label="Save and leave" :disable="!canSaveBeforeLeave || cannotDiscard" :loading="savingBeforeLeave" @click="leavePage(true)" />
        </q-card-actions>
      </q-card>
    </q-dialog>
    <q-page-container>
      <div id="main-content" class="dashboard-page" tabindex="-1">
        <CredentialsPanel v-if="pageName === 'credentials' && canAdminister" :key="authState.username" />
        <ActorApplication v-else-if="applicationUrn && config" :urn="applicationUrn" :context="context" />
        <FullPageExtension
          v-else-if="selectedPage"
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
              <h1>{{ serviceLabel }} overview</h1>
              <p>{{ config?.subtitle || "Service status and capabilities" }}</p>
            </div>
            <q-btn
              flat
              round
              color="primary"
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
      </div>
    </q-page-container>
  </q-layout>
</template>

<style scoped>
.header-menu { display: none; }
@media (max-width: 1100px) { .header-menu { display: inline-flex; } }
.unsaved-work-dialog { width: min(560px, calc(100vw - 32px)); padding: 8px; color: var(--klab-ink); }
.unsaved-work-dialog h2 { font-size: 1.5rem; line-height: 1.3; font-weight: 600; text-wrap: balance; margin: 0 0 16px; }
.unsaved-work-dialog p { font-size: 16px; line-height: 1.5; }
.unsaved-work-actions { gap: 8px; flex-wrap: wrap; }
</style>
