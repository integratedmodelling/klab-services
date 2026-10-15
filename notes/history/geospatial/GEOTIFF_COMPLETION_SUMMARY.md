# GeoTIFF work completed — summary for Eric

**Local finish time:** October 14, 2026 at **7:55:50 AM Eastern Daylight Time**
(`2026-10-14 07:55:50 -04:00`, captured from the local Windows system clock).

## Result

**Independent review passed: Ready for maintainer review.** The reviewer found no
demonstrated blocking or important defect remaining in the bounded contribution.
This is not official maintainer acceptance or a publication/merge decision.

**Current branch integration:** the user selected a buildable patch, and the full
reviewed correction is now carried on
`integratedmodelling/klab-services:feature/python-client` at commit
`9c7c371539ff31a05238cfc7355395ca05f21ace`. Files are under
`python-client/component-patches/geospatial`, with
`python-client/tools/build_geospatial_component.py` reconstructing the exact reviewed
component source and producing its deployable archive. This preserves the existing
separate-component Maven layout; it does not claim a geospatial upstream-main merge.

Branch integration verification: 634 Python offline tests/gates/lint passed; the
reconstructed exact source tree ran 22 Java tests and eight independent TIFF checks;
fresh live acceptance and 3/3 serial samples passed with cleanup confirmed. Hosted
Python checks and Maven packaging are green at the branch integration commit.

- Checkout: `C:\Users\lumsd\Downloads\k1\klab-component-geospatial`.
- New branch: `fix/geotiff-scientific-export`.
- Target/base: `main`, `8d4ef8665016761eb12a504a304f02eb3b55f1c7`.
- Existing orientation/metadata work was preserved and refined on the new branch.
- At the original completion checkpoint the changes were local and uncommitted.
  After later authorization they were committed as
  `a1d36d30bde17d853de71e0cc7d285e597666e4c` and pushed to
  `Dobbes/klab.component.geospatial:fix/geotiff-scientific-export`.
  Upstream component PR #9 was subsequently **closed without merging**, following
  the user's requirement to keep open contributions scoped to `feature/python-client`.
  The reviewed code is preserved in the clean local checkout and fork; no upstream
  component merge or distribution publication occurred.

## What changed

1. **Correct geographic value association:** native y increases northward; raster
   rows now use `height - 1 - nativeY`.
2. **Precision:** DoubleScanner GeoTIFF output is float64, preserving values that
   float32 cannot represent. Float32 input values widen without being changed. PNG
   continues to request its existing float32 representation.
3. **Missingness:** valid zero remains valid; NaN is explicitly encoded as nodata,
   with the reader's corresponding missing-cell mask.
4. **Source units:** Unicode uses ASCII-safe XML references; reserved punctuation
   and literal entity-looking text follow GDAL's documented double-escaping convention.
   Units are never derived from the spatial CRS. Unsupported provided unit definitions
   fail explicitly rather than being silently omitted.
5. **Lifecycle:** owned temporary files are removed on stream close and generation
   failure. A secondary delete error is suppressed onto the primary close IOException.
6. **Scientific regressions:** ordinary Java checks cover precision, metadata,
   geographic axes, shifted origins and rotated/sheared georeferencing; the optional
   `tools/check_geotiff_reference.py` independently reads real Java-written files.

Int64 exact export needs a separate integer scanner contract and is explicitly
rejected. Categorical, ZIP, new window/multishard/temporal protocols are outside this
bounded registered plain numeric exporter correction. Server-side raster memory
remains proportional to cell count; no constant-memory generation claim is made.

## Validation completed

| Check | Result |
|---|---|
| Full component Maven suite | **22 passed**, zero failures/errors/skips |
| Genuine component package build | **Passed** |
| Independent Rasterio 1.5.2/GDAL 3.12.2 reader | **Eight actual written fixtures passed**, all 160 cells, positions, masks, units, CRS/affine and dtype checked |
| Corrupted-file reader control under `python -O` | **Correctly rejected**; original evidence untouched |
| Rebuilt-component GeoJSON and one-sample GeoTIFF functional acceptance | **Passed in both owned environments** |
| Fresh-process serial recomputation/export | **4/4 verified**, zero failures |
| Repeated fresh-process export, concurrency 2 | **14/14 verified**, zero failures |
| Owned sessions, five JVMs and authority cleanup | **Confirmed**, cleanup exit 0 |
| Python tool lint and tracked patch whitespace | **Passed** |

Cases include EPSG:3857 float32/float64, EPSG:4326 latitude-/longitude-first CRSs,
nonzero image origin, rotation/shear, Unicode/reserved punctuation/literal entities,
valid zero and one missing cell. The precision fixture preserves `16777217.125`.

Live client: merged Python integration `a11462ddcf7aa0e2643c1f44faad95e8dd9fed36`.
Explicit compatible server: `244883d7b04f1eae2cc22e799575d33a9bdb2a43`.
These are small synthetic reference workflows; no provider-accuracy, capacity or
general separate-deployment certification is claimed.

## Artifact and review evidence

- Genuine local artifact:
  `klab-component-geospatial/target/klab.component.geospatial-component-1.0-SNAPSHOT.jar`.
- SHA-256:
  `c38628db17df9356f8febcfeff4c671e66bc8bca348aad58676e8a1d9c5fe810`.
- Maintainer-facing record: `klab-component-geospatial/docs/GEOTIFF_REVIEW.md`.
- The independent reviewer verified all 31 Java source hashes against final tests,
  package and both live reports; verified the current archive hash; rechecked the
  eight fixture files and all 22 retained live TIFFs without changing evidence.
- Review followed `C:\Users\lumsd\Downloads\KLAB_CLI_Contribution_Review.md` and repeated
  until the current evidence gaps and material defects were resolved.

External detailed evidence under `C:\Users\lumsd\AppData\Local\Temp\opencode`:
`geotiff-tests-final1`, `geotiff-package-final1`,
`geotiff-final-live-serial1`, `geotiff-final-live-repeated1`,
and `geotiff-reader-negative-final1`. Earlier failing tests remain retained.

## Your review entry points

Start with `docs/GEOTIFF_REVIEW.md` and `git diff` on the new component branch, then:

- `src/main/java/org/integratedmodelling/geospatial/utils/Geotools.java`
- `src/main/java/org/integratedmodelling/geospatial/utils/NativeGeoTiffWriter.java`
- `src/main/java/org/integratedmodelling/geospatial/library/GeodataIO.java`
- `src/test/java/org/integratedmodelling/geospatial/utils/GeotiffExportTest.java`
- `src/test/java/org/integratedmodelling/geospatial/library/GeotiffStreamTest.java`
- `tools/check_geotiff_reference.py` and README reproduction commands.

The component remains separately preserved, outside the current open Python-client
contributions. Future upstream delivery would require renewed authorization and
appropriate repository access. The old published TIFF artifact is not relabelled
as corrected. Current open work is klab-services PR #90 (target `feature/python-client`)
and its repository-level tracking issue #89.
