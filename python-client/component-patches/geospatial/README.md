# GeoTIFF correction carried by feature/python-client

This branch contains the reviewed Java exporter correction and regression tests as
a reproducible source patch for the existing separate geospatial component.

- Upstream project: `integratedmodelling/klab.component.geospatial`.
- Pinned base: `8d4ef8665016761eb12a504a304f02eb3b55f1c7`.
- Reviewed correction: `a1d36d30bde17d853de71e0cc7d285e597666e4c`.
- Expected reconstructed source tree: `f05b578a14c707d8fd34348e56adc9a96bf25d66`.
- Patch SHA-256: `3100b1fb9c42398edfd00b43909dd7ba80857da5b6778fb8d45e3d58334885cc`.

The patch contains the actual orientation, float64 precision, nodata/mask, Unicode/
GDAL unit metadata and owned-stream cleanup changes, their Java regressions and
optional independent reader. Its source is reviewed in the patch here on the
Python-client branch. The upstream component PR was closed; no external open PR is
needed to build this branch's correction. This does not claim an upstream merge or
a published Maven distribution.

## Build from this branch

Select JDK 21 and have compatible k.LAB host SNAPSHOT artifacts resolved, as for
the standalone component. From `python-client/`:

```powershell
python tools/build_geospatial_component.py --java-home C:\jdk21 --work-dir C:\existing-parent\new-geotiff-build --independent-reader
```

For offline development, an existing component repository supplies Git objects;
it is cloned into fresh external work and is never edited:

```powershell
python tools/build_geospatial_component.py --java-home C:\jdk21 --source-repo C:\component-checkout --offline --work-dir C:\existing-parent\new-geotiff-build --independent-reader
```

`--independent-reader` requires optional `[throughput]` Rasterio, not a new SDK core
dependency. The builder runs the component Maven tests and packaging; reader checks
are optional and explicitly recorded as Passed/Not run. The staged checkout must
match the pinned base, patch hash and exact reviewed source tree before compilation.
The component remains a separate package following `docs/MAVEN.md`, rather than
becoming a new services reactor module.

The output report is `<work-dir>/component-build.json`, containing exact commands,
source identities, tested Java hashes, deployable artifact path and archive SHA-256.
The builder does not install or update any running service.

## Use the built artifact in acceptance

Use the report's artifact path/hash and `<work-dir>/component` as the source checkout:

```powershell
python tools/run_export_acceptance.py --server-repo C:\explicit-compiled-server --java C:\jdk21\bin\java.exe --worldview C:\pinned-imod --component C:\build\component\target\klab.component.geospatial-component-1.0-SNAPSHOT.jar --component-sha256 <report-artifact-sha256> --component-source C:\build\component --state-dir C:\existing-parent\new-test-state --throughput --functional-suite geotiff-export
```

This stages only owned signed TEST state and explicitly pins the local artifact.
Source units, original Job/observation identity, all 20 world-coordinate/value
associations, valid zero and NaN nodata/mask are independently checked. Current
server compatibility limits remain explicit in the acceptance report; server
integration #89/#90 is not completed by this patch.

## Boundaries

DoubleScanner output is float64; float inputs widen without changing values. Exact
Int64 and categorical export remain unsupported through this entry point. No new
ZIP/window/multishard/temporal protocol or Python-side scientific re-encoding is
introduced. The original component review history and reproduction instructions
are part of the patched source tree.
