<script setup lang="ts">
import { computed, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
const props = defineProps<{ context: DashboardContext }>();
interface Proposal { id: string; namespace: string; alias: string; identity: string; status: string; approvedAlias?: string; message?: string }
interface Snapshot { revision: number; proposals: Proposal[]; codelists: Record<string, { entries: { code: string; value: string }[] }> }
const authority = ref(""), snapshot = ref<Snapshot | null>(null), busy = ref(false), error = ref("");
const selected = ref<Proposal | null>(null), approvedAlias = ref(""), rationale = ref("");
const administrator = computed(() => Array.isArray(props.context.capabilities?.permissions)
  && props.context.capabilities.permissions.includes("ADMINISTER"));
const authorities = computed(() => {
  const bindings = props.context.capabilities?.authorityBindings;
  return Array.isArray(bindings) ? [...new Set(bindings.map(b => b.localId as string))].sort() : [];
});
const namespace = ref(""), editing = ref<string | null>(null), codeAlias = ref(""), identity = ref("");
const namespaces = computed(() => Object.keys(snapshot.value?.codelists || {}).sort());
const entries = computed(() => snapshot.value?.codelists[namespace.value]?.entries || []);
function editable(alias: string) {
  const history = snapshot.value?.proposals.filter(p => p.namespace === namespace.value && p.approvedAlias === alias) || [];
  return history.some(p => p.status === "MANAGED")
    && !history.some(p => p.status === "ACCEPTED" || p.status === "REDIRECTED");
}
function resetEditor() { editing.value = null; codeAlias.value = ""; identity.value = ""; }
function editEntry(entry: { code: string; value: string }) {
  editing.value = entry.code; codeAlias.value = entry.code; identity.value = entry.value;
}
async function saveCode() {
  if (await command(editing.value === null ? "CREATE" : "UPDATE", {
    namespace: namespace.value, alias: editing.value ?? codeAlias.value,
    approvedAlias: codeAlias.value, identity: identity.value
  })) resetEditor();
}
watch(authorities, names => {
  if (!names.includes(authority.value)) { authority.value = names[0] || ""; snapshot.value = null; }
}, { immediate: true });
watch(namespace, resetEditor);
const pending = computed(() => snapshot.value?.proposals.filter(p => p.status === "PENDING") || []);
async function command(operation: string, values: Record<string, unknown> = {}) {
  if (busy.value || !authority.value) return false;
  busy.value = true; error.value = "";
  try {
    snapshot.value = await props.context.api.request<Snapshot>("api/v1/authority/codelists", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ operation, authority: authority.value, expectedRevision: snapshot.value?.revision || 0, ...values })
    });
    selected.value = null;
    if (!namespaces.value.includes(namespace.value)) namespace.value = namespaces.value[0] || "";
    return true;
  } catch (cause) { error.value = `${cause instanceof Error ? cause.message : String(cause)}. Refresh before retrying a decision.`; return false; }
  finally { busy.value = false; }
}
function select(proposal: Proposal) { selected.value = proposal; approvedAlias.value = proposal.alias; rationale.value = ""; }
async function review(decision: string) {
  if (!selected.value) return;
  await command("REVIEW", { namespace: selected.value.namespace, proposalId: selected.value.id,
    decision, approvedAlias: approvedAlias.value, message: rationale.value });
}
async function remove(namespace: string, alias: string) {
  if (!window.confirm(`Withdraw ${namespace}:${alias}? Existing source references will no longer resolve.`)) return;
  if (await command("DELETE", { namespace, alias, message: "Removed by administrator" })) resetEditor();
}
</script>
<template>
  <section class="codelists">
    <h2>Authority codelists</h2>
    <p v-if="!administrator">Administrator permission is required to review proposals.</p>
    <template v-else>
      <form @submit.prevent="command('LIST')" class="controls">
        <label>Authority <select aria-label="Authority" v-model="authority" :disabled="busy" required @change="snapshot = null; selected = null; resetEditor(); command('LIST')">
          <option v-for="name in authorities" :key="name" :value="name">{{ name }}</option>
        </select></label>
        <button :disabled="busy || !authority">{{ busy ? 'Loading…' : 'Refresh' }}</button>
      </form>
      <p v-if="!authorities.length">No authorities are configured in this worldview.</p>
      <p v-if="error" role="alert">{{ error }}</p>
      <template v-if="snapshot">
        <section class="review" aria-label="Code management">
          <h3>Code management</h3>
          <label>Codelist <select aria-label="Codelist" v-model="namespace" :disabled="busy">
            <option v-for="name in namespaces" :key="name" :value="name">{{ name }}</option>
          </select></label>
          <p v-if="!namespaces.length">No codelists are declared for this authority.</p>
          <template v-else>
            <p>Accepted community aliases and predefined entries are read-only.</p>
            <table><thead><tr><th>Alias</th><th>Authority code</th><th>Actions</th></tr></thead>
              <tbody><tr v-for="entry in entries" :key="entry.code">
                <td>{{ entry.code }}</td><td>{{ entry.value }}</td>
                <td v-if="editable(entry.code)">
                  <button :disabled="busy" @click="editEntry(entry)">Edit</button>
                  <button :disabled="busy" @click="remove(namespace, entry.code)">Remove</button>
                </td><td v-else>Read-only</td>
              </tr></tbody>
            </table>
            <p v-if="!entries.length">No approved aliases yet.</p>
            <form @submit.prevent="saveCode">
              <h4>{{ editing === null ? 'Add code' : 'Edit code' }}</h4>
              <label>Alias <input v-model="codeAlias" required pattern="[A-Z][A-Za-z0-9_]*" :disabled="busy" /></label>
              <label>Authority code <input v-model="identity" required :disabled="busy" /></label>
              <div class="controls">
                <button :disabled="busy">{{ editing === null ? 'Add code' : 'Save changes' }}</button>
                <button v-if="editing !== null" type="button" :disabled="busy" @click="resetEditor">Cancel</button>
              </div>
            </form>
          </template>
        </section>
        <h3>Pending proposals ({{ pending.length }})</h3>
        <table><thead><tr><th>Proposed alias</th><th>Authority code</th><th></th></tr></thead>
          <tbody><tr v-for="proposal in pending" :key="proposal.id">
            <td>{{ proposal.namespace }}:{{ proposal.alias }}</td><td>{{ proposal.identity }}</td>
            <td><button :disabled="busy" @click="select(proposal)">Review</button></td>
          </tr></tbody>
        </table>
        <form v-if="selected" @submit.prevent="review('ACCEPT')" class="review">
          <h3>{{ selected.namespace }}:{{ selected.alias }} → {{ authority }}:{{ selected.identity }}</h3>
          <label>Approved alias <input v-model="approvedAlias" required pattern="[A-Z][A-Za-z0-9_]*" :disabled="busy" /></label>
          <label>Reason or suggested alternative <textarea v-model="rationale" :disabled="busy" /></label>
          <div class="controls"><button :disabled="busy">Accept</button><button type="button" :disabled="busy" @click="review('REJECT')">Reject</button></div>
        </form>
        <details><summary>Review history</summary><p v-for="proposal in snapshot.proposals" :key="proposal.id">
          {{ proposal.namespace }}:{{ proposal.alias }} — {{ proposal.status }} {{ proposal.approvedAlias }} {{ proposal.message }}
        </p></details>
      </template>
    </template>
  </section>
</template>
<style scoped>
.codelists { padding: 24px; max-width: 1100px; margin: auto; }
.controls { display: flex; gap: 16px; align-items: end; flex-wrap: wrap; }
label { display: grid; gap: 6px; margin: 12px 0; }
input, select, textarea, button { padding: 9px 12px; border-radius: 5px; border: 1px solid #79998e; }
input, select, textarea { background: #fff; color: #17372e; }
button { cursor: pointer; } button:disabled { opacity: .5; cursor: default; }
table { width: 100%; border-collapse: collapse; } td, th { padding: 10px; text-align: left; border-bottom: 1px solid #79998e; }
.review { border: 1px solid #79998e; border-radius: 8px; padding: 16px; margin-top: 20px; }
[role=alert] { color: #ffb4ab; }
</style>
