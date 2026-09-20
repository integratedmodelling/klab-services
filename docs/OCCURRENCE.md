# Occurrences: achieved behavior and next steps

Status: 2026-09-20. S0–S5, including S3.1/S3.2, and the implementable S7a client path are achieved for bounded simulated processes. The user confirmed that the live process fixture resolves, dispatches its transitions, and displays correct results in all IDE views. Event execution and partial spatial coverage remain the next phase, pending the scanner construction and mediation strategy.

This document replaces the chronological implementation plan with the accepted contracts, achievements, and remaining gates. Earlier implementation detail is available in repository history.

## Achieved through S5 and S7a

| Stage | Delivered |
| --- | --- |
| S1–S2 | Portable schedules and execution roles; validated actuator persistence; registration after commit; separation of occurrence registration from quality INIT |
| S3 | Process dependencies resolve on the inherent substantial/event; named inputs and outputs retain their bearer; `creates` is deferred until temporal execution |
| S3.1 | Dependency-local schedule requests, provenance, whole-schedule replacement, inclusive acceptance ranges, model/Java locks, candidate rejection and schedule-aware reuse |
| S3.2 | Correct direct effects versus descriptive quality links, typed portable evidence and graph edges, semantic consequence closure; ontology synchronization including authoritative odo-im 0.1.0 |
| S4 | Bounded native-calendar dispatch, per-registration filtering, fresh transactions, durable progress, historical catch-up, retry and restart support |
| S5 | Executable inline scalar process assignments, localized quality geometry, lazy transactional writes, atomic temporal Data/history and geometry deltas, effective-change propagation and durable no-op receipts |
| S7a | Durable committed transition envelopes, client recovery/deduplication, graph/data refresh, Timeline and quality visualization; successful live process acceptance |

The live fixture uses a Region, an initialized Elevation quality, and an Erosion process assigning `elevation - 10` each month. Registration commit starts the twelve monthly transitions across the year. Explicit process cadence is independent of the context geometry's step.

### Execution and semantic contracts

- Processes resolve through SIMULATION. Event instantiators register scheduled plans; individual events are observations with their own lifecycle. All relationships are substantials: functional relationships may host processes, structural relationships may not.
- Ordinary injected quality dependencies belong to the process's inherent substantial/event and complete INIT before the process reads them. Their explanatory models need not be listed by the substantial model. Process registration never executes its temporal computation under INIT.
- A quality connected by `creates` is an epiphenomenon, still borne by the affected substantial/event. Its observation and data are materialized only by a successful temporal transition, without a fictitious INIT state. Unavailable prior-state reads are errors.
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

Verification for the geometry-default addition: 37 selected Java tests passed across schedule/negotiation serialization, resolver candidate selection and reuse, registration, simulated dispatch and temporal scalar storage. The 17 schedule/resolver tests also passed after adding event-instantiator and native-calendar assertions. No live services were restarted for this addition.

```text
mvn -pl klab.services.resolver,klab.services.runtime -am test -Dtest=OccurrenceScheduleTest,OccurrenceNegotiationTest,ScheduleNegotiationResolutionTest,TemporalProcessIntegrationTest,OccurrenceRegistrationTest,SimulatedDispatchTest -Dsurefire.failIfNoSpecifiedTests=false
```

### Scanner construction and mediation prerequisite

The user will define fully and partially conformant scanner construction, binding and mediation. Implement that strategy before claiming support for event effects with partial spatial coverage. Preserve named/annotated affected-property bindings, time-localized geometry, split/fill-curve negotiation, lazy writes and effective-change detection. Validate untouched cells and historical shards, not just contextualizer invocation counts. Advanced Java contextualizers and distributed backends require their own integration gates.

### S6 — Event production and feedback

Execute scheduled event instantiators, accepting zero or more individuals. Validate each individual's temporal support against the logical instantiation time: no start in the past; future extent is legitimate. Resolve each individual normally and enqueue observed-event consequences only after successful commit. Preserve identity across retry, replay and equal-time events.

Before enabling this path, settle batch atomicity, partially failed batches, future-start activation, recursive event production and point-event support. Test empty success, invalid past starts, future durations, partial spatial effects, no-op versus effective effects, rollback after allocation, restart and idempotent observed-event feedback. Scanner mediation must prove that only covered cells change.

Continuation prompt: “Implement S6 using the agreed scanner construction and mediation strategy. Execute scheduled event instantiators, validate and resolve emitted individuals, and enqueue committed observed events idempotently. Close batch/future-event policies and test partial spatial effects through actual storage contents, rollback, retry and restart.”

### S7b — Events and behavior-driven mutations

Complete event-production transport, all-observed-event visibility and causal navigation after S6. k.Actors behaviors bound to process/event models must join the same digital-twin transition. Plan explicit versioned mutation records rather than overloading quality deltas or generic graph added/deleted sets.

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
