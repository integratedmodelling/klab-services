<script setup lang="ts">
import {computed, onBeforeUnmount, ref, shallowRef, watch, type Component} from "vue";
import type {DashboardContext} from "../types";
import AuthenticationDialog from "./AuthenticationDialog.vue";
import ApplicationNode from "./ApplicationNode.vue";
import {ApplicationConnection, type InstanceState, type UiSnapshot, type WidgetInput} from "../services/application-ui";
import "../styles/application-ui.css";
import {resolveExtension} from "../services/extensions";

const props = defineProps<{urn: string; context: DashboardContext}>();
const snapshot = ref<UiSnapshot | null>(null);
const error = ref("");
const connected = ref(false);
const launching = ref(false);
const rendererEpoch = ref(0);
const instance = ref<InstanceState | null>(null);
const toolkitRenderer = shallowRef<Component>();
let connection: ApplicationConnection | undefined;
let generation = 0;
let previousKey: string | undefined;
let styleElements: HTMLStyleElement[] = [];
const theme = computed(() => snapshot.value?.root.properties.theme || "light");
const nodes = computed(() => snapshot.value?.root.children || []);
const regions = computed(() => ({header: nodes.value.filter(n => n.type === "header"),
  footer: nodes.value.filter(n => n.type === "footer"), drawer: nodes.value.filter(n => n.type === "drawer"),
  main: nodes.value.filter(n => !["header", "footer", "drawer"].includes(n.type))}));
const authPage = computed(() => ({name: "application", title: props.urn, description: "Open this k.Actors application.",
  component: "application", order: 0, requiresAuthentication: true}));

function key(): string { return `klab.ui:${document.baseURI}:${props.context.auth.username}:${props.urn}`; }
function readHint(name: string): {key?: string; id?: string} | null {
  try { return JSON.parse(sessionStorage.getItem(name) || "null"); } catch { return null; }
}
function writeHint(name: string, value?: unknown): void {
  try { if (value === undefined) sessionStorage.removeItem(name); else sessionStorage.setItem(name, JSON.stringify(value)); } catch { /* Resuming after reload is optional when browser storage is disabled. */ }
}
function clean(): void {
  connection?.close(); connection = undefined;
  connected.value = false;
  toolkitRenderer.value = undefined;
  styleElements.forEach(element => element.remove()); styleElements = [];
}
async function start(fresh = false): Promise<void> {
  const current = ++generation;
  clean(); error.value = ""; snapshot.value = null; launching.value = true;
  const hintKey = previousKey = key();
  try {
    const hint = fresh ? null : readHint(hintKey);
    const creationKey = hint?.key || crypto.randomUUID();
    writeHint(hintKey, {key: creationKey, id: hint?.id});
    const result = await props.context.api.request<InstanceState>("api/v1/ui/instances", {method: "POST",
      headers: {"Content-Type": "application/json"}, body: JSON.stringify({urn: props.urn, locale: navigator.language,
        idempotencyKey: creationKey, resume: hint?.id})});
    if (current !== generation) return;
    instance.value = result;
    if (result.toolkit && result.toolkit.protocolVersion !== 1) throw new Error("Unsupported toolkit protocol");
    if (result.toolkit && result.toolkit.id !== "quasar") {
      if (!result.rendererUrl) throw new Error("Toolkit has no browser renderer");
      // The URL is generated from a trusted installed component's module allowlist by the server.
      toolkitRenderer.value = resolveExtension("application-toolkit", {...props.context.config,
        modules: {"application-toolkit": result.rendererUrl}});
    }
    snapshot.value = result.state.snapshot;
    writeHint(hintKey, {key: creationKey, id: result.id});
    for (const css of Object.values(result.stylesheets)) {
      const element = document.createElement("style");
      // Stylesheets are trusted, project-authored CSS; keep their rules inside the application.
      element.textContent = `@scope (.application-ui) { ${css} }`;
      document.head.appendChild(element); styleElements.push(element);
    }
    connection = new ApplicationConnection(props.context.api, result.id,
      (state, rebuild) => { if (current === generation) { snapshot.value = state; if (rebuild) rendererEpoch.value++; } },
      (online, message) => { if (current === generation) { connected.value = online; error.value = message; } });
    await connection.connect();
  } catch (reason) { if (current === generation) error.value = reason instanceof Error ? reason.message : "Application unavailable"; }
  finally { if (current === generation) launching.value = false; }
}
function input(event: WidgetInput): void { connection?.send(event); }
async function stop(): Promise<void> {
  if (!instance.value) return;
  try {
    await props.context.api.request(`api/v1/ui/instances/${instance.value.id}`, {method: "DELETE"});
    generation++; clean(); snapshot.value = null; instance.value = null;
    writeHint(key()); error.value = "Application stopped";
  } catch (reason) { error.value = String(reason); }
}
watch(() => [props.urn, props.context.auth.ready, props.context.auth.authenticated, props.context.auth.username], () => {
  generation++; clean(); snapshot.value = null; instance.value = null;
  if (!props.context.auth.authenticated) { if (previousKey) writeHint(previousKey); return; }
  if (props.context.auth.ready) void start();
}, {immediate: true});
onBeforeUnmount(() => { generation++; clean(); });
</script>

<template>
  <main class="actor-application">
    <div class="application-controls">
      <span>{{ urn }}</span><q-space />
      <q-btn v-if="context.auth.authenticated" flat no-caps label="Reconnect" :loading="launching" @click="start()" />
      <q-btn v-if="context.auth.authenticated" flat no-caps label="New instance" @click="start(true)" />
      <q-btn v-if="instance" flat no-caps label="Stop" @click="stop" />
    </div>
    <q-banner v-if="error" role="status" class="application-status">{{ error }}</q-banner>
    <component v-if="snapshot && context.auth.authenticated && toolkitRenderer" :is="toolkitRenderer" :key="rendererEpoch"
      :snapshot="snapshot" :disabled="!connected" :context="context" @input="input" />
    <section v-else-if="snapshot && context.auth.authenticated" :key="rendererEpoch" class="application-ui application-scaffold" :data-theme="theme"
      :lang="snapshot.root.properties.locale" :aria-busy="!connected">
      <header v-if="regions.header.length" class="application-header"><ApplicationNode v-for="node in regions.header" :key="node.id" :node="node" :disabled="!connected" :theme="theme" @input="input" /></header>
      <aside v-if="regions.drawer.some(n => !n.properties.hidden)" class="application-drawer"><ApplicationNode v-for="node in regions.drawer" :key="node.id" :node="node" :disabled="!connected" :theme="theme" @input="input" /></aside>
      <section class="application-main"><ApplicationNode v-for="node in regions.main" :key="node.id" :node="node" :disabled="!connected" :theme="theme" @input="input" /></section>
      <footer v-if="regions.footer.length" class="application-footer"><ApplicationNode v-for="node in regions.footer" :key="node.id" :node="node" :disabled="!connected" :theme="theme" @input="input" /></footer>
    </section>
    <AuthenticationDialog :visible="context.auth.ready && !context.auth.authenticated" :auth="context.auth" :page="authPage" />
  </main>
</template>
