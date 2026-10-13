# Historical numerical export verification — through 13 October 2026

This preserves the original pilots, component provenance and pre-remediation
measurements. Current results/recommendations are in
[the authoritative record](../exports-verification.md); commands and the scientific
contract are in [the numerical guide](../geotiff-throughput.md).

The shared scenario now lives in `tools/workflows/export_deliverables.py`.
Named acceptance is `geotiff-export` via `tools/run_functional_acceptance.py`;
the original `tools/geotiff_throughput.py` command remains a compatibility entry
point. See [functional testing](../functional-testing.md).

This is a development-only integration test of the closest full numerical
workflow, not a scientific adapter or change to the stored/exported algorithm.
It uses the existing typed observation, handles and streaming APIs with a genuine
installed numeric GeoTIFF exporter and an optional independent Rasterio/GDAL reader.

## Workflow and modes

`full-roundtrip` measures:

1. Create an owned explicit-persistence context and typed 5×4 EPSG:3857 Region.
2. Reset the explicit TEST field control and compute the coordinate-coded Aspect.
3. Verify native storage values, valid zero and one missing cell; save original
   observation and Job references and close the local client.
4. Start a **new Python process**, restore current authorized work, verify the
   same Job/result/source unit and native readback, discover and stream GeoTIFF.
5. Read the actual file independently; validate world-coordinate mapping, shape,
   CRS, bounds, numeric dtype, every value, zero and NaN/mask behavior.
6. Explicitly release only the sample's owned context; release the personal
   benchmark session after all workers drain, including on failure.

Full recomputation is intentionally **serial**: the explicit fixture uses one
global 20-cell counter. Accepting overlapping resets would make the scientific
oracle invalid. This test does not invent parallel scientific data to produce
better figures.

`repeated-export` computes a seed once outside the measured interval, then uses
bounded concurrency for fresh-process restore/export/reader cycles. Every sample
gets an isolated directory/file. It measures downstream recovery/export throughput,
not fresh computation. Warmups are verified and excluded from measured rates.

## Run

Core SDK dependencies are unchanged. The independent reader is opt-in:

```powershell
python -m pip install '.[dev,local,throughput]'
python tools/run_export_acceptance.py --server-repo C:\explicit-server-checkout --java C:\jdk\bin\java.exe --worldview C:\pinned-imod --component C:\pinned-geospatial-component.kar --state-dir C:\existing-parent\new-throughput-state --throughput --samples 3 --duration 15 --warmup 1
```

The owned runner first performs the existing GeoJSON acceptance, then the
numerical workflow, and confirms all test-owned JVM exits on success or failure.
By default it pins the published component documented in
[stream exports](../stream-exports.md). To test a corrected local archive, additionally
supply `--component-sha256 <exact-archive-hash> --component-source <source-checkout>`.
The runner labels it `local-source-build` and records the base commit, dirty-tree
status and per-Java-source hashes; it does not relabel it as the published artifact.

Against an already prepared authorized fixture:

```powershell
python tools/geotiff_throughput.py run --fixture-config C:\state\scientist.json --server-state-dir C:\state --work-dir C:\state --report C:\state\full-roundtrip.json --samples 3 --duration 15 --warmup 1
python tools/geotiff_throughput.py run --fixture-config C:\state\scientist.json --server-state-dir C:\state --work-dir C:\state --report C:\state\repeated-export.json --mode repeated-export --concurrency 2 --samples 4 --duration 15 --warmup 1
```

Reports use unique sample workspaces and an explicit new report path. Tokens are
read only from the generated private configuration, not command-line arguments
or report fields. A failed sample stops new submissions, drains active workers
and invalidates successful workflow/cell/byte rates. Cleanup failure also fails
acceptance. An attempted transfer duration may be reported without a successful
throughput figure.

## Scientific reference and rate meaning

The native grid's y increases **northward**, as specified by GridImpl.getCellBounds.
The 20-cell XY fixture has `value = 10*x + y`, valid zero at `(0,0)` and missingness
at `(2,1)`. A north-up GeoTIFF's row increases **southward**. Therefore the expected
top row is `[3,13,23,33,43]`, bottom row `[0,10,20,30,40]`, and the missing pixel is
row 2/column 2. Checking only array shape or raw ordinal values would miss an
incorrect georeferencing/value association.

The independent reader checks affine transform `(100,0,200000,0,-100,6000400)`,
EPSG:3857, bounds, cell centers and float32/float64 representation. This small
integral-valued fixture is exactly representable; no unexplained numeric tolerance
or client-side flipping/re-encoding is used. The corrected-exporter contract
requires measurement units matching the source, explicit NaN nodata and a mask
matching every cell. Absent revision tags are recorded, not inferred from the
spatial CRS. Earlier pilots recorded absent nodata/unit tags separately; they
already failed orientation, and the strengthened acceptance also rejects those
metadata omissions.

Rates are verified workflows/cells/exported bytes divided by the **actual workload
wall interval**. Stage times, fresh-process wall time, original IDs, file hash,
reader versions and fixture/source provenance are retained. These are short,
20-cell workflow measurements, not network bandwidth, maximum capacity or robust
tail-latency claims. RasterIO imports/process startup are included in the measured
fresh-process cycle.

## First genuine result: scientific failure, no successful throughput

The first full pilot reached native computation/readback, fresh-process recovery
and actual GeoTIFF transfer. Independent Rasterio 1.5.2 / GDAL 3.12.2 inspection
found **vertically inverted value association** in the pinned official component
`1.0-20260926.083046-222` (archive SHA-256
`e7d470f69943e93ce48e9669bad6a41b618dfbe0e7bc748076e010583daef644`), against server
`244883d7b04f1eae2cc22e799575d33a9bdb2a43`.

- Shape, EPSG:3857 and north-up transform were correct.
- World position `(200050,6000350)` held **0.0**, while the native/reference
  north-west cell must be **3.0**.
- File rows were `[0,10,20,30,40]` through `[3,13,23,33,43]`, the reverse of the
  required geographic association. The missing cell was row 1 rather than row 2.
- The file contained a NaN but supplied no nodata or measurement-unit tag; all
  dataset-mask entries were valid. These omissions are reported separately.
- Result: **0 verified / 1 failed**, roughly 10.41 seconds for the attempted
  full workflow. No successful throughput rates were emitted. Session and all
  five owned JVMs were cleaned.

The exporter source's `Geotools.stateToCoverage` writes native xy offsets directly
to raster row indices; this is a source-inspection lead consistent with the
reproduced result. The test deliberately does not
flip client bytes, loosen the oracle or call successful HTTP export scientifically
correct. That published archive remains failing; the corrected local component
verification is recorded below. GeoJSON acceptance is a separate capability.

External initial evidence: `geotiff-throughput-pilot/geotiff-throughput.json`,
its worker report and unchanged actual `field.tiff`. Deterministic tests reject
flipped rasters, lost zero/missingness, wrong masks/coordinates/dtypes and suppress
all successful rates when any sample fails.

The bounded-concurrency repeated-export pilot reproduced the same defect in two
different fresh processes, both restoring original observation 26 / Job 3. Both
500-byte files had SHA-256
`5e77f719d7f009fd8700bdc4b67b3fe66d52a5d97fb6b7cfab077c95428249d9`.
Result: **0 verified / 2 failed**, approximately 3.29 seconds measured workload
wall time, again with no successful rates. Seed computation/native validation
passed outside measurement; session and all five owned JVMs were cleaned.
Evidence: `geotiff-throughput-repeated-pilot/geotiff-throughput.json`.

Implementation checks passed **563 maintained tests** (8 existing live tests
selected separately), the existing export line/branch/changed-code gates and
targeted lint. The 18 focused benchmark checks include the earlier workload
queue/rate tests. These passing deterministic checks validate the test's guards;
they do not turn the failed real numerical workflow into passing acceptance.

## Corrected local exporter: verified result

On 12 October 2026, branch `fix/geotiff-orientation-metadata` in the separate
`klab.component.geospatial` checkout corrected native XY-to-image rows, propagated
coverage nodata, explicitly encoded NaN nodata and source band units, and cleaned
temporary export files on stream closure/failure. Its obsolete annotation on the
unimplemented categorical exporter was removed to build against the current host
API; categorical export remains unregistered.

Artifact provenance:

- Geospatial base: `8d4ef8665016761eb12a504a304f02eb3b55f1c7`, dirty local correction.
- Genuine Maven-produced archive:
  `target/klab.component.geospatial-component-1.0-SNAPSHOT.jar`.
- Archive SHA-256:
  `46175d3ed1f81f90c4ac1ce60774091baff118cd1fbc3e6f61a7f2183ff00482`.
- Server: `244883d7b04f1eae2cc22e799575d33a9bdb2a43`, clean.
- Python base: `6b73dd9dfaf173e9f9603bf155bbca851d59b480`, dirty throughput tooling;
  core SDK export code unchanged.
- Windows, JDK 21.0.12.1+1, Python 3.12.7, Rasterio 1.5.2 / GDAL 3.12.2.
- Same pinned worldview/model/native field as the original pilots. Reports retain
  exact source hashes and original observation/Job IDs.

Both runs first passed GeoJSON acceptance. Each had one verified warmup excluded
from measurement; repeated export also excluded seed computation. The configured
15-second interval drains complete samples, so actual wall intervals are longer:

| Mode | Concurrency | Verified / failed | Wall seconds | Workflows/s | Verified cells/s |
|---|---:|---:|---:|---:|---:|
| Full recomputation + fresh-process export | 1 | 3 / 0 | 16.485 | 0.182 | 3.640 |
| Seeded repeated fresh-process export | 2 | 14 / 0 | 16.427 | 0.852 | 17.045 |

Every actual TIFF was 622 bytes, SHA-256
`f867a405b9d7aa133fef61233ea3d166e6a6b7ecbeefef576848439636b4a2d9`.
The independent reader confirmed the north-west value **3.0**, unchanged affine
transform/EPSG:3857, all 20 geographic value associations, valid zero, one NaN,
NaN nodata, the correct validity mask and `degree_angle` band unit. Session cleanup
passed and all five owned JVM exits were confirmed in both runs.

These are short 20-cell synthetic workflow measurements including process startup
and independent reading, not bulk-transfer bandwidth or production capacity.
The rate difference is between two different modes, not a controlled before/after
optimization claim. No successful rate is attributed to the old published archive.

External evidence under the isolated test workspace:
`geotiff-corrected-serial1/{run-report.json,geotiff-throughput.json}` and
`geotiff-corrected-repeated1/{run-report.json,geotiff-throughput.json}`, their logs,
worker records and untouched TIFFs. The full component `mvn test` passed all **10**
tests; Python checks passed **566** maintained tests and targeted lint/diff checks.

## Permanent test ownership

- **Exporter regression:**
  `klab.component.geospatial/src/test/java/org/integratedmodelling/geospatial/utils/GeotiffExportTest.java`.
  Run under normal `mvn test`; world-coordinate orientation and TIFF metadata
  failures belong to the exporter owner, alongside existing renderer tests.
- **Whole Python workflow / throughput:**
  `klab-services/python-client/tools/workflows/export_deliverables.py`, invoked through
  `tools/run_export_acceptance.py` against an explicitly pinned real component.
- **Deterministic acceptance/rate guards:**
  `klab-services/python-client/tests/unit/test_geotiff_throughput.py`, in normal offline
  Python CI. Rasterio remains an optional throughput dependency.

The Python tooling is included in the export/functional-workflow review branch.
The component correction remains a separate local, uncommitted change.
