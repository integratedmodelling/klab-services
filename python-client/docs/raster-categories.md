# Explicit categorical dictionaries over numerical rasters

`RasterCategories`/`categorize_raster` create a **local caller-declared derivative**,
not native categorical metadata, an ontology classifier or a committed revision.
JAVA-GAP-0014 remains open for maintained native block/category/time mappings.

```python
from klab_client import open_raster, RasterCategories, categorize_raster

numerical = open_raster(exported_artifact)
dictionary = RasterCategories(
    ((0, "bare"), (-2, "forest"), (7, "water")),
    "Exact code mapping supplied by the data publisher; verify independently",
)
classified = categorize_raster(numerical, dictionary)
```

Codes stay in `dataset.value`, with their integer dtype and every original value
unchanged. `dataset.valid` retains the mask; zero and negative codes are not missing.
`dataset.category` contains exact labels, with empty string only where `valid` is
false. Always use the mask, not label truthiness, to determine missingness.
The added variable records CALLER_DECLARED and the dictionary provenance. Category
names are not inferred from band order, colour palettes, numerical units or an
unverified server hint. Multiband callers must still supply `RasterBandAxis` when
opening a TIFF; this helper preserves those dimensions without inventing time.

Dictionary input is an immutable tuple of unique exact signed64 integer-code/
nonblank-label tuples, at most256entries and256characters/label, with attributable
meaning. Valid codes absent from the dictionary fail, never coerce to nodata or a
fallback label. Floating-point codes, invalid masks and overwriting an existing
category variable refuse. Exact min/max signed64 controls are tested without
float conversion; out-of-range unsigned codes cannot alias negative categories.

The result deep-copies the local dataset; the source is not mutated. The explicit
eager allocation estimate includes retained source, detached values/masks/coordinates,
Unicode labels and temporary comparison buffers. It is not a hostile-code OS RSS
limit. Analysis extras remain lazy; core SDK import needs no NumPy/xarray/rasterio.
No network access, native retry/fallback, parser, scientific model or simulator
dependency is introduced by classification.

The separate `category-code-export` simulator fixture provides six pinned int16
codes including zero, negative and a masked sentinel. Its observable is explicitly
`synthetic:CategoryCode`, not an elevation measurement or native semantic claim;
units are unknown/absent. Its labels are supplied separately by the caller, not
invented embedded GeoTIFF metadata. Authentic/native scientific fixtures are unchanged.
