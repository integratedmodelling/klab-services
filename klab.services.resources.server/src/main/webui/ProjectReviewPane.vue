<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import type { DashboardContext } from "@klab-dashboard/types";
import type { ReviewGlyph, Source, WorkspaceTreeNode } from "@klab-dashboard/workspaces/model";
import ProposalReader from "@klab-dashboard/components/ProposalReader.vue";
import ProposalDraftEditor from "@klab-dashboard/workspaces/ProposalDraftEditor.vue";
import { proposalEditorConfig } from "@klab-dashboard/workspaces/proposal-schema";
import { candidateFromProposal, maxCandidateBytes, type CandidateBase, type CandidateComparison } from "@klab-dashboard/workspaces/proposal-candidate";
import { reviewError } from "@klab-dashboard/services/review-errors";
import {
  base64, bundleFile, decodeReviewBytes, encodeReviewText, mentionsStatement, parseProposal, proposalValue, record,
  readReviewDecisions, reviewDecisionPath, reviewId, reviewInput, sameProposalValue, serializeProposal, setProposalValue, structuredProposalCopy,
  type ProposalConflict, type RecordValue, type ReviewAnchor, type ReviewBundle, type ReviewCatalogue,
  type AppliedComparison, type ReviewDecision, type ReviewSnapshot, type ReviewTarget,
} from "@klab-dashboard/workspaces/project-review";

const props = defineProps<{
  context: DashboardContext;
  workspace: string;
  project: string;
  source?: Source;
  outline?: WorkspaceTreeNode;
  sourceDirty: boolean;
  statement?: string;
  selection: () => ReviewAnchor | undefined;
  jump: (start: number, end?: number) => void;
  locateStatement: (statement: string) => boolean;
}>();
const emit = defineEmits<{ glyphs: [ReviewGlyph[]]; compare: [CandidateComparison]; appliedCompare: [AppliedComparison] }>();
type Section = "Comments" | "Proposals" | "History" | "Evidence" | "Bootstrap";
const tabs: Section[] = ["Comments", "Proposals", "History", "Evidence", "Bootstrap"];
const tab = ref<Section>("Comments");
const catalogue = ref<ReviewCatalogue>();
const loading = ref(false);
const busy = ref(false);
const error = ref("");
const notice = ref("");
const comment = ref("");
const commentAnchor = ref<ReviewAnchor>();
const commentBase = ref<ReturnType<typeof base>>();
const draft = ref<{ id: string; created: string; title: string; anchor?: ReviewAnchor; statement?: string; bundles: ReviewBundle[];
  document: RecordValue; baseline: RecordValue; inputs: { bundle: ReviewBundle; document: RecordValue }[];
  conflicts: ProposalConflict[]; resolutions: Map<ProposalConflict, unknown>; proposalId: string; supersedes: string | null;
  base: ReturnType<typeof base> }>();
const editorConfig = computed(() => proposalEditorConfig(props.source?.document.type));
const candidateFile = ref<File>();
const draftCandidate = ref<CandidateComparison>();
const includeCandidate = ref(false);
function invalidateCandidate() {
  draftCandidate.value = undefined;
  includeCandidate.value = false;
}
watch(() => draft.value?.id, () => {
  candidateFile.value = undefined;
  invalidateCandidate();
});
watch(() => props.source?.revision, invalidateCandidate);
const unresolved = computed(() => {
  const value = draft.value;
  return value?.conflicts.filter(conflict => !value.resolutions.has(conflict)
    || !sameProposalValue(value.resolutions.get(conflict), proposalValue(value.document, conflict.path))).length ?? 0;
});
const links = ref<string[]>([]);
const linkQuery = ref("");
const metadata = ref({ origin: "", attribution: "", relation: "" });
const file = ref<File>();
const evidenceId = ref("");
const fileInput = ref<HTMLInputElement>();
const evidencePreview = ref<{ path: string; text: string }>();
const details = ref<Record<string, string>>({});
const itemList = ref<HTMLElement>();
const highlighted = ref("");
const linkedProposal = ref<ReviewBundle>();
const history = ref<ReturnType<typeof readReviewDecisions>>();
const historyLoading = ref(false);
const historyError = ref("");
const historyComparisonError = ref<{ line: number; message: string }>();
const comparingDecision = ref<number>();
const historyPageSize = 25;
const historyLimit = ref(historyPageSize);
let historyGeneration = 0;
let historyStale = false;
let generation = 0;
const detailRequests = new Map<string, Promise<string>>();
let commentAttempt: { id: string; created: string; identity: string } | undefined;

const documentUrn = computed(() => props.source?.document.urn ?? props.source?.document.label.split(" [", 1)[0]);
const target = computed<ReviewTarget>(() => {
  const type = props.source?.document.type;
  const kinds: Record<string, string> = {
    ONTOLOGY: "ontology", MODEL_NAMESPACE: "namespace", STRATEGY: "observation-strategy",
    BEHAVIOR: "behavior", APPLICATION: "behavior", SCRIPT: "behavior", TESTCASE: "behavior",
  };
  return { project: props.project, kind: type ? kinds[type] ?? "document" : "project", urn: documentUrn.value ?? props.project };
});
const relevant = computed(() => (catalogue.value?.bundles ?? []).filter(({ manifest }) =>
  manifest.target.kind === "project" || props.source && (
    manifest.base.path === props.source.document.path || manifest.target.urn === documentUrn.value
    || manifest.target.urn.startsWith(documentUrn.value + ":"))));
const commentBundles = computed(() => relevant.value.filter(bundle => bundle.manifest.kind === "comment"));
const proposals = computed(() => relevant.value.filter(bundle => bundle.manifest.kind === "proposal"));
const visibleProposals = computed(() => {
  const linked = linkedProposal.value;
  return linked && !proposals.value.some(item => item.path === linked.path) ? [linked, ...proposals.value] : proposals.value;
});
const proposalLibrary = computed(() => catalogue.value?.bundles.filter(bundle => bundle.manifest.kind === "proposal") ?? []);
const proposalsByPath = computed(() => new Map(proposalLibrary.value.map(bundle => [bundle.path, bundle])));
const hasHistory = computed(() => catalogue.value?.material.some(item => item.path === reviewDecisionPath));
const visibleHistory = computed(() => (history.value?.entries ?? []).slice(0, historyLimit.value).map(entry => ({
  entry, proposals: decisionProposals(entry),
})));
const reviewDate = new Intl.DateTimeFormat("en", { dateStyle: "medium", timeStyle: "short", timeZone: "UTC" });
function decisionDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? value : reviewDate.format(date) + " UTC";
}
function decisionProposals(entry: ReviewDecision): { key: string; label: string; bundle?: ReviewBundle }[] {
  if (entry.proposalInputs.length) return entry.proposalInputs.map(({ manifest, sha256 }) => {
    const bundle = proposalsByPath.value.get(manifest);
    const exact = bundle && (!sha256 || bundle.sha256 === sha256) ? bundle : undefined;
    const directory = manifest.split("/").at(-2) ?? manifest;
    return { key: manifest, label: exact?.manifest.id ?? directory.split("--").at(-1) ?? directory, bundle: exact };
  });
  return entry.proposalRevisions.map(id => {
    const matches = proposalLibrary.value.filter(bundle => bundle.manifest.id === id);
    return { key: id, label: id, bundle: matches.length === 1 ? matches[0] : undefined };
  });
}
const workflowPage = computed(() => props.context.config.pages.find(page => page.component === "resources-review"));
const linkChoices = computed(() => proposalLibrary.value.filter(bundle =>
  bundle.manifest.id.toLowerCase().includes(linkQuery.value.trim().toLowerCase())));
const materials = computed(() => (catalogue.value?.material ?? []).filter(item => tab.value === "Evidence"
  ? item.path.startsWith("review/evidence/") : item.path.startsWith("review/bootstrap/")));
const canAttach = computed(() => Boolean(props.source && !props.sourceDirty));
const canSubmit = computed(() => !loading.value && !busy.value);
const isDirty = computed(() => Boolean(comment.value.trim() || draft.value || file.value
  || Object.values(metadata.value).some(value => value.trim())));
function discard() {
  comment.value = "";
  commentAnchor.value = undefined;
  commentBase.value = undefined;
  commentAttempt = undefined;
  draft.value = undefined;
  links.value = [];
  linkQuery.value = "";
  metadata.value = { origin: "", attribution: "", relation: "" };
  file.value = undefined;
  evidenceId.value = "";
  if (fileInput.value) fileInput.value.value = "";
}
defineExpose({ isDirty: () => isDirty.value, isBusy: () => busy.value, discard, showGlyph, invalidateHistory });

function url(path = "") {
  return "api/v1/project-review" + path + "?" + new URLSearchParams({ workspace: props.workspace, project: props.project });
}
async function readFile(path: string, fresh = false) {
  const materialUrl = "api/v1/retrieve/ADDITIONAL_MATERIAL?" + new URLSearchParams({ urn: props.project + "/" + path });
  const api = props.context.api;
  const response = fresh ? await api.request<{ content: string }>(materialUrl, { cache: "no-store" })
    : await api.get<{ content: string }>(materialUrl);
  if (!response?.content && response?.content !== "") throw new Error("The review file is unavailable.");
  return decodeReviewBytes(response.content);
}
async function readText(path: string) { return new TextDecoder("utf-8", { fatal: true }).decode(await readFile(path)); }
async function download(path: string, name = path.split("/").at(-1) ?? "review") {
  try {
    const bytes = await readFile(path);
    const objectUrl = URL.createObjectURL(new Blob([bytes.buffer]));
    const link = document.createElement("a");
    link.href = objectUrl;
    link.download = name;
    link.click();
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
  } catch (cause) { error.value = reviewError(cause); }
}
async function refresh() {
  const current = ++generation;
  loading.value = true;
  error.value = "";
  try {
    const value = await props.context.api.request<ReviewCatalogue>(url(), { cache: "no-store" });
    if (current === generation) {
      catalogue.value = value;
      clearHistory();
      historyStale = false;
      if (tab.value === "History") void loadHistory();
    }
  } catch (cause) {
    if (current === generation) error.value = reviewError(cause);
  } finally {
    if (current === generation) loading.value = false;
  }
}
function clearHistory() {
  historyGeneration++;
  history.value = undefined;
  historyError.value = "";
  historyComparisonError.value = undefined;
  historyLoading.value = false;
  historyLimit.value = historyPageSize;
}
function invalidateHistory() {
  historyStale = true;
  clearHistory();
  if (tab.value === "History") void refresh();
}
async function loadHistory() {
  if (!hasHistory.value || history.value || historyLoading.value) return;
  const current = ++historyGeneration;
  historyLoading.value = true;
  historyError.value = "";
  try {
    const bytes = await readFile(reviewDecisionPath, true);
    if (current === historyGeneration) history.value = readReviewDecisions(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
  } catch (cause) {
    if (current === historyGeneration) historyError.value = reviewError(cause);
  } finally {
    if (current === historyGeneration) historyLoading.value = false;
  }
}
function selectSection(section: Section) {
  tab.value = section;
  linkedProposal.value = undefined;
  evidencePreview.value = undefined;
  notice.value = "";
  if (section === "History") {
    if (historyStale) void refresh();
    else void loadHistory();
  }
}
watch(() => [props.workspace, props.project], () => {
  catalogue.value = undefined;
  details.value = {};
  linkedProposal.value = undefined;
  historyStale = false;
  clearHistory();
  void refresh();
}, { immediate: true });
watch(() => props.source?.revision, () => { highlighted.value = ""; });

function commentItems(path: string): { commentId?: string; comment: string; author?: string; proposalRevisionId?: string }[] {
  if (!details.value[path]) return [];
  try {
    const data: unknown = JSON.parse(details.value[path]);
    return Array.isArray(data) ? data.filter(item => item && typeof item.comment === "string") : [];
  } catch { return []; }
}
async function loadDetail(path: string) {
  if (path in details.value) return details.value[path];
  const key = props.project + "/" + path;
  let pending = detailRequests.get(key);
  if (!pending) {
    const project = props.project;
    pending = readText(path).then(text => {
      if (project === props.project) details.value[path] = text;
      return text;
    }).finally(() => detailRequests.delete(key));
    detailRequests.set(key, pending);
  }
  return pending;
}
async function inspect(bundle: ReviewBundle) {
  try { await loadDetail(bundleFile(bundle, bundle.manifest.kind === "comment" ? "comments.json" : "proposal.yaml")); }
  catch (cause) { error.value = reviewError(cause); }
}
function currentAnchor(bundle: ReviewBundle) {
  const anchor = bundle.manifest.anchor, source = props.source;
  return canAttach.value && source && anchor && bundle.manifest.base.path === source.document.path
    && bundle.manifest.base.revision === source.revision && Number.isInteger(anchor.start)
    && Number.isInteger(anchor.end) && anchor.start >= 0 && anchor.end >= anchor.start && anchor.end <= source.text.length
    ? anchor : undefined;
}
watch(() => relevant.value.flatMap(bundle => {
  const anchor = currentAnchor(bundle);
  if (!anchor || !props.source) return [];
  const line = props.source.text.slice(0, anchor.start).split(/\r\n|\r|\n/).length;
  return [{ id: bundle.path, line, tooltip: `${bundle.manifest.kind === "comment" ? "Comment" : "Proposal"} by ${bundle.manifest.contributor}` }];
}), glyphs => emit("glyphs", glyphs), { immediate: true });
async function showGlyph(path: string) {
  const bundle = relevant.value.find(item => item.path === path);
  if (!bundle) return false;
  linkedProposal.value = undefined;
  await showBundle(bundle);
  return true;
}
async function showBundle(bundle: ReviewBundle, focus = false) {
  const path = bundle.path;
  tab.value = bundle.manifest.kind === "comment" ? "Comments" : "Proposals";
  highlighted.value = path;
  await nextTick();
  const card = Array.from(itemList.value?.querySelectorAll<HTMLElement>("[data-bundle]") ?? [])
    .find(item => item.dataset.bundle === path);
  if (card instanceof HTMLDetailsElement) card.open = true;
  card?.scrollIntoView({ block: "nearest" });
  if (focus) card?.querySelector("summary")?.focus({ preventScroll: true });
  void inspect(bundle);
}
function openHistoryProposal(bundle: ReviewBundle) {
  linkedProposal.value = bundle;
  void showBundle(bundle, true);
}
function locate(bundle: ReviewBundle) {
  const anchor = currentAnchor(bundle);
  if (anchor) props.jump(anchor.start, anchor.end);
}
function attachComment() {
  if (!canAttach.value) return;
  const selection = props.selection();
  if (selection) {
    commentAnchor.value = selection;
    commentBase.value = base();
  }
}
function base() {
  const source = props.source;
  return source
    ? { state: "snapshot", path: source.document.path, revision: source.revision, content: encodeReviewText(source.text) }
    : { state: "absent" };
}
async function submit(path: string, body: unknown) {
  return await props.context.api.request<{ path: string; sha256: string }>(path, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body), includeProblemDetail: true,
  });
}
async function perform(action: () => Promise<void>) {
  if (!canSubmit.value) return;
  busy.value = true;
  error.value = notice.value = "";
  try { await action(); } catch (cause) { error.value = reviewError(cause); }
  finally { busy.value = false; }
}
async function postComment() {
  if (!catalogue.value?.canComment || !comment.value.trim()) return;
  await perform(async () => {
    const payload = comment.value.trim();
    const snapshot = commentAnchor.value && commentBase.value ? commentBase.value : base();
    const identity = JSON.stringify([payload, target.value, snapshot.revision, commentAnchor.value]);
    if (!commentAttempt || commentAttempt.identity !== identity)
      commentAttempt = { id: reviewId(), created: new Date().toISOString(), identity };
    const { id, created } = commentAttempt;
    await submit(url("/bundles"), {
      kind: "comment", target: target.value, id, created_at: created,
      base: snapshot, anchor: commentAnchor.value,
      payload: encodeReviewText(JSON.stringify([{ commentId: id, author: props.context.auth.username, target: target.value.urn, stance: "question",
        status: "open", comment: payload }])),
    });
    comment.value = "";
    commentAnchor.value = undefined;
    commentBase.value = undefined;
    commentAttempt = undefined;
    notice.value = "Comment staged.";
    await refresh();
  });
}
async function startProposal() {
  const config = editorConfig.value;
  if (!catalogue.value?.canPropose || !config || !canAttach.value) return;
  await perform(async () => {
    const statement = props.statement;
    const anchor = statement ? props.selection() : undefined;
    const sourceTarget = target.value;
    const snapshot = base();
    const matches = [];
    for (const bundle of proposals.value) {
      if (bundle.manifest.anchor?.statement && bundle.manifest.anchor.statement !== statement) continue;
      if (bundle.manifest.payload_schema !== config.payload) continue;
      const text = await loadDetail(bundleFile(bundle, "proposal.yaml"));
      if (!statement || bundle.manifest.anchor?.statement === statement || bundle.manifest.target.urn === statement
          || mentionsStatement(text, statement)) matches.push({ bundle, text });
    }
    if (matches.length > 100) throw new Error("This target has more than 100 inputs. Narrow the review before starting a revision.");
    // Only an ontology bundle at this exact target can be the contract predecessor.
    const predecessor = matches.findIndex(({ bundle }) => bundle.manifest.target.kind === sourceTarget.kind
      && bundle.manifest.target.urn === sourceTarget.urn);
    if (predecessor > 0) matches.unshift(...matches.splice(predecessor, 1));
    const id = reviewId(), created = new Date().toISOString();
    const merged = config.prepare(statement ?? sourceTarget.urn, id, created.slice(0, 10), matches, predecessor >= 0);
    draft.value = { id, created, title: statement ?? sourceTarget.urn, statement, anchor: anchor && { ...anchor, statement },
      bundles: matches.map(item => item.bundle), ...merged, resolutions: new Map(), base: snapshot };
    links.value = [];
  });
}
function updateDraftField(key: string, value: unknown) {
  if (!draft.value || !record(draft.value.document.proposal) || editorConfig.value?.managed.has(key)) return;
  draft.value.document.proposal[key] = structuredProposalCopy(value);
  invalidateCandidate();
}
function resolveConflict(conflict: ProposalConflict, value: unknown) {
  const current = draft.value;
  if (!current) return;
  error.value = "";
  const before = proposalValue(current.document, conflict.path);
  const changed = !sameProposalValue(before, value);
  if (changed && !setProposalValue(current.document, conflict.path, value)) {
    error.value = "Restore the parent field in the draft before using this value.";
    return;
  }
  if (changed) invalidateCandidate();
  current.resolutions.set(conflict, structuredProposalCopy(value));
}
function locateDraftStatement(statement?: string) {
  if (statement && !props.locateStatement(statement)) notice.value = "Open the ontology that contains this declaration to locate it.";
  else notice.value = "";
}
function chooseCandidate(event: Event) {
  candidateFile.value = (event.target as HTMLInputElement).files?.[0];
  invalidateCandidate();
}
function candidateText(bytes: Uint8Array) {
  if (!bytes.length || bytes.length > maxCandidateBytes) throw new Error("Choose a candidate file between 1 byte and 2 MiB.");
  return new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(bytes);
}
async function sha256(bytes: Uint8Array<ArrayBuffer>) {
  return Array.from(new Uint8Array(await crypto.subtle.digest("SHA-256", bytes)),
    byte => byte.toString(16).padStart(2, "0")).join("");
}
async function verifiedSnapshot(path: string, descriptor: ReviewSnapshot) {
  if (descriptor.size > maxCandidateBytes) throw new Error("This snapshot exceeds the 2 MiB comparison limit.");
  const bytes = await readFile(path, true);
  if (bytes.length !== descriptor.size || await sha256(bytes) !== descriptor.sha256)
    throw new Error("The saved snapshot differs from the review record.");
  return new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(bytes);
}
async function compareDecision(entry: ReviewDecision) {
  const { baseRevision, resultingRevision, target, snapshots, id } = entry;
  if (!baseRevision || !resultingRevision || !target || comparingDecision.value !== undefined) return;
  const requested = historyGeneration;
  const trigger = document.activeElement;
  comparingDecision.value = entry.line;
  historyComparisonError.value = undefined;
  try {
    let saved: string | undefined, candidate: string | undefined;
    if (snapshots && id && /^review-[a-f0-9-]{36}$/.test(id)) {
      const directory = `review/decisions/${id}/`;
      [saved, candidate] = await Promise.all([
        verifiedSnapshot(directory + "base.kwv", snapshots.base),
        verifiedSnapshot(directory + "result.kwv", snapshots.result),
      ]);
    } else {
      // Older decisions can use retained proposal bytes only if their source revisions match.
      const encoder = new TextEncoder();
      const matches = async (text: string, urn: string, revision: string) =>
        await sha256(encoder.encode(urn + "\u0000" + text)) === revision;
      const source = props.source;
      if (source?.document.type === "ONTOLOGY" && documentUrn.value === target
          && encoder.encode(source.text).length <= maxCandidateBytes) {
        if (source.revision === baseRevision) saved = source.text;
        if (source.revision === resultingRevision) candidate = source.text;
      }
      for (const { bundle } of decisionProposals(entry)) {
        if (saved !== undefined && candidate !== undefined) break;
        if (!bundle || bundle.manifest.target.kind !== "ontology" || bundle.manifest.target.urn !== target) continue;
        const { base, files } = bundle.manifest;
        if (saved === undefined && base.file && base.revision === baseRevision && files[base.file]?.size <= maxCandidateBytes) {
          const text = await verifiedSnapshot(bundleFile(bundle, base.file), files[base.file]).catch(() => undefined);
          if (text !== undefined && await matches(text, target, baseRevision)) saved = text;
        }
        if (candidate === undefined && files["candidate.kwv"]?.size <= maxCandidateBytes) {
          const text = await verifiedSnapshot(bundleFile(bundle, "candidate.kwv"), files["candidate.kwv"]).catch(() => undefined);
          if (text !== undefined && await matches(text, target, resultingRevision)) candidate = text;
        }
      }
    }
    if (saved === undefined || candidate === undefined) throw new Error("The exact source snapshots for this decision are unavailable.");
    if (requested === historyGeneration && tab.value === "History") {
      // The focused history action remains the return point for the comparison.
      if (trigger instanceof HTMLButtonElement) trigger.focus();
      emit("appliedCompare", { saved, candidate, language: "kwv", caption: target });
    }
  } catch (cause) {
    if (requested === historyGeneration) historyComparisonError.value = { line: entry.line, message: reviewError(cause) };
  } finally { comparingDecision.value = undefined; }
}
async function stagedBase(bundle: ReviewBundle, saved: Source): Promise<CandidateBase> {
  const { base, target, files } = bundle.manifest;
  const snapshot = base.file && Object.hasOwn(files, base.file) ? files[base.file] : undefined;
  if (base.state !== "snapshot" || !base.revision || !base.path || !snapshot?.sha256) return { status: "unrecorded" };
  if (target.project !== props.project || target.urn !== documentUrn.value
      || target.kind !== "ontology" || base.path !== saved.document.path || base.revision !== saved.revision)
    return { status: "changed" };
  const bytes = new TextEncoder().encode(saved.text);
  return bytes.length === snapshot.size && await sha256(bytes) === snapshot.sha256
    ? { status: "verified", revision: base.revision } : { status: "changed" };
}
function matchingCandidate(bundle: ReviewBundle) {
  const source = props.source, manifest = bundle.manifest;
  return source?.document.type === "ONTOLOGY"
    && (manifest.target.urn === documentUrn.value || manifest.base.path === source.document.path)
    && (manifest.files["candidate.kwv"] || manifest.base.revision === source.revision
      && proposalEditorConfig(source.document.type, manifest.payload_schema));
}
async function compareSavedCandidate(bundle: ReviewBundle) {
  const saved = props.source;
  if (!saved || !matchingCandidate(bundle)) return;
  await perform(async () => {
    const descriptor = bundle.manifest.files["candidate.kwv"];
    const attribution = bundle.manifest.id + " · " + bundle.manifest.contributor;
    const inputs = [reviewInput(bundle), ...(bundle.manifest.inputs ?? [])];
    if (!descriptor) {
      const yaml = await loadDetail(bundleFile(bundle, "proposal.yaml"));
      const candidate = candidateFromProposal(parseProposal(yaml), saved, props.outline);
      if (new TextEncoder().encode(candidate).byteLength > maxCandidateBytes) throw new Error("The candidate exceeds 2 MiB.");
      emit("compare", { candidate, caption: "Preview from proposal fragments · " + attribution, base: await stagedBase(bundle, saved), inputs });
      return;
    }
    if (descriptor.size > maxCandidateBytes) throw new Error("The candidate exceeds 2 MiB.");
    const bytes = await readFile(bundleFile(bundle, "candidate.kwv"));
    const candidate = candidateText(bytes);
    const hash = await sha256(bytes);
    if (hash !== descriptor.sha256) throw new Error("The candidate file has changed since this proposal was staged.");
    emit("compare", { candidate, caption: attribution, base: await stagedBase(bundle, saved), inputs });
  });
}
async function compareDraft() {
  const current = draft.value, saved = props.source;
  if (!current || !saved || unresolved.value || saved.document.type !== "ONTOLOGY") return;
  await perform(async () => {
    const file = candidateFile.value;
    if (file && file.size > maxCandidateBytes) throw new Error("The candidate exceeds 2 MiB.");
    if (!file && current.base.revision !== saved.revision)
      throw new Error("Start a draft against the current saved revision or choose a complete candidate file.");
    const candidate = file ? candidateText(new Uint8Array(await file.arrayBuffer()))
      : candidateFromProposal(current.document, saved, props.outline);
    if (new TextEncoder().encode(candidate).byteLength > maxCandidateBytes) throw new Error("The candidate exceeds 2 MiB.");
    const snapshot = current.base;
    const candidateBase: CandidateBase = snapshot.revision === saved.revision && snapshot.path === saved.document.path
      && snapshot.content === encodeReviewText(saved.text)
      ? { status: "verified", revision: saved.revision } : { status: "changed" };
    draftCandidate.value = { candidate, caption: file?.name ?? "Preview from merged proposal fragments", base: candidateBase,
      inputs: draftInputs(current) };
    emit("compare", draftCandidate.value);
  });
}
function draftInputs(current: NonNullable<typeof draft.value>) {
  const selected = new Set(links.value);
  return [...new Map([...current.bundles, ...proposalLibrary.value.filter(bundle => selected.has(bundle.path))]
    .map(bundle => [bundle.path, reviewInput(bundle)])).values()];
}
async function postProposal() {
  const current = draft.value;
  if (!current || !catalogue.value?.canPropose || unresolved.value) return;
  await perform(async () => {
    const proposal = current.document.proposal as RecordValue;
    if (proposal.id !== current.proposalId || proposal.revision_id !== current.id
        || (proposal.supersedes_revision ?? null) !== current.supersedes)
      throw new Error("Keep the generated proposal ID, revision ID and predecessor in the draft.");
    const inputs = draftInputs(current);
    if (inputs.length > 100) throw new Error("A review revision can link at most 100 inputs.");
    await submit(url("/bundles"), {
      kind: "proposal", target: target.value, id: current.id, created_at: current.created,
      base: current.base, anchor: current.anchor, inputs, payload: encodeReviewText(serializeProposal(current.document)),
      candidate: includeCandidate.value && draftCandidate.value ? encodeReviewText(draftCandidate.value.candidate) : undefined,
    });
    draft.value = undefined;
    links.value = [];
    notice.value = "Proposal staged.";
    await refresh();
  });
}
function selectFile(event: Event) {
  file.value = (event.target as HTMLInputElement).files?.[0];
  evidenceId.value = file.value ? reviewId() : "";
}
async function postEvidence() {
  const selected = file.value;
  if (!selected || !catalogue.value?.canUpload) return;
  await perform(async () => {
    if (selected.size > 32 * 1024 * 1024) throw new Error("Evidence is limited to 32 MiB.");
    await submit(url("/evidence"), {
      target: target.value, id: evidenceId.value, filename: selected.name, ...metadata.value,
      content: base64(new Uint8Array(await selected.arrayBuffer())),
    });
    metadata.value = { origin: "", attribution: "", relation: "" };
    file.value = undefined;
    if (fileInput.value) fileInput.value.value = "";
    notice.value = "Evidence and its provenance record staged.";
    await refresh();
  });
}
async function previewMaterial(path: string, size: number) {
  error.value = "";
  try {
    if (size > 128 * 1024) throw new Error("Download this file to inspect its full contents.");
    evidencePreview.value = { path, text: await readText(path) };
  } catch (cause) { error.value = reviewError(cause); }
}
</script>

<template>
  <section class="project-review" aria-label="Project review">
    <header class="review-heading">
      <div><h3>Project review</h3><p>{{ documentUrn || project }}</p></div>
      <button type="button" aria-label="Refresh review" title="Refresh review" :disabled="loading || busy" @click="refresh">
        <q-icon name="refresh" size="18px"/>
      </button>
    </header>
    <nav class="review-tabs" aria-label="Review sections">
      <button v-for="section in tabs" :key="section" type="button" :aria-pressed="tab === section"
              @click="selectSection(section)">{{ section }}</button>
      <a v-if="workflowPage" :href="'ui/' + encodeURIComponent(workflowPage.name)">Review workflows</a>
    </nav>
    <p v-if="error" class="knowledge-error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="loading && !catalogue" role="status">Loading review…</p>
    <div v-if="catalogue" ref="itemList">
      <p v-if="catalogue.unreadable.length" role="status">{{ catalogue.unreadable.length }} review manifests could not be read.</p>
      <template v-if="tab === 'Comments'">
        <form v-if="catalogue.canComment" class="review-form" @submit.prevent="postComment">
          <label>Comment<textarea v-model="comment" :disabled="busy" required rows="3"/></label>
          <div class="review-actions">
            <button type="button" :disabled="!canAttach || busy" @click="attachComment">Use editor selection</button>
            <button v-if="commentAnchor" type="button" :disabled="busy" @click="commentAnchor = commentBase = undefined">Remove position</button>
            <button type="submit" :disabled="!comment.trim() || !canSubmit">Post comment</button>
          </div>
          <p v-if="commentAnchor" class="review-muted">{{ commentAnchor.start === commentAnchor.end ? "Position attached" : "Selected span attached" }}</p>
        </form>
        <p v-else>Posting requires a writable Git project and access to stage changes.</p>
        <p v-if="!commentBundles.length">No comments for this target.</p>
        <details v-for="(bundle, index) in commentBundles" :key="bundle.path" :data-bundle="bundle.path"
                 :class="{ 'review-highlight': highlighted === bundle.path }" :open="index < 6 || highlighted === bundle.path"
                 @toggle="($event.currentTarget as HTMLDetailsElement).open && inspect(bundle)">
          <summary><strong>{{ bundle.manifest.contributor }}</strong><span>{{ bundle.manifest.created_at }}</span></summary>
          <div v-for="(item, index) in commentItems(bundleFile(bundle, 'comments.json'))" :key="item.commentId ?? index" class="review-comment">
            <p v-if="bundle.manifest.status === 'historical' && item.author && item.author !== bundle.manifest.contributor">
              <strong>{{ item.author }}</strong>
            </p>
            <p>{{ item.comment }}</p>
            <p v-if="item.proposalRevisionId" class="review-muted">Proposal {{ item.proposalRevisionId }}</p>
          </div>
          <div class="review-actions">
            <button v-if="currentAnchor(bundle)" type="button" @click="locate(bundle)">Go to source</button>
            <button v-if="bundle.manifest.anchor && !currentAnchor(bundle)" type="button"
                    @click="download(bundleFile(bundle, Object.keys(bundle.manifest.files).find(name => name.startsWith('base.')) ?? 'base.bin'))">Download reviewed source</button>
          </div>
        </details>
      </template>
      <template v-else-if="tab === 'Proposals'">
        <button v-if="catalogue.canPropose && editorConfig && !draft" type="button"
                :disabled="!canSubmit || !canAttach" @click="startProposal">{{ statement ? "Draft from current declaration" : "Draft proposal" }}</button>
        <p v-if="!editorConfig">Open a document with a proposal editor to start a draft.</p>
        <form v-if="draft && editorConfig" class="review-form" @submit.prevent="postProposal">
          <h4>{{ draft.title }}</h4>
          <p v-if="draft.base.revision !== source?.revision" class="review-muted">This draft refers to an earlier saved source revision.</p>
          <p v-if="draft.bundles.length">Merged from {{ draft.bundles.length }} proposal revisions.</p>
          <ProposalDraftEditor :document="draft.document" :baseline="draft.baseline" :inputs="draft.inputs"
                               :conflicts="draft.conflicts" :resolutions="draft.resolutions" :config="editorConfig"
                               :statement="draft.statement" :disabled="busy"
                               :can-locate="canAttach && draft.base.revision === source?.revision"
                               @field="updateDraftField" @resolve="resolveConflict" @locate="locateDraftStatement"/>
          <fieldset v-if="source?.document.type === 'ONTOLOGY'">
            <legend>Candidate source</legend>
            <label>Complete candidate file (optional)<input type="file" accept=".kwv,text/plain" :disabled="busy"
                                                            @change="chooseCandidate"/></label>
            <div class="review-actions">
              <button type="button" :disabled="!canSubmit || unresolved > 0" @click="compareDraft">Compare candidate in Monaco</button>
            </div>
            <label v-if="draftCandidate" class="review-check">
              <input v-model="includeCandidate" type="checkbox" :disabled="busy"/> Include this candidate when staging
            </label>
          </fieldset>
          <div class="review-actions">
            <button type="button" :disabled="busy || !canAttach || draft.base.revision !== source?.revision"
                    @click="draft.anchor = selection()">Use editor selection</button>
            <button v-if="draft.anchor" type="button" :disabled="busy" @click="draft.anchor = undefined">Remove position</button>
            <span v-if="draft.anchor" class="review-muted">{{ draft.anchor.start === draft.anchor.end ? "Position attached" : "Selected span attached" }}</span>
          </div>
          <fieldset v-if="proposalLibrary.length" :disabled="busy">
            <legend>Link earlier proposals</legend>
            <input v-model="linkQuery" type="search" aria-label="Filter proposals by ID" placeholder="Find a proposal ID"/>
            <label v-for="bundle in linkChoices" :key="bundle.path" class="review-check">
              <input v-model="links" type="checkbox" :value="bundle.path" :disabled="draft.bundles.some(item => item.path === bundle.path)">
              <span>{{ bundle.manifest.id }} · {{ bundle.manifest.contributor }}
                <span v-if="draft.bundles.some(item => item.path === bundle.path)" class="review-muted"> · Included in merge</span></span>
            </label>
          </fieldset>
          <div class="review-actions review-draft-actions">
            <button type="button" :disabled="busy" @click="draft = undefined; links = []">Discard draft</button>
            <button type="submit" :disabled="!canSubmit || unresolved > 0">Stage proposal</button>
          </div>
        </form>
        <p v-if="!visibleProposals.length">No proposals for this target.</p>
        <details v-for="bundle in visibleProposals" :key="bundle.path" :data-bundle="bundle.path"
                 :class="{ 'review-highlight': highlighted === bundle.path }"
                 @toggle="($event.currentTarget as HTMLDetailsElement).open && inspect(bundle)">
          <summary><strong>{{ bundle.manifest.id }}</strong><span>{{ bundle.manifest.contributor }} · {{ bundle.manifest.created_at }}</span></summary>
          <p v-if="bundle.path === linkedProposal?.path && !proposals.includes(bundle)" class="review-muted">Target {{ bundle.manifest.target.urn }}</p>
          <ProposalReader v-if="details[bundleFile(bundle, 'proposal.yaml')]" :source="details[bundleFile(bundle, 'proposal.yaml')]"/>
          <div class="review-actions">
            <button v-if="currentAnchor(bundle)" type="button" @click="locate(bundle)">Go to source</button>
            <button v-if="matchingCandidate(bundle)" type="button" :disabled="!canSubmit"
                    @click="compareSavedCandidate(bundle)">Compare candidate in Monaco</button>
            <button v-for="name in Object.keys(bundle.manifest.files)" :key="name" type="button"
                    @click="download(bundleFile(bundle, name))">{{ name }}</button>
            <button type="button" @click="download(bundle.path)">Manifest</button>
          </div>
        </details>
      </template>
      <section v-else-if="tab === 'History'" aria-label="Review decisions" :aria-busy="historyLoading || loading">
        <p v-if="historyLoading || loading" role="status">Loading review history…</p>
        <p v-else-if="historyError" class="knowledge-error" role="alert">Review history could not be loaded. {{ historyError }}</p>
        <p v-else-if="!history?.entries.length">No review decisions recorded for this project.</p>
        <p v-if="history?.unreadable" class="review-muted" role="status">
          {{ history.unreadable === 1 ? "1 entry could not be read." : `${history.unreadable} entries could not be read.` }}
        </p>
        <ol v-if="visibleHistory.length" class="review-history" role="list">
          <li v-for="{ entry, proposals: references } in visibleHistory" :key="entry.line">
            <h4>{{ entry.disposition.replace(/[_-]/g, " ") }}<template v-if="entry.target"> · {{ entry.target }}</template></h4>
            <p v-if="entry.editor || entry.created" class="review-muted review-history-byline">
              <span v-if="entry.editor">{{ entry.editor }}</span>
              <time v-if="entry.created" :datetime="entry.created">{{ decisionDate(entry.created) }}</time>
            </p>
            <p v-if="entry.rationale" class="review-history-rationale">{{ entry.rationale }}</p>
            <dl v-if="entry.baseRevision || entry.resultingRevision || entry.commit" class="review-history-revisions">
              <template v-if="entry.baseRevision"><dt>Base revision</dt><dd><code>{{ entry.baseRevision }}</code></dd></template>
              <template v-if="entry.resultingRevision"><dt>Resulting revision</dt><dd><code>{{ entry.resultingRevision }}</code></dd></template>
              <template v-if="entry.commit"><dt>Git commit</dt><dd><code>{{ entry.commit }}</code></dd></template>
            </dl>
            <h5 v-if="references.length || entry.proposalIds.length">Contributing proposals</h5>
            <ul v-if="references.length" class="review-history-proposals" role="list">
              <li v-for="reference in references" :key="reference.key">
                <button v-if="reference.bundle" type="button" class="review-history-link" @click="openHistoryProposal(reference.bundle)">
                  {{ reference.label }}<q-icon name="arrow_forward" size="14px" aria-hidden="true"/>
                </button>
                <span v-else>{{ reference.label }} <span class="review-muted">· Unavailable</span></span>
                <span v-if="reference.bundle" class="review-muted">{{ reference.bundle.manifest.contributor }}</span>
              </li>
            </ul>
            <p v-else-if="entry.proposalIds.length">{{ entry.proposalIds.join(", ") }}</p>
            <div v-if="entry.disposition === 'applied' && entry.baseRevision && entry.resultingRevision && entry.target" class="review-actions review-history-comparison">
              <button type="button" :aria-disabled="comparingDecision !== undefined" @click="compareDecision(entry)">
                {{ comparingDecision === entry.line ? "Loading changes…" : "View applied changes" }}
              </button>
            </div>
            <p v-if="historyComparisonError?.line === entry.line" class="knowledge-error" role="alert">{{ historyComparisonError.message }}</p>
          </li>
        </ol>
        <div v-if="hasHistory" class="review-actions">
          <button v-if="history && historyLimit < history.entries.length" type="button" @click="historyLimit += historyPageSize">Show older decisions</button>
          <button v-if="historyError" type="button" @click="loadHistory">Retry</button>
          <button type="button" @click="download(reviewDecisionPath)">Download history</button>
        </div>
      </section>
      <template v-else>
        <form v-if="tab === 'Evidence' && catalogue.canUpload" class="review-form" @submit.prevent="postEvidence">
          <label>Evidence file<input ref="fileInput" type="file" :disabled="busy" required @change="selectFile"/></label>
          <label>Origin<input v-model="metadata.origin" :disabled="busy" required maxlength="2048" placeholder="Publication, URL or archive"/></label>
          <label>Attribution<input v-model="metadata.attribution" :disabled="busy" required maxlength="2048"/></label>
          <label>Relation to this review<textarea v-model="metadata.relation" :disabled="busy" required maxlength="2048" rows="2"/></label>
          <div class="review-actions"><button type="submit" :disabled="!file || !canSubmit">Stage evidence</button></div>
        </form>
        <p v-if="!materials.length">No {{ tab.toLowerCase() }} records.</p>
        <ul v-else class="review-materials">
          <li v-for="item in materials" :key="item.path">
            <span>{{ item.path.slice('review/'.length) }}</span>
            <div class="review-actions">
              <button v-if="/\.(?:txt|md|jsonl?|ya?ml|csv)$/i.test(item.path)" type="button" @click="previewMaterial(item.path, item.size)">Inspect</button>
              <button type="button" @click="download(item.path)">Download</button>
            </div>
          </li>
        </ul>
        <div v-if="evidencePreview" class="review-material-preview">
          <h4>{{ evidencePreview.path }}</h4>
          <pre>{{ evidencePreview.text }}</pre>
        </div>
      </template>
      <p v-if="sourceDirty" class="review-muted">Save source changes to attach a new position or start a proposal.</p>
    </div>
  </section>
</template>

<style scoped>
.project-review { min-width: 0; color: var(--klab-ink); overflow-wrap: anywhere; }
.review-heading, .review-actions, .review-tabs { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.review-heading { justify-content: space-between; }
.review-heading h3 { margin: 0; font-size: 17px; font-weight: 650; }
.review-heading p { margin: 2px 0 0; color: var(--klab-muted); }
.review-tabs { margin-block: 12px; padding-bottom: 8px; border-bottom: 1px solid var(--klab-border); }
.review-tabs button[aria-pressed="true"], .review-actions button[type="submit"] {
  background: var(--klab-selection); color: var(--klab-action);
}
.review-form { display: grid; gap: 9px; margin-block: 12px; padding-block: 0 12px; border-bottom: 1px solid var(--klab-border); }
.review-form label { display: grid; gap: 4px; font-weight: 550; }
.review-form input, .review-form textarea, .review-form fieldset { min-width: 0; }
.review-form textarea {
  width: 100%; padding: 7px; border: 1px solid var(--klab-field-border); border-radius: var(--klab-radius);
  background: var(--klab-surface); color: var(--klab-ink); resize: vertical;
}
.review-form textarea[spellcheck="false"], .project-review pre { font-family: var(--klab-mono); }
.review-form .review-check { display: flex; align-items: start; gap: 6px; font-weight: 400; }
.review-form p, .review-form h4 { margin: 0; }
.review-draft-actions { position: sticky; bottom: 0; z-index: 1; padding-block: 7px; background: var(--klab-surface-alt); }
.project-review details { margin-block: 8px; padding-block: 5px; border-bottom: 1px solid var(--klab-border); }
.project-review summary { cursor: pointer; }
.project-review summary span { margin-inline-start: 8px; color: var(--klab-muted); font-size: 12px; }
.project-review pre { margin: 6px 0; white-space: pre-wrap; overflow-wrap: anywhere; }
.review-comment p { margin: 8px 0; white-space: pre-wrap; }
.review-muted { color: var(--klab-muted); }
.review-highlight { background: var(--klab-selection); }
.review-materials { list-style: none; padding: 0; }
.review-materials li { padding: 7px 0; border-bottom: 1px solid var(--klab-border); }
.review-materials .review-actions { margin-top: 4px; }
.review-history, .review-history-proposals { list-style: none; margin: 0; padding: 0; }
.review-history > li { padding-block: 12px; border-bottom: 1px solid var(--klab-border); }
.review-history > li:first-child { padding-top: 0; }
.review-history h4, .review-history h5, .review-history p, .review-history dl { margin: 0; }
.review-history h4 { font: 650 15px/1.4 var(--klab-font); }
.review-history h4::first-letter { text-transform: uppercase; }
.review-history h5 { margin-top: 10px; font: 600 13px/1.4 var(--klab-font); }
.review-history .review-history-rationale { margin-top: 7px; white-space: pre-wrap; }
.review-history .review-history-revisions { display: grid; grid-template-columns: max-content minmax(0, 1fr); gap: 4px 10px; margin-top: 8px; }
.review-history-revisions dt { color: var(--klab-muted); }
.review-history-revisions dd { margin: 0; }
.review-history-revisions code { font-family: var(--klab-mono); }
.review-history-byline, .review-history-proposals li { display: flex; flex-wrap: wrap; align-items: baseline; gap: 2px 8px; }
.review-history-byline { font-size: 12px; }
.review-history-proposals li { margin-top: 3px; }
.project-review .review-history-link {
  display: inline-flex; gap: 4px; align-items: center; min-width: 0; padding: 2px 0;
  border-color: transparent; background: transparent; color: var(--klab-action); text-decoration: underline; text-align: start;
}
.project-review .review-history-link:hover { background: var(--klab-selection); }
.project-review .review-history-link:focus-visible { outline: 2px solid var(--klab-action); outline-offset: 2px; }
.review-history + .review-actions { margin-top: 10px; }
.review-history-comparison { margin-top: 8px; }
</style>
