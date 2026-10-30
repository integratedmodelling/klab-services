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
onMounted(async () => { busy.value = true; try { await load(); edit(); } catch { error.value = "Credential administration is unavailable."; } finally { busy.value = false; } });
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
  try { await serviceApi.request(`credentials?id=${encodeURIComponent(set.key)}`, { method: "DELETE" }); await load(); if (active) edit(); }
  catch { error.value = "Could not delete the credential set."; }
  finally { busy.value = false; }
}
</script>

<template>
  <main class="credentials-page q-pa-lg">
    <div class="row items-center q-mb-md"><h1 class="text-h4">Credentials</h1><q-space /><q-btn label="Add credential set" :disable="busy" @click="edit()" /></div>
    <p>Store credentials on this service and reference a set by its ID. Secret fields are never returned. Leave a secret blank when editing to keep its current value.</p>
    <q-banner v-if="error" class="bg-red-1 text-negative q-mb-md" role="alert">{{ error }}</q-banner>
    <div class="row q-col-gutter-lg">
      <section class="col-12 col-md-5" aria-label="Credential sets">
        <q-list bordered separator><q-item v-for="set in sets" :key="set.key">
          <q-item-section><q-item-label>{{ set.description }}</q-item-label><q-item-label caption>{{ set.host }} · {{ set.schema }}</q-item-label><code>{{ set.key }}</code></q-item-section>
          <q-item-section side><q-btn flat label="Edit" :disable="busy" @click="edit(set)" /><q-btn flat color="negative" label="Delete" :disable="busy" @click="remove(set)" /></q-item-section>
        </q-item></q-list><p v-if="!busy && !sets.length">No credential sets configured.</p>
      </section>
      <q-form class="col-12 col-md-7 q-gutter-y-md" @submit="save">
        <h2 class="text-h6">{{ id ? "Edit credential set" : "New credential set" }}</h2>
        <q-input v-model="name" label="Name" :disable="busy" maxlength="200" required />
        <q-input v-model="endpoint" label="Endpoint URL" hint="For example: https://github.com/organization" :disable="busy" required />
        <q-select v-model="schemaId" label="Authentication schema" :options="schemas.map(s => ({ label: s.label, value: s.id }))" emit-value map-options :disable="busy || !!id" @update:model-value="parameters = {}" />
        <q-input v-for="field in schema?.fields || []" :key="field.name" v-model="parameters[field.name]" :label="field.label + (field.required ? ' *' : '')" :type="field.secret ? 'password' : 'text'" :autocomplete="field.secret ? 'new-password' : 'off'" :hint="configured.includes(field.name) && field.secret ? 'Stored securely; leave blank to keep' : undefined" :disable="busy" :required="field.required && !configured.includes(field.name)" />
        <q-checkbox v-for="field in (schema?.fields || []).filter(f => f.secret && !f.required && configured.includes(f.name))" :key="`clear-${field.name}`" v-model="clearFields" :val="field.name" :label="`Clear stored ${field.label}`" :disable="busy" />
        <q-checkbox v-model="allUsers" label="Allow all authenticated users" :disable="busy" />
        <q-input v-model="users" label="Authorized users" hint="Comma-separated usernames; administrators always have access" :disable="busy" />
        <q-input v-model="groups" label="Authorized groups" hint="Comma-separated group names" :disable="busy" />
        <q-input v-model="excludedUsers" label="Excluded users" hint="Comma-separated usernames; exclusions override usage grants" :disable="busy" />
        <q-input v-model="excludedGroups" label="Excluded groups" :disable="busy" />
        <q-btn type="submit" color="primary" label="Save credential set" :loading="busy" />
      </q-form>
    </div>
  </main>
</template>
