<script setup lang="ts">
import { computed, defineAsyncComponent, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import "@klab-dashboard/styles/theme.css";
import "@klab-dashboard/knowledge/knowledge.css";
import type { LanguageStatus } from "@klab-dashboard/workspaces/language-session";
import {
  documentGitStatus, documentGroups, folderGitStatus, gitDecoration,
  type Diagnostic, type DocumentItem, type EditorExtension, type Project, type ReviewGlyph, type Source, type Workspace,
} from "@klab-dashboard/workspaces/model";

const Monaco = defineAsyncComponent(() => import("@klab-dashboard/workspaces/WorkspaceMonaco.vue"));
const props = defineProps<{
  context: DashboardContext;
  reviewGlyphs?: (source: Source) => ReviewGlyph[];
  editorExtensions?: readonly EditorExtension[];
}>();
const emit = defineEmits<{ review: [Source, string] }>();
const root = ref<HTMLElement>();
const editor = ref<{ find(): void; format(): void; jump(offset: number, normalizedOffsets?: boolean): void }>();
const catalog = ref<Workspace[]>([]);
const workspace = ref("");
const projects = ref<Record<string, Project>>({});
const expandedProjects = ref(new Set<string>());
const projectName = ref("");
const project = computed(() => projects.value[projectName.value]);
const workspaceTree = computed(() => {
  const current = catalog.value.find(item => item.urn === workspace.value);
  return (current?.projects ?? []).map(name => {
    const loaded = projects.value[name];
    const repository = loaded?.repository;
    const status = gitDecoration(loaded ? repository?.status : current?.repositoryStatuses?.[name]);
    const groups = loaded ? documentGroups.map(group => {
      const documents = loaded.documents.filter(document => group.types.includes(document.type))
        .map(document => ({ document, status: documentGitStatus(repository, document.path) }));
      return {
        label: group.label,
        documents: documents.map(({ document, status }) => ({ document, status: gitDecoration(status) })),
        status: gitDecoration(folderGitStatus(repository, group.directory, documents)),
      };
    }).filter(group => group.documents.length || group.status) : [];
    return { name, status, groups };
  });
});
const source = ref<Source>();
const text = ref("");
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const feedback = ref("");
const pending = ref<(() => void)>();
const cursor = ref("Ln 1, Col 1");
const navigatorVisible = ref(true);
const split = ref(false);
const fullscreen = ref(false);
const lineNumbers = ref(true);
const minimap = ref(false);
const semantic = ref<{ status: string; reason?: string; diagnostics: Diagnostic[] }>();
const semanticPending = ref(false);
const draftDiagnostics = ref<Diagnostic[]>([]);
const draftStatus = ref("");
const languageStatus = ref<LanguageStatus>();
const languageDiagnostics = ref<Diagnostic[]>([]);
const languageEndpoint = computed(() => source.value && project.value?.editable
  ? { api: props.context.api, url: url("/source/lsp", { document: source.value.document.id }) } : undefined);
const settings = ref({ metadata: "", permissions: "", definedWorldview: "", snapshot: false });
const baselineSettings = ref("");
const operation = ref("");
const parameter = ref("");
const confirmDiscard = ref(false);
const repositoryOpen = ref(false);
const dirty = computed(() => source.value
  ? text.value !== normalize(source.value.text)
  : Boolean(project.value && baselineSettings.value && JSON.stringify(settings.value) !== baselineSettings.value));
const diagnostics = computed(() => {
  const items = dirty.value ? [...draftDiagnostics.value, ...languageDiagnostics.value]
    : [...(source.value?.diagnostics ?? []), ...languageDiagnostics.value, ...(semantic.value?.diagnostics ?? [])];
  return [...new Map(items.map(item => [JSON.stringify([item.level, item.message, item.offset]), item])).values()];
});
const errors = computed(() => diagnostics.value.filter(item => item.level === "Error").length);
const warnings = computed(() => diagnostics.value.filter(item => item.level === "Warning").length);
const repositoryActions = [
  { value: "GET_LATEST", label: "Get latest", remote: true },
  { value: "SAVE_CHANGES", label: "Commit changes", remote: false },
  { value: "PUBLISH_CHANGES", label: "Publish commits", remote: true },
  { value: "SYNC_AND_PUBLISH", label: "Sync and publish", remote: true },
  { value: "COMMIT_AND_SWITCH", label: "Commit and switch branch", remote: false },
  { value: "MERGE_CHANGES_FROM", label: "Merge from branch", remote: false },
  { value: "DISCARD_LOCAL_CHANGES", label: "Discard uncommitted changes", remote: false },
];
const needsBranch = computed(() => ["COMMIT_AND_SWITCH", "MERGE_CHANGES_FROM"].includes(operation.value));
const acceptsMessage = computed(() => ["SAVE_CHANGES", "SYNC_AND_PUBLISH"].includes(operation.value));
let active = true;
let semanticRequest: AbortController | undefined;
let projectFullscreenFallback = false;
let originalOverflow = "";
let inertSiblings: { element: HTMLElement; inert: boolean }[] = [];

function normalize(value: string) { return value.replace(/\r\n|\r/g, "\n"); }
function url(path = "", extra: Record<string, string> = {}) {
  const query = new URLSearchParams({ workspace: workspace.value, project: projectName.value, ...extra });
  return `api/v1/workspaces${path}?${query}`;
}
function json(method: string, body: unknown): RequestInit {
  return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}
function message(reason: unknown) {
  const description = reason instanceof Error ? reason.message : String(reason);
  if (description.startsWith("409")) return "This item changed on the service. Copy your edits before reloading.";
  if (description.startsWith("403")) return "The service denied this operation. Check your project permissions or whether another user is editing.";
  return description;
}
function resetSettings(value: Project) {
  settings.value = {
    metadata: JSON.stringify(value.metadata, null, 2), permissions: value.permissions ?? "",
    definedWorldview: value.definedWorldview ?? "", snapshot: value.snapshot,
  };
  baselineSettings.value = JSON.stringify(settings.value);
}
async function fetchProject() {
  const value = await props.context.api.get<Project>(url("/project"));
  if (!active) return;
  projects.value = { ...projects.value, [value.urn]: value };
  resetSettings(value);
}
function clearSource() {
  semanticRequest?.abort();
  semantic.value = undefined;
  semanticPending.value = false;
  languageStatus.value = undefined;
  languageDiagnostics.value = [];
  source.value = undefined;
  text.value = "";
  cursor.value = "Ln 1, Col 1";
}
function setSource(value: Source) {
  source.value = value;
  text.value = normalize(value.text);
  refreshSemantics();
}
function refreshSemantics() {
  const current = source.value;
  semanticRequest?.abort();
  semantic.value = undefined;
  semanticPending.value = false;
  if (!current || !["ONTOLOGY", "MODEL_NAMESPACE"].includes(current.document.type) || current.document.errors) return;
  const request = new AbortController();
  semanticRequest = request;
  semanticPending.value = true;
  void props.context.api.request<NonNullable<typeof semantic.value>>(
    url("/source/semantics", { document: current.document.id, revision: current.revision }), { signal: request.signal },
  ).then(result => {
    if (!request.signal.aborted) semantic.value = result;
  }).catch(() => {
    if (!request.signal.aborted) semantic.value = { status: "UNAVAILABLE", diagnostics: [] };
  }).finally(() => {
    if (!request.signal.aborted) semanticPending.value = false;
  });
}
watch([source, text, languageStatus], ([current, draft, status], _previous, cleanup) => {
  draftDiagnostics.value = [];
  draftStatus.value = "";
  if (!current || !project.value?.editable || !dirty.value || status === "connected" || status === "checking") return;
  const request = new AbortController();
  draftStatus.value = "Checking syntax…";
  const timer = window.setTimeout(async () => {
    try {
      const result = await props.context.api.request<Diagnostic[]>(
        url("/source/diagnostics", { document: current.document.id }),
        { ...json("POST", { text: draft }), signal: request.signal },
      );
      if (!request.signal.aborted) {
        draftDiagnostics.value = result;
        draftStatus.value = "Draft syntax checked";
      }
    } catch {
      if (!request.signal.aborted) draftStatus.value = "Draft syntax check unavailable";
    }
  }, 450);
  cleanup(() => {
    window.clearTimeout(timer);
    request.abort();
  });
});
async function fetchSource(document: DocumentItem) {
  const value = await props.context.api.get<Source>(url("/source", { document: document.id }));
  if (active) setSource(value);
}
async function load(action: () => Promise<void>) {
  loading.value = true;
  error.value = "";
  feedback.value = "";
  try { await action(); }
  catch (reason) { if (active) error.value = message(reason); }
  finally { loading.value = false; }
}
function navigate(action: () => void) {
  if (saving.value || loading.value) return;
  if (dirty.value) pending.value = action;
  else action();
}
function proceed() {
  const action = pending.value;
  pending.value = undefined;
  action?.();
}
function chooseWorkspace(next: string) {
  if (!next || workspace.value === next) return;
  navigate(() => {
    clearSource();
    workspace.value = next;
    projectName.value = "";
    projects.value = {};
    expandedProjects.value = new Set();
    repositoryOpen.value = false;
    error.value = feedback.value = "";
  });
}
function selectProject(name: string) {
  if (name === projectName.value && !source.value && !error.value) {
    expandedProjects.value.add(name);
    return;
  }
  navigate(() => { void load(async () => {
    clearSource();
    projectName.value = name;
    expandedProjects.value.add(name);
    repositoryOpen.value = false;
    await fetchProject();
  }); });
}
function toggleProject(name: string) {
  if (expandedProjects.value.delete(name)) return;
  if (projects.value[name]) expandedProjects.value.add(name);
  else selectProject(name);
}
function selectDocument(name: string, document: DocumentItem) {
  if (name === projectName.value && document.id === source.value?.document.id) return;
  navigate(() => { void load(async () => {
    clearSource();
    if (projectName.value !== name) {
      projectName.value = name;
      repositoryOpen.value = false;
      await fetchProject();
    }
    await fetchSource(document);
    if (window.matchMedia("(max-width: 680px)").matches) navigatorVisible.value = false;
  }); });
}
function discard() {
  if (source.value) text.value = normalize(source.value.text);
  else if (project.value) resetSettings(project.value);
}
function report(result: Diagnostic[]) {
  const problem = result.filter(item => item.level === "Error").map(item => item.message);
  if (!problem.length) return true;
  error.value = [...new Set(problem)].join("\n");
  return false;
}
async function save() {
  if (!dirty.value || saving.value || !project.value?.editable) return false;
  saving.value = true;
  error.value = feedback.value = "";
  try {
    if (source.value) {
      const current = source.value;
      const value = /\r\n/.test(current.text) ? text.value.replace(/\n/g, "\r\n") : text.value;
      const result = await props.context.api.request<{ source?: Source; notifications: Diagnostic[] }>(
        url("/source", { document: current.document.id }), json("PUT", { revision: current.revision, text: value }),
      );
      if (!active) return false;
      if (!result.source) {
        if (report(result.notifications)) error.value = "The service could not confirm the saved source. Your edits are still open.";
        return false;
      }
      setSource(result.source);
      feedback.value = result.source.document.errors ? "Source saved with document errors." : "Source saved.";
      try { await fetchProject(); } catch (reason) { error.value = `Source saved. Project refresh failed: ${message(reason)}`; }
    } else {
      const metadata: unknown = JSON.parse(settings.value.metadata);
      if (!metadata || Array.isArray(metadata) || typeof metadata !== "object") {
        error.value = "Project metadata must be a JSON object.";
        return false;
      }
      const current = project.value;
      const result = await props.context.api.request<Diagnostic[]>(url("/project"), json("PUT", {
        revision: current.settingsRevision,
        settings: {
          metadata, permissions: settings.value.permissions === (current.permissions ?? "") ? null : settings.value.permissions,
          definedWorldview: current.administrator && settings.value.definedWorldview !== (current.definedWorldview ?? "")
            ? settings.value.definedWorldview : null,
          snapshot: settings.value.snapshot === current.snapshot ? null : settings.value.snapshot,
        },
      }));
      if (!active || !report(result)) return false;
      baselineSettings.value = JSON.stringify(settings.value);
      feedback.value = "Project settings saved.";
      try { await fetchProject(); } catch (reason) { error.value = `Settings saved. Project refresh failed: ${message(reason)}`; }
    }
    return true;
  } catch (reason) {
    error.value = reason instanceof SyntaxError ? "Project metadata contains invalid JSON." : message(reason);
    return false;
  } finally { saving.value = false; }
}
async function saveThenProceed() {
  if (await save()) proceed();
}
function reload() {
  const document = source.value?.document;
  navigate(() => { void load(async () => {
    discard();
    await fetchProject();
    if (document && project.value?.documents.some(item => item.id === document.id)) await fetchSource(document);
    else if (document) clearSource();
  }); });
}
function revisionStatus(project: Project | undefined, document: DocumentItem) {
  return gitDecoration(documentGitStatus(project?.repository, document.path));
}
async function runRepository() {
  if (saving.value || dirty.value || !project.value?.canManageRepository || !operation.value) return;
  if (operation.value === "DISCARD_LOCAL_CHANGES" && !confirmDiscard.value) {
    confirmDiscard.value = true;
    return;
  }
  const document = source.value?.document;
  confirmDiscard.value = false;
  await load(async () => {
    try {
      const result = await props.context.api.request<Diagnostic[]>(url("/repository"), json("POST", {
        operation: operation.value, parameter: parameter.value.trim(),
      }));
      if (report(result)) feedback.value = result.find(item => item.level === "Warning")?.message ?? "Repository updated.";
      await fetchProject();
      if (document && project.value?.documents.some(item => item.id === document.id)) await fetchSource(document);
      else if (document) clearSource();
    } finally {
      operation.value = parameter.value = "";
    }
  });
}
function toggleRepository() {
  repositoryOpen.value = !repositoryOpen.value;
  confirmDiscard.value = false;
}
function changeFullscreen() {
  if (document.fullscreenElement === root.value) fullscreen.value = true;
  else if (!projectFullscreenFallback) fullscreen.value = false;
}
function leaveFallback() {
  if (!projectFullscreenFallback) return;
  document.body.style.overflow = originalOverflow;
  for (const sibling of inertSiblings) sibling.element.inert = sibling.inert;
  inertSiblings = [];
  projectFullscreenFallback = fullscreen.value = false;
}
async function toggleFullscreen() {
  const target = root.value;
  if (!target) return;
  if (fullscreen.value) {
    if (document.fullscreenElement === target) await document.exitFullscreen();
    else leaveFallback();
    return;
  }
  if (target.requestFullscreen) {
    try { await target.requestFullscreen(); return; }
    catch { if (!active) return; }
  }
  originalOverflow = document.body.style.overflow;
  document.body.style.overflow = "hidden";
  for (let branch: HTMLElement | null = target; branch?.parentElement; branch = branch.parentElement) {
    for (const element of branch.parentElement.children) {
      if (element === branch || !(element instanceof HTMLElement)) continue;
      inertSiblings.push({ element, inert: element.inert });
      element.inert = true;
    }
  }
  projectFullscreenFallback = fullscreen.value = true;
}
function keydown(event: KeyboardEvent) {
  if (event.key === "Escape" && !event.defaultPrevented) leaveFallback();
}
function beforeUnload(event: BeforeUnloadEvent) {
  if (dirty.value || saving.value) { event.preventDefault(); event.returnValue = ""; }
}
async function initialize() {
  await load(async () => {
    catalog.value = await props.context.api.get<Workspace[]>("api/v1/workspaces");
    workspace.value = catalog.value[0]?.urn ?? "";
  });
}
const unregister = props.context.registerNavigationGuard?.({
  isDirty: () => dirty.value || saving.value, isBusy: () => saving.value,
  canSave: () => dirty.value && Boolean(project.value?.editable), save, discard,
});
onMounted(() => {
  document.addEventListener("fullscreenchange", changeFullscreen);
  document.addEventListener("keydown", keydown);
  window.addEventListener("beforeunload", beforeUnload);
  void initialize();
});
onBeforeUnmount(() => {
  active = false;
  semanticRequest?.abort();
  unregister?.();
  document.removeEventListener("fullscreenchange", changeFullscreen);
  document.removeEventListener("keydown", keydown);
  window.removeEventListener("beforeunload", beforeUnload);
  leaveFallback();
});
</script>

<template>
  <section ref="root" class="knowledge-ui workspace-browser" :class="{ 'workspace-fullscreen': fullscreen }" aria-label="Workspaces">
    <div class="workspace-top">
      <q-tabs :model-value="workspace" dense no-caps align="left" class="workspace-tabs"
              aria-label="Available workspaces" @update:model-value="chooseWorkspace">
        <q-tab v-for="entry in catalog" :key="entry.urn" :name="entry.urn" :label="entry.urn"/>
      </q-tabs>
      <button type="button" :aria-pressed="fullscreen" @click="toggleFullscreen">
        <q-icon :name="fullscreen ? 'fullscreen_exit' : 'fullscreen'" size="18px"/>
        {{ fullscreen ? "Exit full screen" : "Full screen" }}
      </button>
    </div>
    <div v-if="pending" class="workspace-feedback" role="alert">
      <span>Save your changes before leaving?</span>
      <button type="button" :disabled="saving" @click="saveThenProceed">Save and continue</button>
      <button type="button" :disabled="saving" @click="proceed">Discard and continue</button>
      <button type="button" :disabled="saving" @click="pending = undefined">Keep editing</button>
    </div>
    <div v-if="error" class="workspace-feedback knowledge-error" role="alert">
      <span>{{ error }}</span>
      <button v-if="!workspace" type="button" @click="initialize">Retry</button>
    </div>
    <div class="workspace-layout" :class="{ 'workspace-without-tree': !navigatorVisible }">
      <aside v-if="navigatorVisible" class="workspace-tree" aria-label="Project tree">
        <h2>Projects</h2>
        <p v-if="!workspaceTree.length" class="workspace-note">{{ loading ? "Loading workspaces…" : "No accessible projects." }}</p>
        <ul>
          <li v-for="{ name, status, groups } in workspaceTree" :key="name">
            <div class="workspace-tree-project">
              <button type="button" class="workspace-tree-disclosure" :disabled="loading || saving"
                      :aria-label="`${expandedProjects.has(name) ? 'Collapse' : 'Expand'} ${name}`"
                      :aria-expanded="expandedProjects.has(name)" @click="toggleProject(name)">
                <q-icon :name="expandedProjects.has(name) ? 'expand_more' : 'chevron_right'" size="17px"/>
              </button>
              <button type="button" class="workspace-tree-row" :disabled="loading || saving"
                      :aria-current="name === projectName && !source ? 'true' : undefined" @click="selectProject(name)">
                <q-icon :name="expandedProjects.has(name) ? 'folder_open' : 'folder'" size="17px"/>
                <span class="workspace-row-label">{{ name }}</span>
                <span v-if="status" class="workspace-file-status" :data-status="status"
                      :title="`Git ${status}`">{{ status }}</span>
              </button>
            </div>
            <ul v-if="projects[name] && expandedProjects.has(name)" class="workspace-documents">
                <li v-for="group in groups" :key="group.label">
                  <div v-if="group.label" class="workspace-folder">
                    <q-icon name="folder_open"/>
                    <span class="workspace-row-label">{{ group.label }}</span>
                    <span v-if="group.status" class="workspace-file-status" :data-status="group.status"
                          :title="`Git ${group.status}`">{{ group.status }}</span>
                  </div>
                  <ul :class="{ 'workspace-group': group.label }">
                    <li v-for="{ document, status: fileStatus } in group.documents" :key="document.id">
                      <button type="button" class="workspace-tree-row workspace-tree-document" :disabled="loading || saving"
                              :aria-current="name === projectName && source?.document.id === document.id ? 'true' : undefined"
                              @click="selectDocument(name, document)">
                        <q-icon name="description" size="15px"/><span class="workspace-row-label">{{ document.label }}</span>
                        <span v-if="document.errors || document.warnings || fileStatus" class="workspace-row-status">
                          <span v-if="document.errors" class="workspace-problem" role="img"
                                :aria-label="`${document.errors} ${document.errors === 1 ? 'error' : 'errors'}`">
                            <q-icon name="error_outline"/>
                          </span>
                          <span v-else-if="document.warnings" class="workspace-warning" role="img"
                                :aria-label="`${document.warnings} ${document.warnings === 1 ? 'warning' : 'warnings'}`">
                            <q-icon name="warning_amber"/>
                          </span>
                          <span v-if="fileStatus" class="workspace-file-status" :data-status="fileStatus"
                                :title="`Git ${fileStatus}`">{{ fileStatus }}</span>
                        </span>
                      </button>
                    </li>
                  </ul>
                </li>
            </ul>
          </li>
        </ul>
      </aside>
      <div class="workspace-main" :aria-busy="loading || saving">
        <header class="workspace-toolbar">
          <button type="button" :aria-pressed="navigatorVisible" aria-label="Toggle project tree" title="Toggle project tree"
                  @click="navigatorVisible = !navigatorVisible"><q-icon name="account_tree" size="18px"/></button>
          <div class="workspace-title">
            <span v-if="source">{{ projectName }}</span>
            <h2>{{ source?.document.label || projectName || "Project workspace" }}</h2>
          </div>
          <div v-if="project" class="workspace-actions">
            <button v-if="project.editable" type="button" class="workspace-primary" :disabled="!dirty || loading || saving"
                    @click="save">{{ saving ? "Saving…" : source ? "Save source" : "Save settings" }}</button>
            <button v-if="source" type="button" @click="editor?.find()">Find</button>
            <button v-if="source && project.editable" type="button" :disabled="saving" @click="editor?.format()">Format</button>
            <button type="button" aria-label="Reload from service" title="Reload from service" :disabled="loading || saving" @click="reload">
              <q-icon name="refresh" size="17px"/>
            </button>
            <button v-if="project.repository?.branch" type="button" :aria-expanded="repositoryOpen" @click="toggleRepository">
              <q-icon name="fork_right" size="16px"/> Revision control
            </button>
            <button v-if="source" type="button" :aria-pressed="lineNumbers" aria-label="Line numbers" title="Line numbers"
                    @click="lineNumbers = !lineNumbers"><q-icon name="format_list_numbered" size="17px"/></button>
            <button v-if="source" type="button" :aria-pressed="minimap" aria-label="Minimap" title="Minimap"
                    @click="minimap = !minimap"><q-icon name="map" size="17px"/></button>
            <button v-if="source" type="button" :aria-pressed="split" @click="split = !split">
              <q-icon name="vertical_split" size="18px"/> {{ split ? "Close split" : "Split view" }}
            </button>
          </div>
        </header>
        <section v-if="repositoryOpen && project?.repository" class="workspace-revision" aria-label="Revision control">
          <div class="workspace-revision-heading">
            <strong>{{ project.repository.branch }}</strong>
            <span>{{ project.repository.status.toLowerCase() }}</span>
            <details v-if="Object.keys(project.repository.files).length">
              <summary>{{ Object.keys(project.repository.files).length }} changed files</summary>
              <ul><li v-for="(status, path) in project.repository.files" :key="path">{{ status.toLowerCase() }} · {{ path }}</li></ul>
            </details>
          </div>
          <div v-if="project.canManageRepository" class="workspace-revision-actions">
            <select v-model="operation" aria-label="Repository operation" :disabled="loading || saving"
                    @change="parameter = ''; confirmDiscard = false">
              <option value="">Choose operation</option>
              <option v-for="action in repositoryActions.filter(item => !item.remote || project?.repository?.remote)"
                      :key="action.value" :value="action.value">{{ action.label }}</option>
            </select>
            <input v-if="needsBranch || acceptsMessage" v-model="parameter" :list="needsBranch ? 'workspace-branches' : undefined"
                   :aria-label="needsBranch ? 'Branch name' : 'Commit message'"
                   :placeholder="needsBranch ? 'Branch name' : 'Commit message (optional)'"/>
            <datalist id="workspace-branches">
              <option v-for="branch in project.repository.branches" :key="branch" :value="branch"/>
            </datalist>
            <button type="button" :disabled="!operation || loading || saving || dirty || (needsBranch && !parameter.trim())"
                    @click="runRepository">{{ confirmDiscard ? "Confirm discard changes" : "Run" }}</button>
            <span v-if="dirty">Save your edits first.</span>
          </div>
        </section>

        <div v-if="source" class="workspace-editor-layout" :class="{ 'workspace-split': split }">
          <Suspense>
            <Monaco ref="editor" :key="`${projectName}/${source.document.id}`" :source="source" :value="text"
                    :read-only="!project?.editable || saving || loading" :line-numbers="lineNumbers" :minimap="minimap"
                    :semantic-diagnostics="semantic?.diagnostics ?? []" :draft-diagnostics="draftDiagnostics"
                    :language-endpoint="languageEndpoint"
                    :review-glyphs="reviewGlyphs?.(source)" :extensions="editorExtensions"
                    @change="text = $event" @cursor="cursor = $event" @save="save" @review="emit('review', source, $event)"
                    @language-status="languageStatus = $event" @language-diagnostics="languageDiagnostics = $event"/>
            <template #fallback><p class="workspace-note" role="status">Loading editor…</p></template>
          </Suspense>
          <aside v-if="split" class="workspace-supplement" aria-label="Document details">
            <slot name="companion" :source="source" :text="text">
              <h3>Document details</h3>
              <p>{{ source.document.label }} · .{{ source.document.extension }}</p>
              <h4>Problems</h4>
              <p v-if="dirty && draftStatus">{{ draftStatus }}</p>
              <p v-else-if="!diagnostics.length">{{ languageStatus === "checking" ? "Checking language diagnostics…"
                : semanticPending ? "Checking semantics…" : "No reported problems." }}</p>
              <ul v-if="diagnostics.length">
                <li v-for="(item, index) in diagnostics" :key="index">
                  <button type="button" @click="editor?.jump(item.offset ?? 0, item.normalizedOffsets)">{{ item.level }} · {{ item.message }}</button>
                </li>
              </ul>
              <p v-if="!dirty && semantic && semantic.status !== 'COMPLETE'">{{ semantic.reason || "Semantic validation unavailable." }}</p>
            </slot>
          </aside>
        </div>

        <form v-else-if="project" class="workspace-settings" @submit.prevent="save">
          <h3>Project settings</h3>
          <p v-if="project.description">{{ project.description }}</p>
          <dl class="workspace-facts">
            <div><dt>Version</dt><dd>{{ project.version || "—" }}</dd></div>
            <div><dt>Worldview</dt><dd>{{ project.worldview || "—" }}</dd></div>
            <div v-if="!project.administrator || !project.editable">
              <dt>Defined worldview</dt><dd>{{ project.definedWorldview || "—" }}</dd>
            </div>
          </dl>
          <label class="workspace-field">Project metadata
            <textarea v-model="settings.metadata" spellcheck="false" :readonly="!project.editable || saving || loading" rows="8"/>
          </label>
          <label v-if="project.editable" class="workspace-field">Project permissions
            <input v-model="settings.permissions" :disabled="saving || loading"/>
            <small>Use the service’s project permissions expression. An empty value grants owner-only access.</small>
          </label>
          <label v-if="project.administrator && project.editable" class="workspace-field">Defined worldview
            <input v-model="settings.definedWorldview" :disabled="saving || loading"/>
          </label>
          <label class="workspace-check">
            <input v-model="settings.snapshot" type="checkbox" :disabled="!project.editable || saving || loading"/>
            Synchronize with Git origin before loading
          </label>
        </form>
        <div v-else class="workspace-empty">
          <p>{{ loading ? "Loading workspace…" : workspace ? "Select a project to browse its documents and settings."
                  : "Your account has no accessible workspaces on this service." }}</p>
        </div>
        <footer class="workspace-status" aria-label="Editor status">
          <span v-if="project">{{ project.editable ? 'Editable' : 'Read only' }}</span>
          <span v-if="project?.repository?.branch"><q-icon name="fork_right"/> {{ project.repository.branch }} · {{ project.repository.status.toLowerCase() }}</span>
          <span v-if="source && revisionStatus(project, source.document)">File {{ revisionStatus(project, source.document) }}</span>
          <button v-if="source" type="button" class="workspace-diagnostics-toggle" @click="split = true"
                  :aria-label="`Show problems: ${errors} errors and ${warnings} warnings`">
            <q-icon name="error_outline"/> {{ errors }} <q-icon name="warning_amber"/> {{ warnings }}
          </button>
          <span v-if="source && !dirty && semanticPending">Checking semantics…</span>
          <span v-else-if="source && dirty">{{ draftStatus }}</span>
          <span v-if="source && languageStatus" aria-live="polite">{{ {
            starting: "Starting language server…", checking: "Checking language diagnostics…",
            connected: "Language server connected", unavailable: "Language server unavailable",
          }[languageStatus] }}</span>
          <span v-else-if="source && !dirty && semantic && semantic.status !== 'COMPLETE' && !source.document.errors"
                :title="semantic.reason">Semantic check unavailable</span>
          <span class="workspace-status-message" role="status">{{ dirty ? "Unsaved changes" : feedback }}</span>
          <span v-if="source">{{ cursor }}</span>
          <span v-if="source">.{{ source.document.extension }}</span>
        </footer>
      </div>
    </div>
  </section>
</template>

<style scoped src="@klab-dashboard/workspaces/workspaces.css"/>
