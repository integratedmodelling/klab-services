<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import "monaco-editor/esm/vs/editor/editor.all.js";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { installKlabTheme, klabThemeName, registerLanguage } from "./language";
import { theme } from "../services/theme";

const props = defineProps<{
  saved: string; candidate: string; language: string; caption: string;
  applyBlock: string; canLock: boolean; busy: boolean; applying: boolean;
  historical?: boolean;
}>();
const emit = defineEmits<{ close: []; lock: []; apply: [] }>();
const host = ref<HTMLElement>();
const close = ref<HTMLButtonElement>();
const hasChanges = ref(false);
const narrow = ref(false);
const originalLabel = props.historical ? "Base revision" : "Saved ontology";
const modifiedLabel = props.historical ? "Applied revision" : "Candidate ontology";
const endingStyles = (text: string) => [...new Set(text.match(/\r\n|\r|\n/g) ?? [])].sort().join("|");
const lineEndingsDiffer = props.saved !== props.candidate
  && (endingStyles(props.saved) !== endingStyles(props.candidate)
    || props.saved.replace(/\r\n|\r/g, "\n") === props.candidate.replace(/\r\n|\r/g, "\n"));
let editor: monaco.editor.IStandaloneDiffEditor | undefined;
let models: monaco.editor.ITextModel[] = [];
let resize: ResizeObserver | undefined;
let frame: number | undefined;

onMounted(() => {
  (globalThis as typeof globalThis & { MonacoEnvironment?: monaco.Environment }).MonacoEnvironment ??= {
    getWorker: () => new EditorWorker(),
  };
  installKlabTheme();
  const language = registerLanguage(props.language);
  const original = monaco.editor.createModel(props.saved, language);
  const modified = monaco.editor.createModel(props.candidate, language);
  models = [original, modified];
  const container = host.value!;
  narrow.value = container.clientWidth < 700;
  editor = monaco.editor.createDiffEditor(container, {
    theme: klabThemeName(theme.value), renderSideBySide: !narrow.value,
    readOnly: true, originalEditable: false, ignoreTrimWhitespace: false,
    originalAriaLabel: originalLabel, modifiedAriaLabel: modifiedLabel,
    renderIndicators: true, minimap: { enabled: false }, scrollBeyondLastLine: false,
    renderOverviewRuler: false, useInlineViewWhenSpaceIsLimited: false,
  });
  editor.onDidUpdateDiff(() => { hasChanges.value = Boolean(editor?.getLineChanges()?.length); });
  editor.setModel({ original, modified });
  resize = new ResizeObserver(() => {
    if (frame !== undefined) return;
    frame = window.requestAnimationFrame(() => {
      frame = undefined;
      const inline = container.clientWidth < 700;
      if (narrow.value !== inline) {
        narrow.value = inline;
        editor?.updateOptions({ renderSideBySide: !inline });
      }
      editor?.layout({ width: container.clientWidth, height: container.clientHeight });
    });
  });
  resize.observe(container);
  close.value?.focus();
});
watch(theme, value => monaco.editor.setTheme(klabThemeName(value)));
onBeforeUnmount(() => {
  resize?.disconnect();
  if (frame !== undefined) window.cancelAnimationFrame(frame);
  editor?.dispose();
  models.forEach(model => model.dispose());
});
</script>

<template>
  <section class="workspace-candidate-diff" :aria-label="historical ? 'Applied ontology comparison' : 'Candidate ontology comparison'">
    <header>
      <div><h3>{{ historical ? "Applied changes" : "Ontology comparison" }}</h3><p>{{ caption }}</p></div>
      <nav aria-label="Comparison actions">
        <button type="button" aria-label="Previous change" title="Previous change" :disabled="!hasChanges" @click="editor?.goToDiff('previous')">
          <q-icon name="arrow_upward" size="16px"/>
        </button>
        <button type="button" aria-label="Next change" title="Next change" :disabled="!hasChanges" @click="editor?.goToDiff('next')">
          <q-icon name="arrow_downward" size="16px"/>
        </button>
        <button ref="close" type="button" :disabled="busy" @click="emit('close')">{{ historical ? "Back to history" : "Back to review" }}</button>
        <button v-if="!historical && canLock" type="button" :disabled="busy" @click="emit('lock')">Lock to edit</button>
        <button v-if="!historical" type="button" class="workspace-diff-apply" :disabled="busy || Boolean(applyBlock)" @click="emit('apply')">
          {{ applying ? "Applying…" : "Apply candidate" }}
        </button>
      </nav>
    </header>
    <p v-if="applyBlock" class="workspace-diff-note" role="status">{{ applyBlock }}</p>
    <p v-if="lineEndingsDiffer" class="workspace-diff-note" role="status">The files differ in line endings.</p>
    <div class="workspace-diff-labels" :class="{ 'workspace-diff-inline': narrow }">
      <span>{{ narrow ? (historical ? "Removed from base" : "Removed from saved") : originalLabel }}</span>
      <span>{{ narrow ? (historical ? "Added in applied revision" : "Added in candidate") : modifiedLabel }}</span>
    </div>
    <div ref="host" class="workspace-diff-host"/>
  </section>
</template>

<style scoped>
.workspace-candidate-diff { display: flex; flex: 1; flex-direction: column; min-width: 0; min-height: 0; }
.workspace-candidate-diff header, .workspace-candidate-diff nav { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.workspace-candidate-diff header { justify-content: space-between; padding: 8px; background: var(--klab-surface); }
.workspace-candidate-diff h3 { margin: 0; font: 600 15px/1.35 var(--klab-font); }
.workspace-candidate-diff p { margin: 0; color: var(--klab-muted); }
.workspace-diff-labels { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr));
  border-block: 1px solid var(--klab-border); color: var(--klab-muted); background: var(--klab-surface); }
.workspace-diff-labels span { padding: 4px 8px; }
.workspace-diff-labels:not(.workspace-diff-inline) span + span { border-inline-start: 1px solid var(--klab-border); }
.workspace-diff-inline { display: flex; gap: 4px; }
.workspace-diff-note { padding-inline: 8px; }
.workspace-candidate-diff .workspace-diff-apply:not(:disabled) {
  color: var(--klab-action-ink); background: var(--klab-action); border-color: var(--klab-action);
}
.workspace-diff-host { flex: 1; min-width: 0; min-height: 0; overflow: hidden; }
</style>
