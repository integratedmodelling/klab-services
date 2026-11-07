<script setup lang="ts">
import { computed } from "vue";
const props = withDefaults(defineProps<{ value: unknown; depth?: number }>(), { depth: 0 });
const entries = computed(() => props.value && typeof props.value === "object" && !Array.isArray(props.value)
  ? Object.entries(props.value).slice(0, 100) : []);
const items = computed(() => Array.isArray(props.value) ? props.value.slice(0, 100) : []);
const label = (key: string) => key.replaceAll("_", " ").replace(/([a-z])([A-Z])/g, "$1 $2");
</script>
<template>
  <p v-if="depth > 8" class="reader-note">Further detail is available in the exact source.</p>
  <template v-else-if="Array.isArray(value)">
    <p v-if="!value.length" class="reader-note">No entries supplied.</p>
    <ol v-else class="proposal-items"><li v-for="(item, index) in items" :key="index"><ProposalValue :value="item" :depth="depth + 1" /></li></ol>
    <p v-if="value.length > 100" class="reader-note">Showing the first 100 entries. The exact source contains every entry.</p>
  </template>
  <dl v-else-if="value && typeof value === 'object'" class="proposal-fields">
    <div v-for="[key, item] in entries" :key="key"><dt>{{ label(key) }}</dt><dd><ProposalValue :value="item" :depth="depth + 1" /></dd></div>
    <p v-if="Object.keys(value).length > 100" class="reader-note">Additional fields are available in the exact source.</p>
  </dl>
  <p v-else class="proposal-value">{{ value === null ? 'Not supplied' : String(value) }}</p>
</template>
<style scoped>
.proposal-fields { margin: 0; display: grid; gap: 24px; }
.proposal-fields dt { font-size: .875rem; font-weight: 600; text-transform: capitalize; margin-block-end: 6px; color: #53667b; }
.proposal-fields dd { margin: 0; }
.proposal-value { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; text-wrap: pretty; }
.proposal-items { padding-inline-start: 24px; display: grid; gap: 16px; margin: 0; }
.reader-note { color: #53667b; font-size: .875rem; }
</style>
