<script setup lang="ts">
import type { Declaration } from "../knowledge/model";
import { semanticColor } from "../knowledge/model";
const props = defineProps<{ nodes: Declaration[]; selected?: string; disabled?: boolean }>();
const emit = defineEmits<{ select: [string] }>();
function containsSelected(node: Declaration): boolean {
  return node.urn === props.selected || node.children.some(containsSelected);
}
</script>
<template>
  <ul class="worldview-tree">
    <li v-for="node in nodes" :key="node.urn">
      <details v-if="node.children.length" :open="containsSelected(node) || undefined">
        <summary><svg class="tree-chevron" viewBox="0 0 16 16" aria-hidden="true"><path d="m6 3 5 5-5 5" /></svg><button type="button" :disabled="disabled" :aria-current="selected === node.urn ? 'true' : undefined" :title="node.urn" @click="emit('select', node.urn)"><i :style="{ background: `var(--semantic-${semanticColor(node.type)})` }" />{{ node.label }}</button></summary>
        <WorldviewTree :nodes="node.children" :selected="selected" :disabled="disabled" @select="emit('select', $event)" />
      </details>
      <button v-else type="button" :disabled="disabled" :aria-current="selected === node.urn ? 'true' : undefined" :title="node.urn" @click="emit('select', node.urn)"><i :style="{ background: `var(--semantic-${semanticColor(node.type)})` }" />{{ node.label }}</button>
    </li>
  </ul>
</template>
<style scoped>
.worldview-tree { list-style: none; margin: 0; padding: 0 0 0 12px; }
.worldview-tree button { border: 0; background: transparent; text-align: left; width: 100%; display: inline-flex; align-items: center; gap: 8px; overflow-wrap: anywhere; min-width: 0; }
.worldview-tree button[aria-current] { background: var(--klab-selection); color: var(--klab-action); }
.worldview-tree summary { display: flex; align-items: center; list-style: none; cursor: pointer; }
.worldview-tree summary::-webkit-details-marker { display: none; }
.worldview-tree summary::marker { content: ''; }
.tree-chevron { width: 16px; height: 16px; flex: 0 0 16px; color: var(--klab-muted); fill: none; stroke: currentColor; stroke-width: 2; }
.worldview-tree details[open] > summary > .tree-chevron { transform: rotate(90deg); }
.worldview-tree i { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; }
</style>
