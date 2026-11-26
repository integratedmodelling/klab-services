import { record, type RecordValue, type ReviewInput } from "./project-review";
import type { Source, WorkspaceTreeNode } from "./model";

export const maxCandidateBytes = 2 * 1024 * 1024;
export type CandidateBase =
  | { status: "verified"; revision: string }
  | { status: "unrecorded" | "changed" };
export interface CandidateComparison {
  candidate: string;
  caption: string;
  base: CandidateBase;
  inputs: ReviewInput[];
}

/** Apply only explicit replacements with a unique, nonoverlapping range in the saved snapshot. */
export function candidateFromProposal(proposal: RecordValue, source: Source, outline?: WorkspaceTreeNode): string {
  const assets = (record(proposal.proposal) && proposal.proposal.assets) || [];
  const fragments = Array.isArray(assets) ? assets.filter(asset =>
    record(asset) && typeof asset.candidate_kwv === "string" && asset.candidate_kwv.trim()) : [];
  if (!fragments.length) throw new Error("Add candidate k.WV to a proposal asset or choose a complete candidate file.");
  if (!outline || outline.document?.id !== source.document.id
      || outline.document.sourceVersion !== source.document.sourceVersion)
    throw new Error("Reload the saved ontology to refresh its declaration locations.");

  const urn = source.document.urn ?? source.document.label.split(" [", 1)[0];
  const declarations = new Map<string, WorkspaceTreeNode[]>();
  function visit(nodes: WorkspaceTreeNode[]) {
    for (const node of nodes) {
      const name = urn + ":" + node.label;
      const matches = declarations.get(name) ?? [];
      matches.push(node);
      declarations.set(name, matches);
      visit(node.children);
    }
  }
  visit(outline.children);
  const eol = source.text.match(/\r\n|\r|\n/)?.[0] ?? "\n";
  const edits = fragments.map(asset => {
    const name = typeof asset.qualified_name === "string" ? asset.qualified_name : "the proposal asset";
    const matches = declarations.get(name);
    const start = matches?.[0].offset, length = matches?.[0].length;
    if (matches?.length !== 1 || start === undefined || length === undefined
        || start < 0 || length <= 0 || start + length > source.text.length)
      throw new Error(`Choose a complete candidate file for ${name}, which needs its own source placement.`);
    const replacement = (asset.candidate_kwv as string).trim().replace(/\r\n|\r|\n/g, eol);
    if (/^ontology\b/i.test(replacement))
      throw new Error("Choose the complete ontology file for a candidate that includes an ontology header.");
    const original = source.text.slice(start, start + length);
    const leading = original.match(/^\s*/)?.[0] ?? "";
    const trailing = original.match(/\s*$/)?.[0] ?? "";
    return { start, end: start + length, replacement: leading + replacement + trailing };
  }).sort((a, b) => a.start - b.start);
  const parts: string[] = [];
  let end = 0;
  for (const edit of edits) {
    if (edit.start < end) throw new Error("The proposal replaces overlapping declarations. Choose a complete candidate file.");
    parts.push(source.text.slice(end, edit.start), edit.replacement);
    end = edit.end;
  }
  return parts.join("") + source.text.slice(end);
}
