# Thorough live scientific acceptance

This file describes the real-terrain/reference suite. A separate deterministic
arithmetic fixture now passes actual signed-JWT scientific/lifecycle/concurrent
acceptance and reproducible throughput; see [throughput.md](throughput.md) and
tests/test_local_workflow.py. That success does not fabricate a terrain reference
or satisfy this suite's external data/provider prerequisites.

The live suite now distinguishes **self-consistency/lifecycle checks** from a
**separately obtained scientific reference**. Passing unit conversion alone cannot
prove that terrain values or their locations are correct: an incorrectly shifted
or biased raster could still satisfy `mm = m * 1000`.

## Checks actually executed against a configured stack

Both live tests submit real observations and perform these checks:

1. Retrieve runtime/reasoner capabilities; check the configured home runtime ID
   against the actual server ID when supplied.
2. Create a disposable ONE_OFF session/context, or attach to KLAB_CONTEXT_ID.
   Submit the maintained Region and Elevation through Runtime and retrieve their
   server job results. Require positive committed IDs and full coverage.
3. Require returned metre units, reasoner semantic identity and a 5 × 4 spatial
   grid. Metadata alone is never used as a source of numeric values.
4. Stop waiting with a zero timeout and preserve the handle, then resume and
   retrieve the completed result. This deterministic check does **not** claim
   the computation was still running when the timeout happened.
5. Retrieve all 20 actual cells using **D2_YX, D2_XY and D2_XInvY**. Independently
   map `(x,y)` indices between these traversal orders and compare every value and
   missingness flag. The rectangular shape makes accidental axis swaps visible.
6. Retrieve all cells in mm; require finite plausible elevation, at least one
   nonmissing value, unchanged missingness and the declared conversion invariant.
7. Request cancellation after completed-result retrieval. Require the server to
   report no running job cancelled and leave the result retrievable.
8. Close the original local client. Create a **new** client, attach to the same
   context, reconstruct the original focus path and job handle, retrieve the same
   observation, and reread all cells with identical values/missingness. Require
   the context to remain visible in the server's context information.
9. Only after all assertions pass, explicitly release newly created disposable
   scopes. Attached user-owned contexts are not released. Failed/interrupted
   scopes are retained with printed and JUnit-recorded handles for investigation.

The `test_live_elevation_reference` test additionally compares every native metre
cell with a separately obtained reference, including exact missingness and
explicit absolute/relative tolerances. A reference mismatch fails even if all
self-consistency checks pass.

## Independent reference requirements

Set `KLAB_ELEVATION_REFERENCE` to a JSON file obtained **before** this Python run.
Do not generate expected values from the Python result being tested. Valid
methods are:

* `independent-provider`: values computed/extracted from the actual reference
  dataset using an independent provider tool, with matching grid, CRS, sampling,
  vertical datum, source revision and missing-data rules. This can test numerical
  correctness beyond transport self-consistency.
* `supported-java-workflow`: run the equivalent maintained Java/k.Actors workflow
  using the same deployed assets, record all initialization D2_YX cell values and
  dataset provenance. This establishes Python/Java workflow agreement; it does
  not independently certify that the common provider's scientific model is right.

The repository intentionally has **no checked-in invented live reference values**.
The loader validates the reference's geometry, slice, units, provenance and finite
nonnegative tolerances. The result records a SHA-256 of the original reference
file so reviewers can identify the exact oracle used. It cannot authenticate the
origin of an arbitrary user-supplied file: provide the cited source alongside the
acceptance report. Do not put credentials in reference metadata.

Required JSON fields:

| Field | Required value / meaning |
|---|---|
| `schema_version` | `1` |
| `semantic_definition` | `"geography:Elevation in m"` |
| `units`, `crs` | `"m"`, `"EPSG:3857"` |
| `bounds` | `[200000, 200500, 6000000, 6000400]` (minX, maxX, minY, maxY) |
| `shape`, `curve` | `[5, 4]`, `"D2_YX"` |
| `slice` | `{"type":"INITIALIZATION","key":"0-0","start":0,"end":0}` |
| `method` | `"independent-provider"` or `"supported-java-workflow"` |
| `source` | Citation, command/workflow, or reproducible reference extraction record |
| `dataset_id`, `dataset_revision` | Provider/dataset identifier and pinned revision/version |
| `sampling`, `vertical_datum` | Explicit sampling/resampling policy and vertical reference |
| `absolute_tolerance_m`, `relative_tolerance` | Finite nonnegative decimal strings, justified by provider precision |
| `values_yx` | Exactly 20 independently obtained values (decimal strings/JSON numbers); `null` for missing cells |

At least one reference value must be nonmissing. Comparison is
`abs(measured-expected) <= max(absolute_tolerance_m, abs(expected)*relative_tolerance)`.
Missingness must agree exactly. The reference's dataset ID is an assertion about
the oracle's provenance, not automatic proof of which model the server selected;
retain the equivalent Java/provider selection/provenance report as well.

Within the grid, D2_YX offset is `y*5+x`, D2_XY offset is `x*4+y`, and D2_XInvY
offset is `x*4+(3-y)` (API Data.FillCurve). These are **index** conventions;
do not assume that `y=0` is north or south when extracting independently
georeferenced cells. Audit the actual grid-to-world orientation in the
provider/Java workflow before producing the reference.

## Commands and auditable artifacts

Configure the existing endpoint/credential/service/asset prerequisites from
README, plus the independent reference. From `python-client/`, Linux:

```sh
export KLAB_ELEVATION_REFERENCE='/absolute/path/to/independent-elevation.json'
python -m pytest -o addopts='' -o junit_family=xunit1 -m live -s tests/test_live.py --junitxml=live-acceptance.xml
```

PowerShell:

```powershell
$env:KLAB_ELEVATION_REFERENCE = 'C:\path\to\independent-elevation.json'
& .\.venv\Scripts\python.exe -m pytest -o "addopts=" -o "junit_family=xunit1" -m live -s .\tests\test_live.py --junitxml=live-acceptance.xml
```

The full invocation requires both scientific assets and the reference and **fails
clearly when either is missing**. To explicitly run only the self-consistency and
lifecycle checks before an independent reference is available, use:

```sh
python -m pytest -o addopts='' -m live -k 'not reference' -s tests/test_live.py
```

Report that as self-consistency/lifecycle validation, not reference-validated
scientific correctness. The original script remains a concise quickstart; it
does not replace this deeper suite.

JUnit properties/stdout record context IDs, focus paths, job/observation IDs,
initial observed status, UTC timestamps, all 20 native and converted cell texts,
coverage/units/grid checks, returned CRS/bounds/shape/grid parameters when supplied,
reference hash/provenance/error, and cleanup outcomes.
Known credentials are redacted; endpoint/token configuration is not recorded.
Job handles are recorded before waiting so failed runs can be investigated.
Keep the report together with the exact client/server revision and independent
reference. A report with failures is not a successful live acceptance result.

## Honest limits

The original acceptance expansion had no configured stack. On 5 October 2026,
the subsequent [local stack investigation](local-stack.md) built and started
all four services and Neo4j, loaded the maintained public imod worldview, and
verified real public Python HTTP calls. Ordinary scientific calls failed with
HTTP 403; compatible numerical models/data and a reference remain absent. Live
semantic probes also found a server string-unit builder stub. Thus scientific
acceptance remains unverified despite demonstrated infrastructure startup.
The expanded suite's offline synthetic checks do not establish a live pass.

The completed-job cancellation check does not prove active cancellation, an
ABORTED model execution, subscriber coalescing, or a nonzero in-flight timeout.
Those require deployment-owned deterministic failure/long-running fixtures and
their expected outcomes; substituting guessed semantics or timing-sensitive
elevation submissions would not be reliable evidence. Authentication expiry,
cross-user authorization/isolation, temporal data and provider provenance audit
also remain separate live-stack acceptance cases. Their offline error/race tests
do not establish a live pass.
