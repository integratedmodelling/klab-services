# Explicit caller-declared raster bands

`open_raster` keeps its single-band default. A multiband numerical rectilinear
GeoTIFF requires `RasterBandAxis`; otherwise the SDK refuses to invent scientific
meaning from band order. Band labels and attribution are immutable caller input,
**not** a verified native temporal/history/revision contract (JAVA-GAP-0014).

```python
from klab_client import RasterBandAxis, export_for_analysis, open_raster

artifact = export_for_analysis(observation, "result.tif")
axis = RasterBandAxis(
    "time",
    ("2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z", "2026-01-03T00:00:00Z"),
    "Explicit mapping supplied by the data publisher/operator; verify independently",
)
analysis = open_raster(artifact, band_axis=axis)
assert analysis.dataset.value.dims == ("time", "y", "x")
assert analysis.dataset.value.attrs["band_axis_source"] == "CALLER_DECLARED"
```

Coordinates must be a bounded homogeneous tuple of unique labels or exact finite
numbers and match the TIFF's band count. Labels named `time` are not parsed into
native committed intervals; no timestamp is guessed, no band is reordered and no
slice is selected implicitly. Values retain native dtype; `valid` retains each
band's mask, including zero/negative controls. Different band units, dtype/scales,
palette data and unsafe georeferencing refuse instead of coercing scientific data.
All bands count toward the eager byte budget. The SDK does not gain a simulator
dependency or automatic native-error fallback. Native multiband/time/category/
multishard/revision acceptance remains separate from this local TIFF decoder.

The development-only simulator's `--scenario declared-band-export` supplies a
separately pinned3band×2row×3column float32 example. Its independent manifest is
`developer-tools/klab-service-simulator/src/klab_simulator/fixtures/synthetic-declared-bands.json`.
It explicitly labels the proposed times CALLER_DECLARED and the output
SYNTHETIC_SIMULATOR; the TIFF does not become native Java temporal history because
the caller chooses a `time` axis. Run
`python -m pytest -q developer-tools/klab-service-simulator/tests/test_declared_bands.py`
after installing the ordinary SDK analysis extra and the separate simulator to
exercise owned observation/export/analysis/cleanup over real local HTTP.
