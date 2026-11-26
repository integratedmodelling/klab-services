import { parseDocument, stringify } from "yaml";

export interface ReviewTarget { project: string; kind: string; urn: string }
export interface ReviewAnchor { start: number; end: number; statement?: string }
export interface ReviewInput { manifest: string; sha256: string }
export interface ReviewBundle {
  path: string;
  sha256: string;
  manifest: {
    kind: "proposal" | "comment";
    id: string;
    contributor: string;
    status: string;
    created_at: string;
    payload_schema: string;
    target: ReviewTarget;
    base: { state: string; path?: string; revision?: string; file?: string };
    anchor?: ReviewAnchor;
    inputs: ReviewInput[];
    files: Record<string, { sha256: string; size: number }>;
  };
}
export interface ReviewCatalogue {
  canComment: boolean;
  canPropose: boolean;
  canUpload: boolean;
  bundles: ReviewBundle[];
  material: { path: string; size: number }[];
  unreadable: string[];
}
export const reviewDecisionPath = "review/decisions.jsonl";
export interface ReviewSnapshot { size: number; sha256: string }
export interface AppliedComparison { saved: string; candidate: string; language: string; caption: string }
export interface ReviewDecision {
  line: number;
  id?: string;
  disposition: string;
  editor?: string;
  created?: string;
  target?: string;
  rationale?: string;
  baseRevision?: string;
  resultingRevision?: string;
  commit?: string;
  proposalIds: string[];
  proposalRevisions: string[];
  proposalInputs: { manifest: string; sha256?: string }[];
  snapshots?: { base: ReviewSnapshot; result: ReviewSnapshot };
}
export function readReviewDecisions(source: string) {
  const entries: ReviewDecision[] = [];
  let unreadable = 0;
  const field = (value: unknown) => typeof value === "string" && value.trim() ? value.trim() : undefined;
  const list = (value: unknown) => Array.isArray(value) ? [...new Set(value.flatMap(item => field(item) ?? []))] : [];
  const snapshot = (value: unknown): ReviewSnapshot | undefined => record(value)
    && typeof value.size === "number" && Number.isSafeInteger(value.size) && value.size >= 0
    && typeof value.sha256 === "string" && /^[a-f0-9]{64}$/.test(value.sha256)
    ? { size: value.size, sha256: value.sha256 } : undefined;
  source.split(/\r?\n/).forEach((line, index) => {
    if (!line.trim()) return;
    try {
      const item: unknown = JSON.parse(line);
      if (record(item)) {
        const disposition = field(item.disposition);
        if (disposition) {
          const base = record(item.snapshots) ? snapshot(item.snapshots.base) : undefined;
          const result = record(item.snapshots) ? snapshot(item.snapshots.result) : undefined;
          entries.push({
            line: index + 1, id: field(item.id),
            disposition,
            editor: field(item.editor), created: field(item.created_at) ?? field(item.timestamp),
            target: record(item.target) ? field(item.target.urn) : field(item.target), rationale: field(item.rationale),
            baseRevision: field(item.base_revision), resultingRevision: field(item.resulting_revision),
            commit: field(item.resulting_commit) ?? field(item.commit),
            snapshots: base && result ? { base, result } : undefined,
            proposalIds: list(item.proposal_ids), proposalRevisions: list(item.proposal_revision_ids),
            proposalInputs: Array.isArray(item.inputs) ? item.inputs.flatMap(input => {
              if (!record(input)) return [];
              const manifest = field(input.manifest);
              return manifest && manifest.startsWith("review/proposals/") ? [{ manifest, sha256: field(input.sha256) }] : [];
            }) : [],
          });
          return;
        }
      }
    } catch { /* A damaged entry should not hide the rest of the ledger. */ }
    unreadable++;
  });
  return { entries: entries.reverse(), unreadable };
}
export function reviewInput(bundle: ReviewBundle): ReviewInput {
  return { manifest: bundle.path, sha256: bundle.sha256 };
}
export function bundleFile(bundle: ReviewBundle, filename: string) {
  return bundle.path.slice(0, -"/manifest.json".length) + "/" + filename;
}
export function base64(bytes: Uint8Array) {
  let binary = "";
  for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
  return btoa(binary);
}
export function encodeReviewText(text: string) { return base64(new TextEncoder().encode(text)); }
export function decodeReviewBytes(content: string) {
  return Uint8Array.from(atob(content), character => character.charCodeAt(0));
}
export function reviewId() { return "review-" + crypto.randomUUID(); }

export type RecordValue = Record<string, unknown>;
export function record(value: unknown): value is RecordValue {
  return Boolean(value && typeof value === "object" && !Array.isArray(value));
}
export function parseProposal(text: string): RecordValue {
  const document = parseDocument(text, { schema: "core", strict: true });
  if (document.errors.length) throw new Error(document.errors[0].message);
  const data: unknown = document.toJS({ maxAliasCount: 20 });
  if (!record(data) || !record(data.proposal)) throw new Error("The proposal must contain a proposal record.");
  return data;
}
export function mentionsStatement(text: string, statement: string) {
  const proposal = parseProposal(text).proposal as RecordValue;
  return Array.isArray(proposal.assets) && proposal.assets.some(asset =>
    record(asset) && (asset.qualified_name === statement || asset.asset_id === statement));
}
const stableIds = ["comment_id", "commentId", "evidence_id", "action_id", "decision_id", "gap_id",
  "asset_id", "source_id", "ontology_id", "revision_id"];
export function recordId(value: unknown) {
  if (!record(value)) return;
  const key = stableIds.find(key => typeof value[key] === "string");
  if (key) return key + ":" + value[key];
}

function newProposal(target: string): RecordValue {
  return {
    proposal_schema: "classpath:/schemas/llm/domain-context-proposal.schema.json", context_pack_version: "1.3",
    proposal: {
      title: "Proposal for " + target,
      scope: { domain: target, purpose: "Describe the proposed change", requested_tier: 1, target_community: "Ontology contributors",
        tier_1_context_required: false, tier_1_context_status: "not_required", included: [], excluded: [],
        spatial_context: null, temporal_context: null, intended_scales: [] },
      source_policy: { authority_criteria: [], inference_policy: "Explain the basis for each proposed change" },
      sources: [], existing_ontologies: [], root_scope_aliases: [], evidence: [], assets: [], dependency_order: [],
      upstream_gaps: [], root_domain_gaps: [], actions: [],
      orthogonality_review: { hard_unambiguity_gate_passed: false, pairwise_review_complete: false, matrix: [],
        unresolved_ambiguities: [], unresolved_overlaps: [] },
      validation: {
        schema_valid: false, references_resolve: false, acyclic: false, requested_tier_valid: false,
        mandatory_tier_1_context_present: false, tier_ancestry_valid: false, orthogonality_reviewed: false,
        internally_unambiguous: false, named_composition_reviews_complete: false,
        semantic_review: "Pending", syntax_validation: "Pending", warnings: [],
      },
      corpus_feedback: { state: "open", coverage_comments: [], structural_comments: [], cross_cutting_issues: [],
        decisions: [], requested_changes: [] }, change_log: [],
    },
  };
}
export type ProposalPath = (string | { id: string })[];
export interface ProposalConflict {
  path: ProposalPath;
  choices: { value: unknown; inputs: ReviewBundle[] }[];
}
export function pathLabel(path: ProposalPath) {
  return path.slice(1).map(part => typeof part === "string" ? part.replaceAll("_", " ") : part.id.split(":").slice(1).join(":")).join(" / ");
}
function segmentIndex(container: unknown, part: ProposalPath[number]): string | number | undefined {
  if (typeof part === "string") return record(container) && Object.hasOwn(container, part) ? part : undefined;
  if (!Array.isArray(container)) return;
  const index = container.findIndex(item => recordId(item) === part.id);
  if (index >= 0) return index;
}
export function proposalValue(document: unknown, path: ProposalPath): unknown {
  let value = document;
  for (const part of path) {
    const key = segmentIndex(value, part);
    if (key === undefined) return;
    value = (value as RecordValue)[key];
  }
  return value;
}
export function setProposalValue(document: RecordValue, path: ProposalPath, value: unknown): boolean {
  const owner = proposalValue(document, path.slice(0, -1));
  const part = path[path.length - 1];
  if (typeof part === "string" && record(owner) && !["__proto__", "prototype", "constructor"].includes(part)) {
    if (value === undefined) delete owner[part];
    else owner[part] = structuredProposalCopy(value);
    return true;
  }
  const key = segmentIndex(owner, part);
  if (key === undefined) return false;
  (owner as RecordValue)[key] = structuredProposalCopy(value);
  return true;
}
export function structuredProposalCopy<T>(value: T): T {
  return value === undefined ? value : JSON.parse(JSON.stringify(value)) as T;
}
export function sameProposalValue(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (Array.isArray(a) && Array.isArray(b))
    return a.length === b.length && a.every((item, index) => sameProposalValue(item, b[index]));
  if (record(a) && record(b))
    return Object.keys(a).length === Object.keys(b).length
      && Object.entries(a).every(([key, value]) => Object.hasOwn(b, key) && sameProposalValue(value, b[key]));
  return false;
}
const lifecycle = new Set(["id", "revision_id", "iteration", "supersedes_revision", "status", "generated_at",
  "iteration_control", "validation"]);
export function proposalChanges(before: RecordValue, after: RecordValue, managed: ReadonlySet<string> = lifecycle) {
  const changes: { path: ProposalPath; before: unknown; after: unknown; statement?: string }[] = [];
  function compare(a: unknown, b: unknown, path: ProposalPath, statement?: string) {
    if (sameProposalValue(a, b)) return;
    const location = [a, b].find(value => record(value) && typeof value.qualified_name === "string") as RecordValue | undefined;
    statement = (location?.qualified_name as string | undefined) ?? statement;
    if (record(a) && record(b)) {
      for (const key of new Set([...Object.keys(a), ...Object.keys(b)])) {
        if (path.length === 1 && managed.has(key)) continue;
        compare(a[key], b[key], [...path, key], statement);
      }
    } else if (Array.isArray(a) && Array.isArray(b) && [...a, ...b].every(item => recordId(item))) {
      const left = new Map(a.map(item => [recordId(item)!, item]));
      const right = new Map(b.map(item => [recordId(item)!, item]));
      for (const id of new Set([...left.keys(), ...right.keys()]))
        compare(left.get(id), right.get(id), [...path, { id }], statement);
    } else changes.push({ path, before: a, after: b, statement });
  }
  compare(before.proposal, after.proposal, ["proposal"]);
  return changes;
}
export function serializeProposal(document: RecordValue) { return stringify(document); }

export function prepareProposal(target: string, id: string, date: string,
                                sources: { bundle: ReviewBundle; text: string }[], hasPredecessor: boolean) {
  const conflictPaths = new Map<string, ProposalPath>();
  const values = sources.map(source => parseProposal(source.text));
  const baseline = values[0] ?? newProposal(target);
  const draft = structuredProposalCopy(baseline);
  const proposal = draft.proposal as RecordValue;
  const predecessor = hasPredecessor ? sources[0]?.bundle : undefined;
  const iteration = predecessor
    ? Math.max(0, ...values.map(value => Number((value.proposal as RecordValue).iteration) || 0)) + 1 : 1;
  function merge(current: unknown, incoming: unknown, path: ProposalPath): unknown {
    if (current === undefined) return structuredProposalCopy(incoming);
    if (sameProposalValue(current, incoming)) return current;
    if (Array.isArray(current) && Array.isArray(incoming)) {
      const ret = [...current];
      for (const item of incoming) {
        const id = recordId(item);
        const index = id ? ret.findIndex(candidate => recordId(candidate) === id)
          : ret.findIndex(candidate => sameProposalValue(candidate, item));
        if (index < 0) ret.push(structuredProposalCopy(item));
        else if (id) ret[index] = merge(ret[index], item, [...path, { id }]);
      }
      return ret;
    }
    if (record(current) && record(incoming)) {
      for (const [key, value] of Object.entries(incoming)) {
        if (key === "__proto__" || key === "prototype" || key === "constructor" || path.length === 1 && lifecycle.has(key)) continue;
        current[key] = merge(Object.hasOwn(current, key) ? current[key] : undefined, value, [...path, key]);
      }
      return current;
    }
    conflictPaths.set(JSON.stringify(path), path);
    return current;
  }
  values.slice(1).forEach(value => merge(proposal, value.proposal, ["proposal"]));
  const conflicts: ProposalConflict[] = [];
  for (const path of conflictPaths.values()) {
    // A conflict about the parent value also resolves any incompatible descendant values.
    if (path.some((_, index) => index > 0 && conflictPaths.has(JSON.stringify(path.slice(0, index))))) continue;
    const choices: ProposalConflict["choices"] = [];
    values.forEach((document, index) => {
      const value = proposalValue(document, path);
      if (value === undefined) return;
      const existing = choices.find(choice => sameProposalValue(choice.value, value));
      if (existing) existing.inputs.push(sources[index].bundle);
      else choices.push({ value, inputs: [sources[index].bundle] });
    });
    if (choices.length > 1) conflicts.push({ path, choices });
  }
  const proposalId = predecessor && typeof proposal.id === "string" ? proposal.id : id;
  const supersedes = predecessor?.manifest.id ?? null;
  const inheritedIds = (field: string) => [...new Set(values.flatMap(value => {
    const control = (value.proposal as RecordValue).iteration_control;
    return record(control) && Array.isArray(control[field]) ? control[field] : [];
  }).filter(value => typeof value === "string"))];
  const unresolved = inheritedIds("unresolved_feedback_ids");
  const proposed = inheritedIds("proposed_action_ids");
  const applied = inheritedIds("applied_action_ids");
  Object.assign(proposal, {
    id: proposalId, revision_id: id, iteration, supersedes_revision: supersedes,
    status: "draft", generated_at: date,
    iteration_control: { current_iteration: iteration, previous_revision_id: supersedes, next_iteration: iteration + 1,
      state: "open_for_feedback", unresolved_feedback_ids: unresolved, proposed_action_ids: proposed, applied_action_ids: applied },
    validation: (newProposal(target).proposal as RecordValue).validation,
  });
  return { document: draft, baseline, conflicts, proposalId, supersedes, iteration,
    inputs: values.map((document, index) => ({ document, bundle: sources[index].bundle })) };
}
