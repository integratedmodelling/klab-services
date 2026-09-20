# Storage mediation development plan

Status: stages 0–4 implemented; stages 5–8 await first-phase full-harness acceptance. Baseline inspected on
2026-09-20. Sections 8–12 record completion traces, test evidence, and remaining boundaries.

[STORAGE.md](STORAGE.md) remains the authoritative description of committed architecture,
public APIs, implementation boundaries, and supported behavior. This file records development
stages, proposed contracts, unresolved decisions, and acceptance criteria. Names introduced below
are design placeholders, not new APIs. At each completed stage, update STORAGE.md and API Javadocs
with only the behavior actually delivered; retain this file as the execution record.

## 1. Outcomes and constraints

Allow contextualizers, exporters, and detached query observations to request a view of existing
data with their own partitioning, traversal, geometry, and compatible value semantics. Keep the
producer's geometry, native strategy, original Observable unit, and persisted values authoritative.
Mediation must not create a second stored observation merely to present a different unit.

Deliver exact conformant mediation first. Follow with lazy value conversion, contextual unit
conversion, and explicit spatial resampling. Complete concept-keyed storage and its durable
semantic identity. Preserve a provider boundary suitable for future Spark, Cassandra/Acceleo,
and other remote storage or computation implementations without requiring those deployments now.

Core invariants for every stage:

- A request never silently changes a producer's storage strategy, unit, dictionary, or history.
- Dependency and export views are read-only. Keep native output writes and existing primitive
  write-through adapters; defer mediated writes until ownership and inverse semantics are proven.
- Validate the complete request before opening writable output scanners or resetting histograms.
  Failure must identify the input binding, source, request, and unsupported transformation.
- A scan pins a committed source revision/time slice. Missing data files or descriptors are errors;
  missing spatial coverage is a separate, explicitly requested result policy.
- Scanners and cursors are task-local. Immutable plans can be shared; mutable cursors cannot.
- Primitive numeric paths avoid boxing and allocation per value. Memory is bounded by partition
  metadata and configured tile/block caches, not the full source or destination value count.
- General spatial remapping, dictionary translation, and semantic conversions never silently
  inherit permissive defaults from numeric casts.

## 2. Implementation baseline and audit targets

The following repository evidence determines the sequence. Paths are relative to the repository;
class names can be located in the indicated module.

| Area | Current implementation and consequence |
|---|---|
| Public access | `klab.core.api`: `Storage.scan(event, strategy, scannerClass, readOnly)` lacks an explicit consumer geometry, requested Observable, and mediation policy. `Scanner.shard()` identifies a physical shard; a mediated view may span several. |
| Local storage | `klab.core.services`: `StorageImpl.scan()` supports equal strategies and primitive adapters; `remapScanners()` throws. The `UNSPECIFIED` curve branch can replace the native strategy and needs an attribution/migration audit. |
| Cursors | `StorageImpl.BaseScanner.nextLong()` advances and returns an index; typed `get()` advances and `peek()` does not. A wrapper must not advance twice when obtaining location and value. |
| Runtime binding | `klab.services.runtime`: `CompiledDataflow.harmonizeSharding()` negotiates native strategies; `AbstractExecutor` requests dependency scans with the output strategy and pairs them by list position/count. Count equality alone does not establish spatial alignment. |
| Existing adapters | `ScannerAdapters` provides float/double mediation. Reuse its primitive and mutability discipline without assuming its delegated physical-shard metadata is sufficient for geometry or unit views. |
| Unit machinery | `ValueMediator`, `Unit`, and `UnitImpl` expose contextual conversion; `AbstractMediator` contains locator-driven length/area/volume/time operations. However, `UnitServiceImpl.contextualize`, locator conversion, compatibility, and unit algebra are incomplete. Stage 0 verified destination-receiver conversion and exposed misleading service argument names; see section 8. |
| Other values | Audit `NumericRangeImpl` for actual conversion behavior. `CurrencyImpl` and `CurrencyServiceImpl` do not supply a complete conversion service. Do not treat declared interfaces as working integrations. |
| Keyed values | `Storage.KeyScanner` and `DataKey` already exist, and `KEYED` reserves integer storage. `StorageImpl.getNativeScanner()` explicitly rejects keyed access until a persistent key and dedicated scanner exist. |
| Provenance | `CompiledDataflow` writes `AFFECTS` properties including binding order, roles, read state, and semantic relations, and distinguishes detached query views. Mediation metadata must extend these semantics without replacing them or adding false causal edges. |
| Time/history | `TemporalWriteSet`, `LocalTemporalWriteSet`, and `TemporalScalarExecution` exist. `AbstractExecutor` currently rejects non-initialization quality execution without a transactional temporal output protocol. Mediation must not bypass that restriction. |

Read [KNOWLEDGE_GRAPH.md](KNOWLEDGE_GRAPH.md), [PERSISTENT_TWINS.md](PERSISTENT_TWINS.md), and
[DISTRIBUTED_TWINS.md](DISTRIBUTED_TWINS.md) alongside STORAGE.md when changing persistence,
restart, and provider boundaries. Audit actual code where these documents and newer temporal
paths differ; record any reconciled contract in STORAGE.md with the implementation change.

## 3. Proposed separation of responsibilities

### 3.1 Request, plan, and execution

Introduce an additive request/plan path, preserving the existing scan signature as an exact-native
compatibility entry point. A proposed `ScanRequest` describes:

- Source identity and revision selection, event/time slice, and requested coverage/geometry.
- Consumer partition descriptors, fill curve, size constraints, and requested scanner type.
  Permit an explicit partition layout, not just a suggested split count, so every input follows
  exactly the same cells as the output task even when native split heuristics differ.
- Source and target Observable/value semantics, including unit/range/currency and intensive or
  extensive dimensions. Obtain source semantics from committed storage, not caller assertions.
- Geometry, resampling, coverage, no-data, numeric precision, and dictionary compatibility policies.
- Access mode and budgets for mapping metadata, source blocks, and prefetch.

Compile an immutable `ScanPlan` before execution. It classifies the request as native, exact
conformant, contextual conversion, or non-conformant; resolves capabilities and source versions;
validates metadata; and describes consumer partitions and the ordered transformation pipeline.
Opening a plan creates independently closeable scan sessions/cursors and bounded source readers.
Do not eagerly load all source buffers to discover whether the request is valid. Corruption or
remote failure during reading still fails the execution and prevents successful output publication.

Separate consumer metadata from physical metadata. Prefer an additive scan-view descriptor with
consumer geometry, curve, value type, unit, dictionary, partition identity, and source references.
At the stage-1 API gate, choose how legacy `shard()` callers bind to it: a documented read-only
logical shard facade or a new accessor plus binding migration. Do not falsely label a merged view
with one physical shard's URN, histogram, or native type. Never persist a logical shard descriptor
as `HAS_DATA`; finalization continues to receive native output ownership.

Define `size`, `hasNext`, `nextLong`, `get`, and `peek` precisely for a consumer-local cursor. A
location accessor must not advance it. Preserve current iteration conventions for existing callers,
specify exhaustion behavior, and test mixed cursor/value use. A locator must identify the cell and
interval used by a conversion, not just an offset within whichever source shard happens to be read.

### 3.2 Exact indexing and provider access

For conformant layouts, use this mapping:

`consumer partition offset -> consumer grid coordinate -> source grid coordinate -> source shard + local offset`

Require equivalent coverage, grid alignment, resolution, dimension meaning, and cell support;
matching cell counts or bounding boxes is insufficient. Distinguish coordinate representation
differences that are proven exact from transformations requiring resampling. Use checked `long`
indices and inverse fill-curve mappings. Reject unsupported curves rather than reinterpret bytes.

A sequential source scanner cannot implement an arbitrary traversal permutation efficiently.
Introduce an internal typed indexed/block reader with immutable revision pinning. Keep native
buffers private. The local implementation reads primitive offsets; other providers can implement
tile fetch, batched gathers, or server-side execution. Plan contiguous spans and bounded tiles;
never open a remote request for every cell or materialize a full converted buffer by default.

### 3.3 Semantic pipeline

The planner chooses an explicit order: source read/validity, necessary source-side semantic
normalization, spatial sampling or conservative aggregation, target-side contextual conversion,
and final primitive representation. Exact remapping has one source contributor per target cell.
There is no universal rule that all unit conversions belong after resampling: extensive totals
and densities require different placement of area/duration factors. Nonlinear range conversion
also cannot be freely moved across averaging. Record the ordered operations in the plan.

Keep missingness separate from numeric value so integer, boolean, and keyed states can represent
uncovered cells without treating zero/false/a category as missing. Decide the additive validity API
in stage 1 and implement it before non-conformant scans. Preserve existing native representations
and numeric no-data conventions through documented adapters.

## 4. Delivery stages

Stages below are implementation-sized milestones, each divisible into the listed changes. No
milestone is complete until its acceptance tests and authoritative documentation updates land.

| Stage | Depends on | Deliverable/status |
|---|---|---|
| 0 | None | Baseline audit and regression fixtures — completed; see section 8 |
| 1 | 0 | Request, plan, consumer metadata, provider contracts — completed; trace and verification in section 9 |
| 2 | 1 | Exact split/merge and traversal mediation — completed; see section 10 |
| 3 | 2 | Contextualizer, export, individual-value/text API, query integration — completed; see section 11 |
| 4 | 1, 3 | Lazy unit/range/currency conversion and edge persistence — planned |
| 5 | 4 | Per-cell area/duration and extension/intension mediation — planned |
| 6 | 2, 3, 5 | Non-conformant spatial mediation — planned |
| 7 | 1; 3 for binding | Durable keyed scanners and worldview commitment — planned |
| 8 | 3–7 | Recovery, performance, provider conformance, release — planned |

Stage 7 can be developed after stage 1 independently of numeric mediation. Its dictionary and
validity contracts must be ready before enabling categorical spatial mediation in stage 6.

### Stage 0 — Verify the baseline and establish reference results

1. Trace producers, dependency bindings, detached `Observation.QUERY_ID` requests (`id == 0`),
   exporters, revision reads, and temporal writes end to end. Inventory all scan callers and
   existing fill-curve implementations; identify which declared curves have correct inverses.
2. Characterize current primitive adapters and cursor semantics. Audit native curve attribution,
   geometry serialization, partial actuator coverage, semantic equality, and no-data behavior.
3. Audit the unit service with asymmetric conversions in both directions, affine temperature
   conversion, contextual units, and reparsing after serialization. Check where semantic
   extension/intension and aggregated dimensions are actually represented.
4. Create small analytic reference fixtures: irregular split boundaries over a numbered raster;
   differently located geographic cells; partial-overlap elevation/event grids; precipitation
   depths and cell volumes; integer concept codes with a reproducible dictionary.

Acceptance: a support matrix lists working behavior, stubs, and explicit rejections; fixtures
reproduce existing behavior and expose conversion direction or context defects without relabeling
them as supported features. Record actual test results and follow-up decisions in section 7.

### Stage 1 — Commit the minimum additive contracts

1. Add request validation and immutable planning, retaining the native fast path and compatibility
   overload. Resolve the view-versus-physical-shard, cursor/location, validity, and close/cancel
   contracts before introducing wrappers that depend on them.
2. Introduce the internal indexed/block source reader and a local implementation. Pin source
   revisions and expose provider capabilities without leaking ojAlgo types or local paths.
3. Define a serializable, versioned mediation description distinct from compiled converters,
   executable lambdas, cursor state, and caches. Specify equality/fingerprints and validation.
4. Define precision policies: existing float/double adapters remain available; integer narrowing,
   rounding, overflow, and non-integral conversion need explicit rules. Boolean/keyed values
   cannot pass through arithmetic casts. Define native versus view histogram availability.

Acceptance: identity requests preserve native behavior; invalid requests have no write side
effects; legacy binding compiles; session closure releases acquired resources even after partial
open failure; independently opened cursors do not interfere. Public contracts are documented in
STORAGE.md only when implemented.

### Stage 2 — Conformant partition and fill-curve mediation

1. Implement conformance checks and coordinate/index codecs using existing geometry/curve code.
   First deliver split and merge with unchanged traversal, then supported traversal permutations.
2. Build a source shard spatial/index directory from persisted geometry and explicit partition
   metadata. Map across shard boundaries without assuming equal shard sizes or equal indices.
3. Optimize identity, contiguous spans, regular-grid arithmetic, and bounded tile gathers.
   Use primitive kernels, long offsets and reusable scratch arrays, with no per-cell boxed values,
   locator objects or full-dataset index maps in the default path. Cache
   immutable plans by source revision/geometry, target layout, curve, and relevant policies.
4. Support read-only views for every implemented primitive type. Keep concept values gated until
   stage 7. Reject nonconformance with a specific reason until stage 6 supports its policy.

Acceptance: one-to-many, many-to-one, and many-to-many scans produce the reference cell sequence
with no duplicates or omissions; cover uneven partitions, singleton/empty valid requests,
different min/max split sizes, reversed axes, supported 2D/3D curves, and checked large indices.
Index-only tests can exercise counts beyond `int` without allocating huge buffers. Rejected
geometry requests leave native values, descriptors, and histograms unchanged.

### Stage 3 — Bind all consumers through the same planning path

1. Refactor `harmonizeSharding()` so producer attribution remains stable while compatible consumer
   preferences become per-binding requests. Preserve genuinely incompatible semantic/type errors.
2. In `AbstractExecutor`, plan every input against the output task's explicit partition geometry
   and traversal before opening writable outputs. Check partition identity, size, and location
   alignment, not only scanner-list length. Preserve the concrete planning error as the cause.
3. Bind each parameter's requested type independently from the output's native type. Cover
   generic scanners, typed scanners, observation parameters, and shard/view parameters.
4. Route exporters, individual-value API access (including text responses), and detached query observations through this path. Resolve source storage by
   durable source identity, never by query ID zero. Keep query requests and views ephemeral;
   two simultaneous queries requesting different units/layouts must not overwrite each other.
   Select individual cells and temporal revisions through the same planning path, applying validity
   and mediation before formatting. Preserve exact longs and distinguish missing from zero/false;
   define locale-independent text. Use bounded indexed point access, not full scans/materialization.
   Streaming exporters must retain sessions until stream close/cancellation, not merely until the
   reflected exporter method returns.
5. Integrate read planning with temporal revision selection and the existing write-set protocol.
   Do not enable general temporal output solely because inputs can now be mediated. Release
   sessions on normal completion, cancellation, reflection failure, and export interruption.

Acceptance: a contextualizer with several differently partitioned dependencies receives aligned
inputs for each output task; an exporter requests one traversal across many native shards; a
query uses no extra persisted storage/observation. Text values must agree with the same cell in
an adapted export and dependency scan (units, validity, revision and precision). Test initialization and supported temporal
paths, failure before output reset, and cancellation/resource release.

### Stage 4 — Lazy value mediation and dependency provenance

1. Complete and verify ordinary unit compatibility and conversion direction in `UnitServiceImpl`.
   Compile reusable primitive conversion kernels once per plan. Cover multiplicative and affine
   conversions; preserve no-data before invoking any converter. Perform final narrowing once.
2. Implement lazy numeric decorators with repeatable `peek()` and correct cursor advancement.
   Audit and complete range conversion: source/target endpoints, open bounds, degenerate ranges,
   monotonicity, and out-of-range policy. Do not clip or normalize silently.
3. Define currency service requirements and a deterministic rate-provider interface. A currency
   plan must pin currencies, valuation date/time, rate source, rate/version, and rounding policy;
   distinguish exchange conversion from inflation/base-year adjustment. Test with fixed rates.
   Live-provider selection is a separate deployment decision; unavailable rates fail explicitly.
4. Persist durable dependency mediation on the source-input-to-consumer `AFFECTS` link. Extend
   graph serialization, transaction writes, reads, restart reconstruction, and schema validation.
   Preserve existing role/read-state/rank properties. Identify bindings explicitly: the same
   source may feed multiple parameters of one consumer in different units, so a single mutable
   conversion property for the source/target pair is insufficient. Choose separate binding edges
   where supported or a versioned binding-keyed payload at the schema gate.
5. Store source and requested semantic definitions, ordered operations/policy, schema version,
   binding identity, and reproducibility inputs such as the currency snapshot. Record source
   revision and event-specific execution evidence through existing provenance/revision structures;
   do not overwrite an old run's conversion evidence when the dependency runs again.
6. Keep source Observable and native bytes in their original unit. Query ID-zero mediation uses
   the same in-memory description but creates no graph asset. When a detached binding supplies a
   durable consumer, resolve its true stored source and persist that binding's description there;
   never make zero an edge endpoint. Respect existing process/descriptive edge distinctions.

Acceptance: asymmetric unit round trips and affine conversions match reference values; range and
currency edge cases fail or convert according to policy. Two bindings to the same source keep
different conversions across restart. A query has no graph effects. Rollback cannot publish a
conversion description without its committed consumer, and native payloads remain unchanged.

### Stage 5 — Contextual units and extension/intension

1. Complete the contextualization path using `AbstractMediator` as a starting point, not as proof
   of finished support. Compile multiply/divide operations for explicitly identified extensive
   dimensions; verify standard area/length/volume/duration units and reject ambiguous semantics.
2. Supply cell-specific source and target locators through the scan plan. Resolve whether a factor
   belongs to source support, target support, or an overlap contribution. Do not use shard area
   when cell area is required or infer precipitation semantics from the string `mm` alone.
3. For precipitation depth, verify `volume_m3 = depth_mm * 0.001 * area_m2` and its inverse. Test
   independently varying area, depth, and missingness, including non-native traversal.
4. Obtain physical area from the spatial scale/CRS implementation. Cache a constant only where
   valid, or bounded per-row/per-tile metrics for geographic grids. EPSG:4326 cell area requires
   position and geodetic interpretation; square degrees are not square meters. Validate axis
   order, clipping, and the selected earth/area model.
5. Resolve calendar duration from actual interval boundaries and calendar/time-zone semantics.
   Support unequal months and leap years; a month/year label cannot become one fixed duration.
   Require time-zone context where local calendar boundaries matter. Keep calendar-unit
   conversion distinct from full temporal resampling, which remains deferred.
6. Cache compiled semantic operations independently from location-dependent metric tiles. Cache
   keys include geometry, CRS/metric policy, interval/calendar, source/target semantics, and version.

Acceptance: equal angular cells at different latitudes yield appropriate different volumes;
February and March, leap-year intervals, and applicable local-time transitions use their actual
durations. Interleaved/concurrent scans cannot reuse the wrong context. Missing/zero/invalid
required extents fail explicitly, with no fallback to a context-wide average.

### Stage 6 — Non-conformant spatial mediation

1. Implement spatial plan classification: same-CRS grid offset/resolution changes first, then
   supported CRS reprojection. Use affine transforms for grid/world coordinates where appropriate;
   CRS transformations may be nonlinear and need the existing spatial transformation facilities.
   Record CRS identities, axis order, transforms, tolerance, and numerical precision policy.
2. Define target-driven sampling and overlap footprints. Distinguish uncovered target cells,
   masked source cells, and absent/corrupt persisted storage. Require an explicit coverage policy
   (for example, missing output outside overlap or rejection of incomplete coverage), with valid
   coverage fraction available when useful. Never extrapolate silently.
3. Deliver nearest-neighbor sampling, then explicit interpolation for suitable intensive numeric
   states, then conservative overlap-weighted aggregation for extensive quantities/densities.
   Specify source/target cell support, boundary conventions, weight normalization with missing
   data, minimum coverage thresholds, and the treatment of partial cells.
4. Keep categorical/boolean policies separate: nearest or explicitly defined category aggregation;
   no interpolation of integer concept codes. Gate dictionary resolution on stage 7.
5. Implement a bounded tile/window cache with source partition pruning. Handle transformed edges,
   wraparound/antimeridian cases, and singularities explicitly; publish a tested support matrix
   and reject unsupported domains rather than extrapolate a grid transform.
6. Exercise event-relative properties: read pre-event elevation over an earthquake's target extent
   and grid, with partial overlap. Return a mediated input view; any resulting elevation update
   must use the existing transactional output protocol and an explicit partial-update policy.
   Completing partial-quality writes is a separate gate, not an implicit side effect of reads.

Acceptance: analytic ramps verify sampling and interpolation; constant fields remain constant
under supported intensive policies; extensive totals are conserved over matched covered domains
within declared tolerance. Test shifted/rotated grids, coarse/fine grids, partial/no overlap,
CRS changes, no-data boundaries, and combined precipitation-unit/resampling order. Unsupported
temporal remapping remains explicit while spatial mediation works with selected time slices.

### Stage 7 — Concept keys, dictionaries, and worldview persistence

1. Implement `KeyScanner` using integer-backed native buffers and the existing `DataKey` contract.
   Decode to cached canonical concept objects on typed reads; encode only recognized compatible
   concepts on writes. Permit a primitive code view only when its dictionary is explicitly bound;
   do not expose keyed storage as an ordinary numeric measurement.
2. Define a versioned durable dictionary: stable code-to-semantic-definition mapping, labels,
   authority references where applicable, ordering/rank semantics, no-data representation,
   dictionary identity/hash, and worldview commitment. Never persist only runtime reasoner IDs,
   Java object serialization, or labels as semantic identity. Keep unknown codes distinct from
   no-data and detect overflow/corruption.
3. Select concurrency policy before enabling parallel writes: prefer a declared immutable
   dictionary where categories are known; otherwise allocate codes through a coordinated writer
   or deterministically merge task dictionaries and remap codes before publication. Never mutate
   a committed dictionary in place or let two shards assign different meanings to one code.
4. Store the worldview commitment at the root context, including a stable identity and resolvable
   version/content fingerprint and necessary ontology dependencies. Each dictionary references
   that commitment. Validate it on reopen and import; missing or incompatible semantics must
   fail clearly rather than silently rebind against the currently installed worldview.
5. Commit dictionary version/references with shard descriptors and the appropriate root commitment.
   Ensure restoration validates the complete combination. Define lazy concept resolution and
   offline behavior explicitly. For legacy keyed payloads without a dictionary/commitment, require
   supplied migration evidence or reject semantic reads; do not guess meaning from integer codes.
6. Translate compatible dictionaries by canonical semantic identity, preserving ordering where
   defined. Cache code translation tables by dictionary versions. Cross-worldview equivalence
   requires an explicit validated mapping, never label matching. Category histograms must carry
   dictionary identity and merge semantically, without arithmetic on category codes.

Acceptance: concept write/read/restart preserves identity and ordering; parallel producers cannot
collide; dictionary merge/remap is stable; category histograms survive restoration; incompatible
worldviews, unknown codes, missing dictionaries, and mismatched descriptor hashes fail explicitly.
Test key remapping combined with conformant traversal and supported categorical spatial policies.

### Stage 8 — Hardening and provider portability

1. Run the full consumer matrix against local storage and a fake block/remote provider that returns
   delayed, reordered, missing, and failed blocks. Prove algorithms do not depend on local paths,
   `BufferArray`, shared memory, or one request per cell. Cap concurrent reads and retries; expose
   cancellation and prevent mixing source revisions during a retry.
2. Test graph/filesystem recovery: descriptor, dictionary, commitment, and mediation schema
   versions; interrupted publication; rollback; missing source revisions; and orphan cleanup.
   Use staged/versioned publication rather than claiming the filesystem and graph are atomic.
   Adopt existing temporal publication facilities where suitable; record remaining recovery limits.
3. Benchmark native, exact-remap, lazy conversion, contextual metrics, and resampling paths.
   Track throughput, allocations/value, peak memory, source blocks/bytes, cache behavior, and
   planning overhead separately. Establish agreed regression budgets from the stage-0 baseline;
   require bounded memory and no per-cell network access before acceptance.
4. Version request/description schemas and provider capabilities. Permit optimized server-side
   execution only when it preserves pinned inputs, precision/no-data policy, dictionaries, and
   reproducible semantics. Keep the local reader as the reference implementation. Real Spark or
   Cassandra/Acceleo adapters are subsequent projects with the same conformance suite.
5. Release incrementally by capability. Unsupported combinations retain actionable errors; no
   fallback to native ordering or unmediated values. Update STORAGE.md, component/exporter author
   guidance, migration instructions, and the stage record with the tested support matrix.

Acceptance: end-to-end restart and failure tests pass, provider behavior agrees with the local
reference within declared numerical tolerances, and recorded performance/resource budgets hold.

## 5. Validation strategy

Extend existing focused suites rather than rely solely on new isolated wrapper tests:

- `ScannerAdaptersTest`: primitive compatibility, cursor semantics, read-only behavior, composition.
- `StorageManagerImplTest` and `StorageReconstructionTest`: native widths, persisted descriptors,
  restoration, dictionary references, missing data, and immutable source bytes after view reads.
- `FillCurveTest` and `GeometryAndCurvesTest`: bijections and geometry/index reference results.
- `AbstractExecutorScannerBindingTest`: per-binding plans, aligned input/output tasks, parameter
  types, detached queries, concrete errors, and failure before output mutation.
- `TemporalStorageTest` plus supported runtime temporal tests: pinned revisions, prior/current
  read-state semantics, transactional output boundaries, and cancellation/failure recovery.

Add focused plan, contextual-unit, spatial-policy, keyed-dictionary, and graph round-trip tests
where those behaviors have no existing home. Use deterministic analytic fixtures and randomized
small-grid partitions/permutations against a simple independent reference. Validate conservation
and semantic identity, not merely agreement between two implementations of the same formula.

Existing focused baseline command (PowerShell):

```powershell
.\mvnw.cmd -pl klab.core.services -am "-Dtest=ScannerAdaptersTest,StorageManagerImplTest,StorageReconstructionTest,TemporalStorageTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Run API geometry tests for indexing changes and compile/test `klab.services.runtime` for every
attribution, binding, graph, or temporal integration change. Add each new suite to the appropriate
module checks; record environment-dependent integration prerequisites and unexecuted tests.
This planning change itself does not claim that implementation tests have run or passed.

## 6. Decisions required at implementation gates

| Gate | Decision and proposed starting point |
|---|---|
| Stage 1 | Additive consumer-view metadata versus logical shard facade; preserve native finalization identity either way. |
| Stage 1 | Missingness and locator API for all types; do not reserve an ordinary boolean or category value as missing. |
| Stage 2 | Supported invertible curves and exact conformance tolerances; expand only with reference tests. |
| Stage 4 | Graph representation for multiple mediated bindings and immutable run evidence; preserve causal edge roles. |
| Stage 4 | Range endpoint policy, rounding, and rate-provider provenance; keep deployment-specific live currency sourcing optional. |
| Stage 5 | Authoritative intensive/extensive semantic metadata and physical metric/calendar providers; reject ambiguous requests. |
| Stage 6 | Resampling and partial-coverage defaults by semantic class; require explicit policy for lossy operations. |
| Stage 7 | Dictionary code allocation, worldview fingerprint format, and legacy migration policy; do not invent old meanings. |
| Stage 8 | Performance budgets, schema compatibility window, and provider conformance requirements based on measurements. |

Deferred scope: arbitrary mediated writes, general temporal resampling, generalized moving
dimensions, automatic cross-worldview reconciliation, concrete distributed-engine deployments,
and implicit partial-quality output merging. These are explicit follow-on decisions, not reasons
to delay conformant read mediation, calendar-aware units, or durable keyed storage.

## 7. Development record

For each implementation increment, append: stage/substep, status, commit/PR, API/schema decisions,
tests and measurements, known limitations, and the exact STORAGE.md sections updated. Link
follow-on work for unmet acceptance criteria; do not mark a stage complete on API scaffolding alone.

| Date | Stage | Record |
|---|---|---|
| 2026-09-20 | Planning | Inspected storage, scanner binding, unit-service scaffolding, keyed contracts, dependency edge writes, and temporal execution restrictions. Created this staged plan. No implementation behavior changed. |
| 2026-09-20 | 0 | Added baseline characterization suites and reusable analytic fixtures; traced Java function, resource-adapter, exporter, and k.IM declarations; corrected STORAGE.md and annotation/unit Javadocs to describe current behavior. No mediation or precedence behavior changed. Detailed evidence follows. |

## 8. Stage-0 audit and completion trace

Baseline commit: `d1527830d`; stage-0 changes are working-tree changes on that baseline (no commit
or PR created by this task). Completed means the audit and fixtures are delivered, not that the
defects discovered by the audit have been repaired. Tests named `BaselineTest` deliberately expose
current gaps; when the owning stage fixes a gap, replace its characterization with the intended
success/error-contract regression. Do not preserve a defect merely to keep a baseline test green.

### 8.1 Needs and completion evidence

| Stage-0 need | Completed work | Evidence and remaining boundary |
|---|---|---|
| Trace all producers and consumers | Traced resolver attribution, Java registration, runtime harmonization/binding, argument matcher export path, detached queries, and temporal reads/writes | Section 8.2; gaps S0-01 through S0-12 below. No new consumer API introduced. |
| Establish Java annotation contract, including adapters | Recorded names, defaults, type derivation, class/method behavior, descriptor construction, and missing enforcement | STORAGE.md Java table; `ComponentRegistryShardingTest` covers function registration, exporter registration, adapter defaults/descriptor conversion. Adapter registry constructor mapping additionally inspected in source. |
| Establish k.IM annotation contract | Read shared/Xtext grammar, parser syntax beans, service adaptation, observation annotation precedence, and resolver switch | `ShardingAnnotationSyntaxTest` uses the real k.IM parser and LanguageAdapter; `ShardingAnnotationsBaselineTest` compiles effective beans; existing `DataflowAnnotationsTest` checks main-output inheritance. No language repository edits. |
| Characterize native strategy preservation | Tested later-concrete merge semantics, unchanged input bean, incompatible semantic types, and actual runtime harmonizer | `ShardingStrategyBaselineTest`, `ShardingAttributionBaselineTest`; runtime settings and scalar size clearing documented. |
| Characterize scanner semantics and rejections | Used a real local double scanner with float wrapper; verified peek/get/index behavior, readonly rejection, no buffer mutation, and rejection before manager access | `StorageMediationBaselineTest`, existing `ScannerAdaptersTest`, `AbstractExecutorScannerBindingTest`. No promise of post-exhaustion safety or non-advancing locator. |
| Inventory usable curves and indexing risks | Exhausted small rectangular 2D grids across supported curve pairs, tested 3D fallback, inspected integer narrowing and Hilbert rejection | `ShardingStrategyBaselineTest`, `FillCurveTest`, `GeometryAndCurvesTest`; arbitrary long-index mappings still need stage 2. |
| Audit unit service and recontextualization | Tested asymmetric metric conversions, affine Celsius/Kelvin conversion, reparsing definitions, stub responses, and existing per-locator operation engine | `UnitMediationBaselineTest`; conversion direction is clarified rather than changed. Reparse is not a persisted graph round trip. |
| Establish independent numerical fixtures | Added numbered raster, uneven shard boundaries, target event grid with partial overlap, depth/area/volume examples, geographic area ratios, calendar intervals, and concept codes | `storage/stage0/reference.properties` and `StorageReferenceFixturesTest`; fixtures are analytic oracles, not output from nonexistent mediated scanners. |
| Check durability and semantics baseline | Re-ran width, file round-trip, descriptor completeness/order, temporal publication, and scanner binding regressions; inspected key/worldview/edge schema paths | Existing storage tests plus section 8.2. Durable concept dictionaries and mediation properties are not implemented. |
| Publish authoritative findings and assign follow-ups | Updated STORAGE.md attribution, annotation tables, curves, cursor/export, temporal, and value-mediation sections; linked ANNOTATIONS.md and clarified API Javadocs | Section 8.3 is the staged work queue; runtime behavior is unchanged in stage 0. |

### 8.2 End-to-end traces

**Java declarations → descriptors.** In `klab.core.api`, `KlabFunction` exposes `split=-1`,
`fillCurve=UNSPECIFIED`, `minSizeForSplitting=0`, and `maxSize=0`. `ComponentRegistry` in
`klab.core.services` copies those values in `createContextualizerPrototype()` and derives the
primitive type from `Artifact.Type`. A method's annotation supplies its own values; no class-level
sharding fallback is applied. `ResourceAdapter` instead exposes `splits=1` with the same curve/size
members. The registry adapter implementation constructor copies those values and its descriptor
construction passes them to `AdapterDescriptor`; `shardingStrategy()` does not set a primitive
type. `Exporter` only adds a curve requirement to its prototype. The API-level geometry declarations
are a separate compatibility concern, not an instruction to resample storage. TODO dynamic split
and curve annotations are not implemented paths.

**k.IM source → annotation bean → actuator.** The sibling `klab-languages` repository's
`org.integratedmodelling.languages.kim/.../Kim.xtext` inherits annotation syntax from
`org.integratedmodelling.languages.observable/.../Observable.xtext`. `ANNOTATION_ID` permits a
lowercase identifier, while the resolver recognizes camel-case names for three sharding fields.
`FunctionCallSyntax.DEFAULT_ARGUMENT_NAME` is `value`. `LanguageAdapter.adaptAnnotation()` calls
`putUnnamed()` for that name, including explicit `value=...`, producing `_p1`. In contrast,
`DataflowCompiler.compileModel()` dereferences `annotation.get("value")`. Real parser tests and
compiler-bean tests cover the two boundaries separately and expose the mismatch; a programmatic
bean success is not reported as working source syntax.

`ObservationImpl` collects concept and observable annotations and merges model annotations for
the main output. Definitions and `override=true` follow the existing annotation precedence rules.
The compiler visits the resulting quality observation's annotations and sets an actuator strategy:
type/curve use uppercased enum lookup, split uses `Integer.parseInt`, sizes use `Long.parseLong`.
This is not an annotation schema validator. Fractional splits fail parsing, negative sizes/zero
splits are accepted, and `D2_XInvY` is damaged by uppercasing. Unrecognized names are ignored.

**Actuator → native attribution → storage.** `CompiledDataflow.harmonizeShardingInternal()` in
`klab.services.runtime` traverses children, then returns the recorded strategy for positive-ID
qualities. For new qualities it merges local, model, child, and runtime strategies, later concrete
values winning. `RuntimeService.getDefaultShardingStrategy()` supplies concrete semantic type,
geometry-derived curve, and processor-count/one split, overriding earlier concrete hints for those
fields. The final settings pass enforces short floats and clears sizes when space is not
distributed or parallelism is disabled. `CallDescriptors.shardingStrategy()` can read Java
requirements but has no caller integrating it into that merge. `ServiceContextScope` exposes a
strategy, but this harmonization method does not separately consult that scope accessor.

The native strategy is attached to observation contextualization data. `StorageManagerImpl`
requires it at creation; positive-ID reconstruction reads graph descriptors. `StorageImpl` creates
native layouts through `getGeometries()`: a count of one bypasses limits; a positive count goes
directly to geometry splitting; size hints affect only the nonpositive-count branch. Counts are
suggestions and the splitter needs actual grid bounds. Before using these beans as plan-cache keys,
note `ShardingStrategy.equals(ShardingStrategy)` is an overload, not `equals(Object)/hashCode`.

**Native storage → contextualizer.** `AbstractExecutor.execute()` first opens output scanners,
then requests each input with the output strategy and matches by scanner-list index/count. A
dependency failure can therefore occur after the output histogram was reset. `StorageImpl.scan()`
itself rejects strategy mismatch before opening scanners, but this does not preflight the entire
consumer. The executor's reflection binding can adapt float/double scanner types; it cannot ensure
different native shard layouts refer to the same cells. Finalization uses the native output.
`StorageImpl.scan()` can replace a strategy whose curve is UNSPECIFIED with the entire requested
strategy; that branch needs tightening before native-contract immutability can be assumed for all
legacy/incomplete descriptors.

**Observation-wide/export access.** `ArgumentMatcher` obtains native shards with a supplied event
or initialization for at-most-one time state, calls `getNativeScanner()`, adapts primitive types,
and invokes `ScannerAdapters.mergeScanners()`. Only the single-scanner case works. Exporter curve
metadata is not applied in this route, and initialization scanners are not forced readonly. This
is an additional integration point to migrate in stage 3, beyond `AbstractExecutor`.

**Detached query path.** `RuntimeService.submit()` sends ID-zero observations to `query()` rather
than the normal submission path. `queryResult()` produces an ID-zero view, copying the source
Observable, URN, value, contextualization data, and other metadata, while recording requested
geometry and actual coverage. It does not compile a unit/scanner mediation. `ResolutionGraph`
assigns internal reference keys to distinguish detached query results. `CompiledDataflow` excludes
query observations from durable links/assets and rejects them in restorable occurrence closures.
`StorageManagerImpl.getStorage()` indexes numeric observation IDs and only reconstructs positive
ones; the copied query metadata is not a durable source-storage binding. Stage 3 must carry that
source identity explicitly instead of treating zero as a shared storage key. These source-audit
findings do not claim new query execution or persistence support.

**Temporal state.** `TemporalScalarExecution` requests each input's native strategy through
`LocalTemporalWriteSet`; the write set rejects a differing layout. `StorageImpl` resolves exact
event-keyed shards, a covering revision, or an allowed committed baseline, and rejects missing
state. `stageTemporal()` stages buffers/descriptors and uses transaction callbacks to expose or
roll back them. Ordinary `AbstractExecutor` refuses temporal quality output. Existing
`TemporalStorageTest` verifies supported publication/reconstruction behaviors. Stage 0 does not
turn temporal scalar iteration into spatial alignment or authorize partial-quality writes.

**Unit/range/currency execution.** `ValueMediator.convert` defines destination receiver semantics.
`UnitImpl` delegates to `UnitServiceImpl` in destination-first order; the latter's misleading
`from/to` parameter names must not lead a fix to reverse the public receiver contract. Ordinary
m/mm and Celsius/Kelvin tests pass. Compatibility returns false even for convertible units;
algebra/split/contextualize and ordinary locator conversion are unfinished. `AbstractMediator`
executes supplied dimension-factor operations with each locator's standardized metric, but no
complete compiler builds that operation list. `UnitImpl.aggregatedDimensions` is metadata, not a
working contextualizer. `NumericRangeImpl` has working bounded conversion but incomplete locator
conversion and an unsafe non-range validation expression. Currency methods return null/false and
there is no rate service. No value mediators are integrated into scanners.

**Geometry, missingness, and persistence.** `ShapeImpl.getStandardizedArea()` uses a metered shape,
not square degrees; a production area policy still needs per-cell accuracy/projection tests.
Geographic and calendar fixture results are independently calculated references, not claims about
that provider. Geometry equality, serialization, and bounding-box overlap alone do not prove
aligned grid support; stage 1/2 must define a conformance fingerprint and tolerance. Temporal
partial-coverage validation already rejects unsupported occurrence closures. Native scanners have
no shared validity channel for integer/boolean/keyed uncovered cells; histogram accumulation skips
NaN and leaves missing-count tracking as TODO. `HAS_DATA` descriptors store native geometry,
strategy, and primitive payload identity; graph/filesystem publication is not universally atomic.
`AFFECTS` already carries causal/role/rank/read-state properties but no complete durable mediation
description. `DataKey` and `KeyScanner` exist, yet native and temporal keyed access reject; no
complete dictionary-plus-root-worldview restoration contract was found in the graph storage path.

### 8.3 Support matrix and assigned needs

“Working” here is limited to the tested or inspected boundary listed; it never implies working
consumer mediation. S0 identifiers are stable follow-up references for subsequent stage records.

| ID | Baseline support/finding | Next required completion |
|---|---|---|
| S0-01 | Java function/adapter/export declarations are recorded, not enforced throughout attribution | Stage 3: consume requirements explicitly and distinguish hard capabilities from preferences. Include resource adapters, not just Java functions. |
| S0-02 | k.IM `_p1` versus `value` mismatch affects positional and named value forms | Stage 1 validation/contract prerequisite: choose one canonical argument convention and adapt both source forms; add end-to-end source-to-strategy tests. |
| S0-03 | Camel-case compiler names do not parse; mixed-case curve enum cannot survive uppercasing | Stage 1 prerequisite: choose documented source names/aliases and enum resolution, update grammar only if needed, and validate malformed forms. |
| S0-04 | Effective annotation precedence works for programmatic beans, but runtime defaults override type/curve/splits | Stage 3: explicitly separate native attribution from consumer requirements and update precedence tests/docs together. |
| S0-05 | Size hints are bypassed for explicit counts and cleared for scalar/no-parallel execution | Stages 1–2: specify state-count versus memory budgets, enforce true limits, and test split feasibility. |
| S0-06 | Strategy equality is not value equality for Object/cache keys; UNSPECIFIED scan can replace native strategy | Stage 1: immutable canonical plan keys and explicit legacy attribution handling. |
| S0-07 | Small-grid linear/2D mappings work; ZYX aliases XYZ, Hilbert throws, mapping narrows to int | Stage 2: publish supported curve capability table; implement or reject distinct ZYX, add checked long-index codecs. |
| S0-08 | Native/float-double cursors work; nextLong advances and lacks exhaustion checking | Stage 1: commit cursor, locator, and exhaustion semantics; preserve compatible callers. |
| S0-09 | Storage mismatch rejection is side-effect-free locally, but executor opens outputs before checking inputs | Stage 3: preflight the entire binding plan before any output reset. |
| S0-10 | Export argument binding only merges a singleton and may expose writable initialization data | Stage 3: use the same planned readonly consumer path and honor export curves. |
| S0-11 | ID-zero query results are detached views, with no mediated source-storage binding | Stages 3–4: bind by source identity/revision, preserve requested semantics separately, and avoid zero-key collisions/graph assets. |
| S0-12 | Temporal write sets are native-layout only; supported scalar revision protocol exists | Stages 3/8: preserve prior/current state and revision publication; do not bypass unsupported output gates. |
| S0-13 | Ordinary unit conversion and affine offsets work; service argument names are misleading | Stage 4: preserve `target.convert(value, source)`, rename/clarify service parameters consistently, and compile primitive kernels. Documentation clarified in stage 0. |
| S0-14 | Compatibility/contextualization/algebra remain stubs despite a usable operation executor | Stages 4–5: complete semantic compiler, extent factor selection, no-data propagation, and per-cell locator wiring. |
| S0-15 | Geographic area/calendar fixtures differ by location/interval; no scanner metrics integration | Stage 5: choose physical metric/calendar providers and prove cell-local results and bounded caching. |
| S0-16 | Bounded range conversion works; currency and locator-range conversion are unfinished | Stage 4: validate range domains/degeneracy; define pinned rate and rounding provenance. |
| S0-17 | Non-conformant mediation rejects; partial-coverage writes remain unsupported | Stage 6: implement explicit sampling/coverage policy and semantic conversion order; retain write gates. |
| S0-18 | KEYED buffer width exists but scanners reject; durable dictionary/worldview contract absent | Stage 7: dictionary allocation/versioning, root commitment, restoration, semantic translation. |
| S0-19 | Native graph descriptors restore; mediation binding/run metadata are absent | Stage 4: version binding-specific AFFECTS descriptions; stage 8: publish/recover with data/dictionary revisions. |
| S0-20 | No common validity channel or mediated histogram semantics | Stage 1: define view validity/metadata; stage 6/7: uncovered and categorical behavior; stage 8: verify persistence boundaries. |

The user accepts either consistent convention provided it is documented. Stage 0 records the
existing destination-receiver unit convention; it does not introduce a second conversion order.
Annotation argument/name normalization remains a stage-1 design choice, with both Java and k.IM
contracts and adapter capabilities required to stay documented together.

### 8.4 Fixture inventory and limitations

Reusable fixture resource:
`klab.core.services/src/test/resources/storage/stage0/reference.properties`.

- A 5×3 numbered XY raster with uneven linear shard ends `[4,9,15]` and independently listed YX
  and reversed-Y expected sequences. These linear partitions are a reference directory, not a
  claim about the current geometry splitter's tile choices.
- Source elevation over `[0,3]×[0,2]`, unit cells and a linear center-value function; event target
  `[2,4]×[0,2]` uses half-width cells. Expected nearest results include explicit outside NaNs.
- Precipitation depth/area/volume vectors include missingness; a second operation-engine test
  proves that two cell locators with areas 100 and 250 produce different volumes for equal depth.
- Equal angular cells centered at latitude 0 and 60 have area ratio 1:0.5 under a declared
  spherical model. This ratio is an analytic oracle, not an ellipsoidal/geodesic accuracy claim.
- UTC February/March 2023, February 2024, and the 2024 year have durations 28/31/29/366 days.
  Local time-zone transitions, ellipsoidal metrics, and composed spatial conservation are later
  acceptance tests, not stage-0 completions.
- Synthetic worldview `test:storage-worldview@1`, codes 1/2 for Forest/Grassland, and explicit
  fixture-only missing code 0. These names are not installed concepts; zero is not a proposed
  production dictionary sentinel. The fixture prevents future tests from relying on reasoner IDs.

The operation-engine tests supply mocked area/duration locators to isolate existing mechanism
from unfinished context construction. The reference-fixture tests validate oracles and available
curve helpers; they do not exercise a nonexistent spatial mediation implementation. No performance
threshold has been established. Stage 8 must measure native and mediated throughput under a
recorded workload before choosing a regression budget.

### 8.5 Verification record

The focused multi-module run uses JDK 21 and Maven 3.9.5 with cached dependencies (`-o`). It covers
API, common/core services, resolver, resources (including the real parser), and runtime. A first
sandboxed attempt could not resolve existing API classes during test compilation; the same Maven
command succeeded with normal host access. This was an execution-environment limitation, not a
production code change. Initial fixture assumptions exposed the named-value/camel-case syntax
gaps; corrected baseline tests now characterize the observed behavior. A split fixture was also
given the explicit bounds the current grid splitter requires.

```powershell
.\mvnw.cmd -o -pl klab.services.runtime,klab.services.resolver,klab.services.resources -am "-Dtest=ShardingStrategyBaselineTest,FillCurveTest,GeometryAndCurvesTest,ComponentRegistryShardingTest,UnitMediationBaselineTest,ShardingAnnotationsBaselineTest,ShardingAnnotationSyntaxTest,StorageMediationBaselineTest,StorageReferenceFixturesTest,ScannerAdaptersTest,StorageManagerImplTest,StorageReconstructionTest,TemporalStorageTest,AbstractExecutorScannerBindingTest,DataflowAnnotationsTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **57 tests, zero failures/errors/skips**, BUILD SUCCESS (2026-09-20). Module totals: API
18, core services 30, resolver 5, resources 3, runtime 1. An additional focused
`ShardingAttributionBaselineTest` run exercises the actual runtime harmonizer:

```powershell
.\mvnw.cmd -o -pl klab.services.runtime -am "-Dtest=ShardingAttributionBaselineTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Result: **2 tests, zero failures/errors/skips**, BUILD SUCCESS (2026-09-20). The test fixture
explicitly supplies runtime settings; the initial mock omitted them and was corrected. Combined
verification covers **59 tests**, including **26 new tests in eight stage-0 suites**. Raw local
execution logs are retained under `target/storage-stage0/` (build artifacts, not committed
documentation); this section is the durable summary.

No live external database, distributed backend, currency
provider, full-service query integration, or performance benchmark was run. Such behavior remains
assigned to its delivery stage and is not inferred from this focused suite.


## 9. Stage 1 implementation and completion trace

The committed API and operational contract are in [STORAGE.md](STORAGE.md#planned-read-sessions-s1).
This section records development choices and evidence. Section 8 is the historical S0 audit;
its syntax and native-strategy defects are superseded where explicitly completed below.

### 9.1 Requirements and delivered work

| Need | Delivered implementation | Evidence / remaining scope |
|---|---|---|
| Immutable request/planning | `StorageScan.Request`, `Layout`, `Slice`, partition/semantic snapshots; additive `Storage.plan/open/scanCapabilities` | Metadata-only planning, budget/alignment rejection, defensive copies and foreign-provider rejection in `StorageScanTest` |
| Consumer view independent of physical shard | `StorageScan.View` and `Location`; physical `shard()` retained | Float-native/double-view test; views rejected by native finalization; no graph view descriptors |
| Cursor and validity contract | Strict task-local primitive cursors, nonadvancing validity/location/peek, explicit exhaustion and closed-session errors | Mixed cursor operations, NaN/false validity, no advance on readonly writes; legacy iteration retained |
| Primitive indexed/block provider boundary | Internal `IndexedStorageReader` / `LocalIndexedReader`, exact native types, caller-owned arrays, bounded transfers | Block budgets, source bounds, wrong-type rejection and Long.MAX_VALUE precision |
| Pinned source lifecycle | Provider-owned native plans, initialization generation check and read leases; concrete committed temporal shards | Stale-plan failure, previously acquired writers blocked, independent sessions, cancellation and partial-open cleanup; later temporal publication leaves an open historical scan unchanged |
| Portable metadata | Version-1 immutable `Description`, canonical geometry encodings, structural equality, length-prefixed SHA-256 fingerprint | JSON round-trip equality/fingerprint; initialization changes alter revision token/fingerprint; executable handles are not serialized |
| Precision and histograms | Lossless default, explicit double-to-float permission; other arithmetic casts rejected; view histogram unavailable | Float widening, narrowing to infinity, NaN propagation, native long/boolean behavior |
| Stable native contract | Defensive copies in storage, requests and physical shard strategy access; remove silent UNSPECIFIED replacement | Mutation regression test; existing reconstruction and binding suites remain green |
| Unified k.IM specification | Lowercase names, positional/named value equivalence, case-insensitive enums without changing enum spellings, common validation | Real parser + LanguageAdapter tests and resolver tests; source grammar unchanged |
| Java function and adapter consistency | Registration validation; preserve adapter `maxSize` in registry descriptor; shared adapter annotation decoder | Function/export registration and adapter declaration tests; Java runtime requirement binding remains S3 |
| Disable sharding for tests | Preserve unconditional runtime setting override for new attribution | Actual harmonizer test overrides model/child split and size hints; positive-ID source layout remains unchanged |
| Export and text-value access | Explicit same-planner requirement in S3, with boundary formatting and streaming lifetime acceptance | Traced controller -> BaseService -> language/ArgumentMatcher; no premature endpoint/export implementation claimed |
| Low allocation by default | Primitive scalar/block kernels; per-partition metadata only; reader-local cancellation locking | Measured allocation regression below; future remapping/conversion must retain this property |

### 9.2 Specification decisions

- k.IM uses `type`, `split`, `maxsize`, `minsplitsize`, `fillcurve`; both `@split(4)` and
  `@split(value=4)` are valid. Legacy camel-case programmatic beans receive a clear rejection.
- Java keeps existing member names. Functions default to unspecified `split=-1`; adapters retain
  conservative `splits=1`. These differences are intentional and documented rather than silently
  changing every adapter to parallel execution.
- Sizes are states per shard, never bytes. Minimum is soft; positive maximum is checked by the
  planned native reader. Zero is unspecified. Invalid bounds fail at annotation/plan boundaries.
  Native splitter improvements and full consumer enforcement belong to S2/S3.
- Local plans currently require exact native layouts and semantics. Unsupported policy enum values
  are explicit future requests, not implementations. S1 does not perform unit conversion, split/
  merge, geographic resampling or concept dictionary mediation.
- Metadata fingerprints include a storage-instance/write-generation token. They identify local
  snapshots, not globally durable content; cross-provider cache/provenance identity remains staged.
- Scanner lifetime belongs to the session. Storage owns buffers; leases guard initialization
  mutation while temporal publication can append separate immutable revisions. Reader locks are
  local, so reads on independent shards do not serialize against each other.
- Native geometry splitting now uses plain descriptors, avoiding unnecessary reconstruction of
  service-local scales. This fixes the multi-shard session fixture without claiming a new splitter.

### 9.3 Performance evidence and limits

`primitiveFloatAdaptationAndValidityDoNotAllocatePerValue` warms the primitive float-to-double
read/validity path, then measures thread allocation for one million reads. It uses a primitive
accumulator and a fixed 64 KiB allowance to detect per-value boxing while tolerating JVM bookkeeping.
The HotSpot allocation counter is capability-gated; unsupported JVMs explicitly skip the test.

An initial 24-byte/read result was investigated with JFR. The allocation originated in Mockito's
instrumented `Enum.ordinal()` (`WeakConcurrentMap.LatentKey`), not boxed values. The hot validity
check now uses direct type comparisons and the measured run reports **0 bytes for 1,000,000 reads**.
This is a focused allocation regression, not an end-to-end throughput benchmark or a claim that
all consumers, native writes, persistence, or contextual conversions allocate nothing. Legacy
primitive-iterator boxed `next()` remains available; efficient callers must use `nextLong()` and
typed value methods. Explicit `location()` and text formatting necessarily allocate on demand.

S2/S4/S5 must preserve primitive conversion kernels and bounded scratch buffers. S8 still owns
representative dataset throughput, contention, locality, mapped-buffer limits and remote-provider
benchmarks. Native write lease coordination is conservative and also needs that contention audit.

### 9.4 Remaining integration and corrected audit findings

S0's source-language failures are fixed; its tests now assert successful lowercase source decoding
and precise rejection of invalid declarations. Native strategy mutation through an unspecified
curve is removed. The S0 adapter trace overstated preservation of maximum size: review during S1
found the registry constructor dropped `maxSize`; the registry now carries it into the descriptor.

S3 must migrate `AbstractExecutor` and `ArgumentMatcher` together, preflight inputs before opening
writable outputs, honor adapter/function/exporter requirements, and retain explicit source identity
for detached queries. Export streams own sessions until close/cancellation. Individual-value API
text responses must use the same location/revision/validity/conversion path, preserve exact longs,
and format only at the response boundary. Bounded point access must not traverse or materialize
an entire dataset. S4/S5 then add ordinary and contextual semantic conversion to all these routes.

No external database, remote storage provider, production exporter stream, or HTTP text-value
integration was exercised in S1. These remain explicit staged deliverables.


### 9.5 Verification record

On 2026-09-20, JDK 21 and the repository Maven wrapper, offline reactor build:

```powershell
.\mvnw.cmd -o -pl klab.services.runtime,klab.services.resolver,klab.services.resources -am "-Dtest=StorageScanTest,ShardingStrategyBaselineTest,FillCurveTest,GeometryAndCurvesTest,ShardingAnnotationsBaselineTest,DataflowAnnotationsTest,ShardingAnnotationSyntaxTest,ComponentRegistryShardingTest,StorageMediationBaselineTest,StorageReferenceFixturesTest,UnitMediationBaselineTest,ShardingAttributionBaselineTest,TemporalStorageTest,ScannerAdaptersTest,StorageManagerImplTest,StorageReconstructionTest,AbstractExecutorScannerBindingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

**BUILD SUCCESS: 72 tests, zero failures/errors/skips.** Module totals: API 18, core services 41,
resolver 5, resources 4, runtime 4. Allocation measurement: **0 bytes / 1,000,000 primitive reads**.
The final run includes defensive shard-strategy copying, temporal revision pinning, real-parser
annotation tests, native storage reconstruction, legacy executor binding and the parallelism-off
override. `git diff --check` passes. Local logs/profiling artifacts are in `target/storage-stage0/`;
this record is the durable summary. S1 is complete within the native-read scope above.


## 10. Stage 2 implementation and completion trace

The authoritative contract is [STORAGE.md](STORAGE.md#conformant-scanner-mediation-s2).
S2 adds conformant mediation to planned readonly sessions. It does not change legacy native
allocation, writable scanning, the file format, or runtime strategy attribution. The legacy
mismatched-strategy rejection is retained until S3 migrates the consumer binders.

### 10.1 Needs, delivery and evidence

| Need | Delivered | Evidence / boundary |
|---|---|---|
| Grid conformance | `ConformantScan` derives a common integer lattice from persisted geometry, checks CRS, resolution, integer cell edges, shape, located non-spatial context and coverage | Shift, resolution, CRS, shape-CRS conflict, gaps and overlaps rejected before buffer access; observation outer-edge omissions rejected too |
| Source directory | Balanced bounding-volume directory over actual shard boxes | Uneven physical partitions; no assumption of equal sizes, list-position correspondence or transient parent metadata |
| Long-index codecs | Checked mixed-radix arithmetic for D1_LINEAR, D2_XY, D2_YX, D2_XInvY and D3_XYZ | Round trips at offset 9,000,000,007; explicit 3D coordinate oracle; overflow rejection. D3_ZYX/Hilbert remain rejected |
| Split/merge | Deterministic automatic partitioning and explicit consumer partitions | One-to-many, many-to-one, many-to-many, reversed explicit order, uneven boundaries and singleton boxes |
| Primitive values | `ConformantReader` delegates exact primitive reads through one source lookup | 180 small-grid combinations across five types, three source/target curves and native/consumer split counts; Long.MAX_VALUE-based values stay exact |
| Block access | Direct contiguous native spans and bounded primitive gathers into caller-owned arrays | Fifteen type/traversal combinations crossing an uneven shard boundary; range/budget errors remain explicit |
| Value adaptation / validity | Existing session cursors compose with the mapped reader | Float-to-double view, NaN propagation, exact long/boolean values; S1 narrowing policy unchanged |
| Immutable planning/cache | Bounded metadata-only LRU keyed by source snapshots plus the full request | Repeated requests reuse plan handles; writes invalidate old handles and fingerprints; cache contains no reader/buffer payload |
| Resource ownership | Existing source leases and session closure cover all mapped cursors | Independent mapped sessions, cancellation and stale-plan checks; S1 partial-open cleanup and temporal pin tests retained |
| View metadata | Version-2 description records remapping and consumer partitions; multi-source views expose `view().sources()` | JSON/fingerprint round trips, explicit consumer order, source/view distinction; `shard()` rejects an ambiguous multi-source view |
| Edge requests | Empty partition list derives a layout; native empty identity retains strict exhaustion; no cells are fabricated | Empty cursor never accesses its reader, singleton codec bounds, maximum=1 partition generation |
| Size hints / budgets | Soft minimum/count preference, hard consumer maximum; bounded directory links and cache admission | Different native/consumer limits, singleton partitions and preserved native strategy |
| Primitive default / allocations | Reused coordinate scratch and cached lookup, no payload boxing or per-cell metadata | Separate scanner-peek and moving indexed-gather allocation checks; measurements below |

### 10.2 Decisions and integration boundaries

- Remapping covers nonempty rectangular regular grids in one to three spatial axes. Existing exact
  scalar/empty reads keep their native path. Empty `partitions` means automatic derivation; it is
  not a request to silently discard coverage. A missing source remains an error.
- Geometry is checked against the observation's spatial support, not merely the bounding union
  of the supplied shards. This detects a missing outer strip as well as internal holes.
- Located source time remains selected by `Slice`. Spatial-only target geometry inherits it;
  explicit different non-spatial extents fail. Observation-wide time metadata is not confused with
  event-local partition metadata. Temporal resampling is not introduced.
- Persisted splits can omit CRS and inherit it from their owner. CRS strings are compared literally;
  shape and grid CRS must agree. No reprojection or implicit projection aliases are introduced.
- The new codec avoids the legacy int-indexed curve mapper. D3_ZYX remains ambiguous for existing
  bytes because the legacy implementation aliases XYZ, so S2 rejects it rather than changing native
  meaning. D3_XYZ and linear 3D mapping are verified independently.
- Consumer curves apply inside each partition. Request one consumer partition for one global
  traversal, including future exporters. Consumer geometry/type comes from `view()`, not a physical
  shard descriptor; S3 binders must respect the multi-source `shard()` rejection.
- Automatic splits divide the largest box along its longest axis. Count/minimum are preferences;
  a positive maximum may increase the count. Explicit partitions determine exact order and must
  honor the maximum. No execution parallelism is launched by planning; the runtime disable setting
  still controls new native attribution.
- `Budget.maxPartitions` bounds both sides, with a source-reference cap of eight times that budget.
  The metadata cache has at most 16 entries, each admitted only below 1024 combined source, target
  and source-reference items. Larger valid requests bypass caching. Plans remain provider-bound.
- Version 1 metadata remains readable. Version 2 adds INDEX_REMAP plus the primitive operation;
  structural deserialization does not replace provider conformance validation. Optional null layout
  types have a reserved fingerprint marker. WKT-bearing geometry round trips normalize escaped
  commas without changing the global geometry parser.

S3 remains responsible for `AbstractExecutor`, adapter/function requirements, exporters, detached
queries and individual-value/text API access. S4/S5 add ordinary/contextual value semantics through
this same primitive path. Masks, changed coverage/resolution, affine transformations, unsupported
curves and remote-provider execution remain outside S2. No new native shards or graph views are
persisted by mediation.

### 10.3 Verification record

Verification uses JDK 21, the repository Maven wrapper and offline dependencies. Intermittent
missing-class errors occurred when compiling into the active checkout's shared target directories.
The final verification uses an isolated source copy under `target/storage-stage2/verify`, containing
HEAD plus the current Java changes, so its compiler outputs are independent of desktop IDE builds.
No build configuration or unrelated source was changed to bypass these errors.

```powershell
.\mvnw.cmd -o -pl klab.services.runtime,klab.services.resolver,klab.services.resources -am "-Dtest=StorageScanTest,ConformantScanTest,ShardingStrategyBaselineTest,FillCurveTest,GeometryAndCurvesTest,GridNImplTest,ShardingAnnotationsBaselineTest,DataflowAnnotationsTest,ShardingAnnotationSyntaxTest,ComponentRegistryShardingTest,StorageMediationBaselineTest,StorageReferenceFixturesTest,UnitMediationBaselineTest,ShardingAttributionBaselineTest,TemporalStorageTest,ScannerAdaptersTest,StorageManagerImplTest,StorageReconstructionTest,AbstractExecutorScannerBindingTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The allocation checks warm the paths, then measure one million operations with a fixed 64 KiB
allowance for JVM bookkeeping. One check moves between cells and source shards instead of repeatedly
peeking a cached offset. These are allocation regressions, not throughput benchmarks; S8 retains
large-dataset locality, contention, disk and remote-provider performance work. New tests do not
require an external database or service.


Final result (2026-09-20): **BUILD SUCCESS — 96 tests, 95 passed, one pre-existing disabled test,
zero failures/errors**. Module totals: API 18, core services 65 (one skipped), resolver 5,
resources 4, runtime 4. The skipped test is `GridNImplTest.projection_canBeNullOrProvided`, already
annotated `@Disabled`; no S2 test is skipped. All eleven new `ConformantScanTest` methods pass.
Native scanner, mapped scanner, and moving cross-shard gather checks each report **0 bytes over
1,000,000 measured operations** in the final run. The latter verifies the actual moving-index path.

The verified Java sources were hash-compared with the active checkout after the run. The final log
is `target/storage-stage2/verification.log`; `git diff --check` passes. No production export,
HTTP text-value route, live database or remote provider was exercised. S2 is complete within the
conformance and supported-curve boundaries documented in STORAGE.md; S3 consumer integration is next.


## 11. S3 implementation trace

### Needs, decisions and delivered behavior

| Need | Completion and evidence |
|---|---|
| Stable producer attribution, including adapter declarations | Child strategy inheritance removed. The producer's own function/adapter declarations participate between model and runtime hints. Positive-ID storage remains unchanged; runtime disable still forces one native split and clears size hints. Existing attribution regressions retained. |
| Validate inputs before output reset | Metadata-only `Storage.writeLayout`; all dependency plans use explicit output IDs, geometry, size and curve. Sessions and reflection bindings validate before writable acquisition. `StorageConsumerExecutionTest` checks mixed-type/alignment execution and failure before any output scan, retaining the binding-specific cause. |
| Independent parameter types and metadata | Contextualizer reflection chooses each input scanner class independently. Generic inputs retain source primitives; Observation, View and native Shard bindings remain explicit. A mediated input cannot masquerade as a physical Shard. Explicit FloatScanner requests permit narrowing; other reads remain lossless. |
| Export one traversal over many shards | Freeform invocation uses `StorageReads`, exporter curve metadata and typed read plans; overload type mismatch does not acquire writers. Real reflected exporter tests traverse three source shards and preserve the requested curve. |
| Stream ownership and cancellation | Invocation resources transfer to InputStream; EOF, close and read failure release leases. Failed reflection, unmatched overload and synchronous returns release at invocation exit. Tests prove reads remain usable after method return and writes remain blocked until stream completion. |
| Point/text API | Added immutable serializable Point, runtime interface/client and authorized HTTP route. Long offset seek is bounded indexed access through the same planner; text preserves exact longs, zero/false, NaN validity and locale-independent formatting. Point JSON and policy/bounds regressions included. |
| Detached ID-zero quality queries | Existing durable source selected in the authorized context; one source ID retained in query metadata. Requested semantics/geometry remain ephemeral. Query contextualization/layout metadata is copied. Storage creation at zero is rejected; lookup resolves the source. Tests cover independent concurrent views and independent query strategy beans. Unsupported units fail until S4. |
| Temporal reads and write-set ownership | PRIOR and CURRENT snapshot read sessions remap into output partitions before writable acquisition. Sparse CURRENT offsets become primitive sorted arrays for allocation-free lookup; later writes cannot mutate snapshots. Rollback/commit closes retained sessions. Existing temporal process tests and new snapshot/remap test cover this path. General temporal contextualizers remain gated. |
| k.Actors full-stack harness | Added `inspector.scancheck` and `celltext`, with catalog/argument checks and Java invocation tests. The real testcase under `docs/testcases/klab/staging/vxii/storage.kactors` parses with the installed grammar. TESTING.md documents copying it into the staging project and running with parallelization enabled/disabled. |
| Keep default reads primitive | All scan/seek/remap paths use primitive access; string allocation is confined to text requests and bounded inspector samples. Temporal sparse writers retain their existing boxed representation, while read offset lookup no longer boxes. S1/S2 allocation regressions are retained. |

### Scope boundaries and manual acceptance

STORAGE.md is authoritative for API contracts. This trace does not claim units/ranges/currencies,
contextual conversion, non-conformant resampling or concept dictionaries: those remain S4–S7.
The existing resource-adapter input-payload/remote dependency-transfer placeholders remain outside
this scanner-binding change. Function/adapter layout fields are producer preferences under the
recorded precedence, not a promise of separately writable consumer layouts.

The available checkout did not contain `klab.staging.vxii`; the harness source is therefore stored
in this repository for copying into that project. It uses a small rectangular projected grid and
requires live geography semantics and an elevation resource. JUnit validates its syntax and Java
verbs. No full-stack assembly, live endpoint/remote service test or harness execution was attempted,
as requested. The user-run harness should inspect report outcomes and cleanup, not only agent exit.

### Verification

Verification uses the isolated source checkout under `target/storage-stage2/verify`, reusing the
S2 setup to avoid shared IDE compiler output. Sources are synchronized from the current worktree;
there is no build-configuration workaround. Initial runs caught and corrected a test fixture's
registry setup, a missing test-executor method and a nested-map lexical ambiguity in the actual
k.Actors file. These were not waived. The final combined regression result is recorded below.


Final result (2026-09-20): **BUILD SUCCESS — 146 tests, 145 passed, one pre-existing disabled test,
zero failures/errors**. Module totals: API 18, core services 90 (one skipped), resolver 5,
resources 5, runtime 28. Runtime server compilation succeeds. The sole skip remains
`GridNImplTest.projection_canBeNullOrProvided`; no new test is disabled. Native, mapped and moving
cross-shard allocation checks each report **0 bytes per 1,000,000 measured operations**.

```powershell
.\mvnw.cmd -o -pl klab.services.runtime.server,klab.services.resources -am "-Dtest=StorageScanTest,ConformantScanTest,ShardingStrategyBaselineTest,FillCurveTest,GeometryAndCurvesTest,GridNImplTest,ShardingAnnotationsBaselineTest,DataflowAnnotationsTest,ShardingAnnotationSyntaxTest,ComponentRegistryShardingTest,StorageMediationBaselineTest,StorageReferenceFixturesTest,UnitMediationBaselineTest,ShardingAttributionBaselineTest,TemporalStorageTest,ScannerAdaptersTest,StorageManagerImplTest,StorageReconstructionTest,AbstractExecutorScannerBindingTest,StorageConsumerExecutionTest,StorageConsumerIntegrationTest,TemporalProcessIntegrationTest,RuntimeServiceQueryTest,CoreActorLibraryInspectorTest,StorageHarnessSyntaxTest,ExportDispatchTest,ExportResponseTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

The final log is `target/storage-stage2/s3-verification.log`. All 35 changed Java/harness sources
were hash-compared against the isolated verification checkout with no differences. `git diff
--check` passes. The verification includes the final correction resolving temporal query bindings
through their durable source, covered by a new read-before-write ordering regression. S3 is
complete within the documented scanner and provider boundaries; S4 is the next numeric mediation stage.


## 12. Stage 4 implementation trace

S4 implements ordinary value mediation on the shared planned-read routes. The authoritative
contract is the S4 section of [STORAGE.md](STORAGE.md). S5 and later stages are deliberately held
until the user-run full harness has confirmed this first phase.

| Need | Completion and proof |
|---|---|
| Resolve unit direction and compatibility | Destination-first names and JavaDoc; real dimensional compatibility; restart-safe definition parsing; scaled-unit cache correction. Primitive coefficients support multiplication and affine offsets. Asymmetric m/mm, Celsius/Kelvin and scaled-unit tests. |
| Lazy, efficient scanner conversion | Version-3 portable descriptions and cached primitive kernels in local sessions. Remap precedes conversion, final primitive narrowing occurs once; NaN and cursor semantics retained. Real FLOAT/DOUBLE scanner tests include repeatable peek, seek, missing values and text. INTEGER/LONG/BOOLEAN semantic conversion explicitly rejects; identity remains unchanged. |
| Range contract | Finite positive widths, increasing bounds, matching endpoint inclusion; exact included endpoints; no clipping/extrapolation; bad domains fail. Fixed upper-exclusive accessor and aligned public bounded-range conversion checks. |
| Deterministic currency | Explicit RateProvider and immutable pinned Rate with definitions, valuation Instant, provider/version, rate, exchange/inflation and binary64 rounding. CODE@year distinguishes base-year semantics. No network lookup in a plan or scanner. Fixed quote, bad direction, unavailable rate, invalid rate and compound-operation tests. |
| Multiple bindings on one dependency pair | Each prerequisite AFFECTS edge carries its own version-1 Binding JSON, identified by localName:rank. Existing pseudograph and Neo4j CREATE path already preserve parallel relationships. Existing rank/role/readState/semanticRelations retained. Transaction tests publish two differently converted bindings and deserialize their properties independently. |
| Restart semantics | In-process binding identities follow temporary-to-durable source publication. Occurrence snapshots retain requested input observables and pinned rates instead of replacing them with native source semantics. Restored bindings resolve the real source separately. Plan/binding/evidence JSON round trips validate schema and reproduce conversion metadata. No persisted converted observation or HAS_DATA descriptor. |
| Per-run provenance | Activity metadata receives an immutable Evidence JSON entry per binding invocation, containing the complete source revision/event/plan. Unique keys preserve earlier evidence. Transaction rollback removes staged entries; consumer failure publishes neither binding relationships nor consumer. |
| Queries, exports and individual text values | ID-zero views keep requested observables and query-specific rates without allocating graph assets. Durable dependency edges resolve the positive source. Point requests carry optional quotes. Exports accept storageObservable/storageCurrencyRate and pass detached views to the existing matcher/session lifecycle. |
| Temporal reads | PRIOR and CURRENT apply the same compiler to pinned snapshots before output writers open. Regression verifies independent converted prior/current values and rollback closes both sessions. |
| k.Actors first-phase harness | New static inspector.unitcheck checks an independent factor/offset plus traversal/partition/text agreement. Existing staging testcase now verifies elevation m→mm. Actual testcase parser and inspector catalog are verified in JUnit. |

### Decisions and limits

S4 supports one changed ordinary mediator per plan and FLOAT/DOUBLE conversion. Integer-valued
semantic conversion requires a future explicit overflow/rounding policy. Contextual unit flags and
extent distributions are carried in semantic identity; S4 does not infer cell areas or durations.
Range endpoint inclusion must match; this avoids converting an included source endpoint into an
excluded target endpoint. Currency plans use binary64 scientific arithmetic, without rounding to
minor monetary units. Exchange and inflation are separate operations. Rates are supplied explicitly
by the caller/provider and preserved in binding and execution provenance.

Identity/conformant description versions 1/2 keep their original fingerprint encoding. Version 3
includes the semantic meaning key and conversion; old constructor overloads remain available.
Converted views do not advertise native histograms. Existing process/descriptive causal conventions
remain intact. Non-quality dependency edges retain their existing semantic behavior.

### Verification and corrections

Builds use an isolated source copy at `target/storage-stage2/verify`; shared IDE outputs are untouched.
During verification, a moving-cursor allocation regression was traced with JFR to Mockito's globally
instrumented `List.getFirst`, not boxed conversion arithmetic. The mapped reader now caches its
immutable primitive type once, removing the lookup from production reads as well. The ordinary
conversion path allocates no per-value wrapper, locator, coefficient or coordinate object.

JUnit covers compilation through runtime server, actual parser input, ordinary/temporal reads,
multiple bindings, transaction publication/rollback, JSON reconstruction and primitive allocation.
It does not prove a live Neo4j restart or a full resolver/service/HTTP round trip. Those remain in the
user-run full-stack acceptance gate; no live stack or currency provider was started.

Final result (2026-09-20): **BUILD SUCCESS — 167 tests, 166 passed, one pre-existing disabled test,
zero failures/errors**. Module totals: API 18, core services 101 (one skipped), resolver 5,
resources 5, runtime 38. Runtime server compilation succeeds. The sole skip remains
`GridNImplTest.projection_canBeNullOrProvided`; no new test is disabled. Native, mapped, moving-gather
and moving-mediated-cursor checks each report **0 bytes per 1,000,000 measured operations**.

The final run includes the source-ID publication/JSON transport regression, old semantic-snapshot
compatibility, and rejection when final float narrowing would cross an excluded range endpoint.
All 47 changed Java/harness source files match the isolated verification copy by SHA-256.
`git diff --check` passes. The final log is `target/storage-stage2/s4-verification.log`.

```powershell
.\mvnw.cmd -o -pl klab.services.runtime.server,klab.services.resources -am "-Dtest=ValueMediationTest,ClassificationTransactionTest,StorageScanTest,ConformantScanTest,ShardingStrategyBaselineTest,FillCurveTest,GeometryAndCurvesTest,GridNImplTest,ShardingAnnotationsBaselineTest,DataflowAnnotationsTest,ShardingAnnotationSyntaxTest,ComponentRegistryShardingTest,StorageMediationBaselineTest,StorageReferenceFixturesTest,UnitMediationBaselineTest,ShardingAttributionBaselineTest,TemporalStorageTest,ScannerAdaptersTest,StorageManagerImplTest,StorageReconstructionTest,AbstractExecutorScannerBindingTest,StorageConsumerExecutionTest,StorageConsumerIntegrationTest,TemporalProcessIntegrationTest,RuntimeServiceQueryTest,CoreActorLibraryInspectorTest,StorageHarnessSyntaxTest,ExportDispatchTest,ExportResponseTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

S4 closes the ordinary-mediation implementation phase within the documented type/provider bounds.
The full harness has not been run here. S5+ remains deferred until that manual acceptance gate passes.
