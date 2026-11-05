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

Implementation and acceptance are pending at this contract checkpoint.
