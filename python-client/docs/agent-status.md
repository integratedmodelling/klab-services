# Agent status payloads — read-only, offline

`decode_agent_status(body, expected_agent_urn=...)` decodes the existing Java
`RuntimeAgent.Status` JSON payload into an immutable `AgentStatusSnapshot`.
It accepts only bounded UTF-8 bytes (default/hard ceiling64KiB), exact typed fields,
an exact expected URN, Java long ranges and READY/RUNNING/STOPPED/FAILED states.
Duplicate/unknown/missing fields, malformed Unicode and unexpected `@CLASS` fail
closed. `detail` may be absent or null, and is excluded from the default repr;
it remains untrusted, potentially sensitive text, not automatically redacted.

```python
from klab_client import decode_agent_status

# body is an already captured Status payload, NOT an entire AMQP Message envelope.
snapshot = decode_agent_status(body, expected_agent_urn="test.agent.status")
print(snapshot.state)
```

This performs **no I/O** and is **not** an agent handle, subscription, status query,
start/stop operation or terminal-cleanup verifier. Any caller can forge a matching
URN. Broker/principal/Runtime/incarnation authentication must be enforced by a
future transport adapter, not inferred from this payload. Sender timestamps are
not a total order, a local clock or a durable recovery cursor. `viable` is preserved
independently of state; READY does not establish that no start was attempted, and
STOPPED/FAILED do not establish session/queue/registry removal. Activity Job hints
are a different protocol. No new endpoint or scientific logic is introduced.

## Reproduce this slice from the PR checkout

JDK21/Maven wrapper and Python3.11+ are required. From the repository root:

```sh
python -m pip install -e './python-client[dev]'
python -m pytest -q python-client/tests/unit/test_agent_status.py
./mvnw -B -pl klab.core.common -am -Dtest=AgentStatusWireTest,AgentInstantiationRequestSerializationTest,AgentEventBusTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Windows: use `mvnw.cmd` and quote each `-D...` argument in PowerShell. Both languages
use `klab.core.common/src/test/resources/agent-status-wire.json`. Java creates all
four states with the production `JacksonConfiguration` mapper and asserts equality
to that fixture. Actual generated JSON remains in
`klab.core.common/target/agent-status-wire-actual.json`; Surefire reports contain
the Java result. Python consumes the same committed fixture and exercises malformed
and boundary inputs. No persistent service, credential, broker, agent or Job is
created; all test-owned work ends with the test process.

Initial serializer verification deliberately caught the guessed fixture's missing
`@CLASS` and null-detail omission. The fixture/decoder were corrected to observed
production serialization, not the Java serializer modified to match Python.
The first PowerShell command also failed on an unquoted `-D` argument before any
test ran; neither failure is represented as a successful verification.

This common-module command succeeds independently of the unresolved downstream
`klab.core.services` language-SNAPSHOT EXTENT/ATTRIBUTE compilation failure recorded
in #89/#125. Do not interpret it as a full reactor build passing. Full execution/
control, origin/incarnation-bound handles, transport authentication, one-shot
mutation policy, terminal confirmation and independently verified owned cleanup
remain unfinished under #125. This slice alone cannot close that issue.
