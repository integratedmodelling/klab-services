# Bounded independent experiments

`ExperimentRunner` runs a bounded sequence of typed `Experiment` requests with
1–16 worker threads (default 1). Each experiment requires an explicit
`OwnedContextPolicy` and receives a new session/context. Supply a factory returning
a newly owned, ready `Connection` or an open `Client` with a configured home
Runtime identity. Do not return a shared connection, client or HTTP transport.
Acquire credentials inside the factory through the existing credential providers.

Each `Experiment` includes a unique short name, an `ObservationRequest`, and a
context policy. An optional `context_request` first observes a context object;
the experiment request then runs within that observation. Request geometry,
resolution and scientific variations must be supported by the actual deployment;
the runner does not manufacture variations or establish scientific validity.

Call `runner.run(experiments, timeout=120)` to receive an `ExperimentBatch` whose
outcomes preserve input order. The timeout is per experiment from worker start,
not a batch-wide hard deadline. Existing request timeouts still bound in-flight
HTTP; a user factory or processor is not forcibly interrupted. A processor supplied
to the runner receives `(experiment, result)` while that experiment's client is
open, allowing existing validation, export and analysis APIs to be used.

## Lifecycle and recovery

- A failed experiment does not discard its successful siblings.
- Default `release="retain"` preserves remote work and handles, while owned local
  connections are closed. Opt-in `release="terminal"` requests disposal only after
  a known terminal outcome; released handles may no longer restore.
- A caller `threading.Event` stops waits at normal Job request boundaries.
  `cancel_remote=True` additionally requests cancellation of the current Job when
  caller cancellation is observed. Request acceptance is not terminal confirmation.
- `runner.close()` stops local waiting, not remote computation. Keyboard interruption
  raises `BatchInterrupted` with a retained `.batch` snapshot.
- Inspect outcome states, cancellation acceptance and cleanup errors separately.
  A returned observation is not proof of valid scientific output.
- `batch.dumps()` / `ExperimentBatch.loads()` provide a bounded, versioned local
  inspection manifest. It excludes credentials, requests, processor output and raw
  exception messages. Loading it performs no network calls or resubmission and does
  not resume a durable batch. Restore individual retained handles through the
  existing authenticated handle API instead.

Offline tests exercise real thread overlap and an actual loopback HTTP server with
fresh-process restoration. These are not unchanged-Java scientific acceptance or
scientific throughput measurements. See the contribution review and milestone
status for the remaining live gates.
