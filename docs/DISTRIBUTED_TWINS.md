# Implementing connected digital twins

This is a proposed implementation contract and delivery plan for
`ContextScope.connect(ContextScope remoteContext)`, based on the source reviewed on 2026-09-05.
Proposed types, endpoints, message payloads, and schema below do not yet exist unless explicitly
identified as current. Read [DIGITALTWINS](DIGITALTWINS.md),
[KNOWLEDGE_GRAPH](KNOWLEDGE_GRAPH.md), [STORAGE](STORAGE.md), and [SCOPES](SCOPES.md) first.

## Required outcome

Given scopes for independently owned twins A and B, `A.connect(B)` returns a new scope C,
with its own ID and a new digital twin that presents a live view over both sources. A and B
retain ownership of their graph, storage, execution, and lifetime. The user can browse, query,
resolve, and create observations through C as one context. C's newly computed observations,
cross-source relations, and provenance belong to C.

This is different from attaching another client to A, focusing A with `within`, copying B into
A, or destructively merging two Neo4j databases. “One twin” means one authorized interface and
identity space, not a globally atomic snapshot of independent systems.

Example: A observes catchment hydrology and B observes land use. C can expose both observation
trees and compute runoff using a hydrology observation from A and a land-use observation from B.
Its output records both source identities and exact input revisions. A subsequent B commit
invalidates the relevant C computation through messaging; unrelated C observations remain valid.
Closing C releases its subscriptions and resources without closing A or B.

## Existing extension points and gaps

| Current code | What can be reused / what must change |
|---|---|
| [`ContextScope.connect`][scope-api] | Public signature and intent; its URL/configuration links refer to connection facilities inherited through scopes, not implemented composition. |
| [`ClientContextScope.connect`][client-scope], [`ServiceContextScope.connect`][service-scope] | Both return null. Add transport and coordinator delegation respectively. |
| `KnowledgeGraph.merge(URL)` | Neo4j and client implementations are stubs. Do not make graph merge alone responsible for storage, scheduling, authorization, or scope creation. |
| `DigitalTwinImpl` | Couples construction and transaction handling to Neo4j and service scopes. Introduce a facade/component seam rather than casting a federated graph to Neo4j. |
| `RuntimeService`, runtime client/controllers, `ScopeManager` | Existing authorized context creation, reconstruction, registration, and release paths. Add a composition request/response through the same scope authorization boundary. |
| `MessageType.DigitalTwinConnected` | Existing configuration payload can advertise completed connection; insufficient as a change-replication protocol. |
| `ClientKnowledgeGraph`, `KnowledgeGraph.Commit` | Local projection and commit application concepts; numeric keys and transient commits need origin qualification and durable revisions. |
| `AMQPChannel` | Live transport and message serialization; transient auto-ack receivers require replay/recovery support. |
| `StorageManagerImpl`, scanners | Local storage for C outputs; source reads need an origin-aware provider. |
| `SchedulerImpl`, `TimeEmitter` | Initialization/executor machinery and interval generation; temporal integration and recovery are prerequisites. |

## Explicit first-version decisions

1. **Coordinator:** host C on A's host runtime, using the caller's authorized session. This makes
   `A.connect(B)` operationally asymmetric. Neither source becomes subordinate or changes host.
2. **Writes:** imported assets are read-only. New observations and cross-source edges are written
   to C. Source mutation, if added later, is an explicit authorized source command with its own
   result; never silently route ordinary C updates to A or B.
3. **Consistency:** use per-source ordered, versioned commits and eventual synchronization. A
   query/execution pins a revision vector `(A:rA, B:rB, C:rC)`. Do not promise global serializability.
4. **Availability:** strict creation requires both sources authorized and reachable and an initial
   consistent source snapshot. Later outages produce visible degraded status, not empty graphs.
   Default computations requiring an unavailable fresh input fail or wait with a deadline.
5. **Semantics:** require compatible worldview identity/version for the first version. Distinct
   observations with equal semantics remain distinct. Incompatible versions fail with an
   actionable explanation until explicit mediation exists.
6. **Focus:** import each passed scope's authorized selection, including focus and observer,
   frozen as a serializable selection descriptor. Do not serialize a live scope or silently
   expand a focused selection to an entire source. Default C starts at its new root; callers
   explicitly focus it using mapped observations.
7. **Geometry:** retain source geometries and observers. C does not invent a common grid or
   observer. Resolution mediates only supported combinations; ambiguous overlapping observations
   require explicit selection rather than left-source precedence.
8. **Lifecycle:** C owns only its local state, projection caches, and subscriptions. Its persistence
   is independent; choose the normal default context policy unless an options overload requests
   another. Releasing C cannot cascade deletion into A or B.

The existing signature delegates to these defaults. Add a future options overload without
changing the meaning of existing calls. Reject null, closed, unresolved, or transaction-bound
source scopes. Reject identical source selections in the first version. The same leaf twin
reached through different nested views is deduplicated by origin identity, not treated as a cycle.

## Components of C

```text
ClientContextScope C -> host Runtime C -> connected DigitalTwin facade
                                         | graph projection + local overlay
                                         | source storage readers + local output storage
                                         | local scheduler + source change subscriptions
                                         |
                             authenticated source APIs and messaging
                                   /                       \
                             Runtime A                  Runtime B
                             owns twin A                owns twin B
```

### Graph projection and local overlay

Introduce a proposed `ConnectedDigitalTwin` implementing `DigitalTwin` and an origin-aware graph
facade. Retain a normal local graph for C-owned observations, activities, memberships, cross-source
edges, composition metadata, and durable synchronization state. Import authorized source metadata
into a versioned projection, not as locally owned source observations.

The coordinator must supply the facade everywhere the context is used: graph lookup, resolution
reuse, provenance/dataflow extraction, observation submission, and storage access. A browser-only
union would leave the Resolver unable to use the same observations the user sees.

Queries operate over one pinned revision vector. Push supported filters to sources or query
the materialized projection; complete cross-source joins in C. Apply global ordering, deduplication,
offset, and limit after union. Stable pagination tokens include the revision vector, sort key,
and source continuation state. Never concatenate two independently paginated pages and call
that a globally paginated result. Unsupported operators fail explicitly.

Selections must be dependency-closed for the advertised operation. Hidden prerequisites can
be represented as opaque, authorized references where allowed; otherwise mark the computation
unavailable. Never infer permission to reveal provenance/data from permission to list an asset.

### Identity and DTOs

Add immutable, versioned proposed DTOs in `klab.core.api`:

| Proposed DTO | Minimum contents |
|---|---|
| `TwinRef` | Stable runtime ID, context ID, validated service endpoint, dataset generation. |
| `SourceSelection` | TwinRef, focus/observer references, supported filters, worldview/version. |
| `OriginAssetRef` | TwinRef plus source asset ID/URN; stable across coordinator restart. |
| `ConnectRequest` | Request UUID/idempotency key, left/right selections, options, protocol version. No credentials in persisted DTOs. |
| `ConnectedTwinDescriptor` | C configuration, source selections, mapping/schema versions, state, revision vector, diagnostics. |
| `SourceChange` | Schema version, source/generation, commit identity, contiguous stream sequence, asset revisions, edge upserts/deletes with properties, causal references. |
| `SourceSnapshot` | Snapshot token and watermark, authorized assets/edges, immutable data-version references, pagination. |

Since current APIs expose long IDs, persist a bijection in C from `OriginAssetRef` to positive
C-local IDs. Use the repaired C allocator; do not encode two arbitrary longs into one long.
Rewrite all exposed references consistently: edges, parent/focus IDs, observation relations,
commit sets, actuator inputs, storage lookup, and provenance references. Retain origin URNs as
metadata/reference fields while C-facing identities use C's namespace. Source DTOs and cached
objects must not be mutated in place. Test two sources both containing asset and commit ID 1.

Use origin-qualified commit keys for transport deduplication. A runtime restart incarnation
identifies a connection attempt; a durable dataset generation distinguishes deletion/recreation
or restore-to-earlier-history. Do not reset stable asset identity merely because a process restarts.

### Storage

Dispatch imported observation reads through an origin/version-aware storage provider. Start
with read-only retrieval of immutable source shards at a pinned source revision, using the
existing primitive format where suitable. Validate type, size, geometry, fill curve, time slice,
checksum, key dictionary, and access rights. Cache by origin asset, generation, revision, shard,
and representation, not only by observation number.

Keep large buffers out of graph-change messages. Fetch through an authenticated data endpoint
with bounded transfers and cancellation. Source filesystem paths are never remote references.
Allocate C's output buffers locally. If source versions are not retained, return a revision-expired
error and restart the computation against a new vector; never read today's bytes as yesterday's
input. Version retention is therefore a prerequisite for reproducible live computation.

No arbitrary resampling, unit conversion, fill-curve conversion, or KEYED dictionary merging is
implied. Implement supported mediation explicitly and fail for unsupported combinations, as
required by [STORAGE](STORAGE.md).

### Scheduler and provenance

A and B keep running their own schedulers. C receives committed changes and schedules only
C-owned dependents. Importing an observation does not initialize or recompute it at its source.
Persist subscriptions and affected-origin dependency mappings alongside C's execution records.

Each C execution pins source versions, creates a fresh local transaction/activity, reads the
pinned data, executes in dependency order, and commits output/provenance before advancing its
processed-event state. Coalesce redundant invalidations only when no required intermediate
state is lost. Distinguish logical event time from message delivery time.

Use an execution idempotency key containing C, output, source revision vector, event interval,
and plan version. Concurrent paths to the same dependency must share work. Detect cycles in
both local dependencies and the composition graph. For cross-source derived events, carry causal
origin and ancestry so echoing the same commit cannot trigger endless recomputation. Hop limits
are a diagnostic safeguard, not the primary cycle algorithm.

Provenance records source asset identity, source revision, original producing activity where
visible, import/view activity, mediation, and C's computation. Do not attribute source work to
C or discard source histories. A vector gives reproducible inputs only if those versions remain
retrievable; it is not itself a distributed snapshot protocol.

## Creation protocol

Implement a proposed coordinator service method and authenticated runtime endpoint. Keep client
methods thin; resolve source scope tokens on their owning runtimes, through existing identity and
host-runtime headers. Possession of a URL, configuration, or message is not authorization.

1. Validate request idempotency, source identities, selection, worldview, lifecycle, graph cycles,
   and read/compute permissions. Check that C's requested visibility cannot expose source content
   to a broader audience. For shared C, enforce rights per viewer and per source.
2. Reserve C's identity and persist a `PREPARING` composition record and request key. Do not
   publish an active scope yet. Repeated requests with the same key return the same operation;
   mismatched payload reuse fails.
3. Establish authorized source subscriptions and buffer changes. Obtain source snapshot tokens
   and watermarks. A source must guarantee that snapshot S and replay after its watermark have
   neither a gap nor contradictory versions. Subscribe-before-snapshot alone is insufficient
   without this server-side contract.
4. Import each snapshot under its source watermark, allocate C mappings, and commit the initial
   projection. If a source lacks historical snapshots, require a source-side consistent export
   protocol before claiming this step works.
5. Replay buffered/durable changes after each watermark, in source sequence. Atomically apply
   each source change and advance its cursor. Restore dependency indexes and local executors.
6. Mark C `ACTIVE`, register its fully instrumented root scope in `ScopeManager`, and return
   its descriptor. Emit existing `DigitalTwinConnected` only after activation. The client creates
   its scope and projection using that descriptor, so all service requests address C.
7. On failure, mark the operation failed, release subscription leases, and clean only C's staged
   state. Preserve enough diagnostics/idempotency state to make retry safe. Never delete sources.

The public synchronous method returns an active scope or a descriptive exception within a
bounded deadline, never null. A long-running internal operation may expose a status endpoint;
retry with the original key resolves an ambiguous timeout instead of creating duplicate twins.

## Reliable messaging and recovery

The current transient, auto-ack AMQP channel cannot be the only copy of a source change.
Add an outbox/change record in the same source graph transaction as each visible modification.
A publisher retries sending those records; messages can be duplicated or reordered. Give every
source stream a contiguous sequence independent of global asset IDs and timestamps.

On receipt, validate schema, source identity, authorization, generation, and selection. Apply
only the next sequence, store the received key and new cursor atomically with C's projection,
then acknowledge. A durable consumer is useful, but an authenticated replay endpoint remains
necessary for outages beyond broker retention. Do not change acknowledgement semantics for
all existing notification users merely to add the federation consumer.

| Failure | Required behavior |
|---|---|
| Duplicate delivery | Idempotent no-op; do not rerun C outputs. |
| Sequence gap/out-of-order delivery | Buffer within limits and request replay; never silently advance past a missing change. |
| Source commits, publisher crashes | Outbox resumes publishing after restart. |
| C applies change, acknowledgement is lost | Redelivery sees the committed cursor/key and does not reapply. |
| Replay retention exceeded | Enter `RESYNCING`; obtain a new consistent snapshot, reconcile tombstones, and resume. |
| Source unavailable | Enter `DEGRADED`; show freshness and prohibit required fresh computations by default. |
| Permission revoked | Stop reads/replication, invalidate accessible cached payloads, and remove unauthorized projection content; pause dependent work. |
| Source deleted/recreated | Process tombstone/generation change; never attach old references to a new dataset with reused IDs. |
| C runtime restarts | Load composition, mappings, revision vector, subscriptions, and pending work before advertising active status. |
| C deleted | Cancel source leases and local work; remove C data only. |

Source deletion and permission changes need explicit protocol events and periodic authenticated
reconciliation. A missed notification must not preserve access indefinitely. Metadata and
provenance can themselves be sensitive. Bound replay buffers, snapshot pages, data caches,
retries, and connection attempts; expose actionable diagnostics and cursor lag.

## Delivery plan and acceptance gates

### Phase 0: repair local invariants

Finish the high-priority graph/scheduler findings in the companion documents. This review fixes
incoming edge orientation, dependency sequence selection, and server configuration/ownership
flags; allocator atomicity, authorization, transaction rollback, query capability validation,
temporal integration, and source data versioning remain prerequisites. Separate scope release
from durable deletion. Restore upstream test compilation and executable Neo4j contract tests.

**Gate:** two local contexts remain isolated under concurrent writes; dependency traversal is
correct after persistence; event execution produces committed, reproducible state or fails
explicitly; closing a handle does not accidentally delete durable state.

### Phase 1: contracts, schema, and source protocol

Add the DTOs, version negotiation, endpoint definitions, permission checks, composition schema,
stable identity mapping, source revision/outbox records, snapshot/replay/data-version APIs, and
migration/backfill rules. Existing twins start at a defined generation and baseline snapshot;
never invent historical commits that were not retained.

**Gate:** serializer round trips, unknown-version rejection, source snapshot/change races,
outbox crash recovery, identity collisions, and unauthorized-source tests pass.

### Phase 2: read-only composition

Implement coordinator activation/recovery and wire both `connect` stubs. Integrate graph and
storage facades through C's context. Add client mapping, pagination, provenance, and freshness
display. Source changes are visible through messaging and replay; new C computation can remain
disabled until phase 3, but must report that capability explicitly.

**Gate:** browse and query two runtimes as one C; changes converge, including deletion and link
property changes; equal numeric IDs remain distinct; restart, expired replay, and close are safe.

### Phase 3: cross-source computation

Make Resolver reuse and compiled actuator references origin-aware. Execute C outputs against
pinned input versions, record provenance, and wire invalidations to a transaction-safe scheduler.
Add explicit observer/geometry conflict handling and supported scanner mediation.

**Gate:** the catchment/land-use example computes through C, records both revisions, and
recomputes exactly once per accepted logical change despite duplicate deliveries. Unavailable
or expired inputs fail visibly. No source is modified by a C computation.

### Phase 4: nested views and operational hardening

Support nested composition with stable leaf identity and cycle checks, shared-view authorization,
bounded resynchronization, upgrades, metrics, and fault injection at all commit/publish/ack
boundaries. Source-write delegation is a separate feature requiring explicit conflict and
compensation semantics, not a prerequisite for this view-based API.

**Gate:** `connect(connect(A,B),D)` preserves origin identity, overlapping leaves are deduplicated,
cycles are rejected, and revocation propagates without unauthorized data remaining readable.

## Minimum end-to-end test matrix

Run real Neo4j-backed runtimes and a broker for protocol tests; use deterministic clocks for
scheduler tests. Do not substitute mocks for broker loss or database atomicity.

- Same host and different hosts; equal source numeric IDs; distinct equal-semantic observations.
- Focused selections, different observers, incompatible worldviews, mismatched units/grids/keys.
- Snapshot concurrent with add/update/delete; edge-property-only change; stable global pagination.
- Duplicate/reordered messages, dropped notifications, broker restart, source restart, C restart.
- Replay expiration, data-version expiration, source deletion/recreation, authorization revocation.
- Failure after source commit/before publish and after C apply/before acknowledgement.
- Dependency chain, diamond, local cycle, nested-view cycle, late event, and executor eviction.
- Release/close/delete C while sources remain open; persistent C reattachment and lease cleanup.
- Client queries and Resolver queries expose the same authorized C graph and identities.

Completion means the public call returns a usable new context with graph, storage, scheduler,
messaging, provenance, and lifecycle working together. A pair of subscriptions or a merged
client visualization is a useful phase deliverable, but does not complete `ContextScope.connect`.

[scope-api]: ../klab.core.api/src/main/java/org/integratedmodelling/klab/api/scope/ContextScope.java
[client-scope]: ../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/scope/ClientContextScope.java
[service-scope]: ../klab.core.services/src/main/java/org/integratedmodelling/klab/services/scopes/ServiceContextScope.java
