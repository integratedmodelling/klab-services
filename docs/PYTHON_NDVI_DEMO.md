# Python-backed NDVI storage demonstration

## What this delivers

A runnable environmental-computation example that moves beyond doubling arrays:

**k.LAB red/NIR storage scanners -> real Python NDVI calculation -> k.LAB output
storage -> verified readback and CSV.**

NDVI (Normalized Difference Vegetation Index) is a dimensionless remote-sensing index:

```text
NDVI = (near-infrared reflectance - red reflectance)
       / (near-infrared reflectance + red reflectance)
```

Vegetation commonly reflects more near-infrared than red light, giving positive NDVI.
The index is relevant to ecological and land-surface computations; these six synthetic
cells do not establish vegetation classes, vegetation health, or a scientific model's validity.

## Test organization

This scenario is part of the [testing guide](TESTING.md#python-backed-ndvi-integration-scenario).
The JUnit cases live in `klab.core.services/src/test/java` alongside the existing
computation/storage tests; the Python worker and independent pytest cases live in
`src/test/resources/python-bridge` in the same module.

| JUnit case | Expected outcome |
| --- | --- |
| `computesVegetationIndexThroughStorageAndExportsCsv` | Six expected cell values read back through a fresh storage scanner; CSV generated after value assertions |
| `rejectsMisalignedGridsBeforeWriting` | Shifted input grid rejected before computation writes |
| `invalidReflectanceDoesNotWritePartialOutput` | Invalid reflectance rejected and all output values remain unchanged |

The CSV is a diagnostic artifact under `target`, not a committed test fixture. Open
`klab.core.services/target/ndvi-demo/ndvi.csv` after the successful run to inspect it.

## CLI

Use the Python environment and Java 21 setup from [the bridge runbook](PYTHON_BRIDGE.md).
From the repository root in PowerShell, after that setup:

```powershell
$env:KLAB_PYTHON_EXECUTABLE = (Resolve-Path .\.venv\Scripts\python.exe).Path
.\mvnw.cmd -pl klab.core.services -am "-Dtest=PythonNdviDemoIT" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Unix, with JDK 21 and `KLAB_PYTHON_EXECUTABLE` configured:

```sh
./mvnw -pl klab.core.services -am '-Dtest=PythonNdviDemoIT' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

To experiment with Python directly, without Java or storage:

```powershell
'{"protocol_version":1,"operation":"ndvi","red":[0.2,0.3,0.4,null,0,0.1],"nir":[0.8,0.3,0.1,0.5,0,0.9]}' |
    & $env:KLAB_PYTHON_EXECUTABLE .\klab.core.services\src\test\resources\python-bridge\ndvi_worker.py
```

The response uses the existing version-1 envelope, with `values` containing NDVI.
Run all independent Python checks with:

```powershell
& $env:KLAB_PYTHON_EXECUTABLE -m pytest -v klab.core.services/src/test/resources/python-bridge
```

## Expected result

The demonstration uses a synthetic 3-by-2 EPSG:4326 grid, one initialization slice,
one DOUBLE shard per band, and `D2_YX` traversal (x varies fastest).
`x` and `y` in the CSV are cell indices, not longitude/latitude coordinates.

| x | y | Red | NIR | NDVI (rounded) |
| ---: | ---: | ---: | ---: | ---: |
| 0 | 0 | 0.2 | 0.8 | 0.6 |
| 1 | 0 | 0.3 | 0.3 | 0 |
| 2 | 0 | 0.4 | 0.1 | -0.6 |
| 0 | 1 | missing | 0.5 | missing |
| 1 | 1 | 0 | 0 | missing |
| 2 | 1 | 0.1 | 0.9 | 0.8 |

The CSV preserves ordinary floating-point precision (e.g. `0.6000000000000001`).
Java assertions compare numerical results with tolerance `1e-12`.

## Data contract

- Inputs are **aligned, unitless surface reflectance in [0, 1]**, not raw satellite digital numbers.
  Sensor scale/offset correction and cloud/quality masks must be applied upstream.
  This deliberately restrictive demonstration rejects negative or greater-than-one values;
  it is not a universal satellite-product validity policy.
- Both bands must have equal lengths. Boolean, string and non-finite values fail.
- Missing values in storage are `Double.NaN`, transported as strict JSON `null` and
  written back as `Double.NaN`. Infinity is rejected, not converted to missing data.
- A missing band or a zero denominator produces missing output.
- Output is dimensionless and within [-1, 1].
- The Java helper accepts fresh native scanners with matching geometry and sharding
  strategies, at most 256 cells. The caller owns the scanners and finalizes output.
  Planned/reprojected/multi-shard cursors are outside this example.
- The complete response is validated before any returned values are added to the output.
  This is not a transaction/rollback guarantee: allocating a writable scanner already
  affects storage bookkeeping, and storage-write failures are not rolled back here.

## What is real and what is still a fixture

`PythonNdviDemoIT` constructs real `StorageImpl` instances, real numeric buffers,
geometry and real read/write scanners. It launches the configured Python interpreter,
calls `finalizeRun`, and verifies results through a new storage read cursor.
Service scopes, the storage manager and persistence infrastructure are mocked, following
the existing storage-test conventions. Observation descriptors use synthetic concepts
and transient IDs; there is no worldview or digital-twin commit.

The helper and worker remain test-scoped and opt-in (`IT` suffix). This is a runnable
adapter demonstration at the storage boundary, **not a registered production adapter**.
There is no k.IM model that selects it, remote-sensing ingestion, reprojection, tiling
scheduler, or service deployment. It does not create a durable k.LAB observation.

## Source walkthrough

- `klab.core.services/src/test/resources/python-bridge/ndvi_worker.py`: NDVI and strict protocol.
- Same folder, `test_ndvi_worker.py`: independent scientific/validation/CLI checks.
- `klab.core.services/src/test/java/org/integratedmodelling/klab/runtime/computation/PythonNdviDemo.java`:
  scanner-to-JSON conversion, Python invocation, full-response validation, scanner writes.
- Adjacent `PythonNdviDemoIT.java`: real storage fixture, readback/CSV, spatial mismatch and
  invalid-reflectance checks. Edit its `RED`/`NIR` arrays and corresponding expected values
  to make a different synthetic example.

## Next deliverable

The [bounded scalar prototype and integration findings](PYTHON_RUNTIME_PROTOTYPE.md)
extend this example with batching and cancellation. A real-buffer failure test shows
why initialization needs an atomic output lifecycle before production integration.

Agree with maintainers whether Python belongs in a resource adapter or a computation
extension. Then replace the fixture's direct call with that registered execution path,
including dependency binding, units, scanner lifetime, cancellation and output ownership.
Use this NDVI example as its acceptance scenario. A production adapter needs that step;
changing the scientific formula alone does not supply it.
