# Hydrology dossier 0.1 to proposal review 1: mapping boundary

Source inspected: `task-4/imod-strawman/experiments/strawman-2026/bootstrap/hydrology/dossier.json`, `METHOD.md`, `INSTRUMENTATION_HANDOFF.md` (2026-10-03).

The research index is not a context-pack proposal. Do not submit it with the proposal MIME type. Preserve the full original research index, source-first questions and narrative as supporting-material attachments. A context-pack 1.3 proposal remains required. This is an explicit mapping specification, not an implemented automatic converter; ambiguous conversions must remain blocked.

| Research 0.1 | ProposalReview 1 projection | Constraint |
| --- | --- | --- |
| sources.id, url, locator | Evidence.id, source, locator | Keep title/date/scope/status in the original attachment and proposal sources; excerpt is an actual excerpt only, never synthesized. |
| concepts.id | Concept.id | Preserve stable IDs. Names are not IDs. |
| concepts.category | Concept.kind | subject/process/relationship/event/quality map directly; generic predicate requires a reviewed ATTRIBUTE/REALM/ORDERING classification, never a guessed one. |
| concepts.name | Concept.expression | Qualified candidate reference only; this does not claim the full question was translated. |
| concepts.parent, parent_status | Concept.ancestry and unresolvedSemantics | Proposed parents remain claims. Do not mark derivedType validated without a corresponding server validator. Preserve rationale/upstream issue in proposal alignment/open questions. |
| qualities, parameters | Concept.qualityIds | Resolve each name to a dossier stable ID or retain as unresolved; do not mint IDs for missing candidates. |
| bindings.affects/creates/confers | Concept.affects/creates/confers | Stable IDs plus source evidence. Confers is a semantic proposal/upstream issue, not active executable grammar. |
| bindings.source/target | Concept.sourceType/targetType | Typed endpoint expressions required for relationships. |
| source_ids | evidenceIds | Validate references to Evidence records. |
| questions.id/text/source_order/provenance | Question.id/text; list order; Evidence records | Preserve original order and source provenance. Same-agent questions are not independent holdouts. |
| questions.concept_ids | Question.conceptIds | Structural incidence is validated, not semantic adequacy. |
| questions.expression | Question.observableExpressions | Null means no expression; preserve dependencies in gaps. Never substitute a narrative paraphrase. |
| questions.dependencies/negative | Question.gaps/invalidProbes | Distinguish an invalid semantic case from an expression actually sent to a parser. |
| questions expected meaning | Question.intent | Research 0.1 has no dedicated precise-intent field. Supply a reviewed intent or report the mapping incomplete; expected_type is not enough. |
| quality_summaries | QualityAnalysis | Resolve qualityId; preserve limitation, comparison rule, valueStructure, contextAndScope and evidenceIds. Missing summary concepts remain unresolved, not invented attributes. Preserve overlap in contextAndScope and the source attachment. |
| ambiguities; coverage shortfalls | unresolvedSemantics; coverageShortfalls | Counts are diagnostics; unresolved semantics blocks acceptance when carried in the review projection. |
| validation; grammar_status | Original evidence, not authoritative Check PASS | Only the server-side validator emits authoritative checks for exact bytes/current imports. Existing parse-pass does not prove reference, adaptation, Reasoner, model execution, implication or detection support. |

Candidate.contextDigest binds the proposal's `existing_ontologies` manifest. IMPORT_CONTEXT must independently resolve current imported revisions/hashes on each acceptance; changed context blocks acceptance. A hash of the manifest alone cannot establish that external imports remain current.

READY for review corresponds to frozen inspectable evidence with IN_REVIEW status; only an authorized exact-candidate ACCEPT transition records human acceptance. Application and PR handoff remain BLOCKED.

Remaining integration: the research four-artifact manifest has not been standardized or validated by this backend slice. Full raw research attachments are preserved but not included as a typed, revision-matched four-artifact manifest. Production acceptance stays blocked by the unavailable server validator. Agree artifact roles/media/schema and implement manifest checks in the validator before enabling that gate. No graph consequence engine is included.
