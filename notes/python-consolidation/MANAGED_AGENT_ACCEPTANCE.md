# Genuine managed Runtime acceptance — PASS, bounded scope

Successful evidence: `.consolidation-evidence/managed-agent-owned-v5-bounded-read-20261104/`.
Combined source `3efaf5ef27da126e94bfb3638bd9ae786994942d`; installed noneditable
42-module client source inventory matches that checkout. The subsequently merged
broker prerequisite #195 carries the same Java correction. The final #138 refresh
changes accepted-base ancestry, not these Python product modules.

Unlike `AgentChannelInteropTest`, this run creates no fake lifecycle peer. A
genuine Qpid SystemLauncher broker binds only loopback38095/private vhost, with
an ephemeral dedicated credential and no20937/20938/default shutdown listener.
All owned service JVMs explicitly use `-Dklab.local.broker.uri`. The private
TEST-only account permits dynamic agent exchanges inside its isolated vhost;
this is not a production least-privilege ACL claim. Credentials/arguments/logs
remain in restricted local run state, not committed here.

The installed `AgentExecutions` parses and registers one actual BEHAVIOR using
the existing Resources/Runtime endpoints, explicit `doNotStart` and unbound
observation/session flags. The behavior has required version metadata and an
explicit `using core.console as console` import. Runtime confirms registration
and `messagingConnected=true`. Installed `DeferredAgent` attaches once and observes
**READY → RUNNING → STOPPED** (a second RUNNING report was retained). No automatic
start/stop or registration replay is used. Each prior failed test stack was fully
owned, terminated and isolated from later fresh state.

Pinned Qpid lacks the capabilities required by Pika confirms; the caller explicitly
selects `publication="unconfirmed"`. Start and stop outcomes stay **UNCONFIRMED**,
even with later lifecycle evidence. `remote_cleanup` stays **UNKNOWN**. The
observed states are sender reports over the provisioned broker, not authenticated
incarnation or durable root-commit proof. Principal/incarnation inputs remain
operator assertions. Local queue/connection cleanup is confirmed separately.

The same run then passes the independently checked real-Java123.25m/20-valid-cell
workflow, partial-provenance/UNKNOWN-durability inspection, independent-client
restoration and identical data reread. Owned context/session releases return true
and disappear from a fresh authorized listing. All eight launched processes and
all owned listeners close; every pre-existing Java PID/start identity remains
unchanged. No Modeler service, unrelated terminal or existing database is stopped.

Earlier v1/v2 attempts failed source parsing (missing required behavior metadata),
v3 rejected an undeclared self action `print`, and v4 successfully registered but
the harness mishandled a legitimate empty bounded read. V4's finally block recorded
cleanup but left `status=IN_PROGRESS` before its uncaught AttributeError; **it is
a failed harness run, not an accepted run**. These records are preserved unchanged.
V5 corrects only the fixture/harness; it does not relax client validation or fake
the lifecycle. Scientific and remote-agent cleanup limits remain distinct.
