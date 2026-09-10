# Classification and characterization implementation

Status: first foundation milestone implemented; runtime execution is not enabled. This is a
continuation of [OBSERVATION.md](OBSERVATION.md), not a declaration that the staging example runs.

## Contract

Classification resolves an abstract predicate X in a substantial Y. It attributes a concrete
strict specialization Z of X to each selected Y observation. Observation identity, geometry,
parent and cohort membership are preserved. The classification request is an operation, not a
new observation or cohort. Instantiating missing Ys is a separate prerequisite which may create
observations. Characterization then resolves Z of Y in the scope of that particular observation.
Both lifecycle transitions belong to Runtime, not to user-authored continuations.

| Request | Activity | Member source | Successful result |
|---|---|---|---|
| Abstract X of each Y | CLASSIFICATION | Query existing cohort support; resolve missing collective support | Existing/new Ys acquire concrete Z; no classification-result observation |
| Abstract X of Y | CLASSIFICATION | Existing Ys in the contextual scope | Same semantic update, without implicitly demanding an instantiation |
| Concrete Z of Y | CHARACTERIZATION | The classified Y, used as its own contextual scope | Explanation of its attributed predicate; no replacement Y |
| Concrete Z of each Y | CHARACTERIZATION | Distribution across members | Per-member characterization, not classification merely because `each` occurs |
| Predicate of quality Q | TRANSFORMATION | The base quality graph | Existing transformation contract, outside this work |

Abstraction selects classification versus characterization. Collective inherence selects how
members are obtained. `isInstantiation()` historically includes collective predicate processing;
use the new `modifiesExistingObservations()` distinction when deciding whether an observation
result may be registered. Do not equate a successful empty cohort with missing coverage.

The sample is corrected, as confirmed by the maintainer:

```kim
model geography:Tanzania earth:Region
    observing earth:PhysicalEnvironment of each earth:Region;

model earth:PhysicalEnvironment of each earth:Region
    using klab.generators.random.categories();

private model klab:random:objects:polygons
    as each earth:Region;
```

A classifier attached to the Tanzania model would contextualize the parent, not implement its
dependency. The instantiator now directly produces the requested Region collective.

## Source audit

| Boundary | Current path and missing behavior |
|---|---|
| Semantics | `Contextualization.forSemantics` previously classified any collective inherent, even for a concrete predicate. It now distinguishes abstraction and rejects non-substantial/non-quality inherents. |
| Strategy selection | Activity patterns and ordinary producers already parse. Added a Tier-0 member-resolving classification strategy and direct characterization strategy. Lowering explicitly rejects semantic-update activities until operation targets can be executed; this prevents falsely treating the directive as an observation-producing plan. |
| Resolver | `ResolutionCompiler.resolve(Observable, ...)` obtains/registers an unresolved observation before recursively resolving it. Classification requires an operation-resolution entry point that does not call `requireObservation` for X of Y. Existing collective query and missing-scale logic must be reused for Y. |
| Dataflow | `CompiledDataflow` builds executors around an observation target. A separate operation observable and the member binding must survive compilation; using a phantom X-of-Y observation as a carrier is not acceptable. |
| Invocation | `ContextualizerExecutor` currently passes `observation.getObservable()` and ignores returned values. Classification must pass X of Y as the operation observable and each Y as the Observation argument; the returned Concept must be handled. |
| Results | `ContextualizationScopeImpl` only contains a target, event and observation outcomes. It lacks typed attribution results and before/after semantics. |
| Completion | `RuntimeService.submitContextualizationResult` explicitly throws for CLASSIFICATION. Its instantiation branch submits created children and waits for them. Characterization requires a separate, optional-explanation outcome, not a blanket swallowing of execution failures. |
| Transactions | `DigitalTwinImpl.TransactionImpl` shares modified/added state with the root. `update(asset)` adds to modified, but in-place semantic mutation needs a staged copy or undo before failure; a shared live object must not remain altered after rollback. |
| Provenance | Child contextualization activities formerly wrote CONTEXTUALIZED for every outcome. They now choose typed links. Later classifier execution must link the activity to each modified member, not to a directive placeholder. |

## Implemented milestone C0

- Added abstraction-aware predicate activity selection and a semantic-update activity predicate.
- Added typed effect relationships and changed the current transaction writer to use them:
  INSTANTIATED, ACKNOWLEDGED, DETECTED, SIMULATED, MEASURED, QUANTIFIED, VALUED,
  CATEGORIZED, VERIFIED, CLASSIFIED, CHARACTERIZED, TRANSFORMED and CONNECTED.
- Retained CONTEXTUALIZED for old graphs and unspecified activities. CONTEXTUALIZED_BY remains
  the observation-to-actuator/execution relationship; it has a different purpose.
- Updated Neo4j query visibility to follow all activity effects. New effect edges are deliberately
  absent from deletion ownership. Classifying an existing observation does not make it owned by
  the classifying context. Historical deletion semantics are not migrated in this milestone.
- Added the two Tier-0 source strategies to the reference corpus and `imod/strategies/observations.obs`.
  These are definitions awaiting the operation-target milestone, not runnable classifiers today.
- Corrected the staging model separation. The resource and live numerical/classifier execution
  have not been exercised.

C0 validation: the eight-module Maven reactor compiled and 22 focused tests passed:
`ClassificationContractTest` (2), `ObservationPipelineTest` (5),
`ObservationStrategyAdaptationTest` (6) and `Neo4jQueryCompilerTest` (9). These verify semantic
dispatch, the expanded corpus and interface serialization, existing Tier-0 behavior, typed effect
visibility and the deletion boundary. They do not exercise classification against live members.

## Strategy contract

Classification always consumes observed members. Resolving `each Y` is therefore a mandatory
prerequisite within the Tier-0 strategy, not a Tier-1 fallback. There is no classifier strategy
that bypasses member resolution. Existing complete cohort coverage makes that resolution a query
reuse; partial coverage triggers resolution of the missing support.

```text
strategy 0 named classification.members
  for pattern {
    node {
      activity = CLASSIFICATION;
      inherent = capture members as node { collective = true; };
    }
  }
  ensure context.exists()
  resolve $members to cohort
  observe $this with inputs(members = cohort);

strategy 0 named characterization.direct
  for pattern { node { activity = CHARACTERIZATION; } }
  ensure context.exists()
  observe $this;
```

The classifier model still directly explains X of each Y; its strategy first obtains the members
on which it operates. “No direct classifier strategy” does not remove the separate classifier
model from the namespace. Both source definitions are installed, but execution remains gated.

An abstract classifier request must not use `request.fully_specified` to reject its deliberately
abstract predicate. Validate the inherent and operation signature instead. The `members` port
must become a typed collective/member binding, not an ordinary scalar model argument. The current
classification strategy covers collective inherence; singular-inherent classification needs its
own existing-member binding contract before enabling that form.

The runtime must finish resolution of newly instantiated members before passing them to the
classifier. Characterization is never written as a strategy continuation.

## Progressive implementation and acceptance

### C1 — Resolve operations without registering result observations

Introduce an operation target carrying the observable, context observation identity, coverage and
input graph bindings. Keep observation-producing and semantic-update targets explicit in portable
plans and actuators. Route classifier dependencies through operation resolution before
`requireObservation`. Retain lexical namespace/project/scenario constraints at each lookup.

Suggested API direction (names are provisional): an interface-backed `ContextualizationRequest`
with operation observable/context identity/geometry, and a `ContextualizationResult` reporting
completion, affected identities and diagnostics. Register concrete beans in JacksonConfiguration;
keep interfaces free of Jackson dependencies. Reuse the same target contract for characterization.
Do not serialize a ContextScope or a live executor.

Implement classifier model selection and the Tier-0 members port, reusing existing query coverage and
missing-scale calculation. Deduplicate members by durable or transaction-local identity. Cohort
support and classification completion are separate quantities. Classifying only the cached members
must not claim completion for an incompletely resolved requested cohort.

Acceptance: no new observation ID/CREATED link for X-of-Y; the member-resolving strategy remains
rank zero; fully covered/partial/empty cohorts; existing and newly instantiated Ys; private model
visibility; interface JSON round trips. Only after these pass remove the explicit lowerer guard.

**Prompt:** “Implement C1 in docs/CLASSIFICATION.md: semantic-update operation targets from Resolver
through portable Dataflow, member-resolving Tier-0 classification and the typed members port. Prove that the
classifier directive never becomes an observation. Update the running docs with supported paths.”

### C2 — Invoke and validate classifiers per member

Select a classifier executor from the operation contextualization. Resolve the contextualizer's
signature before execution: operation Observable, member Observation and member ContextScope.
Execute once per member per classified support/event, not once per grid cell or data shard. The
operation observable stays X of Y while the Observation argument is the concrete member.

Require a non-null Concept Z, concrete and consistent, of the appropriate predicate family and a
strict semantic specialization of X (`is(Z, X)` plus exclusion of X itself). A null, abstract,
unrelated or inconsistent result is an error, not an empty successful observation. Do not guess
category compatibility from URN prefixes. Restrict the first implementation to local concept-
returning contextualizers; report unsupported signatures explicitly.

Acceptance: actual `klab.generators.random.categories` signature; deterministically controlled
choices; mixed existing/new members; zero members with complete support; return validation; no
concurrent duplicate classification. Provide explicit policy/tests for reclassification before
silently replacing an existing X-family predicate. Preserve unrelated predicates and roles.

**Prompt:** “Implement C2: a member classifier executor with typed invocation and semantic closure
validation. Retain returned concepts as pending attributions; do not mutate persisted observations
or create result observations. Test the actual generator signature and invalid returns.”

### C3 — Atomic semantic updates and provenance

Represent pending attributions with member identity, old/new observable, abstract predicate,
concrete result, coverage/event and activity. Construct new observables with the Reasoner builder;
attach traits versus roles correctly. Preserve observation ID, URN, geometry, cohort and parent.
Avoid mutating hash keys while observations are vertices in graphs or members of sets.

Apply replacements within the root transaction, mark existing members modified, and include new
members in their creation records with final semantics. Update persistence indexes and invalidate
client/query caches from commit payloads. Store before/after semantics in provenance so extraction
can reconstruct what was classified; a bare CLASSIFIED edge is insufficient for replay.
Rollback must restore both in-memory views and durable state. Runtime activities must link to all
affected members with CLASSIFIED, without making those effects deletion-ownership edges.

Acceptance: commit.modified contains existing members; no replacement identities/cohorts; new
members carry final semantics; rollback after a later-member failure; concurrent classifications;
provenance queries and client graph updates; deletion cannot remove independently owned members.

**Prompt:** “Implement C3: atomic staged attribution updates and CLASSIFIED provenance, including
before/after semantics, commit propagation, query/client invalidation and rollback tests.”

### C4 — Mandatory characterization scheduling, optional explanation

After each valid classification, Runtime builds concrete Z of singular Y and resolves it in
`scope.within(member)`. The abstract X must not leak into this request. Keep the classification
transaction open until its required child lifecycle work finishes, as for instantiation.

Distinguish no explanatory model from failed resolution infrastructure or failed contextualizer
execution. No characterization model is a successful terminal lifecycle condition; it does not
undo classification. An execution failure follows the normal failure/rollback policy. Do not
reclassify on completion of characterization or resolve the whole member recursively in a loop.
Record CHARACTERIZED for actual characterization work, with a distinct no-model outcome when
only the lifecycle decision was made. Apply the same no-model distinction to acknowledged newly
instantiated members rather than suppressing arbitrary failures.

Acceptance: exactly one characterization request per new attribution; correct concrete semantics
and member scope; no-model success; execution failure propagation; no observation creation;
multiple members/child completion order; explicit provenance of actual work.

**Prompt:** “Implement C4: Runtime-owned characterization after classification, using an explicit
no-model success outcome. Test per-member scopes, no duplicate lifecycle work, commit ordering
and the distinction between missing explanation and failed execution.”

### C5 — Live staging acceptance

Run the corrected staging namespace with the installed worldview and generator. Verify the
classifier model is selected, Regions are queried/instantiated as necessary, each result is a
concrete PhysicalEnvironment specialization, no classification observation appears, and existing
members appear as modified in the commit. This namespace must succeed without characterization
models. Add the forthcoming characterization namespace and check its member-specific execution.

Test cohort partial coverage, zero members, repeat submissions, failure on one member, concurrent
contexts, and provenance extraction/replay inputs. Update OBSERVATION.md, OBSERVABLES.md and the
domain context pack only with runtime claims supported by this evidence.

**Prompt:** “Complete C5 using the staging classification namespaces. Record live model selection,
member identity/coverage, commits and typed provenance; distinguish test doubles from actual
Runtime, Resources and knowledge graph evidence. Close stages only when their acceptance passes.”
