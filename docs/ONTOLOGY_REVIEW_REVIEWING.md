# Reviewing an ontology proposal

This guide is for domain experts and ontology reviewers evaluating whether proposed meanings are
clear, useful, source-supported and compatible with their imported context. You do not need to be a
backend developer. Your task is to examine the exact candidate, expose gaps and record an accountable
judgment. Use the [editor's guide](ONTOLOGY_REVIEW_EDITING.md) for packet preparation and revision.

Shared decision IDs **OR-01 through OR-12** have the same meaning in both guides. Each unresolved
operation is explained under **DETAILED WORKFLOW TO BE DECIDED**, with the decision needed and an
interim way to preserve useful work. Those entries are proposals for agreement, not adopted policy.

This revision distinguishes current controls from a proposed repeatable public-review/integration
cycle. Technical contracts are in the [behavior design](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md); the
[land/agriculture note](ONTOLOGY_REVIEW_LAND_AGRICULTURE.md) is a worked boundary proposal for discussion.

## Find a task

- [Obtain the case and access](#obtain-the-case-and-the-right-access).
- [Find the proposal and source view](#find-the-proposal-and-inspect-source-beside-it), then [verify the candidate](#verify-what-you-are-being-asked-to-judge).
- [Examine meaning and coverage](#examine-meaning-evidence-and-coverage), then [interpret automatic checks](#separate-automatic-checks-from-judgment).
- [Use configured actions](#use-configured-stage-actions-when-available) and [discuss repeated contributions](#proposed-repeated-contributions-and-integration).
- [Give feedback, return work and re-review](#give-actionable-feedback-and-return-work).
- [Address public input and disagreements](#public-input-and-disagreements); consult the [state/action matrix](#state-and-action-reference) and [decision boundary](#or-09).
- [Preserve decisions and recover](#preserve-a-reproducible-decision-and-recover-from-conflicts), then use the [reviewer's checklist](#reviewers-checklist).

## Open decision index

| Decision | Topic | Decision | Topic |
| --- | --- | --- | --- |
| [OR-01](#or-01) | Author/integrator ownership | [OR-07](#or-07) | Public and external participation |
| [OR-02](#or-02) | Reviewers, quorum and conflicts of interest | [OR-08](#or-08) | Disagreement and escalation |
| [OR-03](#or-03) | Scope, imports and authority policy | [OR-09](#or-09) | Acceptance and rejection policy |
| [OR-04](#or-04) | Package and evidence binding | [OR-10](#or-10) | Application, Git and publication |
| [OR-05](#or-05) | Semantic validation and probes | [OR-11](#or-11) | Retention, archival and migration |
| [OR-06](#or-06) | Contributions and integration | [OR-12](#or-12) | Live operation and recovery |

## Current state and review boundary

Verified on **2026-10-07**, after fetching both repositories' remote `develop` branches:

| Repository | Verified source baseline | Relevant capability |
| --- | --- | --- |
| klab-services | [`5bae64208a7098c6ee1e81813bd494f3417ce3be`](https://github.com/integratedmodelling/klab-services/commit/5bae64208a7098c6ee1e81813bd494f3417ce3be) | Review protocol plus workflow behavior hooks, content attachments and document/project actors. |
| klab-ide | [`70c4bd9ac0b42004511b8fd855483b31fb720467`](https://github.com/integratedmodelling/klab-ide/commit/70c4bd9ac0b42004511b8fd855483b31fb720467) | Native proposal forms plus configured stage buttons and parameter dialogs. |

The bundled `ontology-expert-review` remains **1.1**, typed review extension **1**, and context-pack
proposal format **1.3**. It has **no configured behavior, lifecycle actions or stage buttons**.
The generic mechanisms are shipped; the review automation and iterative contribution cycle proposed
here are not. Existing flow versions and actual deployed service/IDE versions must be checked.

**Production acceptance remains blocked.** The server validates the document schema and parses
candidate ontology bytes in isolation. Authoritative import snapshots and isolated Reasoner
validation remain unavailable; imported adaptation is blocked. Schema/parser success does not
establish scientific validity. This review protocol does not apply ontology changes, create a Git
commit/PR or publish a release. Document/project actors now provide mutation primitives, but safe
review application and release orchestration are still missing.

Use these labels throughout: **shipped mechanism** means present in source; **proposed wiring**
means an action can be connected but is not configured; **missing primitive/API** needs implementation;
**policy** needs human agreement. The [behavior design](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md) maps
every operation to hooks, actors, contracts and decisions. Its examples are design pseudocode.

The earlier native review tests and new bridge/input tests provide implementation evidence, not a
live demonstration of the proposed workflow. This revision inspected source; it did not rerun
runtime tests or verify a deployed HTTP/Monaco session. See [OR-12](#or-12).

A **proposal** explains intended changes; its **candidate ontology** is the exact source offered
for review. A **dossier** connects sources, concepts and questions. A **round** in the proposed design
collects independently owned proposals against one immutable packet. A checksum identifies bytes,
not scientific truth. Proposal revision, flow revision and document storage/base revision identify
different things. You can gather sources and prepare packets offline; protected reads, uploads and
recorded workflow actions require the authenticated Resources service.

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

**DETAILED WORKFLOW TO BE DECIDED** — Appoint the author/editor, contribution owners and an
explicit integrator; decide competence, substitution, co-authorship and conflicts when one person
holds several responsibilities. Recommend keeping each submitted proposal's actual author and
letting the integrator own a separate integrated proposal. The flow owner and current stage owner
are not interchangeable. The current return transition goes to the original author; the proposed
review/integration loop need not detour through author editing every time. New bootstrap asset
provisioning remains an operational decision. See [responsibility contracts](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#roles-access-and-public-participation).
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-01).

### OR-02

**DETAILED WORKFLOW TO BE DECIDED** — Agree assigned-review qualifications, quorum, recusal,
conflicts and notification. Assigned expertise complements PUBLIC participation; public contributors
must not need invitations or special reviewer grants. Recommend a user-accessible, case-scoped Hub
reviewer query in a separate controller, with profile consent and workflow visibility checks.
`klab.hub` is not installed here: [the query contract](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c06--user-accessible-hub-reviewer-query)
is proposed, not an existing endpoint or IDE picker. Existing owner/assignee fields can support
authorized assignment, but they do not implement discovery, assignment acceptance or consensus.
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

## Use configured stage actions when available

The new generic mechanism renders buttons configured in a stage's `actions` list, separately from
the transition selector. **The bundled ontology workflow configures none.** Names such as
**Extract proposal**, **Attach current document snapshot** and **One more contribution** in this
design are proposed operations, not current menu instructions.

On an instrumented workflow supplied by an administrator, a persisted current stage must be
editable by you. Provisional initial stages and read-only stages do not offer these buttons.

1. Preserve unsaved notes and proposal form edits. A behavior uses saved server state. The native
   proposal form has no separate Save-note operation; pressing a behavior button is not a save.
   The existing unsaved-draft confirmation can ask to discard edits. Choose **Cancel** if those
   edits have not been safely preserved.
2. Select an available configured button. Its tooltip says it runs the named action using the
   saved stage. Discovery rechecks availability and returns the authoritative revision.
3. Read the dialog's saved-stage warning. Enter only the requested text, boolean or numeric
   parameters. Agent/object selection, reviewer pickers and buffer capture need new UI contracts.
   **Cancel** invokes no action; **Run** sends that configured action and discovered revision.
4. Wait for the background request. On success the editor refreshes and clears proposal drafts;
   inspect the saved result before taking another action. A button does not itself advance the
   stage unless a future typed operation expressly provides that behavior.
5. On a stale revision, failure or missing response, keep the draft and reconcile with a fresh case
   before retrying. Current buttons have no durable execution receipt. An external effect or flow
   save may already have happened; do not infer rollback from the error dialog.

The `editor` injected into a behavior is a read-only saved-stage description, not the source-editor
widget. `document` reads current server source. Capturing the unsaved document currently on screen
needs an explicit freeze/upload step under [OR-04](#or-04). Existing content actors can attach text,
bytes, an allowed server file or an HTTP(S) URL; a server-file path is not an IDE-local file path.
Attachment rules and ownership still apply. Document/project changes use the acting participant's
permissions and do not become safe merely because a workflow calls them.

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
supporting evidence, not automatically a valid context-pack proposal. These instructions target 1.1;
record older flow versions and resolve archival/migration under [OR-11](#or-11), rather than
disguising an attachment's media type.

### OR-03

**DETAILED WORKFLOW TO BE DECIDED** — Agree domain boundaries/names, upper-ontology parentage,
source standards, authority providers and immutable import policy. Recommend adding **agriculture**
as a candidate domain while retaining **land** for cross-sector land use, management, relationships,
conflicts and degradation. The [source-grounded boundary note](ONTOLOGY_REVIEW_LAND_AGRICULTURE.md)
preserves all 27 candidate dispositions and blockers; it authorizes no ontology changes. The current
`contextDigest` binds declared `existing_ontologies`, not their authority, completeness or freshness.
Keep missing imports and disputed meanings open until their owners resolve them.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-03).

### OR-04

**DETAILED WORKFLOW TO BE DECIDED** — Standardize immutable snapshots, base revisions, package
manifest, evidence access/provenance and cross-proposal lineage. Current proposal bytes and candidate
bindings are protected, but live document handles are not snapshots. A saved-source attachment can
be prototyped with existing actors; atomic source/base capture and unsaved IDE-buffer capture need
new contracts. Proposal extraction is a missing actor, not a current button. Never replace bytes
after exact confirmation. See [snapshot](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c03--saved-source-and-ide-buffer-snapshots)
and [extraction contracts](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c04--proposal-extraction-questions-and-predicates).
The comments MIME alone does not define a complete comments schema or prove action/source correspondence.
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
| **Questions & coverage** | Inspect native question text, intent, evidence/concept references, expressions, invalid probes and gaps. Follow the linked proposal/supporting evidence for positive cases, result-category audits and detailed imported derivations; these have no dedicated native question fields. Ask whether the expression answers the actual question. |
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

**DETAILED WORKFLOW TO BE DECIDED** — Provide a typed action facade for isolated checks, immutable
authoritative imports, isolated Reasoner validation, action/source correspondence and reproducible
question probes. Existing document `notifications()` is not a candidate validation receipt.
Do not invoke authoritative Reasoner operations as casual read-only checks: they may mutate knowledge.
Record validator/version, exact inputs and PASS/FAIL/BLOCKED/NOT_RUN separately from human judgment.
Candidate-visible automated probes are not blind or domain-expert review. See the
[validation contract](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c05--validation-and-readiness).
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-05).

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

## Proposed repeated contributions and integration

**DETAILED WORKFLOW TO BE DECIDED — OR-04, OR-06, OR-07, OR-08.** The intended PUBLIC path admits
Hub-authorized people without invitations or special reviewer grants. Assigned review remains useful
for targeted expertise. Both paths would use the following sequence; it is not a current UI flow.

1. Open an accessible review round and its immutable published packet. Confirm round ID, candidate
   and base revisions, evidence access, deadline and your allowed contribution operations. Public
   read visibility alone is not permission to contribute; eligibility is checked through the Hub.
2. Start your own proposal. Record evidence, questions, counterexamples, requested actions and any
   alternative candidate snapshot. An endorsement or objection is still an attributed proposal.
   Your proposal does not modify another reviewer's work or the author's frozen candidate.
3. Submit against that exact round/base and retain the receipt. A proposed **One more contribution**
   control prepares another owned draft without changing the shared review stage. For a new claim,
   use a new proposal ID; to correct your earlier submission, explicitly supersede your own revision.
   Ownership and immutable lineage apply to PUBLIC and assigned reviewers alike.
4. If the round closes or its current material changes while you draft, keep your draft. Expect an
   explicit late/stale response. Do not relabel an old review as if you inspected a new candidate;
   compare/rebase deliberately before submitting to another round.
5. Read the integrator's disposition of every submitted action and the resulting integrated
   proposal. Raise an attributed correction or appeal if your meaning was lost. Dissent, deferred
   work and unresolved semantics must remain visible to authorized readers.
6. The editor may send revised material into another public round or judge it sufficient to move
   to the next permitted stage. Re-review the new exact revision when a round repeats. Onward
   progression is not final scientific acceptance, project application or publication.

An editor may integrate provisionally while contributions continue, but final integration uses a
frozen cutoff manifest, not the mutable current status. Contributions accepted after a provisional
batch need explicit reconciliation; later-than-cutoff work needs a receipt and next-round/decline
decision. Whether provisional integration and other reviewers' proposals are visible during review
is a policy choice, especially for blind review.

The narrow append operation is the recommended minimal addition. A contributor-local flow with a
same-schema **contribute-again** transition is an alternative. A self-loop on the shared review stage
would close it and disrupt other participants. Neither approach grants PUBLIC permission to integrate,
advance the main review, accept or apply. See the [design and alternatives](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#proposed-round-and-proposal-model).

## Give actionable feedback and return work

The steps in this section describe today's single-candidate return path. The proposed repeated
contribution cycle above keeps individual feedback from closing the shared review stage.

Write each finding so the editor can respond without guessing: identify the candidate revision,
concept/question/action ID, field or expression, observed problem, source/counterexample, requested
change and condition for considering it resolved. Distinguish a correction needed for meaning from
a preference about wording. State when evidence is insufficient and what would resolve it.

For example: “For question hydrology-Q12, the proposed concentration expression does not establish
transported load. Retain the question; identify discharge and integration dependencies, or record
the missing full expression. Re-review when those mappings and their evidence are supplied.” This
is a proposed change, not a claim that the source has already been edited or that the issue is closed.

### OR-06

**DETAILED WORKFLOW TO BE DECIDED** — Recommend independent reviewer proposals and an explicit
editor-owned integration stage. A proposed **One more contribution** action appends only that
person's submission to an exact open round; it does not transition the shared review stage. Each
proposal has its own identity, revision, evidence, base and attribution. The integrator records
dispositions and produces an integrated proposal, then chooses a new public round with revised
material or progression to the next permitted stage. Author editing can be requested when needed.
Current rationale/return controls do not implement this cycle. Decide new-versus-superseding
contributions, draft storage and whether integration drafts are visible while collection is open.
See [the proposed round model](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#proposed-round-and-proposal-model).
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

**DETAILED WORKFLOW TO BE DECIDED** — Implement Hub-authorized PUBLIC participation without
special reviewer grants or invitations, with an accessible published packet and a narrow operation
for each person's own proposals. This is identified participation, not anonymous access. Agree
moderation, quotas, evidence disclosure and the Hub assertion/renewal contract. In current source,
`PUBLIC` is an admitted-group sentinel, not a `WorkflowRole` enum value. Known-person eligibility
requires authentication, non-anonymity, email and `workflow.knownRealPerson=true`; default REVIEWER
is added only if no workflow roles exist. PUBLIC access does not bypass stage ownership or make
generic uploads/buttons public. `publicRead` separately controls visibility and is read-only in the
IDE. See [the exact current and proposed distinction](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#roles-access-and-public-participation).
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-07).

### OR-08

**DETAILED WORKFLOW TO BE DECIDED** — Choose adjudicator, escalation and appeal policy. Recommend
an explicit disposition for every contribution action: adopt, modify, defer, reject or unresolved,
with reasons and attribution. Preserve dissent and incompatible alternatives; no arrival order,
vote count or generated summary establishes scientific truth. The editor may integrate provisionally
while collection is open, but final integration must use an immutable cutoff manifest and reconcile
all later admitted submissions. Decide whether reviewer acknowledgment or a pending appeal blocks
progression. See [integration](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c08--integration-and-the-next-step).
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-08).

## State and action reference

This matrix describes **current bundled workflow 1.1 only**. It is not the proposed public
contribution/integration topology, which needs new typed operations and wiring.

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

**DETAILED WORKFLOW TO BE DECIDED** — Define the editor's sufficiency criteria for advancing
integrated work, separately from final acceptance/rejection authority. Moving to the next stage is
not acceptance or publication. Recommend whole-candidate human acceptance initially; partial approval
requires a rebuilt exact candidate and revalidation. Current acceptance requires an ontology, exactly
one PASS each for IMPORT_CONTEXT, DOCUMENT_SCHEMA, PARSER, ADAPTATION and REASONER, no unresolved
semantics and authenticated human rationale. The server reruns checks; production defaults still
block acceptance. Neither a behavior action nor an integrator's judgment may forge those gates.
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-09).

The protocol's terminal `accepted` state would alias the verified proposal as `final-proposal` and
ontology as `accepted-ontology`, committing them with review data, source closure and history in the
flow aggregate. Optional comments/support are not all copied automatically to the terminal stage;
preserve their review-stage references. The rejected state's declared visibility differs from peer
review: do not assume all former assignees can inspect the complete terminal record. Evidence-access
and retention arrangements remain [OR-11](#or-11).

### OR-10

**DETAILED WORKFLOW TO BE DECIDED** — Define authorized application, Git/PR handoff, publication
and rollback. `document.update` and project CRUD now provide real source mutation primitives;
`project.write_text` can store additional material and stage its path in Git. They do not supply
base-revision compare-and-swap, review application, an isolated commit/PR or release actor.
Project writes survive a later workflow failure. Recommend a dry-run plan, exact base check,
separate authorization and durable effect receipt before application. `APPLICATION` and `PR_HANDOFF`
remain BLOCKED in the review protocol. See [application boundaries](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c11--application-git-and-release-handoff).
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-10).

## Preserve a reproducible decision and recover from conflicts

Record the service/workflow version, flow/stage ID and revision, candidate bindings, inspected evidence
locators, import snapshots, questions/probes, check results, rationale, actor and returned transaction.
Distinguish a draft opinion from a successfully recorded transition. A source-editor Save changes
working source through its own path; it does not revise the frozen review candidate. Return work to
editing to submit revised bytes.

On conflict or an uncertain response, load a fresh case before retrying. Selecting **Open flows**
while its workflow tab remains open only focuses the cached editor; it does not refresh that editor's
candidate or history.

1. Copy all unsaved notes and form edits you need to preserve to a local file.
2. If the workflow is paired with source, toggle **Side-to-side workflow review** off. Close the
   individual workflow tab. When unsaved proposal edits exist, the confirmation asks **Discard
   unsaved proposal review edits and notes in this workflow?** Choose **OK** only after preserving
   them; **Cancel** keeps the tab and draft. Closing is refused while a submission is in progress.
3. Open the ontology asset's context menu again. Choose **Open flows**, or **Closed flows** if the
   case has completed, and select the same flow. With the old tab closed, this creates an editor from
   the freshly retrieved case.
4. Compare its candidate, visible history and checks with your saved notes. The server may have
   committed before a response was lost. Reconcile before retrying instead of repeating a stale
   decision or only replacing its flow revision. Ask the author/coordinator about records hidden
   by your access projection.

This closes and opens a local UI tab; it does not invoke the **Reopen flow** lifecycle action, which
is blocked for proposal-review flows. Closing does not undo persisted transitions or attachments.
Failed/cancelled confirmation retains the local draft; **Reset** discards selected-stage edits after
confirmation and does not roll back stored attachments. Proposal stages do not expose normal
delete/reopen shortcuts.

### OR-11

**DETAILED WORKFLOW TO BE DECIDED** — Agree cutoff, late contributions, withdrawal, retained
access, restricted-source handling and archival. Recommend sealed round/submission manifests;
late input receives a receipt and an explicit next-round or rejection disposition. Reopening creates
a linked new round rather than changing the old base. Current proposal flows block direct stage
creation/deletion, flow deletion and generic reopen. Generic behavior-only flows have weaker closure
protection and need typed guards before reuse. No backward-compatibility requirement dictates the
new protocol; decide archival treatment of existing flows explicitly. See
[closure policy](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c09--decisions-closure-reopening-and-late-contributions).
[Editor counterpart](ONTOLOGY_REVIEW_EDITING.md#or-11).

### OR-12

**DETAILED WORKFLOW TO BE DECIDED** — Establish live authenticated HTTP/Monaco verification,
durable operation receipts, idempotency/outbox, owner identity after restart and concurrency recovery.
A catalog error can occur after the flow revision committed; project writes/email can also survive
a failed action. Reconcile saved state and effects before retrying. New actions require exact flow,
contributor and document/base checks as applicable; a negative revision must not bypass them.
The manager currently serializes operations in one process; scalable public collection needs scoped
coordination and storage-level atomic admission/closure. Pin behavior source and test upgrades,
timeouts and revoked permissions. See [recovery contracts](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c12--recovery-durability-and-behavior-updates).
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
- [ ] Current controls distinguished from proposed actions; no configured ontology behavior assumed.
- [ ] PUBLIC eligibility separated from public visibility, assignment and integration/acceptance authority.
- [ ] Each contribution and integration proposal preserves owner, immutable base and supersession lineage.
- [ ] Round cutoff, late submissions, dissent and both integration exits accounted for.
- [ ] Action error reconciled against persisted flow and external effects before retrying.

## Implementation references

The [behavior design](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#implementation-references) links the exact
bridge, content/target actor, authorization and IDE action sources inspected for this revision.
[WORKFLOWS.md](WORKFLOWS.md#kactors-instrumentation) and
[AGENTS_REFERENCE.md](AGENTS_REFERENCE.md#workflow-target-binding-and-restoration) describe shipped
mechanisms; [WORKFLOW_EXTENSION.md](WORKFLOW_EXTENSION.md) records remaining generic contracts.

Use merged source when prose differs. [PROPOSAL_REVIEW_CONTRACT.md](../PROPOSAL_REVIEW_CONTRACT.md) gives
the typed protocol; [SCHEMA_VALIDATION_INCREMENT.md][increment] updates the older schema limitation.
[WORKFLOWS.md](WORKFLOWS.md) explains generic access and projections; the proposal-specific restrictions
in [WorkflowManager][manager] and [ProposalReviewProtocol][protocol] take precedence for this flow.

- [Workflow definition][workflow] and [review types][types]: exact role matrix, states and candidate/check fields.
- [Controller][controller], [shared binding inspector][binding] and [isolated validator][validator]: authentication surface, saved-byte identity and current check limits.
- Pinned IDE [stage form][ide-stage], [workflow shell][ide-shell], [workspace controls][ide-workspace], [review model][ide-model] and [lexical markers][ide-markers]: labels, decision gate, draft handling and source links.
- Pinned IDE [tab host][ide-tab-host]: individual workflow-tab close confirmation and disposal.
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
[ide-shell]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/WorkflowEditor.java
[ide-stage]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/ProposalStageEditor.java
[ide-workspace]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/WorkspaceEditor.java
[ide-model]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/ProposalReviewModel.java
[ide-tab-host]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/pages/EditorPage.java
[ide-markers]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/ProposalLexicalMarkers.java
[ide-notes]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/docs/PROPOSAL_REVIEW.md
[sandbox]: https://github.com/integratedmodelling/imod/tree/68a6cbc/experiments/strawman-2026/bootstrap
[method]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/METHOD.md
[hydrology]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/hydrology/BOOTSTRAP.md
