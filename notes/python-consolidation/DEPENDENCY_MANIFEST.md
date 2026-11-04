# Dependency audit — initial baseline, not final certification

Decision: **TARGETED_UPDATE_RECOMMENDED** (verification in progress).

| Layer | Observed baseline | Interpretation |
|---|---|---|
| Services delivery | `e09a7de7cac6b48526fd8b7ac72879abb36a77a3` | Exact initial candidate, before integration |
| Services root | GeoTools 34.4, JTS 1.20.0, Jackson 2.19.0 declared | Effective reactor POM and per-module dependency trees captured |
| Local geospatial | `a1d36d30bde17d853de71e0cc7d285e597666e4c` | Historical corrected-exporter branch, not published release |
| Geospatial upstream | `3f98082db59e4b0b82eecff0e5753eec41fed671` | Freshly fetched main; still declares HM 0.11.3-SNAPSHOT |
| Geospatial resolved | hm-gears / hm-dbs 0.11.3-SNAPSHOT, GeoTools 34.4 | Resolved offline, not merely inferred from POM |
| HM 0.12.0 release POM | GeoTools **34.6**, JTS 1.20.0, Jackson 2.19.0, H2GIS 1.5.0, H2 1.4.197 | Actual Maven Central parent; no 35.x adoption |
| Existing geospatial H2 | **1.1.119** managed from H2GIS-requested 1.4.197 | Must not claim H2/H2GIS compatibility from resolution alone |
| Existing service H2 | Module-dependent 1.4.197 / 1.1.119 paths | Per-module resolver/packaging/load inspection remains mandatory |
| Existing Jackson | Most services 2.19.0; other module paths resolve 2.18.3/2.20.0 | Reactor-wide single-version claim would be false |
| Running Java | No Java process visible at baseline | Actually-loaded versions NOT_RUN, not equal to Maven resolution |

## Resolver-only experiment

On the unchanged local component SHA, an explicit command-line override
`-Dhortonmachine.version=0.12.0` resolved successfully. This is **not** an adopted
POM change, compile/packaging test, compatibility proof or running deployment.
The component's GeoTools BOM can override HM's 34.6 baseline to 34.4; examine the
resolved tree before choosing a reviewed candidate. No silent snapshot update or
35.x migration is authorized.

The separate component PR #9 was closed unmerged at the contributor's request;
its preserved exporter work is not an accepted/published component. Do not reopen
it or publish binaries automatically. A dependency proposal must respect repository
permissions and document whether it targets services, the component, or both.

## Raw evidence

Under local `C:/Users/lumsd/AppData/Local/Temp/opencode/`:

- `klab-dependency-tree-delivery-20261104/run.json` and `stdout.log`
- `klab-component-dependency-tree-20261104/run.json` and `stdout.log`
- `klab-effective-delivery-20261104/run.json`
- `klab-effective-component-20261104/run.json`
- `klab-delivery-effective-before-20261104.xml`
- `klab-component-effective-before-20261104.xml`
- `klab-component-hm012-resolver-probe-20261104/run.json` and `stdout.log`

Initial malformed PowerShell output-argument attempts and missing offline help-plugin
attempts are retained as failures, not overwritten. Fully qualified pinned plugin
invocations succeeded afterward. Remaining work: exact selected artifact hashes,
resolver paths/metadata, packaging contents, duplicate classes, toolchain/settings,
active profiles, before/after verification and final integrated manifest.

## Subsequent inspection

The lock-like `klab-dependency-artifacts-before-20261104.json` records 466 selected
module/artifact rows with existing resolver files, hashes and metadata. Separate
language-cache inventory records ten main/IDE artifacts; the five main JAR hashes
match the prior integration note. Their original source-build provenance remains
NOT_VERIFIED; no source revision is inferred from a SNAPSHOT filename.

Toolchain, active profiles and password-masked effective settings were captured
with pinned help-plugin invocations. The first `-version` call exits before goals;
only the later `-V` run captures the profile/settings goals. Raw settings remain
private and are not committed. The component fat JAR is flattened, not a set of
nested JARs: use the expanded packaged-coordinate/class-entry inventory, not an
empty nested-JAR list, to assess its actual contents and potential host shadowing.
