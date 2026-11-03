# Consequence engine — contracts and implementation

This document specifies the implemented consequence contracts and their verification
boundary. Intrinsic conferral followed by implication has passed a complete live cycle
without a characterization model. Dependent configuration birth and same-identity,
current-time explanatory recomputation have passed a bounded live scenario. These
results do not establish every relationship, network or cessation path.

See [implementation status and continuation prompts](CONSEQUENCE_ENGINE_STATUS.md)
for exact verified scenarios, controlled-only coverage, unsupported cases and remaining
blockers. Parsing, OWL declarations and a successful helper test alone do not prove a
Runtime consequence or scientific model executed.

The [admission follow-up audit and staged repair prompts](CONSEQUENCE_ENGINE_ADMISSION_AUDIT.md)
cover CHANGE repetition, recomputation evidence, birth identities and nested cancellation.

Here, a **commit** means an observation/knowledge transaction commit in a digital
twin, not a Git commit. Consequences operate on observations and their scientific
meaning, not source-control changes.

## Integrity constraints and actionable consequences

`requires` records integrity constraints, not consequence execution. `requires identity`
remains active: legal identity qualification discharges requirement-induced abstractness,
while explicitly authored abstractness remains. The unsupported `requires attribute`
and `requires extent` forms are removed. `implies`, `confers` and `emerges from` record
actionable consequences under the contracts below.
## Agreed semantic contract

The engine runs after each observation/knowledge commit. Its responsibilities are:

| Consequence | Intended behavior |
| --- | --- |
| Configuration DETECTION | Recognize an emergent configuration observation from detected ingredients |
| Change processes | Resolve change processes for qualities when their context becomes occurrent |
| Conferred roles | Apply `confer` axioms for roles conferred by processes or relationships |
| Implications | Resolve targets implied by roles or relationships, and attribute roles implied by configurations |
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
rule is implemented by the normalized consequence-rule and configuration-detection
paths described below. Independent subject/agent/event heads cannot take
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

| Path | Implemented behavior | Verification limit |
| --- | --- | --- |
| Rule source and transport | Normalized authored conferral, implication and emergence evidence with source spans, accepted-generation indexing and retained semantic-cycle binding | Syntax/source evidence is not execution authority |
| Roles and implications | Guarded intrinsic role attribution before optional characterization; ordinary optional resolution for implied targets | Process→role→quality is live verified; relationship endpoints and independent alternative supports lack equivalent full-stack acceptance |
| Configuration discovery | Incremental candidates, positive Boolean matching, guarded native capture, detection placement and prepared single-root explanatory execution | Simple dependent birth/recomputation is live verified; disconnected/cross-cohort and split/merge paths have controlled coverage |
| Cycles and settings | Per-twin explicit-cycle serialization; frozen enablement and semantic view through recursive drain | In-memory ownership is not durable recovery or an authority-dataset snapshot |
| Modeled cessation | Local Data.Builder output intent, execution-bound validation, atomic native deactivation/history, current-view and scheduler exclusion, cohort affects propagation | Controlled native COUNT1→0 is verified; full service-side cessation acceptance remains blocked before testcase start |
| Change processes | Existing resolved semantic writer avoids a redundant change request only with valid perspective/support evidence | General resolve-once and per-timestep model reuse policy remain deferred |

The legacy `emergentResolvables` stub is not the runtime discovery implementation.
The active rule API is `Reasoner.consequenceRules`, consumed through retained semantic
cycles and guarded Runtime handlers. Missing or unsupported evidence defers explicitly;
there is no receipt-free discovery or model-execution fallback.

## Runtime implementation

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

## Configuration discovery implementation

`ConfigurationDiscoveryHandler` delegates to the installed configuration runtime and
backend. The retained Reasoner view supplies complete normalized rules; candidate indexes
limit revisits to affected contexts, bearers and structural components. A rule candidate
is not a successful match. Matching retains Boolean branches, distinct ingredient IDs,
observer scope, actual support and full source/scientific evidence.

The native capture includes exact positive assets and selected structural neighborhoods,
including relevant absence evidence. Semantic relevance is checked before incomplete
support can block a recipe: a proved unrelated unresolved optional observation does not
block complete ingredients, while relevant or uncertain incomplete evidence still defers.
Payload, attribution, phantom and concurrent-update guards remain active for the captured
domain. Detection uses the existing issued-receipt path; read-only matching alone cannot
authorize publication.

Dependent configurations require a common bearer and compatible support. Substantial or
relationship components may include participants from different source cohorts; each
configuration belongs to its own cohort. Disconnected networks produce separate instances.
The detector does not invent an inherency or combine unrelated contexts to complete a match.
Mixed dependent bearers, negative/absence patterns and unsupported structural expressions
remain explicit limits.

After guarded detection, a normal admitted single-root model may explain the configuration.
Preparation retains the actual compiled root and its dependencies; execution receipts and
native read sets are revalidated at atomic publication. A successful match does not mean a
model was found or executed. Input changes recompute at the current modeled instant, update
the same configuration identity and append immutable revision history. Existing historical
support is checked against its immutable before-state rather than against legitimately
changed current input support. Foreign concurrent writes still reject publication.

The [status report](CONSEQUENCE_ENGINE_STATUS.md) distinguishes the actual dependent
birth/recomputation acceptance from controlled network/lifecycle tests. Multi-root
explanations and configuration termination are explicitly unsupported.

## Role conferral and implication contract (November 1)

These contracts supersede the earlier model-mediated conferral design. Their runtime
handlers require the corresponding evidence, attribution and cycle paths; missing or
unsupported evidence remains explicitly deferred. Verification by scenario is listed in the status report;
a parser hook alone does not execute them.

### Conferral

`confers <role> [to <substantial>|source|target] [within <bearer>]` attributes a role
without requiring characterization or a characterization model. For a process with
no recipient clause, use its actual durable substantial bearer. For a relationship,
`to source` and `to target` select that endpoint; omission selects both endpoints.
There is no `both` keyword. Explicit `to <substantial>` resolves `each <substantial>`
through normal resolution and attributes the role to the resulting recipients.
Successful empty membership, incomplete discovery and failed resolution are distinct.
`within` specializes the role-bearing substantial's context of application, not the
declaring process or relationship; it is not a replacement
recipient or permission to fabricate inherency. Roles apply only to substantials.

Use the guarded semantic-attribution transaction with source-rule, observer, support,
recipient and semantic-witness evidence. Retain the existing bearer identities and
normal applicability/satisfiability checks. This authority is distinct from classifier
invocation evidence: do not invent classifier events or bypass classification guards.
Attribution records the source observation, authored rule and cause. A second supporting
source for an already present role must not appear to be a newly acquired role.

A conferral commits attribution first. The generated commit then triggers that role's
implications before optional role characterization. Characterization uses the recipient's
singular scope and cannot undo intrinsic attribution merely because no model exists.
Its directive is resolved through Runtime's guarded semantic-update lifecycle, not registered
as a new observation. Only an explicit Resolver `NO_MODEL` outcome establishes an absent
characterization model; rejected admission, malformed plans and execution failures remain failures.
Never wait for the consequence worker's own queue or execute the same implication both
inline and again after commit. Withdrawal and role lifetime are not inferred here.

### Implication

`implies <target> [for <substantial>] [within <bearer>]` retains three separate things:
its target expression, secondary inherency supplied by `for`, and specialization
supplied by `within`. Native inherency in the target is preserved. In this declaration,
a top-level `for` is the secondary clause; parentheses can preserve a native target
`for` expression. Ordinary observable syntax outside this clause is unchanged.

- A role implies a dependent, relationship or predicate by requesting normal optional
  resolution of the target when the role is attributed. For a consequence-conferred
  role, attempt the implications before characterization of that role.
- A configuration implies a role on selected substantial participants. Without `for`,
  only a dependent configuration's actual bearer is a default. A configuration of
  substantials has no such bearer and needs an explicit recipient. Never assign a role
  to a dependent merely to make this route executable.
- A relationship may imply model-resolved dependents, relationships or predicates when
  inherency is unambiguous. `for` must identify the intended bearer where the
  relationship context alone is ambiguous. Relationship role attribution belongs to
  `confers`; it is not a second implicit attribution route through `implies`.

These are operational consequences, not calls to the old unimplemented role-closure
methods. All normal access, scientific dependencies, support and semantic checks remain.

### Emergence and incremental evidence

`emerges from <ingredients> [within <context>]` detects a configuration under its bearer
for dependent ingredients, or across the relevant context for substantial ingredients.
The configuration belongs to its own cohort, linked to its configuration observable;
connected participants may belong to different source cohorts. Preserve authored
Boolean expressions and distinct observation identities. Detection precedes resolution
of the configuration; the resulting configuration may imply roles. Models do not
instantiate configurations. A `within` restriction specializes the emergence context.

Build semantic candidate indexes for accepted rule generations, then use changed
observations, attributions and structural links to revisit only affected bearer/cohort
partitions. Keep supporting observation IDs, support and rule/semantic evidence so
changes can invalidate cached matches. A one-time partition initialization is distinct
from a global rescan after every commit. A source candidate is not a successful match;
unavailable proof, unsupported matching and a proven no-match must remain distinct.

Participant changes update the same configuration observation, preserving immutable
history. Legitimate modeled cessation of a participant or relationship triggers the
configuration's explanatory model through its affects dependencies; it is not an
integrity failure and does not cause generic structural retraction or deactivation.
Later worldview definition mismatches do not retroactively rewrite the twin. New
requests still require ordinary admission, access and semantic guards. Disconnected relationship networks form separate configuration
instances. Match each connected component independently and preserve its identity as
members change. On split, retain one existing identity and create the additional components; on merge,
choose one continuing identity while preserving the others and their history. A stable
deterministic arbitrary choice is acceptable; record identity lineage and participant
changes. Configurations persist even when no composing relationships remain. Explicitly
reject unsupported configuration termination models; do not add termination semantics.

An input-change recomputation runs the newly admitted explanatory root at the current
modeled time and appends a revision without advancing time or creating a named event.
Its private preparation retains the exact compiled root, including valid roots with
no direct computation. Scheduler still traverses ordinary prerequisites; their current
knowledge and existing initialization/reuse rules are preserved. This does not claim
that every upstream model is refreshed for each input revision. General resolution
reuse remains a separate policy discussion. Multiple explanatory roots are explicitly
unsupported in this initial implementation.

Modeled cessation requires authentic execution-bound output and retained native beforeimage.
Local components may call `Data.Builder.cease(target)`; the builder records intent, not
authority. Successful typed/adapter output transfers that intent through its actual owning
execution and transaction. Arbitrary metadata, copied IDs, unexecuted inputs, foreign scopes,
failed outputs and configuration targets cannot authorize removal. Remote builders do not
yet implement this output contract and reject it explicitly.

The native commit excludes ceased substantials, their dependent consequences and involved
relationships from current lookup/reference/enumeration and scheduling, while preserving
historical state and provenance. Surviving cohort-dependent computations receive membership
change propagation; the controlled actual COUNT path preserves INIT1 and writes current0
without advancing modeled time. Unrelated survivors remain accessible. Independent alternate
role-support behavior is not established by that result. Full service-side cessation has not
passed acceptance; see the status report. No named events or `on termination` semantics are
introduced, and configuration termination remains rejected.

### Configurations and assemblages

Detection does not instantiate an assemblage subject from the co-occurrence of its
component subjects. First detect their configuration or network through explicit
structuring properties. A model resolving that configuration may instantiate an
assemblage through the ordinary, guarded instantiation pathway. The configuration
records detected organization; the assemblage is a model-produced substantial.
## Frozen resolution/contextualization cycles

Consequence enablement and semantics are pre-contextualization choices. Capture them
at a resolution/contextualization cycle's start and retain them through its ordinary,
queued and recursively generated consequence work. Changing the Runtime setting or
publishing a worldview must not drop or reinterpret an already admitted chain. Later
cycles can observe those changes after the prior cycle's consequences are handled.
`DISABLE_CONSEQUENCE_ENGINE` defaults to false; a disabled cycle still permits ordinary
explicit resolution/contextualization, while scheduling no automatic consequences.
This is not a whole-twin immutable setting and is separate from explicit shutdown.

Use retained accepted semantic actors and cycle-owned lifetime references, not cached
rule DTOs or a current-epoch comparison alone. Scope copies and remote requests must
preserve cycle identity. Unknown, closed or wrong-owner semantic handles must fail
explicitly, never fall back to current semantics. Keep existing authority, admission,
source, access and coverage guards inside the selected view. A retained logical actor
does not guarantee a snapshot of an external mutable authority.

Within each digital twin, explicit requests wait for the active cycle and all its
consequences to drain, then begin a fresh frozen cycle. Causal child submissions inherit
their parent's cycle and must not queue behind it. This does not serialize unrelated
twins or supported internal model evaluation. Cycle ownership must outlive subscriber
cancellation until actual work and queued consequences drain. In-memory ownership does
not imply crash recovery, durable replay or migration of existing observations.

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

## Remaining integration and verification boundaries

These checks constrain continuation; they are not evidence that the unverified scenarios pass:

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

The root commit hook and in-memory cycle ownership are specified above. Durable
restart/replay ownership, optional-uncertainty policy and negative/absence detection
remain separate work. Mixed dependent emergence needs complete common-bearer evidence;
unsupported or incomplete evidence must defer rather than manufacture a configuration.

## Questions for collaborators

- Split/merge continuity uses a deterministic economic identity choice. A split retains
  one existing identity and creates additional components; a merge retains one continuing
  identity while preserving other configurations and their history, including empty ones.
  Identity choice has no additional semantic meaning. Configuration termination models
  remain explicitly unsupported.
- Which negative/absence patterns are supported beyond positive Boolean emergence?
  Incomplete evidence defers; modeled participant cessation recomputes the existing
  configuration rather than causing generic structural retraction.
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

## Native validation captures and durable audit references

A live configuration attempt retains its complete, detached native read sets through
semantic admission, scientific execution and atomic publication. These captures include
opaque metadata and structural absence evidence; every native guard still validates the
original full objects. Audit JSON never recreates or authorizes a native capture.

Configuration source and affects receipts serialize a versioned audit reference for each
such capture: context, asset IDs, selected neighborhoods, native edge IDs, and SHA-256 over
the complete capture's UTF-8 JSON under the normal k.LAB mapper. Hashing streams the full
capture, including metadata and previous history, without allocating another JSON string.
The original in-memory capture remains unchanged. A reference is audit data, not executable
proof, a content store, or a promise that the raw capture can be reconstructed from it.

Only opaque concurrency-capture payloads are omitted from receipt serialization. Recipe,
query, source response, model-plan, semantic request/witness, placement and prior-content
hash fields remain. Participant/support revisions, execution provenance and existing
immutable historical rows are not rewritten. Source and temporal artifacts retain their
ordinary history contracts; an audit reference must never stand in for missing execution
or scientific-input evidence.

Embedding full captures in their own audit would re-embed prior configuration authorities
and history on each revision. The resulting finite JSON tree amplifies through repeated
escaping; it is not a geometry expansion or a Java object cycle. The audit projection
prevents this feedback without changing parser limits, global observation transport,
semantic admission or native concurrency guards.

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
