# k.LAB experimental Python computation worker

Separate package **klab-python-worker**, not the end-user SDK, Resources adapter,
embedded inference engine or production service. Parent requirements **RC-P5/CAP-17**,
planning guide Phase5; native compatibility **JAVA-GAP-0017 / REAL_NOT_RUN**.
No simulator dependency/import is shipped in either production Python package.

## Initial role, contract and replacement boundary

Initial role selected for development: a **scoped computation worker** executes a
trusted operator-selected local Python script on declared numeric fields and offers
its validated output to an explicitly configured backend. The backend, not worker,
owns leases and publication authority. This is a replaceable **proposed** transport,
not a claim Java currently advertises these endpoints. Resources contextualization
Avro and scientist point/block APIs are distinct and not repurposed as a worker ABI.

The initial client requires `allow_experimental=True`. HTTPS or loopback-only HTTP,
explicit credentials/scope, no redirects/proxy environment trust, bounded messages,
explicit protocol header `X-Klab-Worker-Protocol: klab.experimental.worker/1`.
Unknown/failed native responses remain errors, never simulator fallback or retries.
No ambient token discovery, server-supplied command, code download or shell execution.

Proposed relative route prefix: `experimental/worker/v1/` under configured Runtime
base URL. Current Java endpoint is **UNKNOWN_PENDING_REFERENCE**. HTTP proposal:

|Method / relative operation|Request|Response / state|
|---|---|---|
|POST register|worker_id, operation|protocol, worker_id, REGISTERED; same registration idempotent, conflicting operation409|
|POST tasks|operation, input Field|protocol, task_id, QUEUED; no automatic retry after ambiguous submission|
|POST claim|worker_id|protocol, task_id/input/lease/LEASED, or IDLE; one claim, not an acknowledgement of completion|
|GET tasks/{id}|none|state, output/provenance (null until atomic publication)|
|POST tasks/{id}/publish|lease, validated output, input_sha256, implementation_sha256|PUBLISHED/output_sha256; duplicate/cancelled/stale lease409|
|POST tasks/{id}/fail|lease|FAILED, no output; no implicit redelivery|
|POST tasks/{id}/cancel|empty|CANCELLED; published output cannot be cancelled409|
|POST tasks/{id}/recover|confirm_recompute:true|QUEUED only from RECOVERY_REQUIRED; caller must explicitly consent, no automatic replay|
|POST release|worker_id|RELEASED; its unfinished leased tasks ABORTED without publication/replay|

All responses carry exact protocol string. Synthetic simulator owner credentials
`sim-token-alice`/bob and distinct worker-role `sim-token-worker-alice`/bob are public
test constants, not real provisioning. Scope must be an existing owned context.
Wrong role403, unknown credential401, foreign/absent context denial, bad input422,
version/lease conflict409, finite capacity429. Release of context/session removes
its simulator workers/tasks; local WorkerClient.close never disposes remote state.

## Data and trusted process boundary

Field is immutable: rank1..4, up to4096numeric cells, explicit unique axis names,
coordinates per axis, declared CRS/unit or explicit null, exact finite values and
boolean validity mask (invalid values must be null). Zero and negatives are valid;
no guessed coordinates, implicit unit/CRS conversion, categorical dictionary or
arbitrary time semantics. No output may change input layout/unit/validity in this
initial role. Output/implementation/input hashes are attributable protocol evidence,
not signatures or verification that the scientific computation is correct.

`execute(trusted_script, field, limits=Limits(...), cancel=Event())` starts a hidden
`python -I` subprocess in a private temporary working directory with minimal
environment, no parent credentials/PYTHONPATH. It enforces wall time, cell budget,
bounded input and checked stdout/stderr file sizes, validates output before return,
and reaps the direct child in finally. A trusted bootstrap waits for parent input
before executing the exact script snapshot: Windows containment is a non-breakaway
Job Object with kill-on-close, POSIX containment a new session/process group. If
containment cannot be established, operator code never runs. Cleanup terminates
owned descendants even after successful direct-child exit. `run_once` checks the
retained task state about every200ms, stops local computation when cancellation or
another terminal state is observed, and fails closed if status cannot be fetched.
Monitoring requests use a200ms per-network-operation timeout (rather than the
ordinary configured transport timeout); this is not an instantaneous distributed
cancellation guarantee or a total HTTP response deadline. `run_once` acknowledges failure without
publishing child errors/partial data. Remote cancellation fences publication even
if a local script finishes after cancellation. It does not replay ambiguous writes.

**Limits:** trusted scripts only. This is process isolation and owned descendant
cleanup, not a hostile-code OS sandbox or hard resident-memory/disk quota. A POSIX
script must not deliberately escape its process group with setsid; Windows Job
membership is kernel-enforced and no breakaway is enabled. Operators
must supply a reviewed container/job boundary for untrusted code. Default simulator
state is volatile. With an explicitly owned opt-in `.klab-sim.sqlite` checkpoint,
published/cancelled tasks and scientific context/Job references survive simulator
restart. Active leases do **not**: RECOVERY_REQUIRED fences old publication authority,
worker registration is cleared, and only explicit owner
`recover_task(..., allow_recompute=True)` permits recomputation. No automatic replay;
callers must decide whether a trusted script's potential side effects make
recomputation appropriate. Native restart/ABI and deployment-level limits remain unfinished C work,
not secretly marked complete by the vertical. Issue127 remains open for its full
native/deployment/ABI and acceptance obligations. No public release is authorized.

## Install and coherent starter

```console
python -m pip install ./python-client ./python-worker ./developer-tools/klab-service-simulator
python -m klab_simulator --port 38000 --seed 7
python python-worker/examples/scoped_worker.py --runtime http://127.0.0.1:38000/runtime
python -m pytest -q python-worker/tests
python -m pytest -q developer-tools/klab-service-simulator/tests/test_worker_vertical.py
```

The example uses **SYNTHETIC_SIMULATOR** nonuniform data/known answers, an ordinary
SDK-owned context and separate worker role/process, explicit lease/publication,
provenance, reconnect and owned cleanup. It is client/worker contract acceptance,
not Java science or historical-model replication. Same package talks to a reviewed
native adapter only when that adapter implements the explicitly selected contract;
there is no implicit hybrid mode or fallback.
