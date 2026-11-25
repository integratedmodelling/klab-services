<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import "monaco-editor/esm/vs/editor/editor.all.js";
import EditorWorker from "monaco-editor/esm/vs/editor/editor.worker?worker";
import { installKlabTheme, klabThemeName, registerLanguage } from "./language";
import { WorkspaceLanguageSession, type LanguageEndpoint, type LanguageStatus } from "./language-session";
import { theme } from "../services/theme";
import type { Diagnostic, ReviewGlyph, Source } from "./model";

const props = defineProps<{
  source: Source;
  value: string;
  readOnly: boolean;
  lineNumbers: boolean;
  minimap: boolean;
  semanticDiagnostics: Diagnostic[];
  draftDiagnostics: Diagnostic[];
  languageEndpoint?: LanguageEndpoint;
  reviewGlyphs?: ReviewGlyph[];
  extensions?: readonly ((editor: monaco.editor.IStandaloneCodeEditor) => monaco.IDisposable)[];
}>();
const emit = defineEmits<{
  change: [string];
  cursor: [string];
  save: [];
  review: [string];
  languageStatus: [LanguageStatus | undefined];
  languageDiagnostics: [Diagnostic[]];
}>();
const host = ref<HTMLElement>();
let editor: monaco.editor.IStandaloneCodeEditor | undefined;
let model: monaco.editor.ITextModel | undefined;
let glyphs: monaco.editor.IEditorDecorationsCollection | undefined;
let highlighting: monaco.editor.IEditorDecorationsCollection | undefined;
let extensionHandles: monaco.IDisposable[] = [];
let installingSource = false;
let resizeObserver: ResizeObserver | undefined;
let layoutFrame: number | undefined;
let languageSession: WorkspaceLanguageSession | undefined;
let completionProvider: monaco.IDisposable | undefined;
let languageDiagnostics: Diagnostic[] = [];
// Same lexical exclusions as the IDE concept highlighter.
const conceptSpans = /\/\/[^\r\n]*|\/\*[\s\S]*?(?:\*\/|$)|"(?:\\[\s\S]|[^"\\])*(?:"|$)|'(?:\\[\s\S]|[^'\\])*(?:'|$)|\[(?:\\[\s\S]|[^\]\\])*(?:\]|$)|(?:^|[^\w.:-])([a-z_]+(?:\.[a-z_-]+)*:[A-Z][A-Za-z_0-9]*)\b/g;
const semanticCategories = new Set(["DOMAIN", "CONFIGURATION", "EVENT", "EXTENT", "PROCESS", "QUALITY", "RELATIONSHIP", "TRAIT", "ROLE", "SUBJECT"]);
let highlightTimer: number | undefined;

function highlightConcepts() {
  window.clearTimeout(highlightTimer);
  highlightTimer = window.setTimeout(() => {
    if (!model) return;
    const decorations: monaco.editor.IModelDeltaDecoration[] = [];
    for (const match of model.getValue().matchAll(conceptSpans)) {
      const urn = match[1], category = props.source.concepts?.[urn];
      if (!category || !semanticCategories.has(category)) continue;
      const offset = match.index + match[0].length - urn.length;
      const start = model.getPositionAt(offset);
      const end = model.getPositionAt(offset + urn.length);
      decorations.push({
        range: monaco.Range.fromPositions(start, end),
        options: { inlineClassName: `workspace-semantic-${category}` },
      });
    }
    highlighting?.set(decorations);
  }, 150);
}

function markers(diagnostics: Diagnostic[], text: string) {
  if (!model) return [];
  return diagnostics.map(diagnostic => {
    // Resources offsets refer to the original text, including its line endings.
    const offset = Math.max(0, diagnostic.offset ?? 0);
    const start = model!.getPositionAt(text.slice(0, offset).replace(/\r\n|\r/g, "\n").length);
    const end = model!.getPositionAt(text.slice(0, offset + Math.max(1, diagnostic.length ?? 1))
      .replace(/\r\n|\r/g, "\n").length);
    const severity = diagnostic.level === "Error" ? monaco.MarkerSeverity.Error
      : diagnostic.level === "Warning" ? monaco.MarkerSeverity.Warning
      : monaco.MarkerSeverity.Info;
    return { startLineNumber: start.lineNumber, startColumn: start.column, endLineNumber: end.lineNumber,
      endColumn: end.column, message: diagnostic.message, severity };
  });
}

function setMarkers() {
  if (!model) return;
  const saved = props.source.text.replace(/\r\n|\r/g, "\n");
  const matches = model.getValue() === saved;
  monaco.editor.setModelMarkers(model, "java-bridge", matches ? markers(props.source.diagnostics, props.source.text) : []);
  monaco.editor.setModelMarkers(model, "klab-semantics", matches ? markers(props.semanticDiagnostics, props.source.text) : []);
  monaco.editor.setModelMarkers(model, "klab-draft", !matches && model.getValue() === props.value
    ? markers(props.draftDiagnostics, props.value) : []);
  monaco.editor.setModelMarkers(model, "kim-lsp", markers(languageDiagnostics, model.getValue()));
}

function connectLanguageServer() {
  languageSession?.dispose();
  completionProvider?.dispose();
  languageSession = undefined;
  completionProvider = undefined;
  languageDiagnostics = [];
  emit("languageDiagnostics", []);
  setMarkers();
  const endpoint = props.languageEndpoint, current = model;
  if (!endpoint || !current) { emit("languageStatus", undefined); return; }
  const session = new WorkspaceLanguageSession(endpoint,
    () => ({ version: current.getVersionId(), text: current.getValue() }),
    status => emit("languageStatus", status), diagnostics => {
      languageDiagnostics = diagnostics;
      setMarkers();
      emit("languageDiagnostics", diagnostics);
    });
  languageSession = session;
  completionProvider = monaco.languages.registerCompletionItemProvider(current.getLanguageId(), {
    triggerCharacters: [":", "."],
    async provideCompletionItems(candidate, position, _context, token) {
      if (candidate !== current || token.isCancellationRequested || props.readOnly) return { suggestions: [] };
      const request = new AbortController();
      const canceled = token.onCancellationRequested(() => request.abort());
      try {
        const items = await session.complete(position.lineNumber - 1, position.column - 1, request.signal);
        const word = candidate.getWordUntilPosition(position);
        return { suggestions: items.map(item => ({
          label: item.label,
          detail: item.detail,
          kind: monaco.languages.CompletionItemKind.Text,
          insertText: item.text ?? item.label,
          insertTextRules: item.snippet ? monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet : undefined,
          range: item.range ? new monaco.Range(item.range.start.line + 1, item.range.start.character + 1,
            item.range.end.line + 1, item.range.end.character + 1)
            : new monaco.Range(position.lineNumber, word.startColumn, position.lineNumber, word.endColumn),
        })) };
      } finally { canceled.dispose(); }
    },
  });
}

function setGlyphs() {
  if (!model || !editor) return;
  const items = (props.reviewGlyphs ?? []).filter(item => item.line >= 1 && item.line <= model!.getLineCount());
  editor.updateOptions({ glyphMargin: items.length > 0 });
  glyphs?.set(items.map(item => ({
    range: new monaco.Range(item.line, 1, item.line, 1),
    options: { glyphMarginClassName: "workspace-review-glyph", glyphMarginHoverMessage: { value: item.tooltip } },
  })));
}

function setExtensions() {
  extensionHandles.forEach(handle => handle.dispose());
  extensionHandles = editor ? (props.extensions ?? []).map(extension => extension(editor!)) : [];
}

onMounted(() => {
  (globalThis as typeof globalThis & { MonacoEnvironment?: monaco.Environment }).MonacoEnvironment ??= {
    getWorker: () => new EditorWorker(),
  };
  installKlabTheme();
  model = monaco.editor.createModel(props.value, registerLanguage(props.source.document.language));
  model.setEOL(monaco.editor.EndOfLineSequence.LF);
  editor = monaco.editor.create(host.value!, {
    model, theme: klabThemeName(theme.value),
    ariaLabel: `Source for ${props.source.document.label}`, readOnly: props.readOnly,
    renderValidationDecorations: "on", minimap: { enabled: props.minimap },
    lineNumbers: props.lineNumbers ? "on" : "off", scrollBeyondLastLine: false,
    tabSize: 2, contextmenu: false, fixedOverflowWidgets: true,
  });
  // Lay out after Vue's split/fullscreen resize has settled, outside the observer callback.
  resizeObserver = new ResizeObserver(() => {
    if (layoutFrame !== undefined) return;
    layoutFrame = window.requestAnimationFrame(() => {
      layoutFrame = undefined;
      editor?.layout();
    });
  });
  resizeObserver.observe(host.value!);
  glyphs = editor.createDecorationsCollection();
  highlighting = editor.createDecorationsCollection();
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => emit("save"));
  editor.onDidChangeModelContent(() => {
    if (!installingSource) emit("change", model!.getValue());
    languageSession?.changed();
    setMarkers();
    highlightConcepts();
  });
  editor.onDidChangeCursorPosition(({ position }) => emit("cursor", `Ln ${position.lineNumber}, Col ${position.column}`));
  editor.onMouseDown(({ target }) => {
    if (target.type !== monaco.editor.MouseTargetType.GUTTER_GLYPH_MARGIN) return;
    const item = props.reviewGlyphs?.find(item => item.line === target.position?.lineNumber);
    if (item) emit("review", item.id);
  });
  setMarkers();
  highlightConcepts();
  setGlyphs();
  setExtensions();
  connectLanguageServer();
});

watch(() => [props.readOnly, props.minimap, props.lineNumbers], () => editor?.updateOptions({
  readOnly: props.readOnly, minimap: { enabled: props.minimap }, lineNumbers: props.lineNumbers ? "on" : "off",
}));
watch(() => props.value, value => {
  if (!model || model.getValue() === value) return;
  installingSource = true;
  model.setValue(value);
  installingSource = false;
});
watch(() => [props.source, props.semanticDiagnostics, props.draftDiagnostics], setMarkers);
watch(() => props.source, highlightConcepts);
watch(() => props.reviewGlyphs, setGlyphs);
watch(() => props.extensions, setExtensions);
watch(() => props.languageEndpoint?.url, connectLanguageServer);
watch(theme, value => monaco.editor.setTheme(klabThemeName(value)));

defineExpose({
  find: () => { editor?.focus(); void editor?.getAction("actions.find")?.run(); },
  format: () => { if (!props.readOnly) void editor?.getAction("editor.action.formatDocument")?.run(); },
  jump: (offset: number, normalizedOffsets = false) => {
    if (!model || !editor) return;
    const saved = props.source.text.replace(/\r\n|\r/g, "\n");
    const text = !normalizedOffsets && props.value === saved ? props.source.text : props.value;
    const position = model.getPositionAt(text.slice(0, Math.max(0, offset)).replace(/\r\n|\r/g, "\n").length);
    editor.revealPositionInCenter(position);
    editor.setPosition(position);
    editor.focus();
  },
});

onBeforeUnmount(() => {
  window.clearTimeout(highlightTimer);
  resizeObserver?.disconnect();
  if (layoutFrame !== undefined) window.cancelAnimationFrame(layoutFrame);
  extensionHandles.forEach(handle => handle.dispose());
  completionProvider?.dispose();
  languageSession?.dispose();
  editor?.dispose();
  model?.dispose();
});
</script>

<template><div ref="host" class="workspace-monaco"/></template>

<style>
.workspace-monaco { position: relative; min-width: 0; min-height: 0; height: 100%; }
.workspace-review-glyph::before { content: ""; display: block; width: 10px; height: 10px; margin: 5px auto; border-radius: 50%; background: var(--klab-action); }
.workspace-semantic-DOMAIN { color: var(--semantic-DOMAIN) !important; }
.workspace-semantic-CONFIGURATION { color: var(--semantic-CONFIGURATION) !important; }
.workspace-semantic-EVENT { color: var(--semantic-EVENT) !important; }
.workspace-semantic-EXTENT { color: var(--semantic-EXTENT) !important; }
.workspace-semantic-PROCESS { color: var(--semantic-PROCESS) !important; }
.workspace-semantic-QUALITY { color: var(--semantic-QUALITY) !important; }
.workspace-semantic-RELATIONSHIP { color: var(--semantic-RELATIONSHIP) !important; }
.workspace-semantic-TRAIT { color: var(--semantic-TRAIT) !important; }
.workspace-semantic-ROLE { color: var(--semantic-ROLE) !important; }
.workspace-semantic-SUBJECT { color: var(--semantic-SUBJECT) !important; }
</style>
