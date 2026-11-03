# Python behavior/agent lifecycle: historical transport audit

Current PR111 development implements only the separately labelled
[source-only preview](agent-source-preview.md). The following original audit is
retained; full lifecycle remains incomplete, never inferred from source translation.

**BLOCKED full lifecycle; not implemented or accepted.** Audit source base
`bb4f62ce01bb41d93f506ee34c19687b401d8640`, under the user's explicit instruction
to defer blockers and continue to other features. Keep this PR draft/unmerged.

## Existing implementation is not an absent feature

RuntimeServerController exposes `POST /api/v1/instantiate/agent`, accepts a parsed
AgentInstantiationRequest and authorizes the requested user/session/context. It
checks observation-bound behavior/task requests and adapts the returned agent
descriptor. Options include compile-only, no-start, no observation/session binding
and reporting generated Java. Messaging connection/scope affect deferred start.

`klab.core.common/.../runtime/actors/AgentImpl.java` implements stop by publishing
AgentStopRequested. Remote lifecycle state and custom messages are carried through
the agent messaging channel, not through an established generic HTTP Job result.
The ServicesAPI AGENT path constant alone is not proof of a mapped controller for
stop/status/request/reply: no matching generic Runtime controller was located in
the inspected server source. Do not manufacture HTTP endpoints from constants.

The current k.Actors guide and Agent runtime contracts distinguish:

- start/stop request transmission from subsequent lifecycle confirmation;
- runtime-incarnation-qualified single-use agent identity from reconnecting to a
  new runtime instance;
- script/app/testcase-created private sessions from ordinary behavior/task scopes;
- local handle detachment from remote stop and scope disposal;
- compile/source preview from an executing persistent agent;
- action reply, emitted event, agent status and scientific Job completion.

## Concrete blocker

Python has no accepted origin/principal-bound broker subscription/control adapter
for this lifecycle, no verified terminal-status observation and no owned-service
fixture proving cancellation/control/cleanup. The general event-recovery audit in
draft #108 remains relevant but durable replay is not assumed necessary for every
possible bounded agent product. A separately scoped live best-effort control
channel may be viable after explicit transport/authentication/cleanup agreement.

Do not instantiate persistent work and then claim closing HTTP stopped it. Do not
interpret AgentImpl.stop()'s send boolean as confirmed termination. Do not retry an
ambiguous instantiate call or turn a runtime restart into implicit agent restart.
No agent was launched for this audit; no control or scientific acceptance passed.

## Unblock and required evidence

1. Define supported behavior kinds and source/parsed-DTO intake, compiler preview
   versus execution, safe broker origin/credential provisioning and incarnation
   identity. Reuse current Java parser/analyzer rather than port k.Actors to Python.
2. Agree request/reply timeouts, start/status/stop confirmation, bounded message
   queues/bytes, forbidden custom/lifecycle messages and transport loss policy.
3. Supply a genuine owned broker/runtime fixture. Demonstrate finite completion,
   persistent stop confirmation, failure, disconnected/restarted runtime, denied
   scopes, duplicate stop, cancellation during startup and ownership cleanup.
4. Record whether deferred start and retained completed agents are supported.
   Preserve the difference between terminal agent status, released private session
   and local disconnection. No shared worldview/model edits are implied.

A source-only compilation preview could be a separately approved narrower feature,
but cannot be marketed as running/managing agents. Existing Java capabilities do
not waive Python lifecycle acceptance. Continue to federation verification under
the user-approved sequencing exception and circle back when this gate is resolved.
