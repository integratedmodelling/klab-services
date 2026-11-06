# Ontology community review: implementation plan

Status: proposed delivery sequence, 6 November 2026. Baseline: Services develop
`212548f761ada2b5eecaac013191be0d5ce4152d`. The early review example may be replaced.
No publication, deployment, service restart or email delivery is part of this work.

## Intended workflow

The editor prepares an initial draft, then opens reviewer review, community review, or
both when needed. Each opening creates a round bound to one immutable proposal revision
and source/import snapshot. Reviewers contribute independently; community contributors
need an authenticated, nonanonymous identity. Only assigned workflow reviewers enter
reviewer rounds. Contributors never acquire editorial rights through participation.

The editor sees every contribution, records its disposition, and integrates a new
proposal revision. A new revision does not silently change a round already under review.
The editor can open another round of either kind, close intake on any round, and close
the review cycle with a reason. A closed cycle and its contributions remain immutable
and consultable for provenance; a later cycle can be opened against the ontology at any
time. Closing is separate from scientific acceptance, applying ontology edits, and
publishing an ontology. No quorum, vote or automatic acceptance rule.

## Findings in the existing implementation

- `ontology-expert-review.yaml` has editing, peer-review, community-review and terminal
  states, but the transition graph and authorization do not implement this contract.
- `WorkflowBehaviorBridge` already exposes named phase actions and transactional
  attachment operations; `WorkflowManager.executeAction` checks stage ownership and
  an expected aggregate revision. Preserve those guarantees for bootstrap actions.
- `Flow` supports multiple current states. Review-round/contribution identity and an
  integrated proposal lineage must be explicit rather than inferred from stage titles.
- `ProposalReviewProtocol` binds exact candidate attachments/checksums and prevents
  replacement under review. Reuse this boundary; do not equate a contribution with a
  transition or allow reviewers to mutate the editor's candidate.
- `WorkflowParticipant.from` currently synthesizes REVIEWER for a known-real-person
  flag. `PUBLIC` admission still uses role/group policy. Neither implements “any
  authenticated user” safely; introduce explicit participation policies instead.
- `requireStageEditor` protects ordinary stage mutations. Add a separate contribution
  operation; do not relax this check to make community comments work.
- `ResourcesWorkflows.vue` displays workflow definitions/flowcharts, not a review stage.
  Reuse the Resources UI authentication shell, after testing deep-link return behavior.
- Existing comments/proposal formats can carry feedback, but server-owned author,
  round/revision, timestamps and durable submission identity are required.

## Delivery order and acceptance gates

### A. Bootstrap preparation (today, first slice)

1. Extract direct parsed `@review` annotations from ontology declarations, including
   nested declarations, and retain every authored parameter. Combine with an optional
   BOOTSTRAP.md snapshot. Record document URN, source checksum, statement offset/length,
   and bootstrap checksum/locator. Treat referenced review-record paths as text; do not
   fetch files or URLs from annotations.
2. Generate a draft proposal using the existing schema, an empty comments document and
   an input manifest. Keep semantic/technical approval unrecorded. Preserve source text
   verbatim as supporting material; do not invent ontology edits from metadata.
3. Expose “Prepare review draft” as a configured phase action implemented in k.Actors,
   backed by a bounded Java preparation helper. Resolve the target and project text
   through the owning Resources service, under existing permissions. Obtain BOOTSTRAP.md
   using project-relative access; no arbitrary host paths. Preview/download/edit before
   submission. A rerun creates a new draft or explicitly reports an existing one;
   never replaces edited attachments implicitly.
4. Generate all attachments and metadata in the existing action transaction. Require
   expectedRevision, ensure rollback on validation/blob/store failure, and enforce
   byte/count budgets before publication. Source/import freshness is checked at submit.

Acceptance: no BOOTSTRAP/no annotations, either alone and both together; multiple and
nested annotations; UTF-8 and multiline text; preserved provenance; hostile links are
not fetched; oversize input; stale revision; duplicate request; noneditor denial; partial
attachment failure leaves no visible draft; existing draft is preserved. Generated
proposal passes the bundled schema but records no scientific/semantic pass.

### B. Repeatable rounds and contribution API (today after A)

Add round id/kind, candidate reference, intake status, and integrated revision lineage.
Use existing concurrent current stages for independent reviewer/community rounds.
Provide editor commands to open either round from any active editorial point, close
intake, integrate a revision and close the workflow. Reuse round definitions; each
opening creates a new instance, never resets old contributions. Make action availability
server-derived. Existing sample workflow versions remain pinned for existing flows;
provide an explicit replacement version for new flows, no silent mutation/migration.

Add immutable submitted contributions (comments and/or alternate proposal), each bound
to round, base revision and authenticated author. Draft contribution edits belong to
their author only; editor dispositions are separate append-only records. No attachment
upload may claim another author's identity. Editor can inspect all rounds/contributions.
Use request ids for duplicate submission and expected revisions for editor commands;
avoid requiring unrelated contributors to overwrite the same comments attachment.

Acceptance: two concurrent reviewers, two ordinary authenticated community users,
nonreviewer blocked from reviewer round, anonymous blocked from community, round closed
during submit, late feedback stays attached to old candidate, independent contribution
submissions do not lose updates, editor integration opens round two, closing all intake
blocks further writes, denied reads/downloads do not leak restricted round data.

### C. Resources stage access and feedback UI (tomorrow, after B API contract)

Add a Resources review route carrying stable flow/stage ids, e.g.
`/ui?view=review&flow=<id>&stage=<id>` (route spelling to follow shell conventions).
Preserve the local destination through authentication and reject external return URLs.
Render a candidate-centered, face-to-face view: editable current candidate alongside
attributed submitted feedback/alternate proposals, with contributor and proposal filters.
Reuse existing proposal/comment components where portable. Editing is role-dependent:
editor integration edits create a new candidate draft/revision; contributor edits belong
to their own contribution draft. Submitted originals remain intact. Do not assume an
automatic merge, inline patch or voting interaction. Show the active filters, reviewed
base revision and visible contribution counts so context is not lost. Provide editor
contribution inbox, dispositions and integration controls. Display expired/closed rounds and permission
failures clearly; history stays bound to the reviewed revision.

Acceptance: fresh-browser URL -> login -> same stage; authenticated ordinary user can
contribute to community round; reviewer sees assigned restricted round; forged ids,
attachment URLs, cross-flow references and editor commands denied server-side; escaped
untrusted content, upload bounds, CSRF/session behavior; keyboard-accessible submit and
action errors. Test actual Resources server auth and return URL, not just client routing.

### D. Durable notices and email (tomorrow, following persisted B events)

Record editor notices when contributions are submitted and participant notices when a
round opens/closes, a revised proposal is offered, or the workflow closes. Write durable
notification records in the aggregate mutation (or equivalent transactional outbox).
Deliver asynchronously after persistence; retry with bounded backoff, deduplicate by
event and recipient, retain delivery status and expose failed delivery to the editor.
SMTP failure must not roll back a successful review submission. Existing blocking mail
helpers may be reused by the delivery worker, never as the transaction boundary.

Resolve recipients from workflow membership, contributor identities and explicit
subscriptions; “community” does not mean mail every account. Link to the authenticated
stage without embedding tokens or protected proposal content. Recheck access at read
time. Use a disabled/test sink until delivery configuration and recipients are verified.

Acceptance: event persisted once on success and none on rollback; retry after restart;
duplicate event produces one notice per recipient; SMTP failure is visible and retryable;
missing email preserves in-app notice; role revoked before viewing restricts access;
no real mail in tests. Exactly-once SMTP delivery cannot be guaranteed after an ambiguous
SMTP success: prefer stable Message-ID plus documented at-least-once retry behavior.

### E. Project contribution history and automatic Git commits (pilot requirement)

Write cycle provenance into a dedicated section of the hosting project's additional
material, using the project's established layout (confirm the actual API path before
wiring it). Store immutable candidate revisions, attributed submissions, integration
dispositions, source/import hashes, event ids and cycle closure manifests. Index the
material from a short human-readable history; never rewrite closed-cycle records.

Use existing k.Actors project instrumentation where it already mediates authorized
project writes and commits. The workflow queues an auditable, idempotent history export
and commit after the review event is durably recorded. A repository worker locks that
project, checks its expected base, writes only the reserved review paths, and commits
only those paths with event/cycle/actor attribution. Preserve unrelated staged/unstaged
changes. Do not run broad `git add` or commit a user's working tree. Where existing
instrumentation cannot isolate the index, use a service-owned isolated repository/index.

Workflow persistence and Git are separate transactions: keep a durable pending receipt,
retry a failed commit, recover an uncertain commit by event id/tree/hash before retry,
and record the resulting commit id. Show pending/failed archival in the UI; never claim
Git provenance exists merely because feedback was accepted. No remote push is implied.
Enforce project WRITE/commit permissions; a community identity cannot supply arbitrary
paths, commit identity or commands. Export restricted-round content only where project
read policy is equally restrictive; otherwise use an access-equivalent archive and an
opaque project reference until policy is resolved.

Acceptance: automatic commit in a disposable fixture repository; unrelated staged and
unstaged work untouched; duplicate/restarted export; concurrent events; base mismatch;
commit-hook failure; ambiguous successful commit recovery; immutable closed history;
path traversal denied; community member cannot read restricted peer exports. Do not
test the capability by committing into existing user projects.

## Pilot stress testing

Use several isolated contributor clients or agents against disposable seeded identities:
two reviewers, two ordinary authenticated community contributors and an editor. Exercise
parallel submissions, filters, stage-visible feedback, simultaneous rounds, new candidate
revisions and close/submit races. Agents simulate people, not authentication proofs:
retain separate server-side tests with distinct authenticated principals and rejected
anonymous/nonmember requests. No real account creation or participant invitations.

## Scope and decisions that need not block implementation

- Submitted feedback is attributed and visible to participants authorized for that
  iterative stage; editors can see all contributions. Private drafts are not shared.
  Opening community review does not expose restricted reviewer-stage feedback. Test
  contributor/proposal filters as presentation controls, never authorization boundaries.
- Independent simultaneous rounds are supported; the editor can instead start with peer
  review, progress to community review and return to peer review as needed.
- Keep roles scoped to workflow membership. Global service ADMIN retains existing
  operational access. Do not infer reviewer assignment from email, group presence or
  authentication alone. Explicit disallow policies keep precedence.
- Source artifacts may remain optional in the old example, despite the current UI
  experience; validate the new action and submission together instead of assuming the
  YAML required flags alone describe actual bootstrap requirements.
- A usable end-to-end gate is: generate/edit draft -> open reviewer round -> two
  contributions -> integrated revision -> open community URL -> ordinary user feedback
  -> next round -> editor closes -> durable notices. Broader publication stays separate.
- The two-day sequence is a target, not a guarantee: prioritize A+B, then one functional
  stage route, then durable email. A polished inbox and optional subscription controls
  follow only after authorization and persistence tests pass.

## First implementation status

See the companion status note for exact delivered code and test results. This plan
does not claim the bootstrap action, community UI or mail worker is already operational.
