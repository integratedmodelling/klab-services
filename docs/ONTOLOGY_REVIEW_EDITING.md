# Preparing and editing an ontology review

This guide is for editors and domain experts preparing a small, defensible worldview contribution.
Use it with the [reviewer's guide](ONTOLOGY_REVIEW_REVIEWING.md). It describes the merged review
implementation and the decisions still needed before a complete production process can be offered.
An editor develops the meaning and evidence; the Resources service records the review; an authorized
human makes the scientific judgment. These are separate responsibilities.

Shared decision IDs **OR-01 through OR-12** have the same meaning in both guides. Each unresolved
operation is explained under **DETAILED WORKFLOW TO BE DECIDED**, with the decision needed and an
interim way to preserve useful work. Those entries are proposals for agreement, not adopted policy.

## Current state and terms

Verified on **2026-10-04**, after fetching both repositories' remote `develop` branches:

| Repository | Verified `develop` commit | Merged change |
| --- | --- | --- |
| klab-services | [`efba529aa2ae384acd4eff6f9e4f33817a04f487`](https://github.com/integratedmodelling/klab-services/commit/efba529aa2ae384acd4eff6f9e4f33817a04f487) | [PR #83](https://github.com/integratedmodelling/klab-services/pull/83), including final head `45ac91ac51770649e3f6f84f004c5f20d176a700` and schema increment `cf0798712` |
| klab-ide | [`90b3116c2eab1b712fa5a3e0eeadbd3065207eae`](https://github.com/integratedmodelling/klab-ide/commit/90b3116c2eab1b712fa5a3e0eeadbd3065207eae) | [PR #1](https://github.com/integratedmodelling/klab-ide/pull/1), including final head `474a9f9016d7e3ea18572911cd4c78312227cb71` |

The bundled workflow is `ontology-expert-review` **1.1**; the typed review extension is version **1**;
the proposal document uses context pack **1.3**. These version numbers identify different things.
Existing flows remain pinned to their workflow definition. A merge does not establish which software
is running on a service or workstation.

**Production acceptance is still blocked.** Submission, inspection, requests for changes and revised
submission are implemented. The default backend now validates the proposal schema and parses candidate
ontology bytes in isolation. Authoritative import snapshots and isolated Reasoner validation remain
unavailable; imported adaptation is blocked. Applying an ontology, creating a Git commit or PR, and
publishing a release are not performed by this review workflow.

Here, a **proposal** is the structured account of intended changes; a **candidate ontology** is the
exact `.kwv` source offered for review; a **dossier** connects sources, concepts and questions. A
**flow** is one review case; a **stage** is a task within it. A checksum identifies saved bytes, not
scientific truth. A proposal revision identifies an authored version; the separate flow revision
detects concurrent workflow changes.

You can gather sources, draft meanings, prepare files and run the local pilot offline. Starting a
flow, uploading, reading protected attachments or recording transitions requires the loaded,
authenticated Resources service. Native IDE compilation, 20 focused tests, isolated split-view
rendering and an in-memory backend handoff were reported for PR #1. A live authenticated HTTP session
and a physical glyph click through the complete Monaco bridge have not been verified. See
[implementation evidence](#implementation-references) and [OR-12](#or-12).

## Establish responsibility and access

Before authoring, record the proposed domain, target ontology asset, editor, sources of expertise,
and the person coordinating review. The target must exist as an `ONTOLOGY` asset resolvable by the
Resources service. A blank local file alone is not a startable service asset.

Creation needs `EDITOR` or `ADMIN`, plus workflow permission. Non-admin permission normally comes from
group property `workflow.permitted`, using `ontology-expert-review`, a versioned identifier or `*`.
Roles, stage access, owner/assignee restrictions, denied transitions and any group response deadline
all still apply. The flow owner is its original author; stage ownership can differ. An assigned
reviewer can take admitted review transitions without becoming an editor or gaining general upload
rights. A `publicRead` flow is a read-only browser in the IDE.

### OR-01

**DETAILED WORKFLOW TO BE DECIDED** — Editor selection and ownership transfer need an agreed appointing
authority, competence criteria, replacement procedure and treatment of co-authors. The backend has
owner/assignee fields; it does not choose the appropriate editor. Creation of a new empty ontology
asset for bootstrap review also needs an agreed provisioning path. Until arranged, prepare the packet
locally and identify the intended asset and responsible editor. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-01).

### OR-02

**DETAILED WORKFLOW TO BE DECIDED** — Reviewer appointment, qualifications, quorum, conflicts of interest
and recusal are policy decisions. The native form has no reviewer-selection control. An authorized
integration can provide `targetState.owner`/`assignees` on a transition, or use authorized stage
updates; this is backend capability, not a documented menu action. Decide who assigns whom, how they
are notified, and how independent decisions are collected before one transition closes a stage.
The current IDE generally makes the acting user the next stage owner; request-changes deliberately
leaves ownership to the backend, which returns editing to the original author. Arrange assignment
with the coordinator and verify the resulting stage before assuming someone else can act.
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-02).

## Prepare the scope, sources and imported context

1. Describe one bounded observational scope in ordinary language: what is included, excluded, and
   important to the intended community. Begin from a clean minimal worldview. Current domain names
   are provisional addresses, not a requirement to preserve legacy categories. Exclude the obsolete
   `decision` namespace; keep root, data and calendar prerequisites distinct from disciplinary domains.
2. Read sources before selecting vocabulary. Record source ID, title, URL, edition/date, section or
   page, actual supported claim, scope and access status. Mark an unread source, metadata-only discovery,
   introductory account or disputed interpretation honestly. Never manufacture an excerpt.
3. Record the proposed domain tier, imported ontology names, immutable revisions/hashes where available,
   and the parent/ancestry needed for every candidate. Use the [context pack][context] and
   [ontology language guide](ONTOLOGY_LANGUAGE.md). Lower tiers need their authoritative Tier-1 context;
   only root accesses foundational ODO concepts directly. Keep an upstream gap visible instead of
   inventing a local parent to hide it.
4. Separate reuse, specialization (`is`), genuine equivalence (`equals`), composed expressions and
   authority references. Use [authorities](AUTHORITIES.md) for external identifier vocabularies;
   an authority classification is not automatically an equivalent ontology concept.

### OR-03

**DETAILED WORKFLOW TO BE DECIDED** — Agree domain boundaries/names, tier and root ownership, approved
source standards, authority providers and import policy. Choose who supplies authoritative immutable
import snapshots, how their freshness is checked and how changed imports invalidate review. The
current `contextDigest` binds the proposal's declared `existing_ontologies`; it does not prove that
those external ontologies are authoritative, complete or current. Keep missing context and disputed
ancestry in the issues list. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-03).

## Build a small bootstrap dossier

Draft **15 important, concrete, non-specialist questions from sources before drafting vocabulary**.
Preserve their original wording, order and provenance as concepts evolve. For each question record
the intended meaning, candidate concept IDs, full draft observable expression(s), expected result
category, imported derivation/ancestry, positive case, counterexample or invalid probe, and missing
observations, models or context. A question can require several resolutions. If no honest expression
exists, record the gap rather than substituting a paraphrase and calling it executable.

Seek the following coverage, without padding:

| Target per domain | What to record |
| --- | --- |
| 5 subjects | Identity/boundary, bearer-specific qualities, evidence, examples and counterexamples. |
| 5 processes | Participants or host, governing qualities, and separately evidenced `affects`, `creates` and proposed `confers` bindings. |
| 5 relationships | Typed source and target, direction, contextual scope and evidence; adjacency alone does not prove a functional relation. |
| 5 events | Bounded occurrence, participants, start/end interpretation, qualities and evidenced bindings. |

Explain each justified shortfall and weak source area. Five per kind and fifteen questions are
exploration targets, not automatic scientific gates. Map questions back to concepts; identify unused
concepts, missing concepts and overloaded meanings. Preserve asset/concept/evidence/question IDs
across renaming so feedback remains traceable.

For an unmeasured or poorly defined quality, investigate whether an attribute, realm or ordering is
useful. Record the underlying quality, boundary/comparison rule, nominal or ordered values, overlap,
scale, context and source support. Do not invent thresholds or exhaustive partitions. **Unknown,
unmeasured and disputed are evidence states, not automatically domain categories.**

Keep process and runtime claims bounded: change and cessation require an occurrent (a process or
event); `change in X`
resolves separately at time transitions. An unresolved change leaves the twin open-world with
available knowledge, not an inferred zero change. Implication/detection are syntax-only for this
review work. `confers` is proposed meaning/upstream work, not an active executable clause. Recording
bindings does not run a consequence engine. See [occurrences](OCCURRENCE.md) and the
[bootstrap method][method].

The [existing sandbox][sandbox] at `68a6cbc` contains 22 partial domain dossiers, not an approved
ontology. Use [hydrology][hydrology] as a bounded example: a surface catchment footprint and a
groundwater contributing area need distinct meanings; channel identity and water-body identity need
upstream clarification. “Did storage decline through evaporation?” requires separate change and
attribution evidence. A missing sensor reading is not evidence of a dry river. These are review prompts, not
approved declarations or a replacement for hydrologist review.

Ask reviewers to develop fresh probes. Label automated probes written with candidates visible as
such; they are not blind tests or independent domain-expert approval. The sandbox's existing probes
have that limitation. The exact probe and per-expression verification process remains [OR-05](#or-05);
the qualification and independence policy remains [OR-02](#or-02).

## Prepare the proposal and attachments

Use the [context-pack 1.3 document schema][schema] and its [worked structure][context], including
`proposal_schema`, `context_pack_version`, stable proposal ID, fresh `revision_id`, typed assets,
evidence, imported context, open questions and ordered action records. Do not submit research
`dossier.json` as a proposal. The [dossier mapping](../DOSSIER_MAPPING.md) explains which research
fields can populate the typed projection and which need interpretation. Keep titles, detailed source
status, full ancestry arguments and narrative in the proposal/supporting files when no native field
exists.

For workflow **1.1**, choose these logical types in **Attachments**:

| Attachment type | Media type | Editing-stage requirement |
| --- | --- | --- |
| `bootstrap-proposal` | `application/vnd.klab.proposal+yaml` | Required; initial atomic submission needs exactly one unambiguous proposal. |
| `bootstrap-comments` | `application/vnd.klab.comments+json` | Required; explain intended use and the review requested. |
| `candidate-ontology` | `application/vnd.klab.ontology` | Optional for submission, at most one in the stage; exact ontology required for acceptance. |
| `supporting-material` | `*/*` | Optional sources, research dossier, question ledger, local reports and narrative. |

The initial proposal has no predecessor. A revised proposal keeps its proposal ID, uses a new revision
ID and names the reviewed revision in `supersedes_revision`. Candidate binding includes proposal and
ontology attachment IDs/checksums, proposal revision, predecessor, complete ordered action IDs and
import-context digest. Uploading populates these fields through the shared inspector. Do not replace
them with a filename, Git branch name or self-reported validation flag.

The server checks stored bytes independently. Proposal parsing rejects duplicate keys, extra JSON
roots/YAML documents, trailing content and invalid UTF-8. Limits are 4 MiB for the proposal, 2 MiB for
ontology, 16 MiB for another upload, 32 MiB and 256 attachments per stage; HTTP limits may be tighter.
Submitted candidate payloads cannot be deleted. Review stages expose aliases to the submitted proposal,
ontology, comments and supporting material, preserving attachment IDs/checksums. An alias references
the same payload; it is not a newly edited copy.

### OR-04

**DETAILED WORKFLOW TO BE DECIDED** — Standardize package transport and evidence binding: the four-artifact
manifest, comments document format, required evidence roles, revision matching and checks that ontology
bytes implement the exact approved actions against their bases/preconditions. The MIME declaration
alone is not a complete comments schema. Current ID/checksum checks do not prove semantic
candidate/action correspondence. Preserve raw research files as supporting material and record the
mapping explicitly; do not claim an automatic dossier converter or package-import button.
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-04).

An optional local pilot is [tools/review/validate_package.py][pilot]. With existing PyYAML and
`jsonschema` dependencies and a prepared pilot package, run this from the repository root, replacing
the quoted placeholder with the package directory:

```powershell
python tools/review/validate_package.py '<package-directory>'
```

It checks the four roles
PROPOSAL, ONTOLOGY, DOSSIER and SOURCE_QUESTIONS, saved-byte hashes and schema. Read its JSON results:
exit zero can mean structurally sound but **acceptance BLOCKED**. It neither starts a service nor
authorizes acceptance. `PILOT_MANIFEST` and `ROOT_CONTEXT` are local report labels, not service check
enum values. See the [pilot description](../VALIDATION_INCREMENT.md#four-artifact-local-pilot).

## Start and submit using the native IDE

These steps describe implemented controls when the matching IDE and service are loaded; they are
not a claim of a completed live integration test.

1. Open the target ontology in its workspace tree. Use its context menu's **Start workflow** submenu,
   then **k.LAB ontology expert review**, if offered. There is no intervening clickable “Workflows”
   submenu in this source. Missing entries can mean unavailable service/catalog, unsuitable asset or
   permissions; establish these with the coordinator rather than trying an invented path.
2. The initial editing stage is a provisional local flow. In **Attachments**, select the logical type
   and drop the prepared file into the upload area. Supply proposal and comments; add the candidate
   ontology and supporting files as appropriate. Initial uploads show `pending` until the atomic
   initialization succeeds. To replace pending proposal/ontology bytes, copy needed notes, use
   **Cancel workflow**, and restart; the initial form rejects a second ambiguous candidate upload.
3. Inspect **Candidate**. Use **Sources**, **Concepts & bindings**, **Questions & coverage** and
   **Quality analysis** to add/edit structured records during authoring. Row selection exposes detail;
   the record forms specify lists one item per line. **Issues & checks** holds unresolved semantics,
   coverage shortfalls and server results. Empty tables are missing information, not successful checks.
4. In **Decision**, fill **Your rationale** with scope, review request and known blockers. Choose the
   transition whose action text is **Submit the edited asset for peer review.** in
   **-- Choose the next stage --**, then press **Submit**. Labels combine target-stage description
   and action description; transition ID `submit` appears in its tooltip. Submission and resubmission
   use the same transition.
5. Read **Confirm exact proposal review**. Verify proposal/revision, artifact IDs/checksums, ordered
   actions, import digest and rationale. Initial `pending:` IDs are local references; the service
   derives persisted IDs from the exact frozen uploads. Confirm with **OK**, or **Cancel** to retain
   the draft. No later upload can join this already frozen confirmation.
6. On success, inspect the returned `peer-review` stage: its status is `IN_REVIEW`, not accepted. Note
   the flow/stage IDs and revision, resulting attachment bindings, checks and reviewer assignment.
   Use **Open flows** on the same asset to find it again. An unavailable assignment control is
   [OR-02](#or-02), not evidence that reviewers have been notified.

## Understand validation and readiness

Read every check's kind, status, messages and candidate binding in **Issues & checks**. `PASS` applies
only to that check; `FAIL` reports a failed check, `NOT_RUN` means it was not performed, and `BLOCKED`
means it could not establish the required result. Reports on a submitted revision do not validate
unsaved edits.

| Check | Current meaning and limit |
| --- | --- |
| `DOCUMENT_SCHEMA` | Real NetworkNT 2.0.4 Draft 2020-12 validation of the fixed server-selected proposal schema; structure, not scientific validity. |
| `PARSER` | Real Worldview parser on exact ontology bytes; syntax only. Missing ontology gives `NOT_RUN`. |
| `ADAPTATION` | Real conversion in a fresh nonpersistent scope; imports are unavailable there, so imported candidates are `BLOCKED`. |
| `IMPORT_CONTEXT`, `REASONER` | Default production providers remain `BLOCKED`. |
| `SCIENTIFIC_REVIEW` | Separate authenticated human acceptance record; automatic validators cannot manufacture it. |
| `APPLICATION`, `PR_HANDOFF` | `BLOCKED`; no application or PR publication is implemented. |

Schema-invalid but otherwise bindable submissions can be inspected and returned for correction;
malformed identity/binding can prevent submission itself. The server reruns validation at review
transitions, including acceptance. A schema pass is never scientific validity. Older backend/IDE
outcome notes still describe the earlier missing-schema provider; [the schema increment][increment]
and merged implementation supersede that particular blocker.

### OR-05

**DETAILED WORKFLOW TO BE DECIDED** — Provide isolated imported adaptation and Reasoner sessions over
immutable snapshots, per-expression reference/type/ancestry results, fresh probe provenance and a
representative observation/model test procedure. Decide which results block completion and who can
attest to them. Existing Reasoner validation may synchronize authoritative saved knowledge; do not
invoke it casually as a read-only check. Parser success, a local report or a test-only all-PASS
validator cannot substitute for these providers. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-05).

## Respond to feedback and revise

Read the review rationale and referenced issues before changing meanings. Preserve the source-led
question even when its proposed expression proves inadequate. Record each feedback ID, target
asset/field/question, evidence, proposed action, response and unresolved disagreement. Distinguish a
reviewer's suggested revision from a source edit actually applied.

### OR-06

**DETAILED WORKFLOW TO BE DECIDED** — Agree persistent feedback threads, line comments, issue resolution
and conversion of feedback into approved action records. The current **Decision / Your rationale**
is saved with a transition; there is no separate native proposal-note Save action. The review-margin
double-click callback is not connected to comment creation by `ProposalStageEditor`. Reviewers with
only assigned-reviewer authority cannot upload files; an authorized stage editor can add admitted
comments/support. Decide how additional feedback reaches that editor and becomes auditable evidence.
Until then, keep a local issue ledger and include precise issue references in transition rationale.
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-06).

After `request-changes` or `public-input`, the new `editing` stage carries `CHANGES_REQUESTED` and the
previous candidate/rationale. The original author receives ownership by default. Upload the revised
proposal to this new editing stage, with the same proposal ID, new revision and exact predecessor.
Supply current comments again: the return transition carries review data, not copies of every earlier
attachment into the new stage. Earlier evidence remains in history subject to access.

Upload a changed ontology; if it truly has identical bytes, the binding can reference a previously
stored ontology from this flow. Preserve the old ID/checksum exactly, and verify access. Reattach
supporting evidence needed in the next review stage rather than assuming reviewers see hidden history.
Update the dossier, explain resolved and remaining issues in **Your rationale**, and use `submit`
again. This creates a fresh peer-review stage; it does not silently revise the old one. Approval of an
old revision never transfers automatically.

### OR-07

**DETAILED WORKFLOW TO BE DECIDED** — Plan external participation, invitations, moderation, public
visibility and any anonymous route. `open-input` moves an authorized peer review to community review;
it is not an invitation service or a promise of unrestricted access. `PUBLIC` requires the
known-real-person identity conditions described in [workflow access](WORKFLOWS.md), including email
and `workflow.knownRealPerson=true`. `publicRead` permits identified callers to browse, with the IDE
read-only; it does not create anonymous access or commenting rights. Prepare a redacted/reproducible
packet and questions for external humans, but select participants and sharing permissions before
distribution. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-07).

### OR-08

**DETAILED WORKFLOW TO BE DECIDED** — Establish escalation, adjudication of competing meanings, handling
of reviewer disagreement, and who can accept justified coverage shortfalls. Record alternatives,
evidence and the owner of each upstream decision. A count target, majority assumption or schema pass
cannot settle a scientific dispute. Keep unresolved semantics explicit rather than removing them to
enable acceptance. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-08).

## State and action reference

This matrix follows the [bundled YAML][workflow]. Roles listed are necessary transition roles, not
sufficient permission: workflow/stage/group access, current-stage ownership or assignment, deadlines,
exact candidate and flow revision also apply. The assigned-reviewer exception applies only to admitted
non-SUBMIT review transitions. “Editor” here is the `EDITOR` software role, not an appointment policy.

| From | Transition ID | To | Declared roles | Practical result |
| --- | --- | --- | --- | --- |
| `INIT` | `initialize` | `editing` | ADMIN, EDITOR | Create authoring; native first submission initializes atomically. |
| `editing` | `submit` | `peer-review` | ADMIN, EDITOR | Freeze submitted or superseding candidate; `IN_REVIEW`. |
| `peer-review`, `community-review` | `request-changes` | `editing` | ADMIN, EDITOR, REVIEWER | Return exact candidate with rationale; `CHANGES_REQUESTED`. |
| `peer-review` | `open-input` | `community-review` | ADMIN, EDITOR | Advance same candidate; `IN_REVIEW`. |
| `community-review` | `public-input` | `editing` | ADMIN, REVIEWER | Return for another iteration; `CHANGES_REQUESTED`. |
| `peer-review` | `accept-peer-review` | `accepted` | ADMIN, EDITOR | Gated exact-candidate acceptance; blocked by default production validation. |
| `community-review` | `accept-community-review` | `accepted` | ADMIN, EDITOR | Same acceptance gates; blocked by default production validation. |
| `peer-review` | `reject-peer-review` | `rejected` | ADMIN, REVIEWER | Record terminal rejection and rationale. |

There is no direct community-rejection transition. Review-stage candidate uploads, direct review
stage creation/deletion, flow deletion and reopen are blocked by the opted-in audit lifecycle.

### OR-09

**DETAILED WORKFLOW TO BE DECIDED** — Agree the institutional meaning of acceptance/rejection, action-level
deferral or partial approval, and renewed review after rejection or changed dependencies. The current
protocol decides the whole exact candidate/action set. Acceptance requires the ontology and exactly
one `PASS` for each of IMPORT_CONTEXT, DOCUMENT_SCHEMA, PARSER, ADAPTATION and REASONER, no dossier
unresolved semantics, and authorized human rationale. Test acceptance proves those gates, not the
real candidate's validity. Terminal aliases `final-proposal` and `accepted-ontology` preserve verified
bytes in the flow aggregate; optional final comments/support are not all automatically copied there.
Retain access to the review history. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-09).

### OR-10

**DETAILED WORKFLOW TO BE DECIDED** — Design authoritative application, action-precondition execution,
Git/PR ownership and destination, release approval, publication, rollback and revalidation after
application. Agree transaction/retry boundaries before claiming success. `accepted` metadata and a
`publication` provenance label do not apply source changes or publish anything. Prepare the exact
candidate, base/import references, rationale and remaining gates for this future handoff; keep it
separate from review completion. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-10).

## Recover safely and preserve the record

Cancelling **Confirm exact proposal review** or a failed transition retains the local draft. A late
upload rejected while confirmation is open must be added again afterwards. **Reset** discards the
selected stage's unsaved form edits after confirmation; it is not an attachment rollback. Persisted
uploads in an existing flow have already changed the service. **Cancel workflow** is for the
provisional flow, not withdrawal of a submitted case.

On a stale revision, checksum mismatch, unavailable response or changed import: copy local notes;
reopen the flow; inspect its latest candidate, history and checks; reconcile before retrying. A lost
response may follow a successful server write. Do not merely replace `expectedRevision` on an old
decision. If the submission succeeded, continue from the returned/current stage instead of making
a duplicate case. Unknown review-extension versions are inspection-only.

### OR-11

**DETAILED WORKFLOW TO BE DECIDED** — Choose evidence retention, restricted-source handling, archival,
withdrawal/correction of submitted records, and migration of pinned workflow 1.0 cases. Version 1.0
may retain the historical editing-comments MIME mismatch; version 1.1 consistently uses
`application/vnd.klab.comments+json`. Do not relabel JSON comments as proposal YAML or assume migration
occurred. Bound payload immutability is implemented; retention duration, archival and lawful sharing
procedures are not established here. [Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-11).

### OR-12

**DETAILED WORKFLOW TO BE DECIDED** — Establish a supported live setup and verification procedure for
service discovery, authenticated HTTP roles, assignment, uploads and physical Monaco interaction.
Define persistent draft recovery, nonblocking transition transport and operational concurrency
recovery. The present draft survives covered cancellation/failure/tab navigation; whole-application
shutdown recovery is not implemented. Keep working files/notes locally. Aggregate persistence is
protected within the manager, but catalog synchronization is separate and multi-process compare-and-set
is absent. Choose and test those operational guarantees before relying on concurrent production use.
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-12).

## Editor's readiness checklist

- [ ] Named responsible editor and intended reviewers; actual role/assignment access checked.
- [ ] Bounded scope, provisional names, sources/status and imported ancestry documented; no invented roots.
- [ ] Fifteen source-led questions with precise intent, expressions or explicit gaps, and counterexamples.
- [ ] Five-per-kind coverage sought; honest shortfalls, quality/predicate boundaries and runtime limits recorded.
- [ ] Proposal conforms to context pack 1.3; research dossier and source questions remain distinguishable.
- [ ] Correct attachment types, immutable IDs/checksums, action set, revision lineage and import digest checked.
- [ ] Validation states reported separately; no schema/parser result presented as scientific approval.
- [ ] Feedback answered by stable ID; unresolved decisions OR-01 through OR-12 visible to reviewers.
- [ ] Exact confirmation reviewed; returned stage/history inspected; local recovery copies retained.
- [ ] Readiness means ready for review, with acceptance/application/publication blockers still explicit.

## Implementation references

The merged source governs behavior when older narrative notes disagree. The concise technical starting
point is the [proposal-review contract](../PROPOSAL_REVIEW_CONTRACT.md), read with the
[schema increment][increment], historical [backend result](../BACKEND_RESULT.md) and
[validation increment](../VALIDATION_INCREMENT.md).

- [Workflow definition][workflow], [WorkflowManager][manager] and [ProposalReviewProtocol][protocol]: roles, transitions, aliases, ownership and gates.
- [Flow types][flow], [ProposalReview types][types], [shared candidate inspector][binding] and [controller][controller]: transport and immutable bindings.
- [Isolated validator][validator] and [schema provider][schema-provider]: what production checks actually establish.
- Pinned IDE [WorkflowEditor][ide-shell], [ProposalStageEditor][ide-stage], [WorkspaceEditor][ide-workspace] and [review model][ide-model]: controls and local draft behavior.
- Pinned IDE [review notes][ide-notes]: reported tests and unverified live boundaries; its older schema-blocker statement is superseded by the backend schema increment.

[context]: ../llm/DOMAIN_CONTEXT_PACK.md
[schema]: ../klab.services.resources/src/main/resources/schemas/llm/domain-context-proposal.schema.json
[workflow]: ../klab.services.resources/src/main/resources/workflows/ontology-expert-review.yaml
[manager]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowManager.java
[protocol]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/ProposalReviewProtocol.java
[flow]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/Flow.java
[types]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/ProposalReview.java
[binding]: ../klab.core.common/src/main/java/org/integratedmodelling/common/review/ProposalCandidateBinding.java
[controller]: ../klab.services.resources.server/src/main/java/org/integratedmodelling/resources/server/controllers/WorkflowController.java
[validator]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/IsolatedProposalCandidateValidator.java
[schema-provider]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/ProposalSchemaValidator.java
[increment]: ../SCHEMA_VALIDATION_INCREMENT.md
[pilot]: ../tools/review/validate_package.py
[ide-shell]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/WorkflowEditor.java
[ide-stage]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/ProposalStageEditor.java
[ide-workspace]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/WorkspaceEditor.java
[ide-model]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/src/main/java/org/integratedmodelling/klab/ide/components/ProposalReviewModel.java
[ide-notes]: https://github.com/integratedmodelling/klab-ide/blob/90b3116c2eab1b712fa5a3e0eeadbd3065207eae/docs/PROPOSAL_REVIEW.md
[sandbox]: https://github.com/integratedmodelling/imod/tree/68a6cbc/experiments/strawman-2026/bootstrap
[method]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/METHOD.md
[hydrology]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/hydrology/BOOTSTRAP.md
