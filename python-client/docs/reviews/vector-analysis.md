# Contributor review: bounded native GeoJSON interpretation

2026-11-05 UTC. Author/CLI review, **not independent human approval**.
Base597ecb0aed8dddf926e001d635b9552c5d22ddf8; branchfeature/python-vector-analysis.
Initial implementation06c6eef53; installed/live gates remain separate.

## Recommendation

**Needs fixes / validation**, not merge-ready. The feature is a useful missing
scientific-user slice, not a new exporter or remote algorithm. Required guards and
final actual-Java/export validation must be demonstrated before proposing a PR.

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
- Installed full gates active; actual Java native rectangle workflow not yet run.
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
