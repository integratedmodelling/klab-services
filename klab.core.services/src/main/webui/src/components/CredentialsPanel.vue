<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { serviceApi } from "../services/api";
interface Field { name: string; label: string; secret: boolean; required: boolean }
interface Schema { id: string; label: string; mechanism: string; fields: Field[] }
interface Info { key: string; host: string; description: string; schema: string; parameters: Record<string, string>; configuredFields: string[]; privileges: { public: boolean; allowedUsers: string[]; allowedGroups: string[]; excludedUsers: string[]; excludedGroups: string[] } }
const sets = ref<Info[]>([]);
const schemas = ref<Schema[]>([]);
const id = ref<string | null>(null);
const name = ref("");
const endpoint = ref("");
const schemaId = ref("basic");
const parameters = ref<Record<string, string>>({});
const users = ref("");
const groups = ref("");
const allUsers = ref(false);
const excludedUsers = ref("");
const excludedGroups = ref("");
const clearFields = ref<string[]>([]);
const configured = ref<string[]>([]);
const originalPrivileges = ref<Info["privileges"] | null>(null);
const error = ref("");
const busy = ref(false);
const loaded = ref(false);
let active = true;
onBeforeUnmount(() => { active = false; parameters.value = {}; sets.value = []; });
const schema = computed(() => schemas.value.find(s => s.id === schemaId.value));
function edit(set?: Info) {
  id.value = set?.key || null; name.value = set?.description || ""; endpoint.value = set?.host || "";
  schemaId.value = set?.schema || schemas.value[0]?.id || "basic";
  parameters.value = { ...set?.parameters }; configured.value = set?.configuredFields || [];
  originalPrivileges.value = set?.privileges || null;
  allUsers.value = set?.privileges.public || false;
  excludedUsers.value = set?.privileges.excludedUsers.join(", ") || "";
  excludedGroups.value = set?.privileges.excludedGroups.join(", ") || "";
  clearFields.value = [];
  users.value = set?.privileges.allowedUsers.join(", ") || "";
  groups.value = set?.privileges.allowedGroups.join(", ") || "";
}
async function load() {
  const [nextSets, nextSchemas] = await Promise.all([serviceApi.get<Info[]>("credentials"), serviceApi.get<Schema[]>("credentials/schemata")]);
  if (active) { sets.value = nextSets; schemas.value = nextSchemas; }
}
async function initialize() {
  busy.value = true;
  error.value = "";
  try {
    await load();
    if (active) { loaded.value = true; edit(); }
  } catch { error.value = "Could not load credential sets and authentication schemas."; }
  finally { busy.value = false; }
}
onMounted(initialize);
const split = (text: string) => text.split(",").map(s => s.trim()).filter(Boolean);
async function save() {
  busy.value = true; error.value = "";
  try {
    const values = Object.fromEntries(Object.entries(parameters.value).filter(([key, value]) => value !== "" || !schema.value?.fields.find(f => f.name === key)?.secret));
    for (const key of clearFields.value) values[key] = "";
    const set = await serviceApi.request<Info>("credentials", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ host: endpoint.value, credentials: { id: id.value, name: name.value, scheme: schemaId.value, parameters: values, privileges: { ...originalPrivileges.value, public: allUsers.value, allowedUsers: split(users.value), allowedGroups: split(groups.value), excludedUsers: split(excludedUsers.value), excludedGroups: split(excludedGroups.value) } } }) });
    parameters.value = {};
    await load(); if (active) edit(set);
  } catch { error.value = "Could not save the credential set. Check its name, endpoint and required fields."; }
  finally { busy.value = false; }
}
async function remove(set: Info) {
  if (!window.confirm(`Delete credential set “${set.description}”? Components referencing its ID will lose access.`)) return;
  busy.value = true; error.value = "";
  try {
    if (!await serviceApi.request<boolean>(`credentials?id=${encodeURIComponent(set.key)}`, { method: "DELETE" })) {
      throw new Error("Credential set was not deleted");
    }
    await load();
    if (active && set.key === id.value) edit();
  }
  catch { error.value = "Could not delete the credential set."; }
  finally { busy.value = false; }
}
</script>

<template>
  <section aria-labelledby="credential-sets-title">
    <div class="credential-heading">
      <div>
        <h2 id="credential-sets-title">Credential sets</h2>
        <p>Use a set’s ID to reference its credentials from a component.</p>
      </div>
      <q-btn v-if="loaded" outline color="primary" label="Add credential set" :disable="busy" @click="edit()" />
    </div>
    <q-banner v-if="error" class="error-banner" role="alert">
      {{ error }}
      <template v-if="!loaded" #action><q-btn flat label="Retry" @click="initialize" /></template>
    </q-banner>
    <p v-if="busy && !loaded" class="empty-state" role="status">Loading credential sets…</p>
    <div v-if="loaded" class="credential-layout">
      <section aria-label="Stored credentials">
        <q-list v-if="sets.length" bordered separator class="credential-list">
          <q-item v-for="set in sets" :key="set.key" :active="id === set.key" active-class="selected-set">
            <q-item-section>
              <q-item-label>{{ set.description }}</q-item-label>
              <q-item-label caption>{{ set.host }} · {{ set.schema }}</q-item-label>
              <code>{{ set.key }}</code>
            </q-item-section>
            <q-item-section side>
              <q-btn flat label="Edit" :disable="busy" @click="edit(set)" />
              <q-btn flat color="negative" label="Delete" :disable="busy" @click="remove(set)" />
            </q-item-section>
          </q-item>
        </q-list>
        <p v-else class="empty-state">No credential sets configured.</p>
      </section>
      <q-form class="credential-form" @submit="save">
        <h3>{{ id ? "Edit credential set" : "New credential set" }}</h3>
        <q-input v-model="name" outlined label="Name" :disable="busy" maxlength="200" required />
        <q-input v-model="endpoint" outlined label="Endpoint URL" hint="For example, https://github.com/organization" :disable="busy" required />
        <q-select v-model="schemaId" outlined label="Authentication schema" :options="schemas.map(s => ({ label: s.label, value: s.id }))" emit-value map-options :disable="busy || !!id" @update:model-value="parameters = {}" />
        <q-input v-for="field in schema?.fields || []" :key="field.name" v-model="parameters[field.name]" outlined :label="field.label + (field.required ? ' *' : '')" :type="field.secret ? 'password' : 'text'" :autocomplete="field.secret ? 'new-password' : 'off'" :hint="configured.includes(field.name) && field.secret ? 'Leave blank to keep the stored value' : undefined" :disable="busy" :required="field.required && !configured.includes(field.name)" />
        <q-checkbox v-for="field in (schema?.fields || []).filter(f => f.secret && !f.required && configured.includes(f.name))" :key="`clear-${field.name}`" v-model="clearFields" :val="field.name" :label="`Clear stored ${field.label}`" :disable="busy" />
        <q-checkbox v-model="allUsers" label="Allow all authenticated users" :disable="busy" />
        <q-input v-model="users" outlined label="Authorized users" hint="Comma-separated usernames. Administrators always have access." :disable="busy" />
        <q-input v-model="groups" outlined label="Authorized groups" hint="Comma-separated group names" :disable="busy" />
        <q-input v-model="excludedUsers" outlined label="Excluded users" hint="Comma-separated usernames. Exclusions override usage grants." :disable="busy" />
        <q-input v-model="excludedGroups" outlined label="Excluded groups" :disable="busy" />
        <q-btn unelevated type="submit" color="primary" label="Save credential set" :loading="busy" />
      </q-form>
    </div>
  </section>
</template>

<style scoped>
.credential-heading { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: start; gap: 12px; margin-bottom: 20px; }
h2 { font: 600 21px/1.35 var(--klab-font); letter-spacing: -.025em; margin: 0; }
h3 { font: 600 16px/1.4 var(--klab-font); margin: 0; }
.credential-heading p { margin: 4px 0 0; color: var(--klab-muted); }
.credential-layout { display: grid; grid-template-columns: minmax(0, 5fr) minmax(0, 7fr); gap: 24px; }
.credential-list, .credential-form { background: var(--klab-surface); border: 1px solid var(--klab-border); border-radius: var(--klab-radius); }
.credential-list { overflow-wrap: anywhere; }
.selected-set { background: var(--klab-surface-alt); }
.credential-list code { font: 13px/1.5 var(--klab-mono); }
.credential-form { display: grid; gap: 16px; padding: 20px; }
.credential-form .q-btn { justify-self: start; }
@media (max-width: 760px) { .credential-layout { grid-template-columns: minmax(0, 1fr); } }
</style>
