# Finite reference raster labels (experimental)

`ReferenceRasterLabels` / `AsyncReferenceRasterLabels` require an owned originating
root Context, explicit `allow_experimental=True` and exact Runtime advertisement
`referenceRasterMetadataProtocol="klab.experimental.reference-raster-metadata/1"`.
No silent fallback, semantic inference, source upload or native repair occurs.

```python
from klab_client import ReferenceRasterLabels, export_for_analysis, open_reference_raster

labels = ReferenceRasterLabels(context, allow_experimental=True)
metadata = labels.metadata(observation)
artifact = export_for_analysis(observation, "owned-codes.tif")
result = open_reference_raster(artifact, metadata)  # offline; optional analysis extra
dataset = result.dataset
```

For async clients, await `metadata` and `async_export_for_analysis`; the offline
adapter remains an ordinary local call. Notebook workflows use these public APIs in
an exact-interpreter SDK-only owned kernel; no simulator module enters the kernel.

## Exact meaning

The independent reference service explicitly declares a bounded integer code/label
dictionary, worldview ID/revision/service attribution or band labels/UTC instants
for exact raw GeoTIFF content. It adopts literal fixture labels through this new
protocol; they are **not embedded native TIFF category/time metadata or real ontology
classification**. All evidence is labelled
`SERVER_DECLARED_FINITE_RASTER_LABELS_NOT_NATIVE_SEMANTIC_AUTHORITY`.

The SDK pins current observation/root/Runtime/client/credential, exact source epoch,
raw export SHA256, numeric-array/mask digest, shape/dtype/CRS/transform/units/nodata
and explicit declaration fields. Unknown/duplicate dictionary codes or labels,
Boolean/float category codes, duplicate JSON keys, malformed instants and identity
drift refuse. Returned metadata is immutable; public `to_wire()` returns a new copy.
Server claims/hashes are not cryptographic authentication, scientific truth, permission
authority, committed history or native worldview admission.

The offline adapter verifies bytes and content before associating labels. Integer
codes—including zero, negatives and raw nodata sentinels—remain unchanged; missingness
uses only the existing boolean mask. Unknown valid codes refuse rather than becoming
a default category or nodata. Continuous float bands remain float32/float64; no cast
to categorical integers, value slicing, interpolation, unit conversion or resampling
occurs. Restricted EPSG declarations are compared with local canonical TIFF CRS
identity, not used to reproject coordinates. Unsupported EPSG identities refuse.

Band coordinates are explicitly named `reference_band`; their declared UTC instants
remain separate attribution, not inferred native temporal history. Category/band
array attributes say `SERVER_DECLARED_FINITE_REFERENCE`. Existing caller-only
`RasterCategories`, `categorize_raster` and `RasterBandAxis` APIs retain their original
caller-declared semantics. Their use internally for local associations does not turn
either API into native semantic authority.

## Bounds, ownership and acceptance

Declarations are limited to4096cells,256dictionary entries/band labels, bounded text,
exact UTC instants and65536response bytes. The explicit allocation budget covers
metadata plus retained numeric/source/label arrays through existing adapter checks.
Optional dependency failures are explicit. Offline loading opens only the supplied
local file; no Runtime/Reasoner request is needed after owner release. Changing local
file bytes or supplying foreign metadata refuses, never relabels unrelated data.
Mutating returned arrays cannot modify the raw artifact or subsequent independent loads.

Public fixture workflows verify raw pinned category TIFF integer values/masks, exact
multiband float values, dictionary/worldview and band/time declarations, Bob denial,
owner cleanup and notebook kernel shutdown. These are Python/reference acceptance,
not maintained native dictionary/worldview/time/provider compatibility. Those remain
JAVA-GAP-0014/0006 evidence obligations. No public release, deployment, native service
repair or final original-row acceptance is authorized by this proposal.
