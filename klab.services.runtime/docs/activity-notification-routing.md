# Explicit Runtime activity-hint publisher routing (under verification)

This opt-in prerequisite adds a **separate sender-only connection** for root-context
`DigitalTwin/ActivityStarted` and `ActivityFinished` hints. It does not replace the
Runtime's existing user/session/context federation messaging, widen its broker
permissions, add an HTTP endpoint, or change a running deployment.

Implementation and acceptance are separate. Source compilation and43 focused
regressions pass; the new publisher helper and transport worker have100% unexcluded
line/branch coverage. Enforced coverage-profile packaging, broader regressions and
actual Job/broker acceptance are in progress. **Do not treat this guide as a
completed deployment or merge approval.** Python notification PR108 remains draft.

## Operator contract

Supply a private UTF-8 JSON profile through the JVM property
`-Dklab.runtime.activityNotifications=C:/private/runtime-hints.json`. With the
property absent, no new connection, thread or publisher is created. Invalid or
wrong-Runtime profiles fail startup without echoing their content. File permissions
are the operator's responsibility; credentials must never be committed, printed,
forwarded from HTTP tokens, or placed directly on a command line.

```json
{
  "runtimeId": "THE-EXACT-RUNTIME-SERVICE-ID",
  "contexts": [
    {
      "context": "approved_session.approved_context",
      "owner": "approved-owner",
      "host": "127.0.0.1",
      "port": 18197,
      "tls": false,
      "username": "publisher-context-a",
      "password": "REPLACE WITH A DISTINCT RANDOM BROKER SECRET"
    }
  ]
}
```

The sample secret contains spaces and intentionally fails validation. Use a unique
random broker publisher secret, distinct from every reader and HTTP credential.
The profile is loaded once at service construction, is limited to64KiB and16
explicit contexts, and is not hot-reloaded. Exact context IDs and their owners must
be approved in advance; the existing authenticated context-creation API supports
an explicitly requested context ID within its authorized session. This profile
does not grant permission to create or access that context. Normal authorization
and ownership checks still apply. Unknown fields, duplicate JSON keys/trailing
content, repeated contexts/accounts/secrets, reserved principals and unsafe names
are rejected. Profile identity must match the Runtime service ID and stored context
owner; a federation ID, context guess or HTTP principal does not substitute.

Each route selects the virtual host named **exactly** like the root context and
publishes only its exact non-durable fanout exchange. There is no default-host
fallback, federation-URI rewriting, reader credential or guest account. Use the
separate [scoped broker profile](../../support/klab.amqp.server/README.md) or an
independently verified equivalent. The publisher never creates/consumes queues and
does not delete shared exchanges on close. Plaintext admits only literal
`127.0.0.1` or `localhost`. Other hosts require TLS using the JVM's default trust
context **and hostname verification**, not a trust-all RabbitMQ default. Trust-store
management and a deployed broker's policy require their own verification.

## Hints, budgets and lifecycle

- A matching hosted root context gets an additional sink; derived execution scopes
  share it but do not own its lifecycle. Root close releases it even if ordinary
  resource cleanup fails. Runtime shutdown closes all its hint publishers.
- Initialization is asynchronous and attempts the configured origin only once.
  A Python passive receiver must wait for the publisher's exchange to exist;
  missing exchange is not permission to create it from the reader. Events produced
  before attachment can be missed, including context-initialization activities.
- No broker I/O runs on the observation's sending thread. Each of at most16 routes
  has one daemon worker and at most128 pending immutable snapshots, each at most8KiB.
  Snapshot fields are restricted to source message identity/timestamp and Activity
  Runtime/URN/ID/transient ID/type/start/end/outcome. Full provenance, descriptions,
  stack traces, dataflows, metadata and arbitrary author content are not exported.
  These hints are not a full Activity export or durable provenance receipt.
- Connection/handshake/RPC setup waits are bounded. A connection/publish error is
  terminal for that route for the lifetime of this Runtime instance. Closing clears
  pending hints and aborts the connection. There is no reconnect, replay, automatic
  resubmission, job cancellation, scientific rollback or delivery confirmation.
- Queue overflow/malformed snapshots drop hints. Broker loss, a local send return,
  or a dropped hint cannot change Job success into an event-delivery guarantee.
  Continuity and upstream loss/overflow remain **UNKNOWN**. Run authoritative HTTP
  Job/graph reads independently; duplicate and out-of-order hints remain possible.

## Required acceptance before merge

1. Strict profile/identity/secret diagnostics and bounded snapshot/queue regressions.
2. Existing parent channel preservation, derived-scope sharing, once-only root
   cleanup, terminal failure/no reconnect and shutdown regression tests.
3. Real isolated broker correct/wrong context and reader/publisher separation.
4. Genuine Runtime-created Job/activity delivered through this configured product
   path, not a test-injected replacement channel or hand-created Activity message.
5. Independent owned queue/connection/process cleanup, final source/package/CI and
   local review, with failures preserved. A local review is not independent approval.

No real scientific reproduction, production model/data mapping, durable recovery,
agent control, package publication or running deployment changes are included.
