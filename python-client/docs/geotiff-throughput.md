# Numerical export acceptance and throughput

Shared scenario: `tools/workflows/export_deliverables.py`. Named acceptance:
`geotiff-export` via `tools/run_functional_acceptance.py`. The old
`tools/geotiff_throughput.py` command remains a compatibility entry point.

Current results, review findings, artifact hashes and server compatibility are in
the [authoritative verification record](exports-verification.md). The
[original numerical pilots](history/geotiff-throughput-2026-10-13.md) are historical
evidence, not validation of subsequent revisions.

## Workflow and modes

`full-roundtrip` creates an owned explicit-persistence context and typed 5×4
EPSG:3857 Region, resets the explicit TEST field control and computes a coordinate-
coded Aspect. It validates native storage, saves original observation/Job handles,
closes the local client, then starts a **new Python process** to restore the same
authorized work, stream its genuine TIFF and validate it independently with
Rasterio/GDAL. Owned contexts/session are explicitly released.

Full recomputation is serial: the fixture's global 20-cell counter cannot safely
overlap resets. `repeated-export` computes a seed outside measurement, then uses
bounded concurrent fresh-process recovery/export/reader cycles. Each sample has a
unique file/workspace. Verified warmups and seed computation are excluded from rates.

## Scientific reference

Native y increases northward (`GridImpl.getCellBounds`); north-up TIFF rows increase
southward. The independent oracle is `value = 10*x + y`, with valid zero at `(0,0)`
and one missing cell at `(2,1)`. Required top row: `[3,13,23,33,43]`; bottom row:
`[0,10,20,30,40]`; missing pixel: row 2/column 2.

The reader checks every geographic value association and cell center, one band,
EPSG:3857, bounds `(200000,6000000,200500,6000400)`, affine transform
`(100,0,200000,0,-100,6000400)`, float32/float64 representation, valid zero, NaN nodata,
matching mask and the source measurement unit. These integral fixture values are
exactly representable. Revision tags are not invented from spatial CRS metadata;
no client-side flip/re-encoding or relaxed scientific oracle is used.

## Run

Rasterio remains optional for the SDK:

```powershell
python -m pip install '.[dev,local,throughput]'
python tools/run_export_acceptance.py --server-repo C:\explicit-server-checkout --java C:\jdk\bin\java.exe --worldview C:\pinned-imod --component C:\corrected-component.jar --component-sha256 <exact-archive-hash> --component-source C:\component-source --state-dir C:\existing-parent\new-state --throughput --samples 3 --duration 15 --warmup 1
```

The numerical suite requires the separately corrected component. The default
published archive remains suitable for its verified GeoJSON capability but fails
the numerical oracle. A local archive requires an explicit hash and source checkout;
the runner records it as `local-source-build`. Server fixtures/classpaths must be
compiled in the explicitly selected checkout. The owned runner prepares the current
scenario models and verifies stack cleanup on success/failure.

Against an already prepared authorized fixture:

```powershell
python tools/run_functional_acceptance.py --state-dir C:\state --suite geotiff-export --report C:\state\numerical.xml
python tools/run_workflow_throughput.py --workflow export-deliverables run --fixture-config C:\state\scientist.json --server-state-dir C:\state --work-dir C:\state --report C:\state\serial.json --samples 3 --duration 15 --warmup 1
python tools/run_workflow_throughput.py --workflow export-deliverables run --fixture-config C:\state\scientist.json --server-state-dir C:\state --work-dir C:\state --report C:\state\repeated.json --mode repeated-export --concurrency 2 --samples 4 --duration 15 --warmup 1
```

Reports require a fresh path. Tokens come from the private generated configuration,
not command-line arguments or report fields. Any failed sample stops submissions,
drains active work and invalidates successful rates; cleanup failure fails acceptance.
Rates divide verified workflows/cells/bytes by actual workload wall time, including
fresh-process startup and reading. Short, small-field figures are neither network
bandwidth nor production capacity or robust tail-latency evidence.

## Ownership

- Exporter orientation/metadata regressions belong in
  `klab.component.geospatial/src/test/java/org/integratedmodelling/geospatial/utils/GeotiffExportTest.java`.
- Whole Python workflows belong in `tools/workflows/export_deliverables.py`.
- Offline scientific/rate guards belong in `tests/unit/test_geotiff_throughput.py`.
