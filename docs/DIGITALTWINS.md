# Digital twins in k.LAB

This is a concept and implementation guide based on the source reviewed on 2026-09-05.
It distinguishes the public contract, current behavior, and work still required. See
[KLAB](KLAB.md), [architecture](ARCHITECTURE.md), [scopes](SCOPES.md),
[resolution](RESOLUTION.md), and [provenance](PROVENANCE.md) for the surrounding architecture.
The proposed composition of two twins is specified in [DISTRIBUTED_TWINS](DISTRIBUTED_TWINS.md).

## Concept and architectural position

A digital twin is a contextualized, semantically typed graph of observations and the history
of activities that produced them. It represents a real or hypothesized system, including
entities, qualities, relationships, processes, events, and observers. Numeric arrays alone
are insufficient: their observable, geometry, observer, dependencies, and provenance make
them observations that k.LAB can interpret and reuse.

The Resources service supplies knowledge and implementations; the Reasoner supplies semantic
interpretation; the Resolver builds a plan; the Runtime executes that plan and owns the twin.
The runtime knowledge graph is distinct from the Reasoner's worldview ontology. A concept
describes what can be observed; an observation records a particular realization in a context.

A `ContextScope` is the authorized handle and current position in a twin. Its identity,
observer, focus, service catalog, transaction, and messaging accompany operations. A focused
scope produced by `within(...)` does not create an independent twin. Likewise, the peer scope
reconstructed in another service points back to the owning runtime. Connecting two independent
twins requires a new identity and composition semantics, beyond normal peer reconstruction.

## Public API and object ownership

[`DigitalTwin`][dt] exposes three main components, configuration, transactions, provenance,
dataflow views, and disposal. [`DigitalTwinImpl`][impl] constructs a contextualized Neo4j graph,
a `StorageManagerImpl`, and a `SchedulerImpl`. The runtime's main graph provides the database
connection and context catalog; contextualized graphs share that database connection.

| API | Responsibility and current boundary |
|---|---|
| `getKnowledgeGraph()` | Assets, relations, history, descriptors, and queries; not primitive array payloads. |
| `getStorageManager()` | Per-observation data and native shards; see [STORAGE](STORAGE.md). |
| `getScheduler()` | Executor registration, initialization, dependencies, and intended temporal reactivity. |
| `transaction(...)` | Provenance-bearing transaction tree, normally managed by the service context. |
| `getProvenanceGraph(context)` / `getDataflowGraph(context)` | Graph projections; the implementation currently constructs both with the root scope, ignoring the supplied context. |
| `getOptions()` | Creation configuration; this review corrected the server constructor to retain the finalized configuration. |
| `isClient()` / `dispose()` | Ownership/lifecycle boundary; current server behavior needs correction, discussed below. |

`Configuration` carries identity, name/description, owner, access rights, runtime/service URLs
and service ID, persistence and timeout, an optional observer, behavior URN, and sharding
strategy. Configuration validation and runtime scope creation must establish a usable context
before publication. A requested federation ID is not general authority to create arbitrary
identities. Configuration messages are descriptors, not serialized live scopes or capabilities.

[`ClientDigitalTwin`][client] and `ClientKnowledgeGraph` are partial client representations.
They retrieve and cache runtime state and integrate commits. They are not autonomous copies
of server storage and execution. A loaded client subgraph must not be mistaken for the whole
twin, nor for a restart-safe replica.

## Knowledge graph

The graph records runtime assets and their relationships, including observation containment,
cohort membership, dependencies, provenance activities, agents, plans, actuators, geometry,
and shard descriptors. Context, provenance, and dataflow roots provide entry points. Different
projections answer different questions without introducing separate authoritative histories.

An observation may have `HAS_DATA` links to physical shard descriptors and `AFFECTS` links
relating its computation to others. `HAS_CHILD` and `HAS_MEMBER` represent different structural
roles; a cohort catalogs observations sharing an observable without making those observations
identical. Semantically equal observations can have different identities, geometries, or
provenance and must remain distinct.

The graph API supports direct lookup, link traversal, a query builder, and database transactions.
The actual Neo4j query implementation supports only part of that contract. Asset numbers are
allocated by the database implementation, not globally across runtimes. Graph identity and
query isolation must be repaired before using independent graphs in a single view.

[KNOWLEDGE_GRAPH](KNOWLEDGE_GRAPH.md) describes persistence, querying, transaction boundaries,
the client projection, and the defects found in this review, with reproduction cases.

## Storage

Storage holds the primitive values associated with quality observations. A storage manager
indexes `Storage` objects by observation ID; each storage has time-indexed groups of shards,
and each shard owns a primitive buffer. Components access these through typed scanners.

The dataflow compiler attributes the native sharding strategy before allocation. Type,
geometry, fill curve, and split policy define the buffer contract. Compatible float/double
scanner adapters mediate precision without allocating a converted data cube. A different
geometry, key dictionary, or unit cannot be reconciled merely by concatenating buffers.

Initialization selects timestamp zero; later slices use the event start. Successful execution
finalizes histograms and, under durable persistence policies, writes data files. The scheduler's
execution path flushes storage before linking new shard descriptors or updating existing ones
in the transaction. The graph retains descriptors; the files retain primitive payloads.

Graph commit and filesystem persistence are not one atomic resource transaction. Data may
have been written or in-memory identities changed before a graph rollback. Recovery therefore
needs staging, orphan cleanup, and a rule for authoritative committed versions. The existing
flush ordering protects against publishing a descriptor before its file is complete, but does
not provide a general rollback of overwritten data. See [STORAGE](STORAGE.md) for details.

For a composed twin, source data should remain owned by its source runtime. The new twin needs
an origin-aware storage facade, read-only versioned imports, and local storage for newly
computed qualities. This is proposed behavior, not current `StorageManagerImpl` functionality.

## Scheduler

### Contract and initialization path

[`Scheduler`][scheduler-api] associates executable contextualizers with observations, submits
observations, reports temporal bounds/resolution, and supports a switch to real time.
Events distinguish initialization from subsequent changes. Executors take a geometry, event,
and context scope and return success/failure. Persisted service calls are intended to support
executor reconstruction.

In [`SchedulerImpl`][scheduler], construction creates an initialization event and posts it to
a Reactor replay sink. `submit` rejects empty observations, registers geometry time bounds,
installs a subscription, and explicitly initializes the observation. Initialization recursively
walks incoming `AFFECTS` links, groups prerequisites by sequence, and runs each group on virtual
threads before running the target executor. Quality execution flushes storage, updates
histograms and shard descriptors, then records event timestamps in the current transaction.

The timestamp checks try to avoid recomputing already handled dependencies. Substantial quality
initialization receives special treatment through a zero timestamp. These checks are not a
concurrent execution lock, a causal history, or an exactly-once guarantee.

### TimeEmitter versus scheduler integration

[`TimeEmitter`][emitter] implements fixed-duration registrations, simulated time, real-time
timers, stopping, and schedule serialization/reconstruction. Subclasses receive emitted
intervals through `emitEvent`. Its tests exercise those mechanics independently.

The enclosing scheduler is explicitly described as a stub in its source. It creates an ordinary
`TimeEmitter` with an empty callback, does not register the observed temporal extents with it,
and leaves schedule reconstruction commented out. Its subscription filters out initialization;
`handleEvent` handles only initialization. Consequently this subscription performs no temporal
contextualization. `checkApplies` also returns true unconditionally. Successful initialization
must not be advertised as a working time-driven twin.

### Problems found and required regression cases

These are source-review findings unless an executed reproduction is explicitly identified.

| Priority | Finding and consequence | Repair and regression case |
|---|---|---|
| High; fixed in this change | Neo4j incoming `getLinks` reversed endpoints; recursive `AFFECTS` traversal could visit its own target indefinitely. | Corrected graph direction; added incoming/outgoing result-adaptation regression tests. Full persisted execution still needs integration coverage. |
| High | No temporal integration, as described above. | Wire emitter registrations and callbacks to event-local execution scopes/transactions; assert a later interval changes values and commits provenance. |
| High | The executor cache is capped at 200; execution calls `getIfPresent`, so eviction silently skips work and returns success. Its unused loader also returns a success-only placeholder. | Reconstruct executors or fail explicitly; submit more than 200 executable observations and revisit the first. |
| High | No visiting set or per-event shared future protects dependency recursion. Cycles can recurse indefinitely; a diamond may execute a shared dependency concurrently. | Validate dependency cycles and deduplicate each observation/version/event run; test cycle and diamond graphs. |
| High; fixed in this change | Sequence was read from the first outgoing `AFFECTS` edge of the prerequisite rather than the specific incoming edge being traversed. | Now uses the current link's sequence; a multi-edge scheduler integration regression remains desirable. |
| High | `TimeEmitter.register` reuses an overlapping in-phase registration without extending its range. | Merge ranges or retain separate logical registrations. An executed Java 21 probe registering `[0,4000]` then `[2000,6000]` at 1 second returned one ID and emitted only through 4000. |
| Medium | Coalesced registrations have one ID with no reference count; `unregister` removes the shared cadence. | Separate subscriber lifetime from physical event coalescing; remove one of two subscribers. |
| Medium | `switchToRealTime(until)` passes `until` to an emitter method whose argument is a logical starting epoch, not a termination boundary. | Define the boundary contract and translate explicitly; test a finite real-time run. |
| Medium | Scheduler resolution is never assigned; epoch zero doubles as an unset marker. `notifyTime` assumes non-null endpoints. | Model unset/open time explicitly and update resolution; test absent, open, zero, and pre-epoch times. |
| Medium | Replay retains all events; successful subscriptions are not retained for disposal. | Bound replay and persist a cursor; cancel subscriptions and close the emitter with the twin. |
| Medium | Replaying non-initial events occurs when subscribing, before explicit initialization. Currently inert, this becomes an ordering hazard when handling is implemented. | Initialize or restore state before admitting replayed events; test a late subscriber. |
| Medium | Failed dependency futures return false without preserving their causes in the first failure check. | Propagate concrete failures into the activity and scope; test a throwing prerequisite. |

After wiring events, create a fresh execution transaction for each accepted event. The scope
captured at submission may belong to a completed transaction and must not be reused as the
mutable execution context for future callbacks. Use event-local geometry, retain source
revisions, and define when successful execution becomes visible before advancing the cursor.

## Transactions and observation lifecycle

1. A service context assembles an observation and creates a provenance activity/transaction.
2. Resolution contributes a dataflow; compilation attributes storage and registers executors.
3. Initialization executes dependencies and target computations. Secondary observations may
   contribute child transactions to the same root.
4. Children share the root graph, modifications, failures, cohort geometry updates, and
   contextualizers. A child commit returns `INTERMEDIATE_COMMIT_ID` (zero), not a database commit.
5. Root commit reserves a commit ID, prepares observation metadata, stores assets, applies
   modifications, and creates relationships in a graph transaction. Observations receive IDs
   and URNs before provenance activities snapshot those URNs.
6. Closing the graph transaction commits it. The twin builds and caches a `KnowledgeGraph.Commit`,
   finalizes the submitted target, and unregisters the transaction. Failure returns `-1`.

The commit descriptor contains added/modified/deleted asset sets and relationship triples.
The implementation caches these descriptors for at most 200 entries and ten minutes of
inactivity. They support prompt client synchronization, not durable replay. There is no
distributed atomic commit implied by a local transaction tree.

An additional failure boundary deserves a regression test: `DigitalTwinImpl.commit` calls
`kgTransaction.fail` from the catch outside try-with-resources. For an exception thrown by
the transaction body that did not already mark the graph transaction failed, resource close
can commit before that catch runs. Explicitly mark failure before closing, or change the graph
API to explicit success/commit with rollback by default. Database-helper exceptions and
application exceptions must both be tested.

## Persistence, cleanup, and implementation discrepancies

The API offers `ONE_OFF`, `IDLE_TIMEOUT`, `SERVICE_SHUTDOWN`, `REINITIALIZED_ON_TIMEOUT`, and
`EXPLICIT_ACTION` persistence policies. Their intended lifetime is explained in [SCOPES](SCOPES.md).
The following source details prevent treating that lifecycle description as a blanket guarantee:

- This review fixed the server constructor's unassigned configuration field; `getOptions()` now
  returns the configuration used to contextualize the graph.
- This review fixed server `DigitalTwinImpl.isClient()` to return false, as required by the
  interface's ownership rule.
- `ServiceContextScope.close()` unconditionally calls `digitalTwin.dispose()`; `dispose()` deletes
  the context graph and clears storage. It does not itself branch on persistence. Closing a peer,
  releasing a handle, stopping a runtime, and deleting durable state need separate operations.
- Disposal does not close the scheduler's emitter or subscriptions. Neo4j client `shutdown()`
  is empty; shared driver ownership needs an explicit service-level close.
- Provenance/dataflow methods ignore their passed context. Focused views need tests that ensure
  they honor observer and context filtering.

Only the configuration and ownership-flag defects in this list were fixed here. The remaining
lifecycle issues are particularly relevant to distributed twins: closing a composed view must
never dispose either source twin.

## Verification and source map

The review traced the API, service/client scopes, twin transaction implementation, Neo4j backend,
client graph, scheduler, emitter, and AMQP transport. The standalone Java 21 emitter reproduction
above executed successfully and demonstrated the truncated registration range. No live Neo4j
integration test was run. `KnowledgeGraphNeo4jContractTest` is commented out, so its presence
does not establish coverage.

The focused reactor command attempted during this review was:

```powershell
.\mvnw.cmd -o -pl klab.services.runtime -am "-Dtest=TimeEmitterTest,DigitalTwinCommitTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

It stopped in `klab.core.api` test compilation, including unresolved `Data.FillCurve` references
in existing tests, before the selected runtime tests ran. Git ownership warnings also appeared.
No passing reactor-test claim is made. A subsequent production compile with
`-o -pl klab.services.runtime -am -Dmaven.test.skip=true compile` also stopped upstream, in
`klab.core.common`, with unresolved API types including `Scope` and
`ExternalAuthenticationCredentials`. The changed runtime classes were not reached.
`KnowledgeGraphLinkDirectionTest` was added to exercise incoming/outgoing persisted-result
adaptation with a fake query result; it has not run because of these build blockers.
Restore upstream compilation, then run it alongside the selected tests and add the remaining
graph/scheduler integration regression cases above.

[dt]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/digitaltwin/DigitalTwin.java
[impl]: ../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/DigitalTwinImpl.java
[client]: ../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/digitaltwin/ClientDigitalTwin.java
[scheduler-api]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/digitaltwin/Scheduler.java
[scheduler]: ../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/scheduler/SchedulerImpl.java
[emitter]: ../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/scheduler/TimeEmitter.java
