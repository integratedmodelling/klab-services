# Occurrences: achieved behavior and next steps

Status: 2026-09-24. S0–S5, including S3.1/S3.2, and the implementable S7a client path are achieved for bounded simulated processes. The user confirmed that the live process fixture resolves, dispatches its transitions, and displays correct results in all IDE views. The event lifecycle implementation below adds scheduled instantiation, individual boundaries, partial spatial effects and client duration history; live event/IDE acceptance remains a separate gate.

This document replaces the chronological implementation plan with the accepted contracts, achievements, and remaining gates. Earlier implementation detail is available in repository history.

## Achieved through S5 and S7a

| Stage | Delivered |
| --- | --- |
| S1–S2 | Portable schedules and execution roles; validated actuator persistence; registration after commit; separation of occurrence registration from quality INIT |
| S3 | Quality inherency selects the occurrence or its context as bearer; named inputs and outputs retain that selection; `creates` is deferred until temporal execution |
| S3.1 | Dependency-local schedule requests, provenance, whole-schedule replacement, inclusive acceptance ranges, model/Java locks, candidate rejection and schedule-aware reuse |
| S3.2 | Correct direct effects versus descriptive quality links, typed portable evidence and graph edges, semantic consequence closure; ontology synchronization including authoritative odo-im 0.1.0 |
| S4 | Bounded native-calendar dispatch, per-registration filtering, fresh transactions, durable progress, historical catch-up, retry and restart support |
| S5 | Executable inline scalar process assignments, localized quality geometry, lazy transactional writes, atomic temporal Data/history and geometry deltas, effective-change propagation and durable no-op receipts |
| S7a | Durable committed transition envelopes, client recovery/deduplication, graph/data refresh, Timeline and quality visualization; successful live process acceptance |

The live fixture uses a Region, an initialized Elevation quality, and an Erosion process assigning `elevation - 10` each month. Registration commit starts the twelve monthly transitions across the year. Explicit process cadence is independent of the context geometry's step.

### Execution and semantic contracts

- Processes resolve through SIMULATION. Event instantiators register scheduled plans; individual events are observations with their own lifecycle. All relationships are substantials: functional relationships may host processes, structural relationships may not.
- Quality dependencies of processes and individual events select their bearer from semantics. If a quality inheres to the occurrence (including an inherited compatible type), resolve and attribute it there. Otherwise it must inhere to the context observation and the occurrence must `affect` or `create` it. Inherency to the occurrence takes precedence. Missing or incompatible inherency is rejected; an inline assignment does not establish a semantic effect on the context.
- Ordinary quality dependencies complete INIT before the process reads them, under their selected bearer. Their explanatory models need not be listed by the context model. Process registration never executes its temporal computation under INIT.
- A quality connected by `creates` is an epiphenomenon borne by its semantically selected occurrence or context. Its observation and data are materialized only by a successful temporal transition, without a fictitious INIT state. Unavailable prior-state reads are errors.
- Inline `set x to [expression]` accepts a named quality output or `observing` dependency. `set to [expression]` is illegal for a process. Assignments compile against named bindings and run over the quality's localized geometry. Reads use prior causal state; multiple output assignments do not acquire an accidental order through writes.
- Direct `affects` links an occurrent to changed qualities. `marks`, `increases with`, `decreases with`, `discretizes`, and `classifies` specialize `describes` and link qualities. Preserve property class, declared direction and provenance in typed quality-to-quality AFFECTS evidence. Reachability from either affected endpoint does not imply execution order, INIT, or creation.
- `marks` concerns boolean qualities; `discretizes` maps a quantifiable quality to ordering classes under the explaining model's one-to-one mapping; `classifies` defines predicate classification without uniqueness or coverage guarantees.
- Actual recomputation follows resolved model dependency paths. Descriptive reachability alone does not manufacture a computation. Process input bindings remain in actuator HAS_CHILD links and portable plans; they no longer produce reverse quality-to-process prerequisite AFFECTS edges. Existing graph edges are not retroactively migrated.

### Scheduling, including geometry defaults

Precedence is dependency observable `@time`, then model `@time`, then Java `@Contextualizer`, then geometry fallback. A higher-precedence request replaces the entire schedule, rather than merging individual fields. Every downstream declared lock and inclusive `minStep`/`maxStep` range still applies; incompatible candidates are rejected so resolution can try alternatives. Model and portable Java metadata use the same native time units and constraints.

Dependency annotations are lexical to that dependency edge and retain requester/model/observable provenance. They do not leak into siblings or unrelated dependency resolution. A dependency schedule is sufficient even when the selected implementation declares no schedule of its own.

When all explicit sources are absent:

- A process inherits cadence from the original context observation geometry, before coverage collapse or temporal localization.
- An event instantiator inherits cadence from the observer's PERCEIVES geometry, not its occupied geometry.
- The source must provide a bounded scheduled temporal extent. A missing time dimension, bare interval without cadence, unavailable perceived geometry, invalid step, or real-time extent is rejected; no arbitrary cadence is invented.
- Preserve the original step multiplier, native calendar unit, phase and bounds. Months and years are never converted to indicative fixed durations. Geometry decoding preserves multipliers such as `2.month`.
- Geometry is a fallback, not a constraint on explicit schedules. An explicit monthly schedule can run over a daily context. Existing downstream compatibility validation still runs during candidate resolution.
- Persist the accepted fallback with CONTEXT_GEOMETRY or OBSERVER_GEOMETRY provenance and the source observation identity. Negotiation version 2 supports these defaults and dependency-only requests; existing version-1 explicit negotiation remains readable.
- Cached/reference reuse checks geometry-derived cadence against the current source and rejects implicit rescheduling. A subsequent geometry change does not silently alter the registered process.

A successful root registration commit activates all its occurrences before requesting advancement to the latest accepted bounded end. Each transition uses a fresh root transaction. Reentrant activation queues another drain instead of recursively executing an unfinished tick. Rollback never activates a registration. A failed transition stops the drain and reports an error while preserving the committed registration and earlier states.

Scheduler construction restores registrations without running models. An explicit advancement or later registration commit resumes unfinished work from durable receipts. Accepted bounds retain automatic-run intent even when the first transition fails. Native calendar recurrence and per-registration cursors support newly introduced historical cadences; equal-time occurrences retain distinct identities. Real-time execution remains explicitly unsupported.

### Storage, no-ops and delivery

Temporal writes use a lazy transaction-owned write set. Merely obtaining a scanner or returning immediately does not allocate a new durable state. Effective value/support changes determine dirty outputs; successful unchanged execution is a no-op, not failure. No-op completion is durable for retry suppression but creates no new Data state, geometry extension, causal recomputation or client timeline clutter.

Changed buffers, Data links, temporal support deltas, provenance and completion commit atomically. Rollback discards provisional output; historical data remains accessible. Downstream computations run only when effective changes reach their resolved inputs. This contract is intended to accommodate later distributed storage/computation implementations without relying on local invocation counts.

TransitionCommit version 1 persists on the root Activity with the graph commit summary, scheduler journals and typed quality-state deltas. ActivityFinished carries committed consequences; history recovery survives messaging outages and client reconnects. Client scopes deduplicate/reconcile delivery and invalidate affected graph/data views before notifying viewers. History polling handles unavailable graphs with bounded backoff. Synthetic ticks without consequences stay out of the Timeline; all successfully committed observed events must ultimately be shown.

The scalar path has automated storage-content, Data-link, rollback, retry, restart, calendar-cadence, no-op and transport coverage, and user-confirmed live Runtime/Resolver/IDE acceptance. The new geometry fallback is covered separately by Java negotiation/transport/reuse tests; its parsed k.IM and live observer-event acceptance remain to be exercised. Equal-time timeline entries are distinct, but selecting a particular quality revision sharing an endpoint still needs an event/revision-aware export contract.

## Next steps

### Semantic bearer revision (2026-09-24)

The shared `of` argument rules accept processes as quality bearers without making processes
countable. Inherited inherency and `applies to` bounds still constrain specialization. A process
continues to require its own substantial/event host; allowing a quality to belong to a process
does not make processes hosts for other ordinary processes.

ProcessPlan version 3 records CONTEXT or OCCURRENT on each quality binding. Resolution uses that
scope for queries and initialization; graph storage and deferred creation use the same bearer.
Versions 1 and 2 retain their original context-bearer interpretation on replay. Existing observations
are not reparented; re-resolution is needed to adopt revised semantics.

ModelKbox's existing `inferModels` extension point now adds `change in X` descriptors for quality
outputs and dependencies that a process semantically affects or creates. Occurrence qualities retain
their declared inherency during indexing. Resolver semantic matching recognizes the same inferred
outputs. Resources' existing knowledge-indexing pass removes and reindexes each namespace;
run that pass with the updated service to populate existing catalogs. This makes a process a
candidate for explicit change requests; it does not implement joint-effect selection.

The `change in` operator is valid for every quality, irrespective of its bearer or whether a model
currently affects it. The affects/creates requirement controls which process models advertise that
change, not whether the change concept can be constructed. In ODO, `changes` has a union domain
of Process and Event; separate domains would require both disjoint types and make every change
unsatisfiable. The generated `odo:Change` is a Process and retains the quality's bearer.

Change inference also exercises inherited OWL restrictions, including imported vocabularies such
as PROV. Clause matching must compare OWL properties and their superproperties without requiring
unrelated properties to have a registered k.LAB namespace. A failed knowledge-indexing pass must
leave semantic search unavailable and return a discovery error, rather than query a partial catalog
and report that an ordinary explanatory model is absent. A later successful indexing pass restores
search availability.

**Pending collinearity rule:** when `change in X` is requested in a context providing X and Y,
prefer a process that changes both over two independent processes changing X and Y. Define the
coverage and priority policy before implementing it; independent per-observable ranking is not
sufficient to make that choice.

Verification: 52 targeted tests passed for observable validation, parsed ontology declarations,
bearer selection and scope routing, plan transport, ModelKbox inference and resolver matching,
schedule negotiation, registration and temporal storage/restart. No live services were restarted.

```text
mvn -pl klab.services.resources,klab.services.runtime -am test -Dtest=ObservableValidatorTest,ProcessModelBindingsTest,WorldviewValidationTest,OccurrenceModelIndexTest,TemporalProcessIntegrationTest,ScheduleNegotiationResolutionTest,OccurrenceRegistrationTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Earlier geometry-default verification

Verification for the geometry-default addition: 37 selected Java tests passed across schedule/negotiation serialization, resolver candidate selection and reuse, registration, simulated dispatch and temporal scalar storage. The 17 schedule/resolver tests also passed after adding event-instantiator and native-calendar assertions. No live services were restarted for this addition.

```text
mvn -pl klab.services.resolver,klab.services.runtime -am test -Dtest=OccurrenceScheduleTest,OccurrenceNegotiationTest,ScheduleNegotiationResolutionTest,TemporalProcessIntegrationTest,OccurrenceRegistrationTest,SimulatedDispatchTest -Dsurefire.failIfNoSpecifiedTests=false
```

### S6 — Event production and feedback

Collective event instantiators execute at the start of each accepted schedule interval, never at
INIT, and may return zero or more individuals. The complete batch is validated before child
resolution begins. Each child resolves through the ordinary submission path within the producing
root transaction; a failed required child prevents that transaction from committing. Individual
plans and dependencies are prepared immediately, while their event computations wait for boundaries.

An individual has one atomic, bounded, positive-duration period, without temporal subdivisions.
Its start must not precede the logical instantiation time. Future starts are allowed; the end may
outlive the producing interval and the instantiator's entire schedule. A one-cell spatial grid is
valid, including geometry expressed using only a bounding box. A physical event never has zero
duration; its two scheduler boundary notifications are instantaneous state transitions.

Individual plans use the portable EVENT execution role. The scheduler merges START and END with
regular schedules, rebuilding the due queue after each commit so newly produced events can enter
before the next tick. A boundary has stable identity `observed:<id>:START|END`; separate durable
completion flags survive retry and restart. Pending events are registered only after successful
commit. An end beyond the current simulation horizon remains pending until the clock advances.

Java contextualizers receive the executing boundary through any `TimeInstant` parameter, and can
inspect `Scheduler.Event.getBoundary()`. Unqualified inline assignments execute at both boundaries.
Portable assignment calls reserve `_eventBoundary=START|END` for future `at start/end set` syntax;
the qualifier is valid only on individual event plans. The source-language syntax is not yet added.

Quality bearer selection remains semantic. Event scanners traverse the event's spatial geometry,
including for context-borne qualities. Inputs open as prior-state snapshots; only qualities
semantically affected by the event receive writable Java scanners. Scalar affected assignments use
the same event view. Closing a writable view scatters explicitly written cells back to native
storage by nearest cell center: cells outside the event remain unchanged. The existing mediation
setting, CRS, rectangular-grid and value-conversion restrictions still apply. Deferred scalar
`creates` outputs use their selected bearer and do not receive a fictitious INIT revision.

Client lookups and graph queries hide pending events. A successful START publishes a version-2
SchedulerJournal carrying `ObservedEvent` (identity, semantic type, name, full geometry, start and
end), inside the existing TransitionCommit envelope. `TransitionHistory.eventsByType()` supplies
one immutable duration record per started event, grouped by canonical semantic type for IDE
lanes. END records do not create duplicate bars, and reordered/replayed delivery converges.

Acceptance must include real instantiator contextualizers and IDE rendering; the client history
API is available here, but this repository does not establish live UI acceptance. Distributed
writeback, real-time driving and behavior-driven recursive production remain separate gates.

Verification: 88 selected tests passed across client/common, core services, resolver and runtime.
Coverage includes single-cell bbox/shape promotion and round trips, atomic-period validation,
whole-batch rejection before child submission, required child success, Java boundary arguments,
read-only versus affected scanners, partial writes with untouched native cells, downstream
recomputation at START and END beyond the original horizon, rollback/retry/restart, and replayed
duration history. Existing process creation and no-op behavior remain covered. The final Maven
run used an isolated source copy under `target/event-verification-workspace` to avoid concurrent
IDE compiler output replacement; its `event-verification.log` records BUILD SUCCESS. No live
services were restarted.

```text
mvn -o -pl klab.services.runtime,klab.services.resolver -am test -Dtest=GeometryRepositoryTest,EventSupportTest,OccurrenceCompilationTest,OccurrenceExecutorTest,OccurrenceRegistrationTest,SimulatedDispatchTest,TemporalStorageTest,TemporalProcessIntegrationTest,AbstractExecutorScannerBindingTest,TransitionTransportTest,ProcessModelBindingsTest,ClientKnowledgeGraphTest,ComputationalClosureTest,InstantiationNamespaceTest -Dsurefire.failIfNoSpecifiedTests=false
```

### S7b — Events and behavior-driven mutations

Use the committed event duration history above for IDE event lanes and complete live acceptance and causal navigation. k.Actors behaviors bound to process/event models must join the same digital-twin transition. Plan explicit versioned mutation records rather than overloading quality deltas or generic graph added/deleted sets.

| Mutation | Required committed client behavior |
| --- | --- |
| Occupied geometry change | Move the same substantial identity, refresh spatial views, preserve geometry history |
| Substantial instantiation | Add the initialized observation and its links under the correct bearer/context |
| Configuration instantiation | Display participants and relationships as one atomic batch |
| End of existence | Remove from the active-time projection and cancel future participation while preserving historical inspection |

Keep occupied geometry, perceived geometry, existence and computed support distinct. Mutations need stable identities, causal/action provenance, temporal support and base/result revisions. Retirement is a temporal tombstone, not deletion. Settle authorization, competing mutations, spatial conflict rules, cascading retirement and behavior transaction entry before implementation. Test reordered create/move/retire messages, reconnect, rollback and atomic quality recomputation alongside mutations. Negotiate envelope compatibility explicitly.

Continuation prompt: “Complete S7 event delivery after S6, then integrate agreed k.Actors mutation contracts in a new versioned envelope. Apply committed batches to receiving scopes and IDE views, preserving history and replay safety.”

### S8 — Real time and operational release

Real-time behavior remains rejected. Define clock switching, outage/catch-up, concurrency and operational stop/resume policy before enabling it. Gate release on migration/restart, controlled-clock tests, backend/distributed-storage acceptance, load tests and live client compatibility. Upgrade API/common, resolver, runtime and IDE as a compatible stack; do not mix legacy semantic plans with corrected ontology/runtime behavior.
