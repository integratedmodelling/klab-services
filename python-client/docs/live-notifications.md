# Bounded live activity notifications (PR #108 — draft)

The user explicitly selected best-effort notifications, not durable recovery.
Implementation and 71 mocked source-derived tests exist; **actual acceptance is
BLOCKED/FAIL. Do not merge or count this roadmap feature complete yet.**
The owned broker did not satisfy the mandatory negative context-binding policy
test. Wrong-password and wrong-principal rejection alone do not establish context
isolation. Root cause is under investigation; no production vulnerability is
claimed. See [sanitized facts](results/notifications-controlled-20261102.json).

Install `klab-python-client[events]`. Only synchronous root `Context` objects with
a known Runtime identity and Qpid Broker-J AMQP 0-9-1 are admitted initially.
There is no inferred broker origin, HTTP-to-AMQP credential conversion, public
guest default, raw publish/control method or automatic reconnect.

```python
from klab_client import ActivityNotifications, NotificationBroker

# `context` is an already-authorized synchronous root Context. The operator must
# independently provision this broker account and its exact-context exchange ACL.
broker = NotificationBroker(
    "amqps://broker.example/%2F", username=broker_user, password=broker_password,
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
- HTTP context access is not broker authorization. The operator must provision ACLs
  that allow reading/binding only approved exchanges, deny publishing to receivers,
  and restrict temporary queue ownership. A client cannot retrofit those policies
  from an HTTP token or prove event authenticity merely from an envelope field.
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

## Pending acceptance

Current candidate `772e564ec12d00d6499499f51287fa71818678e2` includes the real
Qpid passive-fanout fix. All 71 focused tests and hosted Python3.11–3.13, core-only,
build and Jenkins checks pass. Initial full software run passed2112 tests, seven
coverage gates, lint/build and an isolated core-only installed-wheel check. The
seven recorded owned broker JVM identities are independently absent; failed
private fixtures remain preserved. Software success does not waive live failures.

Need an owned ACL-controlled actual Qpid broker plus pinned Java publisher/Runtime
evidence, correct/wrong principals and contexts, publish denial, socket disconnect,
slow consumer/queue-limit and admitted oversized/malformed input, local shutdown
and independent queue/process cleanup checks. A Java Message/AMQP publisher fixture
alone is transport compatibility, not a genuine Runtime activity/Job acceptance.
TLS policy has software tests; real TLS verification needs its own controlled
certificate/socket evidence before any deployed TLS acceptance claim.
