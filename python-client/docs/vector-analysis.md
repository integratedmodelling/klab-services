# Bounded native GeoJSON analysis

## Q3 contract — before implementation

Base `597ecb0aed8dddf926e001d635b9552c5d22ddf8` includes accepted PR203/201/202.
Existing `export_for_analysis(..., media_type="application/geo+json")` saves native
bytes, but `open_raster` does not interpret vectors. This increment adds an
optional local GeoJSON FeatureCollection adapter, not an exporter or model engine.

Required: preserve feature order, integer/string IDs (including absence), nested
properties, explicit null/false/zero, polygon holes and multiparts. Keep original
feature records alongside an optional GeoDataFrame so missing properties and null
are distinguishable. RFC7946 longitude/latitude coordinates only; reject legacy
CRS declarations, invalid/nonfinite coordinates, duplicate JSON keys, malformed
geometry, and budget overruns. Never reproject, repair topology, infer categories/
units/area, or claim artifact identity is a server revision. Local reads only;
optional dependencies remain absent from core import.

Tests: independent multipart/hole/null/property/ID fixtures; malformed, unknown,
invalid CRS/geometry, oversized and tampered artifacts; missing optional extras;
fresh installed actual Java native rectangle export compared with independently
calculated geographic corners. Current Java exporter is pinned, not republished;
synthetic geometry is not historical study replication. Large-provider/soak and
arbitrary GeoJSON interoperability are not claimed.

## Use

Install `klab-python-client[vector]`. Core imports do not load GeoPandas/Shapely.

```python
from klab_client import export_for_analysis, open_vector

artifact = export_for_analysis(observation, "native.geojson", media_type="application/geo+json")
vectors = open_vector(artifact)
frame = vectors.dataframe
print(vectors.sha256, vectors.geometry_column, frame.crs)
print(vectors.features[0])  # immutable original record, including original ID
```

`open_vector` also accepts a local file path. Defaults cap input at16MiB,
10,000 features,100,000 positions and1,000,000 dense dataframe cells. Configurable
hard maxima are256MiB/100,000 features/1,000,000 positions/1,000,000 table cells.
These are eager-allocation guards, **not total process-RSS guarantees**. A feature
with null geometry remains null. Empty coordinate geometries, mixed dimensions
within one geometry, unclosed rings and invalid topology fail without repair.
Explicit2D/3D coordinates are preserved; no vertical datum/height units inferred.
Coordinates use GEOS doubles. Integer ordinates with magnitude above2**53 are
explicitly unsupported rather than silently rounded; original property/feature
ID integers are not subject to that geometry-storage restriction.
Topology checks use GEOS's planar representation. There is no antimeridian splitting,
geodesic interpretation, datum transformation or area calculation; callers must not
interpret longitude/latitude degree areas as scientific ground-area measurements.

IDs, feature order and every source property are preserved in `features`; duplicate
IDs are not deduplicated. Dataframe properties use object dtype; absent and null
properties both display as None there, but remain distinct in immutable records.
The active geometry column gets a noncolliding name; no source property is replaced.
No dataframe index or property is promoted to a k.LAB identity or class dictionary.

The `vector-analysis` owned reliability scenario uses actual advertised Java native
GeoJSON and compares its original rectangle against independent Mercator-to-lon/lat
corners. Multipart/hole/null contracts are independently specified local fixtures,
not a claim that this live rectangle exercises arbitrary Java multipart producers.
Verified2026-11-05 atf62f6bd42:2,943installedtests/17gates/lint/sourceparity and
the actualJava native rectangle workflow PASS. Native digest
`e55d912527a0956e403fb448cf34e7be92b8fe6d833058599b390ac9f08d895d`;
ownedprocess/listenercleanupPASS,unrelatedJavaunchanged. Normal hosted/merge and
upstreamrelease/support remain separate gates. See author/CLI
[contributor review](reviews/vector-analysis.md).
