# The observation cycle

This document describes how an observation moves from the public `ContextScope` API through
submission, resolution, dataflow compilation, contextualization, storage, and knowledge-graph
commit. It also records the current implementation boundaries found by tracing that path through
the resolver and runtime.

The main entry points are:

- `ContextScope.observation(Observable)`, which creates an `Observation.Builder` at the knowledge
  graph insertion point represented by the scope;
- `Observation.Builder.submit()`, the normal asynchronous observation operation;
- `Observation.Builder.query().submit()`, a read-only lookup of existing knowledge;
- `RuntimeService.submit(Observation, ContextScope)`, the service-level driver of the complete
  cycle;
- `Observation.Builder.register()`, a low-level operation that assigns a provisional ID without
  starting resolution.

## Using the `ContextScope` API

A `ContextScope` is a handle to a digital twin and identifies where an observation belongs. Its
focus and observer are part of the request:

- `scope.within(context)` focuses the scope on a substantial context. Qualities and other
  dependents are inserted below this observation.
- `scope.withObserver(observer)` changes the observer used for the observation.
- `scope.between(source, target)` establishes the endpoints needed for a relationship.
- `scope.getContextObservation()`, `getObserver()`, `getSourceObservation()`, and
  `getTargetObservation()` expose that state.
- resolution constraints carried by the scope select scenarios, projects, namespaces, geometry,
  provenance agents, and other resolver inputs.

The usual client code is:

```java
CompletableFuture<Observation> future =
    scope
        .within(context)
        .observation(observable)
        .geometry(requestedGeometry)
        .submit();

Observation result = future.join();
if (result.isEmpty()) {
  result.getNotifications().forEach(System.err::println);
}
```

Dependent observations may omit `geometry(...)`; a quality inherits the geometry of its context.
Individual substantials require an identity through `identity(Urn)` or
`identity(namespace, name)`. Client scopes submit substantials at the digital-twin root even when
the builder was obtained from a focused scope. Collective substantials are identified by their
semantics and cohort rather than by one individual URN.

`register()` is not a substitute for `submit()`. It only validates the object and assigns a
scope-unique provisional ID so another component can manage resolution explicitly.

## Observation states

The ID and the empty flag describe different aspects of state:

| State | Representation | Meaning |
|---|---|---|
| Unassigned | `id == -1` | Built but not registered with a runtime. |
| Provisional | `id < -1` | Registered in the current scope/transaction but not committed to the knowledge graph. |
| Query | `id == 0` | Read-only query request or detached query-result view. It must never be persisted. |
| Resolved | `id > 0` | Existing knowledge-graph observation. Submitting it is a no-op. |
| Failed | `isEmpty() == true` | The operation failed or found no query result. Notifications explain why. |

An ID-0 query result is intentionally distinguishable from a persisted observation. A completely
covered quality query returns its original positive-ID observation; a partial quality query and all
collective query results remain ID 0. `getResolvedCoverage()` and the query metadata describe the
covered fraction.

The principal query metadata keys are:

- `Metadata.IM_QUERY_COVERAGE`: exact fraction of the requested geometry found;
- `Metadata.IM_QUERY_GEOMETRY`: encoded geometry originally requested;
- `Metadata.IM_QUERY_SOURCE_IDS`: observations that supplied the result;
- `Metadata.IM_QUERY_COHORT_ID`: cohort addressed by a collective query.

An observation also accumulates:

- notifications from validation, resolution, compilation, adapters, and contextualization;
- contextualization data describing the hosting service, adapter, and native sharding strategy;
- event timestamps recording successful contextualizations;
- timestamp-keyed histogram snapshots for quality storage.

## Semantic roles

`Observation.classifyRole()` maps observable semantics to the roles used when the compiled
dataflow is stored:

| Role | Typical knowledge-graph behavior |
|---|---|
| `DEPENDENT` | Linked below its context with `HAS_CHILD`; dependencies are linked with `AFFECTS`. |
| `COLLECTIVE_SUBSTANTIAL` | Represents one collective observation and contributes to a cohort through `CONTRIBUTED_TO`. |
| `INDIVIDUAL_SUBSTANTIAL` | Identified by URN and associated with a collective or root context. |
| `RELATIONAL` | Requires source and target; complete relationship persistence is still under development. |

A cohort is not the same asset as a collective observation. A cohort accumulates the results of
collective observations. Only collective observations linked by `CONTRIBUTED_TO` participate in a
collective query; individually inserted substantials do not imply that their surrounding geometry
was collectively observed.

## Submission and query preflight

`Observation.Builder.submit()` delegates to the scope's `RuntimeService`. The runtime first handles
terminal cases without opening a transaction:

1. A positive-ID or empty observation is returned unchanged.
2. An ID-0 observation is executed as a read-only query.
3. A normal quality or collective submission is preflighted with the same query mechanism.
4. If existing coverage satisfies the resolver's completion margin, the existing quality or
   collective query view is returned immediately.
5. Otherwise the normal observation cycle continues. Registration assigns a negative ID; identity
   lookup may still return an existing individual substantial.

Queries are supported only for qualities and collective substantials:

- A quality query requires `scope.getContextObservation()`. It finds the matching child quality and
  intersects its geometry with the request.
- A collective query finds the cohort and unions the geometries of matching collective observations
  linked through `CONTRIBUTED_TO`. The union preserves areas in which a collective observation found
  no instances.
- No source or zero geometric intersection produces an empty observation with informational
  notifications.
- A partial result reports its actual geometry and exact coverage without creating an activity,
  dataflow, scheduler registration, storage, or knowledge-graph asset.

## Transaction and activity structure

A non-terminal submission creates a transaction tree and three principal provenance activities:

1. `SUBMISSION` owns the overall operation and the root transaction.
2. `RESOLUTION` is a child activity used while obtaining and compiling the dataflow.
3. Each executed observation creates a `CONTEXTUALIZATION` activity in another child scope.

The submission transaction initially links the prospective observation at the scope insertion
point and records `CREATED`, `HAS_CONTEXT`, and `HAS_OBSERVER` provenance as applicable. Resolution
constraints are serialized into the resolution activity metadata, and the encoded dataflow is
stored there for inspection.

Child transaction commits merge their changes into the parent. The root submission commit is the
point where provisional runtime assets obtain persistent IDs and the knowledge graph is changed.
Failure in a child transaction is intended to fail the transaction tree.

## Resolution and coverage

`ResolverService.resolve()` delegates to `ResolutionCompiler`, which performs the following for
each quality or collective target:

1. Query the runtime for existing coverage in the requested scale.
2. Treat coverage below the configured empty margin as no coverage.
3. Treat coverage above the configured completion margin as satisfied by a reference.
4. For significant partial coverage, add the existing observation/query view as a reference and
   resolve models only against the representable missing scale.
5. If a geometric complement cannot be represented by one `Scale`, retain the reference but resolve
   the full requested scale rather than risk under-resolution.

For partial qualities, the reference should be the positive-ID source observation. Collective
references remain ID-0 query views because they summarize multiple contributing observations and
their union geometry.

Resolution produces a `ResolutionGraph`, then `DataflowCompiler` serializes it into actuators:

- `OBSERVE` actuators contain computations for observations that must be contextualized;
- `REFERENCE` actuators name already available inputs and contain no computation;
- `RESOLVE` exists in the API for stored dataflows but is not the normal output of the current
  compiler.

An actuator carries both its observation's native geometry and a coverage geometry describing the
portion assigned to that actuator. Reference actuators are attached as children so contextualizers
can bind them by local name.

## Runtime dataflow compilation

`RuntimeService.compile()` creates one `CompiledDataflow` for every root actuator. Compilation:

1. Resolves or creates actuator observations.
2. Harmonizes quality sharding strategies between the runtime, model, adapter, and dependencies.
3. Builds an actuator dependency graph and topological execution ranks. Reference actuators are not
   executed.
4. Compiles service calls into local/remote adapter executors, scalar executors, or general
   contextualizer executors.
5. Stores observations, actuators, dependency relationships, provenance, and executor registrations
   in the resolution transaction.

Actuator-to-observation association is identity-based. This matters when a dataflow contains more
than one actuator with the same observable, or an ID-0 collective query view. Reference actuators
retain their exact detached observation for local-name binding, while only persistable dependent
observations enter the transaction. Query views are therefore usable as runtime references without
becoming knowledge-graph assets; in particular, they do not create `AFFECTS` relationships whose
endpoints would implicitly persist them. Positive-ID references retain `AFFECTS` for later event
propagation. Actuator coverage is recorded on `CONTEXTUALIZED_BY` for provenance, although applying
it to storage execution still depends on the partial-quality policy described below.

The principal stored relationships are:

- `CONTEXTUALIZED_BY`: observation to actuator;
- `AFFECTS`: dependency observation to the observation that consumes it;
- `HAS_PLAN`: resolution activity to the root actuator;
- `HAS_CHILD`: dataflow/actuator hierarchy and semantic containment;
- `CONTRIBUTED_TO`: collective observation to cohort;
- `RESOLVED`: resolution activity to its result.

After the resolution child transaction commits, its executors are registered with the scheduler.

## Scheduling and contextualization

`ServiceContextScope.contextualize(observation)` submits the root observation to the digital-twin
scheduler. The scheduler registers the observation's temporal extent, subscribes it to later
events, and runs initial contextualization synchronously. It returns whether initialization
succeeded. The runtime awaits that result before committing the submission: false or exceptional
initialization fails the transaction and completes submission with an empty observation carrying
the available notifications.

For each event, the scheduler:

1. Traverses incoming `AFFECTS` relationships and contextualizes dependencies first, grouped by
   execution rank.
2. Runs independent dependencies concurrently using virtual threads.
3. Invokes the registered executor for the observation.
4. Stops the branch if execution returns false or the observation contains error notifications.

An `ExecutorImpl` creates a `CONTEXTUALIZATION` activity and runs its compiled contextual executors
in sequence. Quality executors create storage, then `AbstractExecutor` creates one output scanner
per shard plus conformant read scanners for quality dependencies. Shard tasks may run concurrently.

After successful quality execution, the scheduler:

- finalizes each scanner run;
- flushes shard data before committing shard descriptors;
- copies timestamp-keyed histogram snapshots into the observation;
- adds or updates `HAS_DATA` shard relationships;
- records the event timestamp and updates the observation.

Instantiation contextualizers may emit child observations, which are submitted recursively. The
parent contextualization composes and awaits all child submission futures; an empty or exceptional
required child fails the parent instead of allowing it to commit early. Classification and
connection contextualization require additional post-processing beyond scalar data production.

Finally, the root submission transaction commits, the commit ID is placed in
`Metadata.IM_COMMIT_ID`, and the submission future completes. The coverage calculated by the
resolver is copied into `Observation.getResolvedCoverage()` before execution.

## Remaining decisions and unsupported cases

The query, resolution, reference-binding, initial-failure, and child-completion paths now form a
coherent cycle. The following gaps cannot be closed safely without choosing domain semantics or
extending the storage and event architecture.

### Partial quality storage and execution geometry

`DataflowCompiler` records missing geometry in `Actuator.getCoverage()`, and runtime compilation
records it on `CONTEXTUALIZED_BY`. Execution cannot yet apply that geometry correctly:
`SchedulerImpl` invokes dependencies with the root event geometry, while the storage scan API has
no execution-geometry parameter and native scanners are constructed for an observation's storage
geometry. Geometry remapping is also explicitly unsupported in the current storage implementation.

This cannot be solved by merely passing a smaller geometry to an executor. The existing quality is
available as a reference input, while the submitted root normally becomes a new provisional
observation with its own storage. Nothing defines how existing values and newly computed values
become one returned quality. The system must choose and implement one policy end to end:

- extend the existing quality observation and storage in place; or
- materialize a new composite observation whose storage/view delegates covered sectors to the
  reference and missing sectors to new shards.

That decision determines observation identity, shard ownership, scanner delegation, histogram
aggregation, commit behavior, and the geometry passed through the scheduler. Until it is made,
partial resolution plans are compiled correctly and preserve their references, but partial-quality
value composition is not fully executable.

### Semantic post-processing and event scheduling

- classification and connection contextualization currently throw `KlabUnimplementedException`;
- relationship source/target persistence and relationship-specific post-processing contain
  explicit TODOs;
- scheduler `checkApplies()` currently accepts every event, while `handleEvent()` implements only
  a limited set of event paths;
- scheduler state is not rebuilt from past knowledge-graph events at startup;
- failure cleanup for non-transactional storage is still a TODO.

The desired temporal trigger semantics, restart replay policy, and domain behavior for
classification, connection, and relationships are not specified by the observation-query
contract. These limitations affect what "submission complete" means for classifications,
relationships, reactive observations, and restart behavior.

## Completion contract

The intended contract for callers is:

- positive-ID input: return it unchanged;
- ID-0 query: return an existing positive quality, an ID-0 query view, or an empty result;
- normal successful submission: return a positive-ID contextualized observation after the root
  transaction commits;
- any resolution, compilation, initial execution, or required child-submission failure: return an
  empty observation carrying explanatory notifications and fail the transaction tree.

The positive-ID, query, zero-coverage, and full-coverage branches implement this contract. Partial
quality resolution reaches the correct plan and preserves exact references, but complete value
composition awaits the storage policy above. Advanced semantic and reactive contextualization
retains the listed limitations.

## Code map

- Public API: `klab.core.api/.../scope/ContextScope.java`,
  `klab.core.api/.../knowledge/observation/Observation.java`, and
  `klab.core.api/.../services/RuntimeService.java`.
- Scope builders: `klab.core.services/.../ServiceContextScope.java` and
  `klab.core.common/.../ClientContextScope.java`.
- Submission/query driver: `klab.services.runtime/.../RuntimeService.java`.
- Resolution: `klab.services.resolver/.../ResolutionCompiler.java`, `ResolutionGraph.java`, and
  `DataflowCompiler.java`.
- Runtime compilation/execution: `klab.services.runtime/.../CompiledDataflow.java` and executor
  implementations in the same package.
- Scheduling: `klab.services.runtime/.../digitaltwin/scheduler/SchedulerImpl.java`.
- Transaction implementation: `klab.services.runtime/.../digitaltwin/DigitalTwinImpl.java`.
- Storage: `klab.core.services/.../runtime/storage/StorageManagerImpl.java` and `StorageImpl.java`.
- Persistence: `klab.services.runtime/.../neo4j/KnowledgeGraphNeo4j.java`.
