# Events/reconnect: source audit and blocked acceptance gate

**Historical durable-recovery audit.** The user subsequently approved a separate
bounded best-effort activity-notification scope for PR #108; see
[live-notifications.md](live-notifications.md). That implementation does not satisfy
or waive the recovery contracts below. Its controlled live acceptance is pending.

**BLOCKED / NOT IMPLEMENTED.** This is a contract audit for the next sequential
feature after PR #107, not an event SDK, scientific acceptance result or completed
roadmap item. No new public placeholder method, transport or production Java
change is introduced. Keep its PR draft; do not merge it as the requested feature.

Audited source base: `bb4f62ce01bb41d93f506ee34c19687b401d8640`, the normal merge
of PR #107. Conclusions concern the inspected general context notification path,
not every specialized temporal/flow/application protocol or an uninspected remote
deployment. No live broker credentials, subscription or recovery experiment was
used to establish these source findings.

## Observed contracts, not inferred guarantees

| Source | Observed behavior | Consequence for the Python feature |
| --- | --- | --- |
| `klab.core.common/.../authentication/scope/AMQPChannel.java` | `queueDeclare()` creates a receiver queue; `basicConsume(consumerQueue, true, ...)` uses auto acknowledgement. Broker URI comes from the federation; payload processing errors are logged | Live transport exists, but this receiver path is not durable acknowledged client replay. Connecting successfully cannot prove missed updates were recovered |
| `klab.core.services/.../application/controllers/WebsocketsBus.java` | SockJS endpoint `/comms/application`; simple memory `/topic`/`/queue` broker | The existence of STOMP does not establish an authorized context event catalog, retention policy, replay cursor or gap-free resync |
| `klab.services.runtime/.../digitaltwin/DigitalTwinImpl.java` | Commit cache maximum 200 entries, ten minutes since access; `getCommit(id)` is `getIfPresent` | A missing descriptor is not proof of an empty commit or unchanged graph. Numeric commit lookup is not an ordered durable feed |
| `klab.services.runtime.server/.../controllers/RuntimeServerController.java` | Authenticated context `GET /api/v1/commit?id=...` retrieves one descriptor | It does not supply a general enumeration/replay/snapshot-watermark contract |
| `klab.core.common/.../client/digitaltwin/ClientKnowledgeGraph.java` | Ingests observations and retrieved/embedded commits; local applied-commit bookkeeping | A Java client projection is not evidence that an arbitrary disconnected Python client can reconstruct all missed changes |
| `klab.core.api/.../services/runtime/Message.java` | ActivityStarted/ActivityFinished, twin lifecycle and consequence messages with distinct payload contracts | Activity completion is not automatically root publication, and one message type cannot substitute for all state changes |
| `Neo4jQueryCompiler.java` and the accepted Python graph browser | Explicit context visibility and deterministic offset ordering without a shared revision lease | Browsing current pages is not an atomic snapshot with a recoverable event watermark |

Full paths and SHA-256 pins are recorded in the adjacent
`results/events-contract-source-20261102.json`. Existing design discussions in
`docs/DISTRIBUTED_TWINS.md` explicitly distinguish transient auto-ack messaging
from proposed snapshot/watermark/replay contracts. Specialized application UI
reconnection and flow-transition history must not be silently generalized to all
Runtime contexts, observations, Jobs or scientific storage revisions.

## Why the requested reliable recovery feature cannot be accepted yet

A disconnected client needs to distinguish no change from missed change, expired
history, a new Runtime incarnation, revoked access and a broken transport. Current
general-path evidence does not establish all of:

1. Authenticated subscription/discovery bound to an approved broker/service origin,
   principal, Runtime incarnation, context and explicit message types. Existing
   issued HTTP credentials must not be forwarded to an advertised broker by guess.
2. Stable scoped event identity, sequence/order and declared retention. A message
   ID, ActivityFinished or numerical commit ID alone is not a replay cursor.
3. Authorized replay after a cursor, explicit gaps/expiry and bounded redelivery.
   Duplicates must not silently repeat scientific submissions or graph changes.
4. Snapshot revision/watermark plus a documented no-gap handoff to subsequent
   events. Subscribe-before-read and offset graph pages alone do not establish it.
5. Revocation, restart/incarnation and context disposal semantics, cancellation
   acknowledgement and owned subscription cleanup.
6. Enforced receive-byte/queue bounds, backpressure/overflow policy and observable
   recovery status rather than unbounded buffering or hidden data loss.

These are missing acceptance contracts, not a claim that no future server version
can implement them. Replacing them with polling, a fabricated SSE URL or automatic
resubmission would change the requested feature and its scientific safety.

## Concrete ways to unblock (decision required)

### A. Explicitly approve a narrower best-effort notifications feature

First specify the supported event types, safe broker-origin/authentication policy
and deployed principal-scoped fixture. Then implement bounded live consumption
with explicit disconnected/overflow/unknown states and local subscription cleanup.
On reconnect, require the caller to re-read known authorized Jobs/observations;
**do not** advertise complete resync, durable replay, continuous scientific
consistency or reconnect without lost events. Retain this audit as a remaining
roadmap gap. This is a scope decision, not an implemented fallback.

### B. Authorize separately scoped production server contracts

Agree the subscription credentials/origin boundary, replay/retention/gap schema,
atomic snapshot-watermark protocol and lifecycle/revocation behavior. Obtain
maintainer/platform authorization for production changes and a deployed fixture.
Then implement the Python adapter against the actual tested server contracts.
Generic Python-roadmap approval is not permission to redesign those Java/shared
service protocols or to modify a running deployment.

### C. Explicitly defer this feature and authorize a sequencing exception

Record the blocked item rather than mark it complete, and choose the next feature
whose contracts can be verified. The existing one-feature-at-a-time instruction
does not by itself authorize silently bypassing this gate.

## Required tests once a scope is approved

- Correct/incorrect principals, context and Runtime IDs; broker-origin credential
  leakage; revocation and forbidden subscriptions; configured TLS behavior.
- Real socket transport; bounded slow consumer/oversized message/overflow cases;
  malformed and unknown payloads remain errors, not valid empty updates.
- Duplicate/out-of-order/gap/expiry/restart cases appropriate to the agreed policy;
  cursor persistence never contains credentials or implies stronger recovery.
- Dropped connection at snapshot/event boundaries; compare independent state and
  accepted revisions rather than equating callback counts with synchronization.
- No scientific mutation replay after ambiguous outcomes; local cancellation
  distinct from accepted server cancellation; no hidden background loop/thread API.
- Genuine owned service/broker run with cleanup evidence. Loopback/mocked events
  must be labelled software-contract tests, not deployed acceptance.

## Current PR and work state

PRs #105 (bounded native async), #106 (indexed pandas views) and #107 (typed graph
pages) were tested, reviewed with author-review limitations stated, and normally
merged sequentially. Their bounded acceptance does not establish this event
protocol. This audit PR remains draft and incomplete. No later feature is started
without a sequencing decision. No service was launched or modified for this audit;
the previous graph acceptance's scopes/JVMs were cleaned. Computer sleep remains
conditional on finishing the requested work, which has not happened.
