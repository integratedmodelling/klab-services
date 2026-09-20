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
| `KEYED` | `KeyScanner` backed by integer codes | 4 bytes | Classified/concept state (scanner not implemented) |
| `BOOLEAN` | `BooleanScanner` | 1 byte | Presence or verification state |

The generic `Scanner` deliberately has no boxed `get` or `add` operation. Typed scanners exist so
large contextualizations can execute without per-value allocation or boxing.

## Attributing the native strategy

`CompiledDataflow.harmonizeSharding()` attributes storage before the first contextualization of a
new quality:

1. Local, model, child, then runtime strategies are merged; later concrete fields win. Numeric
   primitive types may override one another; incompatible semantic types fail.
2. Runtime defaults derive type from semantics (numeric DOUBLE, categorization KEYED, verification
   BOOLEAN), curve from geometry, and split count from available processors. These concrete fields
   currently override earlier hints. Runtime sizes are neutral zeroes.
3. `USE_SHORT_FLOAT_REPRESENTATION=true` selects FLOAT instead of DOUBLE.
   `PARALLELIZE_OBSERVATIONS=false`, or nondistributed space, unconditionally forces one split and
   clears both size hints for newly attributed storage. This remains a supported test mode.
4. The result is recorded on observation contextualization data. Storage creation requires it.
   Existing positive-ID observations keep their recorded native strategy.

`CallDescriptors.shardingStrategy()` exposes Java requirements, but harmonization does not yet
consume them. Consumer preferences are not yet separated from native producer attribution.
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
Exporter registration records its curve but does not yet remap scanner arguments accordingly.

```java
@KlabFunction(name = "sample", description = "Sample a quality",
    type = Artifact.Type.NUMBER, split = 4,
    fillCurve = Data.FillCurve.D2_YX, minSizeForSplitting = 1024, maxSize = 65536)
```

The native splitter bypasses size hints for positive counts and uses them only to derive an
unspecified count. Planned reads additionally reject native shards exceeding a requested maximum.
End-to-end enforcement of Java consumer declarations remains stage 3. Dynamic `@Splits`,
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
Parsing/validation is implemented; consumer remapping and full runtime enforcement are staged in
[STORAGE_PLAN.md](STORAGE_PLAN.md).

### Fill-curve implementation boundary

`D1_LINEAR`, `D2_XY`, `D2_YX`, and `D2_XInvY` have tested mappings in supported dimensions.
For shape [X,Y], `D2_XY` varies Y fastest, `D2_YX` varies X fastest, and `D2_XInvY` reverses Y
within each X block. `D3_XYZ` is row-major; `D3_ZYX` currently aliases it. Hilbert mapping throws.
`FillCurve.map` accepts int and `Mapper.offset` narrows to int: these are not proven large-index
mediation APIs. Planned reads reject UNSPECIFIED, D3_ZYX, and Hilbert curves. Traversal remapping
is not implemented even where standalone curve mapping works.

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

During a quality contextualization, `AbstractExecutor` opens native output scanners and matching-strategy
read-only scanners for quality dependencies. It constructs one task per output shard and binds
component method parameters by the declared input/output name. Parameters may request:

- the `Observation` itself;
- the scanner's `Shard` view;
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
are not the strict planned-session contract below. The executor opens output scanners before
planning all dependencies, so a failed input binding may already have reset output histograms.
It matches scanner lists by index/count, not by proven geometric alignment.

`ArgumentMatcher` obtains native scanners and calls `ScannerAdapters.mergeScanners()`, whose only
working case is a singleton. It does not apply exporter curve metadata and does not force
initialization scanners readonly. `KlabServiceController.exportAsset()` delegates through
`BaseService.exportAsset()` and language invocation into this route. Detached ID-zero queries
copy source metadata but do not yet compile scanner/unit mediation; storage is indexed by source
observation identity, so zero must not become a shared storage key. These consumer routes are
stage-3 integration work, including individual values returned as text.

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
Optional geometry and semantic definitions must match the native source in S1. Empty consumer
partitions select native partitions; explicit partitions must match ordered source geometry and
size and have unique identities. Positive maximum size is enforced against every source shard.
Sources must be finalized initialization shards or restored/committed data. Unsupported layouts,
semantics, coverage/sampling policies, writes, keyed types, and curves fail before buffer access.

### Ownership and portable descriptions

Plans belong to their issuing storage instance. Version-1 `Description` records contain immutable
source descriptors, event, semantic definitions, layouts, partitions, precision, budgets and
operation metadata. JSON round trips preserve structural equality; inconsistent descriptions and
unknown versions are rejected. A description is not an executable plan or permission to open data.

`fingerprint()` is SHA-256 over length-prefixed UTF-8 fields in record/list order. An opaque source
revision token incorporates local storage identity and write generation, so changing initialization
values changes the fingerprint. This is not a durable content hash or a cross-provider cache key.
Temporal source URNs/timestamps identify concrete committed shards. Description persistence in
graph edges is future work; executable cursors, converters and buffers are never serialized.

Opening rechecks generation, semantics and source descriptors. Stale plans fail and require
explicit replanning. An open session leases its source, blocking initialization writes (including
previously obtained writers), writer resets and storage closure. Concurrent sessions have
independent task-local cursors. Close/cancel is idempotent and invalidates scanners, closes reader
handles and releases the lease. Storage owns mapped buffers. Partial opening failure closes acquired
handles and newly restored buffers before releasing the lease. Always use try-with-resources.

### Consumer view, cursor and precision

`shard()` identifies physical native storage; `view()` describes consumer partition, type, semantics,
slice, curve and sources. Views are never persisted as `HAS_DATA` shards. View histograms are
explicitly unavailable; native histograms are not converted-view statistics.

`position()` is the next offset, equal to `size()` at exhaustion. `get()` and `nextLong()` each
consume one position; `peek()`, `isValid()` and `location()` do not advance. Value/location access
after exhaustion throws `NoSuchElementException`; access after close throws `IllegalStateException`.
Writes fail without advancing. Location contains partition, curve, slice and long offset; coordinate
decoding and cell metrics remain future work. Legacy cursor behavior is unchanged.

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
Their binding is S3 work, ordinary semantic conversion S4, and contextual conversion S5. Text
formatting belongs after location selection, validity and primitive mediation at the API boundary.
S1 neither adds a text-value endpoint nor claims that existing exporters honor requested views.

## Unsupported or incomplete operations

### Value mediation baseline

Unit conversion is destination-receiver: `meters.convert(2, millimeters)` returns 0.002.
`UnitService.convert(value, first, second)` likewise uses destination-first order despite its
historical from/to parameter names. Ordinary multiplicative and affine Celsius/Kelvin conversions
work; they are not wired into scanners. Compatibility, algebra, contextualization, and ordinary
locator conversion remain unfinished. `AbstractMediator` can execute supplied dimension-factor
operations, but no complete compiler supplies the required per-cell operations/locators.
`UnitImpl.aggregatedDimensions` is metadata, not evidence of working contextual conversion.

`ShapeImpl.getStandardizedArea()` computes a metered area, not square degrees. Per-cell geographic
area accuracy and calendar duration policies remain unvalidated for scanner conversion.
`NumericRangeImpl` supports bounded conversion but needs compatibility/locator hardening.
Currency conversion/compatibility and the rate provider are stubs. KEYED has a declared width and
interface but no native scanner or durable worldview-bound dictionary.

### Remaining boundaries

The following remain explicit implementation boundaries:

- geometry-aware scanner split/merge and fill-curve remapping when the requested sharding strategy
  differs from native storage;
- unit mediation in scanner decorators;
- temporary scanners from `StorageManager.getTemporaryScanner(...)`;
- complete generalized indexing for moving dimensions other than time;
- a durable, mergeable key descriptor for `KEYED` storage;
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
