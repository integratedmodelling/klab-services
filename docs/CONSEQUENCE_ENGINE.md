# Consequence engine — design draft

This document collects the current design and the initial runtime implementation.
Post-commit scheduling, decision/commit messages and automatic change resolution are
implemented as described below. Configuration discovery and role conferral have
handler designs; their semantic evaluation is not yet executable. Raw role implication
remains unspecified. Declaration parsing, OWL restrictions, activity names and source
validation alone do not establish that a consequence executes.
This is the authoritative home for the evolving design; reference guides and the
agent context pack should summarize it and link here.

Here, a **commit** means an observation/knowledge transaction commit in a digital
twin, not a Git commit. Consequences operate on observations and their scientific
meaning, not source-control changes.

## Agreed semantic contract

The engine runs after each observation/knowledge commit. Its responsibilities are:

| Consequence | Intended behavior |
| --- | --- |
| Configuration DETECTION | Recognize an emergent configuration observation from detected ingredients |
| Change processes | Resolve change processes for qualities when their context becomes occurrent |
| Conferred roles | Apply `confer` axioms for roles conferred by processes or relationships |
| Role implications | Follow implications of roles |
| Optional uncertainty | Optionally resolve `uncertainty of` qualities |

Consequences may cause further consequences through the models selected to resolve
them. The design is not a single pass. Existing resolution idempotency and reuse
are the primary mechanisms for avoiding duplicate work; the engine should not
introduce a separate competing resolution state machine.

### Agreed effective-head interpretation of `of`

Interpret binary `of` by the effective head kind after alias resolution and unary
derivation:

| Effective head | Language meaning and normalized relation |
| --- | --- |
| Quality or process | Native inherency, using `odo:isInherentTo` |
| Predicate | Applicability, retaining shared `odo:isInherentTo` contextualization mechanics |
| Configuration | Emergence shorthand, using `odo:emergesFrom`, not native inherency |

A configuration's application target is stated explicitly with `applies to`
(`odo:appliesTo`). `Network of Relationship` therefore identifies emergence
ingredients; it does not name the Network's application target. The configuration
rule is agreed language semantics, while the runtime that detects the resulting
observation remains future work. Independent subject/agent/event heads cannot take
binary `of`; unary forms containing that token retain their own derivation rules.

### Configuration lifecycle

DETECTION produces the emergent configuration observation. Models never instantiate
configurations: `model each Configuration` is illegal. `model Configuration` is a
legitimate request to resolve an observation **after emergence**. That model does
not create the configuration observation.

Placement depends on the kind of detected ingredients:

- Configuration detected from the (com)presence of dependents is placed under those
  dependents' bearer.
- Configuration detected from substantials belongs to root-level cohorts, like
  substantials.
- Relationship emergence yields Network configurations. Resolving an emerged Network
  creates the scientific network model, a distinct step from detecting its existence.

Different-bearer dependent ingredients need an explicit design decision. This draft
does not merge bearers, pick one arbitrarily, discard ingredients or infer a new
inherency relationship to make detection possible.

`model Quality of each Bearer` distributes the bearer and is distinct from attempting
to instantiate a configuration. Aliases and unary-derived expressions must be checked
by their effective head kind, rather than by surface keywords or stale type flags.

### Containment remains a separate observational contract

Observational containment is neither spatial enclosure nor physical parthood.
`A contains B` and `B contained into A` normalize to the same A-to-B authored contract.
Worldview declaration `within` specializes that contract without changing native
inherency. A successfully empty collective is legal; unresolved or failed discovery
is not successful emptiness. No member, geometry, coverage or completed detection is
entailed merely by declaring containment.

The marked minimum-zero OWL restriction is an asserted declaration carrier, not an
entailment-based permission test. See the
[containment contract](OBSERVABLES.md#observational-containment-declarations) and
[declaration/execution boundary](OBSERVATION.md#observational-containment-declaration-support-versus-contextual-execution).
Neither containment nor emergence is a shortcut for waiving a dependent's native
bearer, identity obligations or explicit abstractness.

### Execution choice and recursive consequences

When a consequence requests resolution, normal strategy precedence still applies.
A successful independent Tier-0 precomputed strategy must win before expensive
Tier-1+ recursive discovery/computation. Preserve coverage-aware partial fulfillment;
only outstanding support may require another route. Declaration legality and the
chosen execution route remain separate.

Idempotency prevents repeated execution of an identical observation/request. Global
termination additionally requires bounded generation of distinct observations and
requests. Verify that invariant against k.LAB's identity, scope and revision rules;
it is not a claim that the current resolution contract fails. No scheduling,
fixed-point or work-queue algorithm is mandated by this document.

## Current support and implementation boundary

| Component | Observed source support | What this does not establish |
| --- | --- | --- |
| Activity API | [Contextualization.java](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/knowledge/Contextualization.java) defines DETECTION and maps configuration semantics to it | A working post-commit detector, placement engine or separate detection/resolution scheduler |
| Worldview compilation | [WorldviewDeclarationSupport.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/owl/WorldviewDeclarationSupport.java) compiles explicit emergence triggers to `odo:emergesFrom` | Automatic observation creation or completed runtime trigger evaluation |
| Trigger registration | [ReasonerService.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/ReasonerService.java) registers emergence declarations through `registerEmergent` | A successful detection consequence |
| Emergence runtime | The same service's `emergentResolvables` currently returns an empty map; `Emergence.makeObservationCatalog` has no active catalog population | End-to-end detection, placement, updates or network construction |
| Model validation candidate | [KimNamespaceVisitor.java](../klab.core.services/src/main/java/org/integratedmodelling/klab/runtime/language/KimNamespaceVisitor.java) and [DocumentSemanticValidator.java](../klab.services.reasoner/src/main/java/org/integratedmodelling/klab/services/reasoner/internal/DocumentSemanticValidator.java) contain the focused `model each Configuration` rejection | A runtime detector; passing-test status must come from the implementation report |
| Transactions and reuse | [Knowledge graph transactions](KNOWLEDGE_GRAPH.md#semantic-attribution-transactions) and [resolution state](RESOLUTION.md#11-reentrancy-and-concurrency-risks) describe existing identity, isolation and commit behavior | A complete consequence-engine integration or general committed semantic-plan catalog |

The existing resolution guide documents attempt isolation and process-local
coalescing of concurrent singular-substantial submissions. It also identifies the
missing atomic publication of reusable successful semantic plans. Reuse and
idempotency integration must therefore be verified for each consequence category;
the design must not equate one existing coalescing path with complete engine support.

The initial paths below implement change-process triggering and the runtime extension
boundary. They do not establish complete emergence, role conferral, role implication,
optional uncertainty, durable work recovery or recursive termination.

## Initial runtime implementation

### Commit boundary, worker and lifecycle

[DigitalTwinImpl](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/DigitalTwinImpl.java)
owns one [ConsequenceEngine](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/ConsequenceEngine.java).
The successful **root** transaction enqueues its commit after the native graph
transaction closes, semantic attributions and commit cache are published, post-commit
callbacks run, and the transaction is unregistered. Intermediate child commits,
rollback, cancellation before commit and failed durable close do not enqueue work.
A failure to enqueue cannot change the outcome of an already durable commit.

The engine uses a lazy daemon platform thread at `Thread.MIN_PRIORITY`. Ordinary
passes within a twin are serialized in enqueue order. Uncertainty is a separate,
lowest-priority queue item: all queued ordinary passes, including those generated by
other consequences, precede it. Already-running uncertainty is not preempted; a steady
stream of ordinary commits can postpone it. This is not a claim of global commit order,
ID order or strict OS scheduling priority. Commit summaries are detached before
enqueueing and separately copied for each handler. Graph queries see committed
current state, which may include later commits; they are not historical snapshots.
No reasoner or client-messaging call runs on the committing thread.

Each pass visits configuration detection, change processes, role conferral, role
implication and uncertainty. `register(kind, handler)` supplies the runtime extension
point. Configuration discovery is installed with an explicit deferred result; change
resolution is installed and active; the other missing handlers report `DEFERRED`.
Handlers return `APPLIED`, `NO_ACTION`, `DEFERRED` or `FAILED`, an explanation and
observation evidence IDs. A failed handler does not prevent the other categories or
later commits from running. Handlers must await their own submissions, honor
interruption and never wait for this engine's queue: a resulting commit starts a
later pass rather than a recursive Java call.

The queue is in-memory and currently unbounded. Pending IDs and the last 1024 completed
commit IDs suppress duplicate scheduling locally. This is transport/work deduplication,
not observation reuse or a new resolver state machine. Shutdown/disposal interrupts
the worker and drops queued work before storage can be closed/deleted; a handler that
does not stop within five seconds prevents that disposal. There is no durable outbox,
restart replay, retry policy, backpressure policy or exactly-once delivery guarantee.
These remain prerequisites before enabling potentially expansive semantic handlers.

### Scope, causality and client messages

[Consequence](../klab.core.api/src/main/java/org/integratedmodelling/klab/api/digitaltwin/Consequence.java)
defines the versioned cause and typed decision/commit envelopes. Each decision has a
UUID, triggering commit/activity IDs, context ID, selected observer ID, bound worldview
fingerprint when available and the previous decision ID for a consequence-generated
commit. This fingerprint identifies the bound worldview; it is not an atomic snapshot
of live reasoner revisions or a complete reuse key.

`ServiceContextScope.forConsequence` derives a scope preserving the requesting user,
observer, context and resolution constraints while dropping the closed transaction,
remote transaction ID and originating request's cancellation signal. A dedicated
cause field is inherited by further scope copies and asynchronous runtime submissions;
it is not stored in the shared scope data map or a thread-local. The root activity of
each resulting transaction persists the cause as `klab.consequence.cause` metadata.
Commit summaries, including temporal journal snapshots, carry `consequenceCause` too.
`ContextScope.getConsequenceCause()` also exposes the cause to service clients.
`Utils.Http.Client.withScope` transports it in the Base64/UTF-8 JSON `klab-consequence`
header and clears it when a reused client adopts an ordinary scope. The authorization
filter forwards the header; `ScopeManager.contextualizeScope` validates its context
against the already-authorized scope and derives isolated diagnostics before restoring
the observer, bearer and local/remote transaction. The header grants no access. This
keeps remote resolver/runtime requests in the same causal chain without leaking
background status into another request or waiving mandatory remote dependencies.

All consequence resolutions are **optional**. The derived scope's observation builder
and the runtime submission entry point mark new top-level requests optional. Mandatory
dependencies inside an executing model retain their normal semantics: optionality does
not turn a failed dependency into successful scientific computation. Errors and warnings
reported through the consequence scope or its copies are collected for its decision;
they do not set the originating user's error state or send separate error/warning
notifications. An exception becomes a `FAILED` decision and processing continues. Each
handler produces one outcome notification through the root scope, including on failure.
Activity/provenance events still describe attempts; they are not additional user errors.

Ordinary user submission notifications are unchanged. Consequence submissions do not
send `ObservationSubmissionFinished`, which existing interfaces interpret as a user
navigation request. Two new `DigitalTwin` messages use the context's `Events` channel
and existing AMQP fan-out to all connected subscribers:

| Message | Payload and meaning |
| --- | --- |
| `ConsequenceDecision` | `Consequence.Decision`: the handler's outcome and explanation, including explicit deferral/failure |
| `ConsequenceCommitted` | `Consequence.Commit`: a **new successful root commit** produced under a consequence scope, its added observation IDs and graph changes, plus its originating cause |

Ordinary triggering commits do not emit `ConsequenceCommitted`. The engine emits that
message when it processes the generated commit, before evaluating its next consequences.
The decision and commit are correlated by decision ID; clients must not assume ordering
relative to ordinary submission/activity messages. Delivery failures are logged and do
not roll back graph changes or stop semantic processing. Disconnected clients do not
replay these messages automatically; existing temporal-history recovery remains separate.

[ClientDigitalTwin](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/digitaltwin/ClientDigitalTwin.java)
forwards decisions to its event consumers and validates the context of each envelope.
For consequence commits it first applies the summary through
[ClientKnowledgeGraph](../klab.core.common/src/main/java/org/integratedmodelling/common/services/client/digitaltwin/ClientKnowledgeGraph.java),
invalidating modified assets/adjacency before notifying consumers. The existing shared
commit-ID deduplication handles ordinary commit messages arriving before or after the
new event, including temporal commits. Consumers still receive the consequence
notification when graph changes were already applied so that causal information is
not lost. No additional cache-only commit lookup is required. The commit is retained
in the client graph's commit queue. Its observations are addressable through normal
asset retrieval and graph traversal before consumer callbacks run; notification does
not require eagerly downloading every observation or selecting any of them.

### IDE notification and navigation contract

The sibling `klab-ide` project was analyzed at these paths:

- `IDEContextScope.processSubmissionFinished` records the commit, changes focal root
  and observation, and dispatches `submissionFinished` and `knowledgeGraphModified`.
- `DigitalTwinControlPanel.submissionFinished` calls `ObservationTree.update` and
  selects the observations view. Its generic `knowledgeGraphModified` callback also
  rebuilds the observation tree.
- `ObservationTree.update` clears descriptions, replaces the root and selects the
  requested observation. Merely retaining the focal IDs does not retain expanded
  branches, selection and scroll position.
- `DigitalTwinEventRouter` already serializes viewer delivery through `IDEContextScope`;
  the commit navigation menu and activity views provide places to expose background
  work without selecting it automatically.

Only commits directly enacted by user submissions should use the first-class
submission-completion path. Consequence commits must be **visible and inspectable**,
including every added observation, on every connected client. A background designation
must never discard the commit or hide its observations. Its cause supplies the link to
the originating submission and decision; IDs or completion order do not establish
whether a commit is first-class.

The IDE integration should add dedicated `consequenceDecision` and
`consequenceCommitted` viewer callbacks. On a commit, retain a deduplicated entry in a
background history/commit menu, expose the added observations and update a quiet badge
or activity entry. Selecting that entry may intentionally focus the commit or one of
its observations. Receiving it must not call `submissionFinished`, `setFocus`, switch
views, select an observer or rebuild the tree through `knowledgeGraphModified`.
Decision entries are keyed by decision ID and commit entries by commit ID; attach
their causal links even when messages arrive in either order.

For an already visible branch, reconcile children and modified descriptions by stable
asset ID while preserving tree items, expansion, selection and scroll position. Refresh
only affected visible cards or layers, preserving map extent and active tabs. Off-screen
observations remain accessible through background history and ordinary graph navigation.
Batch updates on the JavaFX thread. A duplicate ordinary/temporal notification carrying
`consequenceCause` must follow this same background path. Temporal commits without a
user submission should also avoid submission navigation. This is the IDE integration
design; the current services change implements the typed transport and client graph
ingestion, not the JavaFX tree reconciliation. Tests for that IDE work must assert both
observation accessibility and preservation of focus, expansion, selection and views.

### Activity types, agent attribution and causal provenance

`Activity.Type.CONSEQUENCE_SUBMISSION` distinguishes engine-initiated submissions from
ordinary `SUBMISSION` activities. Both satisfy `Type.isSubmission()` for observation
creation provenance and observer-selection boundaries. Resolution remains `RESOLUTION`;
scientific execution retains its actual contextualization type (`SIMULATION`, `MEASURE`,
`CLASSIFICATION`, etc.). A consequence label does not replace the scientific operation.

The runtime selects this submission type before starting the transaction. The native
transaction boundary also stamps submissions opened directly by a handler. Every activity
in a consequence transaction carries the cause metadata and a `BY_AGENT` edge to the
persisted **k.LAB** provenance agent. An inherited user-supplied provenance constraint
cannot attribute engine work to that user. The requesting user still supplies authorization,
context, observer and resolution constraints; authorship and access identity are distinct.
New observation creation summaries record k.LAB as `im:creation-agent`, preserving the
original creator of pre-existing observations.

Each root consequence transaction retrieves the durable activity identified by
`cause.triggeringActivityId` and adds an ordinary `TRIGGERED` edge from that activity
to the new submission. Child execution activities remain linked to their executing
parent. The trigger's URN, persistent ID and transient ID are attached to the new root
before `ActivityStarted`, and are retained in `ActivityFinished` and the durable graph.
An unavailable triggering activity or k.LAB agent fails the optional attempt; it must
not silently produce unlinked or user-attributed provenance. The original activity
is neither reopened nor stored again.

For a user submission followed by successive consequences, the graph therefore contains
`user submission → consequence submission → next consequence submission`, with
`TRIGGERED` edges throughout and the execution phases below their own submissions. This
preserves the causal path back to the original user-bound contextualization rather than
making asynchronous work appear to be a new user action. If a triggering commit instead
originated in a scheduler or another autonomous activity, preserve that actual antecedent
and its existing provenance; do not invent a user action. Decision IDs and triggering
commit IDs additionally explain *why* each edge exists.

The IDE's existing `ActivityCatalog` can reconstruct these links from parent transient
IDs or triggering URNs even when child events arrive first. Its activity tree already
has a fallback icon for new types; a dedicated consequence icon/label can be added with
the background commit UI. Observer recovery stops at both submission types so a missing
observer selection never inherits an unrelated perspective from the causal ancestor.

### Automatic change resolution

[ChangeProcessHandler](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/ChangeProcessHandler.java)
runs for commits containing added observations or modified assets. Initially it scans
the committed observation catalog visible in the requesting context. The digital twin
is treated as occurrent if any committed observation has GRID/REAL occupied time, an
agent has GRID/REAL perceived time, or an individual event/process is present. PHYSICAL time alone and a collective event
declaration are not sufficient. This is a twin-wide activation rule; qualities are then
resolved separately in their own native bearer scopes. No mutable "became occurrent"
flag is needed, so newly observed qualities and previously existing qualities are both
considered after occurrence becomes available, including after restart on the next commit.

For each concrete quality with positive resolved coverage, the handler obtains its
durable `HAS_CHILD` bearer, groups qualities by bearer and requests `change in Quality`
through `Observable.Builder.as(CHANGE)` and the ordinary context observation builder's
`submit()`. Missing bearers or support are deferred. It uses the bearer's grid support
when available, otherwise the quality's support, and sets an explicit Geometry
constraint on the request. No global instantiation, private resolver or synthetic
process-model execution is introduced.

Before each request it re-reads durable `AFFECTS` writer links. An existing resolved
process with an `INFLUENCE` edge, the same bearer, compatible observer and complete support
already provides change. A descriptive influence, event, wrong bearer or partial support
does not. The handler also checks normal observation reuse for the exact change request,
retaining the resolver's observer-specific preference over absolute fallback. It checks
again for every quality so a joint process installed by the first request can satisfy
later qualities without another submission. Partial results remain subject to normal
coverage-aware resolution. No-model/unresolved results are deferred; errors are failed;
shutdown cancels an outstanding submitted future. Decision evidence lists the qualities
whose change submissions returned successfully.

`ResolutionConstraint.Type.ChangeQualities` transports the observed quality group into
normal model selection. In
[PrioritizerImpl](../klab.services.resolver/src/main/java/org/integratedmodelling/klab/services/resolver/PrioritizerImpl.java),
an admitted process model gains preference when its native inherent is equivalent to
the actual bearer and its declared affected/created outputs or dependencies cover at
least two distinct requested qualities. More covered qualities rank first within this
partition. A generic supertype bearer, repeated entries for one quality, arbitrary inputs
without effects, and non-process models gain no joint preference. Revision-bound
semantic distance checks use the model's admission receipt. Observer compatibility is
compared first, followed by this preference and the existing ranking criteria.

This preference applies **within model selection**, so Tier-0 independent strategies
retain precedence over recursive strategies. Admission, constraints, coverage, process
binding validation, normal reuse and scheduler writer arbitration still apply. This is
an initial preference, not a global set-cover optimizer or permission to run overlapping
writers. The existing scheduling and process-plan machinery performs actual change.

## Configuration discovery handler design

[ConfigurationDiscoveryHandler](../klab.services.runtime/src/main/java/org/integratedmodelling/klab/services/runtime/digitaltwin/ConfigurationDiscoveryHandler.java)
is the installed entry point. It currently returns `DEFERRED` with the missing reasoner
capability named explicitly. The following is the intended implementation contract,
not a claim that configuration observations are already submitted.

1. **Select rules affected by the commit.** Obtain configuration declarations whose
   `emerges from` ingredients or `applies to` targets can match added/modified
   observations and changed links. Also revisit existing ingredients when a suitable
   target first appears. Remove/reclassify/retract changes require invalidation of
   earlier matches; lack of invalidation support must produce deferral, not an invented
   deletion. Candidate indexing is an optimization; the reasoner retains the complete
   normalized declaration and source provenance.
2. **Match emergence.** Evaluate the authored Boolean expression over committed,
   observer-visible observations, returning ingredient IDs and their matched clauses.
   AND needs every operand under compatible support; OR preserves the successful
   alternative(s). Preserve nested expressions, inherited restrictions, aliases and
   effective head semantics. Do not flatten clauses to an unordered set or collapse
   distinct observations keyed only by observable. Absence, negation and mixed-bearer
   matching remain explicit unsupported cases until their contracts are defined.
3. **Check applicability independently.** `emerges from` answers which evidence creates
   the configuration; `applies to` answers which observed targets admit it. For every
   potential target, require reasoner evidence that its semantics satisfy the complete
   applicability expression. A match on ingredients alone is insufficient. Inherited
   applicability and specialization must be preserved. An absent applicability clause
   must be represented distinctly from failed/unavailable evaluation; target selection
   for that case needs an explicit language/runtime policy before submission.
4. **Choose placement and support.** Common-bearer dependent ingredients produce a
   configuration under that bearer when the applicability check admits the target.
   Substantials/relationships produce configurations in root cohorts; their application
   target is separate from their graph parent. Relationship ingredients produce Network
   configurations. Record target, placement, ingredients and actual shared support;
   never infer geometry from containment or select an arbitrary bearer. Different
   dependent bearers and ambiguous target/support matches defer with an explanation.
5. **Validate and submit detection.** A match receipt should contain configuration
   semantics, rule/source ID, ingredient IDs, target ID, placement, support, observer,
   worldview/reasoner revision and graph read guards. Revalidate this evidence at the
   DETECTION commit boundary. Resolve an existing configuration identity first; admit
   a new identity atomically only for a supported new match. Pass the receipt into
   the normal context-scope submission path specialized for DETECTION. DETECTION
   materializes the emergent observation; any subsequent `model Configuration`
   resolution happens after that observation exists. Never use `model each Configuration`
   or let model execution manufacture the discovery evidence.
6. **Notify and continue.** Return the detection decision with evidence IDs. A successful
   resulting root commit automatically persists its cause, emits `ConsequenceCommitted`
   and schedules another pass. A no-match, unsupported evaluation, no-model resolution
   and failure remain distinct; detecting a configuration does not assert that a model
   for it was resolved.

The reasoner needs a public, transported declaration/match contract before step 5 can
be enabled. `Reasoner.applicableObservables` supplies existing applicability access,
but not a complete Boolean match receipt. `ReasonerService.emergentResolvables` is not
a usable public detection API: it currently returns an empty map and its catalog is
unpopulated. Parsing/normalization can be extended to retain the emergence/applicability
expression trees and provenance required here; existing clause syntax and OWL support
are not replaced by a runtime text parser. Unsupported capability must remain distinct
from a successful query with no matches.

For Terrain, Elevation/Slope/Aspect under an applicable Region would supply the common
bearer and ingredient evidence. For a StreamNetwork, StreamConnection observations
supply emergence evidence and an applicable Region supplies the application target;
the Network still belongs to a root cohort. Its network model is a subsequent request.

## Process role conferral handler design

The `ROLE_CONFERRAL` slot currently defers. Its intended first executable path is a
committed process plus reasoner evidence for its `confer` declarations. For each
declaration, retain the conferred concrete role, the process/rule identity, applicability
restrictions, observer and semantic revision. Find the process's actual durable bearer
and validate that the role applies there. A relationship-based rule must specify which
participant receives the role; it must not confer to both endpoints by default.

Resolve the role within the selected bearer scope using normal role/classification
resolution, then apply the resulting semantic attribution through the existing guarded
transaction mechanism. Reuse the bearer identity rather than creating a new bearer,
and persist process/rule/evidence provenance in addition to the consequence cause.
Idempotent role attribution must precede any follow-on resolution of observables enabled
by that role. Abstract roles requiring specialization, missing target evidence, and
unspecified withdrawal/lifetime rules must defer explicitly. A successful attribution
commit naturally causes another engine pass and both decision/commit notifications.

Conferral is separate from raw implication. `ROLE_IMPLICATION` must not infer a closure
or call existing implication stubs as if they supplied an agreed operational contract.
Direction, inherited implications, withdrawal, conflicts, provenance and termination of
role-to-role implication remain to be specified. Uncertainty also remains deferred
until its selection policy is defined, and always occupies the lowest queue priority.
Configuration, change, conferral, implication and uncertainty requests all obey the
same optional-resolution and single-outcome-notification contract.

## Verification paths

- `ConsequenceEngineTest`: durable root boundary, child/failed commits, detached input,
  asynchronous low-priority execution, uncertainty yielding to queued commits, duplicate
  scheduling, failure isolation and a single failure notification, optional request
  construction, inherited scope cause, recursive generated commits and shutdown cancellation.
  It also checks persisted `TRIGGERED`/`BY_AGENT` links over multiple generations,
  k.LAB attribution despite a user provenance argument, unchanged scientific phase types
  and preservation of the original user activity.
- `ConsequenceSubmissionTest`: ordinary submission-completion delivery, its suppression
  for consequence work and optionality at the public runtime entry point.
- `FederatedIdentityTransportTest` and the remote-scope case in `ConsequenceEngineTest`:
  cause-header round trip/cleanup, authorized-context validation, remote transaction
  restoration and isolation of optional diagnostics from ordinary requests.
- `ChangeProcessHandlerTest`: physical versus grid time, event/process activation,
  old/new qualities, grouped requests, joint-writer reuse, partial/wrong-bearer/descriptive
  exclusions and unresolved/failed resolution outcomes.
- `JointChangeRankingTest`: distinct affected/created qualities, exact bearer preference,
  generic-bearer exclusion and observer-priority ordering.
- `ConsequenceTransportTest`: JSON message round trips, multiple client consumers,
  invalidation and new observation accessibility before notification, context isolation and ordinary/consequence commit
  arrival in both orders. This uses the messaging seam; it is not a live AMQP broker test.

Existing graph-client, transition transport, process binding and classification-transaction
tests cover adjacent paths. The implementation report must state actual test outcomes.

## Worked examples and counterexamples

**Terrain-like pattern.** After committed Elevation, Slope and Aspect observations
provide the required (com)presence under a common bearer, DETECTION may recognize the
configuration there according to its authored emergence rule. A later configuration
model resolves that observation. Merely parsing an `emerges from` clause does neither.
This example does not decide whether a particular rule requires all ingredients or
one boolean alternative; preserve the authored boolean contract.

**Stream network.** Detected StreamConnection relationships may support a Network
configuration. Its observation belongs in a root-level cohort; a network model is
constructed when that observation is resolved. Declaring Region containment alone
does not discover StreamConnections, prove their geometry or create the network.

**Occurrent transition.** A quality's context becoming occurrent can request its
change process. That resolution may commit observations that cause another
consequence. Repeated notification of the same completed request should reuse its
result under the applicable identity/scope/revision rules, rather than create another
observation simply because the callback ran again.

**Role and uncertainty.** A committed process or relationship may confer a role;
that role may imply another consequence. Optional uncertainty resolution for a
quality is another ordinary resolution request. Failure, absence of a suitable
model and successful empty membership must retain their distinct outcomes.

## Integration proposals and prerequisites to verify

These are design considerations, not a promised implementation schedule:

1. Identify the authoritative successful observation/knowledge commit boundary and
   its durable notification/replay semantics. Consequences must not inspect a
   speculative prefix as if it were committed.
2. Bind each request to observation identity, scope, observer, semantic/worldview
   revision and relevant support. Determine which revisions invalidate reuse.
3. Reuse normal resolver admission, strategy ordering and result reuse. Audit where
   existing identity/coalescing guarantees apply and which categories need support.
4. Preserve provenance from triggering commit, ingredients, rule and selected model
   through the consequence's resolution and subsequent commits.
5. Verify incremental processing, duplicate notification, cycles, concurrent commits,
   failure and restart/replay behavior without duplicating the resolver state model.

The initial root commit hook is now selected above. Durable work ownership,
optional-uncertainty policy and exact invalidation rules are not selected here. Nor is a common-bearer algorithm for
mixed dependent emergence. Establish these dependencies before claiming an engine
or assigning delivery commitments.

## Questions for collaborators

- What defines the identity of an emerged configuration, and when does a changed
  ingredient update it, invalidate it or produce a different observation?
- How do boolean emergence clauses, partial ingredient availability, absence and
  removal affect detection and retraction?
- What common-bearer rule is justified when dependent ingredients have different
  bearers, including inherited or specialized bearer types?
- Which identity/scope/revision guarantees bound distinct consequence work, and
  which tests demonstrate finiteness for recursive model-generated consequences?
- What is atomic with the triggering commit, and what is a separately retryable
  consequence? How are failure, cancellation and no-model outcomes exposed?
- Who selects optional uncertainty resolution, and what prevents uncertainty or
  change-process requests from generating repeated equivalent work?
- How should provenance and explanations present conferral, implication and
  detection decisions to users?

## Candidate worldview migration and verification

Earlier source used configuration `of` both for emergence ingredients and for
application targets. Applying the agreed rule requires explicit migration of the
latter, rather than silently changing their meaning. Network ingredients normalize
to `emergesFrom`; profile and Terrain application targets use `applies to`.

The private candidate introduces two named applicability specializations for nested
description targets: `geography:TerrestrialSurfaceArrangement` applies to terrestrial
Region without a Crust restriction, and `geography:LocatedBathymetry` applies to
Location. Elevation's nested spatial-arrangement target uses the former. The nested
`Bathymetry of Location` occurrence belongs to **BathymetricSlope**, whose target uses
the latter; BathymetricDepth's native `Height of Location` remains unchanged.

These are private candidate migration choices; their exact compiler/reasoner evidence
belongs to the implementation report. This design draft does not establish live
detection, contextualization or deployment success. Explicit `emerges from` and `applies to` retain distinct
semantic roles despite the readable configuration `of` shorthand.

Related references: [observables](OBSERVABLES.md), [observation strategies](OBSERVATION.md),
[worldview clauses](ONTOLOGY_LANGUAGE.md#77-emerges-from-and-implies),
[resolution](RESOLUTION.md), [knowledge graph](KNOWLEDGE_GRAPH.md) and the
[agent context pack](../llm/DOMAIN_CONTEXT_PACK.md).
