# Model and observable catalog storage

The resources service now depends on `ModelCatalog`. The default implementation remains
`ModelKbox`/`ObservableKbox` using H2. `DocumentModelKbox`/`DocumentObservableKbox` are independent
implementations: constructing either document class does not open an H2 connection. Both paths
use `ModelDescriptorFactory` for domain inference, including secondary observables, process
changes and dereified attributes.

## Selecting a backend

Set JVM system properties before starting the resources service:

| Backend | Properties |
| --- | --- |
| Existing H2 (default) | `-Dklab.model.catalog=h2` |
| Embedded persistent Nitrite/RocksDB | `-Dklab.model.catalog=nitrite` |
| MongoDB | `-Dklab.model.catalog=mongo`, `-Dklab.model.catalog.mongo.uri=...`, `-Dklab.model.catalog.mongo.database=...` |

Nitrite writes `data/models-v1.db` under the same service configuration directory convention as
`ResourcesKBox`. MongoDB uses collection `model_catalog_v1` in the explicitly supplied database.
Use a dedicated database per independent resources catalog/worldview. This is not a mechanism
for merging unrelated services' namespaces. Unknown backend names and missing Mongo settings
fail startup. No Mongo connection is opened for H2 or Nitrite. Credentials must not be committed
to repository configuration; supply the URI through deployment configuration.

Switching takes effect on service restart. Normal semantic indexing rebuilds descriptors from
the workspace into the selected backend. H2 data is not converted or deleted when another
backend is selected; return to `h2` to compare or roll back. Source reindexing is preferred to a
database copy because older H2 records have already lost descriptor information.

`ResourcesProvider.modelKbox()` now returns `ModelCatalog`. Service callers retain storage,
retrieval, namespace, semantic matching and discovery operations. SQL strings, direct database
access and inherited unimplemented `H2Kbox` administration methods are deliberately not part of
the portable contract. SQL-specific callers may continue to use `ModelKbox.create(service)`.

## Findings and fixes

* SQL strings contained unescaped concept definitions, namespace IDs, model names and metadata
  keys. Strings are now escaped and metadata inserts use prepared statements with deterministic
  statement closure.
* H2 startup exceptions were swallowed, leaving a null database. They now propagate as storage
  exceptions. Model schema/serializer initialization is synchronized and marked complete only
  after registration succeeds.
* Namespace removal depended on a namespace table that model indexing did not necessarily
  populate. Removal now also works for orphan model records, and indexing records the namespace
  timestamp after models are written.
* SQL retrieval omitted observation type, enumeration fields and specialization. More broadly,
  SQL's descriptor schema did not retain permissions, mediation, primary-observable status,
  version, priority or other model data. New writes also persist a versioned complete descriptor
  payload in metadata under `$klab:descriptor:v1`. Numeric and named model retrieval use that
  payload. Old rows remain readable through their legacy representation; reindex them to recover
  fields that were never stored.
* Missing SQL records produced empty beans; numeric and named retrieval now return null.
* Spatial candidate checks tested `Space.getShape()` (dimension sizes) instead of the geographic
  shape. Both implementations now check the actual geometric extent, including null extents.
* Project-private filtering could expose models without a project constraint, and SQL operator
  precedence could bypass the project filter. Both backends now require a matching project for
  project-private models, including scenario models. All scenario constraints are considered.
* Discovery now evaluates explicit user/group denials even on otherwise public permissions;
  the general `ResourcePrivileges.checkAuthorization(Scope)` overload bypassed those denials.
  Legacy SQL rows without a descriptor payload reload project permissions and deny access when
  the owning project cannot be resolved.
* Name-based set deduplication discarded distinct matching descriptor variants. Discovery keeps
  those variants for downstream prioritization.
* `ModelReference.copy()` omitted version, priority and abstract-observable status. These fields
  are now copied.
* Startup inference runs in a service scope, while the old implementation only retrieved
  namespaces/projects in user scopes. Local inference now retrieves local workspace metadata;
  missing namespace/project metadata fails indexing instead of silently assigning public access.

Existing predicate-head discovery behavior and failure propagation have been retained, including
the working tree's predicate compatibility and process-change inference changes.

## Document representation and consistency

Concept URNs are unique dictionary keys. Numeric concept IDs are allocated persistently and remain
stable across restart. MongoDB arbitrates concurrent concept insertion through its unique `_id`
and uses an atomic counter; gaps in numeric IDs are harmless. Resolved core heads are indexed.
Unresolved definitions use an empty core head and are retried on discovery. Call
`DocumentObservableKbox.refreshSemanticIndex()` after changing a worldview if the catalog is
retained without reindexing its source models. All clients sharing a catalog must use the same
worldview.

One model document contains every inferred descriptor for that model, its indexed type IDs,
descriptor IDs, name and namespace. Each descriptor is a versioned JSON payload with every
non-transient field, including permissions and metadata. Geometry is normalized to longitude/
latitude WKT; concepts are reconstructed through the reasoner. Descriptor IDs identify one stored
revision and can change on replacement; concept IDs do not. Unsupported payload versions fail
explicitly. Direct `store(ModelReference)` appends a descriptor; `store(KimModel)` replaces all
descriptors inferred from that model.

Namespace replacement stages model documents under a fresh generation. One namespace-record
replacement activates the complete generation. Discovery and administrative retrieval exclude
inactive generations. Inference/staging failure leaves the previous generation active; normal
completion removes the previous generation. If publication has an ambiguous network outcome,
staged data is retained because publication may have succeeded. A process crash or concurrent
replacement can leave inactive documents requiring later maintenance, but they are not returned
by the catalog. Namespace publication is atomic; an entire multi-namespace rebuild and an entire
query across namespaces are not a snapshot transaction. Coordinate namespace edits/rebuilds
through one indexing writer; the backend is not a distributed workspace revision coordinator.

The H2 path infers a namespace's descriptors before clearing its existing records, but legacy SQL
write failure can still leave a partial namespace. The alternate backend is the path with staged
namespace publication. Concept records are retained after deleting models; dictionary garbage
collection is intentionally not implemented.

Mongo model replacement uses the driver's
[single-document replacement operation](https://www.mongodb.com/docs/drivers/java/sync/current/crud/replace-documents/)
and majority write concern. Nitrite serializes writes within an embedded store and commits them.
The adapter uses indexed equality lookups and unions the results: investigation of the installed
Nitrite 4.3 sources and an executable query-plan probe showed that `IN` performs a collection scan
and does not match scalar members of arrays. Nitrite's
[array filters](https://nitrite.dizitart.com/java-sdk/filter/index.html) offer element matching,
but indexed equality supplies the required candidate lookup without scanning the collection.

## Discovery semantics and remaining limits

Candidate discovery first finds acceptable core heads using the reasoner, then checks full
semantic distance, then uses indexed model type IDs. Lexical namespace/project/scenario scope,
countable reification mode, spatial rank and bounding-box intersection are applied. Coverage
constraints are checked through the resources service. `query()` additionally checks resource
permissions; `queryModels()` remains the internal unranked candidate API, like H2, and must not
be exposed as an authorization boundary.

Spatial matching deliberately preserves H2's envelope-intersection semantics rather than
switching to exact polygon intersection. Empty/missing extents remain unconstrained. Temporal
candidate boundaries remain disabled as in the original implementation; coverage validation and
prioritization retain their existing responsibilities. Physical enumerated extents are resolved;
unsupported unresolved enumerations fail explicitly. These semantics should be changed only
with resolver-level acceptance tests, not as an incidental storage migration.

MongoDB is implemented, but requires validation against a deployment before production use.
The current design narrows candidates in the database, then checks geometry, permissions and
coverage in Java. It does not yet push spatial predicates into database geospatial indexes.
Large semantic candidate sets still require materialization; `retrieveAll` and `count` are
administrative operations, not paginated streaming APIs. A single model document must fit the
backend's document-size limits. Load, latency, failover and backup/restore testing remain needed
before replacing H2 in production.

## Tests

Run the focused reactor tests with Java 21:

```text
mvn -pl klab.services.resources -am test -Dtest=DocumentModelKboxTest,LegacyModelKboxTest,PredicateModelDiscoveryTest,MongoKboxStoreTest -Dsurefire.failIfNoSpecifiedTests=false
```

Use an isolated `user.home` through Surefire's `argLine` when exercising the H2 configuration in a
developer environment. The tests cover descriptor fidelity, persistent Nitrite restart and stable
concept IDs, polygon round-trips and spatial intersections, indexed candidate lookup, replacement
failure, namespace cleanup/publication, project/scenario visibility, explicit permission denials,
and predicate matching/retry behavior. `ClassificationPipelineTest` also exercises the existing
resolver classification path and can be added to the test selection.

Set `KLAB_TEST_MONGO_URI` to opt into the Mongo integration test. It creates a uniquely named
`klab_catalog_test_*` database and drops only that database afterward. Without the variable the
test is explicitly skipped. It exercises cross-client IDs, unique concept insertion, indexed
array lookup and complete model replacement.
