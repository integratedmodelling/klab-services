<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from "vue";
import { postJson, retrieveJob, waitForJob } from "../services/administration";
import { serviceApi } from "../services/api";

interface ImportProperty {
  type: string;
  optional: boolean;
  defaultValue?: string;
}

interface ImportSchema {
  schemaId: string;
  type: "PROPERTIES" | "STREAM";
  description?: string;
  properties?: Record<string, ImportProperty>;
  extensions?: string[];
  mediaTypes?: string[];
}

interface ImportResult {
  notifications?: { level: string; message: string }[];
  results?: { resourceUrn: string }[];
  empty?: boolean;
}

const props = defineProps<{ schemata: unknown }>();
const emit = defineEmits<{ imported: [] }>();
const controller = new AbortController();
const selected = ref<string | null>(null);
const values = ref<Record<string, string | null>>({});
const source = ref<"file" | "url">("file");
const file = ref<File | null>(null);
const url = ref("");
const busy = ref(false);
const error = ref("");
const result = ref<ImportResult>();
const available = computed(() => {
  const grouped = props.schemata;
  if (!grouped || typeof grouped !== "object") return [];
  const items = Object.values(grouped).flatMap(group => Array.isArray(group) ? group : []) as ImportSchema[];
  const registered = new Map<string, ImportSchema>();
  for (const item of items) {
    if (item?.schemaId && (item.type === "PROPERTIES" || item.type === "STREAM") && !registered.has(item.schemaId)) {
      registered.set(item.schemaId, item);
    }
  }
  return [...registered.values()].sort((a, b) => a.schemaId.localeCompare(b.schemaId));
});
const options = computed(() => available.value.map(schema => ({
  label: schema.schemaId,
  value: schema.schemaId,
})));
const schema = computed(() => available.value.find(item => item.schemaId === selected.value));
const properties = computed(() => Object.entries(schema.value?.properties ?? {}));
const extensions = computed(() => (schema.value?.extensions ?? []).map(ext => ext.startsWith(".") ? ext : `.${ext}`));
const accept = computed(() => [...extensions.value, ...(schema.value?.mediaTypes ?? [])].filter(Boolean).join(",") || undefined);
const failed = computed(() => result.value?.notifications?.some(note => ["Error", "SystemError"].includes(note.level)));

function propertyLabel(name: string, field: ImportProperty) {
  const words = name.replace(/([a-z0-9])([A-Z])/g, "$1 $2").replace(/[_-]+/g, " ")
    .replace(/\b(?:id|url|urn)\b/gi, word => word.toUpperCase());
  const label = words.charAt(0).toUpperCase() + words.slice(1);
  return field.optional ? `${label} (optional)` : label;
}

function select(id: string | null) {
  selected.value = id;
  values.value = Object.fromEntries(properties.value.map(([name, field]) => [name, field.defaultValue ?? ""]));
  file.value = null;
  url.value = "";
  source.value = "file";
  result.value = undefined;
  error.value = "";
}

async function download() {
  let address: URL;
  try {
    address = new URL(url.value.trim());
    if (!["https:", "http:"].includes(address.protocol) || address.username || address.password) throw new Error();
  } catch {
    throw new Error("Enter an HTTP or HTTPS file URL without embedded credentials.");
  }
  try {
    const response = await fetch(address, { credentials: "omit", signal: controller.signal });
    if (!response.ok) throw new Error();
    const name = address.pathname.split("/").pop() || "download";
    const content = await response.blob();
    return new File([content], name, { type: content.type });
  } catch {
    throw new Error("The browser could not download this URL. Save the file locally and choose it for upload.");
  }
}

async function submit() {
  const format = schema.value;
  if (!format || busy.value) return;
  busy.value = true;
  error.value = "";
  result.value = undefined;
  try {
    const signal = controller.signal;
    const endpoint = `import/${encodeURIComponent(format.schemaId)}/X:X:X:X`;
    let job: number;
    if (format.type === "PROPERTIES") {
      const inputs = Object.entries(format.properties ?? {});
      const missing = inputs.find(([name, field]) => !field.optional && !values.value[name]?.trim());
      if (missing) throw new Error(`Enter a value for ${missing[0]}.`);
      const body = Object.fromEntries(inputs.flatMap(([name]) => {
        const value = values.value[name];
        return value?.trim() ? [[name, value]] : [];
      }));
      job = await postJson<number>(endpoint, body, signal);
    } else {
      const chosen = source.value === "url" ? await download() : file.value;
      if (!chosen?.size) throw new Error("Choose a file with content to import.");
      const body = new FormData();
      body.set("file", chosen);
      job = await serviceApi.request<number>(endpoint, { method: "POST", body, signal, cache: "no-store" });
    }
    if (!Number.isSafeInteger(job) || job <= 0) throw new Error("The service did not return an import job.");
    await waitForJob(job, signal);
    result.value = await retrieveJob<ImportResult>(job, signal);
    if (!result.value) throw new Error("The service returned no import result.");
    if (!failed.value && !result.value.empty) emit("imported");
  } catch (reason) {
    if (!controller.signal.aborted) {
      error.value = reason instanceof Error ? reason.message : "The asset could not be imported.";
    }
  } finally {
    busy.value = false;
  }
}

onBeforeUnmount(() => controller.abort());
</script>

<template>
  <section aria-labelledby="import-assets-title">
    <h2 id="import-assets-title">Import assets</h2>
    <p v-if="!available.length" class="empty-state">This service advertises no import formats.</p>
    <q-form v-else class="import-form" @submit="submit">
      <q-select :model-value="selected" :options="options" emit-value map-options dense outlined
        label="Import format" :disable="busy" @update:model-value="select" />
      <template v-if="schema">
        <p v-if="schema.description" class="format-description">{{ schema.description }}</p>
        <div v-if="schema.type === 'PROPERTIES'" class="import-properties">
          <template v-for="[name, field] in properties" :key="name">
            <q-select v-if="field.type === 'BOOLEAN'" v-model="values[name]" :options="['true', 'false']"
              :label="propertyLabel(name, field)" :clearable="field.optional" dense outlined :disable="busy" />
            <q-input v-else v-model="values[name]" :label="propertyLabel(name, field)" dense outlined
              :type="/password|secret|token/i.test(name) ? 'password' : field.type === 'NUMBER' ? 'number' : 'text'"
              step="any" :required="!field.optional" :disable="busy" />
          </template>
        </div>
        <template v-else>
          <q-option-group v-model="source" inline :disable="busy"
            :options="[{label: 'Upload a file', value: 'file'}, {label: 'Use a file URL', value: 'url'}]" />
          <q-file v-if="source === 'file'" v-model="file" dense outlined label="Choose a file"
            :accept="extensions.join(',') || undefined"
            :disable="busy" @rejected="error = 'Choose a file in a format supported by this import schema.'" />
          <q-input v-else v-model="url" type="url" dense outlined label="File URL" required :disable="busy" />
          <p v-if="accept" class="format-description">Accepted formats · {{ accept.replaceAll(',', ', ') }}</p>
        </template>
        <q-btn unelevated color="primary" type="submit" label="Start import" :loading="busy"
          :disable="schema.type === 'STREAM' && source === 'file' && !file" />
      </template>
    </q-form>
    <p v-if="busy" class="empty-state" role="status">Import in progress…</p>
    <q-banner v-if="error" class="error-banner" role="alert">{{ error }}</q-banner>
    <section v-if="result" class="import-result" aria-labelledby="import-result-title">
      <h3 id="import-result-title" role="status">
        {{ failed ? 'Import reported an error' : result.empty ? 'No assets returned' : 'Import completed' }}
      </h3>
      <ul v-if="result.notifications?.length">
        <li v-for="(note, index) in result.notifications" :key="index">
          <strong>{{ note.level }}</strong> {{ note.message }}
        </li>
      </ul>
      <ul v-if="result.results?.length">
        <li v-for="(item, index) in result.results" :key="index"><code>{{ item.resourceUrn }}</code></li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.import-form, .import-result { max-width: 720px; }
h2 { font: 600 21px/1.35 var(--klab-font); letter-spacing: -.025em; margin: 0 0 14px; }
.import-form { display: grid; gap: 14px; }
.import-form > .q-btn { justify-self: start; }
.format-description { margin: 0; color: var(--klab-muted); overflow-wrap: anywhere; }
.import-properties { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.import-result { border-top: 1px solid var(--klab-border); margin-top: 24px; padding-top: 16px; overflow-wrap: anywhere; }
.import-result h3 { font: 600 16px/1.4 var(--klab-font); margin: 0; }
@media (max-width: 560px) { .import-properties { grid-template-columns: minmax(0, 1fr); } }
</style>
