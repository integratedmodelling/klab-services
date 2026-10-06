# Historical scientific workflow and throughput report

## Verified boundary and fixture

The complete API workflow has now run on real local k.LAB services: signed user
JWT authorization, peer user-scope initialization, session/context creation,
Region submission/acknowledgement, numeric model resolution and execution,
storage reads with unit conversion, separate-client attachment/job retrieval,
and explicit context/session release.

The model is an **explicit deterministic test field**, not measured terrain:

```kim
namespace python.fixture;
model geography:Elevation in m set to [100 + 23.25];
```

Expected independent arithmetic oracle: each of the 20 cells is **123.25 m**;
each server-mediated millimetre read is **123250 mm**. Every benchmark sample
checks actual server storage values, units, committed IDs, numeric coverage and
exact EPSG:3857 bounds `[200000,200500,6000000,6000400]`, regular shape `[5,4]`.
Literal-source/no-computation shortcuts are not the measured workload. A Region
can be acknowledged without an explanatory model (model coverage zero); numeric
quality coverage remains 1.

Authentication uses the existing service certificate/public-key/JWT verification
contract with an **isolated loopback TEST authority**. Certificates carry TEST
level; enrollment secret/RSA keys and credentials are generated outside Git.
Services validate signatures, expiry and user roles normally. No server-key is
supplied by the scientist Client. This does not certify production hub login,
browser-session scoped access or actual elevation-provider accuracy.

## Run from fresh isolated state

Prerequisites: Windows, JDK 21, Maven wrapper, compatible language SNAPSHOT
dependencies (available in the verified Maven cache), Python 3.11+, and the public
imod worldview pinned to `608bef150ced0a109db98a5aad64ba4461beaa54`. See local-stack.md
for build details and portable JDK checksum. Server corrections in this branch
are required; an older unmodified server is not this acceptance target.

```powershell
py -3.11 -m venv .venv
& .\.venv\Scripts\python.exe -m pip install ".[dev,local]"
```

From python-client/, use an existing writable parent and a pinned imod checkout:

```powershell
& .\.venv\Scripts\python.exe tools/run_local_acceptance.py `
  --java 'C:\path\to\jdk-21\bin\java.exe' `
  --worldview 'C:\path\to\imod' `
  --state-dir 'C:\existing-parent\klab-acceptance-new' `
  --build --samples 8 --duration 15
```

The runner optionally builds servers, required Web UI and classpaths; prepares
new test authority/model/configuration; starts the loopback stack; runs actual
pytest scientific/lifecycle tests; measures full workflows at concurrency 1/2/4
and repeated reads at 4; writes reports/logs; then stops its services and authority
even on failure. Existing prepared state is rejected to make a fresh deployment
explicit. Occupied ports are not replaced. Build setup uses -DskipTests; Java
test evidence is recorded separately. Cryptography/psutil are fixture/metrics
extras, not mandatory scientist package dependencies.

Actual verified execution used existing compiled classpaths, without --build:

```powershell
& 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-client-311\Scripts\python.exe' tools/run_local_acceptance.py `
  --java 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\jdk\jdk-21.0.12.1+1\bin\java.exe' `
  --worldview 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-local-stack\assets\imod' `
  --state-dir 'C:\Users\lumsd\AppData\Local\Temp\opencode\klab-repro-final' `
  --samples 8 --duration 15
```

Every step returned exit 0. run-report.json reports passed=true and
cleanup_exit_code=0. Do not publish fixture keys, credentials or the whole state
directory alongside sanitized reports.

## Tests and workload definitions

Offline: `python -m pytest -q`. Against already-running prepared state:

```powershell
$env:KLAB_FIXTURE_STATE_DIR = 'C:\path\to\running-state'
& .\.venv\Scripts\python.exe -m pytest -o "addopts=" -o "junit_family=xunit1" `
  -m live -s tests/test_local_workflow.py --junitxml=local-acceptance.xml
```

Missing configuration fails, not skips. This is separate from test_live.py's
real-terrain/reference example; its missing assets/reference are not silently
satisfied by the constant field.

verify_fixture.py checks actual missing-model failure, reconnect/result retention,
timeout/resume, completed-job cancellation, active cancellation observed
WAITING -> accepted -> INTERRUPTED, denied expired/invalid-signature JWTs, denied
other-user private-context/job access, and explicit release. Completion races are
reported separately; the pytest active-cancel test requires actual confirmation.

Manual benchmark against running state:

```powershell
& .\.venv\Scripts\python.exe tools/workflow_benchmark.py `
  --fixture-config 'C:\state\scientist.json' --mode full-workflow `
  --samples 8 --concurrency 4 --warmup 1 --duration 15 `
  --server-state-dir 'C:\state' --report 'C:\state\full-c4.json'
& .\.venv\Scripts\python.exe tools/workflow_benchmark.py `
  --fixture-config 'C:\state\scientist.json' --mode repeated-read `
  --samples 8 --concurrency 4 --warmup 1 --duration 15 `
  --server-state-dir 'C:\state' --report 'C:\state\repeated-read.json'
```

Full samples use new contexts and unique observation identities: create -> resolve
-> submit -> await -> verify 40 numeric/unit reads -> reattach/resume/readback ->
release. Model/semantic/compiled-code caches may be warm: these are fresh-context
execution runs, not cold caches everywhere.

Repeated-read samples reuse one computed observation, reattach/resume and perform
20 point reads, with **no recomputation**. They are not computation throughput.
Warmup/setup/seed are excluded from measured intervals and included in total time.
Queued work is bounded to concurrency, sustains at least duration/minimum samples,
and drains on first failure. Any failure prevents successful throughput fields
and produces nonzero exit. There is no machine-independent minimum speed assertion.

## Actual fresh-run results

Windows 11 amd64, Python 3.11.9, Temurin 21.0.12.1+1, Maven 3.9.5, Neo4j 2025.10.1.
Model SHA-256: `2ef990b35e0ba7a2288bda4022ef37b3ee26340a5be868a834197ed9071eb877`.
UTC run: 2026-10-06 02:57:17–03:00:08. One verified warmup per phase, minimum
8 samples / 15 seconds. Nearest-rank percentiles over small samples are descriptive,
not statistically robust tail SLAs.

| Phase | Verified / failed | Measured s | Verified rate/s | p50 / p95 / p99 s |
|---|---:|---:|---:|---|
| Full workflow c1 | 8 / 0 | 22.535 | 0.355 workflows | 2.798 / 2.919 / 2.919 |
| Full workflow c2 | 12 / 0 | 17.710 | 0.678 workflows | 2.802 / 3.779 / 3.779 |
| Full workflow c4 | 17 / 0 | 17.913 | 0.949 workflows | 3.684 / 5.615 / 5.615 |
| Repeated read c4 | 54 / 0 | 15.936 | 3.389 read cycles | 1.200 / 1.465 / 1.496 |

Full samples verify 40 cells plus an attachment spot check. Verified cell rates:
14.200, 27.103, 37.960 cells/s. Repeated-read rate: **67.773 cells/s**. These are
end-to-end point-read operation rates, not bytes/s, bulk bandwidth or maximum
server capacity.

| Phase | Aggregate peak RSS bytes | CPU seconds | OS read / write bytes |
|---|---:|---:|---:|
| Full c1 | 3,750,977,536 | 27.609 | 168,346 / 1,591,244 |
| Full c2 | 3,794,702,336 | 34.328 | 234,024 / 2,235,651 |
| Full c4 | 3,683,733,504 | 51.641 | 337,846 / 3,200,684 |
| Repeated read c4 | 3,718,033,408 | 55.984 | 0 / 386,344 |

Five-JVM OS counters: CPU seconds can exceed wall seconds across processes/cores.
IO counters are not physical-disk or network bandwidth measurements.

## Evidence and boundaries

Host-local evidence: klab-repro-final/run-report.json, acceptance.xml, full-c1.json,
full-c2.json, full-c4.json, repeated-read.json and logs. Reports retain actual counts,
stage times, IDs, value hashes, UTC, Git HEAD, dirty-tree flag, implementation/model
hashes and resource counters. A sanitized summary is in results/local-throughput.json.
Checkpoint history and every failure/correction are in work-log.md.

Essential server fixes: string units, terminal null jobs, Resources' operational
Reasoner for indexing, expected pre-computation storage absence vs genuine migration
failure, and private managed-scope/context connection authorization. Explicitly
shared contexts remain allowed; reconstruction uses persisted visible descriptors,
not caller-provided owner/access rights. Browser scope guards were not removed.

Remaining limits: production hub onboarding, scoped browser credentials, actual
terrain/provider reference, temporal/federated/coalesced workloads, large
observations, bulk export, long soak and hardware capacity are not certified by
this small fixture. The arithmetic is simple: results measure orchestration and
point-read costs as well as computation. Local TEST identity is not external-hub
identity; generated synthetic terrain values are not observed terrain.
