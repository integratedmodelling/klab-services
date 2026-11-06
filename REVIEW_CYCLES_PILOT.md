# Review cycle pilot

The pilot combines editor bootstrap with independent review cycles. It does not grant reviewer roles or equate closing a review with accepting or publishing an ontology. `WorkflowParticipant.from` no longer fabricates a reviewer role for a known-person identity with no configured role.

Open an `ontology-review-pilot@0.2` flow from the editor, then enter `<Resources-base>/ui/review?flow=<id>&stage=<id>`. Prepare and edit the candidate, start a cycle from the selected saved proposal, and open reviewer and/or community rounds. The candidate remains editable as a new draft alongside feedback; proposal/cycle, iteration and contributor filters keep the review in context. Community participants use the same authenticated URL and see only the authorized round view. Closed cycles remain consultable and immutable; a later integrated draft can start another cycle.

## Server interface

`GET {FLOW}/review-cycles` returns the current revision, caller-filtered cycles, caller identity and server-derived management/contribution permissions. `POST` takes a `ReviewCycles.Command`. Both require an authenticated principal and return `Cache-Control: private, no-store`. `GET {FLOW}/review-cycles/{cycleId}/attachments/{attachmentId}` returns only the exact proposal or ontology artifact of a visible cycle, as plain Base64 text. It does not grant unrelated stage/material access.

Commands are `OPEN_CYCLE` (sourceStateId, optional proposalAttachmentId and ontologyAttachmentId), `OPEN_ROUND` (cycleId, audience), `SAVE_CONTRIBUTION` (cycleId, roundId, optional contributionId, text, submit), `CLOSE_ROUND` (cycleId, roundId), and `CLOSE_CYCLE` (cycleId). Every command requires the current expectedRevision. Omit contributionId to create independently owned feedback. Save a draft with submit=false and submit that same ID with submit=true. Submitted text cannot be rewritten; corrections are new contributions. A conflict keeps the browser's unsaved feedback for an explicit refresh/retry.

Cycle management requires an authenticated workflow-permitted EDITOR who owns this flow or is an assigned/owning stage editor, or an administrator. A workflow-wide editor role alone does not grant control over someone else's flow. Reviewer rounds require an actual REVIEWER role and workflow permission. Community rounds require an authenticated, nonanonymous identity with a nonblank username; no email, known-person flag or invented reviewer role is needed.

Editors see all rounds and submitted contributions. Participants see submitted feedback only in rounds whose audience admits them. Every draft remains private to its author, including on public flows and when an editor reads the aggregate. The ordinary flow projection applies the same filter before its public-flow shortcut. Closure preserves the same audience restrictions; it does not make private feedback public.

## Revisions, closure, concurrency

Opening a cycle freezes either the legacy server-owned candidate or explicitly selected attachments from a current editing stage. The server checks artifact membership, media, checksums and the bundled proposal schema, then derives candidate identity itself. The cycle records source stage/revision, candidate proposal/revision, exact artifacts, action IDs, context digest, actor and time. Referenced artifacts, source stages and cycle history cannot be deleted. Candidate revision and aggregate revision are different coordinates. Reviewer/community rounds can overlap or repeat in either order; opening one does not close another.

Closing a round freezes it; closing a cycle also closes its remaining open rounds. Closed records cannot be reopened, edited, or deleted through this extension. A new cycle is a new record, including after the underlying legacy flow is closed. Extension closure does not accept, reject, apply, publish, or reopen the underlying workflow. Historical proposal workflow records remain protected by the existing direct-delete restrictions.

All review mutations share WorkflowManager's synchronized lock with its other aggregate mutations. Each command checks expectedRevision, prepares a detached copy, and persists the resulting aggregate once; failed store writes do not alter the previously stored object. Two clients writing the same revision have one winner within this manager/process. The loser must refresh and explicitly retry. There is no cross-process compare-and-swap: this pilot requires one writing manager per store. Storage bounds limit cycles, rounds, contribution counts, individual text size, and total feedback characters.

## Scope and integration

The new pilot supports bootstrap -> saved draft -> frozen cycle -> overlapping rounds -> attributed feedback -> integrated draft -> next cycle, without passing through legacy acceptance. Existing accepted legacy flows remain closed; consulting or opening another cycle over their exact candidate does not reopen them. Java remote client methods are implemented for cycle reads, commands and exact artifact access.

Feedback currently uses text and the candidate pane is a JSON draft editor. Structured proposal/diff contribution attachments, richer comment forms, notification delivery, project-material history export and automatic Git commits remain pending. Persisted contributions are not a delivered editor notice. Those integrations must preserve stage audience, private drafts and exact candidate binding.

Checks cover in-memory principal/ownership cases and simultaneous writers in one manager, actual Nitrite/RocksDB close/reopen, real EngineAuthorization objects at the HTTP controller boundary, and disposable browser fixtures. They do not establish live JWT/identity-provider login, cross-process concurrency or deployed multi-user behavior. See `notes/ontology-review-combined-status.md` for evidence and remaining gates.

Run the focused reactor checks with a writable isolated test home:

```powershell
mvn -o -pl klab.services.resources.server -am test '-Dtest=ReviewCycleProtocolTest,ProposalReviewProtocolTest,WorkflowSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=<isolated-test-home>'
```
