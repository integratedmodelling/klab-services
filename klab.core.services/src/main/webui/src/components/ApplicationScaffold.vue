<script setup lang="ts">
import {computed} from "vue";
import ApplicationNode from "./ApplicationNode.vue";
import type {UiSnapshot, WidgetInput} from "../services/application-ui";
const props = defineProps<{snapshot: UiSnapshot; disabled?: boolean}>();
const emit = defineEmits<{input: [event: WidgetInput]}>();
const theme = computed(() => props.snapshot.root.properties.theme || "light");
const regions = computed(() => {
  const nodes = props.snapshot.root.children;
  return {header: nodes.filter(n => n.type === "header"), footer: nodes.filter(n => n.type === "footer"),
    drawer: nodes.filter(n => n.type === "drawer"), main: nodes.filter(n => !["header", "footer", "drawer"].includes(n.type))};
});
</script>
<template>
  <section class="application-ui application-scaffold" :data-theme="theme" :lang="snapshot.root.properties.locale" :aria-busy="disabled">
      <header v-if="regions.header.length" class="application-header"><ApplicationNode v-for="node in regions.header" :key="node.id" :node="node" :disabled="disabled" :theme="theme" @input="emit('input', $event)" /></header>
      <aside v-if="regions.drawer.some(n => !n.properties.hidden)" class="application-drawer"><ApplicationNode v-for="node in regions.drawer" :key="node.id" :node="node" :disabled="disabled" :theme="theme" @input="emit('input', $event)" /></aside>
      <section class="application-main"><ApplicationNode v-for="node in regions.main" :key="node.id" :node="node" :disabled="disabled" :theme="theme" @input="emit('input', $event)" /></section>
      <footer v-if="regions.footer.length" class="application-footer"><ApplicationNode v-for="node in regions.footer" :key="node.id" :node="node" :disabled="disabled" :theme="theme" @input="emit('input', $event)" /></footer>
  </section>
</template>
