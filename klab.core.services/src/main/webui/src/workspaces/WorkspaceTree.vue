<script setup lang="ts">
import type { WorkspaceTreeNode } from "./model";

defineProps<{ nodes: WorkspaceTreeNode[]; expanded: Set<string>; selected: string; disabled: boolean }>();
const emit = defineEmits<{ select: [WorkspaceTreeNode]; toggle: [string] }>();
const icons = {
  project: "folder", folder: "folder", document: "description",
  concept: "category", model: "schema", action: "bolt", statement: "code",
};
</script>

<template>
  <ul class="workspace-tree-branches">
    <li v-for="node in nodes" :key="node.key">
      <div class="workspace-node-line">
        <button v-if="node.children.length" type="button" class="workspace-disclosure"
                :aria-label="`${expanded.has(node.key) ? 'Collapse' : 'Expand'} ${node.label}`"
                :aria-expanded="expanded.has(node.key)" :disabled="disabled" @click="emit('toggle', node.key)">
          <q-icon :name="expanded.has(node.key) ? 'expand_more' : 'chevron_right'" size="17px"/>
        </button>
        <span v-else class="workspace-disclosure-space" aria-hidden="true"/>
        <button type="button" class="workspace-node" :aria-current="selected === node.key ? 'true' : undefined"
                :title="node.label" :disabled="disabled"
                @click="node.kind === 'folder' ? emit('toggle', node.key) : emit('select', node)">
          <q-icon :name="icons[node.kind]" size="16px"/>
          <span class="workspace-node-label">{{ node.label }}</span>
          <span v-if="node.errors || node.warnings || node.status" class="workspace-node-decorations">
            <span v-if="node.errors" class="workspace-node-error" role="img"
                  :aria-label="`${node.errors} ${node.errors === 1 ? 'error' : 'errors'}`">
              <q-icon name="error_outline" size="14px"/>
            </span>
            <span v-else-if="node.warnings" class="workspace-node-warning" role="img"
                  :aria-label="`${node.warnings} ${node.warnings === 1 ? 'warning' : 'warnings'}`">
              <q-icon name="warning_amber" size="14px"/>
            </span>
            <span v-if="node.status" class="workspace-node-status" :data-status="node.status"
                  :title="`Git ${node.status}`">{{ node.status }}</span>
          </span>
        </button>
      </div>
      <WorkspaceTree v-if="node.children.length && expanded.has(node.key)" :nodes="node.children"
                     :expanded="expanded" :selected="selected" :disabled="disabled"
                     @select="emit('select', $event)" @toggle="emit('toggle', $event)"/>
    </li>
  </ul>
</template>

<style scoped>
.workspace-tree-branches { list-style: none; padding: 0; margin: 0; }
li > .workspace-tree-branches {
  margin-inline-start: 11px;
  border-inline-start: 1px solid var(--klab-border);
  padding-inline-start: 3px;
}
.workspace-node-line { display: flex; min-width: 0; }
.workspace-disclosure, .workspace-disclosure-space { flex: 0 0 23px; }
.workspace-node-line .workspace-disclosure {
  display: grid; place-items: center; min-height: 30px; padding: 0; border: 0; background: transparent;
}
.workspace-node-line .workspace-node {
  display: flex; flex: 1; min-width: 0; min-height: 30px; align-items: center; gap: 6px;
  padding: 4px 5px; border-color: transparent; background: transparent; text-align: left; justify-content: flex-start;
}
.workspace-node-line button:hover:not(:disabled), .workspace-node[aria-current] {
  background: var(--klab-selection); color: var(--klab-action);
}
.workspace-node > .q-icon { flex: none; }
.workspace-node-label { min-width: 0; overflow-wrap: anywhere; }
.workspace-node-decorations { display: flex; flex: none; gap: 4px; align-items: center; margin-inline-start: auto; }
.workspace-node-error { color: var(--klab-danger); }
.workspace-node-warning { color: var(--klab-warning); }
.workspace-node-status { font-size: 11px; color: var(--klab-muted); }
.workspace-node-status[data-status="conflicted"] { color: var(--klab-danger); font-weight: 600; }
@media (pointer: coarse) {
  .workspace-node-line .workspace-node, .workspace-node-line .workspace-disclosure { min-height: 44px; }
  .workspace-disclosure, .workspace-disclosure-space { flex-basis: 34px; }
}
</style>
