# Review cycle pilot

The pilot combines editor bootstrap with independent review cycles. It does not grant reviewer roles or equate closing a review with accepting or publishing an ontology. `WorkflowParticipant.from` no longer fabricates a reviewer role for a known-person identity with no configured role.

Open an `ontology-review-pilot@0.2` flow from the editor, then enter `<Resources-base>/ui/review?flow=<id>&stage=<id>`. Prepare and edit the candidate, start a cycle from the selected saved proposal, and open reviewer and/or community rounds. The candidate remains editable as a new draft alongside feedback; proposal/cycle, iteration and contributor filters keep the review in context. Community participants use the same authenticated URL and see only the authorized round view. Closed cycles remain consultable and immutable; a later integrated draft can start another cycle.

## Server interface

`GET {FLOW}/review-cycles` returns the current revision, caller-filtered cycles, caller identity and server-derived management/contribution permissions. `POST` takes a `ReviewCycles.Command`. Both require an authenticated principal and return `Cache-Control: private, no-store`. `GET {FLOW}/review-cycles/{cycleId}/attachments/{attachmentId}` returns only the exact proposal or ontology artifact of a visible cycle, as plain Base64 text. It does not grant unrelated stage/material access.

Commands are `OPEN_CYCLE` (sourceStateId, optional proposalAttachmentId and ontologyAttachmentId), `OPEN_ROUND` (cycleId, audience), `SAVE_CONTRIBUTION` (cycleId, roundId, optional contributionId, text, submit, optional proposal), `CLOSE_ROUND` (cycleId, roundId), and `CLOSE_CYCLE` (cycleId). Every command requires the current expectedRevision. Omit contributionId to create independently owned feedback. Save a draft with submit=false and submit that same ID with submit=true. Submitted text and proposals cannot be rewritten; corrections are new contributions. A conflict keeps the browser's unsaved feedback for an explicit refresh/retry.

An alternative proposal supplies `proposal: { document: "<JSON or YAML>", ontologySource: "<optional source>" }`. Omit ontologySource to retain the round candidate's ontology. The service records exact content, checksums and derived proposal identity. Incomplete documents can be saved privately; submission requires the bundled document schema and a fresh revision ID for changed inputs. This check does not imply semantic or scientific approval. Alternatives remain bound to their round's candidate and do not replace the editor's draft. The UI supports document/source editing, resuming private drafts and filtering alternatives by contributor and proposal.

Cycle management requires an authenticated workflow-permitted EDITOR who owns this flow or is an assigned/owning stage editor, or an administrator. A workflow-wide editor role alone does not grant control over someone else's flow. Reviewer rounds require an actual REVIEWER role and workflow permission. Community rounds require an authenticated, nonanonymous identity with a nonblank username; no email, known-person flag or invented reviewer role is needed.

Editors see all rounds and submitted contributions. Participants see submitted feedback only in rounds whose audience admits them. Every draft remains private to its author, including on public flows and when an editor reads the aggregate. The ordinary flow projection applies the same filter before its public-flow shortcut. Closure preserves the same audience restrictions; it does not make private feedback public.

## Revisions, closure, concurrency

Opening a cycle freezes either the legacy server-owned candidate or explicitly selected attachments from a current editing stage. The server checks artifact membership, media, checksums and the bundled proposal schema, then derives candidate identity itself. The cycle records source stage/revision, candidate proposal/revision, exact artifacts, action IDs, context digest, actor and time. Referenced artifacts, source stages and cycle history cannot be deleted. Candidate revision and aggregate revision are different coordinates. Reviewer/community rounds can overlap or repeat in either order; opening one does not close another.

Closing a round freezes it; closing a cycle also closes its remaining open rounds. Closed records cannot be reopened, edited, or deleted through this extension. A new cycle is a new record, including after the underlying legacy flow is closed. Extension closure does not accept, reject, apply, publish, or reopen the underlying workflow. Historical proposal workflow records remain protected by the existing direct-delete restrictions.

All existing-flow mutations use a database-atomic revision predicate and write. Independent managers writing the same observed revision have one winner; the loser refreshes and explicitly retries. Attachment cleanup retains committed payloads when a write outcome is uncertain. Nitrite/RocksDB exclusively locks the database directory across processes: a second service process cannot own it until the first closes. This is the existing local database model, not replicated multi-writer storage. Bounds cover cycles, rounds, contribution counts and UTF-8 proposal/source/feedback payload bytes.

## Scope and integration

The new pilot supports bootstrap -> saved draft -> frozen cycle -> overlapping rounds -> attributed feedback -> integrated draft -> next cycle, without passing through legacy acceptance. Existing accepted legacy flows remain closed; consulting or opening another cycle over their exact candidate does not reopen them. Java remote client methods are implemented for cycle reads, commands and exact artifact access.

The candidate pane is a JSON draft editor with text feedback and alternative JSON/YAML proposal/source contributions. Richer comment forms and semantic diffs remain pending. Closing a cycle records a private archive intent atomically and attempts a minimal local Git digest in the ontology's actual hosting project using the closing editor's current write authority. The digest contains a persistent permission-checked review link, with no contribution text, drafts, identities or proposal metadata. A dirty/locked/read-only/unavailable project defers archiving without undoing closure. Startup replay retains pending jobs; an editor can inspect the receipt and explicitly retry from the closed-cycle UI. No cached scope is retained for background writes and no remote push occurs. See `klab.core.services/REVIEW_ARCHIVE_PILOT.md`.

Review submissions and round/cycle changes now persist service-private notification intents in
the same conditional aggregate write. Draft saves do not notify. A service-owned worker replays
committed intents into the private outbox at startup and every minute, stopping before resource
stores close. Service administrators can consult bounded health/status at
`GET api/v1/admin/review-notifications?limit=50`. Delivery remains disabled; the worker has no
transport or email-enable switch. A recipient adapter requires fresh authoritative identity,
verified mailbox, explicit consent and current round authorization, but no live directory is bound.
Persisted intents are not delivered editor notices. Recipient metadata is excluded from user views
and Git history. See `notes/ontology-review-worker-status.md` for verification and deployment limits.

Checks cover independent managers, real database conditional writes and separate-process ownership, actual Nitrite/RocksDB close/reopen, scoped EngineAuthorization objects at the HTTP controller boundary, disposable Git repositories and browser fixtures. An isolated four-principal browser scenario also exercises the real review component, k.Actors bootstrap, native workflow storage, integrated proposal, closure/restart and fake notices. It uses explicit simulated authenticated scopes, not live login. See [audit](notes/ontology-review-audit-status.md), [digest status](notes/ontology-review-digest-status.md) and [pilot handoff](notes/ontology-review-pilot-handoff.md) for exact coverage and remaining gates.

Run the focused reactor checks with a writable isolated test home:

```powershell
mvn -o -pl klab.services.resources.server -am test '-Dtest=ReviewCycleProtocolTest,ProposalReviewProtocolTest,WorkflowSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=<isolated-test-home>'
```
