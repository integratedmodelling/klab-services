# Bounded live activity notifications

The selected scope is best-effort notifications, not durable recovery. Bounded
actual Runtime/numerical/activity acceptance now passes with the explicit synthetic
v3 fixture and merged generation correction. Final integrated software gates pass;
final documentation-head CI/review remain before merge. This does not replace
failed original-query acceptance.
The original shared-host broker attempt did not satisfy the mandatory context
isolation test. Those failures remain in [historical facts](results/notifications-controlled-20261102.json).
The separate per-context virtual-host profile now merged in PR #116 establishes
that provisioning boundary, not complete Python/Runtime notification acceptance.
Current receiver integration with that profile passes the bounded checks below.

Install `klab-python-client[events]`. Only synchronous root `Context` objects with
a known Runtime identity and Qpid Broker-J AMQP 0-9-1 are admitted initially.
There is no inferred broker origin, HTTP-to-AMQP credential conversion, public
guest default, raw publish/control method or automatic reconnect.

```python
from klab_client import ActivityNotifications, NotificationBroker

# `context` is an already-authorized synchronous root Context. The operator must
# independently provision this broker account and its exact-context exchange ACL.
broker = NotificationBroker(
    "amqps://broker.example/s.a", username=broker_user, password=broker_password,
)
with ActivityNotifications(context, broker, timeout=5, max_events=128) as events:
    event = events.read()  # one immutable activity hint, or None on timeout
    if event is not None:
        print(event.kind, event.message_id)  # IDs are NOT durable replay cursors
```

## Origin, identity and permissions

- Broker credentials are explicit, omitted from repr and never populated from HTTP
  endpoints, discovery replies or tokens. Configured HTTP credential material is
  rejected in broker settings. No HTTP request is made by this receiver.
- Remote connections require the system trusted CA set, certificate validation and
  hostname checking. There is no TLS-disable switch. Plain AMQP requires explicit
  `allow_insecure_loopback=True` and localhost/a literal loopback address.
- The Runtime context's exact exchange must exist. Declaration is **passive**;
  this API never creates, modifies or deletes the shared exchange or remote scope.
  Its type is explicitly `fanout`: Qpid checks type even on passive declaration;
  Pika's default `direct` is incompatible with the actual Java publisher path.
- Require a separately provisioned virtual host whose exact name is the root
  context ID. The example assumes `context.id == "s.a"`. Default/shared `/`
  virtual hosts and wrong-context paths are refused before connection. Use the
  [explicit isolated broker profile](../../support/klab.amqp.server/README.md) or a
  separately verified equivalent; a guessed exchange name is not access control.
- HTTP context access is not broker authorization. The operator must provision ACLs
  that allow reading/binding only approved exchanges, deny publishing to receivers,
  and restrict temporary queue ownership. A client cannot retrofit those policies
  from an HTTP token or prove event authenticity merely from an envelope field.
  Connection-level virtual-host denial is reported as authorization denial, not
  confused with a bad password or an unknown transport failure.
- Only root context subscriptions are supported. Messages with valid focus paths
  under that root can be admitted; this is NOT observation-focus privacy filtering.
  Root context, Runtime binding and broker configuration drift terminate use.
- Require `DigitalTwin`/`Events`, `ActivityStarted` or `ActivityFinished`, the pinned
  ActivityImpl payload class, matching dispatch root and Activity.serviceId. This
  validates wire consistency, not a cryptographic issuer signature. Payloads are
  deeply immutable and redact configured credential material; repr omits payload.

## Bounds and lifecycle

- One exclusive auto-delete non-durable receiver queue, manual acknowledgement and
  prefetch one. Ack means admitted to this local receiver, not scientific commit,
  user processing completion, terminal Job state or root observation publication.
- Qpid queue arguments request `qpid.max_count`, `qpid.max_size` and
  `qpid.policy_type=reject`. Other brokers are unsupported until their actual
  equivalent policy is separately verified. Default queue count128 (maximum128).
- Default accepted message size1MiB (maximum1MiB); default total admitted bytes16MiB
  (maximum16MiB); default total events128 (maximum10000). Exhaustion or rejected
  input stops the receiver. Malformed/unknown envelopes never mean empty state.
- **Message size is checked after Pika assembles the delivery.** This is an
  admission/JSON bound, NOT a hard untrusted-wire/RSS bound. A trusted broker must
  separately enforce an incoming message-size limit. Frame limits alone cannot
  establish a message-size limit. Slow consumers also depend on broker policies.
- There is no hidden thread or reconnect loop. `read()` drives I/O and has an
  inactivity timeout, not a hard overall wall-clock deadline against a malicious
  peer. Use from one caller/thread, poll within the broker heartbeat interval, and
  always close explicitly or use the context manager. `Client.close()` alone does
  not close a separate notification receiver.
- States: NEW, OPEN, LIMIT_REACHED, REJECTED, DISCONNECTED, ACCESS_DENIED,
  INTERRUPTED, CLOSED. Broken/lost transport or revocation terminates consumption.
  A new receiver is explicit and starts a new transient queue, not a resumption.
- Cleanup deletes only its own queue and closes its connection. A failure leaves
  `cleanup_confirmed=False` and raises a sanitized transport error unless doing so
  would mask the primary exception. Connection loss may require broker-side
  exclusive-queue expiry; cleanup must not be relabelled confirmed.

## Always unknown continuity — no recovery claims

`continuity` and `upstream_overflow` are always `UNKNOWN`. Queue rejection,
interruption, expiry, missed events, broadcaster failure and Runtime restart are
not all visible to a receiver. Successful subscription, empty timeout, a low
message ID or an ActivityFinished does not establish synchronization. Duplicate
and out-of-order message IDs are delivered, not silently suppressed. A Java
process-local counter is not a stable event identity across restarts.

After a gap, independently re-read already-authorized known Jobs/observations.
Never resubmit scientific mutations to manufacture missed activity messages.
No replay cursor, graph projection, durable journal, gap-free snapshot handoff,
Job cancellation, agent control, ordinary-user privacy or scientific validity is
claimed. The original [durable recovery audit](events-reconnect-contract.md) stays
an unfinished roadmap gate.

## Historical development and transport acceptance

Historical candidate `772e564ec12d00d6499499f51287fa71818678e2` includes the real
Qpid passive-fanout fix. All 71 focused tests and hosted Python3.11–3.13, core-only,
build and Jenkins checks pass. Initial full software run passed2112 tests, seven
coverage gates, lint/build and an isolated core-only installed-wheel check. The
seven recorded owned broker JVM identities are independently absent; failed
private fixtures remain preserved. Software success does not waive live failures.

PR #116 merged normally at `474d2706498e66df8104f701ae408095da5feaeb`: its38
module cases, compiler100% coverage, source-matched dedicated CI and packaged-main
checks verify two independent hosts, four cross-context denials, reader publish
denial and owned queue cleanup. That profile does not automatically configure a
Runtime publisher URI or change existing local/guest federation defaults. Current
receiver changes require exact-context host selection and add explicit handling
of Pika's connection-level access-denied exception;76 focused mocked cases pass
with100% unexcluded lines/branches. Actual receiver/native Java and Runtime activity,
TLS, overflow and disconnect/cleanup integration were subsequently verified in
owned fixtures: see [current isolated acceptance](results/notifications-isolated-20261102.json).
Native Java `AMQPChannel`/`Message` started/finished hints, duplicates, Python
authorization denial, malformed/oversized rejection, event-budget closure,
independent queue absence and terminal broker loss pass. A separate real TLS socket
fixture verifies trusted delivery, untrusted-chain and hostname rejection, cleanup,
and one-message queue overflow rejection with broker reply506. An initial failed
overflow-signaling fixture is retained. Continuity/overflow remain `UNKNOWN`.

The current candidate also passes2117 offline tests, all seven coverage gates,
lint and wheel/sdist builds. These are not Runtime acceptance. The inspected
Runtime path copies parent messaging setup into contexts and initializes session
messaging from a federation-wide broker URI, rather than selecting a separately
approved per-context publisher origin. A separately scoped Runtime-routing
prerequisite and actual Runtime activity/Job acceptance are still required; do not
widen the broker policy or inject a fixture channel and call that path verified.

Need an owned ACL-controlled actual Qpid broker plus pinned Java publisher/Runtime
evidence, correct/wrong principals and contexts, publish denial, socket disconnect,
slow consumer/queue-limit and admitted oversized/malformed input, local shutdown
and independent queue/process cleanup checks. A Java Message/AMQP publisher fixture
alone is transport compatibility, not a genuine Runtime activity/Job acceptance.
TLS policy has software tests; real TLS verification needs its own controlled
certificate/socket evidence before any deployed TLS acceptance claim.

## Genuine Runtime acceptance and final integration

Fresh bounded run18 uses the actual configured Runtime/ServiceContextScope hooks,
not injected Activities or a fixture channel. Runtime executable
`ca9297335d28cfdf76132ecd8f049ac2f3d4adca` includes merged generation-restoration
PR122;181 integrated tests, enforced100% publisher line/branch coverage,13-module
verification and current declared dependency closures pass. All2,612 compiled
classes match packaged JARs. The Python receiver source is the same implementation
used by this run; integration adds accepted prerequisites, not a new receiver path.

Two explicit controlled Jobs return results; all20 cells independently read back as
123.25 metres. Twelve activity hints persist with six paired start/finish IDs and
a successful finish. The wrong-context reader is denied; the second context receives
no hints. Journal and aggregate JSON agree exactly. No mutation is automatically
replayed. Two submission receipts take precedence over the old outer zero counter.

Receivers, their queues, contexts, session, context listing, publisher sockets and
HTTP client all pass cleanup. All14 recorded JVM identities are independently absent;
3,898 launch hashes,1,154 source hashes and fixture launch hashes remain unchanged.
No matching owned visible windows remain; unrelated services/windows are untouched.
See [sanitized genuine Runtime evidence](results/notifications-runtime-20261103.json).

Continuity/upstream overflow remain UNKNOWN; this is not durable replay/resync,
observation-focus privacy, production TLS deployment or study reproduction. Original
and v2 source/model/oracle bytes and failed evidence remain preserved. The v3 model
is an explicitly labelled different synthetic contract, never a passing original
bare query.

Final merged-prerequisite verification passes2,163 offline tests, all eight
unexcluded coverage gates, lint, wheel and sdist. All33 wheel Python modules match
source; both byte-pinned fixture contracts match the sdist; the optional events
dependency is present. The initial Windows-only hash failure is preserved: Git
checkout CRLF changed contract bytes while committed LF bytes matched the pinned
hash exactly. Narrow LF attributes restore those existing bytes without changing
contract content, expected hashes or assertions. The new Windows fixture-byte CI
job passes. Receiver source remains identical to the genuine run18 implementation.
Final documentation-head CI and review remain required before merge. No new
scientific requests or publication are authorized by packaging success.
