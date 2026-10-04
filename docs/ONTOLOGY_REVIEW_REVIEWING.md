# Reviewing an ontology proposal

This guide is for domain experts and ontology reviewers evaluating whether proposed meanings are
clear, useful, source-supported and compatible with their imported context. You do not need to be a
backend developer. Your task is to examine the exact candidate, expose gaps and record an accountable
judgment. Use the [editor's guide](ONTOLOGY_REVIEW_EDITING.md) for packet preparation and revision.

Shared decision IDs **OR-01 through OR-12** have the same meaning in both guides. Each unresolved
operation is explained under **DETAILED WORKFLOW TO BE DECIDED**, with the decision needed and an
interim way to preserve useful work. Those entries are proposals for agreement, not adopted policy.

## Current state and review boundary

Verified on **2026-10-04** against newly fetched remote `develop` branches:

| Repository | Verified merged baseline |
| --- | --- |
| klab-services | [`efba529aa2ae384acd4eff6f9e4f33817a04f487`](https://github.com/integratedmodelling/klab-services/commit/efba529aa2ae384acd4eff6f9e4f33817a04f487), containing [PR #83](https://github.com/integratedmodelling/klab-services/pull/83) final head `45ac91ac51770649e3f6f84f004c5f20d176a700`, including schema increment `cf0798712`. |
| klab-ide | [`90b3116c2eab1b712fa5a3e0eeadbd3065207eae`](https://github.com/integratedmodelling/klab-ide/commit/90b3116c2eab1b712fa5a3e0eeadbd3065207eae), containing [PR #1](https://github.com/integratedmodelling/klab-ide/pull/1) final head `474a9f9016d7e3ea18572911cd4c78312227cb71`. |

These guides describe bundled `ontology-expert-review` **1.1**, typed review extension **1**, and
context-pack proposal format **1.3**. Existing flows keep their pinned workflow version. Confirm the
version of the actual service/IDE before applying these instructions; merged source is not proof of
deployment.

**Production acceptance is blocked.** You can inspect, record a permitted review transition, request
changes and inspect a superseding revision. Real server schema validation and isolated parsing are
available. Authoritative import snapshots and isolated Reasoner validation are still missing, and
imported adaptation is blocked. Neither a schema pass nor a parser pass establishes scientific
validity. The workflow does not apply ontology changes, publish a PR or release a worldview.

The native UI has reported compilation, 20 focused tests, isolated source/stage rendering and an
in-memory backend handoff. A live authenticated HTTP session and physical glyph selection through
the complete Monaco bridge remain unverified. Instructions below describe source-backed controls,
with that limit; [OR-12](#or-12) records the remaining operational decision.

## Obtain the case and the right access

Obtain the target ontology asset/URN, flow ID, current stage, proposal ID/revision, review question and
expected scope from the coordinator. Ask for the proposal, candidate ontology if present, comments,
source evidence, question ledger and relevant import references. You can read a supplied packet offline;
protected downloads and persisted workflow actions require the authenticated Resources service.

The workflow distinguishes roles from assignments. Being able to see a review does not mean you can
decide it. An explicitly assigned `REVIEWER` may use transitions admitting REVIEWER in a contributing
review stage, subject to workflow/group access and any response deadline. Assignment does not grant
generic stage editing or attachment uploads. `EDITOR`/`ADMIN` permissions have different effects;
the [action matrix](#state-and-action-reference) shows the limits. A public-read flow is presented as
read-only by the IDE. Missing stages/history may reflect access filtering, not missing work.

### OR-01

**DETAILED WORKFLOW TO BE DECIDED** — Editor appointment, co-authorship, ownership transfer and initial
asset provisioning need a responsible authority and procedure. Identify the author to whom questions
and returned work belong; do not assume that a visible stage owner establishes scientific expertise.
The backend normally returns requested changes to the original flow author.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-01).

### OR-02

**DETAILED WORKFLOW TO BE DECIDED** — Agree reviewer qualifications, appointment, quorum, recusal and
conflicts-of-interest policy. Declare relevant interests and the limits of your expertise to the
coordinator. Backend owner/assignee fields exist, but the native proposal form has no assignment
selector or implemented invitation procedure. Decide how assignment is made and verified, how
reviewers are notified, and how multiple judgments are retained before one transition closes the
stage. No current gate enforces independent scientific consensus.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-02).

## Find the proposal and inspect source beside it

1. In the workspace containing the target ontology, use the asset's context menu **Open flows**, then
   the entry matching the supplied flow ID. Use **Closed flows** for an accessible completed case.
   Entries combine workflow name and shortened flow ID. There is no intermediate clickable
   “Workflows” submenu. If the case is absent, check asset, service and access with the coordinator.
2. Select the relevant stage card. Read its status, candidate revision and previous actor/rationale.
   `IN_REVIEW` means submitted for inspection; `CHANGES_REQUESTED` means returned for editing. The
   source view can be an editable working document, so establish which exact bytes it represents.
3. Use the source editor control with tooltip **Side-to-side workflow review** to pair the document
   and workflow. If several flows are available, choose the intended one. Pairing enables review mode;
   **Toggle review mode** controls marker visibility. Loading a paired stage refreshes its markers.
4. A proposal glyph marks a dossier concept that can be bound to an unambiguous parsed declaration.
   The implemented selection callback focuses that concept in **Concepts & bindings**. This is a
   lexical/checksum association, not semantic verification or approval. Physical mouse-to-Java bridge
   behavior is part of the unverified live boundary in [OR-12](#or-12).
5. Inspect textual attachments with **Inspect source** in **Attachments**. The IDE fetches through the
   authorized attachment API and checks the checksum before showing text. Read the immutable proposal
   and comments as well as the native summary; the summary does not contain every source detail.

Glyphs are deliberately absent for ambiguous/unbound expressions, unsupported versions, dirty source
text or mismatched snapshots. When the candidate includes an ontology artifact, its checksum must
match the parsed document's UTF-8 bytes. Without one, a glyph is only a lexical reference in the
current parsed document. Absence of glyphs does not mean absence of issues. Switching/closing a pair
clears its markers. **DETAILED WORKFLOW TO BE DECIDED (OR-04)** — Arbitrary-span and cross-document
review anchors need a shared contract linking the span, artifact and revision; version 1 does not
supply it. Use stable concept/issue references and exact attachment locators meanwhile.

## Verify what you are being asked to judge

In **Candidate**, record proposal ID, revision ID, superseded revision, proposal and ontology attachment
IDs/checksums, complete ordered action IDs and import-context digest. Keep the flow revision too: it
detects concurrent workflow changes, while the proposal revision identifies the authored content.
Names and local filenames are not substitutes for immutable IDs.

The backend binds decisions to those exact references and independently checks the stored bytes.
`Flow.State.proposalReview` is server-owned; a transition carries a typed command. Clients cannot
write a successful validator result into that stage data. The import digest binds the declared
manifest, not the actual external world. Review-stage aliases expose submitted proposal, ontology,
comments and support to permitted reviewers without changing their original IDs/checksums.

For workflow 1.1 the logical types are `bootstrap-proposal`
(`application/vnd.klab.proposal+yaml`), `bootstrap-comments`
(`application/vnd.klab.comments+json`), `candidate-ontology`
(`application/vnd.klab.ontology`), and `supporting-material` (general files). A research dossier is
supporting evidence, not automatically a valid context-pack proposal. Version 1.0's historical
comments mismatch needs the handling in [OR-11](#or-11).

### OR-03

**DETAILED WORKFLOW TO BE DECIDED** — Agree source standards, provisional domain boundaries/names,
root/tier governance, authority providers, authoritative import revisions/hashes and invalidation
when imports change. Check each claimed imported parent and ask for the exact supporting snapshot.
Lower-tier work needs its authoritative Tier-1 context; only root accesses ODO directly. A missing
parent is an upstream issue, not permission to invent one. Read the [context pack][context],
[ontology language](ONTOLOGY_LANGUAGE.md) and [authority limits](AUTHORITIES.md).
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-03).

### OR-04

**DETAILED WORKFLOW TO BE DECIDED** — Establish the four-artifact transport manifest, comments format,
evidence-role/revision binding and proof that candidate ontology bytes implement the approved action
set against the correct base/preconditions. Current IDs and hashes prevent substitution; they do
not prove that a requested rename, split, deletion or semantic change was correctly implemented.
Inspect that correspondence manually and record missing mappings. The offline pilot supplies local
evidence, not service acceptance; there is no automatic research-dossier conversion contract.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-04).

## Examine meaning, evidence and coverage

Begin with the source-led questions and intended scope before reading the vocabulary as a finished
taxonomy. This is a clean minimal worldview exercise: existing names are provisional, legacy meaning
must earn retention, and the obsolete `decision` namespace is excluded. The [bootstrap sandbox][sandbox]
at `68a6cbc` has 22 partial dossiers, not 22 approved domain ontologies.

Use the native tabs in this order, returning to the attached source whenever a claim needs context:

| Tab | Review work |
| --- | --- |
| **Sources** | Follow stable evidence IDs to actual locators/excerpts. Check what was read, source scope, edition/date and limits. A reputable title or inaccessible abstract does not support every attributed meaning. |
| **Concepts & bindings** | Select each record and inspect definition in the proposal, claimed type, ancestry, bearer/participants, qualities, relationship endpoints and process/event bindings. These are claims until separately validated. |
| **Questions & coverage** | Inspect question text and precise intent, expression(s), concept incidence, imported derivation, positive cases, invalid probes and gaps. Ask whether the expression answers the actual question. |
| **Quality analysis** | Trace proposed attributes, realms and orderings to a quality and source. Check comparison/boundary, nominal versus ordered values, overlap, context and scale. Unknown/unmeasured/disputed is an evidence state, not automatically a predicate. |
| **Issues & checks** | Read unresolved semantics and justified shortfalls before interpreting server checks. Missing records/results are not successful checks. |
| **Decision** | Draft an accountable rationale tied to the exact revision, issue IDs and action set. It is persisted through a transition. |

For each meaning, try a positive example and a counterexample. Challenge ambiguity, hidden mixtures
of observational categories, redundant concepts and unnecessary names for compositions. Distinguish
specialization (`is`) from equivalence (`equals`), and an external authority identifier from an
ontology definition. A keyword or an asserted `derivedType` is not proof of imported ancestry.

The bootstrap targets are five subjects with qualities, five processes with governing qualities and
evidenced affects/creates/proposed-confers bindings, five relationships with typed endpoints, five
bounded events with bindings, and fifteen important non-specialist questions. Do not reward quota
padding. Ask whether shortfalls are principled, sources are weak or a needed distinction is genuinely
missing. A question slot containing a methodological probe is not automatically an answerable
scientific question.

Keep observable meaning, expression result category, model availability and runtime execution separate.
For example, `change in` a quality yields a process; an event-presence expression yields a quality.
A single expression can be only one component of a broader scientific question. Preserve the original
question when correcting that expression.

Use hydrology for a small, concrete audit, not as an approved template. The [dossier][hydrology]
raises catchment footprint versus volume and channel versus water-body identity. It also corrects a
post-fire sediment question: concentration alone cannot answer transported sediment load, which needs
discharge and contextual integration. Steady throughflow can leave storage unchanged, so a process
does not imply net storage loss. Check the latest correction record and JSON status instead of treating
earlier exploratory paragraphs as the final position.

Develop fresh questions and counterexamples before accepting the author's coverage claim. Record who
produced each probe, what candidate information they saw, and whether it was actually executed.
Automated candidate-visible probes are useful evidence, but neither blind holdouts nor independent
domain-expert review. The sandbox's fresh agent probes were candidate-visible.

### OR-05

**DETAILED WORKFLOW TO BE DECIDED** — Define independent/blind probe arrangements, per-expression
reference/type/ancestry results, imported adaptation, an isolated Reasoner over immutable snapshots,
and representative observation/model checks. Agree required outcomes and who may attest to them.
Do not casually run the existing Reasoner validation as read-only: it may synchronize authoritative
knowledge. Change/cessation must remain driven by an occurrent (a process or event), `change in X` resolves separately, and
unresolved change continues open-world rather than implying zero. Implication/detection are
syntax-only here; proposed `confers` has no active executable clause. Review does not demonstrate
execution of a future consequence engine. [Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-05).

## Separate automatic checks from judgment

The server's `DOCUMENT_SCHEMA` now uses NetworkNT 2.0.4 and the fixed context-pack 1.3 Draft 2020-12
schema. It checks document structure, including nested constraints. Strict input parsing rejects
duplicate keys, additional roots/documents, trailing content and invalid UTF-8. The server selects
the schema; a client cannot supply its own replacement. These facts do not establish scientific validity.

`PARSER` checks exact ontology syntax with the real Worldview parser. `ADAPTATION` runs real conversion
in a fresh nonpersistent scope, but imported declarations are unavailable there. `IMPORT_CONTEXT`
and `REASONER` remain blocked by default. A missing candidate ontology leaves parser/adaptation
`NOT_RUN`. Inspect messages rather than treating every non-error display as a pass.

`FAIL` means a failed check; `BLOCKED` means the required result could not be established; `NOT_RUN`
means no result from execution. None is `PASS`. A schema-invalid but bindable proposal can enter review
for correction. Checks are for the submitted bytes, not the current unsaved source buffer.

Human `SCIENTIFIC_REVIEW` is recorded separately on authorized acceptance. `APPLICATION` and
`PR_HANDOFF` stay blocked. Client self-reports and offline results cannot forge those states.
The offline four-artifact pilot's `PILOT_MANIFEST` and `ROOT_CONTEXT` labels are not service check
types; its zero exit status may accompany acceptance BLOCKED. Older [backend outcome](../BACKEND_RESULT.md)
and [IDE notes][ide-notes] predate the schema increment; the merged [schema implementation evidence][increment]
supersedes that specific historical blocker.

## Give actionable feedback and return work

Write each finding so the editor can respond without guessing: identify the candidate revision,
concept/question/action ID, field or expression, observed problem, source/counterexample, requested
change and condition for considering it resolved. Distinguish a correction needed for meaning from
a preference about wording. State when evidence is insufficient and what would resolve it.

For example: “For question hydrology-Q12, the proposed concentration expression does not establish
transported load. Retain the question; identify discharge and integration dependencies, or record
the missing full expression. Re-review when those mappings and their evidence are supplied.” This
is a proposed change, not a claim that the source has already been edited or that the issue is closed.

### OR-06

**DETAILED WORKFLOW TO BE DECIDED** — Choose persistent feedback threads, source-line comments, issue
states and the route from reviewer suggestions to approved revision/action records. The native
**Decision / Your rationale** is usable now with a permitted transition; it has no separate Save-note
operation. Proposal review does not implement comment creation through margin double-click. An
assigned-reviewer role alone cannot upload attachments or edit the frozen dossier. An authorized
stage editor may append admitted comments/support; agree how reviewers deliver such material and
how its authorship/revision is preserved. Keep a local issue ledger meanwhile.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-06).

To request changes in an accessible current review stage:

1. Fill **Your rationale** with the findings and requested response, tied to stable IDs. Reviewer
   stage records are inspection-only; do not change the candidate/dossier to make the finding disappear.
2. In **-- Choose the next stage --**, select the action **Return the asset to its editor.**
   (`request-changes` in the tooltip). In community review, an admitted reviewer may also use
   **Send the public proposal for another iteration.** (`public-input`). Labels include the target
   description as well as this action text.
3. Press **Submit**, then inspect **Confirm exact proposal review**. Check candidate revision,
   predecessor, proposal/ontology references, checksums, complete ordered action IDs, import digest
   and rationale before **OK**. **Cancel** retains the draft. The command, bytes and flow revision are
   frozen before the dialog opens; a late upload cannot silently change your decision.
4. Check the successful response and the stages/history available to you. The transition creates
   `editing` with `CHANGES_REQUESTED`, owned by the original author by default. Your access projection
   may hide that editing stage and its history entry; have the author/coordinator confirm the return
   when necessary. If the call fails or returns no usable response, copy notes and reconcile with
   the current case before trying again.

Re-review a resubmission as a new exact candidate: same proposal ID, fresh revision ID, correct
`supersedes_revision`, new proposal artifact and updated actions/dossier. Compare both changed and
affected unchanged meanings, source/import changes, and every requested correction. An unchanged
ontology can legitimately retain its earlier artifact reference; changed bytes need a different
binding. Earlier approval or a refreshed flow revision does not approve the new content. Required
review access/assignment must be checked again for the new stage.

## Public input and disagreements

### OR-07

**DETAILED WORKFLOW TO BE DECIDED** — Decide external reviewer onboarding, public/anonymous access,
invitations, moderation, accessible feedback channels and permitted publication of source evidence.
`open-input` is available to admitted ADMIN/EDITOR actors and advances the same candidate to
`community-review`; it does not contact anyone. `PUBLIC` is the authenticated known-real-person
mechanism, including email and `workflow.knownRealPerson=true`, not anonymous participation.
`publicRead` permits identified callers to browse and is read-only in the native UI. Prepare questions
and an exact-version packet for external human feedback, then have the coordinator establish the
participation/sharing route. No current button promises anonymous comments or invitations.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-07).

### OR-08

**DETAILED WORKFLOW TO BE DECIDED** — Agree escalation, adjudicator, disagreement records, coverage
shortfall waivers and treatment of unresolved scientific alternatives. Identify the precise competing
meanings and their evidence; distinguish lack of evidence from incompatible interpretations. Route
root/import or cross-domain questions to their responsible owners once identified. Do not erase
unresolved semantics or infer consensus from a stage description saying “assigned reviewers have
recorded a decision.” The software does not implement a quorum vote.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-08).

## State and action reference

The [workflow YAML][workflow] is the basis for this matrix. Every row is additionally constrained by
workflow/stage/group access, current state, ownership or assignment, response deadline and exact
candidate/flow revision. A reviewer who only has REVIEWER cannot accept or open community review.

| From | Transition ID | To | Declared roles | Meaning |
| --- | --- | --- | --- | --- |
| `INIT` | `initialize` | `editing` | ADMIN, EDITOR | Start authoring; native initial submission is atomic. |
| `editing` | `submit` | `peer-review` | ADMIN, EDITOR | Submit or resubmit; reviewers inspect the resulting candidate. |
| `peer-review`, `community-review` | `request-changes` | `editing` | ADMIN, EDITOR, REVIEWER | Return for correction with rationale. |
| `peer-review` | `open-input` | `community-review` | ADMIN, EDITOR | Advance the same candidate for community input. |
| `community-review` | `public-input` | `editing` | ADMIN, REVIEWER | Return public input for another iteration. |
| `peer-review` | `accept-peer-review` | `accepted` | ADMIN, EDITOR | Exact-candidate gate exists; production acceptance blocked. |
| `community-review` | `accept-community-review` | `accepted` | ADMIN, EDITOR | Same gate and current blockers. |
| `peer-review` | `reject-peer-review` | `rejected` | ADMIN, REVIEWER | Terminal rejection of the exact candidate with rationale. |

There is no direct community-rejection transition. Request changes when another iteration is intended;
rejection closes the case. For permitted peer rejection, the action text is **Reject after peer review.**;
use **Your rationale**, the transition selector, **Submit** and exact confirmation as above. This records
rejection, not source deletion. Do not use a terminal decision merely to save intermediate notes.

### OR-09

**DETAILED WORKFLOW TO BE DECIDED** — Agree acceptance/rejection authority and meaning, partial or
action-level approval/deferral, and a renewed-review route after rejection or changed dependencies.
Current decisions bind the whole candidate and action set. An acceptance attempt requires the exact
ontology, exactly one PASS each for IMPORT_CONTEXT, DOCUMENT_SCHEMA, PARSER, ADAPTATION and REASONER,
no dossier unresolved semantics, and explicit authenticated human rationale. The server reruns checks;
the native UI disables acceptance when required results are missing/non-PASS. Default production
checks still block it. Do not treat synthetic acceptance fixtures as validation of a real ontology.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-09).

The protocol's terminal `accepted` state would alias the verified proposal as `final-proposal` and
ontology as `accepted-ontology`, committing them with review data, source closure and history in the
flow aggregate. Optional comments/support are not all copied automatically to the terminal stage;
preserve their review-stage references. The rejected state's declared visibility differs from peer
review: do not assume all former assignees can inspect the complete terminal record. Evidence-access
and retention arrangements remain [OR-11](#or-11).

### OR-10

**DETAILED WORKFLOW TO BE DECIDED** — Decide authoritative application, approved-action execution,
Git/PR destination and owner, release authority, publication, rollback and repeat validation. Even
an accepted review does not perform these operations. `APPLICATION` and `PR_HANDOFF` remain BLOCKED.
The YAML's publication provenance label is not proof of publication. A future handoff should carry
exact candidate/base/import references, human rationale, unresolved gates and verification results.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-10).

## Preserve a reproducible decision and recover from conflicts

Record the service/workflow version, flow/stage ID and revision, candidate bindings, inspected evidence
locators, import snapshots, questions/probes, check results, rationale, actor and returned transaction.
Distinguish a draft opinion from a successfully recorded transition. A source-editor Save changes
working source through its own path; it does not revise the frozen review candidate. Return work to
editing to submit revised bytes.

On conflict or an uncertain response, copy your notes, reopen the case and compare its latest history,
candidate and checks. The server may have committed before a response was lost. Reconcile instead of
repeating a stale decision or only replacing its flow revision. Failed/cancelled confirmation retains
the local draft; **Reset** discards selected-stage edits after confirmation and does not roll back
already stored attachments. Proposal stages do not expose normal delete/reopen shortcuts.

### OR-11

**DETAILED WORKFLOW TO BE DECIDED** — Define retention period, enduring reviewer access, restricted-source
handling, archival, withdrawal/correction and migration of old flows. Bound candidate payloads cannot
be deleted and the opted-in lifecycle blocks direct review stage creation/deletion, flow deletion and reopen.
That is audit protection, not a full archive policy. Pinned 1.0 flows may retain inconsistent
editing-comments MIME declarations; no automatic migration or `comments+yaml` support exists. Refer
such cases to the coordinator with version and error details rather than disguising the file type.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-11).

### OR-12

**DETAILED WORKFLOW TO BE DECIDED** — Establish a supported live setup and authenticated HTTP access
test, physical Monaco interaction checks, reliable assignment/notification, persistent draft recovery
and concurrency recovery. Current UI/in-memory tests are not live service proof; effective HTTP
Role.USER enforcement still needs integration verification. Whole-application shutdown draft recovery
and nonblocking transition transport are follow-up work. Keep local notes. Catalog projection is
separate from aggregate persistence and multi-process compare-and-set is not implemented; operational
guarantees must be selected and tested before broad simultaneous use.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-12).

## Reviewer's checklist

- [ ] Correct case, stage, candidate, source bytes and workflow version identified; access/assignment checked.
- [ ] Expertise limits and conflicts declared; appointment/quorum policy not assumed.
- [ ] Sources actually support definitions, scope, ancestry, endpoints and claimed process/event effects.
- [ ] Original questions and precise intended meanings examined before vocabulary coverage claims.
- [ ] Types, predicates, overlap, positive examples and counterexamples reviewed; unknown is not a category.
- [ ] Five-per-kind and fifteen-question targets assessed without padding; shortfalls and fresh probes documented.
- [ ] Automatic checks distinguished from scientific judgment, model availability and runtime execution.
- [ ] Findings identify revision, stable IDs, evidence, requested change and resolution condition.
- [ ] Exact candidate/actions/import binding and rationale verified at confirmation; resulting history checked.
- [ ] Superseding revisions re-reviewed; unresolved OR-01 through OR-12 decisions remain visible.
- [ ] Acceptance, application, Git/PR and publication boundaries explicitly reported.

## Implementation references

Use merged source when prose differs. [PROPOSAL_REVIEW_CONTRACT.md](../PROPOSAL_REVIEW_CONTRACT.md) gives
the typed protocol; [SCHEMA_VALIDATION_INCREMENT.md][increment] updates the older schema limitation.
[WORKFLOWS.md](WORKFLOWS.md) explains generic access and projections; the proposal-specific restrictions
in [WorkflowManager][manager] and [ProposalReviewProtocol][protocol] take precedence for this flow.

- [Workflow definition][workflow] and [review types][types]: exact role matrix, states and candidate/check fields.
- [Controller][controller], [shared binding inspector][binding] and [isolated validator][validator]: authentication surface, saved-byte identity and current check limits.
- Pinned IDE [stage form][ide-stage], [workflow shell][ide-shell], [workspace controls][ide-workspace], [review model][ide-model] and [lexical markers][ide-markers]: labels, decision gate, draft handling and source links.
- [Dossier mapping](../DOSSIER_MAPPING.md), [context pack][context], [proposal schema][schema] and [bootstrap method][method]: interpretation and research boundaries.
- [Backend test evidence][increment], [earlier validation evidence](../VALIDATION_INCREMENT.md) and pinned [IDE verification notes][ide-notes]: reported checks, with live integration limits retained.

[context]: ../llm/DOMAIN_CONTEXT_PACK.md
[schema]: ../klab.services.resources/src/main/resources/schemas/llm/domain-context-proposal.schema.json
[workflow]: ../klab.services.resources/src/main/resources/workflows/ontology-expert-review.yaml
[manager]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowManager.java
[protocol]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/ProposalReviewProtocol.java
[types]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/ProposalReview.java
[binding]: ../klab.core.common/src/main/java/org/integratedmodelling/common/review/ProposalCandidateBinding.java
[controller]: ../klab.services.resources.server/src/main/java/org/integratedmodelling/resources/server/controllers/WorkflowController.java
[validator]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/IsolatedProposalCandidateValidator.java
[increment]: ../SCHEMA_VALIDATION_INCREMENT.md
[ide-shell]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/WorkflowEditor.java
[ide-stage]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/ProposalStageEditor.java
[ide-workspace]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/WorkspaceEditor.java
[ide-model]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/ProposalReviewModel.java
[ide-markers]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/ProposalLexicalMarkers.java
[ide-notes]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/docs/PROPOSAL_REVIEW.md
[sandbox]: https://github.com/integratedmodelling/imod/tree/68a6cbc/experiments/strawman-2026/bootstrap
[method]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/METHOD.md
[hydrology]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/hydrology/BOOTSTRAP.md
