# #125 — Python agent execution/control: accurate implementation handoff

Updated 3 November 2026. **OPEN unfinished client implementation and genuine live
acceptance, with transport/provisioning decisions. Not a reproduced Java defect and
not an external-only blocker.** No new stop/status HTTP endpoint is assumed.

## Current accepted state

Pin `e09a7de7cac6b48526fd8b7ac72879abb36a77a3` on
`feature/python-experiment-runner`. #111 is **MERGED** at
`19668515a7307aec4b1e671207f5b2d595572a14`, not still underway.
It implements only `AgentPreviews` / `AgentSourcePreview`: server parsing plus
source-only translation. It fixes `compileOnly`, `doNotStart`, `doNotBindSession`,
`doNotBindObservation`, `reportJavaCode` true, `observationId=-1`. This does not
compile Java, register/start an agent, create a private session or control a broker.

Existing Java already implements agent compilation, registration, lifecycle
messages, status and stop. Do not label these absent merely because Python has no
execution handle. #108's activity reader is not an agent-control channel.

## Reproduce the current Python boundary (safe, offline)

Prerequisites: Git and Python3.11+. From a fresh pinned checkout:

```sh
git clone https://github.com/integratedmodelling/klab-services.git klab-issue125
cd klab-issue125
git checkout --detach e09a7de7cac6b48526fd8b7ac72879abb36a77a3
python -m venv .venv
# Activate .venv for your platform, then:
python -m pip install -e './python-client[dev]'
python -m pytest -q python-client/tests/unit/test_agent_previews.py
python -c "import klab_client; assert hasattr(klab_client, 'AgentPreviews'); assert not hasattr(klab_client, 'AgentExecution'); assert not hasattr(klab_client.AgentPreviews, 'start'); assert not hasattr(klab_client.AgentPreviews, 'stop'); print('SOURCE_PREVIEW_ONLY_NO_EXECUTION_HANDLE')"
```

Expected at the pinned baseline:45 tests PASS and the marker above. These checks
reproduce the missing public execution/control surface, **not** a server failure.
A new implementation should deliberately replace the absence assertions with its
positive execution-handle tests. It must not simply rename preview as execution.

## Reproduce existing Java lifecycle behavior without a broker

Prerequisites: JDK21, `JAVA_HOME`, Maven wrapper and declared Maven repositories.
From the same checkout (`mvnw.cmd` on Windows):

```sh
./mvnw -B -pl klab.core.services -am -Dtest=AgentInstantiationRequestSerializationTest,AgentEventBusTest,RuntimeAgentBaseTest,AgentRegistryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: all selected tests PASS; inspect module `target/surefire-reports/` for
failures/errors/skips. No external Hub, original worldview/project, credentials or
scientific Job is required. AgentRegistry tests compile/register owned test agents
inside the test JVM; AMQP is mocked where used. They are not real broker ACL or
Python lifecycle acceptance. Record source, JDK/Maven and resolved dependency
identities. Failed SNAPSHOT resolution is a setup failure; do not mix closures.

**Actual repeat on3 November:**45 Python preview tests pass. The common Java module
runs five selected tests successfully (four serialization, one event-bus), then
`klab.core.services:compile` fails before `RuntimeAgentBaseTest`/`AgentRegistryTest`
with `LanguageAdapter.java:2006 EXTENT cannot be resolved` and line2007 `ATTRIBUTE`
likewise. This is not an observed agent lifecycle failure.

Minimal Java reproduction: `./mvnw -B -pl klab.core.services -am -DskipTests compile`.
Triggering language dependency:
`org.integratedmodelling:org.integratedmodelling.languages.observable:1.0.0-SNAPSHOT`,
SHA256 `db1a58cc9a3ab8f3f56db1855b9cc3812edc352154d377883031558c23db88af`.
Inspect it with:

```sh
javap -classpath "$HOME/.m2/repository/org/integratedmodelling/org.integratedmodelling.languages.observable/1.0.0-SNAPSHOT/org.integratedmodelling.languages.observable-1.0.0-SNAPSHOT.jar" 'org.integratedmodelling.languages.api.ConceptDeclarationSyntax$RequiresType'
```

Actual constants are `IDENTITY`, `REALM`, `AUTHORITY`; the pinned services source
also needs `EXTENT` and `ATTRIBUTE`. The accepted earlier artifact hash was
`4b89ef600e5d620eaf18553170d11b49f5dd78f93914402d3901e4832f5edcf1`.
All five language SNAPSHOT JARs changed; complete hashes and repeated command
outcomes are in `notes/roadmap-handoff-verification-20261103.json` and the current
#89 body. Establish a coherent maintained source/artifact pair; do not remove
scientific cases, mix old closures or overwrite someone else's Maven cache.
Report changed artifact bytes/outcomes honestly if this exact trigger no longer
resolves. Python state/transport development can continue independently; this build
failure is not justification for calling all of #125 external.

Specific executable evidence:

- `AgentRegistryTest#sourceOnlyTranslationDoesNotCreateAnInstanceOrClassEntry`:
  unchanged registered-agent/class counts, viable Java source, null agent URN.
- `AgentRegistryTest#managedAgentsAreSingleUseAndExplicitStopIsTerminal`:
  start once; repeated start rejected; stop terminal; registry entry removed.
- `AgentRegistryTest#remoteStopUsesManagedCleanupAndRemovesTheRuntimeSubscription`:
  client stop dispatch reaches managed cleanup, registry and subscriptions removed;
  client alive=false. This test uses mocked AMQP and must retain that label.
- `AgentEventBusTest`, `RuntimeAgentBaseTest`: existing wire/state/message behavior;
  adapt these contracts rather than porting compiler or scheduler logic to Python.

## Exact source map for an implementing/fixing agent

1. Python current surface:
   `python-client/src/klab_client/agents.py`, exports in
   `python-client/src/klab_client/__init__.py`,
   `python-client/tests/unit/test_agent_previews.py`.
2. Request DTO:
   `klab.core.common/src/main/java/org/integratedmodelling/klab/rest/AgentInstantiationRequest.java`.
3. Existing controller:
   `klab.services.runtime.server/src/main/java/org/integratedmodelling/klab/services/runtime/server/controllers/RuntimeServerController.java#instantiateAgent`.
   Route `POST /api/v1/instantiate/agent`; `compileOnly` maps to
   `DO_NOT_COMPILE_JAVA`, not the same as a compiled deferred agent.
4. Existing runtime creation/ownership:
   `klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/RuntimeService.java#compileAndRunAgent`.
5. Registry/lifecycle implementation:
   `klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/kactors/compiler/runtime/AgentRegistry.java`;
   registry tests at the same package under `src/test/java`.
6. Java remote handle:
   `klab.core.common/src/main/java/org/integratedmodelling/common/runtime/actors/AgentImpl.java`:
   `connect`, `connectOwned`, `start`, `stop`, `receiveMessage`, `disconnect`.
   `stop()` returns publication success, not terminal cleanup confirmation.
7. Wire/state types:
   `klab.core.api/src/main/java/org/integratedmodelling/klab/api/actors/RuntimeAgent.java#Status`,
   `klab.core.api/src/main/java/org/integratedmodelling/klab/api/services/runtime/Message.java`.
   Inspect `AgentStarted`, `AgentStopped`, `AgentStatusChanged`, `AgentFailed` and
   `AgentStatusRequested`; never treat activity Job hints as these messages.
8. Transport:
   `klab.core.common/src/main/java/org/integratedmodelling/common/runtime/actors/AgentEventBus.java`,
   `klab.core.common/src/main/java/org/integratedmodelling/common/authentication/scope/AMQPChannel.java#forAgent`.
   Agent URN/federation/broker identity and Runtime incarnation are distinct from
   a bare numeric observation ID. Transport state is not serialized in AgentImpl.
9. Language/lifecycle reference: `docs/AGENTS.md` sections4.3 and10;
   `python-client/docs/agent-source-preview.md` and its committed result JSON.

## Work the client agent can do now

- [ ] Implement an origin/principal/Runtime-incarnation-bound execution handle and
  immutable status/descriptor decoding against the existing wire types.
- [ ] Model not-started, request sent, observed running, terminal stopped/failed,
  transport-lost/unknown and local detached separately. A boolean send result is
  not observed termination; an alive=false descriptor alone is not completion proof.
- [ ] Bound source/DTO/message bytes, queues, waits and operations. Reject wrong
  origin/URN/principal/incarnation and malformed lifecycle envelopes.
- [ ] Single-transmission instantiate/start/stop policy with explicit unknown
  outcomes; no automatic mutation replay or implicit restart after service restart.
- [ ] Separate local broker detachment from confirmed remote stop and from explicit
  owned private-session disposal. Preserve original source-preview guarantees.
- [ ] Add deterministic tests for the state machine and protocol adapter, then
  genuinely served acceptance. No placeholder handle counts as full delivery.

## Decisions / backend changes only if a concrete gap is demonstrated

Choose supported behavior kinds and the exact safe broker profile: origin/TLS,
minimal publish/read ACLs, agent exchange/queue ownership, credential provisioning,
terminal status and incarnation identity. Current context reader credentials do
not grant agent publication; HTTP tokens are not broker credentials. Runtime-created
script/app/testcase private sessions need explicit ownership and cleanup semantics.

An agent may propose and test the missing broker profile separately; it must not
weaken the accepted context profile or declare a Java blocker from an enum/path
constant alone. If a maintained Java operation actually fails, extend the selected
Java tests with a minimal red case and record expected/actual wire/status, exact
commit, command and input in this issue **before** claiming a server defect.

## Required real fixture and closing criteria

The live agent-control fixture is **not yet implemented/accepted**. Do not require
another agent to retrieve a private temporary folder or rerun a historical mutation.
Commit a portable fixture with generated test credentials, source-matched launch
inputs and isolated temporary state. Use one finite non-scientific agent first,
then a bounded persistent agent; no default observer or scientific Job is needed.

Demonstrate finite completion, persistent stop with genuine terminal status,
failure, startup cancellation, duplicate stop, transport loss, wrong principal/
scope, Runtime restart/incarnation and queue/agent/private-session cleanup.
Use positive credential controls to distinguish denial from missing transport.
Cap wall time, agents, scopes, bytes and messages; retain a mutation-intent journal.
Never resend an ambiguous mutation or call HTTP closure a remote stop. Independently
verify only owned resources are removed and unrelated sessions/twins remain intact.

Close #125 only when the actual Python execution/control implementation, tests,
served lifecycle acceptance, owned cleanup and documentation are merged. Source
preview, local mock tests, product decisions or a Java test passing alone cannot
close this feature. Durable recovery is separately #126, not automatically a
prerequisite for every explicitly bounded best-effort lifecycle slice.
