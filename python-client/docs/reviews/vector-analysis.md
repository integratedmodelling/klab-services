# Contributor review: bounded native GeoJSON interpretation

2026-11-05 UTC. Author/CLI review, **not independent human approval**.
Base597ecb0aed8dddf926e001d635b9552c5d22ddf8; branchfeature/python-vector-analysis.
Initial implementation06c6eef53; installed/live gates remain separate.

## Recommendation

**Ready for bounded maintainer review** after the corrections and final installed/
actual-Java checks below. This is author/CLI recommendation, not human approval or
release acceptance. Normal final-head hosted checks/protections remain required.

## Capability and architecture

Native Java GeoJSON already exists. The adapter reads local exact bytes directly,
never via external file drivers/network access. Optional GeoPandas/Shapely imports
occur only on use. Existing export/handle associations are reused; no Java, model,
component, worldview or support-policy change. Immutable feature records retain
IDs, missing/null, property collisions and nested fields without the metadata
redaction helper (which would drop legitimate source property names).

## Findings, ordered by consequence

1. **Important, corrected before installed gates:** a wide union of sparse property
   names could create a dense table much larger than bounded input. Added explicit
   max_cells budget checked before dataframe construction, with rejection test.
2. **Important, corrected:** GEOS stores doubles. A3D integer ordinate
   beyond exact double range may be silently rounded, even though raw JSON records
   retain it. Reproduced9007199254740993→9007199254740992.0. Integer ordinates beyond
   magnitude2**53 now fail explicitly, with boundary and refusal tests. No guessed
   height unit or scientific correction. Final corrected-candidate gates required.
3. **Harness correction:** initial polygon test expected17positions; independently
   enumerated three5-positionrings+onepoint equal16. Corrected test expectation,
   not geometry implementation. Initial focused run retained by tool history.

## Validation performed and limits

- Initial55 focused tests PASS, unexcluded100%lines/branches for vector.py; lintPASS.
- Core import probe PASS: no scientific libraries imported through package facade.
- Final corrected f62f6bd42 installed full gates PASS:2,943tests/17coveragegates,
  lint/build/install/sourceparity. Actual Java native rectangle workflow now PASS
  (separate final receipt below). Final wheel
  `3b44da35a00de5415c225dbd7d4d3714464d15a5d822fb1f8bceb90519bdc849`.
- Installed core facade probe PASS: optional scientific libraries remain unloaded.
  Python3.12.7; GeoPandas1.2.0/Shapely2.2.0/pyproj3.8.0/pyogrio0.13.0,
  pandas3.1.1/NumPy2.5.4; noJava dependency/source changes.
- Native multipart/hole production is NOT claimed by a rectangle. Independent
  local fixtures cover those adapter semantics only. Artifact digest is not a
  shared server/provenance revision. Area/unit/category inference is absent.

## Lifecycle, permissions, state and performance

Local analysis does not authenticate, mutate remote state or acquire cleanup
ownership. Native export uses the existing bound observation contract. The live
workflow creates/releases only owned isolated session/context, preserves primary
failure, uses a private runner directory and hidden owned processes. Existing
unrelated Java must remain unchanged. Public budgets bound input/counts/dense cells,
not total Python/GEOS RSS; no large-provider/soak promise. Returned dataframe is
caller-owned mutable data; original feature records are detached and immutable.

## Coordination and next actions

No competing contributor ownership inferred or reviewers assigned. Upstream
release/support and geospatial publication remain separate; the current development
component is pinned, not promoted to supported release. Correct coordinate precision
guard, run final installed/coverage/core gates and actual Java comparison/cleanup,
then re-review exact candidate and require normal hosted CI/protections.

## Final exact-candidate acceptance

Executable candidate `f62f6bd42`, base597ecb0ae; later changes are Markdown only,
to be verified before evidence reuse. Full feature diff and current Java exporter
source reviewed for usefulness/fit/contracts/science/identity/lifecycle/state/
performance/contributor fit/maintainability. No remaining demonstrated blocking or
important defect in this bounded contract. Geometry/feature budgets are not a
representative-provider performance claim; unsupported release contracts remain.

| Check / exact command | Environment / evidence | Result and boundary |
|---|---|---|
| `run_gates.py --repo <checkout> --label ab-q3-vector-final` | Installed noneditable Python3.12.7, f62f6bd42 | PASS:2,943tests/17coveragegates/lint/build/sourceparity; one existing Rasterio warning |
| Focused `pytest tests/unit/test_vector_analysis.py --cov=klab_client.vector --cov-branch` | f62f6bd42 | PASS:56tests,unexcluded100%line/branch |
| `run_reliability_acceptance.py --server-repo <checkout> --java <Temurin21> --components <corrected-components.json> --state-dir <q3-vector-live> --scenario vector-analysis` | Same pinned Java/core/components; synthetic consolidation worldview | PASS:actual advertisedgeospatial.io.geojson,onepolygon/fivepositions,independentcornerswithin1e-6degrees,originalID/URN/positions/CRS84/digestpreserved |
| Owned cleanup / existing Java | q3-vector-live/result.json | PASS:ownedprocesses/listenersclosed,existingJavaunchanged |

Actual native artifact SHA256
`e55d912527a0956e403fb448cf34e7be92b8fe6d833058599b390ac9f08d895d`.
Local multipart/hole/null fixtures are not substituted for Java producer evidence.
No historical replication, raster-value/class/area/vertical-unit interpretation or
server revision/consistent provenance snapshot is claimed. Evidence roots external
`ab-q3-vector-final/identity.json`, `q3-vector-live/result.json`; precision finding
and initial installed run retained. Suggested PR: bounded native vector analysis
with explicit scientific/identity/optional-dependency/budget boundaries, these
commands and remaining limitations. Smallest next action: normal hosted checks.
