# Explicit Runtime activity-hint publisher routing

This opt-in prerequisite adds a **separate sender-only connection** for root-context
`DigitalTwin/ActivityStarted` and `ActivityFinished` hints. It does not replace the
Runtime's existing user/session/context federation messaging, widen its broker
permissions, add an HTTP endpoint, or change a running deployment.

## Current bounded acceptance

After merged PR122, executable candidate `ca9297335d28cfdf76132ecd8f049ac2f3d4adca`
passes181 integrated tests, enforced100% publisher line/branch coverage and13-module
verification/packaging. All2,612 compiled classes match packaged JARs and seven
launch classpaths use current declared Maven closures, not historical mixtures.

Fresh bounded synthetic-v3 run18 passes two explicit controlled Job results and
all20 cells independently read back as123.25 metres. Actual configured Runtime /
ServiceContextScope hooks deliver12 persisted hints with six matched start/finish
IDs and a successful finish. No Activity or publisher channel is injected. The
wrong-context reader is denied and the second context receives no hints.

Receiver/queue/context/session/list/publisher-socket/HTTP cleanup all pass. All14
owned JVM identities are independently absent;3,898 launch hashes,1,154 source hashes
and all fixture launch hashes remain unchanged. No matching owned visible windows
remain; unrelated services/windows are untouched. The fsynced hint journal matches
the aggregate JSON. Two confirmed submissions override the inherited zero summary
counter. Continuity and upstream overflow remain UNKNOWN.

This completes bounded lifecycle/numerical/automatic-hook acceptance of the new
explicit-bearer synthetic fixture, not the original bare query or study reproduction.
Original/v2 files and all failed evidence remain retained. Final documentation-head
CI/review and merge are still required; this is not deployment or publication approval.
Result SHA-256: `2642ce271831b39f540eb081584f4515bd9a97ed575d53cb2ecc0aaf68de7956`.
Hint-journal SHA-256: `cba2eeb9755be57fbdf8c115acc537f157ef3b6770a864af1cce96fbc9598ce7`.

## Historical development passes (preserved failures and limits)

Implementation and acceptance are separate. In the original executable pass, source compilation and43 focused
regressions pass; the new publisher helper and transport worker have100% unexcluded
line/branch coverage. Enforced coverage-profile packaging passes. The selected
broader run has88 passes/six errors versus45 passes/the same six errors on clean
integration base; those baseline failures are retained, not waived or repaired.
A real Qpid/Python transport preflight passes immutable snapshots, wrong-owner
refusal, description omission and cleanup, but uses hand-created Activity inputs.
The13-module source-matched service/graph build passes;3898 launch files are
attested and2612 compiled classes match packaged JARs. Actual broker ingress
rejection of a body over1MiB (reply311) before Python admission and wrong-password
handling also pass. The original full attempt3 stopped because the unchanged
frozen worldview failed declaration-clause/relationship-axis and inherency
validation before Reasoner became operational. Six owned component imports were
confirmed; that attempt submitted no contexts or scientific Jobs. The186 Reasoner
classes matched clean integration base exactly;
this is not a claim that a complete baseline live semantic run was performed.
Two earlier harness failures are preserved. All33 recorded full-fixture/diagnostic/
wire-probe JVM identities were independently absent at that milestone, and its
launch inputs were unchanged. Later authorized synthetic-fixture v2 passed
readiness and reached Job submission, but failed genuine numerical/activity and
publisher socket acceptance; see the canonical-release follow-up below. Historical
launch manifests are not current build attestations. See
[sanitized evidence and hashes](activity-notification-routing-results-20261102.json).
**Do not treat this guide as a
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
## Canonical context release follow-up

The live attempt5 passed service/semantic readiness and obtained two context and
two observation Job receipts, but Resolver logged region `NO_MODEL`/elevation
`FAILED`. Numerical readback and real activity delivery were not verified. Context
and session release returned true and context listing was empty, yet publisher
socket absence failed. A final cleanup assertion masked the earlier exception.
Those receipts and failed evidence remain retained; no automatic mutation replay.

The controlled service-method regression now reproduces the publisher ownership
mistake: request-derived scopes share the sink but do not own its close. Runtime
context release must look up the registered root, apply the existing context ACL,
and close that root rather than the request copy. Missing registration, foreign
scope types and denied ACLs fail closed. An explicit allowed-user ACL remains
admitted. The root closes its sink even if twin disposal throws. These are direct
service-method tests, not a claim of HTTP authorization bypass.

Ten new cases pass, with every line/branch of `releaseContext` covered; the existing
43 routing cases and five merged119 transport cases also pass reactor verify.
The initial test fixture's missing mock service ID failed before reaching the
product; its log and the corrected fixture's four pre-fix failures are preserved.
Five separate offline harness cases verify primary/cleanup exception preservation
without exposing private messages or performing any scientific operations.

This closes a software regression, **not live acceptance**. A source-matched build
and fresh launch manifest, genuine successful Jobs/unchanged oracle readback,
activity delivery, broker isolation, and independent live queue/socket cleanup
are still required before merging117/118/108. The merged119 flush-ownership fix is
not proof of the primary cause of the earlier numerical Job failure. The original
failed sources, frozen original fixture and numerical oracle stay unchanged.

### Attempt6 and the HTTP-path gap

The fresh source-matched attempt6 retained the primary failure correctly:
`JobFailedError` decoding the elevation result's error notification. Region result
retrieval/grid checking succeeded, but numerical readback and actual activity
delivery were not verified. Wrong-context reader denial, receiver closure,
independent receiver queue absence, both context releases, session release and
empty context listing passed; publisher socket absence still failed. All14
recorded JVM identities are independently absent, with no corresponding visible
windows and no mutation replay.

The existing HTTP `closeContext` controller still directly closed its authorized
request scope, bypassing the newly corrected canonical service lifecycle. Three
of six new controller regression cases fail before correction. The route is now
delegated to `RuntimeService.releaseContext`, retaining its
principal/context checks and returning the actual product result. All64 focused
reactor cases pass, including six controller cases; both release methods have no
missed lines or branches. Real **context-only** queue/socket acceptance now passes
in lifecycle7: two receivers and two independently checked queues are gone; both
contexts and the session release successfully; context listing is empty; Runtime
publisher sockets are absent after release. The source-matched13-module build
passes, all3905 launch inputs still match, and all14 recorded JVM identities are
independently absent with no corresponding visible windows. The lifecycle probe
enforced a zero scientific submit budget and submitted no observation Jobs. It
does not establish genuine Activity delivery or numerical execution. Failed elevation Jobs will not
be rerun unchanged, and neither the transport nor service-method fixes are claimed
to resolve the scientific result failure.
