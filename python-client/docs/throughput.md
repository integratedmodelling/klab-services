# Local acceptance and throughput

## Reproduce

Prerequisites: Windows local runner, JDK 21, Maven wrapper, Python 3.11+ and the
public imod project at **608bef150ced0a109db98a5aad64ba4461beaa54**. Prepare rejects
a different revision. Compatible language SNAPSHOT artifacts must resolve in
Maven. No production account or personal ~/.klab directory is used.

From python-client/:

```powershell
py -3.11 -m venv .venv
& .\.venv\Scripts\python.exe -m pip install ".[dev,local]"
& .\.venv\Scripts\python.exe tools/run_local_acceptance.py `
  --java 'C:\jdk-21\bin\java.exe' --worldview 'C:\projects\imod' `
  --state-dir 'C:\existing-parent\new-klab-state' --build `
  --samples 8 --duration 15 --step-timeout 900
```

Use an existing writable parent and a new state directory. `--build` compiles
servers/test fixtures, builds the required Web UI, and generates classpaths.
The runner creates TEST certificates/RSA-signed ROLE_USER credentials, loads
models, starts services, runs live tests, restarts Runtime for persisted ACL
checks, measures each phase, and confirms cleanup. Any failed step/uncertain
cleanup produces nonzero exit. Child steps have finite deadlines; processes
with unverifiable identity are not killed and are reported as cleanup failures.
The runner is Windows-only; the importable SDK/offline CI are cross-platform.

## What acceptance checks

* Normal JWT signature/expiry verification, peer-scope setup, actual Runtime
  arithmetic execution and stored values **123.25 m / 123250 mm** on a 5×4 grid.
* Exact geometry, independent-client attach/resume and named release.
* A coordinate-coded one-shard field with valid zero and one missing cell,
  compared against independent XY/YX/inverted-Y oracles.
* An explicit computation entry/release handshake for in-flight wait/cancellation,
  not a workload-size race. Controls are test-classpath code, never in the product jar.
* Real missing-model failure; expired/invalid credentials denied; a second valid
  user proves an allowed operation before private access must return explicit denial.
  5xx, malformed responses, timeout or transport failure cannot count as denial.
* Private/shared context ACLs preserved across a real Runtime registry restart,
  including users with the same local federation. Enumeration is not authority.

This is an isolated test deployment and synthetic fields, not production-hub
onboarding or observed terrain. The separate [provider/reference suite](live-acceptance.md)
requires genuine models, data and an independently obtained reference.

Explicit live tests against running prepared state:

```powershell
$env:KLAB_FIXTURE_STATE_DIR = 'C:\state'
& .\.venv\Scripts\python.exe -m pytest -o "addopts=" -m live -s tests/test_local_workflow.py
```

## Measurements and evidence

Full-workflow mode measures new context -> resolution/submission -> completion ->
40 verified reads -> reattach/readback -> release. Repeated-read mode reuses one
computed observation and measures reattach/resume plus 20 verified point reads,
with no recomputation. Warmup/setup/seed are excluded from measurement intervals.
Queued work is bounded; failures invalidate successful throughput metrics.
Recorded fields include stage/percentile timings, correctness hashes, source and
compiled class hashes, pinned worldview/model provenance, SNAPSHOT jar hashes,
counts and optional OS JVM CPU/RSS/IO counters. No private credentials are copied.

```powershell
& .\.venv\Scripts\python.exe tools/workflow_benchmark.py `
  --fixture-config 'C:\state\scientist.json' --samples 8 --concurrency 4 `
  --warmup 1 --duration 15 --server-state-dir 'C:\state' --report 'C:\state\full.json'
```

Baseline before the maintainer revisions (Windows 11, Python 3.11.9, JDK 21.0.12.1):

| Phase | Verified / failed | Workflow or read-cycle rate/s |
|---|---:|---:|
| Full c1 | 8 / 0 | 0.355 workflows |
| Full c2 | 12 / 0 | 0.678 workflows |
| Full c4 | 17 / 0 | 0.949 workflows |
| Repeated read c4 | 54 / 0 | 3.389 read cycles |

[Baseline JSON](results/local-throughput.json) retains exact times/counters and
source checkpoint. These are short, small-field point-read measurements, not
maximum capacity, bulk bytes/s, cold-cache or robust tail-latency SLAs. Current
revision checks/results are in [separate reviews](review.md); detailed development
history is under repository `notes/python-client/` and is not shipped with the SDK.
