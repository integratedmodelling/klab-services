# Scientific analysis and provenance: inspected contracts

Inspected integration base: `4ad3887cc6f5d4a816fc9aec5e0f170cfa5efcec`.
Current branch: `feature/python-client-scientific-analysis-provenance`.
These are source-inspection findings, not live acceptance.

| Concern | Authority at this base | Implementation boundary |
| --- | --- | --- |
| Export | `exports.py:50–101`, `ObservationImpl.export_to_file`; scoped native export, advertised schema/media type | Reuse exact-byte export and origin binding; no new exporter |
| Raster meaning | Corrected component patch described in `component-patches/geospatial/README.md` | Single-band numerical GeoTIFF, artifact transform/CRS/unit/mask; no silent correction of published exporter |
| Sparse reads | `client.py:51–63` ScientificData | Keep indexed data separate; no inferred array reshape |
| Query wire | `KnowledgeGraphQuery.java:171–213,398–508` | Typed anchor `{type,id}`, `resultType=LINK`, source/target, relationship, depth=1, offset/limit |
| Query route | `RuntimeServerController.java:531–573`; `RuntimeServiceImpl.query_knowledge_graph` | Scoped POST `/api/v1/query`, read-only despite POST; 422 unsupported, denial/unavailability propagate |
| Link wire | `LinkImpl.java:106–131` getters | `source`, `target`, `relationship`, `sequence`, `properties`; embedded endpoints carry asset classes/IDs |
| Executed effects | `GraphModel.java:327–405`; `docs/KNOWLEDGE_GRAPH.md:87–114` | Activity -> Observation effects; HAS_PLAN and RESOLVED describe plans, not execution proof |
| Activity fields | `ActivityImpl.java:16–32`; inherited ProvenanceNodeImpl | Keep supplied activity type/outcome, IDs, URNs and sanitized extensions; do not synthesize execution |
| Pagination | `docs/KNOWLEDGE_GRAPH.md:199–219`; Neo4j query implementation | Bounded one-hop neighborhood; explicit page offset; unchanged-graph ordering only, no snapshot |
| Identity | `client.py:372–389`, `handles.py:316–336` | Bound context, Runtime origin/ID, exact observation membership lookup before query |
| Consistency | No shared export/query revision lease in inspected interface | Separate fetch times; cross-fetch consistency explicitly unknown |

## Selected defaults

- Optional `analysis` extra: Rasterio, NumPy and xarray. Core imports remain free of
  those imports. Eager in-memory Dataset with native values plus separate validity,
  rectilinear pixel-center coordinates, explicit loading budget, no lazy open files.
- Metadata authority is the actual artifact. Missing CRS/unit/transform is reported
  or rejected when coordinates cannot be justified; conflicting supplied observation
  units reject. No unit normalization guessing.
- Provenance is a bounded **one-hop recorded neighborhood** per explicit request,
  not a recursively complete lineage tree. Callers can inspect further nodes only
  through future independently scoped APIs; this milestone does not invent source
  bindings or causal paths. Include selected recorded relationship kinds, retaining
  plan links as plan evidence. All views are partial and explicitly refreshed by
  making another request. There is no cache, implicit polling or mutation.
- An exact local digest identifies file bytes, not server revision. No persisted
  graph cache or saved-handle schema change is needed.

## Acceptance dependencies

The corrected component and compatible server must be pinned for genuine exported
raster/lineage acceptance. Historical component and connection results are not
acceptance of this implementation. Real Hub access, external provider accuracy,
server identity promotion and full lineage/replay remain separate gates.
