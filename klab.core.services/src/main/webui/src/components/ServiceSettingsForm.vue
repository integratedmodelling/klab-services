<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { changeSetting, componentVersion, getSettings, type ComponentOption, type SettingField } from "../services/administration";

const props = defineProps<{ section: "general" | "email"; components: ComponentOption[] }>();
const emit = defineEmits<{ "components-changed": [] }>();
const controller = new AbortController();
const fields = ref<SettingField[]>([]);
const drafts = ref<Record<string, string | number | boolean | null>>({});
const selectedComponents = ref<Record<string, string | null>>({});
const selectedVersions = ref<Record<string, string | null>>({});
const loading = ref(true);
const loadError = ref(false);
const pending = ref("");
const feedback = ref<Record<string, { text: string; error?: boolean; result?: Record<string, unknown> }>>({});
const securityModes = ["STARTTLS", "SSL", "NONE"];
const labels: Record<string, string> = {
  EMAIL_ENABLED: "Outgoing email",
  EMAIL_SMTP_HOST: "SMTP server",
  EMAIL_SMTP_PORT: "SMTP port",
  EMAIL_FROM_ADDRESS: "Sender address",
  EMAIL_FROM_NAME: "Sender name",
  EMAIL_REPLY_TO: "Reply-to address",
  EMAIL_AUTHENTICATION: "SMTP authentication",
  EMAIL_USERNAME: "SMTP username",
  EMAIL_PASSWORD: "SMTP password",
  EMAIL_SECURITY: "SMTP security",
  EMAIL_CONNECTION_TIMEOUT_MS: "Connection timeout (ms)",
  EMAIL_READ_TIMEOUT_MS: "Read timeout (ms)",
  EMAIL_WRITE_TIMEOUT_MS: "Write timeout (ms)",
  RESET_ALL_GIT_PROJECTS: "Reset all Git projects",
  UPDATE_COMPONENT: "Update component",
  REMOVE_COMPONENT: "Remove component",
};
const sections = computed(() => {
  const visible = fields.value.filter(field => (field.page === "EMAIL") === (props.section === "email"));
  const values = visible.filter(field => field.kind !== "action");
  const actions = visible.filter(field => field.kind === "action");
  return [
    { title: props.section === "email" ? "SMTP configuration" : "Service configuration", fields: values },
    { title: "Service actions", fields: actions },
  ].filter(group => group.fields.length);
});

function label(field: SettingField) {
  return labels[field.name] ?? field.name[0] + field.name.slice(1).toLowerCase().replaceAll("_", " ");
}

async function load() {
  loading.value = true;
  loadError.value = false;
  try {
    fields.value = await getSettings(controller.signal);
    drafts.value = Object.fromEntries(fields.value.map(field => [field.name, field.value ?? ""]));
  } catch {
    if (!controller.signal.aborted) loadError.value = true;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
onBeforeUnmount(() => { controller.abort(); drafts.value = {}; });

function needsComponent(field: SettingField) {
  return field.name === "UPDATE_COMPONENT" || field.name === "REMOVE_COMPONENT";
}

function eligibleComponents(field: SettingField) {
  return props.components.filter(component => component.id && componentVersion(component.version)
    && (field.name === "REMOVE_COMPONENT" ? component.importType !== "BUILT_IN"
      : component.importType === "DEPENDENCY" || (component.importType === "MAVEN"
        && component.mavenCoordinates?.includes("SNAPSHOT"))));
}

function componentIds(field: SettingField) {
  return [...new Set(eligibleComponents(field).map(component => component.id))].sort();
}

function componentId(field: SettingField) {
  const ids = componentIds(field);
  const selected = selectedComponents.value[field.name];
  return selected && ids.includes(selected) ? selected : ids.length === 1 ? ids[0] : null;
}

function versions(field: SettingField) {
  const id = componentId(field);
  return [...new Set(eligibleComponents(field).filter(component => component.id === id)
    .map(component => componentVersion(component.version)))].sort((a, b) => b.localeCompare(a, undefined, { numeric: true }));
}

function versionFor(field: SettingField) {
  const options = versions(field);
  const selected = selectedVersions.value[field.name];
  return selected && options.includes(selected) ? selected : options.length === 1 ? options[0] : null;
}

function changed(field: SettingField) {
  const draft = drafts.value[field.name];
  if (field.kind === "password") return !!draft;
  return String(draft ?? "") !== String(field.value ?? "");
}

async function save(field: SettingField, clear = false) {
  if (pending.value || (!clear && field.kind !== "action" && !changed(field))) return;
  const key = field.name;
  const draft = drafts.value[key];
  let value: unknown = draft ?? "";
  if (field.kind === "integer") {
    const integer = Number(draft);
    if (!/^-?\d+$/.test(String(draft)) || integer < -2147483648 || integer > 2147483647) {
      feedback.value[key] = { text: "Enter a whole number between −2147483648 and 2147483647.", error: true };
      return;
    }
    value = integer;
  }
  if (field.kind === "action") {
    const component = needsComponent(field) ? componentId(field) : null;
    const version = needsComponent(field) ? versionFor(field) : null;
    if (needsComponent(field) && (!component || !version)) return;
    const target = component ? ` ${component} ${version}` : "";
    if (!window.confirm(`${label(field)}${target}?\n\n${field.description}`)) return;
    value = component ? { component, version } : {};
  }
  if (clear) {
    if (!window.confirm("Clear the stored SMTP password?")) return;
    value = "";
  }

  pending.value = key;
  delete feedback.value[key];
  try {
    const result = await changeSetting(field, value, controller.signal);
    if (controller.signal.aborted) return;
    if (field.kind === "action") {
      const failed = result?.result === false;
      feedback.value[key] = {
        text: typeof result?.message === "string" ? result.message : failed ? "The action did not succeed." : "Action completed.",
        error: failed,
        result,
      };
      if (!failed && needsComponent(field)) emit("components-changed");
    } else {
      if (field.kind === "password") {
        field.configured = !clear;
        drafts.value[key] = "";
      } else {
        field.value = value as SettingField["value"];
        drafts.value[key] = field.value;
      }
      feedback.value[key] = { text: clear ? "Password cleared." : "Saved." };
    }
  } catch (error) {
    if (!controller.signal.aborted) {
      feedback.value[key] = { text: error instanceof Error ? error.message : "Could not save the setting.", error: true };
    }
  } finally {
    pending.value = "";
  }
}
</script>

<template>
  <p v-if="loading" class="empty-state" role="status">Loading service settings…</p>
  <q-banner v-else-if="loadError" class="error-banner" role="alert">
    Could not load the service settings.
    <template #action><q-btn flat label="Retry" @click="load" /></template>
  </q-banner>
  <p v-else-if="!sections.length" class="empty-state">This service exposes no settings in this section.</p>
  <section v-for="group in sections" :key="group.title" class="settings-group">
    <h2>{{ group.title }}</h2>
    <q-form v-for="field in group.fields" :key="field.name" class="setting-row" @submit="save(field)">
      <div class="setting-description">
        <span v-if="field.kind === 'action' || field.kind === 'boolean'" :id="`label-${field.name}`">{{ label(field) }}</span>
        <label v-else :id="`label-${field.name}`" :for="`setting-${field.name}`">{{ label(field) }}</label>
        <p :id="`hint-${field.name}`">{{ field.description }}</p>
      </div>
      <div class="setting-editor" :class="{ 'setting-editor--inline': field.kind !== 'action' }">
        <q-toggle v-if="field.kind === 'boolean'" v-model="drafts[field.name]" :disable="!!pending"
          :aria-labelledby="`label-${field.name}`" :aria-describedby="`hint-${field.name}`"
          :label="drafts[field.name] ? 'On' : 'Off'" />
        <q-select v-else-if="field.name === 'EMAIL_SECURITY'" v-model="drafts[field.name]" dense outlined
          :options="securityModes" :disable="!!pending" :for="`setting-${field.name}`"
          :aria-labelledby="`label-${field.name}`" :aria-describedby="`hint-${field.name}`" />
        <template v-else-if="field.kind === 'action'">
          <div v-if="needsComponent(field) && componentIds(field).length" class="component-pickers">
            <q-select :model-value="componentId(field)" :options="componentIds(field)" dense outlined
              label="Component ID" :disable="!!pending"
              @update:model-value="selectedComponents[field.name] = $event; selectedVersions[field.name] = null" />
            <q-select :model-value="versionFor(field)" :options="versions(field)" dense outlined
              label="Version" :disable="!!pending || !componentId(field)"
              @update:model-value="selectedVersions[field.name] = $event" />
          </div>
          <p v-else-if="needsComponent(field)" class="setting-feedback unavailable">
            {{ field.name === 'UPDATE_COMPONENT' ? 'No installed components support updates.' : 'No installed components can be removed.' }}
          </p>
        </template>
        <q-input v-else :model-value="String(drafts[field.name] ?? '')" dense outlined
          @update:model-value="drafts[field.name] = $event"
          :for="`setting-${field.name}`" :aria-labelledby="`label-${field.name}`" :aria-describedby="`hint-${field.name}`"
          :type="field.kind === 'password' ? 'password' : 'text'"
          :inputmode="field.kind === 'integer' ? 'numeric' : undefined"
          :autocomplete="field.kind === 'password' ? 'new-password' : undefined"
          :hint="field.kind === 'password' ? (field.configured ? 'Password stored. Enter a new password to replace it.' : 'No password stored.') : field.kind === 'path' ? 'Path on the service host' : undefined"
          :disable="!!pending" />
        <div class="setting-actions">
          <q-btn v-if="field.kind === 'action'" type="submit" outline color="primary"
            :label="label(field)" :loading="pending === field.name"
            :disable="!!pending || (needsComponent(field) && !versionFor(field))" />
          <q-btn v-else type="submit" outline color="primary" label="Save"
            :aria-label="`Save ${label(field)}`" :loading="pending === field.name" :disable="!!pending || !changed(field)" />
          <q-btn v-if="field.kind === 'password' && field.configured" flat label="Clear password" :disable="!!pending"
            @click="save(field, true)" />
        </div>
        <p v-if="feedback[field.name]" class="setting-feedback" :class="{ failed: feedback[field.name].error }"
          :role="feedback[field.name].error ? 'alert' : 'status'">{{ feedback[field.name].text }}</p>
        <details v-if="feedback[field.name]?.result">
          <summary>Operation result</summary>
          <pre>{{ JSON.stringify(feedback[field.name].result, null, 2) }}</pre>
        </details>
      </div>
    </q-form>
  </section>
</template>

<style scoped>
.settings-group { max-width: 1040px; }
.settings-group + .settings-group { margin-top: 24px; }
h2 { font: 600 21px/1.35 var(--klab-font); letter-spacing: -.025em; margin: 0 0 14px; }
.setting-row { display: grid; grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr); gap: 20px; padding: 8px 0; border-top: 1px solid var(--klab-border); }
.setting-description :is(label, span) { font-size: 15px; font-weight: 600; }
.setting-description p { margin: 2px 0 0; color: var(--klab-muted); font-size: 13px; }
.setting-editor { display: grid; gap: 10px; min-width: 0; align-content: start; }
.setting-editor--inline { grid-template-columns: minmax(0, 1fr) auto; align-items: start; }
.setting-editor--inline .setting-feedback { grid-column: 1 / -1; }
.setting-editor .q-toggle { justify-self: start; min-height: 44px; }
.setting-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.setting-feedback { margin: 0; color: var(--klab-success); }
.setting-feedback.failed { color: var(--klab-danger); }
.setting-feedback.unavailable { color: var(--klab-muted); }
.component-pickers { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); gap: 8px; }
summary { cursor: pointer; }
pre { margin: 8px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; font: 13px/1.5 var(--klab-mono); }
@media (max-width: 640px) { .setting-row { grid-template-columns: minmax(0, 1fr); gap: 10px; } }
@media (max-width: 420px) {
  .setting-editor--inline { grid-template-columns: minmax(0, 1fr); gap: 6px; }
  .component-pickers { grid-template-columns: minmax(0, 1fr); }
}
</style>
