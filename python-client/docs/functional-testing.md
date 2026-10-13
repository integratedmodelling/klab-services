# Functional workflows and test organization

Tests are grouped by execution level; functional suites are named for user outcomes.

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

## Reorganization verification — 13 October 2026

- **573 offline tests** passed on Windows Python 3.12 and fresh installed-package
  Linux Python 3.11/3.13. Existing new-module and changed-code coverage gates passed;
  expanded lint includes all tools. Linux wheel/sdist builds and installed SDK
  source-identity checks passed.
- **Seven local functional tests** passed: GeoJSON acceptance plus the six selected
  computation/lifecycle, recovery, typed and GeoTIFF checks. The two separate
  provider/reference tests require external configured data and were not part of
  this owned synthetic deployment.
- Shared numerical throughput passed **3/3 serial** samples and **12/12 repeated
  exports at concurrency two**, with no scientific failures. Each TIFF preserved
  all 20 value/coordinate associations, source units and nodata mask. All five owned
  JVM exits and session cleanup were confirmed.
- Server `244883d7b`, local corrected component archive SHA-256
  `46175d3ed1f81f90c4ac1ce60774091baff118cd1fbc3e6f61a7f2183ff00482`.
  External evidence: `organized-functional-serial2` and
  `organized-functional-repeated1`, including run reports, JUnit, worker reports
  and unchanged TIFFs. Installed Linux source/test/tool snapshot digest:
  `9fa8ad1cae21` (full digest and per-file hashes in the external matrix reports).
- The initial combined run correctly failed typed scenario validation because the
  older server preparation script omitted `python.scenario.kim` and
  `python.lexical.kim`. The fixture-source selection was corrected; no scientific
  oracle was relaxed and the original failed report remains available.

These checks validate the reorganized workflows; the short, small-field measurements
are not a controlled performance comparison or a production-capacity claim.

## Development outside these tests

- Streamed export SDK (#91): `Client.export_formats`, `export_to_file` and the
  observation convenience method, with bounded-memory transfer, identity checks
  and atomic local publication. Committed/pushed candidate `6b73dd9df`; issue open
  for review/merge. On 13 October the user requested a combined export and
  functional-workflow PR; the original feature checkpoint had no PR.
- Geospatial exporter correction: separate local branch
  `fix/geotiff-orientation-metadata`, correcting north-up value association, NaN
  nodata/mask, source units and temporary-file cleanup. Verified locally, uncommitted.
- Cross-service identity and permissions (#89 / draft #90): preserve caller/context
  identity, independent context access rules and shared-session policy across the
  cooperating services. Separate native Java process checks for nonlocal identity
  groups, broader baseline comparison and server cross-Runtime scope-cache origin
  isolation remain unfinished. Local service checks do not certify cooperation
  between separately deployed installations.
- Typed requests (#85) and saved handles (#86) are merged foundations, not new
  unfinished features. Bulk scientific views, richer catalog/Resolver interfaces
  and provenance are roadmap work rather than active completed implementations.
