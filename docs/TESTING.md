# Testing k.LAB with k.Actors

k.Actors testcases exercise running k.LAB services and the assets they produce. Import
`core.inspector as inspector` to turn captured assets into assertion predicates. The Inspector
is a static actor: it needs no `new` call. Its functions inspect the supplied snapshot, return
ordinary values, and can be used in assertions, conditions, or diagnostic output.

See [the k.Actors guide](AGENTS.md) for the full language and Java actor contract. The implementation
is `CoreActorLibrary.Inspector` in `klab.core.services`. The project
`klab.staging.vxii`, in `testcases/klab/staging/vxii/testsuite.kactors`, demonstrates submitting a
substantial observation followed by a quality and asserting `inspector.viable(elevation !nodata)`.

## Testcase lifecycle and reporting

Declare `testcase`, a description, imports, and actions annotated with `@test`. Initialization and
an optional `main` run first. Local tests then run in declaration order; supplier tests are awaited
before the next test starts. `with properties {parallel: true}` opts into concurrent tests with
shared agent state and separate action scopes. Prefer the default sequential execution unless
concurrency is part of the test.

```kactors
testcase example.inspector
    "Checks captured observation results"
    version 1.0
    using core.context as context,
          core.inspector as inspector,
          core.console as console

@test(name="Region identity")
action region_identity:
    ctx <- context.new
    region <- ctx.submit('staging.vxii.basic.regions.france')
    assert inspector.viable(region)
    assert inspector.contains(ctx, region)
    console.println(inspector.problems(region))
```

The example needs the named observation's project and worldview installed. Substitute an available
observation URN or use the inline observation definition in the staging testcase. A `ctx.submit`
assignment waits for the supplier result; inspect the returned observation after that wait. For
concurrent submission groups, use `then` before inspecting their captures.

A supplier used in value position (for example `observation <- ctx.submit(...)`) is awaited
locally and does not by itself turn the enclosing test action into a supplier. A test consisting
of awaited assignments and assertions completes when its body finishes; it needs no explicit
`return`. This also applies to awaited local actions. Reactive supplier calls and reactive returns
retain their asynchronous lifecycle.

An `assert` records one result through the testcase runtime. A failed assertion ends its test
action; subsequent test actions still run. The testcase can terminate normally while its report
contains failures, so an automated gate must inspect report outcomes, not just agent termination.
The report includes test/assertion outcomes and counts. Exceptions during assertions are recorded
with their stack traces. Trailing `:success` and `:fail` metadata on an `assert` statement supply
the description for each assertion it contains. Only the message corresponding to the actual
outcome is evaluated, after comparison, in the assertion's lexical scope. Expression-valued
messages can therefore refer to local values without evaluating the unused branch. An individual
assertion's metadata overrides the enclosing statement's message when present.

The selected non-null message is stored as the assertion report entry's `description`, and the
IDE displays it beneath the assertion source. With no selected message, that field is omitted.
Evaluation exceptions use `:fail` just like mismatched values and retain their original stack trace.
If evaluating a message itself throws, the assertion outcome is preserved and the separate
`messageError` field contains the formatting error's stack trace; the IDE shows it in a collapsible
detail pane. Report generation never reevaluates the assertion to obtain a message.

```kactors
assert inspector.complete(elevation)
    :success "Elevation contains no missing values"
    :fail "Elevation is incomplete or its data evidence is unavailable"
```

Inspector functions do not create assertion entries themselves: calling one inside a condition
must not increase the assertion count. `viable` prints its failure reasons through `core.console`;
in a testcase these also become action-scoped console entries. `problems` returns the same reasons
without printing. For explicit diagnostic output, use `console.println(inspector.problems(asset))`.
Unexpected backend exceptions propagate instead of masquerading as an ordinary false predicate.

Testcases own a session. Contexts created by `core.context.new` in a test are registered for
cleanup; one-off contexts are closed when the test scope is disposed. Choose persistent contexts
deliberately if their artifacts must survive the run. Client code must attach report and console
listeners before starting a finite testcase, or it may miss its earliest events.

## Viability

`inspector.viable(asset, ...)` requires every supplied asset to pass. No arguments, any null asset,
empty collections, unsupported types, and cyclic asset collections fail. Collections are checked
recursively. `inspector.problems(asset, ...)` returns an immutable list of failure descriptions,
including the position of each failed asset; an empty list means success.

| Asset | Default viability check |
| --- | --- |
| Observation | Nonempty; observable present; nonempty geometry of positive size; no error notifications. Qualities, processes, and relationships also require positive resolved coverage. |
| Substantial observation | Same structural checks; resolution and storage are not required by default. A newly created region can therefore be viable before dependent resolution. |
| Activity | Outcome is `SUCCESS`; unset, `FAILURE`, and `INTERNAL_FAILURE` fail. |
| Actuator | Type and target observation present; child actuators structurally viable and free from cycles. A reference actuator need not contain computations. This is structural validity, not proof of execution. |
| Dataflow | Nonempty with at least one actuator, no error notifications, and structurally viable actuators. A dataflow's optional coverage geometry is not required. |
| Context actor, context scope, digital twin | Knowledge graph is present and online. |
| Knowledge graph | `isOnline()` is true. This is connectivity, not an integrity audit or a count assertion. |
| Storage, storage shard, histogram | Contains at least one valid value, unless an explicit data policy below replaces this default. |
| Geometry | Nonempty with positive size, including scalar geometry. |
| Captured JGraphT graph | Contains at least one vertex. Use `acyclic` separately when cycles are forbidden. |

Viability does not require a durable positive asset ID. Transaction-local assets legitimately have
ID `-1`; commit membership is a separate check. Other runtime asset kinds currently fail with an
unsupported-type diagnostic rather than passing merely because they are non-null. Use `present`
when existence is the only intended assertion.

### Policies and defaults

Pass policies as inline call metadata, **inside the call's parentheses**, as in the staging example.
They apply to every supplied quality, storage, shard, or histogram; data policies do not impose
storage on substantial observations. Unknown keys and malformed values throw an argument error.

| Policy | Requirement |
| --- | --- |
| No data policy | Observation viability does not inspect data. Direct storage/histogram viability requires some valid data. |
| `+data` | At least one valid value across the captured slices. |
| `+nodata` | Nonempty evidence containing only no-data in every captured slice. |
| `!nodata` | At least one valid value and no missing values in every captured slice. |
| `!data` | Same as `+nodata`: nonempty evidence with no valid values. |
| `!resolved` | Require positive resolution coverage even for a substantial observation. This preserves the original Inspector stub's spelling. |
| `+resolved` | Default resolution policy: require coverage for dependent observations only. |
| `:mincoverage 0.95` | Require positive coverage of at least 0.95, including for substantials. Finite range 0..1; zero still requires a positive result. |

Combined policies are conjunctive: `+data +nodata` cannot succeed. Coverage must be finite and at
most 1; NaN or an invalid fraction fails. Missing evidence never proves all-no-data. This matters
when a remote observation has no histogram snapshots or when a histogram is unavailable.

```kactors
assert inspector.viable(elevation +data)
assert inspector.viable(elevation !nodata :mincoverage 1)
assert inspector.viable(masked_quality +nodata)
```

### Data evidence and limitations

Scalar observations use their literal value when available. Zero and false are data; numeric NaN
is no-data. Infinities count as numeric data, consistently with storage, but cannot pass finite range
checks. Non-scalar observations use
their timestamp-keyed histogram snapshots, storage uses its merged histogram, and shards use their
own histogram. These functions do not allocate scanners, sample randomly, or trigger resolution.
They also do not silently fetch storage from whichever context happens to be current.

Runtime storage histogram snapshots now preserve missing counts: NaN values excluded from the
numeric histogram are counted against the shard's cell count. This applies to finalization,
restoration, temporal snapshots, and merged snapshots. An all-NaN histogram can be empty of valid
bins while still reporting a positive missing count. Null/unavailable histograms are different.
Older serialized snapshots with omitted missing counts cannot establish the same completeness
guarantee; regenerate them before using strict data assertions.

The checks cover the snapshots supplied, not future scheduler events or uncaptured timestamps.
Inspect only completed runs and quiescent snapshots: inspecting storage while it is being written
does not establish a transactionally consistent result. Integer and boolean storage currently have
no separate missing-value mask; Inspector cannot invent one. Categorical histogram/data-key
completeness remains a proposal below.

## Available assertion functions

All verbs are functions. Predicate mismatches return false; invalid options or unsupported accessor
arguments throw. Accessors preserve backend failures, including authorization failures.

| Function | Result |
| --- | --- |
| `viable(assets...)` | Boolean conjunction of the policy checks above, printing failure reasons. |
| `problems(assets...)` | Viability failure descriptions with no console side effect. |
| `present(asset)` | Non-null check only. |
| `resolved(observation, minimum)` | Positive coverage at least `minimum`; optional minimum defaults to zero. |
| `hasdata(asset)` | At least one valid value. Same data predicate as `+data`. |
| `nodata(asset)` | All values no-data with positive evidence. Same predicate as `+nodata`. |
| `complete(asset)` | No missing values and some valid data in every captured slice. |
| `inrange(asset, minimum, maximum)` | All numeric extrema or scalar values within inclusive finite bounds. Empty/unavailable evidence fails. Missing values are ignored; combine with `complete` if they must fail. |
| `metadata(observation, key, expected)` | Key exists and its value is deeply equal to `expected`. A missing key differs from an explicitly null value. No numeric/string coercion. |
| `graph(context)` | Knowledge graph from a context actor, context scope, digital twin, or graph itself. |
| `contains(context, assetOrIdOrUrn)` | Committed graph membership via the supplied authorized context. Accepts runtime assets, Long/Integer IDs, strings, or URNs. Named URNs use direct URN lookup; transient IDs never count as committed membership. |
| `linked(context, source, target, relationship)` | Outgoing relationship exists, including transaction-local links when the backend exposes them. Compares committed IDs or transient identities rather than treating all `-1` IDs as equal. |
| `storage(context, observation)` | Existing storage from the explicit context's storage manager. Backend failures, including absent storage, propagate; it does not create test storage. A null observation returns null. |
| `vertices(graph)` / `edges(graph)` | Counts in a captured JGraphT graph. |
| `acyclic(graph)` | Directed graph has no cycle; empty directed graphs pass. Undirected graphs are rejected. |

`context` in membership, relationship, and storage functions must be a `core.context` instance or
a `ContextScope`, so the lookup always has an explicit authorization and transaction scope.
Storage lookups require a manager that supports local access; remote storage retrieval is not
promised by this helper.

```kactors
assert inspector.resolved(elevation, 0.95)
assert inspector.inrange(elevation, -500, 9000)
assert inspector.complete(elevation)
assert inspector.contains(ctx, elevation)
assert inspector.linked(ctx, mainobs, elevation, HAS_CHILD)
```

Use an actual relationship from the digital-twin graph model for the final check. `HAS_CHILD` is
appropriate only when the source and target are related that way; resolution dependencies need
their own relationship. When a Java verb parameter is an enum, a k.Actors constant is matched to
the enum's declared `name()` ignoring case. Strings use the same conversion. Matching does not
use an overridden `toString()`, remove punctuation, or strip qualification. Unknown and ambiguous
names fail; already typed enum values remain unchanged. Source syntax still determines whether a
token is a constant or a variable (use the grammar's constant form, such as `HAS_CHILD`). Static
validation and runtime binding share this conversion.

General scalar comparisons remain ordinary k.Actors assertions, for
example `assert [resubmitted.id == original.id]`.

## Resolution graphs and further assertion proposals

The resolver's `ResolutionGraph` belongs to `klab.services.resolver`, while the Inspector belongs to
the shared services core. Its internal graph is not currently a remotely captured public asset.
Introducing a dependency from the core to the resolver would invert that layering. If a local
integration explicitly supplies `resolutionGraph.graph()`, the existing `vertices`, `edges`,
`acyclic`, and structural `viable` functions work on that JGraphT graph. These checks alone do not
prove successful semantic resolution. The public `Dataflow` and observation coverage checks are
available now for resolution results.

The following are **documented API stubs/proposals, not callable verbs**. They deliberately have
no placeholder implementation that returns success. Each requires the stated decision before
being exposed through the actor catalog.

| Proposed contract | Required decision and intended implementation |
| --- | --- |
| `capture(context, kind, target)` | Define a public immutable capture envelope with context, transaction, asset URN, timestamp, and capture phase. Choose which resolver/scheduler events retain snapshots, who may retrieve them, and how long they survive. |
| `resolution(capture, expectations)` | Define a service-neutral resolution graph DTO: nodes, selected/rejected strategies, edge coverage, constraints, and failure reasons. Then check target coverage, required dependencies, strategy selection, and absence of forbidden dependencies. Distinguish a successful reference-only resolution from an empty failure. |
| `consistent(graph, rules)` | Choose relationship-specific integrity rules and authorized scope: parent cardinality, provenance completeness, orphan handling, and cross-context links. Implement scoped typed queries over a stable commit snapshot; do not assume all graph relationships form a DAG. |
| `values(asset, expected, tolerance)` | Decide cell alignment, units/mediation, absolute versus relative tolerance, missing-value equivalence, and comparison of categories. Use read-only scanners with explicit event and geometry selection; provide mismatch positions and bounded diagnostic samples. |
| `distribution(asset, expectations)` | Define exact statistics versus approximate histogram statistics, missing denominator, and supported categorical keys. Do not derive exact quantiles or means from the current coarse transport bins. |
| `events(asset, expectations)` | Define required scheduler timestamps and whether initialization is included. Validate missing, duplicate, and out-of-order states against the capture envelope rather than assuming the existing histogram keys are a complete history. |
| `eventually(predicate, timeout, interval)` | Decide polling versus event subscription, consistency boundary, cancellation, and timeout reporting. Return a supplier that joins the testcase lifecycle and records one final assertion, with bounded diagnostics. |
| `notifications(asset, expectations)` | Choose matching by stable code, severity, source, or text, plus treatment of warnings and expected failures. Extend beyond the current viability rejection of error notifications. |
| `viable(plan/agent/cohort/commit, policy)` | Specify what each asset's success means beyond existence. Plans may be valid before execution; agent completion alone does not imply test success. Add explicit type-specific evidence contracts. |
| Submission test gate | Decide which project submissions trigger tests, whether `<project>.testsuite` is mandatory, service/worldview fixtures, budgets, concurrency, and blocking versus advisory failures. Run tests against the proposed project revision and use report outcomes, not agent status, to accept or reject. Preserve reports with revision and environment identifiers. |

Until submission gating is wired into the project lifecycle, submitting a project does **not**
automatically guarantee its testcase ran. The staging suite's naming convention is a proposal in
that project, not an implemented universal gate.

## Regression verification

For runtime resource-admission scenarios, see [Runtime shard execution limits](SHARD_EXECUTION.md#executable-scenarios).
They cover shared limits across observations, FIFO waiting, live configuration, cancellation,
failure recovery, storage readback and temporal execution through existing executor paths.

Focused Java tests cover asset viability, the three data policies, scalar and temporal evidence,
graph membership and transaction-local link identity, graph structure, verb descriptors, and
histogram missing-count transport. The runtime invocation regression exercises inline metadata
delivery to `Inspector.viable`; the existing runtime tests cover assertion reporting and action
lifecycle behavior.

From the repository root in PowerShell:

```powershell
.\mvnw.cmd -pl klab.core.services -am '-Dtest=CoreActorLibraryInspectorTest,JavaArgumentConversionsTest,RuntimeAgentBaseTest,BehaviorAnalyzerTest,HistogramUtilsTest,StorageReconstructionTest,CoreActorLibraryContextTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

These are contract tests, not a live staging-project run. A release check must additionally run the
staging testcase against its configured resources, reasoner, resolver, runtime, and knowledge graph,
verify the expected report, and check cleanup. UI presentation, remote transport, scheduler capture,
and submission gating require their own integration evidence.

## Default observer preparation regression tests

Run the metadata, group, identity, settings-storage and observer preparation contracts with:

```powershell
mvn -pl klab.core.services,klab.services.resources,klab.modeler -am '-Dtest=DefaultObserverTest,DefaultObserverPreparationTest,ProjectSettingsIOTest,ProjectSettingsSaveTest,ResourcesProviderSubmissionTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Observer scope/transport and embedded persistence regression tests:

```powershell
mvn -pl klab.services.runtime -am '-Dtest=ObserverScopeTest,ObserverGeometryViewTest,PerceivedGeometryPersistenceTest,DigitalTwinCommitTest,ClientKnowledgeGraphTest,RuntimeServiceQueryTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

The persistence tests use a fresh embedded Neo4j database, including concurrent writers, rollback,
spatial replacement, preserved time, and stale/cross-twin edit rejection. The view tests check
WGS84 reprojection and absent spatial geometry. In `klab-ide`, run
`mvn '-Dtest=ObserverCardTest,TreeModelTest,ObservationDropTargetTest' test` for edit baselines,
viewport validation, observer visibility and geometry previews. See [Default observers](OBSERVERS.md)
for live connection checks and remaining policy, advanced editing and semantic-query work.

Workspace settings regression checks:

```shell
mvn -pl klab.services.resources,klab.modeler -am -Dtest=WorkspaceSettingsTest,ProjectSettingsSaveTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`WorkspaceSettingsTest` checks owner/admin authorization, read-only ACL membership, missing-owner
workspaces, catalog failure, structured metadata, unchanged fields and preservation of ownership,
project membership and service grants. IDE `WorkspaceSettingsEditorTest` checks audit/edit access
and settings request payloads; these tests do not exercise a live JavaFX session or HTTP service.

Manifest settings regression coverage: `ProjectSettingsIOTest` exercises legacy migration,
structured metadata, exact file rollback, malformed files, ignored paths and a real temporary
JGit repository through the normal commit operation. `ProjectSettingsSaveTest` checks the
administrator-only worldview declaration and contributor-only observer changes. The IDE
`ProjectSettingsEditorTest` checks the corresponding visibility and access predicates.

Observer startup geometry regression checks:

```shell
mvn -pl klab.services.runtime -am -Dtest=GeometryAndCurvesTest,DefaultObserverPreparationTest,GeometryRepositoryTest,ObserverUniversalGeometryTest -Dsurefire.failIfNoSpecifiedTests=false test
```

These cover real UNIVERSAL default-observer preparation, the distinct `1` scalar / `*` universal /
`X` empty encodings through runtime scales and repository reads, and replacement of initial
universal perceived geometry by the first concrete observation. They do not prove a complete live
resolver-to-graph-to-ObserverTree round trip.


## Storage mediation harness (S3–S4)

[storage.kactors](testcases/klab/staging/vxii/storage.kactors) is a ready-to-copy testcase for
`klab.staging.vxii/testcases/klab/staging/vxii/storage.kactors`. The staging project was not present
in the accessible checkout, so this repository holds the source. It creates a small rectangular
projected grid, submits elevation within that region, checks committed membership, and compares
three traversal/partition requests. It requires a worldview defining geography:Region and
geography:Elevation and a working elevation resolver/resource. Those live dependencies are
intentional; this is a full-stack harness, not a synthetic unit test. A rectangular grid keeps the
case within S2 conformance; an administrative polygon would require the later masking/resampling
stage. Run it with parallelization enabled and disabled to cover both native attribution modes.

New static inspector functions:

- `scancheck(context, observation, curve, splits, samples)` compares values and validity at bounded
  sample locations in an adapted partitioned view, a single export traversal, and indexed text
  reads. It checks cursor positioning and unchanged source layout/semantics. Curve is an enum name
  such as `"D2_YX"`. Limits are 1–256 partitions and 1–64 samples per partition. This checks the
  integration of shared planner routes; independent numeric/grid oracles are covered by JUnit.
- `celltext(context, observation, curve, offset)` returns locale-independent primitive text, with
  `null` as the missing-data string. Zero/false and exact long values retain their meaning.

These functions require a captured quality and an explicit context, use initialization for at-most-one-state
time support, close all sessions, and propagate backend failures with their cause. They do not
create assertion entries; use `assert inspector.scancheck(...)` as in the testcase. The current
sample also verifies meters-to-millimeters through `unitcheck`; it does not exercise remote HTTP or live temporal events. Java tests
cover revision selection and transaction snapshots, stream ownership, point API behavior, mixed
primitive dependencies, pre-write failure and detached source binding.

The repository parser test `StorageHarnessSyntaxTest` reads the actual testcase file. Inspector
catalog/argument descriptors and Java invocation are checked by `CoreActorLibraryInspectorTest`
and `StorageConsumerIntegrationTest`. Full-stack execution remains a manual acceptance step;
inspect assertion reports and console output, not only normal testcase termination.

S4 adds `unitcheck(context, observation, unit, factor, offset)`. It samples at most 64 locations,
checks the supplied independent affine expectation, compares partition/export/cell-text routes,
and checks that native semantics/layout and missingness remain unchanged. The testcase invokes
`assert inspector.unitcheck(ctx, elevation, "mm", 1000, 0)` after computing elevation in meters.
For the first-phase acceptance run, execute the testcase with parallelization both enabled and
disabled. The deterministic JUnit suite additionally covers Celsius/Kelvin, bounded/open ranges,
fixed currency quotes, two bindings from one source, temporal snapshots, provenance JSON recovery,
transaction failure and zero per-value allocation. Live provider selection, complete HTTP/service
integration and live Neo4j restart remain full-harness checks; no full stack was launched here.

## Canonical monthly process case

The staging project adds `monthly_process` to its existing
`testcases/klab/staging/vxii/testsuite.kactors`. It uses the same Ruaha geometry and daily 2014
scope as `staging.vxii.test.process.basic.testregion`, with resolution constrained to that
namespace. Keep the namespace's monthly Erosion model unchanged: its Elevation dependency has
the model-local alias `elevation`, and each transition subtracts 10 metres. The initial terrain
is random, so no absolute starting elevation belongs in this test.

The generic inspector assertions are:

```kactors
simulation <- ctx.focus(:within region)
assert inspector.schedulecheck(simulation, process, 1, "MONTH",
    "2014-01-01T00:00:00Z", "2015-01-01T00:00:00Z", 12)
assert inspector.relativecheck(simulation, process, elevation, 12, -10, 0.00000001)
assert inspector.processprovenance(simulation, process)
```

`schedulecheck` checks the persisted process plan, its single model cadence and context bounds,
then reads successful process creation activities carrying durable transition envelopes. Event
and commit IDs must be unique; the twelve supports must be contiguous calendar months covering
the year. Calendar months are advanced natively, never approximated by a fixed millisecond span.
The check intentionally supports one journal per process transaction; other history shapes fail
explicitly rather than being interpreted as the same contract.

The occurrence registration is authoritative. Its portable plan must identify the supplied
process and agree with the registered cadence and context bounds. Every journal must match its
registration ID, plan revision, context, actual observer and physical support. An unrelated
ordinary prerequisite plan cannot establish process history.

`relativecheck` opens read-only planned scans at INIT and at every committed transition. It
compares all covered positions and missingness, verifies that the quality is among each journal's
changed assets, and checks `value[n] = value[INIT] + n * delta`. It closes every scan. The example's
absolute tolerance is 1e-8 metres, far below the generated terrain's meaningful precision; a
minimum of four double ULPs accommodates representation rounding at large magnitudes. At least
one finite covered value is required, so an empty or entirely missing fixture cannot pass.
Each value read also requires the exact event-keyed quality revision, committed quality delta
and matching native shard identities. Equal timestamps or a fallback to INIT are insufficient,
including when the requested expected change is zero. Unchanged writes may be omitted by the
runtime; this assertion intentionally requires actual committed quality revisions.

`processprovenance` distinguishes a process's existence record from contextualization. The one
INITIALIZATION creator must have no execution geometry or scheduler invocation times; actual
observer selection must agree with the root and with graph links on every creator. For CREATED
bindings, committed matching qualities may have no INITIALIZATION creator or timestamp-zero
storage descriptor. This is a read-only graph inspection: it never calls the native INIT storage
accessor, which may allocate a buffer. The Erosion fixture has no CREATED binding, so this last
condition is exercised separately by `TemporalProcessIntegrationTest`. No provenance query can
prove that an unrecorded executor call did not happen: `OccurrenceExecutorTest` explicitly
verifies that the process-existence path never obtains a computation builder, invokes the
scheduler or allocates quality storage.

These are assertions, not scheduler controls: they inspect completed committed history without
advancing time, submitting work or changing provenance. Invalid or unavailable evidence throws;
value or contract mismatches return false. Use the testcase assertion report as the result of
live execution; a compiled behavior or a normally terminated agent alone is insufficient.

## Classification and relationship cases

Submission applies `:namespace` and `:project` to the selected context before interpreting
semantics or selecting models. `:within` selects a focus; `:source` and `:target` must be supplied
together and cannot be combined with `:within`. Unknown options and invalid types fail before
submission. A private model requires an explicit applicable lexical scope rather than a
visibility bypass. Pending relationship participants travel together with their complete
geometry so the Resolver can interpret the transaction's endpoint scope before commit.
Selecting a non-null `within` focus replaces inherited endpoint focus and its pending snapshots.
At the Java scope API, `within(null)` retains its separate role of clearing the ambient context
while preserving endpoints; selecting `between` retains the surrounding network context.

Ordinary fixture roots use `!observer` to avoid default-observer creation. The flag does not clear
an inherited or explicit observer. The monthly process case retains its real observer so its
provenance checks remain meaningful. The separate `observer_instantiation` case submits a
concrete singular Agent, selects it with `:observer`, and verifies canonical identity reuse and
successful submission/resolution evidence through `observercheck`. This asserts creation and
resolution of an observer; it does not imply computational initialization.

The staging suite creates a plain `each earth:Region` producer through ordinary `ctx.submit`.
Classification directives use the separate native testcase capability:

```kactors
support <- region.geometry
classification <- ctx.focus(:within region)
before <- inspector.membersnapshot(classification, regions)
receipt <- inspector.apply(classification, {{earth:PhysicalEnvironment of each earth:Region}}, support
    :namespace "staging.vxii.test.classification" :project "klab.staging.vxii")
commit <- receipt.commit_id
assert [commit > 0]
assert inspector.classificationcheck(classification, before, "earth:PhysicalEnvironment")
```

`inspector.apply` requires an actual TestCaseScope before context, option or semantic processing,
and a native runtime implementing `RuntimeService.Testing`. The bounded capability currently
accepts CLASSIFICATION directives only. It executes the existing UPDATE path
with calling-agent provenance and returns an OperationReceipt (context, commit, activity and
directive). It has no RuntimeClient implementation or HTTP endpoint. Production
`ContextScope.submit` keeps its observation-in, observation-out contract. Explicit support geometry
is required: focus alone is not an operation geometry. Capture `membersnapshot` before each apply,
including a repeated directive whose successful no-op must commit a fresh audit.

`classificationcheck` requires at least one previously unclassified member, preserves the exact
member IDs, URNs and geometry, and checks concrete predicate assignment against a fresh complete
audit and its own successful CLASSIFIED attribution edges. Existing classified members are
allowed. Fractional, duplicate, missing or foreign member IDs do not establish an audit.

`classificationnoop` verifies successful repeated classification through explicit EXISTING audit
results for every captured member, with unchanged meanings, geometry and attribution. Absence
of changes alone is not proof of a successful no-op. The separate concrete-self case requests
`any earth:Terrestrial of each earth:Region` in a fresh context, so it does not conflict with
random Aquatic assignments from another test. Initial and generated cohort sizes are not fixed.
The bare Region resource uses `{fraction: 1, xdivs: 3, ydivs: 3}` to avoid an empty random
producer making mutation assertions vacuous. A subdivision step is at most two thirds of
the envelope width on each axis, guaranteeing at least one populated cell while keeping
the canonical classification case small. Polygon coordinates remain random.

The stream connection case checks actual native cohort members and current committed endpoint
assets, rather than stale objects stored in link references. Endpoint meanings and relationship
axes come from the worldview. Directed roles are verified through the typed source/target links
and their endpoint meanings. The two-coordinate line must connect those exact points in either
geometric order: shared shape serialization normalizes LineStrings and may reverse them.
Geometry therefore cannot independently prove the original generator's direction when both
endpoint roles have the same meaning. Bonds match endpoint roles by Concept meaning, not
numeric ID order. Self-links and duplicate ordered pairs are rejected. The inspector currently
supports single-point endpoints; it fails explicitly for unimplemented area/line endpoint
geometry contracts. The random point and relationship generators do not warrant fixed counts
or hard-coded endpoint pairs.
The canonical point resource explicitly uses `{fraction: 1, xdivs: 10, ydivs: 10}`. Its fraction
is a probability, while the relationship contextualizer's `fraction = 20` is a percentage.
Populating every generated cell removes the default probability of an empty endpoint pool.
Each subdivision increment is bounded by twice its nominal width, so ten divisions per axis
provide at least four intervals on each axis. The resulting endpoint pool supports at least
three sampled sources and targets at twenty percent, ensuring a nonself target is available.
The test still compares actual members and endpoints rather than asserting generated counts.
The collective dependency producer is selected with
`ctx.query({{each earth:StreamConnection}} :target region :along "AFFECTS")`.
Its members belong to the linked native cohort; it is not a `HAS_CHILD` observation of the root.

## Rectangular scanner and geometry oracle

The storage example is newly exercised coverage. Its terrain provider is private, so the quality
submission explicitly selects `staging.vxii.basic` and project `klab.staging.vxii`. Elevation also
requires a Terrestrial Region under the worldview's complete bearer/applicability constraints;
a bare Region does not prove that requirement. These fixture choices retain strict visibility
and semantic admission.

```kactors
assert inspector.gridcheck(ctx, elevation,
    "S2(5,4){proj=EPSG:3857,bbox=[200000 200500 6000000 6000400]}")
assert inspector.unitcheck(ctx, elevation, "mm", 1000, 0)
```

The expected rectangle is 500 by 400 metres, with 5 columns, 4 rows and 100 metre cells. Its
southwest centre is (200050, 6000050), and northeast centre (200450, 6000350). `gridcheck`
compares canonical extents, CRS and dimensions, decodes committed native INIT shards using
independent explicit curve formulas, and compares all covered values, validity, coordinates and
physical cell footprints in all requested consumer traversals/partitions. XY traverses x outside
y; YX traverses y outside x; inverted-Y reverses y with x outside. Grid y=0 is south. It never
calls a native INIT accessor that could allocate storage. Missing or duplicate descriptors,
incorrect geometry/value selection and nonfinite evidence fail. This oracle is distinct from
`scancheck`, which checks consistency among shared planner routes. For this 20-cell fixture,
`unitcheck` visits every cell and compares the converted value against its own native value
times 1000 with zero offset; no random initial terrain value is hard-coded.
The expected multiplier and offset must be finite, and at least one finite comparison must
succeed. Matching missing cells are allowed alongside valid cells; an all-missing sample is
insufficient evidence. Nonfinite converted or expected values fail before tolerance comparison.

Native shard descriptors may omit a CRS inherited from their owning observation. Native cell
and coordinate reads and the planned native fast path retain that source projection; they do
not assume EPSG:4326 when no CRS is available. Unsupported grid formats and requests above the
bounded cell budget fail explicitly.

The exploratory StreambedGradient model in staging's basic namespace remains commented out.
Its regional Elevation/Slope inputs do not establish the current stream-reach bearer contract;
atomic namespace admission otherwise withholds the valid quality models too. Correcting that
model's contract is deferred. The code is retained, and namespace validation is not weakened.

Closing a test context first cancels consequence work, drops queued handlers and lets the active
operation finish for up to five seconds before interruption. A further five-second deadline
applies after interruption. If the worker still cannot stop, closure fails and graph/storage
disposal is refused. A passing observation assertion with failed cleanup is a failed testcase.
