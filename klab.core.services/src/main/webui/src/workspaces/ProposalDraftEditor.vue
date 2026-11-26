<script setup lang="ts">
import { computed, ref } from "vue";
import ProposalFieldEditor from "./ProposalFieldEditor.vue";
import { fieldLabel, initialField, resolveField, type ProposalEditorConfig, type ProposalField } from "./proposal-schema";
import { pathLabel, proposalChanges, proposalValue, record, sameProposalValue, structuredProposalCopy,
  type ProposalConflict, type ProposalPath, type RecordValue, type ReviewBundle } from "./project-review";

const props = defineProps<{
  document: RecordValue;
  baseline: RecordValue;
  inputs: { bundle: ReviewBundle; document: RecordValue }[];
  conflicts: ProposalConflict[];
  resolutions: ReadonlyMap<ProposalConflict, unknown>;
  config: ProposalEditorConfig;
  statement?: string;
  canLocate: boolean;
  disabled: boolean;
}>();
const emit = defineEmits<{ field: [key: string, value: unknown]; resolve: [conflict: ProposalConflict, value: unknown];
  locate: [statement?: string] }>();
type View = "Resolve" | "Edit draft" | "Merged changes" | "Compare inputs";
const view = ref<View>(props.conflicts.length ? "Resolve" : "Edit draft");
const views: View[] = ["Resolve", "Edit draft", "Merged changes", "Compare inputs"];
const leftIndex = ref(0);
const rightIndex = ref(props.inputs.length > 1 ? 1 : 0);
const selectedLeft = computed(() => props.inputs[leftIndex.value]);
const selectedRight = computed(() => props.inputs[rightIndex.value]);
const comparison = computed(() => selectedLeft.value && selectedRight.value
  ? proposalChanges(selectedLeft.value.document, selectedRight.value.document, props.config.managed) : []);
const changes = computed(() => proposalChanges(props.baseline, props.document, props.config.managed));
const proposal = computed(() => record(props.document.proposal) ? props.document.proposal : {});
const fields = computed(() => resolveField(props.config, props.config.fields, proposal.value).properties ?? {});
const draftKeys = computed(() => Object.keys(proposal.value).filter(key => !props.config.managed.has(key)));
const available = computed(() => Object.keys(fields.value).filter(key => !props.config.managed.has(key) && !Object.hasOwn(proposal.value, key)));
const missing = ref("");
const custom = ref<{ conflict: ProposalConflict; value: unknown }>();
function resolved(conflict: ProposalConflict) {
  return props.resolutions.has(conflict)
    && sameProposalValue(props.resolutions.get(conflict), proposalValue(props.document, conflict.path));
}
const remaining = computed(() => props.conflicts.filter(conflict => !resolved(conflict)).length);
function showValue(value: unknown) {
  if (value === undefined) return "No value";
  if (value === null) return "Explicitly empty";
  return typeof value === "string" ? value : JSON.stringify(value, null, 2);
}
function fieldAt(path: ProposalPath) {
  let shape: ProposalField | undefined = props.config.fields;
  for (let i = 1; i < path.length; i++) {
    const member = path[i];
    shape = resolveField(props.config, shape, proposalValue(props.document, path.slice(0, i)));
    shape = typeof member === "string" ? shape.properties?.[member] : shape.items;
  }
  return shape;
}
function customValue(conflict: ProposalConflict) {
  custom.value = { conflict, value: structuredProposalCopy(proposalValue(props.document, conflict.path)) };
}
</script>

<template>
  <div class="proposal-draft-editor">
    <nav class="proposal-draft-nav" aria-label="Proposal draft views">
      <button v-for="item in views" :key="item" type="button" :aria-pressed="view === item"
              :disabled="item === 'Compare inputs' && !inputs.length || item === 'Resolve' && !conflicts.length"
              @click="view = item">{{ item }}<template v-if="item === 'Resolve' && remaining"> ({{ remaining }})</template></button>
    </nav>
    <div v-if="view === 'Resolve'">
      <p role="status">{{ remaining ? `${remaining} of ${conflicts.length} conflicts to resolve` : "All conflicts resolved" }}</p>
      <details v-for="conflict in conflicts" :key="JSON.stringify(conflict.path)" class="proposal-section" :open="!resolved(conflict)">
        <summary>{{ pathLabel(conflict.path) }} <span>{{ resolved(conflict) ? 'Resolved' : 'Needs a decision' }}</span></summary>
        <div class="proposal-columns">
          <div v-for="(choice, index) in conflict.choices" :key="index" class="proposal-choice">
            <p v-for="input in choice.inputs" :key="input.path"><strong>{{ input.manifest.contributor }}</strong> · {{ input.manifest.id }}</p>
            <pre>{{ showValue(choice.value) }}</pre>
            <button type="button" :disabled="disabled" :aria-label="'Use value from ' + choice.inputs.map(input => input.manifest.id).join(', ')"
                    @click="emit('resolve', conflict, choice.value); custom = undefined">Use this value</button>
          </div>
        </div>
        <div class="proposal-actions">
          <button type="button" :disabled="disabled" @click="customValue(conflict)">Enter a different value</button>
          <button type="button" :disabled="disabled" @click="emit('resolve', conflict, proposalValue(document, conflict.path)); custom = undefined">Use current draft value</button>
        </div>
        <div v-if="custom?.conflict === conflict" class="proposal-section">
          <ProposalFieldEditor v-model="custom.value" label="Custom resolution" :config="config" :field="fieldAt(conflict.path)" :locked="disabled"/>
          <div class="proposal-actions">
            <button type="button" :disabled="disabled" @click="emit('resolve', conflict, custom.value); custom = undefined">Apply resolution</button>
            <button type="button" @click="custom = undefined">Cancel</button>
          </div>
        </div>
        <p v-if="resolved(conflict)">Resolved value</p>
        <pre v-if="resolved(conflict)">{{ showValue(proposalValue(document, conflict.path)) }}</pre>
      </details>
    </div>
    <div v-else-if="view === 'Edit draft'">
      <template v-for="key in draftKeys" :key="key">
        <details v-if="record(proposal[key]) || Array.isArray(proposal[key])" class="proposal-section" :open="key === 'assets'">
          <summary>{{ fieldLabel(key) }} <span v-if="Array.isArray(proposal[key])">({{ (proposal[key] as unknown[]).length }})</span></summary>
          <ProposalFieldEditor :label="fieldLabel(key)" :model-value="proposal[key]" :config="config" :field="fields[key]"
                               :statement="statement" :locked="disabled" :can-locate="canLocate"
                               @update:model-value="emit('field', key, $event)" @locate="emit('locate', $event)"/>
        </details>
        <ProposalFieldEditor v-else :label="fieldLabel(key)" :model-value="proposal[key]" :config="config" :field="fields[key]"
                             :locked="disabled" @update:model-value="emit('field', key, $event)"/>
      </template>
      <div v-if="available.length" class="proposal-actions">
        <label>Optional field <select v-model="missing">
          <option value="">Choose a field</option>
          <option v-for="field in available" :key="field" :value="field">{{ fieldLabel(field) }}</option>
        </select></label>
        <button type="button" :disabled="disabled || !missing"
                @click="emit('field', missing, initialField(config, fields[missing], statement)); missing = ''">Add field</button>
      </div>
    </div>
    <div v-else-if="view === 'Merged changes'">
      <p v-if="!changes.length">No proposal field changes yet.</p>
      <p v-else>Compared with {{ inputs[0] ? inputs[0].bundle.manifest.id + ' · ' + inputs[0].bundle.manifest.contributor : 'the initial draft' }}.</p>
      <button v-if="statement && canLocate" type="button" @click="emit('locate', statement)">Show reviewed declaration in Monaco</button>
      <details v-for="change in changes" :key="JSON.stringify(change.path)" class="proposal-section" open>
        <summary>{{ pathLabel(change.path) }}</summary>
        <div class="proposal-columns">
          <div><strong>Before</strong><pre>{{ showValue(change.before) }}</pre></div>
          <div><strong>Merged draft</strong><pre>{{ showValue(change.after) }}</pre></div>
        </div>
        <button v-if="change.statement && canLocate" type="button" @click="emit('locate', change.statement)">Show in editor</button>
      </details>
    </div>
    <div v-else>
      <ul class="proposal-inputs">
        <li v-for="input in inputs" :key="input.bundle.path">
          <strong>{{ input.bundle.manifest.contributor }}</strong> · {{ input.bundle.manifest.id }}
        </li>
      </ul>
      <div v-if="inputs.length > 1" class="proposal-columns">
        <label>First input
          <select v-model="leftIndex"><option v-for="(input, index) in inputs" :key="input.bundle.path" :value="index">
            {{ input.bundle.manifest.contributor }} · {{ input.bundle.manifest.id }}
          </option></select>
        </label>
        <label>Second input
          <select v-model="rightIndex"><option v-for="(input, index) in inputs" :key="input.bundle.path" :value="index">
            {{ input.bundle.manifest.contributor }} · {{ input.bundle.manifest.id }}
          </option></select>
        </label>
      </div>
      <p v-if="inputs.length > 1 && !comparison.length">No field differences between these inputs.</p>
      <details v-for="change in comparison" :key="JSON.stringify(change.path)" class="proposal-section" open>
        <summary>{{ pathLabel(change.path) }}</summary>
        <div class="proposal-columns">
          <div><strong>{{ selectedLeft?.bundle.manifest.contributor }} · {{ selectedLeft?.bundle.manifest.id }}</strong><pre>{{ showValue(change.before) }}</pre></div>
          <div><strong>{{ selectedRight?.bundle.manifest.contributor }} · {{ selectedRight?.bundle.manifest.id }}</strong><pre>{{ showValue(change.after) }}</pre></div>
        </div>
      </details>
    </div>
  </div>
</template>

<style scoped>
.proposal-draft-editor { display: grid; gap: 10px; min-width: 0; }
.proposal-draft-editor p { margin: 4px 0; }
.proposal-draft-nav, .proposal-actions { display: flex; align-items: center; flex-wrap: wrap; gap: 5px; }
.proposal-draft-nav { padding-block: 4px 8px; border-bottom: 1px solid var(--klab-border); }
.proposal-draft-nav button[aria-pressed="true"] { color: var(--klab-action); background: var(--klab-selection); }
.proposal-section { padding-block: 8px; border-bottom: 1px solid var(--klab-border); min-width: 0; }
.proposal-section summary { cursor: pointer; font-weight: 600; }
.proposal-section summary span { font-weight: 400; color: var(--klab-muted); margin-inline-start: 4px; }
.proposal-columns { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 145px), 1fr)); gap: 8px; margin-block: 7px; }
.proposal-columns > * { min-width: 0; }
.proposal-columns > label { display: grid; gap: 3px; }
.proposal-columns select { width: 100%; }
.proposal-choice { padding: 6px; border: 1px solid var(--klab-border); }
.proposal-columns pre, .proposal-section > pre { margin: 5px 0; padding: 5px; background: var(--klab-surface);
  white-space: pre-wrap; overflow-wrap: anywhere; font: inherit; }
.proposal-inputs { padding-inline-start: 15px; }
</style>
