# Review cycle pilot

The review extension is independent of the existing proposal-decision stage machine. It does not change shared-stage ownership or grant reviewer roles. `WorkflowParticipant.from` no longer fabricates a reviewer role for a known-person identity with no configured role.

## Server interface

`GET {FLOW}/review-cycles` returns a `ReviewCycles.View` containing the current aggregate revision and caller-filtered cycles. `POST` at the same URL takes a `ReviewCycles.Command`. Both endpoints require an authenticated user and return `Cache-Control: private, no-store`.

Commands are `OPEN_CYCLE` (sourceStateId), `OPEN_ROUND` (cycleId, audience), `SAVE_CONTRIBUTION` (cycleId, roundId, optional contributionId, text, submit), `CLOSE_ROUND` (cycleId, roundId), and `CLOSE_CYCLE` (cycleId). Every command requires the current expectedRevision. Omit contributionId to create another independently owned contribution. Save a draft with submit=false and submit that same ID with submit=true. Submitted text cannot be rewritten; corrections are new contributions.

Authenticated workflow editors (or administrators) may open and close cycles and rounds. Reviewer rounds require an actual REVIEWER role and permission for this workflow. Community rounds require only an authenticated, nonanonymous identity with a nonblank username. They do not require an email address, known-person flag, workflow group, or invented reviewer role. The narrow review endpoint does not grant access to unrelated stages or their attachments.

Editors see all rounds and submitted contributions. Participants see submitted feedback only in rounds whose audience admits them. Every draft remains private to its author, including on public flows and when an editor reads the aggregate. The ordinary flow projection applies the same filter before its public-flow shortcut. Closure preserves the same audience restrictions; it does not make private feedback public.

## Revisions, closure, concurrency

Opening a cycle copies the server-owned candidate from a proposal-review state. It records the source state ID, source aggregate revision, proposal ID and revision, exact proposal and ontology attachment identities/checksums, action IDs, context digest, actor, and time. Review timestamps use millisecond precision so the existing aggregate JSON mapper preserves them exactly. Candidate revision and aggregate revision are different coordinates. Each round and contribution belongs to that frozen candidate through its cycle. Opening a round never advances or closes another round. Reviewer and community rounds can overlap or be repeated in either order.

Closing a round freezes it; closing a cycle also closes its remaining open rounds. Closed records cannot be reopened, edited, or deleted through this extension. A new cycle is a new record, including after the underlying legacy flow is closed. Extension closure does not accept, reject, apply, publish, or reopen the underlying workflow. Historical proposal workflow records remain protected by the existing direct-delete restrictions.

All review mutations share WorkflowManager's synchronized lock with its other aggregate mutations. Each command checks expectedRevision, prepares a detached copy, and persists the resulting aggregate once; failed store writes do not alter the previously stored object. Two clients writing the same revision have one winner within this manager/process. The loser must refresh and explicitly retry. There is no cross-process compare-and-swap: this pilot requires one writing manager per store. Storage bounds limit cycles, rounds, contribution counts, individual text size, and total feedback characters.

## Scope and integration

This increment provides text feedback, provenance snapshots, authorization, persistence, and HTTP transport. Integrated candidate creation continues to use the existing proposal upload, request-changes, and resubmit protocol. A newly integrated candidate needs a new cycle. New-candidate editing after an accepted/closed legacy flow still needs a separate product lifecycle decision; opening feedback cycles does not bypass the existing acceptance lifecycle.

No review UI, contribution proposal/diff attachments, notification outbox/delivery, project additional-material archive, or automatic project Git commits are implemented here. Submitted contribution IDs and timestamps are persisted with the flow but are not a delivered editor notice. Those integrations must preserve draft privacy and the candidate binding. The Java remote ResourcesClient still inherits unsupported defaults for the new methods; the server HTTP endpoints are the pilot transport.

Tests use in-memory stores and proxy user scopes, including simultaneous writers in one manager. They are not HTTP authentication tests, real multi-principal browser tests, or evidence of deployed functionality.

Run the focused reactor checks with a writable isolated test home:

```powershell
mvn -o -pl klab.services.resources.server -am test '-Dtest=ReviewCycleProtocolTest,ProposalReviewProtocolTest,WorkflowSchemaTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Duser.home=<isolated-test-home>'
```
