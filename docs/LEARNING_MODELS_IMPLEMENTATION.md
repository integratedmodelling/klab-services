# Learning models: implementation tracker

The behavioral specification is [LEARNING_MODELS.md](LEARNING_MODELS.md). This file tracks
implementation, evidence, limitations and decisions. The specification is a contract, not a claim
that all described behavior is already executable.

## Repository findings

* The sibling `klab-languages` repository's `Kim.xtext` accepts `learn ... as ...`.
  `ModelSyntax` exposes `ModelType.LEARN` and `getLearnedComputationURN()`.
* `KimModel` already had `isLearningModel()`, but `LanguageAdapter.adaptModel` did not set it.
  Neither the source DTO nor executable `Model` retained the exact learned resource ID.
* `SourceModelMapper` is the shared source-to-executable projection. It already preserves
  dependency/output annotations, which can carry predictor arguments.
* `Resource.Builder` supports adapter IDs, parameters, metadata, local paths, input/output
  schemas and attributes. It is sufficient for an initial engine-neutral fitting SPI.
* `ResolverService.submitResource(Observation, ContextScope)` builds a Resource from observation
  contextualization data and registers it in the scope's resolution graph. Durable contexts also
  use `ResourcesKBox`. `getSubmittedResources` exposes these local resources. This is useful
  infrastructure, but is not an arbitrary learned-Resource submission endpoint.
* `ResourcesKBox` supports version lookup and history selection. Its `putResource` updates the
  stored Resource; it does not itself allocate the next version or serialize concurrent fitting
  attempts. Existing submission explicitly initializes version `0.0.1`.
* `Model.learner(String)` delegates to configuration, but `ModelBuilderImpl`, including its
  learner constructor and fluent methods, is a stub. It is not a working programmatic path.

## Stage 1: identity and extension contract

Implemented in this change:

* Preserve the learning flag and exact learned resource ID in `LanguageAdapter`, `KimModelImpl`,
  `SourceModelMapper` and `ModelImpl`. Default API methods preserve compatibility with other
  implementations. Keep the existing generated model URN unchanged.
* Define `LearningContextualizer` with ordered named predictor and target inputs, a supplied
  Resource.Builder and ContextScope. Snapshot input parameter maps without interpreting engine
  options. The SPI explicitly leaves resource publication to the runtime.
* Add regression coverage for adaptation/projection and the SPI input contract.

This stage does **not** enable learning execution, target resolution, automatic predictor
selection, resource registration, inference, calibration, or the programmatic learner builder.
Do not treat the presence of the flag or SPI as executable support.

## Accepted integration decisions

The following decisions were agreed after the initial specification and are no longer open:

* Training is explicit. Learning declarations never enter ordinary model resolution, including
  explicit-model shortcuts. They remain in retrievable namespace source.
* Bind slots by `named`, otherwise stable reference name. Target precedence is request binding,
  completed preceding action, then ordinary resolution. Invalid explicit bindings never fall back.
  Reject unresolved required targets/dependencies; engines explicitly opt out of supervision.
* Exclude learning previews from automatic evidence acquisition. Explicit binding can deliberately
  reuse one. Preparing targets must keep their storage distinct from predicted values.
* Regenerate outputs using the committed Resource by default. Use a real learning-observer Agent
  per model resource identity/context and a semantic Agent class selected by model-level
  `@learning(observer = ...)`, otherwise an explicitly configured worldview default.
* Current previews may be replaced for the same model/actual observer/output/support. Preserve
  canonical observations, evidence, prior successful previews on failure and revision provenance;
  older Resource versions cannot overwrite previews of newer ones.
* Commit the Resource before preview generation. Commit and preview have separate outcomes and
  retries; a preview failure does not discard a valid Resource.
* Resource registration has its own `RESOURCE_COMMIT` provenance Activity. Graph recording can
  be reconciled after resource commit without allocating another Resource version.

## Stage 2: binding and resolution admission — partial integration

Implemented:

* `LearningPlan.bind` preserves ordered predictors, targets and ancillary dependencies, enforces
  deterministic names, rejects duplicates/unknown request names, validates exact artifact types
  and rejects unresolved inputs. A committed observation ID and nonempty event timestamps are
  required; engine-level sample and alignment validation remains separate.
* Explicit bindings override completed target actions, which override automatic acquisition.
  `@predictor` parameters are preserved. Optional missing dependencies are omitted, and
  `LearningContextualizer.requiresTargets()` defaults to true for supervised learning.
* `LearningPlan.observer` resolves explicit Agent concepts/semantic URNs and supplied defaults
  through the semantic owner. It rejects absent/non-Agent perspectives. This validates the class;
  it does not create an actual Agent or configure worldview settings.
* `Learning.PREVIEW` marks inspection observations. Automatic acquisition filters them; explicit
  observation bindings remain deliberate overrides.
* Discovery excludes learners in `ModelCandidateSources` and the shared catalog descriptor
  factory. Resolver core filtering/ranking, model execution and ordinary executable admission
  also reject them. Executable hashes now include learning identity, preventing a flag/ID change
  from retaining the same admission hash.
* Source projection preserves `named` aliases independently of the reasoner's semantic cache.

Remaining integration work: connect binding to an authenticated dedicated runtime training entry
point, source admission and actual observation-handle lookup; run preceding `set to`/contextualizer
actions into separate evidence storage; supply the automatic acquisition callback with
preview-excluding observation reuse and normal resolver fallback. The callback-based binder does
not itself resolve a dataflow or validate an observation's membership in the current graph.

## Stage 3: execution and resolver commit — inline foundation implemented

Implemented:

* `LearningExecution.train` is an in-process execution kernel for an admitted, prepared plan.
  It invokes a supplied engine once outside sharding/scheduler loops, preserves source semantics
  and input provenance in metadata, detects cancellation and identity overrides, then commits.
  `complete` retries graph recording/preview from an exact receipt without fitting again.
* Commit activity recording and preview publication are host callbacks, with separate outcomes.
  Recording failure leaves the Resource committed and defers previews. Preview failure leaves the
  Resource available and reports an explicit failed preview outcome.
* `Resolver.commitLearnedResource`, `LearnedResourceSubmission`, a scoped client call and
  `POST /api/v1/resource/learned` (using the configured API base) provide an inline commit path.
  The server retrieves and validates source, verifies the learned ID and inference-adapter
  availability, and derives actor/context/service identity from its authorized scope.
* `LearningResourceCommitter` serializes commit operations within the owning store instance,
  validates schema/identity, assigns the initial `0.0.1` version and increments the build for
  subsequent fits, preserves flat history, and recovers old attempts from that history. Storage
  snapshots are detached. Another actor/model cannot overwrite an existing learning resource;
  an attempt cannot be replayed under a different context.
* Durable contexts use the resolver's `ResourcesKBox`; temporary contexts keep a scope-local
  store. Submitted-resource listing includes committed learned resources. Replayed older attempts
  do not replace the scope's latest visible version.
* `Activity.Type.RESOURCE_COMMIT` is an orchestration activity. `LearningCommitActivity.of` builds
  a graph-ready record with attempt/model/context/actor and exact Resource URN/version, optionally
  linked to the initiating activity. It does not invent a successful outcome or persisted graph ID.
* Registered `Resource.Attribute` with the shared polymorphic JSON transport. Input/output schemas
  now survive learned submission and stored receipt round trips instead of failing on an abstract
  interface during deserialization.
* Fixed `ResourceBuilderImpl` to preserve history and local resource paths and emit each output
  once. These existing omissions would otherwise break versioned learned-resource construction.

Limits and next integration work:

* The execution kernel is not wired to `RuntimeService` HTTP/CLI/IDE/k.Actors or component learner
  discovery. No live end-to-end training command is exposed by this change. A caller must admit
  source, authenticate input handles, resolve/create the actual observer and prepare the plan.
* Implement graph recording idempotently through native transactions and durable attempt/version
  lookup. The new activity type/factory and recording callback do not themselves persist graph
  nodes or evidence links. Reconciliation must work across runtime/resolver restarts.
* Implement transactional preview publication with actual-observer isolation, explicit-only
  inspection visibility, exact-version provenance and a guard against older-version replacement.
  The callback contract requires those behaviors; there is no default graph preview writer yet.
* Commit currently supports **inline adapter parameters only**. Local files are rejected rather
  than accepted as portable payloads. Add resolver-owned staging, transfer, cancellation cleanup,
  checksums and retention before enabling file-based ML engines. Existing embedded parameter data
  must itself be portable; arbitrary external storage references are not validated here.
* Version allocation is serialized per store object, not a distributed compare-and-swap protocol.
  Keep one owner for the local store; multi-process/shared-store deployment requires stronger
  storage-level concurrency. Replays rely on retained history; deleting history deletes receipts.
* Schema checks cover names and data types, not engine-format compatibility or adapter-specific
  content validation. Add the common inference schema and adapter validation before production use.
* The IDE can list these resources through the existing submission API, but complete edit,
  publication/deletion and payload-retention workflows still need integration tests.

Acceptance so far is an engine-independent fake learner and detached commit store. Production
acceptance additionally requires a real service/graph run, durable restart/retry tests, observer
isolation and the complete payload lifecycle.

## Stage 4: inference and calibration

Implement a common schema representation for names, data types, semantic metadata, units,
transformations and encoding versions. Decide metadata keys and transport schema before engine
components depend on them. Adapt Resource inputs by explicit name/type binding without relaxing
ordinary semantic resolution globally. Test changed semantics, reordered predictors, unknown
categories, missing values, incompatible units/types and version-pinned execution.

Build a modern ML component independently of the core lifecycle. Require declared adapter/model
format compatibility and reproducible training configuration; a Weka compatibility component can
use the same contract later. Test portable payload import and inference in a fresh context.

Specify and implement the calibration adapter separately: scalar/structured parameter values,
units, parameter domains and constraints, dynamic-model invocation/objective, optimization
termination and diagnostics. Restrict dependency substitution to explicit calibrated-resource
use. A dynamic optimizer may require repeated model executions; the initial SPI does not itself
define that orchestration API.

## Principal weaknesses to resolve

| Concern | Required resolution |
| --- | --- |
| Target leakage and recursive training | Capture training evidence separately; exclude the current learner during target resolution |
| Implicit training in normal discovery | Explicit invocation/admission policy |
| Ambiguous names or semantics | Deterministic binding with errors on multiple matches |
| Type-only compatibility | Stored units, category dictionaries, feature order and preprocessing |
| Spatial/temporal misalignment | Explicit sampling and missing-data policy per engine |
| Concurrent fits and retries | Version allocation and idempotent commit protocol |
| Local payloads and retention | Resolver-owned staging, portable publication and immutable version payloads |
| Calibration scope | Dedicated adapter substitution and dynamic execution contract |

## Validation record

Initial Stage 1 Maven attempts on 2026-11-10 were blocked by downstream core API package resolution
and uncached dependencies (Guice 5.0.1, Spring Boot starter mail 3.4.4 and Spring test 6.2.5).
The focused regression was subsequently run using the local distribution and cached test JARs.

Current validation on 2026-11-10:

* All changed production sources compiled with Java 21 against the local API/Common/Services and
  resolver/resources/runtime distribution dependencies.
* Focused JUnit regression suite: 30 tests passed, covering declaration projection and aliases,
  JSON identity/schema transport, binding precedence and errors, optional and unsupervised inputs,
  preview evidence exclusion, observer-class validation, fake-engine fit/commit/preview outcomes,
  cancellation, identity overrides, discovery/catalog exclusion, concurrent version allocation,
  detached receipts, old-attempt replay, ownership, failed writes and builder history/payload paths.
* The suite includes existing candidate-source and Resource version-lookup regressions.
* `git diff --check`: passed.

Compiler argument files, the temporary JUnit launcher, logs and cached test-library copies are in
the ignored `target/learning-validation/` directory. Java archive access/cleanup was blocked by the
Windows sandbox; compilation and tests succeeded outside that sandbox using local files only.
This is focused validation against cached artifacts, not a successful full Maven reactor build.
The standard Maven equivalent, once dependencies/build configuration are available, is:

```powershell
mvn -o -pl klab.core.services,klab.services.resources -am test '-Dtest=Learning*Test,ResourceBuilderLearningTest,ResourcesKBoxVersionTest,ModelCandidateSourcesTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false'
```

Production acceptance requires live service, graph, persistence-restart, observer-isolation and
payload-lifecycle tests described above. No real ML engine, HTTP round trip or graph writer was
exercised in this focused suite.
