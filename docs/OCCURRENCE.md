# Occurrence: processes, events, and temporal contextualization

Status: **development plan and target contract**, established 2026-09-19. CONNECTION and
CLASSIFICATION are accepted foundations; occurrence execution is not yet implemented end to end.
This document stages development, testing, and deployment. Requirements below are normative for
the feature; proposed representations and explicitly open decisions are not claims about current
behavior. The initial delivery is documentation scaffolding, with no runtime behavior change.

Read with [observation strategies](OBSERVATION.md), [resolution](RESOLUTION.md),
[observable semantics](OBSERVABLES.md), [ontology declarations](ONTOLOGY_LANGUAGE.md),
[knowledge-graph persistence](KNOWLEDGE_GRAPH.md), [connection](CONNECTION.md), and
[classification](CLASSIFICATION.md). Preserve their accepted static-observation behavior.

## 1. Scope and semantic contract

Processes resolve as dependents through SIMULATION. Events are instantiated as substantial
observations. Their temporal execution, rather than model discovery alone, is the major addition.
All relationships are substantials at runtime. Functional relationships can host processes;
structural relationships cannot. The user-level classification of functional relationships as
occurrents remains useful, but must not make their CONNECTION actuators temporal process actuators.

Distinguish four things throughout APIs, persistence, and tests:

| Entity | Resolution and registration | Execution |
|---|---|---|
| Process observation | Resolve a SIMULATION model and its dependencies; persist the validated plan and schedule | Run at matching temporal transitions, never through INIT |
| Event instantiator | Resolve the collective/event-producing plan; persist it and its schedule | Run at matching temporal transitions; emit zero or more individual events, never through INIT |
| Individual observed event | Instantiate, validate temporal geometry, and resolve the individual normally | After successful resolution and commit, enqueue its observed-event consequences |
| Relationship observation | Instantiate and acknowledge through CONNECTION | Retain substantial initialization; a hosted process has its own schedule |

An event instantiator is not its output event. Suppressing INIT for instantiators must not suppress
ordinary acknowledgement/resolution of emitted individuals or initialization of their qualities.
Likewise, persisting a scheduled plan is not evidence that it has produced observations or data.
Registration, successful execution over a temporal support, and output coverage are separate facts.

Process models cannot attach qualities to the process. Their quality dependencies belong to the
inherent substantial or event. Preserve dependency names, observable restrictions, optionality,
lexical constraints, geometry, and bindings while resolving in that bearer's scope. A functional
relationship can be that bearer; neither endpoint is implicitly substituted for the relationship.
Explicitly different dependency inherence must be honored or diagnosed, never silently rebound.

### 1.1 Inline scalar expressions for process outputs

k.IM process models must support inline scalar expressions for inherent variables, without requiring
a Java process contextualizer. The primary model observable is the process; additional observables
listed after it declare the model's outputs. A named quality output belongs to the process's inherent
bearer, not to the process itself.

| Process-model assignment | Required behavior |
|---|---|
| `set to []` | Illegal: the implicit target is the primary process, which cannot be assigned a scalar value |
| `set x to []` | Accepted when `x` identifies a known output observable quality listed after the primary process observable |
| Named assignment to the process, a non-quality, an unknown name, or an input-only dependency | Reject with a target-specific diagnostic |

Here `[]` denotes an inline scalar-expression body, not a prescribed empty expression. Resolve `x`
against the model's declared output names, retain its observable semantics and bearer binding, and
validate the expression's inputs and result against the target quality's data contract. Ambiguous
output names must be diagnosed. Enforce these rules at runtime validation and expose the same
diagnostics through the future k.IM semantic/contextualization-chain validator.

Compile the expression into the resolved executable plan and evaluate it over the localized geometry
of the target quality at the current scheduled time. A scalar expression is evaluated at the relevant
quality locations; it does not assign a scalar to the process or necessarily to the whole quality
extent. Bind inherent input variables at those locations using the required mediation and causal
state versions. Preserve the target's spatial and other support while locating its temporal extent
to the current transaction. Output bindings and expression definitions must survive actuator
persistence, transport, and executor restoration.

Inline assignments obey the same schedule, INIT exclusion, influence, transaction, new Data/storage,
replay and client-update contracts as other process computations. With no Java schedule declaration,
the model must supply `@time`. Integrate the expression into the output quality's execution path so
one scheduled computation produces one committed state, without also scheduling a duplicate output
computation. Multi-output read/write ordering follows the causal-state policy in Section 5; textual
assignment order must not accidentally expose partially written current-time state.

This is a required early execution path for simple processes and deterministic test cases, to be
enabled before sophisticated Java process contextualizers. Those contextualizers will match the
relevant scanners by name or annotation after split and fill-curve negotiation. Inline execution
must preserve the equivalent quality-location and data-layout contract using the scalar compilation
machinery, without depending on completion of that later Java scanner-matching feature.

## 2. Repository baseline and gaps

The following findings come from source inspection, not an end-to-end temporal test run.
Paths below are relative to the repository root.

| Area and implementation seam | Existing foundation | Missing work |
|---|---|---|
| `klab.core.api/.../digitaltwin/Scheduler.java` | INIT, temporal-transition and observed-event kinds; executor and registration contracts | Explicit execution policy, normalized schedules, observed-event submission, durable replay/completion contract |
| `klab.services.runtime/.../digitaltwin/scheduler/SchedulerImpl.java` | Executor cache, registration metadata, persisted-leaf restoration, replaying Reactor sink | `submit` still initializes processes and event instantiators; process/event branches are TODOs; `checkApplies` always accepts; `handleEvent` does no temporal work |
| `klab.services.runtime/.../digitaltwin/scheduler/TimeEmitter.java` | Bounded/unbounded cadence registrations, simulated/real-time clocks, serializable snapshot | `emitEvent` is empty and the scheduler creates an unconnected emitter; `notifyTime` does not register executable schedules; snapshots are not a durable execution journal |
| `klab.core.api/.../services/runtime/extension/KlabFunction.java` and `klab.core.services/.../components/ComponentRegistry.java` | Java declaration and portable function-specification extraction | Temporal limits, cadence, override policy, discovery/transport/cache round trips |
| `klab.core.api/.../knowledge/observation/scale/time/Schedule.java` | Client-facing start/end/finest-resolution summary | Not an executable per-actuator schedule; distinct from `TimeEmitter.Schedule` |
| `klab.services.resolver/.../ResolutionCompiler.java` | Dependent resolution; model dependencies inherit the selected scope; substantial models enter their explained observation | Explicit process-bearer binding and rejection of quality ownership by processes |
| `klab.services.runtime/.../CompiledDataflow.java` | Persists actuators and links prerequisite observations using `AFFECTS` | Temporal policy transport, transactional registration, semantic influence binding distinct from prerequisite traversal |
| `klab.services.reasoner/.../ReasonerService.java` | `affected`, `created`, `affectedOrCreated`; `occurrent` recognizes processes/events | Verified closure over connected semantic declarations, mapping semantic targets to concrete observations and executable creation plans |
| `klab.services.runtime/.../digitaltwin/DigitalTwinImpl.java` | Root transaction assembly, staged observations/links, commit asset deltas | Atomic schedule/journal/completion persistence, post-commit activation and publication, recovery |
| `SchedulerImpl.execute` and storage | Flushes quality storage and links event shards through `HAS_DATA`; updates existing descriptors | Verified temporal slice allocation, preservation of historical data, geometry extension and client refresh |
| `klab.core.api/.../services/runtime/Message.java` | Observation lifecycle, activity and `ScheduleModified` messages | Durable consequence payloads, reconnect/replay protocol, timeline and changed-data consumption |

Additional hazards to resolve before enabling clocks:

- `SemanticType.isSubstantial(Set)` excludes relationships while `isEnumerableSubstantial` includes
  them. `isOccurrentSubstantial` includes functional relationships. Audit callers by purpose;
  changing a shared predicate globally could break cohort identity and temporal support.
- Scheduler recursion follows incoming `AFFECTS`. Adding process-to-quality semantic influence to
  quality-to-process input dependencies can create a cycle. `CompiledDataflow` also writes `rank`
  on some edges while the scheduler reads `sequence`; establish one execution-order contract.
- `checkEvent` relies on a last timestamp, not a distinct event and plan identity. Distinct observed
  events with equal end times, newly registered plans, and retry work require stronger keys.
- `EventImpl.toKey()` and `SchedulerEventImpl.toKey()` use different representations. Neither
  implementation supplies a usable observed-event construction path. Unify transport and identity.
- `restoreExecutor` explicitly rejects multiple actuators, children/input bindings, and partial
  coverage. Temporal replay must restore the actual plan closure or reject it before registration;
  leaf restoration is insufficient for typical process models.
- A retained submission scope may contain a completed transaction. Every later transition needs a
  fresh scope/transaction. Subscriptions must not become active before their registration commits.
- The replay-all Reactor sink is process-local and unbounded. It is neither crash recovery nor an
  authoritative digital-twin history.
- Emitter registrations may share an ID for overlapping equivalent cadences. Verify unequal start/
  end ranges, union coverage, per-actuator filtering, and independent unregistration before reuse.
- `switchToRealTime` currently tests `now < epochEnd` before returning false; reconcile this with
  the API's documented expired-epoch behavior and test the clock boundary explicitly.
- `KnowledgeGraphView` was not found in this checkout. Locate its owning client repository before
  implementing or claiming completion of the Timeline integration.

Existing test seams include `TimeEmitterTest`, `ActuatorPersistenceTest`, `CohortGeometryTest`,
`ResolutionCompilerQueryTest`, `DataflowAnnotationsTest`, and `ResolverTransportSerializationTest`.
Their existence does not establish occurrence readiness.

## 3. Schedule declaration and normalization

Every executable occurrent resolver or event instantiator must have an explicit schedule source:
Java contextualizer metadata, model `@time`, or both. Observation geometry alone is not a substitute.
Missing specification is a runtime validation error until, and also after, the future k.IM semantic
validator diagnoses it earlier. Reject invalid candidates before activating any schedule.

| Java declaration | Model `@time` | Effective schedule |
|---|---|---|
| Absent | Absent | Error for a scheduled occurrence plan |
| Present | Absent | Java schedule, validated against contextual support |
| Absent | Present | Model schedule, validated against contextual support |
| Present, overridable | Present | Model override, then full validation |
| Present, locked | Present and incompatible | Error identifying the locked contextualizer and source annotation |
| Present, locked | Present and equivalent | Proposed: accept as redundant, with Java provenance retained |

Use a specific runtime-retained temporal annotation alongside `@KlabFunction`, or a nested
declaration inside it. The exact Java and k.IM field names remain an O1 decision. Do not publish
examples that imply an unimplemented `@time(...)` syntax. Java reflection metadata must be copied
into the portable service specification; a remote Resolver must not require the Java class.

Proposed normalized contract, independent of source syntax:

| Field | Meaning |
|---|---|
| Schema version and source | Versioned representation; Java/model origin and diagnostic location |
| Bounds | Explicit start and finite end, or a typed open end; allow context-relative declarations only through deterministic binding |
| Cadence and phase | Positive step, temporal unit/resolution, and anchor; distinguish fixed duration from calendar recurrence |
| Override policy | Explicit Java lock/permission; default should permit model overrides |
| Bound support | Intersection with model, actuator and observation support; preserve original declaration separately |
| Plan identity | Stable actuator/plan revision and execution role, not a live executor or Java scope |

Recommended first milestone: bounded fixed-duration schedules with explicit anchors and complete
periods. Use half-open intervals `[start,end)` and execute a period when its end is reached; reject
unsupported calendar recurrence and ambiguous open bounds rather than approximating months as
milliseconds. Instantaneous observed events need a distinct point-membership rule, not an empty
interval. O1 must settle trailing partial periods and the instantiation-time convention before
implementation. Empty support must be reported as unschedulable, not as successful computation.

For a composed contextualization chain, define one effective schedule per executable temporal unit.
Validate every participating contextualizer's limitations and locked declarations. Conflicting
locked schedules are errors. Do not register each utility/filter function as an independent clock.
Keep general contextualization-chain and data-type validation in the same diagnostic pipeline as
semantic validation; this plan does not imply that the entire future validator already exists.

## 4. Registration, execution, and recovery

The target lifecycle is:

```text
resolve model and bearer-bound inputs
  -> validate complete executable plan and effective schedule
  -> persist actuators, bindings, semantic influence and pending registration
  -> commit registration
  -> restore/activate schedule and catch up to a fixed history watermark
  -> process matching transitions in causal order
  -> commit outputs + event completion + publication record
  -> publish committed consequences
```

INIT may initialize required static inputs, but must never execute the scheduled process or event
instantiator, including through recursive `AFFECTS` traversal or `executeDependency`. Put the policy
at the common execution boundary as well as submission. Successful registration must not claim
time-specific data coverage. A resolution arriving while time advances catches up to a captured
watermark, then joins live processing without a gap or duplicate. Requests must not read a later
state as if catch-up had completed; expose pending/failed catch-up explicitly.

Maintain separate identities for a shared clock cadence and an actuator registration. Deduplicated
clock generation must not collapse observation identity, execution support, or independent removal.
Intersect event time with the registration's phase, period, temporal bounds, and requested coverage.
Spatial support and other geometry dimensions remain intact. Pass the resulting transition geometry
and event consistently through local and remote contextualizers, storage, and provenance.

### 4.1 Durable history

Proposed durable records (placement in the graph/schema is an O2 decision):

- Registration: digital twin, observation, actuator/plan revision, role, normalized schedule,
  support, activation state, and catch-up watermark.
- Event: stable ID, kind, temporal support, observed-event observation ID when applicable,
  causal parent, logical ordering information, and source registration/revision.
- Execution receipt: registration/plan, event ID, support partition, result commit, and status.
- Publication record: commit ID and ordered/deduplicable client consequences.

The effective idempotency key includes registration revision, event identity and support partition.
A timestamp alone is never sufficient. New plans replay relevant history for themselves without
rerunning already completed unrelated plans. If a new cadence needs transitions absent from the
old history, derive and identify those transitions deterministically from the registered schedule;
replaying only previously emitted ticks is insufficient.

Persist completion only with the outputs it certifies. A crash before commit retries; a crash
after commit but before delivery republishes the same committed delta. Use an outbox or equivalent
durable publication mechanism. Do not promise exactly-once external side effects: contextualizers
must be replay-safe or declare a supported external idempotency protocol. Retain component/model
versions, seeds and necessary historical inputs; fail explicitly when restoration is impossible.

Do not reuse the root's closed transaction for future work. Each event execution gets a fresh
transaction and correctly bound scope. Serial causal ordering is the initial baseline. Parallelism
is permissible only for independent work after deterministic conflict handling is established.
Cancellation/rollback removes staged registrations and subscriptions; committed registrations
remain recoverable. A failed event cannot silently advance the completion cursor.

Retention/compaction requires a checkpoint that preserves state and replay obligations for later
resolutions. Until that exists, retain required history and bound catch-up batches/backpressure.
Persisted history is distinct from client-visible timeline entries and from activity provenance;
all three can share causal IDs without forcing every clock tick into the user's timeline.

## 5. Semantic influence and quality state

Resolve `affects`, `creates`, and connected declarations such as `increases with` through the
Reasoner. Preserve relation kind/direction and provenance when normalizing executable `AFFECTS`
links. A qualitative proportionality does not itself supply a numerical algorithm. Test the
ontology closure explicitly; the current `affectedOrCreated` implementation is not proof that
all connected properties are covered.

Bind semantic targets to observations in the correct bearer, endpoint context where explicit,
and temporal/spatial support. An unrelated instance with matching concept semantics is not a
valid target. For `creates`, retain a creation obligation/plan until concrete output observations
exist, then link those outputs. An `AFFECTS` edge must not point to an invented observation or
silently turn creation into mutation. Specify whether unresolved targets trigger ordinary
resolution immediately or remain pending before enabling this branch (O3).

Keep executable input prerequisites distinct from causal influence, even if both remain stored
under `AFFECTS` with typed roles. Traversing all incoming edges recursively is unsafe. For a process
that reads and affects the same quality, use explicit temporal state versions: the process consumes
the prior committed slice and its consequence computes the next slice. Reject unplanned same-time
cycles; O3 must settle conflict/composition semantics for multiple processes affecting one target.
Do not use thread completion order to select a writer.

For every new quality computation:

1. Select inputs as of the causal transaction and locate geometry at its temporal support.
2. Execute the resolved quality actuators in dependency order, with the actual event instead of INIT.
3. Allocate new Data nodes and storage for the new temporal state; preserve prior slices. A retry
   of an already committed event reuses its result, not a second state. Existing same-slice update
   behavior must not overwrite a different historical state.
4. Flush durable storage before committing Data descriptors, `HAS_DATA`, geometry/coverage changes,
   histograms, provenance, and execution receipts. Reclaim uncommitted files after failure.
5. Publish a committed delta allowing clients to fetch the new Data and extend quality geometry and
   visualization. Planned future support and actually computed coverage must remain distinguishable.

## 6. Event production and client consequences

A scheduled instantiator may produce zero events successfully. For each produced individual,
validate semantic type, identity, bearer/context, and temporal geometry before acknowledgement.
Its start must not precede the scheduled instantiation instant, including during historical replay;
wall-clock time is not the validation reference. A future end is valid. O1 must explicitly select
whether the instantiation instant is the transition start or end; the proposed end-of-period
execution convention uses the end. Future events must not make future quality state current early.

Resolve each individual through the normal observation lifecycle and await its required dependencies.
Only successfully resolved and committed individuals re-enter the scheduler as observed events.
Keep this observed-event route separate from collective-instantiator registration to prevent
re-registration loops. Proposed initial policy: one atomic instantiator batch, so failure of one
mandatory individual rolls back the batch; settle this in O4. Guard recursive event cascades with
causal IDs, duplicate detection, cancellation and explicit limits/diagnostics.

All observed events remain legitimate client-visible observations, even when they cause no further
computation. All committed event observations must use the ordinary observation lifecycle. Synthetic
temporal transitions reach clients only when they have committed consequences. Proposed criterion:
a committed observation/data/geometry change or recorded model outcome; an empty instantiator run
alone does not create a timeline entry. Failures remain diagnostics, not fictitious observations.

Client consequence payloads need stable event and commit IDs, causal parent, kind, time support,
changed observation/Data IDs, and sufficient ordering information for deduplication and reconnect.
Reuse existing commit asset deltas where possible. `ScheduleModified` describes the schedule, not
an execution result. Locate and update `KnowledgeGraphView` to populate Timeline, receive all
observed events, extend quality geometry, refresh data/histograms, and recover missed notifications
by fetching committed history. A late publication must not revert a newer displayed geometry.

## 7. Decisions to close at implementation gates

These are bounded engineering decisions, not reasons to defer the documentation or clear work.
Record the chosen alternative, compatibility impact, and test evidence here as stages finish.

| ID | Decision and recommendation | Gate |
|---|---|---|
| O1 | Final annotation/DTO syntax, override granularity (whole schedule recommended initially), context-relative bounds, interval boundaries, instantiation instant, partial periods, point events, calendar/time-zone and open-time policy | S1; calendar/open-time extensions may remain explicitly unsupported until S8 |
| O2 | Journal/receipt storage, atomic commit/outbox boundary, monotonic ordering, plan version retention, checkpoint and client-history retention | S2 |
| O3 | `AFFECTS` role/direction mapping, reasoner closure, `creates` target resolution, prior-state reads, competing writers and feedback | S3 before semantic dispatch |
| O4 | Atomic event batch versus partial success, future-start activation, cascade limits, user-facing consequence criterion and message schema | S6/S7 |
| O5 | Owning IDE repository, minimum service/client capability versions, graph migration and operational rollback policy | S7/S8 |

## 8. Staged delivery and continuation prompts

Every stage must update this document with files changed, exact checks run and results, limitations,
and remaining decisions. Do not mark a stage complete on unit tests alone when its gate requires
restart, persistence, distributed execution, or a live client. Do not expand accepted CONNECTION or
CLASSIFICATION scope incidentally. The prompts below are intended to be used in order.

### S0 — Baseline and contract inventory (this delivery)

- Completed: source inventory, required behavior, open decisions, implementation sequence and
  verification/deployment gates documented here.
- Not completed: runtime scaffolding, new annotations/DTOs, migrations, or occurrence execution.
- Verification: all seven relative documentation links resolve; Markdown fence balance and trailing
  whitespace checked. No Java tests were run for this documentation-only delivery.
- Next: S1. Runtime changes are deliberately staged because schedule syntax, event identity and
  causal-edge roles must agree across services before execution can safely be enabled.

### S1 — Portable schedule and execution-role contracts

**Deliver:** close O1 for the bounded fixed-duration subset; Java declaration, model annotation
adapter, normalized serializable schedule and execution role; portable function metadata; structured
runtime validation and diagnostics suitable for later language-validator reuse. Preserve absent
metadata for existing static models. Missing occurrence schedules must fail explicitly.

**Gate:** precedence matrix, locked conflicts, missing/invalid bounds, zero/negative/overflow cadence,
phase, source locations, composed-chain compatibility, Java-to-service and model-to-actuator JSON
round trips, and unchanged static models. Remote discovery must see exactly the local specification.

**Prompt:** “Implement S1 of docs/OCCURRENCE.md. Inspect existing time annotations and service
specifications before selecting syntax. Record O1 decisions for the first supported subset, carry
schedule and execution-role metadata through ComponentRegistry, model adaptation and portable
actuators, and add boundary validation/serialization tests. Keep temporal execution disabled.”

### S2 — Durable registration and INIT separation

**Depends on:** S1 and O2. **Deliver:** persist pending registrations atomically with validated plans;
activate after commit; guard INIT at every invocation path; initialize necessary static inputs;
restore complete plan bindings or reject unsupported plans at registration. Introduce event IDs,
receipts and journal/outbox schema without advancing live clocks.

**Gate:** process and event-instantiator executor counts remain zero after INIT, including recursive
and explicit prerequisites; relationships and event individuals retain normal lifecycle; rollback
leaves no live subscription; restart restores registration without initialization or lost bindings;
registration does not claim computed coverage. Schema round trips include multiple/partial plans.

**Prompt:** “Implement S2 of docs/OCCURRENCE.md. Separate temporal registration from initialization
at the shared execution boundary, preserve static prerequisite initialization, persist and restore
registration and complete actuator bindings, and establish atomic event/receipt publication storage.
Test rollback and restart; do not enable temporal dispatch until these gates pass.”

### S3 — Process bearer and semantic influence

**Depends on:** S1/S2 and O3. **Deliver:** process model inputs resolve on their inherent bearer;
functional/structural hosting rules are explicit; reasoner relationships bind to typed concrete
influence edges and creation obligations; prerequisite and causal traversals are separated.
Bind additional process-model output qualities to their bearer and validate named inline assignment
targets as specified in Section 1.1; reject implicit assignment to the primary process.

**Gate:** process on subject, event, and functional relationship; structural-host rejection; no
process-owned quality; same-named qualities on different bearers stay distinct; named/optional inputs
survive transport; inherited semantic effects and `creates` work without spurious targets; feedback
reads the prior slice; same-time cycles and unsupported competing writers fail deterministically.
Include accepted `set x to []` for a declared output quality and rejected `set to []`, unknown,
ambiguous, input-only and non-quality targets; preserve output bindings across plan transport.

**Prompt:** “Implement S3 of docs/OCCURRENCE.md. Bind process quality dependencies to the inherent
substantial/event while preserving explicit restrictions. Normalize and persist semantic AFFECTS
roles using Reasoner evidence, separating them from computational prerequisites. Close O3, test
functional-relationship hosting and temporal feedback, validate inline process output assignments
under Section 1.1, and preserve CONNECTION identity behavior.”

### S4 — Deterministic simulated dispatch and catch-up

**Depends on:** S2/S3. **Deliver:** connect TimeEmitter to scheduler dispatch; match schedules and
support; construct fresh transactional scopes; pass temporal geometry to local/remote executors;
commit receipts and advance watermarks; restore and replay only required work with backpressure.

**Gate:** no INIT calls; cadence/phase/intersection boundaries; unequal overlapping schedules and
independent removal; two observed events sharing timestamps; late registration needing a new cadence;
same-twin restart; cache eviction; replay equivalence; failure before/after commit; no duplicate
execution result or coverage; concurrent resolution cannot skip a tick. Use a controllable clock.

**Prompt:** “Implement S4 of docs/OCCURRENCE.md for bounded simulated time. Wire emitter output,
per-registration filtering, fresh transactions, causal dispatch and durable catch-up. Test newly
introduced historical cadence, restart, retry, equal-time identities and cache restoration against
an uninterrupted reference run. Keep unsupported real-time/calendar behavior explicitly rejected.”

### S5 — Versioned quality data and committed geometry

**Depends on:** S4. **Deliver:** recompute affected qualities at transaction time, allocate new
temporal Data/storage, retain old states, extend committed coverage, and publish fetchable deltas.
Compile and execute inline `set x to []` process assignments over each output quality's localized
current-time geometry. Deliver this simple-process path before advanced Java process contextualizers
and their scanner matching by name/annotation after split and fill-curve negotiation.

**Gate:** assert distinct Data/shards across transitions, unchanged historical values, exact event
geometry in local/remote invocations, durable flush before descriptor commit, no duplicate retry
slice, rollback/file cleanup, correct histogram/geometry extension, and replay-equivalent storage.
Use an inline process model with model `@time` and no Java contextualizer. Check per-location values
over nonuniform inputs and differing output supports, correct prior-state reads, multiple named
quality outputs, expression type errors, restored expression execution, and no duplicate output
computation. Verify location/value correspondence under supported split and fill-curve configurations.

**Prompt:** “Implement S5 of docs/OCCURRENCE.md. Follow semantic consequences into resolved quality
actuators with transition geometry, persist new temporal states atomically with completion and
geometry deltas, and preserve historical data. Verify storage contents and Data links across retry,
rollback and restart rather than testing invocation counts alone. Implement Section 1.1's inline
scalar process assignments as the first executable fixture, with named output binding and localized
quality geometry; do not defer this path until advanced Java contextualizers are available.”

### S6 — Scheduled event instantiation and observed-event feedback

**Depends on:** S4/S5 and O4 lifecycle decisions. **Deliver:** zero/many output batches, temporal
validation, awaited individual resolution, durable observed-event enqueueing and causal propagation.

**Gate:** empty success; invalid past start rejected; future duration accepted; future start handled
without early mutation; distinct same-time individuals; failed acknowledgement publishes/enqueues
nothing; replay never duplicates individuals; nested event/process consequences terminate or report
a diagnosed cycle. Include event-owned qualities and hosted processes.

**Prompt:** “Implement S6 of docs/OCCURRENCE.md. Execute event instantiators on their schedules,
validate each individual's time against the logical instantiation instant, resolve outputs through
the ordinary lifecycle, and enqueue committed observed events idempotently. Close O4 batch and
future-event policies and test empty, failed, recursive and replayed batches.”

### S7 — Messaging, Timeline, and quality visualization

**Depends on:** S5/S6 and O4/O5 protocol decisions. **Deliver:** versioned consequence messages,
post-commit durable delivery, reconnect history, client deduplication, Timeline and data/geometry
refresh in the repository owning KnowledgeGraphView. Document cross-repository commits together.

**Gate:** suppress consequence-free synthetic ticks; show every observed event; show only committed
data; duplicate/reordered delivery and reconnect converge to the same timeline/geometry; unavailable
clients do not lose history or stall execution; older clients receive a supported compatibility path.

**Prompt:** “Implement S7 of docs/OCCURRENCE.md across runtime and the client owning
KnowledgeGraphView. Publish durable committed consequence deltas, recover them after reconnect,
show consequential temporal transitions and all observed events, and refresh affected quality
geometry/data. Verify Timeline with a live client and record compatible protocol versions.”

### S8 — Real time, release, and operational acceptance

**Depends on:** S1–S7. **Deliver:** supported open-time and simulated-to-real-time policy, bounded
catch-up/backpressure, cancellation/shutdown, migrations and capability gating, observability and
operational recovery. Calendar recurrence can be a separately gated extension if explicitly rejected
by the first release. Integrate k.IM validator diagnostics in the language-owning repository when
available; runtime validation remains mandatory regardless of editor deployment.

**Gate:** controllable-clock real-time tests, outage/catch-up and clock-switch continuity; database
migration and restart against retained history; client compatibility; bounded-resource load tests;
full service/client acceptance scenario below; maintainer acceptance before broad enablement.

**Prompt:** “Implement and release S8 of docs/OCCURRENCE.md. Close supported real/open-time and
deployment policies, run migration/restart, clock-switch, load and live-client acceptance tests,
document unsupported cases and rollback constraints, and enable only the capability set verified
across the deployed services and IDE. Report evidence without treating unavailable checks as passed.”

## 9. Verification and deployment playbook

Use a small deterministic fixture: a substantial with initialized quality Q; a process on that
substantial that reads Q's prior state; a two-step schedule; an event instantiator emitting zero
events on one transition and one event on another; that event affects Q and has a future end.
Implement the first process fixture through k.IM inline `set x to []`, with `x` declared as an output
quality after the primary process observable and an explicit model `@time`. Use a deterministic scalar
expression over nonuniform inherent inputs to verify computation at each quality location and time;
no Java process contextualizer is required. Include a rejected `set to []` fixture. Repeat with a
functional relationship bearer and reject a structural bearer. Add advanced Java scanner-bound
contextualizers later as parity tests after split and fill-curve negotiation is available.

Compare uninterrupted execution against late resolution, executor-cache eviction, and service
restart after the first commit. Assert equal values, temporal support, causal outcomes and logical
output identities; database-generated IDs need not match across independent twins. Inject failures
before flush, after flush/before commit, and after commit/before message delivery. No run may lose
an acknowledged event, overwrite prior data, publish rolled-back state, or invent completed coverage.
Inspect the real client's Timeline and quality display, then disconnect/reconnect and compare again.

Run focused tests at each gate, then relevant module regression suites. Candidate commands from the
repository root (Java 21 and the project's dependency/bootstrap requirements apply):

```powershell
mvn -pl klab.services.runtime -am '-Dtest=TimeEmitterTest,ActuatorPersistenceTest,CohortGeometryTest' -Dsurefire.failIfNoSpecifiedTests=false test
mvn -pl klab.services.resolver -am '-Dtest=ResolutionCompilerQueryTest,DataflowAnnotationsTest,ResolverTransportSerializationTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Extend those selections with each stage's new tests; retain CONNECTION and CLASSIFICATION
regressions. These are proposed verification commands, not commands executed for S0. Add graph/storage
integration tests using the project's supported test infrastructure, then distributed and UI checks.
Grammar/validator changes require the owning language repository's regeneration and validation suite.

Deploy in this order:

1. Back up the graph and storage; test migrations on a copy. Add versioned schema and capability
   negotiation with dispatch disabled. Do not infer schedules for historical occurrent plans.
2. Upgrade API/metadata producers, Resources/Reasoner/Resolver/Runtime participants and clients as
   required by the negotiated protocol. Unsupported consumers must reject occurrence plans clearly.
3. Exercise bounded simulated twins first, then restart/replay and messaging, then real-time canaries.
   A rollout control must cover activation and clock advancement, not just model discovery.
4. Observe execution lag, oldest pending event, retry/failure counts, registration/queue size,
   orphan storage, publication backlog and replay duration. Define operational thresholds from the
   load gate before broad rollout, rather than inventing fixed limits here.
5. On failure, stop accepting new temporal work and quiesce clocks at committed boundaries; preserve
   registrations, receipts, outputs and outbox records for recovery. Disabling execution is not a
   rollback of committed scientific history. Binary rollback is allowed only if the older version
   understands the schema; otherwise roll forward or restore a coordinated graph/storage backup.

Completion requires all six requested capabilities: schedule declarations and INIT separation;
history-consistent temporal dispatch; semantic influence with new quality states; bearer-correct
process dependencies; scheduled event generation/resolution/feedback; and consequential temporal plus
all observed-event delivery to the client. Record maintainer acceptance and replace staging claims
with implemented contracts only after those gates pass.
