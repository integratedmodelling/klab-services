# The digital-twin knowledge graph

See [PERSISTENT_TWINS](PERSISTENT_TWINS.md) for the subsequent actuator representation,
committed scheduler registry, bounded executor restoration, and remaining restart requirements.

This document describes the runtime graph as reviewed on 2026-09-05. It accompanies
[DIGITALTWINS](DIGITALTWINS.md), [STORAGE](STORAGE.md), and [PROVENANCE](PROVENANCE.md).
The distributed design in [DISTRIBUTED_TWINS](DISTRIBUTED_TWINS.md) is proposed work;
the Neo4j implementation does not currently merge remote graphs.

## What the graph represents

The runtime graph is the persistent record of contextualized knowledge: what was observed,
where and when, by whom, through which plan, with what dependencies and data. The Reasoner's
ontology establishes meanings and semantic relationships; this graph records runtime assets
using those meanings. It is neither an OWL reasoner nor a store of all numeric cell values.

[`KnowledgeGraph`][api] is the public boundary. The runtime holds a main graph for service-wide
context discovery and contextualizes it with a twin configuration and user scope. A contextualized
graph carries a root context ID and user scope. [`KnowledgeGraphNeo4JClient`][backend] shares
the parent's Neo4j driver with these contextualized instances. Its name refers to a database
client; it is different from the user-facing [`ClientKnowledgeGraph`][client].

## Assets and relations

[`GraphModel`][model] defines the relationship vocabulary and transport graph representations.
The implementation serializes `RuntimeAsset` implementations into labeled nodes and properties;
`AbstractKnowledgeGraph.asParameters` and Neo4j adaptation reconstruct the corresponding Java
objects. GraphModel's compact transport enums/records are not a complete database schema or
proof that every advertised field and query is implemented.

| Asset or relationship | Role |
|---|---|
| Context root | Twin configuration, activity timestamps, and structural entry point. |
| Provenance/dataflow roots | Context-specific entry points for history and executable structure. |
| Observation | Contextualized observable, identity, geometry, contextualization state, metadata, and event timestamps. |
| Cohort | Catalog of observations for an observable; members remain independently identified. |
| Activity and Agent | Operations and responsible actors; plans/actuators describe intended and compiled computation. |
| Geometry | Encoded extent linked to assets; observer and cohort geometry have additional query logic. |
| Data / `Storage.Shard` | Native type, geometry, time, partition and histogram/persistence metadata; values live in storage files. |
| `HAS_CHILD`, `HAS_MEMBER` | Structural containment and cohort membership. |
| `HAS_PROVENANCE`, `HAS_DATAFLOW`, `HAS_PLAN` | Entry points and links to computational history/plans. |
| `CREATED`, `RESOLVED`, `BY_AGENT`, `TRIGGERED`, `CONTRIBUTED_TO` | Causal/provenance relationships. |
| `AFFECTS`, `CONTEXTUALIZED_BY`, `CONTEXTUALIZED` | Influence and contextualization relationships. Direction matters to execution. |
| `HAS_DATA`, `HAS_GEOMETRY` | Observation payload descriptors and extents. |
| `HAS_OBSERVER`, `HAS_RELATIONSHIP_SOURCE`, `HAS_RELATIONSHIP_TARGET` | Observer and semantic relationship structure. |

A relation can carry properties such as sequence and geometry; it is not adequately described
for replication by endpoint IDs and type alone. Several links between the same nodes can carry
different computational meanings or geometry. Define a durable edge identity before supporting
property updates and precise deletion in a remote view.

## Identity

Persisted runtime assets generally receive positive numeric IDs. Observations additionally receive
context-qualified URNs; uncommitted assets use transient identities. Special graph roots use
context-derived string keys internally, and agent matching uses names in some paths.
Do not assume that every database key can be passed to `getAsset(long, ...)`.

`nextKey()` allocates IDs from a `Statistics` node. Those numbers are intended to be unique in
one database; they do not identify assets across independent runtime databases. Even within a
database, the current allocator has a concurrency defect described below. A distributed reference
must include an origin runtime, source context, and source asset identity. Semantic equality,
asset number equality, and identity equality are three different tests.

## Reads and queries

The revised `query(Class, scope)` supports whole-context searches without an anchor,
typed predicates, directed multi-hop traversal, Boolean composition, and ordered pagination.
`run(scope)` returns distinct assets; `peek(scope)` returns the first result as an Optional.
The builder is mutable. Supported results include observations, cohorts, activities, agents,
plans, actuators, storage shards, Links and RuntimeAsset root marker classes.
`RuntimeAsset.class` searches runtime nodes; geometry descriptors are not RuntimeAssets.

### GraphModel contract

Use `GraphModel.Fields` for property names, `GraphModel.Labels` for database labels and
`GraphModel.Relationship` for edges. Record field constants alias the shared vocabulary.
Legacy spellings remain distinct: centralization does not migrate existing property names.
Property names are validated against `Fields.ALL`; caller values are bound parameters.
Arbitrary Cypher and undeclared metadata properties are not accepted.

### Query operations

| Operation | Behavior |
|---|---|
| `where(field, EQUALS, value)` | Typed equality; null means an absent property. Repeated predicates are AND. |
| `LT`, `LE`, `GT`, `GE` | Strict/inclusive comparisons; combine for numeric ranges. |
| `LIKE` | Case-sensitive pattern: `%` any sequence, `_` one character; other characters literal. |
| `BEFORE`, `AFTER` | Strict timestamp comparison using epoch milliseconds or an ISO-8601 instant string. |
| `source(asset)` / `target(asset)` | Outgoing traversal from / incoming traversal toward the anchor. A class can be a typed wildcard anchor. |
| `along(type, field, value, ...)` | Edge type and optional equality properties, applied to every traversed edge. Omit for any edge type. |
| `depth(n)` / `hops(min,max)` | Inclusive lengths 1..n / min..max. Default 1; maximum 64; zero includes the anchor. |
| `and`, `or`, `not` | Intersection, union, complement within the context and result type. |
| `order(Order.ascending(field), ...)` | Multiple sort keys; String keys mean ascending. |
| `offset(n)` / `limit(n)` | Nonnegative offset; limit -1 unlimited, zero no rows. Apply after Boolean composition. |
| `between(source,target,type)` | Return Links with direction and properties; exactly one hop. Parallel links remain separate. |
| `id(n)` | Identity lookup within context/result type; ignores other selection predicates. |

Boolean children must have the same result type and cannot carry pagination or ordering.
Sorting adds a Neo4j element-identity tie-breaker. Pagination is deterministic in an unchanged
graph, not a snapshot across concurrent writes. Trees are limited to 128 query nodes; database
execution has a 30-second transaction timeout. Unbounded results are materialized in memory.

### Java examples

Given an existing `ContextScope scope` and `Observation collective`:

```java
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import static org.integratedmodelling.klab.api.data.KnowledgeGraph.Query.Operator.*;
import static org.integratedmodelling.klab.api.digitaltwin.GraphModel.Fields.*;

KnowledgeGraph graph = scope.getDigitalTwin().getKnowledgeGraph();

var page = graph.query(Observation.class, scope)
    .where(SIZE, GE, 10L).where(SIZE, LT, 100L)
    .order(KnowledgeGraph.Query.Order.descending(SIZE))
    .offset(20).limit(20).run(scope);

// The direct-child query used by ServiceContextScope.getChildrenOf.
var children = graph.query(RuntimeAsset.class, scope)
    .source(collective).along(GraphModel.Relationship.HAS_CHILD).run(scope);

var descendants = graph.query(Observation.class, scope)
    .source(collective).along(GraphModel.Relationship.HAS_CHILD)
    .hops(2, 4).run(scope);
var ancestors = graph.query(Observation.class, scope)
    .target(collective).along(GraphModel.Relationship.HAS_CHILD)
    .depth(4).run(scope);

var named = graph.query(Observation.class, scope).where(NAME, LIKE, "river%");
var recent = graph.query(Observation.class, scope)
    .where(UPDATED, AFTER, "2026-01-01T00:00:00Z");
var unresolved = graph.query(Observation.class, scope).where(RESOLVED, EQUALS, false);
var selected = named.or(recent).and(unresolved.not()).limit(100).run(scope);

var links = graph.query(KnowledgeGraph.Link.class, scope)
    .source(collective).along(GraphModel.Relationship.HAS_CHILD).run(scope);
// With both endpoints known:
// graph.query(KnowledgeGraph.Link.class, scope)
//     .between(collective, child, GraphModel.Relationship.HAS_CHILD).run(scope);
```

### Transport and failure modes

The endpoint accepts serialized `KnowledgeGraphQuery`: `resultType` selects the result;
`type` is QUERY, AND, OR or NOT; Boolean nodes have `children`. Leaves contain typed
`criteria` records (field, operator, argument), optional source/target descriptors (type,
numeric id or urn), relationship, minimumDepth and depth. Outer queries carry ordering
records (field, direction), offset and limit. Legacy string-valued `assetQueryCriteria`
remain readable; new callers should retain JSON numbers and booleans.

Only a successful query with no matches returns an empty list. `QueryException` provides
machine-readable codes; HTTP responses use ProblemDetail with a `code` property:

| Code | HTTP | Meaning |
|---|---|---|
| INVALID_QUERY | 400 | Invalid field, bounds, shape, value or scope. |
| UNSUPPORTED_QUERY | 422 | Recognized feature with no implementation. |
| ACCESS_DENIED | 403 | Access rejected; the client also maps 401 here. |
| BACKEND_UNAVAILABLE | 503 | Database/transport unavailable; includes gateway failures on the client. |
| EXECUTION_FAILED | 500 | Execution or materialization failed. |

Spatial `INTERSECT`, `COVERS`, `NEAREST` are explicitly unsupported: geometry/CRS, distance
and ranking semantics still need design. They are never silently ignored. Arbitrary
projections, aggregates, path results and semantic reasoning are outside this API.

Context visibility is checked through directed structural/provenance reachability, bounded
at 64 edges, for results and intermediate nodes. This does not replace HTTP authorization
or repair the separate primitive lookup/mutation issues in KG-3. `getLinks` also includes
transaction-local links; general queries read committed database state. Numeric retrieval
has its own mutable asset cache.

## Writes, provenance, and commit visibility

Normal modifications originate in the service context's `DigitalTwin.Transaction`. Its root
collects assets, updates, relationships, and activities; children contribute to the same root.
`KnowledgeGraph.Transaction` is the lower database transaction. Its implementation opens a
Neo4j session/transaction, stores and links assets, and commits on `close` unless marked failed.

On successful close it updates parent IDs for recorded containment/membership links and the
context's last-update timestamp, commits, and populates the stored-asset cache. A scalar
`parentId` is only a convenience: multiple graph parents cannot be represented by that scalar.
Use actual relations for authoritative traversal, especially in a composed view.

Observation IDs/URNs are assigned during storage, before database commit, and quality storage
is rekeyed at that point. Rollback clears transaction bookkeeping but does not restore all
mutated Java objects or filesystem state. Consumers must only publish committed observations.
Retries need explicit reset/staging rather than assuming that a positive in-memory ID proves
a durable node exists.

`DigitalTwinImpl` constructs a `KnowledgeGraph.Commit` after graph close. It carries sets of
added observations/cohorts/assets, modified/deleted assets, and added/deleted relationship
triples. Some durable cohort catalog links are deliberately included even if created by an
earlier independent transaction, allowing clients to discover that structure.

The commit is cached, not written as a durable change log. Its timestamp is not a per-source
contiguous replication sequence. Its relationship triples omit properties and edge identity.
Changes cannot be safely reconstructed after restart or cache eviction from this API alone.

## Client projection and messaging

`ClientKnowledgeGraph` fetches a commit associated with a submitted observation, retrieves
necessary assets, applies it to a local graph, and deduplicates by commit ID. It tracks focus
and loaded content; the cache is not a complete snapshot by default. Its numeric maps and
commit deduplication are appropriate only within the current single-origin assumption.

[`AMQPChannel`][amqp] uses fanout exchanges and server-named transient receiver queues, with
automatic acknowledgement in `basicConsume`. A persistent message/exchange option does not
make those receiver queues durable. Message handling exceptions are logged after broker
acknowledgement. This transport supports live notification, but does not by itself establish
reliable graph replication. Durable outbox/replay and an idempotent receiver are required for
the distributed twin design.

## Review findings

The following are concrete source paths and failure scenarios. Except for the emitter probe
reported in [DIGITALTWINS](DIGITALTWINS.md), findings are static analysis, not live database
reproductions. Priorities reflect impact on correctness and the proposed composition feature.

### KG-1: incoming links reversed endpoints (high; fixed in this change)

In [`KnowledgeGraphNeo4j.getLinks`][neo4j], the incoming pattern is `(n)<-[r]-(m)`, but adaptation
previously always set source to the requested asset n and target to m. For persisted `A -AFFECTS-> B`,
`getLinks(B, INCOMING, ...)` therefore returned B as the source. The scheduler recursively
contextualizes `affecting.source()`, which could recurse into B again. Transaction-local links
have correct orientation, making the bug dependent on persistence and cache state.

The code now sets source=m/target=n for incoming traversal and source=n/target=m for outgoing,
preserving properties. `KnowledgeGraphLinkDirectionTest` exercises both result-adaptation paths
using a fake database result. Those tests pass; they are not live database tests. Still test both directions on persisted nodes, compare transaction-local and
persisted traversal, and execute the resulting two-node dependency graph in integration.

### KG-2: key allocation races across contextualized instances (high)

`nextKey()` synchronizes on one Java graph object but reads and updates `Statistics` in
separate database queries. Contextualized graphs are separate objects sharing the driver.
Two can read N, both calculate N+1, and both return N+1; the conditional update does not check
whether it actually changed a row. Concurrent first use can also create multiple statistics
nodes. Context initialization installs lookup indexes, but does not establish allocator uniqueness.

Use a transactionally locked allocator or an appropriate unique identity strategy, plus a
uniqueness constraint and retry semantics. Test concurrent allocations from two context
instances and two runtime processes, including an empty database. Add lookup indexes only
after auditing and migrating pre-existing duplicates.

### KG-3: context isolation is not enforced in primitive lookups (high)

`retrieveFromGraph` matches label and ID/URN without a root-context ownership predicate.
`getLinks` and update/link queries likewise do not establish context authorization merely by
accepting `Scope`. In these helpers scope primarily supports adaptation/logging. A caller
that can supply an asset identity may address another context; endpoint exploitability depends
on upstream checks and has not been demonstrated in this review.

Enforce authorized membership at the backend boundary before cache access, with explicit rules
for shared agents/catalog assets. Test cross-context reads, writes, links, URN and numeric
lookups, and cache hits under two users. Never treat federation membership as unrestricted
access to every observation.

### KG-4: silently ignored query features (fixed)

The replacement compiler implements ranges, hop bounds, ordering, pagination and Boolean
composition. It retains typed values across transport, corrects reversed AND/OR builders,
and preserves composed result types. Unsupported requests fail explicitly.

### Query visibility follow-up

The initial replacement compiler omitted `TRIGGERED` and `CONTEXTUALIZED` from context
reachability. A child query returned no rows when a collective was reachable through a nested
creation/contextualization activity. An embedded Neo4j test reproduced that failure; both edge
types are now included. A read-only production-data check on collective observation 223
confirmed the same path: the former filter returned zero children, while the corrected
compiled query returned all 11 children (232 through 242). This verifies query selection,
not the final GeoJSON serialization after restarting the runtime. Cross-context `AFFECTS` edges do not confer ownership. Negation also
normalizes an absent-property comparison to false before complementing it, so missing fields
do not incorrectly disappear from NOT results.

### KG-5: failed or unmatched mutations can appear successful (high)

The transactional `query` helper catches `Throwable`, logs it, and returns null. `update` and
`link` do not validate returned results. A link query matching no endpoints is a successful
Cypher statement with zero created relationships; the twin still records its triple in the
commit. A query failure may instead surface later at commit, obscuring the actual cause.

Propagate database failures, validate required row/update counts, and advertise only confirmed
changes. Test a missing endpoint, an invalid query, an offline driver, and a failed update.
The explicit transaction wrapper catches errors only if lower helpers actually throw them.

### KG-6: close-on-success semantics can commit an application failure (high)

`DigitalTwinImpl.commit` catches outside try-with-resources. If application logic throws while
assembling the database transaction without marking it failed, `close` runs first and can
commit partial work. Calling `fail` afterward cannot undo a committed transaction. Move failure
signaling inside the resource lifetime or require explicit commit with rollback on close.
Inject an application failure after a successful store and assert no partial graph survives.

### KG-7: interpolated relationship endpoints (fixed)

Both endpoint identities are bound parameters in the replacement compiler, including
quote-bearing URNs. Caller values are never concatenated into Cypher source.

### KG-8: rollback and cache coherence are incomplete (high for retry/recovery)

`setId` mutates assets and rekeys storage before commit; failure cleanup does not undo those
changes. `update` does not independently invalidate/replace the asset cache, so updates using
a different object instance may leave cached values stale. Deletion/reset also need explicit
cache invalidation. Test failed-store retries and update-through-a-copy followed by lookup.

### KG-9: incomplete link adaptation and lifecycle (medium)

`getLinks` skips opposite nodes with string IDs. Graph roots therefore cannot all be reached
through the same numeric adaptation path. Agent lookup also needs consistency between name
matching and ID matching. Neo4j `shutdown()` is empty, and `isOnline` reflects connection-time
state rather than periodic health. Define typed root references and service-owned driver
shutdown; test disconnect/reconnect and root/agent traversal.

## Implementation and test checklist

Verify the KG-1 correction and fix KG-2, KG-3, KG-5, and KG-6 before broadening graph federation. Preserve explicit failures when building a federated planner. Add source-qualified identity and durable
revision/change records through a versioned schema migration, not ad hoc extra cache keys.

Relevant existing tests are `DigitalTwinCommitTest`, `ClientKnowledgeGraphTest`, and the file
`KnowledgeGraphNeo4jContractTest`. The latter's test code is commented out. Restore an executable
Neo4j-backed contract suite covering two contexts, permissions, all mutation outcomes, links in
both directions, cache invalidation, and crash/retry boundaries. A mock-only test cannot prove
database locking or rollback behavior.

The focused reactor build now passes through the runtime server, including compiler, actuator
serialization, link direction, commit, scheduler and storage regression tests: 30 tests pass,
including three embedded Neo4j execution cases. These checks
do not establish production permissions, allocator safety or crash recovery.

[api]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/data/KnowledgeGraph.java
[model]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/digitaltwin/GraphModel.java
[neo4j]: ../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/neo4j/KnowledgeGraphNeo4j.java
[backend]: ../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/neo4j/KnowledgeGraphNeo4JClient.java
[client]: ../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/digitaltwin/ClientKnowledgeGraph.java
[amqp]: ../klab.core.common/src/main/java/org/integratedmodelling/common/authentication/scope/AMQPChannel.java
