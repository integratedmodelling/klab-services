# Ontology review behavior design for discussion

This is a documentation proposal, not an installed workflow or permission policy. Read the
[editing guide](ONTOLOGY_REVIEW_EDITING.md) and [reviewing guide](ONTOLOGY_REVIEW_REVIEWING.md)
for role-specific steps. This document explains how the new k.Actors mechanisms could support
them, including independent reviewer proposals and a repeatable review/integration cycle. No production
code, workflow YAML or ontology is changed by this design. Backward compatibility is not a design
requirement; choose a coherent new protocol rather than encoding new meanings in old fields.

## Verified baseline and capability map

Source inspected on **2026-10-07**, after fetching both remote `develop` branches:

| Repository | Exact baseline | Relevant additions |
| --- | --- | --- |
| klab-services | [`5bae64208a7098c6ee1e81813bd494f3417ce3be`](https://github.com/integratedmodelling/klab-services/commit/5bae64208a7098c6ee1e81813bd494f3417ce3be) | Workflow behavior bridge, attachment operations, document/project actors and additional project material. |
| klab-ide | [`70c4bd9ac0b42004511b8fd855483b31fb720467`](https://github.com/integratedmodelling/klab-ide/commit/70c4bd9ac0b42004511b8fd855483b31fb720467) | Stage action buttons, parameter discovery/dialog and background action requests. |

**Shipped mechanism** means present in this source, not verified in a deployed session.
**Proposed wiring** connects existing mechanisms but is not configured in the bundled ontology
workflow. **Missing primitive/API** requires new implementation. **Policy** requires a human
decision even when software can enforce it. All proposed contracts below are
**DETAILED WORKFLOW TO BE DECIDED**. Action names, new states and HTTP routes are recommendations,
not controls available to users today.

| Capability | Shipped behavior and limit | Source |
| --- | --- | --- |
| Workflow hooks | One `behavior` URN; ordered stage `onStart`, stage `onCommit`, transition `actions`; stage `actions` define buttons. No `onStop` property. | [Workflow types][workflow-types], [bridge][bridge], [manager][manager], [workflow documentation][workflows] |
| Binding | Reserved context names, then configured/allowed interactive values, then a unique Java/actor type match. No arbitrary expression evaluation in YAML parameters. | [Bridge][bridge] |
| Lifetime | Init/main once; portable checkpoint restored for later operations; finite functions and suppliers; emitter rejection. Owner must reconnect after restart. | [Bridge][bridge], [extension decisions][extension] |
| Stage content | Titles, descriptions, metadata, attachment text/bytes/server file/HTTP(S) URL, reads and removals subject to normal rules. | [Content agent][bridge], [attachment sources][attachment-sources] |
| Targets | Fresh document source/statements/imports, document CRUD, project permissions/locks, additional material. Uses invoking participant for operations. | [Core actors][actors], [actor reference][actor-reference] |
| Buttons | GET discovery and POST configured action ID, mandatory exact flow revision, current open stage, contributor plus stage-editor checks. | [Action DTO][action-dto], [controller][controller], [manager][manager] |
| IDE | Buttons on persisted editable stages, scalar parameter form, **Run** and **Cancel**, background discovery/execution, refresh after success. | [WorkflowEditor][ide-shell] |
| Ontology protocol | Workflow 1.1 remains unwired: no `behavior`, lifecycle actions or buttons. One exact candidate per submitted author revision; return goes to original author. | [Bundled YAML][workflow], [proposal protocol][protocol] |
| Public access | `PUBLIC` is an admitted-group sentinel; known-person access can bypass the workflow allow-list. It is not a fourth enum role or anonymous access. | [Participant][participant], [role enum][roles], [access rules][access] |

Existing tests exercise bridge restoration, binding, supplier failures, attachments and authorization;
the IDE has scalar input tests. They do not establish that this proposed review behavior, public
contribution service or integration protocol exists. We have not performed a live HTTP/Monaco test
for this design. The local workflow-hook conversation was consulted for intent; source determines
the capability claims above. Repository `.agents` directories contained no additional skills.

## Roles, access and PUBLIC participation

**DETAILED WORKFLOW TO BE DECIDED — OR-01, OR-02, OR-07, OR-09.** Adopt the following separation,
then decide the appointing authority, competence criteria, quorum and conflicts policy.

| Responsibility | Recommended authority | Explicit boundary |
| --- | --- | --- |
| Author/editor | Own the author proposal line and its working document; submit revisions. | Cannot rewrite a reviewer's submitted proposal or claim its authorship. |
| Assigned reviewer | Accept a scoped review assignment; own a contribution proposal line. | Assignment grants no project-source editing or parent-case closure authority. |
| Public contributor | Hub-authenticated, authorized known person; create and revise their own contribution while a PUBLIC round is open. | No invitation, special reviewer grant or membership in an editorial group required. |
| Integrator | Explicit case assignment, normally an EDITOR under the present role vocabulary. | Integrates contributions with attribution; cannot silently edit their originals or accept on their authors' behalf. |
| Acceptance authority | Separately appointed, authenticated decision maker. | Integration is not scientific acceptance, application or release. |

One person may hold several responsibilities only if the agreed conflict policy permits it; record
which capacity they used for each act. The flow owner, proposal author, stage owner and integrator
are separate identities. Do not make the integrator the original author merely to reuse return logic.

**Current PUBLIC implementation.** `WorkflowRole` contains only ADMIN, REVIEWER and EDITOR.
`WorkflowParticipant.from` recognizes a known person only when the user is authenticated,
non-anonymous, has an email and has identity data `workflow.knownRealPerson=true`. It adds REVIEWER
only if the role set is otherwise empty. A state admitting `PUBLIC` can bypass `workflow.permitted`
when this participant is a known person with REVIEWER and an admitted role. Existing nonempty role
sets therefore deserve explicit tests; public eligibility must not depend on whether an unrelated
role happens to be present. The trusted issuer of the known-person fact must be verified in Hub
integration; clients must not be allowed to self-assert it.

That access check does **not** create a contribution, assign a stage, permit upload or authorize a
parent transition. Current button/upload operations still require contributor and stage-editor
permission. An assigned reviewer has a narrower review-transition allowance, not general edit
permission. `publicRead` is a separate visibility switch and opens a read-only browser in the IDE;
it is neither PUBLIC participation nor an acceptance grant.

**Recommended public path, missing API:** a service operation admits an eligible Hub-authorized
person to an open PUBLIC round and creates a contribution owned by that person. It checks access
to the published round packet and permits edits only to that contribution. It must not require an
invitation, an EDITOR grant, `workflow.permitted`, or a reviewer search result. Preserve ordinary
abuse controls, deadlines and per-person limits without disguising them as special reviewer grants.
Keep assigned review for requested expertise and accountable coverage. Public and assigned
contributions enter the same integration ledger; policy may distinguish their review weight, never
their provenance. A public contributor cannot close the parent round by submitting feedback.

For restricted evidence, prepare an independently authorized public packet. A PUBLIC round cannot
promise participation while hiding all material needed to assess it. Do not expose protected source
attachments merely because contribution creation is open. Anonymous participation remains outside
the requested model.

## Proposed round and proposal model

**DETAILED WORKFLOW TO BE DECIDED — OR-04, OR-06, OR-08, OR-11.** Recommend a minimal, stage-local
public contribution command: one parent collection stage stays current while each person appends
their own immutable proposals through a narrow operation. Keep drafts owner-specific, and store
submissions in a paginated index rather than one shared stage's metadata/attachment list. This needs
typed round/base references, ownership, contributor revisions and idempotency, not a new general
collaboration framework. Reusing participant-owned flows is an alternative when their stage history
and hooks justify the extra objects. Neither route is implemented by current generic flow creation.

```text
PROPOSED parent case
author preparation -> public/assigned review(round n) -> editor integration(round n)
                         |                                  |
                         |                     integrated proposal I_n, with two exits:
                         |                     revised material -> review(round n+1)
                         |                     sufficient state -> next permitted stage
                         +-> assigned contributions C_1, C_2, ...
                         +-> PUBLIC contributions C_3, C_4, ...

Participant iteration within one round:
own draft -> append contribution receipt -> own next draft / revise own proposal
This operation does not transition the shared review stage.
Author editing can be delegated when needed; it is not a mandatory detour on every loop.
Onward progression is not automatically acceptance, application or publication.
```

The proposed **One more contribution** control wraps this append command. If a literal transition
is preferred, use `contribute-again` only on a participant-owned child flow repeating one schema.
Existing transitions close the source and create a new target, normally owned by the triggering
user, even when both have the same schema. A shared review-to-review self-loop would invalidate
other reviewers' current stage, increment the shared revision and rerun `onCommit`, transition
actions and `onStart`. It is not a safe append mechanism. PUBLIC authority cannot choose another
target schema, advance the parent, close collection, integrate, accept or apply. Per-person
preparation must not reopen a round, resnapshot its base or send whole-round invitations.

Distinguish **round status** (open/closing/integration/closed) from **participant iteration** (draft
number and submitted revisions). Reusing the current round status is useful for admission, but it
is never the identity of the candidate being reviewed. Every submission still addresses the same
immutable round/base. "One more contribution" creates a new proposal ID for a new claim; "revise my
contribution" creates a revision in that person's existing proposal line with explicit supersession.
An alternative policy is one line per participant per round; decide it rather than conflating the
two operations. Bound each person's iterations and attachment sizes without imposing special grants.

The explicit **integration** stage produces an attributed integrated proposal and disposition ledger.
The authorized editor/integrator may update review material and choose **another public review
round** against a fresh immutable candidate revision, or judge it **sufficient to move on** to the
next permitted stage. These are proposed controls, not current button labels. Record the human
sufficiency rationale, unresolved items and next-stage gates. Keep all earlier contributions and
dispositions. Material changes do not inherit previous approval. The editor can instead request
author edits where authorship or project policy requires it; that handoff is optional in the cycle.
Editing review material does not itself authorize overwriting the authoritative project source.

| Record | Identity and immutable binding | Revision rule |
| --- | --- | --- |
| Author proposal `A_n` | Proposal ID, revision, candidate ontology bytes/hash, target/base snapshot, import snapshot/digest, ordered action IDs, evidence. | `A_(n+1)` supersedes the prior revision of the same author proposal line. |
| Review round `R_n` | Case/round ID, exact candidate `B_n` (initially `A_n`, subsequently an editor/integration revision if applicable), visibility/public eligibility, opening/cutoff, assignment records. | Changed material/imports require a new round; the old base does not change. |
| Contribution `C_i@r` | Its own proposal ID/revision, owner, `R_n`, exact `B_n` base, evidence/actions/probes, optional alternative candidate bytes. | Only its owner revises it; `supersedes` stays within that contribution line. |
| Integration proposal `I_n@r` | Its own proposal ID/revision and integrator; exact round snapshot and selected contribution revisions; each action's disposition and reasons. | Integration revisions supersede integration revisions, not the author or reviewer proposal. |
| Next review material `B_(n+1)` | Integrated proposal/candidate revision with named editor, or an optional author response referring to `I_n@r`. | Preserves each proposal line's actual authorship; links across lines with `basedOn`/`respondsTo`, not false supersession. |

Each contribution is a **proposal**, even if it proposes no source change and instead records an
evidence-backed objection or endorsement. Use a typed envelope around a versioned proposal payload;
choose a new schema deliberately. Cross-line fields such as `basedOn`, `integrates` and `respondsTo`
are new contracts, not existing context-pack 1.3 fields. The current single `supersedes_revision`
cannot express this graph. Extend the shared inspector, protocol, schema and IDE together before
offering these operations. An attachment filename or an unvalidated metadata map is not a substitute.

Recommended provenance includes stable actor identity, capacity, service/workflow/behavior source
fingerprints, timestamps, action/operation IDs, exact input/output artifact hashes, base/import
references, visibility policy revision and execution receipt. Keep full identifiers on the server;
expose only the actor profile fields authorized for the viewing audience. Evidence bytes must retain
their original access labels even when multiple proposals reference them.

Storage alternatives remain open for discussion:

| Topology | Benefit | Required protection or cost |
| --- | --- | --- |
| Recommended starting point: stage-local append command with owned drafts/submissions | Keeps one collection stage current; narrow PUBLIC authorization; independent submission receipts. | Small typed submission store/index, contributor revision and atomic open-round admission. Do not embed unbounded payloads in the parent. |
| Participant-owned flows repeating one contribution schema | Reuses current flow/stage/history machinery and isolates owners/revisions. | Typed child links, scoped public creation, contribution protocol and atomic round admission/closure still needed. |
| Several participant-owned stages inside one parent | Fewer aggregate types and one local coordination point. | Shared flow revision causes cross-person conflicts; direct stage creation is blocked for current proposal flows; add typed operations, quotas and access projection. |
| Contributions stored in a dedicated normalized service | Better indexing and independent updates for very large rounds. | Larger implementation; adopt when measured scale justifies it, preserving the same typed contracts. |

Putting all public drafts into one shared stage and removing its ownership checks is not an
acceptable shortcut. Keep the parent collection stage editor-owned and contribution ownership
independent. A paginated index and closure barrier are needed whichever storage option is chosen.

## Operation-to-action map

**DETAILED WORKFLOW TO BE DECIDED.** This table maps the proposed complete workflow, including gaps.
The hook names are shipped; the bindings and review-specific actions below are proposed. The
contracts in the next section define the missing pieces. All actions receive fresh authorized
context; automatic actions cannot pause for a modal to supply missing parameters.

| Review operation | Proposed hook or button | Initial implementation and remaining dependency | Decision |
| --- | --- | --- | --- |
| Establish case and author | Top-level `init`; editing `onStart: prepare_editing` | Keep stable scalar IDs/target handles; prepare instructions via `content`; case-role assignment needs C01. | OR-01 |
| Choose scope, sources and agriculture/land boundary | Editing button `record_scope` | Store structured draft metadata/supporting material; human source analysis remains required. | OR-03 |
| Gather evidence from text/file/URL | Editing button `attach_evidence` | Existing `content.attach_*`; C02 adds provenance, source access labels and retention. | OR-04, OR-11 |
| Snapshot the saved document | Editing button `snapshot_saved_document` | Existing `document.source` plus `content.attach_text` is a useful prototype; C03 adds atomic base identity and exact bytes. | OR-04 |
| Snapshot the document currently edited in the IDE | Editing button `snapshot_editor_document` | C03 client capture/upload required; server `editor` cannot read an unsaved buffer. | OR-04, OR-12 |
| Extract a proposal | Editing button `extract_proposal` | C04 derives a draft from an immutable base/edited snapshot pair; no shipped extractor actor. | OR-04, OR-06 |
| Prepare questions, bindings and predicate analysis | Editing buttons `record_questions`, `record_predicates` | Existing native forms plus C04 coverage ledger; automation may identify gaps, not invent meanings. | OR-03, OR-05 |
| Validate candidate and imports | Editing button and `onCommit: validate_submission` | Reuse isolated schema/parser checks through a new C05 actor facade; immutable imports/reasoner remain missing. | OR-05 |
| Freeze submission | Submit transition `actions: freeze_author_revision` | C02/C03 verify displayed hashes; hook must not rewrite confirmed bytes. | OR-04, OR-12 |
| Discover/assign reviewers | Review `onStart: prepare_review`; button `find_reviewers` | C06 Hub controller/query; C01 records assigned reviewers and acceptance of assignment. | OR-02 |
| Open public participation | Review `onStart: publish_round_packet`; public `start_contribution` service action | C01/C07 admit Hub-authorized PUBLIC participants without special roles/invitations. | OR-07 |
| Create, save and revise a review proposal | Owned draft actions and `append_contribution`; optional child `contribute-again` | C07 stage-local immutable append with ownership, exact base and idempotency; never transition shared review. | OR-06 |
| Notify participants | Transition action `enqueue_notice` | `core.email` exists; C10 durable outbox/receipt needed for reliable retry and scaled delivery. | OR-02, OR-07, OR-12 |
| Close a round | Parent transition `actions: close_round` | C08 freezes admitted contribution revisions under a concurrency barrier. | OR-02, OR-08, OR-11 |
| Integrate contributions | Integration `onStart: prepare_integration`; buttons `compare_contributions`, `record_disposition` | C08 creates an integrator-owned proposal; human handles semantic conflicts and dissent. | OR-06, OR-08 |
| Iterate or advance integrated work | Integration `onCommit: validate_integration`; transitions `review_again` or `advance_integrated` | C08 freezes `I_n`; editor selects a new public round with revised material or next permitted stage, subject to gates. | OR-06, OR-08, OR-09 |
| Delegate author edits when needed | Optional `return_integrated` and author `onStart: prepare_response` | New author revision responds to `I_n`; preserve original author ownership and reopen review with a new base. | OR-01, OR-04, OR-06 |
| Accept/reject/escalate | Decision `onCommit`; explicit decision transition actions | C05 checks plus C09 human authority, rationale and conflict rules. No automation supplies scientific approval. | OR-08, OR-09 |
| Apply, commit, hand off and publish | Separately authorized post-decision actions | Existing `document.update` can mutate source; C11 base-CAS, transaction/effect receipts and Git/release adapter still required. | OR-10 |
| Close, reopen or handle late input | Terminal `onStart: seal_record`; explicit new-round action | C09/C12 preserve sealed history; no automatic hook replay or silent late merge. | OR-11 |
| Recover/retry/audit | Any action through operation envelope; button `inspect_operation` | C10/C12 durable receipts, retry classification and idempotency keys. | OR-12 |

## Contracts for behavior wiring and missing actors

The following is **pseudocode/design**, not compilable k.Actors, YAML to install, or an existing API.
Recommended action names are intentionally explicit. Resolve technical contracts before wiring
them; a behavior is not an authorization boundary or a replacement for typed protocol enforcement.

### C01 — Case responsibilities and participation

**DETAILED WORKFLOW TO BE DECIDED — missing API and policy (OR-01, OR-02, OR-07).**
Propose `review.case.prepare(flow, participant, target)` and
`review.round.join(roundId, participant, mode)` with `mode=ASSIGNED|PUBLIC`.
Input identities come from authenticated context, not form fields. An assigned join requires a
current scoped assignment; a PUBLIC join requires Hub authorization, published packet access and
an open PUBLIC round, with no special reviewer grant. Return an owned draft-space reference and
allowed operations. A duplicate join returns that same reference; it does not duplicate enrollment
or prohibit distinct proposals for new claims. Denial must not disclose hidden assignments or cases. Role changes are
audited and checked again on every mutation; hooks never set owner/assignees directly on Java beans.

### C02 — Evidence and immutable proposal packaging

**DETAILED WORKFLOW TO BE DECIDED — proposed wiring plus missing typed contract (OR-04, OR-11).**
Use existing content attachment calls for the first attachment prototype. Propose
`review.package.freeze(proposalRevision, artifacts, provenance, expectedDraftRevision, operationId)`
to persist immutable descriptors and a manifest linking proposal, ontology, dossier and questions.
The server computes hashes, MIME/size checks and storage IDs. Require source identity/location,
retrieval date, version, supported claim, access rights and transformation history; do not trust a
caller-supplied checksum as verification. Return the same receipt on an identical retry; reject
reuse of a revision or operation ID with different bytes. Attachments and project additional
material are distinct stores; a project path may hold a working copy, never the canonical evidence
identity. Do not delete or replace previously submitted evidence to make a revised proposal fit.

### C03 — Saved source and IDE buffer snapshots

**DETAILED WORKFLOW TO BE DECIDED — proposed wiring plus missing primitive (OR-04, OR-12).**
Prototype a saved-source button using existing verbs:

```text
PSEUDOCODE, using shipped verbs; not an atomic source snapshot API
snapshot_saved_document(document, content):
    source = document.source()
    attachment = content.attach_text("candidate-ontology", "candidate.kwv",
                                     "application/vnd.klab.ontology", source)
    record attachment ID; display its server-computed checksum for review
```

This captures the UTF-8 encoding of returned source text, not necessarily byte-for-byte disk data.
`document.version()` is an authored version, not a storage revision/CAS token. Separate calls to
source/version can race. Recommend a new `review.document.snapshot(target, expectedStorageRevision)`
that atomically returns original bytes, encoding, hash, immutable storage revision, target/project/
service identity and authored version. An absent/stale base must fail, not capture a different base.

For the user's **currently edited document**, add an explicit IDE capture/upload contract. Freeze
the chosen buffer's bytes, document URI, local buffer revision and last synchronized base snapshot;
show saved versus unsaved state and upload the immutable capture. The server authenticates the
target, computes the hash and returns a snapshot receipt. Later keystrokes cannot alter it. Cancel
before upload has no server action; an uploaded unused draft has an explicit retention lifecycle.
Do not save the shared source merely to obtain a review snapshot. No `editor.buffer` or automatic
client connection exists today. Recommended first delivery is the saved-source prototype plus
manual exported-file upload; the atomic/base-aware and unsaved-buffer routes need implementation.

Never generate or replace a candidate in `onCommit` after the user confirmed a different hash.
Prepare through a button, inspect and confirm it, then have the transition hook verify exactly those
bytes. A generated change forces a new preparation/confirmation cycle.

### C04 — Proposal extraction, questions and predicates

**DETAILED WORKFLOW TO BE DECIDED — missing actor (OR-03, OR-04, OR-06).**
Propose `review.proposal.extract(baseSnapshot, editedSnapshot, evidenceIds, contextSnapshot)`.
Return a draft proposal with stable action IDs, target IDs, preconditions, before/after references,
source mappings, unresolved interpretations and extractor/version provenance. It must not mutate the
project or mark scientific checks PASS. Distinguish syntactic edits from uncertain semantic
interpretation; preserve unknowns and ask a human to confirm them. A first implementation may use a
deterministic structural diff and explicit editor mappings. An LLM-assisted alternative must record
model/prompt/input hashes and mark every suggested meaning for human verification.

`review.coverage.assess(proposal, sourceQuestions)` returns a question/concept coverage ledger, not
new evidence. Preserve the source-first fifteen questions and justified five-per-kind shortfalls.
Positive cases, counterexamples, imported derivation and result category remain part of the packet
when a native form lacks fields. Fresh reviewer probes have provenance distinguishing
candidate-visible automation, blind review and domain-expert review. A predicate suggestion does
not establish an exhaustive category scheme or turn unknown evidence into a domain category.

### C05 — Validation and readiness

**DETAILED WORKFLOW TO BE DECIDED — missing actor facade, missing validators and policy (OR-05).**
Propose `review.checks.inspect(candidateBinding, contextSnapshot, requestedChecks)` returning typed
findings tied to exact bytes and validator versions. Adapt the existing isolated schema/parser/
adaptation validator; do not call the authoritative Reasoner as a casual read-only check. Add
authoritative import snapshots, isolated reasoner execution and semantic action/source
correspondence as separately failing/blocked checks. Schema PASS never implies scientific validity.
Use `review.checks.require_ready(report)` as a finite validation action that throws on unmet gates:
the current bridge ignores return values, so returning `false` would not prevent a transition.
Content metadata can display a report but cannot forge server-owned `proposalReview` checks.

### C06 — User-accessible Hub reviewer query

**DETAILED WORKFLOW TO BE DECIDED — missing Hub API and policy (OR-02, OR-07).**
`klab.hub` is not installed in this workspace. The following separate **WorkflowReviewerController**
is an ideal contract, not a claim about existing Hub code or endpoint names. Keep it distinct from
administrative user search. Make it accessible to authenticated people eligible to coordinate the
specified case, including non-administrative editors/integrators; do not require Hub administration.

Recommended read-only routes, relative to the future Hub API base:

```text
GET /workflow-reviewers?serviceId=...&caseId=...&roundId=...&expertise=...&language=...&cursor=...&limit=...
GET /workflow-reviewers/{reviewerId}?serviceId=...&caseId=...&roundId=...
GET /workflow-participation?serviceId=...&caseId=...&roundId=...
```

Use a verified Resources-service authorization decision or a short-lived audience-bound capability
for that exact case, round, caller and operation. The Hub checks issuer, expiry, revocation and
current workflow visibility; it must not trust query parameters asserting EDITOR/REVIEWER roles.
Limit candidate results to profiles consenting to workflow discovery and compatible with the
caller's visible assignment scope. Return stable reviewer ID, allowed display name, disclosed
expertise/languages, availability, declared conflicts visible to the caller, eligibility reason
codes, profile revision and next cursor. Keep email, private memberships and sensitive conflict
details out unless separately authorized. Do not reveal the existence of inaccessible profiles or
cases through counts, errors, search timing guarantees or pagination tokens.

The participation route lets a person inspect their own public eligibility, accessible round packet
and allowed next action. PUBLIC contributors do not need to appear in reviewer search or obtain a
search result/invitation. Broad discovery of other people's profiles remains coordinator-scoped;
self-service participation is not an administrative directory query.

Recommended responses: 200 with a bounded page, 401 unauthenticated, 403 for a visible case with no
operation grant, non-disclosing 404 for hidden cases/profiles, 409 stale case/policy reference,
429 with retry guidance, and 503 when eligibility cannot be safely established. Cap page size and
query cost; use opaque caller/query-bound cursors and short-lived caches. Recheck eligibility when
assigning or joining; a search result is not an authorization grant. Log caller, purpose and
case/round reference with minimized query data. A separate Resources assignment endpoint records
the actual assignment and its acceptance; a read-only Hub query sends no invitation.

Propose `review.reviewers.find(flow, participant, expertise)` as a finite read-only adapter to this
controller. Its initial output can be a paginated report attached to a coordinator-only stage.
An IDE reviewer picker is another missing UI; do not substitute a free-text username box with no
identity resolution.

### C07 — Independent iterative contributions

**DETAILED WORKFLOW TO BE DECIDED — missing aggregate/API/UI (OR-04, OR-06, OR-07).**
Recommend scoped Resources operations to join/create, read, save, submit, supersede and withdraw a
contribution. Each checks case/round visibility, participant eligibility, owner, base, contribution
revision and operation ID. Public eligibility authorizes only one's own contribution; it grants
no ability to edit the parent dossier, other people's proposals or project documents. Assignment
and public participation produce the same typed contribution envelope with different participation
provenance. Read another contribution only when round disclosure policy permits it.

For the recommended minimal implementation, propose this narrowly scoped command:

```text
PSEUDOCODE — proposed Resources API, not a shipped route
POST /review-rounds/{roundId}/contributions
  roundEpoch, publishedPacketId, baseSnapshotId, baseHash
  proposalId, revisionId, expectedOwnedDraftRevision, optional supersedesRevision
  artifactIdsAndHashes, idempotencyKey
-> immutable submissionId, sequence, proposal binding, round receipt, next owned draft reference
```

Authenticate and recheck PUBLIC/assigned eligibility, exact packet access, owner and immutable base.
The round epoch changes on lifecycle/base changes, not each other person's submission. The
contributor's revision changes only for their own draft/proposal. An atomic open-round check and
append assigns the sequence before acknowledging success. Never accept a negative/unset revision
as a bypass, even though some generic transition paths permit a negative expected flow revision.
The same idempotency key/input returns the same authorized receipt; different input is rejected.
Do not increment or transition the parent merely to submit one contribution. Recheck authorization
before returning an earlier receipt, so a retry cannot recover now-forbidden evidence.

For the alternative participant-flow implementation, use that flow's revision for draft CAS and a
typed submission ID for each closed contribution stage. A `contribute-again` transition publishes
that person's proposal and prepares the next stage of the same schema. It must check the parent
round's admission barrier atomically with registration of the submission. Current flow-level
revision checks alone cannot provide that cross-flow guarantee. A draft may remain locally readable
after the round closes, but its next submission must receive an explicit late/rejected disposition,
not a silently changed base. A separate finish/withdraw action closes only the participant's work.

Bind the child to the authorized published review packet. PUBLIC participation must not require
full READ/UPDATE access to the original project merely to resolve a target `document` actor. A
scoped packet/target resolver is part of the missing creation contract; no document/project actor
is injected with the author's authority. Only genuinely permitted project operations may use those
wrappers. Integration and acceptance remain parent operations with separate authority.
The same packet-access constraint applies to the minimal append/draft route.

Submitted revisions are immutable. A reviewer can submit a
new revision superseding their own earlier one while the round remains open; other authors' input
can be cited but not superseded. A changed base requires an explicit rebase proposal/new-round
contribution with old and new base references and fresh validation. No text merge automatically
establishes semantic equivalence. A withdrawal is an attributed event with retention policy, not
erasure. A first native UI needs an owned contribution editor, exact snapshot confirmation and
submission receipt, separate from the current inspection-only review stage.

### C08 — Integration and the next step

**DETAILED WORKFLOW TO BE DECIDED — missing protocol and human policy (OR-02, OR-06, OR-08).**
Propose `review.round.close(roundId, expectedRoundRevision, operationId)` and
`review.integration.prepare(roundSnapshotId)`. Only the designated integrator/coordinator may
close a round. The closure barrier serializes against new submissions and freezes a deterministic
set of contribution revision IDs. Every attempt receives an accepted-in-round or late receipt;
the service must not acknowledge a submission and then lose it from integration. Record withdrawn,
superseded, excluded and pending drafts separately. Quorum/coverage policy determines when closure
is permitted; a deadline alone does not prove consensus.
The service assigns the final cutoff sequence and manifest from committed submissions; a caller
cannot supply an older cutoff to hide already admitted input. A provisional integration batch can
use an earlier watermark, but later arrivals remain pending for explicit reconciliation.
If the index is stored separately from the flow, a hook alone cannot make closure atomic. Use a
typed coordinator transaction, or a durable closing receipt/state that blocks new admission while
the parent transition is reconciled. Do not close the ledger as an untracked external effect and
then pretend a failed flow save reopened collection. This is a required small concurrency contract,
not permission to remove existing ownership checks.

An editor may **integrate provisionally while collection remains open**. Keep a distinct
integrator-owned draft/task and record the exact submission-index watermark and input manifest for
each iteration. New input makes that draft incomplete, not retrospectively wrong or automatically
final. A comparison action can show the delta since its watermark. This reduces the final backlog
but adds reconciliation work and may influence reviewers if drafts are visible; choose disclosure
policy explicitly. A simpler first version waits until closure before integration begins.

For either final integration exit, close collection, obtain its immutable cutoff manifest, reconcile every newly
admitted submission and supersession, and freeze the integrated proposal against that manifest.
The mutable "current" status or latest query page cannot serve as its evidence base. Do not add
an unguarded extra parent stage to simulate this: current proposal lifecycle controls prohibit
direct stage creation. A separate provisional editor task can reuse the same contribution-flow
machinery until the explicit parent integration stage starts.

The integration stage displays the frozen round base and each selected proposal separately. It
may pre-group duplicate targets and syntactic overlaps, but the integrator records a disposition
for every submitted action: adopt, adopt-with-modification, defer, reject or unresolved. Store
reasons, evidence, resulting integrated action IDs and reviewer attribution. Explicitly retain
conflicting meanings and dissent. Neither arrival order, vote count nor automation may silently
choose scientific truth. An integration draft may need another integration revision or escalation.

`review.integration.require_complete(I_n)` checks full accounting, exact references, authorized
integrator, unresolved flags and any required reviewer acknowledgment. The integration editor then
chooses one of two primary exits:

- `review_again`: freeze the revised material and integrated proposal, open a new public/assigned
  round with a new immutable base/packet revision and rechecked access. Keep earlier rounds and
  dispositions. New rounds do not silently inherit old reviewer decisions or pending submissions.
- `advance_integrated`: record the editor's sufficiency rationale and move to the next configured
  stage only after its gates pass. A gate may allow unresolved issues to proceed to explicit
  adjudication; it must not relabel them resolved. This is not final acceptance, application or release.

The optional `return_integrated` exit delegates edits to the original author with `respondsTo=I_n`.
It does not change parent ownership or apply source edits. Decide who may edit the new review
material: the integrator's revision must retain their authorship, with links to prior author work,
instead of borrowing the author's proposal ID to conceal the change. A reviewer can dispute a
disposition through an attributed proposal/appeal; whether this blocks either exit is policy.

### C09 — Decisions, closure, reopening and late contributions

**DETAILED WORKFLOW TO BE DECIDED — missing policy/operations (OR-08, OR-09, OR-11).**
Keep scientific acceptance and rejection separate from contribution submission and integration.
Acceptance needs an authorized human rationale and all exact-candidate gates; the current blockers
still apply. Record whether a decision covers all actions, selected actions or deferral. Recommend
whole-candidate acceptance initially; partial approval needs an explicitly rebuilt candidate and
revalidation, never application of a subset behind an unchanged checksum.

At closure, seal the decision and references. Late input receives a durable receipt and either a
new-round queue or explicit rejection with reason, according to policy; it cannot alter the closed
integration set. Reopening should create a linked new round/case revision with new access/deadline
checks, preserving the sealed record. Current proposal flows prohibit generic reopen; a button
named "Reopen" cannot remove that restriction. Decide who can reopen, whether late material is
publicly visible, and how withdrawn evidence or author absence is handled. Ordinary read/refresh
and administrator reopen do not replay behavior hooks today.

### C10 — Operation receipts, notifications and idempotency

**DETAILED WORKFLOW TO BE DECIDED — missing primitive (OR-02, OR-12).**
Add an operation envelope with authenticated actor, case/round/stage, behavior fingerprint/action,
expected aggregate revision(s), input hashes and caller-generated idempotency key. The server
stores a unique key plus normalized input digest and a receipt. Same key/same input returns the
recorded outcome; same key/different input is a conflict. Revision checks alone do not deduplicate
a timed-out request. Store button executions as audit events distinct from transition history.

Recommend a transactional outbox for email, project writes, Git handoff and other external effects.
Commit the intended effect and receipt with the aggregate; workers use leases and destination
deduplication keys. Report queued, running, succeeded, retryable failure, terminal failure or
unknown outcome. Never report delivery merely because it was queued. `core.email` can send now,
but a sent message survives a later workflow failure and may be repeated on retry. Supplier timeout
does not prove the remote effect stopped. Until the outbox exists, restrict prototype actions to
read/attachment preparation and require manual reconciliation before repeating external effects.

### C11 — Application, Git and release handoff

**DETAILED WORKFLOW TO BE DECIDED — existing mutation primitives, missing safe orchestration and
policy (OR-10).** `document.update`/`project.update_document` can write a project using the caller's
permissions and existing lock; `project.write_text` can store additional material and stage its
path in Git. These do not provide an ontology-review application transaction, a commit/PR/release
actor or rollback. A successful document write is external to the flow's aggregate transaction.

Propose `review.application.plan(acceptedBinding, expectedBaseSnapshot)` producing a dry-run change
set and `apply(planId, expectedStorageRevision, operationId)` with compare-and-swap, revalidation,
explicit authorization and durable effect receipt. Fail if the base/imports changed. Do not
overwrite concurrent work or assume acquiring a project lock proves the reviewed base is current.
Git handoff must identify repository/branch/base commit, exact paths, patch hash and authorization;
never commit unrelated staged project material. Release/publication needs its own approved
destination, actor and verification/rollback plan. This documentation revision authorizes none of
these implementations or external effects.

### C12 — Recovery, durability and behavior updates

**DETAILED WORKFLOW TO BE DECIDED — missing operational contracts (OR-11, OR-12).**
Failures before aggregate persistence, including hook, parameter and checkpoint failures, prevent
the working aggregate from being published. However, `persistMutation` writes the flow **before**
synchronizing `ResourceInfo`; a catalog error can be returned after the new flow revision committed.
Read the authoritative aggregate/receipt before retrying. Attachment payload staging has compensating cleanup, not a multi-record
transaction; process crashes can leave orphan blobs. Project writes and email are not rolled back.
Recover by inspecting the operation receipt and current aggregate, then reconciling external effects
before deciding whether to retry. If no receipt exists in today's prototype, preserve notes,
refresh the case and obtain coordinator assistance; do not assume either success or failure.

Persist portable IDs/references, not live Java beans, credentials, editor widgets or service
instances. Pin behavior and transitive dependency source fingerprints per case/round. The shipped
resolver can recompile changed source with the same version and restore old globals; version/layout
changes fail closed. Recommend explicit migration/restart policy rather than treating a same-version
edit as harmless. No backward-compatibility constraint requires automatic migration of old flows.
Record their disposition or keep them read-only under a separate agreed archival policy.

For scale, add per-aggregate serialization and storage-level CAS across service processes. The
present manager is synchronized in one process across behavior resolution/execution, and compilation
occurs per operation. The supplier wait is bounded, but synchronous supplier/function execution is
not preempted by that timeout. Provide
bounded work queues, pagination, evidence quotas, retry budgets and a durable closure barrier before
admitting a large public round. Do not load all contributions into flow metadata or a single
checkpoint. Owner absence after restart blocks today's bridge; resolve durable revocable execution
identity without giving a public contributor the original author's project authority.

## Lifecycle wiring and failure boundaries

**DETAILED WORKFLOW TO BE DECIDED — proposed wiring (OR-04, OR-06, OR-12).** Use `onCommit` for
the user-requested stage stop/exit preparation. It runs before a transition, not whenever an editor
closes or a stage is administratively closed. There is no general stop/delete/reopen callback.
Terminal stages still run `onStart`; closing a UI tab invokes no workflow lifecycle action.
Generic `updateState` can close an ordinary behavior-only stage without `onCommit`, transition
actions or required-artifact validation. Current proposal-enabled flows prohibit that status edit.
The new contribution protocol must preserve equivalent protection: closure only through its typed
operations, including explicitly handled abandonment. Wiring hooks alone does not enforce it.

```text
PSEUDOCODE of an intended ordinary transition using shipped hook positions
authorize participant + check expected flow revision and allowed transition
restore behavior checkpoint
outgoing.onCommit: verify exact prepared inputs; throw if invalid
transition.actions: record proposed transition-specific intent; no silent candidate rewrite
validate required attachments and exact proposal protocol
checkpoint outgoing; prepare/close source and create target with transition history
incoming.onStart: prepare instructions/authorized draft context
checkpoint incoming; persist aggregate and staged attachments
```

Initial creation also executes `init`, then `main`, the INIT transition actions and stage `onStart`.
Automatic first submission runs its full sequence before publication. Since initial IDE stages
are provisional and have no behavior buttons, a practical extraction/snapshot first step needs a
persisted draft creation path or a pre-flow preparation UI. Neither is supplied by merely adding
`actions` to the current YAML. Recommend a persisted author draft with its own safe cancellation
contract in the new protocol; keep manual package upload as the current route.

Bind `workflow`, `flow`, `stage`, `content`, `transition`, `user`, `participant`, `editor`, `document`
and `project` by reserved name as applicable; configured scalar values can supply scope labels or
report titles. The actor wrappers resolve current project/document data on use, not immutable
review snapshots. An unresolved automatic parameter aborts; button discovery returns missing inputs
for the UI. Existing text/boolean/numeric fields are adequate for a report title, not for a reviewer
picker, arbitrary agent, attachment selection, secrets or a rich integration decision.

Maintain business invariants in typed services. The bridge receives mutable aggregate beans and
trusted behavior code; a convention saying "do not change ownership" is not a security sandbox.
New facades should narrow writable fields, enforce authorization and return typed outcomes. Do not
use direct bean mutation to bypass proposal immutability, set server validation, mint permissions,
open contribution stages or skip final candidate confirmation.

## Decisions to settle before implementation

Each row is **DETAILED WORKFLOW TO BE DECIDED**; links point to the shared role-guide decision IDs.

| Decision | Recommended starting point | Alternative or choice still needed |
| --- | --- | --- |
| [OR-01](ONTOLOGY_REVIEW_EDITING.md#or-01) Ownership | Separate author, contribution owner and assigned integrator. | Appointment, substitution and permitted combined responsibilities. |
| [OR-02](ONTOLOGY_REVIEW_EDITING.md#or-02) Review policy | Assigned expertise plus open PUBLIC contributions; Hub query is case-scoped. | Quorum, conflicts, profile disclosure and assignment acceptance. |
| [OR-03](ONTOLOGY_REVIEW_EDITING.md#or-03) Domains/imports | Add agriculture as a candidate; retain land for land-use processes, relationships, conflicts and degradation. | Review the supplied source-grounded boundary/dispositions; settle names, parents and authoritative imports. |
| [OR-04](ONTOLOGY_REVIEW_EDITING.md#or-04) Exact package | Atomic immutable snapshots and a new typed cross-proposal envelope. | Saved-source first versus concurrent delivery of IDE buffer capture. |
| [OR-05](ONTOLOGY_REVIEW_EDITING.md#or-05) Validation | Isolated checks and explicit BLOCKED status; human judgment remains distinct. | Import authority, reasoner isolation and action/source correspondence. |
| [OR-06](ONTOLOGY_REVIEW_EDITING.md#or-06) Contributions | Stage-local append; integrated proposal with review-again or advance exits. | Append store versus participant-owned flows; one-more versus supersede; provisional integration while open. |
| [OR-07](ONTOLOGY_REVIEW_EDITING.md#or-07) PUBLIC | Hub-authorized participation without invitations or special reviewer grants. | Eligibility assertion, public packet visibility, moderation and abuse limits. |
| [OR-08](ONTOLOGY_REVIEW_EDITING.md#or-08) Dissent | Explicit dispositions, unresolved alternatives and appeal provenance. | Adjudicator and whether acknowledgment/appeal blocks return. |
| [OR-09](ONTOLOGY_REVIEW_EDITING.md#or-09) Decisions | Whole-candidate human acceptance under exact gates. | Decision authority, rejection scope and partial approval policy. |
| [OR-10](ONTOLOGY_REVIEW_EDITING.md#or-10) Application | Separate authorized CAS/effect-receipt/Git handoff. | Destination, approval, publication and rollback authority. |
| [OR-11](ONTOLOGY_REVIEW_EDITING.md#or-11) Closure | Sealed round snapshots; late receipt; linked new round for reopening. | Cutoff, withdrawal, retention and access after closure. |
| [OR-12](ONTOLOGY_REVIEW_EDITING.md#or-12) Reliability | Durable receipts/outbox, multi-process CAS and pinned behavior source. | Offline owner identity, operation budgets and live acceptance tests. |

Do not move the existing land proposal into an agriculture ontology as a documentation side effect.
Treat agriculture as a candidate domain under the [source-grounded boundary proposal](ONTOLOGY_REVIEW_LAND_AGRICULTURE.md).
Land remains useful for land use and associated relations/conflicts/degradation; cross-domain imports
and shared concept ownership need evidence and an explicit decision. No `imod` ontology edit is part of this work.

## Suggested delivery sequence and verification

**DETAILED WORKFLOW TO BE DECIDED — implementation sequencing (OR-12).** Recommend:

1. Agree the contribution/integration model, PUBLIC eligibility and Hub disclosure contracts.
2. Add immutable/base-aware snapshot and operation receipt primitives; expose a persisted draft path.
3. Wire bounded evidence/snapshot/preparation actions; add deterministic proposal extraction and
   preserve manual review. Demonstrate saved-document and unsaved-buffer differences explicitly.
4. Implement owner-specific contributions, atomic round closure and integrated proposal with both
   review-again and advance exits; retain optional author-edit delegation.
5. Add Hub discovery and public self-service participation, durable effects and scale controls.
6. Complete isolated semantic checks before enabling acceptance; implement application/Git/release
   separately under its own authorization.

For each increment verify both success and forbidden cases: public join without special grants,
known-person users with other roles, revoked/expired eligibility, inaccessible evidence, cross-owner
edits, stale flow/contribution/document revisions, submission racing closure, reviewer supersession,
missing dispositions, dissent, late input, behavior changes and owner absence. Test interrupted
requests before/after persistence and external effects; identical retries must have stable receipts,
different-input reuse must fail. Test the actual IDE capture/confirmation and authenticated HTTP
path, not only in-memory DTOs. A successful schema test or build is not scientific or operational
acceptance.

## Implementation references

These links name exact files in the verified source trees. Local services links refer to this
repository baseline; IDE links are pinned. Existing reference prose remains useful, but source
controls where older prose disagrees.

- [Workflow bridge][bridge], [manager][manager], [action DTO][action-dto] and [REST controller][controller].
- [Workflow schema][workflow-types], [participant][participant], [role enum][roles] and [access rules][access].
- [Core document/project actors][actors], [actor reference][actor-reference] and [additional material API][material].
- [Bundled ontology workflow][workflow], [proposal protocol][protocol] and [proposal types][proposal-types].
- [IDE shell][ide-shell], [proposal stage][ide-stage] and [scalar parameter tests][ide-tests].
- [Bridge tests][bridge-tests], [attachment tests][attachment-tests], [authorization tests][authorization-tests].
- [Workflow contracts][workflows] and [broader bridge decisions][extension].

[workflows]: WORKFLOWS.md#kactors-instrumentation
[extension]: WORKFLOW_EXTENSION.md
[actor-reference]: AGENTS_REFERENCE.md#workflow-target-binding-and-restoration
[material]: RESOURCES.md#additional-project-material
[workflow-types]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/Workflow.java
[action-dto]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/WorkflowBehavior.java
[participant]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/WorkflowParticipant.java
[roles]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/WorkflowRole.java
[access]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/impl/WorkflowImpl.java
[bridge]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowBehaviorBridge.java
[manager]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowManager.java
[attachment-sources]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowAttachmentSources.java
[controller]: ../klab.services.resources.server/src/main/java/org/integratedmodelling/resources/server/controllers/WorkflowController.java
[actors]: ../klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/libraries/CoreActorLibrary.java
[workflow]: ../klab.services.resources/src/main/resources/workflows/ontology-expert-review.yaml
[protocol]: ../klab.services.resources/src/main/java/org/integratedmodelling/klab/services/resources/workflow/ProposalReviewProtocol.java
[proposal-types]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/resources/workflow/ProposalReview.java
[bridge-tests]: ../klab.services.resources/src/test/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowBehaviorBridgeTest.java
[attachment-tests]: ../klab.services.resources/src/test/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowAttachmentSourcesTest.java
[authorization-tests]: ../klab.services.resources/src/test/java/org/integratedmodelling/klab/services/resources/workflow/WorkflowManagerAuthorizationTest.java
[ide-shell]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/WorkflowEditor.java
[ide-stage]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/main/java/org/integratedmodelling/klab/ide/components/ProposalStageEditor.java
[ide-tests]: https://github.com/integratedmodelling/klab-ide/blob/70c4bd9ac0b42004511b8fd855483b31fb720467/src/test/java/org/integratedmodelling/klab/ide/components/WorkflowBehaviorInputTest.java
