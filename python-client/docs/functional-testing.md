# Functional workflows and test organization

Tests are grouped by execution level; functional suites are named for user outcomes.

Current readiness, test results and deployment/component dependencies are recorded
once in [export and functional verification](exports-verification.md).

```text
tests/
  unit/          Offline contracts, validation, file semantics and oracle/rate guards
  integration/   Local HTTP + real subprocess checks without k.LAB services
  functional/    Complete user workflows against real configured services
  fixtures/      Shared independent reference payloads and subprocess fixtures
  conftest.py    Offline network guard and test-only import setup
tools/
  workflows/     Shared scientific scenarios, validation and measured repetition
  run_functional_acceptance.py
  run_workflow_throughput.py
```

## Named acceptance suites

| Suite | User outcome | Shared implementation |
|---|---|---|
| `compute-and-analyze` | Compute, read in metres/millimetres, reconnect; validate Jobs, identity, access and lifecycle | `workflows/compute_and_analyze.py`, `verify_fixture.py` |
| `resume-saved-work` | Save original work and restore/share it in separate authorized Python processes | `workflows/resume_saved_work.py`, existing saved-handles example |
| `typed-compute` | Execute supported typed requests and reject an invalid bound observer | existing typed-observe example |
| `geojson-export` | Produce independently verified geographic geometry, including fresh-process export | `workflows/geojson_export.py` |
| `geotiff-export` | Compute, save, restore and export a raster preserving every coordinate/value, unit and missing-cell mask | `workflows/export_deliverables.py` |
| `provider-reference` | Verify real configured model output, optionally against separately obtained reference data | `workflows/provider_reference.py`, `workflows/scientific_checks.py` |

`functional` identifies complete user workflows. `live` identifies their real-service
requirement. Functional tests carry both markers, so ordinary pytest still excludes
them. Local subprocess integration has its own marker and remains in offline CI.
No assertion was converted into a skip to make the reorganization pass.

## Run

From `python-client/`, offline checks:

```powershell
python -m pytest tests/unit tests/integration
```

Against an already running explicitly prepared TEST stack:

```powershell
python tools/run_functional_acceptance.py --state-dir C:\state --suite compute-and-analyze --suite typed-compute --suite resume-saved-work --report C:\state\functional.xml
python tools/run_functional_acceptance.py --state-dir C:\state --suite geojson-export --suite geotiff-export --report C:\state\exports.xml
```

Reports require a fresh destination. Export suites require the appropriate genuine
installed component; GeoTIFF requires the corrected exporter and optional
`[throughput]` Rasterio dependency. Missing configuration or failed scientific
validation is a failure, not a skip. The provider-reference suite uses its separate
environment/reference contract from [live acceptance](live-acceptance.md).

For owned stack creation/cleanup, use `run_local_acceptance.py` or
`run_export_acceptance.py`. Both invoke these named suites; the export runner keeps
GeoJSON acceptance separate from optional numerical throughput. Explicit server,
component and worldview provenance remain required.

The exporter-enabled owned runner can additionally select the other local suites
using repeated `--functional-suite compute-and-analyze`,
`--functional-suite resume-saved-work`, `--functional-suite typed-compute` and
`--functional-suite geotiff-export` options. Fixture preparation uses the current
scenario models and an explicitly selected server checkout's compiled Runtime
controls, so an older preparation script cannot silently omit typed scenario models.

Measured repetition uses the **same scenario functions**:

```powershell
python tools/run_workflow_throughput.py --workflow compute-and-analyze --fixture-config C:\state\scientist.json --server-state-dir C:\state --samples 8 --duration 15 --warmup 1 --concurrency 2 --report C:\state\compute-throughput.json
python tools/run_workflow_throughput.py --workflow export-deliverables run --fixture-config C:\state\scientist.json --server-state-dir C:\state --work-dir C:\state --samples 3 --duration 15 --warmup 1 --report C:\state\export-throughput.json
```

Compute supports `full-workflow` and `repeated-read`; numerical export supports
`full-roundtrip` and `repeated-export`. The one-sample GeoTIFF acceptance invokes
the same numerical runner with no warmup and no minimum measurement duration.
Warmups/seed are excluded from throughput; failures invalidate successful rates.
The old `workflow_benchmark.py` and `geotiff_throughput.py` commands are thin
compatibility entry points, not duplicate scenario implementations.

Multi-area batch modelling and richer nonuniform selection/conversion benchmarks
remain proposed additions. They have not been implemented by this reorganization.

## Validation and lifecycle

Shared scenario checks use explicit validation, including under `PYTHONOPTIMIZE=1`.
GeoJSON acceptance applies the same source ID/URN and complete known-rectangle
validator to both files. It rejects holes and crossed/clustered corners while
allowing valid cyclic starting-point changes and reversed winding at the declared
precision, without altering the exported bytes.
GeoJSON acceptance always attempts context and session release independently;
cleanup failures are reported without masking an existing scientific failure.
Provider acceptance preserves its documented diagnostic-retention policy on a
failed experiment and never acquires disposal ownership of a caller-owned context.
The saved-work example likewise retains failed test work/handles for diagnosis and
its explicit owner-directed release mode; it does not silently dispose of restored work.

Read [the authoritative verification record](exports-verification.md) for current
results, historical evidence and development/dependency status outside these tests.
