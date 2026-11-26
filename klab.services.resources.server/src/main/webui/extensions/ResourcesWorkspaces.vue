<script setup lang="ts">
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import "@klab-dashboard/styles/theme.css";
import "@klab-dashboard/knowledge/knowledge.css";
import type { LanguageStatus } from "@klab-dashboard/workspaces/language-session";
import WorkspaceTree from "@klab-dashboard/workspaces/WorkspaceTree.vue";
import ProjectReviewPane from "../ProjectReviewPane.vue";
import type { AppliedComparison, ReviewAnchor, ReviewInput } from "@klab-dashboard/workspaces/project-review";
import { maxCandidateBytes, type CandidateComparison } from "@klab-dashboard/workspaces/proposal-candidate";
import {
  documentGitStatus, expandedTreeKeys, filterTree, gitDecoration, projectTree,
  type Diagnostic, type DocumentItem, type EditorExtension, type Project, type ProjectLock, type ProjectNavigation,
  type ReviewGlyph, type Source, type Workspace, type WorkspaceTreeNode,
} from "@klab-dashboard/workspaces/model";

const Monaco = defineAsyncComponent(() => import("@klab-dashboard/workspaces/WorkspaceMonaco.vue"));
const MonacoDiff = defineAsyncComponent(() => import("@klab-dashboard/workspaces/WorkspaceMonacoDiff.vue"));
const props = defineProps<{
  context: DashboardContext;
  reviewGlyphs?: (source: Source) => ReviewGlyph[];
  editorExtensions?: readonly EditorExtension[];
}>();
const emit = defineEmits<{ review: [Source, string] }>();
const root = ref<HTMLElement>();
const editor = ref<{ find(): void; format(): void; jump(offset: number, normalizedOffsets?: boolean): void;
  reviewSelection(): ReviewAnchor | undefined; revealReview(start: number, end?: number): void }>();
const catalog = ref<Workspace[]>([]);
const workspace = ref("");
const projects = ref<Record<string, Project>>({});
const navigationProjects = ref(new Map<string, WorkspaceTreeNode>());
interface SemanticCheck {
  revision: string;
  sourceVersion?: string;
  status: string;
  reason?: string;
  diagnostics: Diagnostic[];
  errors: number;
  warnings: number;
}
const semanticChecks = ref(new Map<string, Map<string, SemanticCheck>>());
const navigationLoading = ref(false);
const navigationError = ref("");
const treeQuery = ref("");
const query = computed(() => treeQuery.value.trim().toLowerCase());
const expanded = ref(new Set<string>());
const searchExpansion = ref<Set<string>>();
const selectedNode = ref("");
const pendingJump = ref<number>();
const layout = ref<HTMLElement>();
const treeScroll = ref<HTMLElement>();
const treeWidth = ref(294);
const layoutWidth = ref(0);
const minimumTreeWidth = 215;
const maximumTreeWidth = computed(() => Math.max(minimumTreeWidth, Math.min(560, Math.floor(layoutWidth.value * .55))));
const actualTreeWidth = computed(() => Math.min(treeWidth.value, maximumTreeWidth.value));
let treeResizeObserver: ResizeObserver | undefined;
let navigationRequest = 0;
const projectName = ref("");
const project = computed(() => projects.value[projectName.value]);
const workspaceTree = computed(() => {
  const current = catalog.value.find(item => item.urn === workspace.value);
  return (current?.projects ?? []).map(name => {
    const tree = navigationProjects.value.get(name) ?? projectTree(name, undefined, current?.repositoryStatuses?.[name]);
    const checks = semanticChecks.value.get(name);
    return checks?.size ? decorateSemanticTree(tree, checks) : tree;
  });
});
const visibleTree = computed(() => filterTree(workspaceTree.value, query.value));
const expandedNodes = computed(() => query.value
  ? searchExpansion.value ?? expandedTreeKeys(visibleTree.value)
  : expanded.value);
watch(query, () => { searchExpansion.value = undefined; });
watch(workspace, value => { if (value) void fetchNavigation(value); });
watch([editor, pendingJump], ([instance, offset]) => {
  if (instance && offset !== undefined) {
    pendingJump.value = undefined;
    instance.jump(offset);
  }
}, { flush: "post" });
const source = ref<Source>();
const comparison = ref<(CandidateComparison & { saved: string; language: string })>();
const appliedComparison = ref<AppliedComparison>();
const shownComparison = computed(() => comparison.value ?? appliedComparison.value);
let comparisonTrigger: HTMLElement | undefined;
function compareCandidate(input: CandidateComparison) {
  const current = source.value;
  if (!current || current.document.type !== "ONTOLOGY") return;
  if (new TextEncoder().encode(current.text).byteLength > maxCandidateBytes) {
    error.value = "The saved ontology exceeds the 2 MiB comparison limit.";
    return;
  }
  comparisonTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
  comparison.value = { ...input, saved: current.text, language: current.document.language };
}
async function closeComparison() {
  comparison.value = undefined;
  appliedComparison.value = undefined;
  await nextTick();
  comparisonTrigger?.focus();
  comparisonTrigger = undefined;
}
function compareApplied(input: AppliedComparison) {
  if (loading.value || saving.value || changingLock.value) return;
  comparisonTrigger = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
  appliedComparison.value = input;
}
watch(() => source.value?.revision, () => { if (comparison.value) void closeComparison(); });
const sourceNavigation = computed(() => {
  const id = source.value?.document.id;
  const projectTree = navigationProjects.value.get(projectName.value);
  if (!id || !projectTree) return undefined;
  function find(nodes: WorkspaceTreeNode[]): WorkspaceTreeNode | undefined {
    for (const node of nodes) {
      if (node.kind === "document") {
        if (node.document?.id === id) return node;
      } else {
        const match = find(node.children);
        if (match) return match;
      }
    }
  }
  return find(projectTree.children);
});
const text = ref("");
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const feedback = ref("");
const pending = ref<(() => void)>();
const cursor = ref("Ln 1, Col 1");
function followTreeCursor(label: string, key?: string) {
  cursor.value = label;
  if (!key) return;
  selectedNode.value = key;
  const path = key.split("/");
  const keys = expanded.value;
  let parent = "";
  let updated: Set<string> | undefined;
  for (const segment of path.slice(0, -1)) {
    parent = parent ? parent + "/" + segment : segment;
    if (keys.has(parent)) continue;
    updated ??= new Set(keys);
    updated.add(parent);
  }
  if (updated) expanded.value = updated;
}
watch([selectedNode, expandedNodes, treeScroll], () => {
  const pane = treeScroll.value;
  const selected = pane?.querySelector("[aria-current='true']");
  if (!pane || !selected) return;
  const bounds = pane.getBoundingClientRect();
  const row = selected.getBoundingClientRect();
  if (row.top < bounds.top) pane.scrollTop += row.top - bounds.top;
  else if (row.bottom > bounds.bottom) pane.scrollTop += row.bottom - bounds.bottom;
}, { flush: "post" });
const navigatorVisible = ref(true);
const split = ref(false);
const reviewOpen = ref(false);
const reviewPane = ref<InstanceType<typeof ProjectReviewPane>>();
const reviewMarks = ref<ReviewGlyph[]>([]);
const activeStatement = computed(() => {
  const document = sourceNavigation.value, current = source.value, key = selectedNode.value;
  if (!document || !current || !key.startsWith(document.key + "/")) return;
  function find(nodes: WorkspaceTreeNode[]): string | undefined {
    for (const node of nodes) {
      if (node.key === key) return node.label;
      if (key.startsWith(node.key + "/")) return find(node.children);
    }
  }
  const label = find(document.children);
  return label ? (current.document.urn || current.document.label.split(" [", 1)[0]) + ":" + label : undefined;
});
const editorReviewMarks = computed(() => source.value ? [
  ...(reviewOpen.value ? reviewMarks.value : []), ...(props.reviewGlyphs?.(source.value) ?? []),
] : []);
function selectReviewAnchor(): ReviewAnchor | undefined {
  const range = editor.value?.reviewSelection();
  return range ? { ...range, statement: activeStatement.value } : undefined;
}
function locateReviewStatement(statement: string) {
  const document = sourceNavigation.value, current = source.value, view = editor.value;
  const urn = current?.document.urn ?? current?.document.label.split(" [", 1)[0];
  if (!document || !current || !view || dirty.value || !statement.startsWith(urn + ":")
      || document.document?.sourceVersion !== current.document.sourceVersion) return false;
  const label = statement.slice((urn?.length ?? 0) + 1);
  const matches: WorkspaceTreeNode[] = [];
  function visit(nodes: WorkspaceTreeNode[]) {
    for (const node of nodes) {
      if (node.label === label) matches.push(node);
      visit(node.children);
    }
  }
  visit(document.children);
  const declaration = matches[0];
  const start = declaration?.offset;
  if (matches.length !== 1 || start === undefined || start < 0 || start >= current.text.length) return false;
  view.revealReview(start, Math.min(current.text.length, start + (declaration.length ?? 0)));
  return true;
}
function reviewGlyphClick(id: string) {
  if (reviewOpen.value && reviewMarks.value.some(glyph => glyph.id === id)) void reviewPane.value?.showGlyph(id);
  else if (source.value) emit("review", source.value, id);
}
function switchSidePane(review: boolean, details: boolean) {
  if (reviewPane.value?.isBusy()) return;
  const change = () => {
    reviewOpen.value = review;
    split.value = details;
    if (review && window.matchMedia("(max-width: 680px)").matches) navigatorVisible.value = false;
    if (!review) reviewMarks.value = [];
  };
  if (!review && reviewPane.value?.isDirty()) pending.value = change;
  else change();
}
const fullscreen = ref(false);
const lineNumbers = ref(true);
const minimap = ref(false);
const semantic = computed(() => {
  const current = source.value;
  if (!current) return;
  const result = semanticChecks.value.get(projectName.value)?.get(current.document.id);
  if (result?.revision === current.revision) return result;
});
const supportsSemanticCheck = computed(() =>
  source.value?.document.type === "ONTOLOGY" || source.value?.document.type === "MODEL_NAMESPACE");
const semanticPending = ref(false);
const draftDiagnostics = ref<Diagnostic[]>([]);
const draftStatus = ref("");
const languageStatus = ref<LanguageStatus>();
const languageDiagnostics = ref<Diagnostic[]>([]);
const languageEndpoint = computed(() => source.value && project.value?.lock.editable
  ? { api: props.context.api, url: url("/source/lsp", { document: source.value.document.id }) } : undefined);
const settings = ref({ metadata: "", permissions: "", definedWorldview: "", snapshot: false });
const baselineSettings = ref("");
const operation = ref("");
const parameter = ref("");
const confirmDiscard = ref(false);
const repositoryOpen = ref(false);
const changingLock = ref(false);
const lockEndpoint = computed(() => project.value ? url("/project/lock") : "");
let lockReadings = 0;
let expiryTimer: number | undefined;
let activityTimer: number | undefined;
let nextRenewal = 0;
const dirty = computed(() => source.value
  ? text.value !== normalize(source.value.text)
  : Boolean(project.value && baselineSettings.value && JSON.stringify(settings.value) !== baselineSettings.value));
watch(dirty, changed => { if (changed) cancelSemanticCheck(); });
const semanticStatus = computed(() => {
  if (semanticPending.value) return "Checking semantics…";
  if (dirty.value) return "";
  switch (semantic.value?.status) {
    case "COMPLETE": return "Semantic check complete";
    case "SYNTAX_ERRORS": return "Semantic check blocked by syntax errors";
    case "FAILED": return "Semantic check failed";
    case "STALE_KNOWLEDGE": return "Semantic check awaiting Reasoner synchronization";
    case "UNAVAILABLE": return "Semantic check unavailable";
    default: return "";
  }
});
function diagnosticKey(item: Diagnostic) {
  return JSON.stringify([item.level, item.message, item.offset ?? null]);
}
const diagnostics = computed(() => {
  const items = dirty.value ? [...draftDiagnostics.value, ...languageDiagnostics.value]
    : [...(source.value?.diagnostics ?? []), ...languageDiagnostics.value, ...(semantic.value?.diagnostics ?? [])];
  return [...new Map(items.map(item => [diagnosticKey(item), item])).values()];
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
function installLock(target: string, lock: ProjectLock) {
  if (!active || target !== lockEndpoint.value || !project.value) return;
  projects.value = { ...projects.value, [projectName.value]: { ...project.value, lock } };
}
watch([lockEndpoint, () => project.value?.lock], ([target, lock], [previous]) => {
  window.clearTimeout(expiryTimer);
  window.clearTimeout(activityTimer);
  activityTimer = undefined;
  if (previous !== target) nextRenewal = 0;
  if (target && lock?.remainingMillis != null) {
    expiryTimer = window.setTimeout(() => {
      if (lock.owned) {
        installLock(target, { ...lock, editable: false, remainingMillis: null });
        feedback.value = "Checking project lock…";
      }
      void readLock("GET", target);
    }, Math.min(lock.remainingMillis, 2_147_483_647));
  }
});
async function readLock(method: "GET" | "POST" | "PUT" | "DELETE", target = lockEndpoint.value) {
  if (!target) return;
  const reading = ++lockReadings;
  try {
    const lock = await props.context.api.request<ProjectLock>(target, { method });
    if (reading !== lockReadings || target !== lockEndpoint.value) return;
    const before = project.value?.lock;
    installLock(target, lock);
    if (method === "POST") feedback.value = lock.owned ? "Project locked for editing." : "Project lock is unavailable.";
    else if (method === "DELETE") feedback.value = lock.owned ? "Project remains locked." : "Project unlocked.";
    else if (before?.owned && !lock.owned) feedback.value = "Project lock expired.";
    else if (feedback.value === "Checking project lock…") feedback.value = "";
  } catch (reason) {
    if (reading !== lockReadings || target !== lockEndpoint.value) return;
    if (method === "POST" || method === "DELETE") throw reason;
    if (feedback.value === "Checking project lock…") feedback.value = "Project lock could not be checked.";
  }
}
function markLockActivity() {
  const lock = project.value?.lock;
  const target = lockEndpoint.value;
  if (!lock?.editable || !lock.idleTimeoutMillis || changingLock.value || activityTimer !== undefined) return;
  const interval = Math.min(10000, lock.idleTimeoutMillis / 3);
  activityTimer = window.setTimeout(() => {
    activityTimer = undefined;
    nextRenewal = Date.now() + interval;
    void readLock("PUT", target);
  }, Math.max(0, nextRenewal - Date.now()));
}
function refreshLock() {
  if (!changingLock.value) void readLock("GET");
}
function changeLock(acquire: boolean) {
  if (loading.value || saving.value || changingLock.value) return;
  const change = async () => {
    const target = lockEndpoint.value;
    changingLock.value = true;
    error.value = "";
    window.clearTimeout(activityTimer);
    activityTimer = undefined;
    try {
      if (!acquire) discard();
      await readLock(acquire ? "POST" : "DELETE", target);
    } catch (reason) {
      error.value = acquire && message(reason).includes("changed on the service")
        ? "This project is already locked." : message(reason);
      await readLock("GET", target);
    } finally { changingLock.value = false; }
  };
  if (!acquire && dirty.value) navigate(() => { void change(); });
  else void change();
}
function resetSettings(value: Project) {
  settings.value = {
    metadata: JSON.stringify(value.metadata, null, 2), permissions: value.permissions ?? "",
    definedWorldview: value.definedWorldview ?? "", snapshot: value.snapshot,
  };
  baselineSettings.value = JSON.stringify(settings.value);
}
async function fetchNavigation(current = workspace.value) {
  const request = ++navigationRequest;
  const isCurrent = () => active && request === navigationRequest && workspace.value === current;
  navigationLoading.value = true;
  navigationError.value = "";
  try {
    const nodes = await props.context.api.get<ProjectNavigation[]>(
      `api/v1/workspaces/navigation?${new URLSearchParams({ workspace: current })}`);
    if (isCurrent()) {
      navigationProjects.value = new Map(nodes.map(node => [node.urn, projectTree(node.urn, node)]));
    }
  } catch (reason) {
    if (isCurrent()) navigationError.value = message(reason);
  } finally {
    if (isCurrent()) navigationLoading.value = false;
  }
}
async function fetchProject(includeNavigation = false) {
  const value = await props.context.api.get<Project>(
    url("/project", includeNavigation ? { includeNavigation: "true" } : {}));
  if (!active) return;
  projects.value = { ...projects.value, [value.urn]: value };
  if (value.navigation) {
    const navigation = { urn: value.urn, repository: value.repository, children: value.navigation };
    navigationProjects.value.set(value.urn, projectTree(value.urn, navigation));
  }
  resetSettings(value);
}
function clearSource() {
  comparison.value = undefined;
  appliedComparison.value = undefined;
  comparisonTrigger = undefined;
  pendingJump.value = undefined;
  cancelSemanticCheck();
  languageStatus.value = undefined;
  languageDiagnostics.value = [];
  source.value = undefined;
  text.value = "";
  cursor.value = "Ln 1, Col 1";
}
function setSource(value: Source) {
  cancelSemanticCheck();
  const checks = semanticChecks.value.get(projectName.value);
  const previous = checks?.get(value.document.id);
  if (previous && previous.revision !== value.revision) checks?.delete(value.document.id);
  if (value.lock) installLock(lockEndpoint.value, value.lock);
  source.value = value;
  text.value = normalize(value.text);
}
function cancelSemanticCheck() {
  semanticRequest?.abort();
  semanticRequest = undefined;
  semanticPending.value = false;
}
async function checkSemantics() {
  const current = source.value;
  if (!current || !supportsSemanticCheck.value || dirty.value || semanticPending.value || loading.value || saving.value) return;
  const name = projectName.value;
  const request = new AbortController();
  semanticRequest = request;
  semanticPending.value = true;
  let result: Pick<SemanticCheck, "status" | "reason" | "diagnostics">;
  try {
    result = await props.context.api.request<typeof result>(
      url("/source/semantics", { document: current.document.id, revision: current.revision }),
      { signal: request.signal, cache: "no-store" },
    );
  } catch (reason) {
    result = {
      status: "UNAVAILABLE", diagnostics: [],
      reason: reason instanceof Error && reason.message.startsWith("409")
        ? "The saved source changed. Reload it before checking semantics again." : message(reason),
    };
  }
  if (semanticRequest !== request) return;
  semanticRequest = undefined;
  semanticPending.value = false;
  if (source.value !== current || dirty.value) return;
  const seen = new Set(current.diagnostics.map(diagnosticKey));
  const newDiagnostics = result.diagnostics.filter(item => {
    const key = diagnosticKey(item);
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
  const checks = new Map(semanticChecks.value.get(name));
  checks.set(current.document.id, {
    ...result, diagnostics: newDiagnostics, revision: current.revision, sourceVersion: current.document.sourceVersion,
    errors: newDiagnostics.filter(item => item.level === "Error").length,
    warnings: newDiagnostics.filter(item => item.level === "Warning").length,
  });
  semanticChecks.value.set(name, checks);
}
function decorateSemanticTree(node: WorkspaceTreeNode, checks: ReadonlyMap<string, SemanticCheck>): WorkspaceTreeNode {
  if (node.kind === "document" && node.document) {
    if (node.project === projectName.value && node.document.id === source.value?.document.id && dirty.value) return node;
    const check = checks.get(node.document.id);
    return check && check.sourceVersion === node.document.sourceVersion && (check.errors || check.warnings)
      ? { ...node, errors: node.errors + check.errors, warnings: node.warnings + check.warnings }
      : node;
  }
  let errors = node.errors, warnings = node.warnings;
  const children = node.children.map(child => {
    const decorated = decorateSemanticTree(child, checks);
    errors += decorated.errors - child.errors;
    warnings += decorated.warnings - child.warnings;
    return decorated;
  });
  return errors === node.errors && warnings === node.warnings ? node : { ...node, children, errors, warnings };
}
watch([source, text, languageStatus], ([current, draft, status], _previous, cleanup) => {
  draftDiagnostics.value = [];
  draftStatus.value = "";
  if (!current || !project.value?.lock.editable || !dirty.value || status === "connected") return;
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
  if (saving.value || loading.value || reviewPane.value?.isBusy()) return;
  if (dirty.value || reviewPane.value?.isDirty()) pending.value = action;
  else action();
}
function proceed() {
  const action = pending.value;
  pending.value = undefined;
  reviewPane.value?.discard();
  action?.();
}
function chooseWorkspace(next: string) {
  if (!next || workspace.value === next) return;
  navigate(() => {
    clearSource();
    workspace.value = next;
    projectName.value = "";
    projects.value = {};
    navigationProjects.value = new Map();
    semanticChecks.value.clear();
    reviewOpen.value = false;
    reviewMarks.value = [];
    expanded.value = new Set();
    treeQuery.value = selectedNode.value = "";
    repositoryOpen.value = false;
    error.value = feedback.value = "";
  });
}
function selectProject(name: string, key: string) {
  if (name === projectName.value && !source.value && !error.value) {
    expanded.value.add(key);
    return;
  }
  navigate(() => { void load(async () => {
    clearSource();
    projectName.value = name;
    expanded.value.add(key);
    selectedNode.value = key;
    repositoryOpen.value = false;
    await fetchProject();
  }); });
}
function toggleBranch(key: string) {
  const keys = new Set(expandedNodes.value);
  if (!keys.delete(key)) keys.add(key);
  if (query.value) searchExpansion.value = keys;
  else expanded.value = keys;
}
function expandTree(open: boolean) {
  const keys = open ? expandedTreeKeys(visibleTree.value) : new Set<string>();
  if (query.value) searchExpansion.value = keys;
  else expanded.value = keys;
}
function selectTreeNode(node: WorkspaceTreeNode) {
  const { project: name, document, offset, key } = node;
  if (!document) {
    if (node.kind === "project") selectProject(name, key);
    return;
  }
  if (name === projectName.value && document.id === source.value?.document.id) {
    if (offset !== undefined && dirty.value) {
      feedback.value = "Save or discard your changes before jumping to a declaration.";
      return;
    }
    selectedNode.value = key;
    if (offset !== undefined) pendingJump.value = offset;
    if (window.matchMedia("(max-width: 680px)").matches) navigatorVisible.value = false;
    return;
  }
  navigate(() => { void load(async () => {
    clearSource();
    if (projectName.value !== name) {
      projectName.value = name;
      repositoryOpen.value = false;
      await fetchProject();
    }
    await fetchSource(document);
    selectedNode.value = key;
    if (offset !== undefined) pendingJump.value = offset;
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
async function saveSource(current: Source, revision: string, value: string, reviewInputs?: ReviewInput[]) {
  const outcome = reviewInputs === undefined ? "Source saved" : "Candidate applied";
  const result = await props.context.api.request<{ source?: Source; notifications: Diagnostic[]; reviewWarning?: string }>(
    url("/source", { document: current.document.id }), json("PUT", { revision, text: value, reviewInputs }),
  );
  if (!active) return false;
  if (!result.source) {
    if (report(result.notifications)) error.value = outcome === "Candidate applied"
      ? "The service could not confirm the candidate was applied. Reload the ontology to check."
      : "The service could not confirm the saved source. Your edits are still open.";
    return false;
  }
  setSource(result.source);
  if (reviewInputs !== undefined) reviewPane.value?.invalidateHistory();
  if (result.reviewWarning) error.value = result.reviewWarning;
  feedback.value = result.source.document.errors ? outcome + " with document errors." : outcome + ".";
  try { await fetchProject(true); } catch (reason) {
    error.value = [result.reviewWarning ?? outcome + ".", `Project refresh failed: ${message(reason)}`].join(" ");
  }
  return true;
}
const applyBlock = computed(() => {
  const candidate = comparison.value, current = source.value, lock = project.value?.lock;
  if (!candidate || !current) return "Open the ontology to apply this candidate.";
  if (candidate.base.status === "unrecorded") return "Applying requires a recorded base revision and snapshot.";
  if (candidate.base.status !== "verified" || current.revision !== candidate.base.revision || current.text !== candidate.saved)
    return "The saved ontology differs from the proposal’s base. Rebase the candidate before applying it.";
  if (candidate.candidate === candidate.saved) return "The candidate already matches the saved ontology.";
  if (dirty.value) return "Save or discard your open source edits before applying the candidate.";
  if (!lock?.editable || !lock.owned) {
    if (lock?.locked && !lock.owned) return "Another editor holds the project lock.";
    return lock?.canLock ? "Lock the project before applying the candidate." : "Applying requires project update permission and an owned lock.";
  }
  return "";
});
const canLockForCandidate = computed(() => {
  const candidate = comparison.value;
  return candidate?.base.status === "verified" && source.value?.revision === candidate.base.revision
    && candidate.candidate !== candidate.saved && !dirty.value && Boolean(project.value?.lock.canLock);
});
async function applyCandidate() {
  const candidate = comparison.value, current = source.value;
  if (!candidate || candidate.base.status !== "verified" || !current || applyBlock.value
      || loading.value || saving.value || changingLock.value) return;
  saving.value = true;
  error.value = feedback.value = "";
  try {
    await saveSource(current, candidate.base.revision, candidate.candidate, candidate.inputs);
  } catch (reason) {
    const status = reason instanceof Error ? reason.message : "";
    if (status.startsWith("409")) {
      comparison.value = { ...candidate, base: { status: "changed" } };
      error.value = "The ontology changed on the service. Return to the review and reload the saved source.";
    } else {
      error.value = message(reason);
      if (status.startsWith("403")) refreshLock();
    }
  } finally { saving.value = false; }
}
async function save() {
  if (!dirty.value || saving.value || !project.value?.lock.editable) return false;
  saving.value = true;
  error.value = feedback.value = "";
  try {
    if (source.value) {
      const current = source.value;
      const value = /\r\n/.test(current.text) ? text.value.replace(/\n/g, "\r\n") : text.value;
      return await saveSource(current, current.revision, value);
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
    if (reason instanceof Error && reason.message.startsWith("403")) refreshLock();
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
    await fetchProject(true);
    if (document && project.value?.documents.some(item => item.id === document.id)) await fetchSource(document);
    else if (document) clearSource();
  }); });
}
function revisionStatus(project: Project | undefined, document: DocumentItem) {
  return gitDecoration(documentGitStatus(project?.repository, document.path));
}
async function runRepository() {
  if (saving.value || dirty.value || !project.value?.canManageRepository || !project.value.lock.editable || !operation.value) return;
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
      await fetchProject(true);
      if (document && project.value?.documents.some(item => item.id === document.id)) await fetchSource(document);
      else if (document) clearSource();
    } finally {
      operation.value = parameter.value = "";
    }
  });
}
function startTreeResize(event: PointerEvent) {
  if (event.button !== 0) return;
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  event.preventDefault();
}
function moveTreeResize(event: PointerEvent) {
  if (!(event.currentTarget as HTMLElement).hasPointerCapture(event.pointerId) || !layout.value) return;
  setTreeWidth(event.clientX - layout.value.getBoundingClientRect().left);
}
function setTreeWidth(width: number) {
  treeWidth.value = Math.round(Math.max(minimumTreeWidth, Math.min(maximumTreeWidth.value, width)));
}
function keyboardTreeResize(event: KeyboardEvent) {
  switch (event.key) {
    case "ArrowLeft": setTreeWidth(actualTreeWidth.value - 16); break;
    case "ArrowRight": setTreeWidth(actualTreeWidth.value + 16); break;
    case "Home": setTreeWidth(minimumTreeWidth); break;
    case "End": setTreeWidth(maximumTreeWidth.value); break;
    default: return;
  }
  event.preventDefault();
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
  if (dirty.value || saving.value || reviewPane.value?.isDirty() || reviewPane.value?.isBusy()) {
    event.preventDefault();
    event.returnValue = "";
  }
}
async function initialize() {
  await load(async () => {
    catalog.value = await props.context.api.get<Workspace[]>("api/v1/workspaces");
    workspace.value = catalog.value[0]?.urn ?? "";
  });
}
const unregister = props.context.registerNavigationGuard?.({
  isDirty: () => dirty.value || saving.value || Boolean(reviewPane.value?.isDirty()),
  isBusy: () => saving.value || Boolean(reviewPane.value?.isBusy()),
  canSave: () => dirty.value && !reviewPane.value?.isDirty() && Boolean(project.value?.lock.editable),
  save,
  discard: () => { discard(); reviewPane.value?.discard(); },
});
onMounted(() => {
  treeResizeObserver = new ResizeObserver(([entry]) => { layoutWidth.value = entry.contentRect.width; });
  if (layout.value) treeResizeObserver.observe(layout.value);
  window.addEventListener("focus", refreshLock);
  document.addEventListener("fullscreenchange", changeFullscreen);
  document.addEventListener("keydown", keydown);
  window.addEventListener("beforeunload", beforeUnload);
  void initialize();
});
onBeforeUnmount(() => {
  treeResizeObserver?.disconnect();
  window.removeEventListener("focus", refreshLock);
  window.clearTimeout(expiryTimer);
  window.clearTimeout(activityTimer);
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
  <section ref="root" class="knowledge-ui workspace-browser" :class="{ 'workspace-fullscreen': fullscreen }" aria-label="Workspaces"
           @pointerdown.capture="markLockActivity" @keydown.capture="markLockActivity" @wheel.passive="markLockActivity">
    <div class="workspace-top">
      <q-tabs :model-value="workspace" dense no-caps align="left" class="workspace-tabs"
              aria-label="Available workspaces" @update:model-value="chooseWorkspace">
        <q-tab v-for="entry in catalog" :key="entry.urn" :name="entry.urn" :label="entry.urn"/>
      </q-tabs>
      <button type="button" class="workspace-icon-button" :aria-pressed="fullscreen"
              :aria-label="fullscreen ? 'Exit full screen' : 'Full screen'"
              :title="fullscreen ? 'Exit full screen' : 'Full screen'" @click="toggleFullscreen">
        <q-icon :name="fullscreen ? 'fullscreen_exit' : 'fullscreen'" size="18px"/>
      </button>
    </div>
    <div v-if="pending" class="workspace-feedback" role="alert">
      <span>Save your changes before continuing?</span>
      <button type="button" :disabled="saving || !dirty || !project?.lock.editable || reviewPane?.isDirty()" @click="saveThenProceed">Save and continue</button>
      <button type="button" :disabled="saving" @click="proceed">Discard and continue</button>
      <button type="button" :disabled="saving" @click="pending = undefined">Keep editing</button>
    </div>
    <div v-if="error" class="workspace-feedback knowledge-error" role="alert">
      <span>{{ error }}</span>
      <button v-if="!workspace" type="button" @click="initialize">Retry</button>
    </div>
    <div ref="layout" class="workspace-layout" :class="{ 'workspace-without-tree': !navigatorVisible }"
         :style="{ '--workspace-tree-width': actualTreeWidth + 'px' }">
      <aside v-if="navigatorVisible" id="workspace-project-tree" class="workspace-tree"
             aria-label="Project tree" :aria-busy="navigationLoading">
        <div class="workspace-tree-header">
          <div class="workspace-tree-search">
            <q-icon name="search" size="16px"/>
            <input v-model="treeQuery" type="search" aria-label="Search workspace" placeholder="Search workspace"/>
          </div>
          <button type="button" aria-label="Collapse tree" title="Collapse tree" :disabled="!expandedNodes.size"
                  @click="expandTree(false)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 10 6 6 6-6M6 4l6 6 6-6" transform="rotate(180 12 12)"/></svg>
          </button>
          <button type="button" aria-label="Expand tree" title="Expand tree"
                  :disabled="navigationLoading || !visibleTree.some(node => node.children.length)"
                  @click="expandTree(true)">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 10 6 6 6-6M6 4l6 6 6-6"/></svg>
          </button>
        </div>
        <div ref="treeScroll" class="workspace-tree-scroll">
          <p v-if="navigationLoading || loading && !workspace" class="workspace-note" role="status">Loading project tree…</p>
          <div v-else-if="navigationError" class="workspace-note" role="alert">
            <p>Could not load the project tree. {{ navigationError }}</p>
            <button type="button" @click="fetchNavigation()">Retry</button>
          </div>
          <p v-else-if="!visibleTree.length" class="workspace-note" role="status">
            {{ query ? "No matching projects or declarations." : "No accessible projects." }}
          </p>
          <WorkspaceTree :nodes="visibleTree" :expanded="expandedNodes" :selected="selectedNode"
                         :disabled="loading || saving" @select="selectTreeNode" @toggle="toggleBranch"/>
        </div>
      </aside>
      <div v-if="navigatorVisible" class="workspace-tree-resizer" role="separator" tabindex="0"
           aria-label="Resize project tree" aria-orientation="vertical" aria-controls="workspace-project-tree"
           :aria-valuemin="minimumTreeWidth" :aria-valuemax="maximumTreeWidth" :aria-valuenow="actualTreeWidth"
           @pointerdown="startTreeResize" @pointermove="moveTreeResize" @keydown="keyboardTreeResize"/>
      <div class="workspace-main" :aria-busy="loading || saving">
        <header class="workspace-toolbar">
          <button type="button" class="workspace-icon-button" :aria-pressed="navigatorVisible" aria-label="Toggle project tree" title="Toggle project tree"
                  @click="navigatorVisible = !navigatorVisible"><q-icon name="account_tree" size="18px"/></button>
          <div class="workspace-title">
            <span v-if="source || appliedComparison">{{ projectName }}</span>
            <h2>{{ appliedComparison ? "Review history" : source?.document.label || projectName || "Project workspace" }}</h2>
          </div>
          <div v-if="project && !shownComparison" class="workspace-actions">
            <button v-if="project.lock.owned || project.lock.canLock" type="button" class="workspace-lock-action"
                    :disabled="loading || saving || changingLock"
                    :aria-label="project.lock.owned ? 'Unlock project' : 'Lock project'"
                    :title="project.lock.owned ? 'Unlock project' : 'Lock project for editing'"
                    @click="changeLock(!project.lock.owned)">
              <q-icon :name="project.lock.owned ? 'lock_open' : 'lock'" size="17px"/>
              {{ project.lock.owned ? "Unlock" : "Lock to edit" }}
            </button>
            <button type="button" class="workspace-primary" :disabled="!project.lock.editable || !dirty || loading || saving"
                    :aria-label="source ? 'Save source' : 'Save settings'"
                    :title="source ? 'Save source' : 'Save settings'" @click="save">
              <q-icon name="save" size="17px"/>
            </button>
            <button v-if="source" type="button" aria-label="Find" title="Find" @click="editor?.find()">
              <q-icon name="search" size="18px"/>
            </button>
            <button v-if="source" type="button" :disabled="!project.lock.editable || saving" aria-label="Format source"
                    title="Format source" @click="editor?.format()">
              <q-icon name="format_align_left" size="17px"/>
            </button>
            <button v-if="supportsSemanticCheck" type="button" aria-label="Check semantics"
                    :title="dirty ? 'Save changes before checking semantics' : 'Check semantics'"
                    :disabled="dirty || loading || saving || semanticPending" @click="checkSemantics">
              <q-icon name="fact_check" size="17px"/>
            </button>
            <button type="button" aria-label="Reload from service" title="Reload from service" :disabled="loading || saving" @click="reload">
              <q-icon name="refresh" size="17px"/>
            </button>
            <button v-if="project.repository?.branch" type="button" :aria-expanded="repositoryOpen"
                    aria-label="Revision control" title="Revision control" @click="toggleRepository">
              <q-icon name="fork_right" size="17px"/>
            </button>
            <button v-if="source" type="button" :aria-pressed="lineNumbers" aria-label="Line numbers" title="Line numbers"
                    @click="lineNumbers = !lineNumbers"><q-icon name="format_list_numbered" size="17px"/></button>
            <button v-if="source" type="button" :aria-pressed="minimap" aria-label="Minimap" title="Minimap"
                    @click="minimap = !minimap"><q-icon name="map" size="17px"/></button>
            <button v-if="source" type="button" :aria-pressed="split" :aria-label="split ? 'Close split' : 'Split view'"
                    :title="split ? 'Close split' : 'Split view'" @click="switchSidePane(false, !split)">
              <q-icon name="vertical_split" size="18px"/>
            </button>
            <button type="button" :aria-pressed="reviewOpen" aria-label="Project review" title="Project review"
                    :disabled="loading || saving" @click="switchSidePane(!reviewOpen, false)">
              <q-icon name="forum" size="18px"/>
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
          <div v-if="project.canManageRepository && project.lock.editable" class="workspace-revision-actions">
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

        <Suspense v-if="shownComparison">
          <MonacoDiff :saved="shownComparison.saved" :candidate="shownComparison.candidate" :language="shownComparison.language"
                      :caption="shownComparison.caption" :historical="Boolean(appliedComparison)"
                      :apply-block="appliedComparison ? '' : applyBlock" :can-lock="canLockForCandidate"
                      :busy="loading || saving || changingLock" :applying="saving"
                      @close="closeComparison" @lock="changeLock(true)" @apply="applyCandidate"/>
          <template #fallback><p class="workspace-note" role="status">Loading comparison…</p></template>
        </Suspense>
        <div v-if="source || (project && reviewOpen)" v-show="!shownComparison" class="workspace-editor-layout"
             :class="{ 'workspace-split': source && (split || reviewOpen), 'workspace-review-split': source && reviewOpen }">
          <Suspense v-if="source">
            <Monaco ref="editor" :key="`${projectName}/${source.document.id}`" :source="source" :value="text"
                    :navigation="sourceNavigation"
                    :read-only="!project?.lock.editable || saving || loading" :line-numbers="lineNumbers" :minimap="minimap"
                    :semantic-diagnostics="semantic?.diagnostics ?? []" :draft-diagnostics="draftDiagnostics"
                    :language-endpoint="languageEndpoint"
                    :review-glyphs="editorReviewMarks" :extensions="editorExtensions"
                    @change="text = $event" @cursor="followTreeCursor" @save="save" @review="reviewGlyphClick"
                    @language-status="languageStatus = $event" @language-diagnostics="languageDiagnostics = $event"/>
            <template #fallback><p class="workspace-note" role="status">Loading editor…</p></template>
          </Suspense>
          <aside v-if="reviewOpen" class="workspace-supplement" aria-label="Review pane">
            <ProjectReviewPane ref="reviewPane" :context="context" :workspace="workspace" :project="projectName"
                               :source="source" :source-dirty="Boolean(source && dirty)" :statement="activeStatement"
                               :outline="sourceNavigation"
                               :selection="selectReviewAnchor" :jump="(start, end) => editor?.revealReview(start, end)"
                               :locate-statement="locateReviewStatement"
                               @glyphs="reviewMarks = $event" @compare="compareCandidate" @applied-compare="compareApplied"/>
          </aside>
          <aside v-else-if="split && source" class="workspace-supplement" aria-label="Document details">
            <slot name="companion" :source="source" :text="text">
              <h3>Document details</h3>
              <p>{{ source.document.label }} · .{{ source.document.extension }}</p>
              <h4>Problems</h4>
              <p v-if="dirty && draftStatus">{{ draftStatus }}</p>
              <p v-else-if="!diagnostics.length">{{ semanticPending ? "Checking semantics…" : "No reported problems." }}</p>
              <ul v-if="diagnostics.length">
                <li v-for="(item, index) in diagnostics" :key="index">
                  <button type="button" @click="editor?.jump(item.offset ?? 0, item.normalizedOffsets)">{{ item.level }} · {{ item.message }}</button>
                </li>
              </ul>
              <p v-if="!dirty && semantic?.reason">{{ semantic.reason }}</p>
            </slot>
          </aside>
        </div>

        <form v-else-if="project" class="workspace-settings" @submit.prevent="save">
          <h3>Project settings</h3>
          <p v-if="project.description">{{ project.description }}</p>
          <dl class="workspace-facts">
            <div><dt>Version</dt><dd>{{ project.version || "—" }}</dd></div>
            <div><dt>Worldview</dt><dd>{{ project.worldview || "—" }}</dd></div>
            <div v-if="!project.administrator || !project.lock.editable">
              <dt>Defined worldview</dt><dd>{{ project.definedWorldview || "—" }}</dd>
            </div>
          </dl>
          <label class="workspace-field">Project metadata
            <textarea v-model="settings.metadata" spellcheck="false" :readonly="!project.lock.editable || saving || loading" rows="8"/>
          </label>
          <label v-if="project.lock.editable" class="workspace-field">Project permissions
            <input v-model="settings.permissions" :disabled="saving || loading"/>
            <small>Use the service’s project permissions expression. An empty value grants owner-only access.</small>
          </label>
          <label v-if="project.administrator && project.lock.editable" class="workspace-field">Defined worldview
            <input v-model="settings.definedWorldview" :disabled="saving || loading"/>
          </label>
          <label class="workspace-check">
            <input v-model="settings.snapshot" type="checkbox" :disabled="!project.lock.editable || saving || loading"/>
            Synchronize with Git origin before loading
          </label>
        </form>
        <div v-else class="workspace-empty">
          <p>{{ loading ? "Loading workspace…" : workspace ? "Select a project to browse its documents and settings."
                  : "Your account has no accessible workspaces on this service." }}</p>
        </div>
        <footer class="workspace-status" aria-label="Editor status">
          <span v-if="appliedComparison"><q-icon name="lock_outline"/> Read-only history</span>
          <template v-else>
          <span v-if="project" :title="project.lock.locked && !project.lock.owned ? 'Project locked by another user' : undefined">
            <q-icon :name="project.lock.editable ? 'edit' : 'lock_outline'"/>
            {{ project.lock.editable ? 'Editable' : project.lock.locked && !project.lock.owned ? 'Locked by another user' : 'Read only' }}
          </span>
          <span v-if="project?.repository?.branch"><q-icon name="fork_right"/> {{ project.repository.branch }} · {{ project.repository.status.toLowerCase() }}</span>
          <span v-if="source && revisionStatus(project, source.document)">File {{ revisionStatus(project, source.document) }}</span>
          <button v-if="source && !shownComparison" type="button" class="workspace-diagnostics-toggle" @click="switchSidePane(false, true)"
                  :aria-label="`Show problems: ${errors} errors and ${warnings} warnings`">
            <q-icon name="error_outline"/> {{ errors }} <q-icon name="warning_amber"/> {{ warnings }}
          </button>
          <span v-if="source && dirty && draftStatus">{{ draftStatus }}</span>
          <span v-if="source && languageStatus" aria-live="polite">{{ {
            starting: "LSP starting…", connected: "LSP active", unavailable: "LSP unavailable",
          }[languageStatus] }}</span>
          <span v-if="semanticStatus" role="status" :title="semantic?.reason">{{ semanticStatus }}</span>
          <span v-if="dirty">Unsaved changes</span>
          <span v-if="feedback" role="status">{{ feedback }}</span>
          <span v-if="source" class="workspace-cursor">{{ cursor }}</span>
          <span v-if="source">.{{ source.document.extension }}</span>
          </template>
        </footer>
      </div>
    </div>
  </section>
</template>

<style scoped src="@klab-dashboard/workspaces/workspaces.css"/>
