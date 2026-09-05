# Restoring persistent digital twins

This guide assesses reopening a twin after a runtime shutdown and records the incremental
restoration work begun on 2026-09-05. See [DIGITALTWINS](DIGITALTWINS.md),
[KNOWLEDGE_GRAPH](KNOWLEDGE_GRAPH.md), [STORAGE](STORAGE.md), and
[DISTRIBUTED_TWINS](DISTRIBUTED_TWINS.md). Reopening stored observations and autonomously
resuming a running simulation are different capabilities; the latter remains incomplete.

## Current restoration path

`RuntimeService.declareContextScope` accepts an existing context ID and constructs a new
`DigitalTwinImpl`. Neo4j contextualization finds an existing Context rather than recreating it.
The new twin constructs a storage manager and scheduler. The process-local caches, executable
closures, transactions, and messaging subscriptions from the previous process no longer exist.

The persistence flags have different meanings. `EXPLICIT_ACTION` is persistent until explicitly
deleted. `IDLE_TIMEOUT` and `REINITIALIZED_ON_TIMEOUT` also have `survivesShutdown=true`, despite
`persistent=false`; their inactivity policies still apply. `SERVICE_SHUTDOWN` and `ONE_OFF` do
not promise survival. Code implementing restart must test the appropriate flag, not equate all
non-EXPLICIT_ACTION contexts with disposable data.

The new `DigitalTwinImpl.shutdown()` releases scheduler subscriptions/timers and closes storage
without deleting graph nodes or durable files. Runtime shutdown uses this for locally hosted
twins outside the existing SERVICE_SHUTDOWN deletion branch. `dispose()` remains destructive.
This is not a general redesign of `ContextScope.close`, which still disposes the twin, nor a
barrier that drains all concurrently executing transactions. Quiescing work before shutdown
remains necessary for a fully crash-safe lifecycle.

## Implemented: transparent lazy storage reconstruction

`StorageManagerImpl.getStorage` now reconstructs positive-ID observations from their attributed
native strategy and graph shard descriptors. It no longer uses the existence of an arbitrary
`.dat` file in the context directory as evidence that the requested observation has data.
No descriptors means no existing storage; a descriptor with a missing/corrupt payload produces
an error when read, rather than being hidden by the directory check.

`StorageImpl` loads descriptors initially and restores each primitive buffer on its first scan.
Restored groups are sorted by shard index and validated for completeness, duplicate indexes,
declared count, and native type. Missing shard geometry fails explicitly: substituting the
observation's whole geometry can change a shard's size or partition boundaries. Read-only scans
require an existing slice and cannot allocate a fresh zero-filled replacement for missing data.
Writable execution can still allocate a new event slice.

Restoration uses the persisted native type and file-header validation. Current settings do not
reinterpret saved bytes. Histograms are rebuilt when buffers are restored; asking for aggregate
histograms may therefore load all shards. Reconstruction remains limited by KEYED dictionary
persistence, unsupported scanner remapping, generalized moving dimensions, and source data
versioning. Geometry coverage/overlap validation across all shards needs stronger tests.

## Implemented: additive actuator representation and bounded recompilation

Previously Neo4j stored textual calls, semantics, parent ID, and strategy, then adapted an
Actuator node to an empty `ActuatorImpl`. The additive version-1 representation now retains:

- identity, name, artifact type, actuator role, strategy, and child count;
- declared and resolved geometry, resolved coverage, and sharding strategy;
- structured service calls, including parameters omitted by source encoding;
- actuator data and annotations.

The original textual computation remains available for inspection. Structured JSON uses the
existing k.LAB serializer; it is a current-runtime representation, not a stable executable
format across arbitrary future code versions. The observation binding is obtained from the
`CONTEXTUALIZED_BY` relationship, not guessed by equating actuator and observation IDs.
Legacy/unknown schema versions do not fabricate executable service calls from their text.

On a cache miss for a persisted observation, the scheduler now follows its actuator link.
For exactly one actuator with complete structured calls, no child bindings, and universal or
full observation coverage, `CompiledDataflow.restoreLeafExecutor` recompiles a bound executor.
It does not run resolution, create observations, or reattribute native storage strategy. Current
component/resource availability and compilation checks still apply. The ordinary execution path
then uses the current execution scope and storage.

Multiple actuators, partial coverage, child/input bindings, and incomplete historical definitions
fail explicitly. New executable observations carry an execution-required marker, so missing
actuator links cannot silently become successful no-ops. Unmarked historical observations with
no implementation links cannot always be distinguished from acknowledged inputs.

This is intentionally not full `DataflowGraph` reconstruction. Its computation/adaptation methods
remain stubs. Replaying a whole twin, restoring a dependent actuator tree, and restoring arbitrary
component behavior require the revised model below.

## Implemented: a durable scheduler registry

Successful scheduler submission records `klab.scheduler.registered` in observation metadata and
marks the observation for update in the existing root transaction. Executor registration records
`klab.scheduler.executionRequired`. Geometry and event timestamps already persist with the
observation. These changes commit together with normal observation/provenance writes rather
than through a shutdown-only metadata write.

`KnowledgeGraph.getScheduledObservations` retrieves the committed registry through the owning
context's containment/membership paths. Neo4j failures are reported, not treated as an empty
registry. The scheduler restores subscriptions and finite time bounds from these observations
without calling their initialization executors or modifying saved timestamps. A separate unset
flag avoids confusing epoch zero with an uninitialized bound; open endpoints are not dereferenced.

This is the known observation-registration state, not a complete scheduler checkpoint.
`TimeEmitter` registrations/cursor are not currently wired into this graph state. Temporal
dispatch, aggregate resolution, event applicability, and durable event completion are still
incomplete. Reopened twins therefore do not automatically resume a clock. Existing databases
without registration markers are not automatically backfilled: “has an observation” does not
prove “was subscribed for temporal execution.”

## Actuator model decisions required for general reconstruction

| Area | Required representation and decision |
|---|---|
| Identity | Separate durable actuator-definition identity from output observation identity. Current API wording equates them while Neo4j allocates independent IDs. |
| Output binding | Explicit output observation reference, context/observer, plan version, and coverage. An observation may have several implementations. |
| Input binding | Ordered ports with aliases, referenced observation/actuator IDs, mediation, and dependency role. `HAS_CHILD` alone loses reference-site aliases and declaration roles. |
| Ordering | Distinguish declaration order from execution dependency rank. Compilation currently writes `rank`; the scheduler reads `sequence`. Migrate to one graph contract. |
| Execution kind | Initialization-only, transition, event, instantiation, and reusable computation; define which implementations may run again. |
| Coverage | Partition selection and conflict/precedence rules for multiple actuators, with explicit space/time coverage. |
| Requirements | Pin component/adapter/model/resource/worldview versions or checksums and define compatibility policy. Recompiling against the latest available plugin is not reproducible replay. |
| Parameters | Versioned typed values for expressions, lookup tables, classifications, local resources, geometry, annotations, and interactive inputs. Arbitrary Java JSON is not a permanent schema. |
| Side effects | Classify idempotent computations versus effectful actors/adapters; persist command/execution IDs and retry policy. |
| Evolution | Migrations, schema capabilities, legacy inspection, and explicit “not reconstructable” diagnostics. Never silently re-resolve an old plan. |

Introduce a versioned execution-definition DTO separate from the live actuator object. Persist
definitions and bindings through `GraphModel` and reconstruct an acyclic executable graph from
those records. Validate missing references, cycles, incompatible versions, and partial coverage
before registering any recovered executor. Memoize by definition/version and execution context;
do not key a dependency graph solely by an ID whose meaning changes during persistence.

## Complete scheduler checkpoint design still required

A checkpoint must include subscriptions and applicability, the schedule/calendar definition,
clock mode and logical time, last committed event per target, pending/failed work, dependency
versions, and an execution lease/generation. A single wall-clock timestamp cannot distinguish
an event that was emitted from one whose output committed.

Persist event completion and the observation/storage/provenance changes in the same transaction.
Write immutable/staged shard versions first, atomically publish their descriptors with event
completion, then acknowledge delivery or advance the emitter cursor. An emitted-but-uncommitted
event must be recoverable. External effects need idempotency keys or compensation; Neo4j rollback
cannot undo an HTTP request or already overwritten file.

Decide explicitly whether restart resumes automatically, remains paused, or catches up to now;
whether missed intervals are replayed individually or may be coalesced; how calendar periods
are represented; and how failures pause/retry/skip work. These are scientific execution semantics,
not implementation details. The current `TimeEmitter.Schedule` is useful for interval-generator
state but cannot establish transaction completion or these policies on its own.

## Remaining lifecycle gaps and implementation sequence

1. Quiesce submissions and drain/cancel work before releasing storage; implement explicit reopen,
   detach, suspend, and delete operations with persistence-aware authorization.
2. Restore the authoritative saved configuration, observer, behavior, and service requirements
   before advertising a reopened scope. Validate new options against saved options.
3. Repair orphan maintenance: it currently regards an unregistered non-`persistent` context as
   orphaned even if `survivesShutdown` is true. Apply timeout/reinitialization from persisted
   timestamps rather than deleting such twins merely because their scopes have not reopened.
4. Implement/migrate the execution-definition and binding model; reconstruct complete dataflows
   without invoking the Resolver. Fail before execution if closure cannot be reconstructed.
5. Add transactional event checkpoints and source-versioned storage; restore schedulers paused,
   validate state, then resume under the chosen policy and a single execution lease.
6. Add retained-version/orphan cleanup, driver ownership shutdown, durable messaging recovery,
   and upgrades across serialization/schema versions.

## Verification

Focused tests include `StorageReconstructionTest` (partition order/completeness/type),
`ActuatorPersistenceTest` (structured call/data preservation and legacy behavior),
`StorageManagerImplTest`, `KnowledgeGraphLinkDirectionTest`, `DigitalTwinCommitTest`, and
`TimeEmitterTest`. The normal filesystem sandbox produced unresolved-class build failures;
the same offline production compile succeeded outside that sandbox. Focused tests are run
there as well; consult the change report for the final result.

Required integration coverage still includes a real service restart with an unread quality,
multiple time slices, legacy/corrupt shards, persisted leaf re-execution, unavailable plugins,
failed initialization, transaction rollback, shutdown during execution, and a Neo4j registry
round trip. Unit tests of descriptors do not establish crash-safe execution or automatic resume.
