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
