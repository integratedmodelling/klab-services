<script setup lang="ts">
import { ref } from "vue";
import CredentialsPanel from "./CredentialsPanel.vue";
import ServiceImport from "./ServiceImport.vue";
import ServiceLogs from "./ServiceLogs.vue";
import ServiceSettingsForm from "./ServiceSettingsForm.vue";
import type { ComponentOption } from "../services/administration";

type Section = "general" | "email" | "credentials" | "logs" | "import";
const props = defineProps<{
  initialSection: "general" | "credentials" | "import";
  serviceTitle: string;
  components: ComponentOption[];
  importSchemata: unknown;
}>();
const emit = defineEmits<{ "components-changed": [] }>();
const section = ref<Section>(props.initialSection);
const configurationSection = ref<"general" | "email">("general");
const showConfiguration = ref(props.initialSection === "general");
const openedCredentials = ref(props.initialSection === "credentials");
const openedLogs = ref(false);
const openedImport = ref(props.initialSection === "import");
const sections = [
  { name: "general", label: "General" },
  { name: "email", label: "Email" },
  { name: "credentials", label: "Credentials" },
  { name: "logs", label: "Logs" },
  { name: "import", label: "Import" },
];

function select(next: typeof section.value) {
  if (next === "general" || next === "email") {
    configurationSection.value = next;
    showConfiguration.value = true;
  }
  if (next === "credentials") openedCredentials.value = true;
  if (next === "logs") openedLogs.value = true;
  if (next === "import") openedImport.value = true;
  section.value = next;
}
</script>

<template>
  <main class="full-page-main">
    <header class="full-page-heading admin-heading" data-enter>
      <div>
        <h1>Administration</h1>
        <p>{{ serviceTitle }}</p>
      </div>
    </header>
    <q-tabs :model-value="section" @update:model-value="select" class="admin-tabs"
      align="left" narrow-indicator no-caps active-color="primary" indicator-color="primary" aria-label="Administration sections">
      <q-tab v-for="item in sections" :key="item.name" :name="item.name" :label="item.label"
        :id="`admin-tab-${item.name}`"
        :aria-controls="item.name === 'general' || item.name === 'email' ? 'admin-panel-configuration' : `admin-panel-${item.name}`" />
    </q-tabs>
    <div v-show="section === 'general' || section === 'email'" class="admin-panel" role="tabpanel"
      id="admin-panel-configuration" :aria-labelledby="`admin-tab-${configurationSection}`" tabindex="0">
      <ServiceSettingsForm v-if="showConfiguration" :section="configurationSection" :components="components"
        @components-changed="emit('components-changed')" />
    </div>
    <div v-show="section === 'credentials'" id="admin-panel-credentials" aria-labelledby="admin-tab-credentials"
      class="admin-panel" role="tabpanel" tabindex="0">
      <CredentialsPanel v-if="openedCredentials" />
    </div>
    <div v-show="section === 'logs'" id="admin-panel-logs" aria-labelledby="admin-tab-logs"
      class="admin-panel" role="tabpanel" tabindex="0">
      <ServiceLogs v-if="openedLogs" :visible="section === 'logs'" />
    </div>
    <div v-show="section === 'import'" id="admin-panel-import" aria-labelledby="admin-tab-import"
      class="admin-panel" role="tabpanel" tabindex="0">
      <ServiceImport v-if="openedImport" :schemata="importSchemata" @imported="emit('components-changed')" />
    </div>
  </main>
</template>

<style scoped>
.admin-heading { margin: 0; padding-bottom: 14px; animation: admin-enter 220ms ease-out; }
@keyframes admin-enter { from { transform: translateY(4px); } }
.admin-tabs { border-bottom: 1px solid var(--klab-border); color: var(--klab-muted); }
.admin-tabs :deep(.q-tab) { min-height: 48px; padding-inline: clamp(8px, 2vw, 18px); }
.admin-tabs :deep(.q-tab:focus-visible) { outline: 2px solid var(--klab-action); outline-offset: -3px; }
.admin-panel { padding-top: 16px; }
@media (max-width: 480px) {
  .admin-tabs :deep(.q-tab) { padding-inline: 6px; }
  .admin-tabs :deep(.q-tab__content) { min-width: 0; }
  .admin-tabs :deep(.q-tab__label) { font-size: 13px; }
}
</style>
