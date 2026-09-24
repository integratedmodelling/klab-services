# Storage in the k.LAB runtime

The [persistent-twin restoration guide](PERSISTENT_TWINS.md) describes restart behavior. Restored
shard groups now require complete, ordered, type-consistent descriptors with geometry. Lazy reads
report missing slices/files explicitly and never allocate replacement values in a read-only scan.

This document is the central implementation guide for observation storage in k.LAB. It describes
how a quality receives a storage contract, how that contract becomes shards and primitive buffers,
how contextualizers access the buffers, and how data is finalized and persisted. The public API is
defined by `Storage`, `StorageScan`, `StorageManager`, and `Data.ShardingStrategy`; the main implementation is in
`StorageManagerImpl`, `StorageImpl`, and `ScannerAdapters`.

Storage belongs to a digital twin. A `StorageManager` owns one `Storage` per quality observation,
and a `Storage` owns time-indexed groups of non-overlapping `Shard` objects. A shard owns one native
primitive buffer. Contextualizers never receive the buffer itself: they access it sequentially
through a typed `Storage.Scanner`.

See [DIGITALTWINS](DIGITALTWINS.md) for the owning twin and scheduler lifecycle,
[KNOWLEDGE_GRAPH](KNOWLEDGE_GRAPH.md) for descriptor transactions, and
[DISTRIBUTED_TWINS](DISTRIBUTED_TWINS.md) for the proposed connected-twin storage facade.

## Core contract

The three layers of the storage contract are:

| Layer | Responsibility |
|---|---|
| `Data.ShardingStrategy` | Declares primitive type, fill curve, split count, minimum split size, and maximum buffer size. |
| `Storage` and `Storage.Shard` | Own the observation data and describe its physical partitioning for an event. |
| `Storage.Scanner` | Provides sequential, primitive, optionally read-only access to one shard. |

The native strategy is part of `Observation.ContextualizationData`. Once storage exists, that
strategy describes the bytes in its shards and must not be silently reinterpreted. A setting change
affects storage attributed after the change; it does not migrate existing storage.

`Storage.Type` maps to scanner and buffer types as follows:

| Native type | Scanner | Primitive buffer width | Normal semantic use |
|---|---|---:|---|
| `DOUBLE` | `DoubleScanner` | 8 bytes | Numeric quality, full precision |
| `FLOAT` | `FloatScanner` | 4 bytes | Numeric quality, reduced precision |
| `INTEGER` | `IntScanner` | 4 bytes | Integer state |
| `LONG` | `LongScanner` | 8 bytes | Long integer state |
| `KEYED` | `KeyScanner` backed by integer codes | 4 bytes | Worldview-bound concept state; zero is missing |
| `BOOLEAN` | `BooleanScanner` | 1 byte | Presence or verification state |

The generic `Scanner` deliberately has no boxed `get` or `add` operation. Typed scanners exist so
large contextualizations can execute without per-value allocation or boxing.

## Attributing the native strategy

`CompiledDataflow.harmonizeSharding()` attributes storage before the first contextualization of a
new quality:

1. Local, model, the producer’s own Java function/adapter declarations, then runtime strategies are merged; later concrete fields win. Numeric
   primitive types may override one another; incompatible semantic types fail.
2. Runtime defaults derive type from semantics (numeric DOUBLE, categorization KEYED, verification
   BOOLEAN), curve from geometry, and split count from available processors. These concrete fields
   currently override earlier hints. Runtime sizes are neutral zeroes.
3. `USE_SHORT_FLOAT_REPRESENTATION=true` selects FLOAT instead of DOUBLE.
   `PARALLELIZE_OBSERVATIONS=false`, or nondistributed space, unconditionally forces one split and
   clears both size hints for newly attributed storage. This remains a supported test mode.
4. The result is recorded on observation contextualization data. Storage creation requires it.
   Existing positive-ID observations keep their recorded native strategy.

`CallDescriptors.shardingStrategy()` contributes the producer’s Java declarations. Child strategies
are no longer propagated into the parent’s attribution. Dependency reads use ephemeral requests
against explicit output partitions, so existing inputs retain their own native layouts and types.
The runtime curve is `D2_XY` for regular 2D space, `D3_XYZ` for regular 3D space, otherwise
`D1_LINEAR`. Strategy getters return copies; incomplete native strategies are no longer silently
replaced by scan requests.

### Java sharding annotations

These declare layout preferences, not unit conversion or resampling. Registration mappings are:

| Field | `@KlabFunction` | `@ResourceAdapter` | `@Exporter` |
|---|---|---|---|
| Suggested partitions | `split=-1` | `splits=1` | No member; descriptor -1 |
| Traversal | `fillCurve=UNSPECIFIED` | Same | Same |
| Soft minimum states per shard | `minSizeForSplitting=0` | Same | No member |
| Maximum states per shard | `maxSize=0` | Same | No member |
| Primitive type | Derived from artifact type: NUMBER/CONCEPT/BOOLEAN become DOUBLE/KEYED/BOOLEAN | Unspecified | Unspecified |

`-1` means unspecified split preference, `1` means one partition, and positive counts are
suggestions to the geometry splitter. Sizes count states, not bytes; zero means unspecified.
Validation rejects zero or less than -1 splits, negative sizes, and a minimum above a positive
maximum. The adapter default of one partition deliberately remains more conservative than the
function default. Java member names remain unchanged. `parallel` and `reentrant` describe execution
and do not replace these fields.

`ComponentRegistry` validates function strategies and copies all four adapter fields, including
`maxSize`, into `AdapterDescriptor`. Its `shardingStrategy()` has no primitive type. A method's
`@KlabFunction` supplies its own defaults: it does not inherit class-level sharding fields.
Exporter registration records its curve; invocation applies it to a single read-only traversal
across the observation’s native shards. UNSPECIFIED selects the source’s native curve.

```java
@KlabFunction(name = "sample", description = "Sample a quality",
    type = Artifact.Type.NUMBER, split = 4,
    fillCurve = Data.FillCurve.D2_YX, minSizeForSplitting = 1024, maxSize = 65536)
```

The native splitter bypasses size hints for positive counts and uses them only to derive an
unspecified count. Planned reads additionally reject native shards exceeding a requested maximum.
Function/adapter layout declarations participate in new producer attribution with the precedence
above; they are preferences, not independent per-input traversal requirements. Every input in one
output task follows that task’s geometry and curve. Dynamic `@Splits`,
`@SplitSize`, and adapter `@FillCurve` methods mentioned in TODOs are not implemented contracts.

### k.IM sharding annotations

`DataflowCompiler` uses the shared `ShardingAnnotations.parse()` decoder. Canonical names are
lowercase; positional and named `value` syntax both work with the parser's normalized `_p1` value.
Programmatic beans can use either representation, but supplying both is ambiguous and rejected.

| Annotation | Value | Meaning |
|---|---|---|
| `@type("float")` | Case-insensitive Storage enum | Primitive type preference |
| `@split(4)` | Integer: -1 or positive | Suggested partition count |
| `@maxsize(65536)` | Nonnegative long | Maximum states per shard |
| `@minsplitsize(1024)` | Nonnegative long | Soft minimum states per shard |
| `@fillcurve("D2_XInvY")` | Case-insensitive FillCurve enum | Traversal preference |

`@split(value=4)` and `@split(4)` are equivalent. Mixed-case enum members such as `D2_XInvY`
are preserved. Missing, ambiguous, malformed, or out-of-range arguments fail validation. Known
historical camel-case bean names are rejected with a lowercase-name diagnostic; the source grammar
already rejects them. `UNSPECIFIED` is a neutral declaration, not an executable traversal.

Concept annotations contribute first, model/dependency annotations override them, and explicit
observation definitions have highest precedence. `override=true` can affect ordinary concept/model
precedence; see [ANNOTATIONS.md](ANNOTATIONS.md). Replacement is by name. Model annotations apply
only to the main output. Runtime strategy merging is a separate step with the precedence above.
Parsing/validation and planned conformant reads are implemented; full runtime binding is staged in
[STORAGE_PLAN.md](STORAGE_PLAN.md).

### Fill-curve implementation boundary

`D1_LINEAR`, `D2_XY`, `D2_YX`, and `D2_XInvY` have tested mappings in supported dimensions.
For shape [X,Y], `D2_XY` varies Y fastest, `D2_YX` varies X fastest, and `D2_XInvY` reverses Y
within each X block. `D3_XYZ` is row-major; `D3_ZYX` currently aliases it. Hilbert mapping throws.
`FillCurve.map` accepts int and `Mapper.offset` narrows to int: these are not proven large-index
mediation APIs. Planned reads reject UNSPECIFIED, D3_ZYX, and Hilbert curves. Planned conformant reads
use a separate checked long-index codec; the legacy helper implementations remain unchanged.

## Creating and finding storage

The hosting `DigitalTwinImpl` owns a `StorageManagerImpl`, created with its service context.
Focused and peer scopes must not be assumed to own independent storage. The manager indexes
storage by observation ID within the twin:

- `createStorage(observation)` creates it lazily from the observation's attributed strategy;
- `getStorage(observation)` returns an existing entry or reconstructs one from contextualization
  data and knowledge-graph shard descriptors;
- `finalizeStorage(temporaryId, finalId)` rekeys storage when transaction commit assigns the
  persistent observation ID;
- `clear()` and `deleteStorage(...)` close mapped buffers and remove the context's persisted files.

Exiting a client or the IDE disconnects local scope peers only. It must not call runtime
`releaseContext` or `releaseSession`: those operations close server scopes and can delete
context graphs and storage. Runtime persistence and timeout policies continue to govern
unattended twins; explicit deletion remains a separate operation.

Calling `getStorage` without an existing or reconstructable contract is an error. Storage creation
is not the place to infer semantics or consult current settings: attribution must already have
happened during dataflow compilation.

## Shards, events, and buffers

Initialization shard groups use timestamp zero. Temporal reads resolve exact event-keyed shards,
a covering revision, or an allowed committed baseline and reject missing state. Other moving
dimensions are not fully generalized. `LocalTemporalWriteSet` stages temporal data;
`stageTemporal()` exposes committed buffers/descriptors only through transaction callbacks and
cleans them on rollback. Ordinary `AbstractExecutor` still rejects temporal quality outputs.

First native allocation splits event-local geometry. Physical shard descriptors record geometry,
index/count, timestamp, strategy, persistence policy, and native type. Splitting uses plain geometry
rather than reconstructing service-local scales. No scanner mediation is implied by native splitting.

`ShardStorage` allocates an ojAlgo mapped `BufferArray` of the exact primitive width. Scratch buffer
files live in the storage manager workspace. They are closed when the manager closes; on Windows,
ojAlgo may retain mappings until garbage collection, so deterministic deletion of every scratch
file is not currently guaranteed.

## Scanning and contextualizer binding

`Storage.scan(event, request, scannerClass, readOnly)` is the access boundary. For a request whose
strategy equals the native strategy, it opens one scanner per native shard.
Write scanners reset the shard histogram only after pending persistence has been flushed; read-only
scanners reject `add(...)`.

The requested scanner class is a real contract, not a hint. The storage layer either returns an
instance assignable to that class, supplies a compatible primitive adapter, or fails explicitly.
It must never return a scanner of another type and defer failure to reflection.

During a quality contextualization, `AbstractExecutor` previews native output partitions and plans
read-only dependency sessions against those exact partitions before opening writable output scanners.
It constructs one task per output shard and binds
component method parameters by the declared input/output name. Parameters may request:

- the `Observation` itself;
- `StorageScan.View` for consumer geometry, traversal and source metadata;
- a physical `Storage.Shard` only when it describes the bound input without mediation;
- generic `Storage.Scanner`;
- the matching typed scanner;
- `DoubleScanner` over native float storage, or `FloatScanner` over native double storage.

The last two cases use `ScannerAdapters`. These are primitive, write-through decorators. A
float-to-double read uses primitive widening; a double-to-float write uses primitive narrowing and
therefore has exactly the precision loss requested by `USE_SHORT_FLOAT_REPRESENTATION`. The reverse
adapter supports a component explicitly requesting floats over double-native storage. The adapter
delegates `shard()`, `size()`, `hasNext()`, and `nextLong()` to the native scanner, so cursor,
geometry, histogram, mutability, and persistence identity are unchanged. It allocates one small
adapter per scanner, never one object per value and never a converted buffer.

No conversion is permitted between floating-point scanners and boolean or keyed scanners. Such a
declaration is a component contract error and fails during binding with the native and requested
types in the message.

### Legacy cursor and export boundary

Legacy `get()` and `nextLong()` both advance; `peek()` does not. Legacy scanner exhaustion checks
are not the strict planned-session contract below. Legacy `scan()` remains the native compatibility
entry point. Runtime dependency and exporter bindings use the planned path described under S3;
`ScannerAdapters.mergeScanners()` is no longer used to acquire observation-wide export inputs.

### Component author rules

Component contextualizers should follow these rules:

- Declare `DoubleScanner` when the algorithm operates in double precision. It remains compatible
  with short-float runtime storage through primitive mediation.
- Declare `FloatScanner` only when float arithmetic is intentional. It can consume double-native
  storage, but reads narrow to float.
- Declare generic `Scanner` only when the implementation does not call typed value methods or
  dispatches explicitly by scanner type; for planned scans, use `scanner.view().valueType()`.
  The physical shard type may differ from the consumer value type.
- Mark input and output parameters accurately. Inputs receive read-only scanners; writing through
  them is always an error.
- Do not retain a scanner beyond the contextualizer invocation or share it with another shard task.
  A scanner owns mutable cursor state.
- Do not assume one shard. Parallelism settings and geometry can produce several independent
  invocations.

Components do not need recompilation when `USE_SHORT_FLOAT_REPRESENTATION` changes. Scanner
interfaces remain the API contract, and the runtime mediates the compatible floating-point
representation at invocation time.

## Finalization, histograms, and persistence

After a shard task succeeds, `Storage.finalizeRun(nativeScanner)` snapshots the shard histogram and,
when the digital-twin persistence policy survives shutdown, schedules the native buffer for
persistence. Finalization deliberately receives the native output scanner rather than a component
adapter.

Persistence runs through a single maintenance executor. Before a writable scanner resets data or
histogram state, pending persistence is flushed so an asynchronous writer cannot observe a new
run's mutations. Each shard is written to a unique temporary file and moved into place atomically
when supported by the filesystem.

The current file format contains a magic number, format version, native type, element width, value
count, and a big-endian primitive payload. Reads validate the header and exact file length. A
backward-compatible reader accepts the original headerless native-endian format when its length is
exactly valid. Knowledge-graph `HAS_DATA` relationships retain shard descriptors; the files retain
the primitive payload.

### Graph atomicity and recovery boundary

Initialization flushes payloads before descriptor publication, but graph and filesystem operations
are not one atomic transaction. A graph failure may leave orphan files; rekeying IDs before graph
commit is not proof of durable publication. Temporal writes use detached versioned buffers and
transaction callbacks; their committed visibility does not provide general initialization rollback.
See [PERSISTENT_TWINS.md](PERSISTENT_TWINS.md) and [DIGITALTWINS.md](DIGITALTWINS.md) for restoration
and ownership/closure behavior. Garbage collection of unreferenced files remains separate work.

### Connected twins: proposed contract

Current managers use local observation IDs and local files. They cannot directly serve a union of
independent twins with overlapping numeric IDs. A connected twin needs an origin-aware read-only
provider for imported observations and ordinary local storage for its own outputs. Cache keys must
include source twin/generation, observation, revision, time slice, shard, and representation.

Read source shards through authenticated, versioned data requests; do not send filesystem paths
or primitive buffers as graph-change messages. Pin data versions to the source revisions recorded
in output provenance. If the requested version has expired, fail explicitly rather than substitute
current bytes. Geometry, units, fill curves, and keyed dictionaries still require supported
mediation. These facilities are planned in [DISTRIBUTED_TWINS](DISTRIBUTED_TWINS.md), not implemented
by the existing storage manager.

## Concurrency and failure behavior

Different shard scanners may execute concurrently on virtual threads. A scanner and its cursor are
task-local. `StorageImpl` uses concurrent maps for shard groups and backing state, while shard
creation is synchronized to prevent duplicate allocation.

Scanner contract failures must be reported before reflective contextualizer invocation. Execution
retains the first concrete task failure as the executor cause; the generic `Execution failed`
exception is used only when no more specific cause was recorded. This distinction is important
because collective contextualization otherwise reports only the parent failure.

## Planned read sessions (S1)

`Storage.plan(StorageScan.Request<T>)` validates metadata without opening buffers or resetting
histograms. `Storage.open(plan)` acquires a read session. Providers without these additive
capabilities reject the methods; the legacy `scan(...)` signature remains available.

```java
var request = StorageScan.Request.nativeRead(
    event, storage.getNativeShardingStrategy(), Storage.DoubleScanner.class);
var plan = storage.plan(request);
try (var session = storage.open(plan)) {
  for (var scanner : session.scanners()) {
    while (scanner.hasNext()) {
      boolean valid = scanner.isValid();
      double value = scanner.get();
      // Consume the primitive value and validity.
    }
  }
}
```

Requests snapshot mutable strategies and events into immutable `Layout` and `Slice` records.
Semantic definitions either match the native source or require a supported S4 conversion. An exact
native request keeps its native partitions and fast path; other layouts use the S2 conformant planner
described below. Empty
consumer partitions mean automatic partitioning, not an empty selection. Explicit partitions have
unique identities and must cover the same cells without overlap or gaps. Positive maximum size
limits consumer partitions, not the underlying physical buffers.
Sources must be finalized initialization shards or restored/committed data. Unsupported layouts,
semantics, coverage/sampling policies, writes, keyed types, and curves fail before buffer access.

### Ownership and portable descriptions

Plans belong to their issuing storage instance. `Description` records contain immutable
source descriptors, event, semantic definitions, layouts, partitions, precision, budgets and
operation metadata. JSON round trips preserve structural equality; inconsistent descriptions and
unknown versions are rejected. A description is not an executable plan or permission to open data.

`fingerprint()` is SHA-256 over length-prefixed UTF-8 fields in record/list order. An opaque source
revision token incorporates local storage identity and write generation, so changing initialization
values changes the fingerprint. This is not a durable content hash or a cross-provider cache key.
Temporal source URNs/timestamps identify concrete committed shards. S4 persists binding intent on
AFFECTS and executed descriptions in Activity metadata; executable cursors, converters and buffers
are never serialized.

Opening rechecks generation, observation geometry, semantics and source descriptors. Stale plans fail and require
explicit replanning. An open session leases its source, blocking initialization writes (including
previously obtained writers), writer resets and storage closure. Concurrent sessions have
independent task-local cursors. Close/cancel is idempotent and invalidates scanners, closes reader
handles and releases the lease. Storage owns mapped buffers. Partial opening failure closes acquired
handles and newly restored buffers before releasing the lease. Always use try-with-resources.

### Consumer view, cursor and precision

`shard()` identifies physical native storage when the view touches exactly one physical shard.
For a view spanning several shards it throws `UnsupportedOperationException`; use
`view().sources()` for the physical source descriptors and `view().partition()` for consumer
geometry. No synthetic or arbitrarily selected physical shard is returned. `view()` also describes
consumer type, semantics, slice and traversal. Views are never persisted as `HAS_DATA` shards. View histograms are
explicitly unavailable; native histograms are not converted-view statistics.

`position()` is the next offset, equal to `size()` at exhaustion. `get()` and `nextLong()` each
consume one position; `peek()`, `isValid()` and `location()` do not advance. Value/location access
after exhaustion throws `NoSuchElementException`; access after close throws `IllegalStateException`.
Writes fail without advancing. Location contains partition, curve, slice and long offset; public spatial-locator
construction and cell metrics remain future work (S2 uses internal grid-coordinate decoding). Legacy cursor behavior is unchanged.

Default `LOSSLESS` supports native DOUBLE/FLOAT/INTEGER/LONG/BOOLEAN and FLOAT to DOUBLE.
DOUBLE to FLOAT requires `ALLOW_FLOAT_NARROWING`, using Java IEEE narrowing including overflow to
infinity. Integers never pass through double; long precision is retained. Other casts, including
boolean arithmetic, are rejected. Floating NaN is invalid; infinity is valid. Integer/long/boolean
storage has no missing-value bitmap, so zero and false are valid.

### Performance and consumer boundaries

Numeric cursors, validity checks and indexed reads use primitive accessors without per-value boxing,
streams or locator allocation. Metadata and sessions allocate per request/partition; locations
allocate only on explicit demand. Block reads fill caller-owned primitive arrays with one
reader-local lock per block. Independent shards do not share a read lock; scalar reads coordinate
with cancellation on their own reader. No full-dataset copy or per-cell index array is constructed.
`Budget` limits metadata partitions (default 65536) and values per block (default 65536), not total
dataset size. This does not guarantee that downstream consumer code is allocation-free.

Exporters and individual-value API text responses are required consumers of this same contract.
Their shared binding is implemented in S3 below; ordinary semantic conversion remains S4 and
contextual conversion S5. Text formatting follows location selection, validity and primitive mediation.

## Conformant scanner mediation (S2)

The local provider advertises `CONFORMANT_READ`. Its planned-read path supports one-to-many,
many-to-one and many-to-many layouts for all five native primitive types. Requested partitions
may cross physical shard boundaries or arrive in a different order. Values are located from
persisted shard geometry, not list position, equal-size assumptions, or transient parent metadata.
The original native strategy, buffers, descriptors and histograms remain unchanged. The legacy
`scan(...)` overload still rejects a different strategy; runtime/export/text consumers use planned sessions.

```java
var viewStrategy = storage.getNativeShardingStrategy(); // a defensive copy
viewStrategy.setSuggestedSplits(1);                    // merge into one consumer traversal
viewStrategy.setCurve(Data.FillCurve.D2_YX);
var request = StorageScan.Request.nativeRead(event, viewStrategy, Storage.DoubleScanner.class);
try (var session = storage.open(storage.plan(request))) {
  var scanner = session.scanners().getFirst();
  // scanner.view() describes the merged traversal; scanner.shard() may be unavailable.
}
```

### Conformance and traversal

Remapping requires nonempty, axis-aligned, rectangular regular spatial grids with one to three
axes, a known matching CRS, equal cell resolution and aligned cell edges. Source partitions and
consumer partitions must each be disjoint and cover the observation's rectangular spatial support
exactly, including its outer boundaries.
Missing bounds, masks/nonrectangular support, changed CRS/resolution, fractional cell offsets,
coverage gaps/overlaps and unresolved distributed non-spatial dimensions fail during planning.
Bounding-box overlap alone is never treated as conformance.

CRS identifiers are compared literally; this path does not infer equivalence or reproject.
Persisted native splits that omit a CRS inherit their owning observation's CRS. A spatial-only
consumer geometry inherits the selected slice's non-spatial context. When supplied explicitly,
non-spatial geometry must match the located source; the unchanged full observation geometry may
also describe coverage, while the request's `Slice` selects the event. It does not request a
second temporal resampling. Explicit partition geometry should use spatial-only or event-local
extents, not a different temporal interval.

Cell-size ratios allow relative error up to 1e-9. Cell edges must be within max(1e-8, eight ULPs)
of an integer lattice coordinate; requests requiring a tolerance greater than 1e-4 cells are
rejected as numerically ambiguous. This accommodates serialization round-off, not subcell shifts.
All index products, volumes and endpoints use checked long arithmetic. Large spatial coordinates
whose floating-point bounds cannot identify cells reliably are rejected. Local physical buffers
retain their existing size limits; long total indexing does not remove those backend limits.

| Curve | Meaning in the planned reader |
|---|---|
| `D1_LINEAR` | Row-major mixed-radix order over the located spatial axes |
| `D2_XY` | Y varies fastest |
| `D2_YX` | X varies fastest |
| `D2_XInvY` | Y varies fastest in reverse within each X position |
| `D3_XYZ` | Z varies fastest, then Y, then X |

Dimensional curve mismatches are rejected. `D3_ZYX` remains rejected because legacy storage
currently aliases it to XYZ; S2 does not reinterpret existing bytes under a different meaning.
Hilbert and UNSPECIFIED remain unsupported. Traversal applies within each consumer partition;
a single globally ordered traversal requires one partition. Float widening/narrowing and validity
compose with remapping under the S1 precision rules, preserving exact integer/long/boolean data.
Exact native scalar/empty reads retain their fast path and strict cursor behavior; remapping an
empty or non-spatial grid is not required to fabricate partitions or source cells.

### Partition policy, metadata and resource bounds

An empty partition list asks the planner to derive the requested layout. It repeatedly divides the
largest box along its longest axis, producing deterministic integer-cell partitions, including
uneven ones. A positive split count is a preference, bounded by available cells and the soft
minimum. With split=-1, a positive minimum suggests total/minimum partitions; otherwise one is
preferred. A positive maximum takes priority and may increase the count beyond the preference.
An explicit partition list defines the exact consumer order and boundaries; its sizes must obey
the maximum. The minimum does not reject unavoidable small partitions. `PARALLELIZE_OBSERVATIONS`
continues to control native output attribution; this readonly view operation does not launch tasks.

Version 1 descriptions remain native-identity descriptions. Version 2 permits different source
and consumer partitions and records `INDEX_REMAP` before the primitive value operation. JSON and
fingerprint round trips include the requested layout and explicit consumer order. The structural
record validates the version/pipeline; compiling a usable plan also validates geometric coverage.
Null optional layout types use a reserved null marker in fingerprints. WKT commas are preserved
when decoding geometry text, including strings produced by the existing geometry encoder.

The compiled plan contains a balanced bounding-volume directory and partition metadata, never
an index per cell. Each mapped reader reuses a fixed coordinate array and its last source lookup.
Block reads transfer contiguous native spans directly into caller-owned primitive arrays; other
traversals use bounded primitive gathers. No converted buffer, boxed value or per-cell locator is
created. Reader-local synchronization protects scratch coordinates/cancellation; source handles
remain session-owned and leased as in S1.

Both source and consumer counts must fit `Budget.maxPartitions`. Source-to-consumer references
are capped at eight times that budget, preventing cross-cutting layouts from creating unbounded
metadata. Violating a budget fails before opening readers. The local metadata-only LRU cache has
at most 16 entries and admits only plans with at most 1024 combined source, target and reference
items each. Keys include source generation/descriptors, observation geometry/identity, semantics,
slice, partitions, type, precision and budgets. Large plans remain usable but bypass the cache.
Caches hold no payload buffers. Stale plans still fail the open-time checks.

## Unsupported or incomplete operations

### Contextual mediation boundaries

Ordinary unit, range and pinned-currency conversions are supported by planned reads, as specified
below. Unit algebra and locator-dependent contextualization remain incomplete. `AbstractMediator`
can execute supplied dimension-factor operations, but no complete scanner compiler supplies the
required per-cell operations/locators. `UnitImpl.aggregatedDimensions` is metadata, not evidence of
working contextual conversion. `ShapeImpl.getStandardizedArea()` computes a metered area, not square
degrees; per-cell geographic area and calendar duration policies remain S5 work. KEYED support is
specified in the S7 section below.

### Remaining boundaries

The following remain explicit implementation boundaries:

- spatial mediation outside the S6 support matrix below, and mediation through the legacy scan overload;
- contextual units requiring cell area/volume/duration, nonlinear conversions, and integer-valued semantic conversion;
- temporary scanners from `StorageManager.getTemporaryScanner(...)`;
- complete generalized indexing for moving dimensions other than time;
- cleanup of orphan persisted shards and fully deterministic mapped-file unmapping;
- applying partial-quality actuator coverage to execution storage.

Callers must not silently fall back to native scanners when one of these operations is requested.
An explicit unsupported-operation failure protects buffer order, type safety, and data integrity.

## Verification

Focused scanner mediation tests are in `ScannerAdaptersTest`. Storage width and durable buffer
round-trip tests are in `StorageManagerImplTest`; broader persistence and contextualization tests
exercise storage through the runtime module. The minimum focused verification is:

```powershell
.\mvnw.cmd -pl klab.core.services -am "-Dtest=ScannerAdaptersTest,StorageManagerImplTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Changes to attribution or executor binding should additionally compile and test
`klab.services.runtime` because that module owns sharding harmonization and component invocation.


## Consumer integration (S3)

### Contextualizers and binding validation

`Storage.writeLayout(event)` previews the ordered native output partitions without allocating
buffers, creating scanners or resetting histograms. `AbstractExecutor` requests those explicit
partition IDs, geometries and sizes for every quality dependency, with the output curve. It plans
all inputs, opens their sessions, and validates reflected parameter requirements before opening
writable output scanners. It verifies the actual native output geometry against the preview.
Planning errors retain the binding name, source/output identity and original cause. Sessions close
after all tasks have completed, including executor cancellation and reflection failures.

Generic inputs retain their source type. Each typed function input independently requests its Java
scanner type; the output type does not dictate input representation. Explicit FloatScanner bindings
permit IEEE double-to-float narrowing; otherwise requests are lossless. Incompatible integer,
boolean and floating-point bindings fail before output acquisition. Observation parameters retain
their observation; StorageScan.View parameters describe the consumer. A physical Shard parameter
is rejected before writing if the input spans shards or changes its geometry, curve or type. Native
output Shard parameters and native output finalization remain unchanged. Native output View parameters
have the output task geometry and no read-source descriptors.

Function and adapter declarations affect their own producer attribution, with the same runtime
overrides documented above. They do not force dependencies to be rewritten. Adapter resource input
payload transport remains the existing adapter API: this stage does not implement the pre-existing
`AbstractResourceContextualizer.getInputData()` placeholder or remote dependency transfer.

### Export lifetime and temporal selection

`StorageReads` constructs shared requests for consumers. `ArgumentMatcher` obtains one planned
read-only scanner per requested exporter scanner parameter using the exporter's declared curve and
Java type. A request merges all native shards into one traversal. `LanguageService` owns these
sessions through `ScanResources`; a returned InputStream takes ownership until explicit close,
EOF or read failure. A synchronous result, failed reflection call or unmatched overload releases
its sessions immediately. Caller-supplied scanners retain caller ownership.

An omitted event is allowed only when the observation has at most one temporal state, selecting
initialization. A multi-state export must supply `storageEvent` (event key), `storageStart` and
`storageEnd` (epoch milliseconds) in export parameters. All three are required together; they
construct an immutable TEMPORAL_TRANSITION Slice and use normal committed revision selection.
Unsupported coverage, curves and semantics fail without falling back to a native writable scanner.

### Detached quality queries and individual values

ID-zero quality queries now bind to an existing positive-ID source in the authorized context.
Their metadata contains exactly one `IM_QUERY_SOURCE_IDS` entry. Requested geometry and Observable
remain on the detached result; contextualization metadata and native strategy beans are copied.
Storage lookup resolves the durable source through the context and never indexes ID zero.
`createStorage()` rejects detached queries. Different query views do not mutate the producer or
create graph observations, activities or data. A changed unsupported value mediator or
non-conformant geometry uses the S6 policy below; contextual conversion remains S5 work.

`RuntimeService.readValue(StorageScan.Point, ContextScope)` and the matching RuntimeClient call
provide point access. HTTP `POST /api/v1/observation/value` accepts a Point JSON object and returns
`text/plain`. Point contains a positive `sourceId`, an explicit `slice`, `curve`, optional full-view
`geometry` and `semantics`, and a zero-based `offset`. UNSPECIFIED curve selects native traversal.
The offset belongs to one complete consumer traversal, not a physical shard. Context authorization
and source lookup precede planning. The service checks bounds before opening the session, seeks
directly with `Scanner.seek(long)`, checks validity, then formats the typed primitive. No preceding
cells are read and no full result is materialized. Seek permits size as the exhausted position;
value access there fails. Legacy writers need not implement seek.

Text is locale-independent Java primitive text: exact decimal longs and integers, `true`/`false`,
and Float/Double text with decimal points and optional exponent. Missing is the literal `null`;
zero and false remain valid. Text formatting allocates only at the API boundary. Point selection
uses the same provider plan and revision rules as dependency/export reads. Ordinary value conversions use the same S4 kernel as the other read routes.

### Transactional temporal reads

`TemporalWriteSet.writeLayout()` previews output tasks. `read(observation, request, PRIOR|CURRENT)`
validates the event and conformant request and returns a closeable snapshot: PRIOR pins the causal
baseline; CURRENT includes sparse pending changes captured when opened. Later writes cannot change
an existing read. `TemporalScalarExecution` opens aligned input snapshots before acquiring output
writers. Ordinary contextualizers still reject non-initialization quality outputs without a write
set. Commit/rollback also closes outstanding temporal read sessions; finalization and publication
continue through the existing write-set protocol.

Temporal views identify transaction-local source descriptors, never persisted HAS_DATA shards, and
cannot be passed as a physical Shard. Baseline reads and coordinate mapping use primitive kernels.
Sparse changed offsets are sorted into primitive long arrays when a snapshot opens; lookups do not
box their offsets. Existing transaction writers still retain boxed sparse change values. Neither
this integration nor the native read path materializes a converted dataset.

### Harness

`core.inspector.scancheck(context, observation, curve, splits, samples)` compares bounded samples
across partitioned, single-traversal and indexed-text views and verifies the producer contract stays
unchanged. Limits are 256 requested partitions and 64 samples per partition. It is an integration
check; independent codec oracles remain JUnit tests. `celltext(context, observation, curve, offset)`
exposes the same text reader to assertions. Both use initialization selection and propagate backend
errors. See [TESTING.md](TESTING.md) and the executable storage testcase linked there.


## Ordinary value mediation (S4)

Providers advertise `VALUE_MEDIATION` when they support the ordinary conversion contract.
A read request may change exactly one ordinary value mediator while retaining the same semantic
concept, observation contextualization and observer. `StorageScan.Semantics.meaning` carries that
non-value identity; `observable` retains the complete source/requested URNs for provenance. Legacy
five-argument semantic snapshots default meaning to observable, so callers using them must retain
the same observable identity. Contextual extent distributions must agree. Missing definitions,
changes to multiple mediators and non-affine units fail during planning. Spatial coverage follows S6 below.

`ValueMediation` compiles immutable coefficients before readers open. `StorageScan.Description`
version 3 contains a `Conversion` and the ordered pipeline `INDEX_REMAP, VALUE_CONVERSION, <primitive
adaptation>`; index remapping may itself be identity. Versions 1 and 2 remain readable and retain
their previous fingerprint encoding. Portable descriptions are evidence, not executable handles:
providers must replan before reopening. Native source semantics, bytes and HAS_DATA descriptors do
not change. Converted histogram metadata is unavailable.

The initial conversion path accepts native FLOAT/DOUBLE and FLOAT/DOUBLE consumers. Arithmetic uses
primitive double values, with one final float cast where requested; double-to-float still requires
`ALLOW_FLOAT_NARROWING`. Native FLOAT results round to their requested float representation. Semantic
conversion of INTEGER/LONG/BOOLEAN fails explicitly rather than silently rounding an integer or
losing long precision. Their identity views remain exact. No converted dataset is materialized,
and mapping/coefficients/bounds never allocate per value. NaN passes through before arithmetic;
valid zero remains valid. `peek()` is repeatable and does not advance, and successful `get()` advances
once. A failed range read leaves the cursor on the offending value.

### Units and ranges

Unit conversion is destination-first: `meters.convert(2, millimeters)` and
`UnitService.convert(2, meters, millimeters)` both return 0.002. `UnitService.conversion(destination,
source)` compiles a positive finite multiplier and finite offset. Compatibility now uses the unit
library; serialized `UnitImpl` definitions are reparsed without requiring transient converter data.
Scaled definitions do not contaminate the base-unit cache. Ordinary Celsius/Kelvin affine conversion
is supported. Depth-to-volume, calendar months/years and other location-dependent operations await S5.

Range snapshots encode `lower:lowerExclusive:upper:upperExclusive`. Conversion requires finite,
strictly increasing bounds, finite widths, and matching endpoint inclusion at both ends. It maps
linearly and monotonically; included endpoints map exactly. Unbounded/degenerate/reversed snapshots,
out-of-source-domain values and rounded results outside the target domain throw. No clipping,
extrapolation or implicit reversal is performed. NaN remains missing. The public numeric-range
upper-exclusion accessor now reports the correct endpoint.

### Pinned currencies

`CurrencyService.RateProvider.quote(source, target, valuation, operation)` is the deterministic
provider boundary. Deployment chooses a provider explicitly; scanning never fetches a rate. A
`CurrencyService.Rate` pins source/target definitions, UTC `Instant` valuation, provider, provider
version, operation, positive finite factor, and `IEEE_754_BINARY64` rounding. This is numerical
storage conversion, with no implicit rounding to monetary minor units. Missing or mismatched quotes
fail. Direct `CurrencyImpl.convert` preserves identity and otherwise throws instead of returning null.

Currency definitions are uppercase `CODE` or `CODE@YYYY`. EXCHANGE preserves the base-year suffix;
INFLATION preserves the currency code and changes the base year. A compound exchange-plus-inflation
request must be separated explicitly. No live provider or automatic currency selection is installed.

Pass the pinned quote in `StorageScan.Request.rate` or `StorageScan.Point.rate`. Consumer/query
observations carry the quote as JSON under `StorageReads.RATE` (`im:storage-currency-rate`). Ordinary
unit/range conversions require no rate. Dependency and temporal bindings propagate the consumer's
quote; query results use the query's quote and never inherit an unrelated source rate.

### Dependency provenance and recovery

Each named dependency has its own AFFECTS relationship, even when two bindings share source and
consumer. The `storageMediation` property is a JSON `StorageScan.Binding` version 1 containing
binding ID (`localName:rank`), source/target semantics and compiled conversion, including any pinned
rate. Existing rank, prerequisite role, readState and semanticRelations properties remain intact.
Neo4j's existing CREATE relationship path and the transaction's directed pseudograph preserve
parallel edges. Binding metadata and consumer publication share one transaction. Process causal
and descriptive edge conventions remain unchanged; read mediation does not invent causal links.

Detached binding observations preserve the requested observable without mutating the source.
In-process bindings follow source ID/URN/coverage through initialization and durable ID assignment;
transport snapshots contain the resolved identity, never the transient source reference.
Occurrence snapshots retain that observable and quote; restart resolves the durable source separately
and replans against its current descriptors. An ID-zero query used by a durable dependency resolves
its positive source ID before creating an edge. Standalone query/export/point reads create no graph
observation, activity, relationship or converted storage.

Every dependency execution records a JSON `StorageReads.Evidence` version 1 in its current Activity
metadata under a unique `im:storage-read:<UUID>` key. It includes binding identity and the complete
plan description: source revision, event/slice, source descriptors, operations, type/precision and
conversion reproducibility inputs. Later executions append new evidence; they do not rewrite old
activities. Rollback removes staged evidence. Temporal descriptions identify transaction-local
snapshots, while committed storage descriptions pin the selected source generation and shards.
`Session.description()` exposes provider evidence (null is permitted for older providers).

### Consumer entry points and harness

Contextualizers, temporal scalar dependencies, ID-zero quality queries, exports and indexed cell
text all use the same conversion compiler. HTTP exports may supply `storageObservable` with the
requested observable definition and `storageCurrencyRate` with a pinned-rate JSON value, in addition
to the existing temporal selection parameters. Export argument matching receives a detached view;
stream ownership and cleanup remain unchanged. `StorageScan.Point` carries requested semantic
snapshots and an optional quote for the text API.

`core.inspector.unitcheck(context, observation, unit, factor, offset)` compares bounded samples
against an independently supplied expected affine mapping and also checks partitioned/export/text
agreement. The staging storage testcase checks elevation meters-to-millimeters with factor 1000.
Full-stack execution remains the user-run acceptance gate before S5; see [TESTING.md](TESTING.md).


## Non-conformant spatial read mediation (S6)

`ACCEPT_LOSSY_MEDIATIONS` is a runtime Boolean setting, default **true**. Quality reuse in
`ResolutionCompiler` validates source/target spatial support before accepting an existing quality.
When false, mismatched extents fail with `ACCEPT_LOSSY_MEDIATIONS=false`, followed by the geometric
reason (for example, `cell resolution differs` or `requested coverage differs`). Partition/curve
changes that remain exactly conformant are still allowed. This gate does not change the separate
float-narrowing policy or migrate native storage.

`StorageReads` selects NEAREST for numeric/boolean data and MAJORITY for `type of` categories,
with MISSING_OUTSIDE for dependency, query, export and point/text reads. Explicit output partitions define target support independently of the input extent. Planning
first attempts S2 conformant mapping; on success it retains the exact description and reader.
Otherwise the runtime gate and requested sampling policy determine whether spatial mediation is
allowed. An explicit `Sampling.EXACT`, including `Request.nativeRead`, never opts into resampling.
All dependency plans validate before writable output acquisition. Cache keys include the gate;
opening a previously issued spatial plan also checks the current setting. Resolver-side runtime clients refresh their settings snapshot before this check. Already open sessions
retain their pinned contract. Failed requests do not change native values, histograms or descriptors.

### Sampling and coverage

| Policy | Types | Meaning |
|---|---|---|
| NEAREST (runtime default) | FLOAT, DOUBLE, INTEGER, LONG, BOOLEAN | Transform target cell centers; select containing half-open source cells, preserving exact native primitives. |
| INTERPOLATE | FLOAT, DOUBLE | Bilinear interpolation of source cell-center values; all positive-weight contributors must exist and be valid. No edge clamping or hole filling. |
| CONSERVATIVE | FLOAT, DOUBLE densities/intensive quantities | Overlap-area weighted mean normalized by the geometrically covered target area. |
| MAJORITY (categorical default) | KEYED | Same-CRS overlap-area majority, canonical-definition tie breaking, strict missing-contributor propagation. |
| CONSERVATIVE_TOTAL | FLOAT, DOUBLE cell totals | Sum source values times overlap area / source cell area. Full-domain coarsening/refinement conserves totals within floating-point tolerance. |

Conservative policies are explicit scientific choices; the runtime does not infer totals versus
densities from unit strings. For categorical information such as `type of`, the default policy is
**area-weighted majority** (`MAJORITY`), with the S7 dictionary contract below. Exact weighted ties
choose the lexicographically smallest canonical semantic definition. A missing positive-area
contributor makes the result missing; uncovered area is ignored when some coverage exists. Zero
overlap is missing. Category codes are never averaged or summed. Numeric interpolation and
conservative policies reject INTEGER/LONG/BOOLEAN rather than treating them as category dictionaries.

MISSING_OUTSIDE emits invalid values where a sample lacks source support. EXACT rejects target
footprints outside the source rectangle during metadata planning (1e-8 source-cell round-off
tolerance). It does not guarantee native validity. NaN contributors propagate missingness; neighbors
are not renormalized to fill missing source cells. Partial conservative densities normalize over
actual geometric overlap; partial totals include only overlap contributions. Zero overlap is missing.
Absent/corrupt files remain errors, never missing spatial values. Floating reads expose missingness
as NaN and text as `null`. Integer/long/boolean callers must check `isValid()` before `get()`/`peek()`:
invalid reads throw, preserving zero/false as ordinary values. Seek/nextLong can skip invalid cells.
Spatial scanners expose target metadata in `view()` and never claim a physical `shard()` identity.

### Geometry, precision and resources

Spatial mediation supports regular, axis-aligned rectangular **2D** grids. Source shards must cover
the native rectangle without gaps/overlaps. Explicit target partitions must cover one target
rectangle without gaps/overlaps, retaining identifiers and order. Derived partitions obey the
existing partition/size budgets and checked long curve codecs. The native scale layer currently
normalizes a single-cell observation to an irregular shape; such observations cannot enter this
regular-grid resampling path (an explicitly gridded one-cell target partition is supported).

| Geometry change | Support |
|---|---|
| Same-CRS resolution/offset changes, coarse/fine grids, subset, partial/no overlap | All spatial policies |
| EPSG:4326 to/from EPSG:3857 | Nearest and bilinear through strict GeoTools transforms, XY/longitude-first axes |
| Conservative EPSG:4326 overlap | Spherical area proportional to longitude width times difference of sine(latitude); the common radius cancels |
| Conservative matching projected CRS | Planar overlap; coordinate-plane conservation, not ellipsoidal ground-area accuracy |
| Rotated/sheared grids, masks, arbitrary reprojection, antimeridian wrapping, polar/singular domains | Explicitly unsupported |
| Changed non-spatial extents or temporal resampling | Explicitly unsupported; selected event slices can be spatially mediated |

Geographic spatial bounds must remain within [-180,180] longitude and strictly inside
+/-85.0511287798066 latitude. Web Mercator bounds must be strictly inside +/-20037508.342789244.
This restricted domain permits monotonic transformed-window pruning. Other matching CRSs use planar
coordinates. Ambiguous cells (width within 32 ULPs of bounds or counts beyond binary64's reliable
integer range) fail rather than guessing. Unknown spatial grid parameters fail during remapping; concrete cell counts and bounds take precedence over the `sgrid` resolution hint.

Version **4** descriptions record `SPATIAL_RESAMPLE`, optional `VALUE_CONVERSION`, then the primitive
operation. Source/target geometry encodings carry bounds, shapes and traversal; `Description.spatial()` and
`View.spatial()` retain effective source/target CRS identifiers even when shard encodings omit them.
Executor requests supply the output observation CRS for such partitions. Policy, source
revision, event and budgets are explicit fields. Version 4 defines the XY convention, strict
transform, half-open boundaries, spherical/planar metrics, tolerance and binary64 arithmetic above.
These versioned policies are included in fingerprints and execution evidence. Versions 1-3 retain
their previous fingerprints. Spatial arithmetic uses double accumulators even over FLOAT storage;
ordinary unit conversion follows sampling and final narrowing happens once. Conservative totals
permit multiplicative ordinary conversion. Affine total and bounded-range/resampling combinations
fail. Contextual precipitation depth-to-volume conversion and calendar metrics remain S5 work.

Plans retain partition metadata and a source spatial directory, never per-cell maps. Transformed
target windows prune source links. Each numeric cursor has one source-value window capped at
min(4096, Budget.blockValues), plus fixed primitive scratch coordinates. Local opening still
validates/leases the native shard group, including files outside the target window. Remote block
fetching and lazy shard acquisition remain S8 work. No converted buffers or HAS_DATA views persist.

`LocalTemporalWriteSet.read` applies the same policies to pinned PRIOR/CURRENT snapshots and closes
sessions on commit/rollback. Pre-earthquake elevation can be read over a changed event extent.
Individual event effects use `TemporalWriteSet.write` over the event geometry. Explicit writes
scatter to native cells by nearest cell center; cells outside the event remain unchanged. A
single-cell event grid is supported. Writable value conversion is rejected, and the same rectangular
grid, CRS and mediation-setting limits apply. Closing a view stages changes in the transaction;
commit publishes them atomically and rollback preserves historical data. Ordinary temporal output
contextualizers still require a transaction-owned write set.


## Keyed storage and categorical mediation (S7)

`Storage.KeyScanner<Concept>` now reads and writes concept values over four-byte signed integer
payloads. Code **0** is missing (`null`); positive codes are dictionary entries. Negative and unknown
codes are corruption errors, never missing values. An ordinary `IntScanner` cannot expose keyed
payloads as measurements. A key scanner exposes an immutable `DataKey` snapshot through `key()`;
`Storage.getKey()` also returns an immutable snapshot. Codes can be inspected through a dictionary's
`reverseLookup` and `lookup`, which keeps their semantic binding explicit. `DataKey.size()` and its
indexed label/concept lists include the reserved missing entry at index 0, so every exposed index
round-trips without an offset. The persisted entry catalog contains only codes 1..N.

For `type of X`, the reasoner resolves each previously unseen definition and checks that it is a
concrete, non-generic, non-bottom subclass of X. X itself is rejected. Only validated canonical
concepts are cached. Repeated insertion uses a hot identity cache and a canonical-definition map;
there is no per-value reasoning, remote call, or serialization. Invalid definitions are cached too.
The observation owns one synchronized allocator shared by its shards and concurrent producers.
Codes are append-only and cannot collide. Their numerical order has no rank meaning: this type-of
dictionary is explicitly unordered. Read cursors decode to cached canonical concept objects.

The first successful typed insertion (including missing) permanently commits the entire root context
to a `WorldviewCommitment`: worldview identity plus a sorted map of resolvable ontology URNs to
SHA-256 source hashes. The reasoner advertises this content snapshot in its capabilities; a local
revision counter alone is insufficient across restarts. The commitment is recorded on the root
Neo4j context, exposed in `DigitalTwin.Configuration`, and copied to `worldview.json` beside storage.
Reopening a committed context requires an available, consistent reasoner with exactly the same
commitment, even if no keyed quality has been requested yet. Same worldview ID with changed content
is rejected. New plans/scanners and previously unseen semantic values recheck the environment;
existing read sessions keep their pinned contract. A failed/rolled-back activity may leave the
context committed, but cannot publish unbound semantic payloads. Cross-worldview translation is
rejected; no label-based equivalence is inferred.

Each finalized shard references an immutable `key-<SHA256>.json` dictionary and a categorical
histogram containing that dictionary hash, positive-code counts, and a separate missing count.
The dictionary records its schema version, root commitment, type constraint, ordered code-to-entry
catalog, canonical semantic definitions, labels, and authority references where supplied by the
reasoner. It never stores Java concept objects or reasoner-local IDs. Snapshot files are flushed and
renamed before a shard can be published; existing dictionary versions are never overwritten with
changed meanings. Graph shard descriptors retain dictionary and histogram references. Restoration
checks hashes, compatible prefix assignments, root commitment, and payload code/count consistency.
Legacy keyed payloads without this evidence fail explicitly. Dictionary entries are reconstructed
without per-cell reasoning; concept resolution/validation is lazy and cached. Offline semantic reads
fail closed when the committed worldview cannot be verified.

Dictionary translations match canonical definitions within the same worldview/type, with bounded
translation-table caching by both dictionary fingerprints. A shared allocator avoids shard remapping
inside an observation. `Storage.getCategoryHistograms()` merges per-time shard counts through these
semantic translations and returns the dictionary with each summary. Numeric histograms never operate
on category codes. Validated immutable dictionary files are cached (at most 16 snapshots of up to
4096 entries); a fresh manager revalidates persisted bytes.

Scan description **version 5** includes the dictionary and worldview in execution evidence and
fingerprints, and supports native, conformant and spatial keyed views. Versions 1–4 retain their
previous fingerprints. Spatial majority uses the S6 planar/spherical overlap metrics and same-CRS
support matrix. Scratch is proportional to dictionary cardinality, not cell count, and is reused
between samples. Explicit nearest sampling is also available, including supported reprojection;
numeric interpolation and conservative sums/means reject keyed values. Temporal PRIOR/CURRENT reads,
commit and rollback use the same dictionaries and snapshots. Temporal localization preserves raw
shard geometry and inherited CRS while replacing only time.

Dictionary/root/payload publication follows the existing staged filesystem/graph protocol; it is
not a claim of atomic transactions across those systems. Orphan semantic snapshots are harmless and
remain part of the final recovery/hardening work. Contextualized units remain S5; the existing S6
geometry limits and final full-stack acceptance checks remain explicit.
