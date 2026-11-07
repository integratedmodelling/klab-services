# Explicit synthetic managed lifecycle reference

**JAVA-GAP-0008 — SIMULATED_PROVISIONAL; native managed execution/cleanup NOT_TESTED.**
`klab.experimental.managed-agents/1` is a finite synthetic lifecycle proposal. It
does **not** parse, compile, register or execute k.Actors source. Existing native
`AgentExecutions`, `DeferredAgent` and `AgentChannel` remain separate; HTTP or AMQP
failure never selects this reference. No simulator code ships in the production SDK.

```python
from klab_client import create_managed_reference, command_managed_reference, read_managed_reference

created = create_managed_reference(context, fixture="idle", allow_experimental=True)
ref = created.reference
running = command_managed_reference(ref, "start", expected_revision=created.revision,
                                    allow_experimental=True)
stopping = command_managed_reference(ref, "stop", expected_revision=running.revision,
                                     allow_experimental=True)
terminal = read_managed_reference(ref, allow_experimental=True)
# STOPPED alone is NOT cleanup evidence, even in this reference.
cleaned = read_managed_reference(ref, allow_experimental=True)
if cleaned.cleanup_confirmed:
    command_managed_reference(ref, "purge", expected_revision=cleaned.revision,
                              allow_experimental=True)
```

Native asynchronous wrappers have the same names prefixed `async_`. All mutations
are explicit and use the last observed exact revision. Stale/repeated/invalid
commands fail409; unknown mutation outcomes are never automatically retried. Start
is single-use; terminal references cannot restart. Stop-before-start is allowed in
the reference but still requires later terminal and separate cleanup confirmation.
SDK close merely closes HTTP transport; it never auto-stops or purges.

Only named fixtures `idle`, `finish`, `fail` exist. Creation returns READY/revision1
and `SYNTHETIC_STATE_MACHINE_NO_KACTORS_EXECUTION`. Start returns RUNNING; stop
returns STOPPING, not terminal confirmation. Explicit GET ticks the deterministic
fixture: STOPPING→STOPPED or RUNNING→FINISHED/FAILED; a **subsequent** GET records
synthetic cleanup confirmation. Purge requires terminal+cleanup and deletes retained
inspection state. These are reference state transitions, not real scheduler,
resource disposal, native lifecycle statuses, scientific results or independent
Java cleanup proof. No arbitrary model/behavior text is accepted.

The root context belongs to the caller and is never created or released implicitly.
This intentionally models only a BEHAVIOR-like caller-owned root lifecycle.
SCRIPT/APP/TESTCASE private sessions, task restrictions, user-agent singleton rules,
component/trait creation, observation binding, custom messages/ask-reply and actual
source validation are **not implemented** here. Consult `docs/AGENTS.md` and native
API/grammar/tests for those contracts; a synthetic fixture is not language support.

Proposed Runtime routes: POST `/experimental/managed-agents/v1/create` with protocol
and fixture; GET `/{agent_id}` with protocol+epoch query; POST
`/{agent_id}/{start|stop|purge}` with protocol, epoch, expected_revision. Identity and
epoch are32lowercasehex; requests≤4KiB, responses≤64KiB, retained references≤16per
process. Capacity429requires explicit cleaned-terminal purge, never silent eviction.
SDK strict UTF8JSON rejects duplicates/nonfinite literals, foreign identity/schema,
nonboolean cleanup/purge confirmation and unconfirmed command transitions.

Current credentials, exact originating root/Runtime and epoch fence every request.
A new SDK connection can explicitly attach the same context and rebind the immutable
reference with `dataclasses.replace(ref, context=context)` to inspect current state;
no reconnect automatically starts/stops anything. Process restart changes epoch and
loses reference rows, so old references fail410even when SQLite restores the context.
No new reference is automatically created after loss. Scope release cleans its
reference rows; no unrelated user scope is touched. Failed durable creation, command,
status tick, purge or reset restores ephemeral rows/epoch and durable state with507,
not an unsaved acknowledgement. Actual native restart/AMQP principal/incarnation,
private-session cleanup and resource-disposal acceptance remain unverified; issue125
stays open. This reference does not certify Java agents as absent or fixed.
