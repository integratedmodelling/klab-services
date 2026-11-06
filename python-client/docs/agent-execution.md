# Deferred agent registration and native AMQP control

This is an opt-in execution surface, separate from `AgentPreviews` and the
transport-free `AgentControlJournal`. It reuses the existing Resources parser,
Runtime instantiate endpoint, production Java `Message` format, native agent
exchange naming, and independently supplied AMQP credentials. No endpoint or
scientific algorithm is invented.

## Explicit registration and attachment

Native awaited registration uses the same deferred/unbound contract:

```python
from klab_client import AsyncAgentExecutions

creation = AsyncAgentExecutions(async_context, principal_key=operator_principal_key,
                               runtime_incarnation=operator_incarnation)
agent = await creation.create(behavior_source, allow_execute=True)
```

Only registration HTTP is async. The returned `DeferredAgent` retains **explicit,
synchronous, thread-affine broker methods**; do not call blocking broker reads from
an async notebook event loop. No async AMQP facade or hidden worker is claimed.
`agent.close()` is local detachment only, never remote stop/disposal.

An async lock serializes attempts on the same creator. Cancellation before native
instantiate admission leaves a later explicit attempt available; cancellation at
or after admission permanently consumes that creator, even while checking the
response identity. A cancelled wait cannot prove rollback or authorize recreation
through a new object. It raises CancelledError normally, not a fabricated receipt.
Context binding is rechecked across awaited capability reads before submission
and before success. Other unconfirmed post-admission errors report
SubmissionOutcomeUnknown and cannot be replayed on that creator. Parser, source,
consent, identity, fixed flags and receipt validation are shared with sync code.
Protocol/cancellation tests are Python evidence, not new live Java compilation,
broker execution, durable exactly-once semantics or scientific acceptance.

```python
from klab_client import AgentExecutions, NotificationBroker

# context is an existing, authorized, root Context from this configured Client.
# These two assertions must come from the operator's maintained deployment
# contract. Neither capabilities.serverId nor uptime establishes incarnation.
creation = AgentExecutions(context, principal_key=operator_principal_key,
                           runtime_incarnation=operator_incarnation)
agent = creation.create(behavior_source, allow_execute=True)
# Registration compiles, but requests doNotStart and both doNotBind flags.
# It does not create/dispose a private session or start in the background.

broker = NotificationBroker("amqps://broker.example/encoded-vhost",
                            broker_username, broker_password)
try:
    agent.attach(broker, federation_id=operator_federation_id)
    publication = agent.start()
    # PUBLISHED means broker-confirmed publication, NOT successful execution.
    event = agent.read()  # one bounded caller-driven read; timeout is transport loss
    # Decide explicitly when/if to request status or stop. Neither is automatic.
finally:
    agent.close()  # local queue/connection only, NEVER stop or remote disposal
```

Only source-parsed `BEHAVIOR` is admitted. `SCRIPT`, `APP`, `UNITTEST`, other
behavior types, arbitrary DTOs, observer selection, and private-session ownership
are excluded. Parsing accepts exact source or only Java parser edge trimming of
ASCII space/tab/CR/LF; both source hashes and the trimming flag are retained.
Resources and Runtime service identities and the context binding are checked
before registration; the response must identify the exact deferred/unbound
agent. A disconnected registration keeps its receipt but cannot attach.

The instantiate request is admitted once per `AgentExecutions` object. Lost,
malformed, failed or identity-drifted post-submission responses remain unknown;
there is no automatic resend, orphan rollback or cleanup assumption. A new
object/process can still replay: this is not a durable intent ledger. An
operator must reconcile an ambiguous registration before creating another.

`DeferredAgent` permits one channel attachment; failed attachment cannot reset
the command journal by reattaching. Context binding is checked before control.
`AgentChannel` also exists for explicitly provisioned native handles, but its
binding fields are caller assertions, not an authentication token or lease.

## Broker requirements and publication outcomes

Use a maintained broker profile with explicit vhost and credentials. Default
TLS verifies the broker hostname/certificate. Insecure transport requires an
explicit loopback-only opt-in. HTTP credentials are not copied into AMQP. The
client passively checks the existing agent exchange, creates one exclusive,
auto-delete bounded queue, and requests only binding, consume and publication
permissions. It never declares a new exchange, deletes an exchange, or creates
or disposes a remote agent/session.

The default `publication="required_confirms"` requires the publisher-confirm
and basic-nack capabilities used by Pika. Missing capabilities fail explicitly;
there is **no silent downgrade**. Pinned Qpid 9.2.0 does not advertise the pair
Pika requires. Its native-wire fixture therefore explicitly selects
`publication="unconfirmed"` (also supported by `DeferredAgent.attach`). This
mode runs a bounded local I/O turn but returns false for status publication and
records start/stop as `UNCONFIRMED`, even if a subsequent status is observed.
It does not claim that the local turn flushed to the broker. It cannot prevent
caller replay through a new object. Do not opt in if broker ACK is required.

Confirm timeout/interruption leaves `UNKNOWN`; nack/return or explicit
unconfirmed mode leaves `UNCONFIRMED`. Neither permits a second start/stop on
that journal. `PUBLISHED` does not prove delivery to a particular runtime agent,
successful execution, terminal state or cleanup. Default status-publication
budget is 16; all deliveries, including command echoes, count against queue
admission/event/serialized-body byte budgets. These are not Python RSS bounds.

The channel is thread-affine and caller-driven using Pika `SelectConnection`.
There is no hidden event-loop thread, automatic reconnect or mutation replay.
I/O waits use explicit deadlines; cleanup may require multiple separately
bounded attempts to close and abort the stream. Private Pika stream abort is a
last-resort failure path. Local native interop used Pika1.4.4 within the declared
`pika>=1.3,<2` dependency range; other versions/platforms are not established by
that result.

## Status and cleanup limits

Decoded message types/classes, queues, dispatch and payload agent URNs, integer
fields and lifecycle/payload state agreements are checked against a shared
production-Java serializer fixture. Unknown/malformed frames fail closed. Own
channel echoes are not lifecycle evidence. Password/username occurrences are
redacted from status details before retention. Broker header `channelId` is an
echo hint, not a signature. Status frames contain neither principal proof nor
Runtime incarnation. Broker ACLs/provisioning remain essential and are not
established by a user-supplied binding or the transport decoder.

Status timestamps are not a cursor or replay order. Terminal and conflicting
reports latch in the journal, not as independently verified current server
state. `remote_cleanup` always remains `UNKNOWN`. Explicit `stop()` requests
termination only; receiving STOPPED does not establish owned graph/session
cleanup. Closing detaches locally, deletes only the private queue and closes
its connection. `local_cleanup_confirmed` records only those local resources;
failed deletion/close remains false and close raises without overwriting an
already active context-manager exception.

## Portable wire fixture (not managed-Runtime acceptance)

From a fresh clone of this branch, with Java21, Python3.12 and Maven access:

```powershell
python -m venv .agent-wire-venv
& ./.agent-wire-venv/Scripts/python.exe -m pip install -e './python-client[dev,events]'
$python = (Resolve-Path ./.agent-wire-venv/Scripts/python.exe).Path
& ./mvnw.cmd -B -pl support/klab.amqp.server -am `
  '-Dtest=AgentMessagingWireTest,AgentStatusWireTest,AgentChannelInteropTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' "-Dagent.python=$python" test
```

On Linux use `python -m venv .agent-wire-venv`, install with that venv's Python,
and run the same Maven arguments with
`-Dagent.python="$PWD/.agent-wire-venv/bin/python"`. Do not omit `agent.python`:
the interop test then **skips**, which is not acceptance. Optional Maven profile
`-Pagent-wire-logging` adds test-only SLF4J diagnostics; it changes no production
dependencies or permissions.

The fixture generates credentials, starts an owned loopback in-memory Qpid
broker, creates an exact-agent **test-only** ACL profile and a production-Java
`AMQPChannel` wire peer. It exchanges READY/RUNNING/STOPPED reports, verifies one
start/stop despite duplicate admission attempts, denies invalid passwords, wrong-vhost access and
client exchange deletion, and independently inventories zero remaining owned
queues before broker shutdown. Native creator alone may delete its exact
auto-delete exchange. Production scoped-reader ACLs are unchanged. This profile
is not a shipped runtime-agent provisioning service or portable deployment ACL.

Inspect `support/klab.amqp.server/target/agent-channel-interop-result.json` and
Surefire reports. The shared JSON under `klab.core.common/src/test/resources/`
is compared to actual production serialization, not a Python-only oracle.
The dedicated native-AMQP CI job runs the fixture rather than accepting a skip.

**Still unaccepted:** real managed Runtime HTTP registration/compilation followed
by authenticated broker execution, incarnation provenance, terminal confirmation,
independently verified owned graph cleanup, private-session agents and durable
restart/recovery. Full Runtime build is blocked by the documented observable
language dependency mismatch (EXTENT/ATTRIBUTE). Restore a coherent maintained
source/artifact pair, then exercise an owned full-stack fixture; do not change
scientific requirements or use this wire peer as a substitute. See issue #125.
