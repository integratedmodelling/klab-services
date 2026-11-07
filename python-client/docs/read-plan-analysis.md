# Offline arrays with explicit reference provenance

`analyze_read_plan(snapshot)` turns a **complete** `ReadPlanData` into a detached
NumPy/xarray numerical dataset. It makes no network request, acquires no grant and
does not need a live Runtime, context or read plan. Analysis extras are lazy.

```python
from klab_client import analyze_read_plan

analysis = analyze_read_plan(complete_snapshot)
values = analysis.dataset.value
valid = analysis.dataset.valid
```

Before arrays are allocated, the helper validates bounded immutable shape/affine/
shard descriptors, exact source observation handle, contiguous full layout, values/
mask counts and every shard+whole-content digest. Partial/mixed/tampered numerical
buffers, unknown ordering, false masks over nonmissing values and nonfinite values
refuse. Invalid reference cells become NaN in float64arrays, remain None in the
immutable source and keep false boolean masks. Valid zero/negative values remain
unchanged. Rectilinear north-to-south centre coordinates are calculated explicitly;
indistinguishable float64centres refuse. No resampling, reprojection, unit conversion,
time/history/category inference or native storage dtype claim is made.

Dataset attributes retain source Runtime URL/ID, root scope, observation ID, plan ID,
epoch, fixture origin, reference content revision, CRS, affine, order, units and
consistency. Revision kind stays **REFERENCE_CONTENT_SHA256_NOT_NATIVE_COMMIT**.
A separate local canonical manifest SHA256 records the proposed descriptor plus
source/plan/epoch/shard specification. It is labelled
**LOCAL_REFERENCE_DESCRIPTOR_NOT_NATIVE_COMMIT**: not a server signature, proof of
authentic native metadata, native provenance graph or committed scientific history.
The immutable input handle/manifest came from the explicit proposed protocol;
manually constructing/replacing local objects cannot create native trust. Value
content hashes alone do not authenticate metadata. These distinctions are retained
even after scope release and service shutdown.

The local dataset is mutable and detached; changing it does not modify source tuples
or the service. Attributes describe its original materialization, NOT a live hash
of subsequent caller edits. To attest a derivative, callers must record their own
algorithm/parameters/output digest; a local manifest hash is not that attestation.
This result deliberately is not an `AnalysisArtifact`/`RasterAnalysis` native TIFF
export receipt, and does not silently pass itself off as `trace_analysis` native
provenance evidence. Numerical reference arrays do not fix geospatial#10 or Java's
historical null Storage.Scanner pipeline defect.

Bounds:4096cells/4096shard records and128cells per shard; budget estimate includes
retained tuples, numerical/mask/coordinate arrays, per-shard validation structures
and descriptor overhead. It is an eager local estimate, not a total process RSS or
hostile-code isolation guarantee. Core import requires no NumPy/xarray/rasterio.
JAVA-GAP-0006/0014 remain externally gated for native layout/revision/lease contracts;
this is explicit reference progress, not native scientific acceptance.
