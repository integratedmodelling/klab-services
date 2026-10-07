# Preparing and editing an ontology review

This guide is for editors and domain experts preparing a small, defensible worldview contribution.
Use it with the [reviewer's guide](ONTOLOGY_REVIEW_REVIEWING.md). It describes the merged review
implementation and the decisions still needed before a complete production process can be offered.
An editor develops the meaning and evidence; the Resources service records the review; an authorized
human makes the scientific judgment. These are separate responsibilities.

Shared decision IDs **OR-01 through OR-12** have the same meaning in both guides. Each unresolved
operation is explained under **DETAILED WORKFLOW TO BE DECIDED**, with the decision needed and an
interim way to preserve useful work. Those entries are proposals for agreement, not adopted policy.

This revision distinguishes current controls from a proposed repeatable public-review/integration
cycle. Technical contracts are in the [behavior design](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md); the
[land/agriculture note](ONTOLOGY_REVIEW_LAND_AGRICULTURE.md) is a worked boundary proposal for discussion.

## Find a task

- [Establish responsibility and access](#establish-responsibility-and-access).
- [Prepare scope and sources](#prepare-the-scope-sources-and-imported-context), then [build the bootstrap dossier](#build-a-small-bootstrap-dossier).
- [Prepare proposal and attachments](#prepare-the-proposal-and-attachments), then [start and submit in the IDE](#start-and-submit-using-the-native-ide).
- [Use configured actions](#use-configured-stage-actions-when-available) and [discuss the integration cycle](#proposed-editor-integration-cycle).
- [Interpret checks and readiness](#understand-validation-and-readiness).
- [Respond and revise](#respond-to-feedback-and-revise); consult the [state/action matrix](#state-and-action-reference) and [acceptance boundary](#or-09).
- [Recover from conflicts](#recover-safely-and-preserve-the-record) and use the [editor's checklist](#editors-readiness-checklist).

## Open decision index

| Decision | Topic | Decision | Topic |
| --- | --- | --- | --- |
| [OR-01](#or-01) | Author/integrator ownership | [OR-07](#or-07) | Public and external participation |
| [OR-02](#or-02) | Reviewers, quorum and conflicts of interest | [OR-08](#or-08) | Disagreement and escalation |
| [OR-03](#or-03) | Scope, imports and authority policy | [OR-09](#or-09) | Acceptance and rejection policy |
| [OR-04](#or-04) | Package and evidence binding | [OR-10](#or-10) | Application, Git and publication |
| [OR-05](#or-05) | Semantic validation and probes | [OR-11](#or-11) | Retention, archival and migration |
| [OR-06](#or-06) | Contributions and integration | [OR-12](#or-12) | Live operation and recovery |

## Current state and terms

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

**DETAILED WORKFLOW TO BE DECIDED** — Appoint the author/editor, contribution owners and an
explicit integrator; decide competence, substitution, co-authorship and conflicts when one person
holds several responsibilities. Recommend keeping each submitted proposal's actual author and
letting the integrator own a separate integrated proposal. The flow owner and current stage owner
are not interchangeable. The current return transition goes to the original author; the proposed
review/integration loop need not detour through author editing every time. New bootstrap asset
provisioning remains an operational decision. See [responsibility contracts](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#roles-access-and-public-participation).
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-01).

### OR-02

**DETAILED WORKFLOW TO BE DECIDED** — Agree assigned-review qualifications, quorum, recusal,
conflicts and notification. Assigned expertise complements PUBLIC participation; public contributors
must not need invitations or special reviewer grants. Recommend a user-accessible, case-scoped Hub
reviewer query in a separate controller, with profile consent and workflow visibility checks.
`klab.hub` is not installed here: [the query contract](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c06--user-accessible-hub-reviewer-query)
is proposed, not an existing endpoint or IDE picker. Existing owner/assignee fields can support
authorized assignment, but they do not implement discovery, assignment acceptance or consensus.
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

**DETAILED WORKFLOW TO BE DECIDED** — Agree domain boundaries/names, upper-ontology parentage,
source standards, authority providers and immutable import policy. Recommend adding **agriculture**
as a candidate domain while retaining **land** for cross-sector land use, management, relationships,
conflicts and degradation. The [source-grounded boundary note](ONTOLOGY_REVIEW_LAND_AGRICULTURE.md)
preserves all 27 candidate dispositions and blockers; it authorizes no ontology changes. The current
`contextDigest` binds declared `existing_ontologies`, not their authority, completeness or freshness.
Keep missing imports and disputed meanings open until their owners resolve them.
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-03).

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

**DETAILED WORKFLOW TO BE DECIDED** — Standardize immutable snapshots, base revisions, package
manifest, evidence access/provenance and cross-proposal lineage. Current proposal bytes and candidate
bindings are protected, but live document handles are not snapshots. A saved-source attachment can
be prototyped with existing actors; atomic source/base capture and unsaved IDE-buffer capture need
new contracts. Proposal extraction is a missing actor, not a current button. Never replace bytes
after exact confirmation. See [snapshot](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c03--saved-source-and-ide-buffer-snapshots)
and [extraction contracts](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c04--proposal-extraction-questions-and-predicates).
The comments MIME alone does not define a complete comments schema or prove action/source correspondence.
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

**DETAILED WORKFLOW TO BE DECIDED** — Provide a typed action facade for isolated checks, immutable
authoritative imports, isolated Reasoner validation, action/source correspondence and reproducible
question probes. Existing document `notifications()` is not a candidate validation receipt.
Do not invoke authoritative Reasoner operations as casual read-only checks: they may mutate knowledge.
Record validator/version, exact inputs and PASS/FAIL/BLOCKED/NOT_RUN separately from human judgment.
Candidate-visible automated probes are not blind or domain-expert review. See the
[validation contract](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c05--validation-and-readiness).
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-05).

## Proposed editor integration cycle

**DETAILED WORKFLOW TO BE DECIDED — OR-01, OR-04, OR-06, OR-08, OR-09.** This is the recommended
operating sequence for discussion. It needs the [missing contracts](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#contracts-for-behavior-wiring-and-missing-actors);
the current eight-transition workflow below does not implement it.

1. Prepare and inspect an immutable review packet. Record its source/base revision and import
   snapshot. Author, reviewer and integrator proposal lines retain separate authorship.
2. Open a review round against those exact bytes. Assigned reviewers supply requested expertise;
   Hub-authorized PUBLIC participants contribute without invitations or special grants. Each person
   owns their draft and immutable submitted proposals. One person's submission leaves the shared
   review stage current for everyone else. Choose the peer-contribution visibility policy separately
   and communicate it before review; see [OR-07](#or-07).
3. Collect proposals and record which earlier revision each supersedes, if any. A new contribution
   is distinct from a revision of an existing one. Do not update the round base while people review it.
4. As integrator, compare contributions against that base and prepare an integrated proposal.
   You may work provisionally while collection is open, recording a submission watermark. At cutoff,
   freeze the input manifest and account for all submissions admitted since that watermark. Record
   adopt/modify/defer/reject/unresolved for every action, with evidence, attribution and dissent.
5. In the explicit editor-owned integration stage, choose between **revised material to another
   public review round** and **sufficient material to the next permitted stage**. The first choice
   freezes a new revision/base and retains old rounds. The second records a human sufficiency
   rationale and checks the destination's gates. It does not itself accept or publish an ontology.
6. Request author edits when needed, passing the integrated proposal and response ledger back to
   the original author. This is an optional handoff, not a required detour on every integration loop.
   Applying integrated material to shared project source remains separately authorized work.

For example, an integration of the [land/agriculture proposals](ONTOLOGY_REVIEW_LAND_AGRICULTURE.md)
could retain land's conversion/management meanings, move cultivation candidates into a proposed
agriculture dossier, and leave disputed upper parents open. That produces revised review material;
it does not approve declarations, erase a reviewer's objection or edit `imod`.

The proposed **One more contribution** control must not perform an ordinary self-loop on the shared
review stage: that would close the stage, create another ID and rerun hooks. A narrow append command
is the minimal recommendation; a participant-owned child flow repeating one schema is an alternative.
See [topology and alternatives](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#proposed-round-and-proposal-model).

## Respond to feedback and revise

The following describes the current return/resubmission path. The proposed integration loop above
adds another route; it does not claim those controls are already present.

Read the review rationale and referenced issues before changing meanings. Preserve the source-led
question even when its proposed expression proves inadequate. Record each feedback ID, target
asset/field/question, evidence, proposed action, response and unresolved disagreement. Distinguish a
reviewer's suggested revision from a source edit actually applied.

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

**DETAILED WORKFLOW TO BE DECIDED** — Implement Hub-authorized PUBLIC participation without
special reviewer grants or invitations, with an accessible published packet and a narrow operation
for each person's own proposals. This is identified participation, not anonymous access. Agree
moderation, quotas, evidence disclosure and the Hub assertion/renewal contract. In current source,
`PUBLIC` is an admitted-group sentinel, not a `WorkflowRole` enum value. Known-person eligibility
requires authentication, non-anonymity, email and `workflow.knownRealPerson=true`; default REVIEWER
is added only if no workflow roles exist. PUBLIC access does not bypass stage ownership or make
generic uploads/buttons public. `publicRead` separately controls visibility and is read-only in the
IDE. See [the exact current and proposed distinction](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#roles-access-and-public-participation).


**DETAILED WORKFLOW TO BE DECIDED** — Separately choose contribution visibility: proposed
**Peer-visible contributions** lets eligible participants inspect submitted peer work while preparing
their own; **Editor-only contributions** lets each person see their own submissions and authorized
editors see all admitted submissions. Private drafts stay private unless explicitly shared. These
modes do not mean collection is accepting/closed, do not grant participation, and do not imply
anonymization or later public release. Pin the policy per round and enforce it on attachments,
history, search, summaries and exports as well as the main view. See the
[visibility contract](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#contribution-visibility-open-and-closed-review).
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-07).

### OR-08

**DETAILED WORKFLOW TO BE DECIDED** — Choose adjudicator, escalation and appeal policy. Recommend
an explicit disposition for every contribution action: adopt, modify, defer, reject or unresolved,
with reasons and attribution. Preserve dissent and incompatible alternatives; no arrival order,
vote count or generated summary establishes scientific truth. The editor may integrate provisionally
while collection is open, but final integration must use an immutable cutoff manifest and reconcile
all later admitted submissions. Decide whether reviewer acknowledgment or a pending appeal blocks
progression. See [integration](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c08--integration-and-the-next-step).
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-08).

## State and action reference

This matrix describes **current bundled workflow 1.1 only**. It is not the proposed public
contribution/integration topology, which needs new typed operations and wiring.

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

**DETAILED WORKFLOW TO BE DECIDED** — Define the editor's sufficiency criteria for advancing
integrated work, separately from final acceptance/rejection authority. Moving to the next stage is
not acceptance or publication. Recommend whole-candidate human acceptance initially; partial approval
requires a rebuilt exact candidate and revalidation. Current acceptance requires an ontology, exactly
one PASS each for IMPORT_CONTEXT, DOCUMENT_SCHEMA, PARSER, ADAPTATION and REASONER, no unresolved
semantics and authenticated human rationale. The server reruns checks; production defaults still
block acceptance. Neither a behavior action nor an integrator's judgment may forge those gates.
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-09).

### OR-10

**DETAILED WORKFLOW TO BE DECIDED** — Define authorized application, Git/PR handoff, publication
and rollback. `document.update` and project CRUD now provide real source mutation primitives;
`project.write_text` can store additional material and stage its path in Git. They do not supply
base-revision compare-and-swap, review application, an isolated commit/PR or release actor.
Project writes survive a later workflow failure. Recommend a dry-run plan, exact base check,
separate authorization and durable effect receipt before application. `APPLICATION` and `PR_HANDOFF`
remain BLOCKED in the review protocol. See [application boundaries](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c11--application-git-and-release-handoff).
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-10).

## Recover safely and preserve the record

Cancelling **Confirm exact proposal review** or a failed transition retains the local draft. A late
upload rejected while confirmation is open must be added again afterwards. **Reset** discards the
selected stage's unsaved form edits after confirmation; it is not an attachment rollback. Persisted
uploads in an existing flow have already changed the service. **Cancel workflow** is for the
provisional flow, not withdrawal of a submitted case.

On a stale revision, checksum mismatch, unavailable response or changed import, load a fresh case
before retrying. Selecting **Open flows** while its workflow tab remains open only focuses the cached
editor; it does not refresh that editor's candidate or history.

1. Copy all unsaved notes and form edits you need to preserve to a local file.
2. If the workflow is paired with source, toggle **Side-to-side workflow review** off. Close the
   individual workflow tab. When unsaved proposal edits exist, the confirmation asks **Discard
   unsaved proposal review edits and notes in this workflow?** Choose **OK** only after preserving
   them; **Cancel** keeps the tab and draft. Closing is refused while a submission is in progress.
3. Open the ontology asset's context menu again. Choose **Open flows**, or **Closed flows** if the
   case has completed, and select the same flow. With the old tab closed, this creates an editor from
   the freshly retrieved case.
4. Compare its candidate, visible history and checks with your saved notes; reconcile before retrying.
   A lost response may follow a successful server write. Do not merely replace `expectedRevision`
   on an old decision. If submission succeeded, continue from the current stage instead of making
   a duplicate case.

This closes and opens a local UI tab; it does not invoke the **Reopen flow** lifecycle action, which
is blocked for proposal-review flows. Closing does not undo persisted transitions or attachments.
Unknown review-extension versions remain inspection-only.

### OR-11

**DETAILED WORKFLOW TO BE DECIDED** — Agree cutoff, late contributions, withdrawal, retained
access, restricted-source handling and archival. Recommend sealed round/submission manifests;
late input receives a receipt and an explicit next-round or rejection disposition. Reopening creates
a linked new round rather than changing the old base. Current proposal flows block direct stage
creation/deletion, flow deletion and generic reopen. Generic behavior-only flows have weaker closure
protection and need typed guards before reuse. No backward-compatibility requirement dictates the
new protocol; decide archival treatment of existing flows explicitly. See
[closure policy](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c09--decisions-closure-reopening-and-late-contributions).
[Reviewer counterpart](ONTOLOGY_REVIEW_REVIEWING.md#or-11).

### OR-12

**DETAILED WORKFLOW TO BE DECIDED** — Establish live authenticated HTTP/Monaco verification,
durable operation receipts, idempotency/outbox, owner identity after restart and concurrency recovery.
A catalog error can occur after the flow revision committed; project writes/email can also survive
a failed action. Reconcile saved state and effects before retrying. New actions require exact flow,
contributor and document/base checks as applicable; a negative revision must not bypass them.
The manager currently serializes operations in one process; scalable public collection needs scoped
coordination and storage-level atomic admission/closure. Pin behavior source and test upgrades,
timeouts and revoked permissions. See [recovery contracts](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#c12--recovery-durability-and-behavior-updates).
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
- [ ] Current controls distinguished from proposed actions; no configured ontology behavior assumed.
- [ ] PUBLIC eligibility separated from peer-contribution visibility, round closure and decision authority.
- [ ] Private drafts, identity disclosure and later release follow the round's explicit policy.
- [ ] Each contribution and integration proposal preserves owner, immutable base and supersession lineage.
- [ ] Round cutoff, late submissions, dissent and both integration exits accounted for.
- [ ] Action error reconciled against persisted flow and external effects before retrying.

## Implementation references

The [behavior design](ONTOLOGY_REVIEW_BEHAVIOR_DESIGN.md#implementation-references) links the exact
bridge, content/target actor, authorization and IDE action sources inspected for this revision.
[WORKFLOWS.md](WORKFLOWS.md#kactors-instrumentation) and
[AGENTS_REFERENCE.md](AGENTS_REFERENCE.md#workflow-target-binding-and-restoration) describe shipped
mechanisms; [WORKFLOW_EXTENSION.md](WORKFLOW_EXTENSION.md) records remaining generic contracts.

The merged source governs behavior when older narrative notes disagree. The concise technical starting
point is the [proposal-review contract](../PROPOSAL_REVIEW_CONTRACT.md), read with the
[schema increment][increment], historical [backend result](../BACKEND_RESULT.md) and
[validation increment](../VALIDATION_INCREMENT.md).

- [Workflow definition][workflow], [WorkflowManager][manager] and [ProposalReviewProtocol][protocol]: roles, transitions, aliases, ownership and gates.
- [Flow types][flow], [ProposalReview types][types], [shared candidate inspector][binding] and [controller][controller]: transport and immutable bindings.
- [Isolated validator][validator] and [schema provider][schema-provider]: what production checks actually establish.
- Pinned IDE [WorkflowEditor][ide-shell], [ProposalStageEditor][ide-stage], [WorkspaceEditor][ide-workspace] and [review model][ide-model]: controls and local draft behavior.
- Pinned IDE [tab host][ide-tab-host]: individual workflow-tab close confirmation and disposal.
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
[ide-shell]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/WorkflowEditor.java
[ide-stage]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/ProposalStageEditor.java
[ide-workspace]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/WorkspaceEditor.java
[ide-model]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/ProposalReviewModel.java
[ide-tab-host]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/pages/EditorPage.java
[ide-notes]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/docs/PROPOSAL_REVIEW.md
[sandbox]: https://github.com/integratedmodelling/imod/tree/68a6cbc/experiments/strawman-2026/bootstrap
[method]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/METHOD.md
[hydrology]: https://github.com/integratedmodelling/imod/blob/68a6cbc/experiments/strawman-2026/bootstrap/hydrology/BOOTSTRAP.md
