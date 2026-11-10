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

## Stage 2: binding and resolution admission

Required decisions before wiring execution:

1. Choose and register the target-binding annotation contract. The proposal is an explicit
   per-output mapping to an observation name or semantic observable; reserve its name and parameter
   types only after checking annotation-library conventions. `named` remains the default binding.
2. Define how a learning request is selected and invoked. Training should be explicit and should
   not be triggered by ordinary predictive discovery. Decide whether to expose a dedicated runtime
   operation, an observation request option, or an existing explicit-model execution path.
3. Define whether normal outputs retain the training target, receive fitted predictions, or must
   be computed by a following action. The specification permits a contextualization chain but
   requires evidence and prediction to remain distinct. Specify multi-output bindings similarly.
4. Decide how preparatory target computations become visible and how already-resolved targets are
   captured before output mutation. Implement resolver exclusion of the current learner when
   acquiring external targets; preserve cycle detection through nested resolution.

Then implement a validated learning plan with deterministic names, separate target/predictor
lists, required-input checks, duplicate/ambiguous-binding errors and preserved annotation arguments.
Reject unsupported learning requests explicitly at admission until execution is available. Test
real parsed declarations as well as source projection, externally resolved and internally computed
targets, multi-output and unsupervised plans, cycles and ordinary-model regression cases.

## Stage 3: execution and resolver commit

* Integrate the SPI with component discovery, contextualizer preparation and invocation. Decide
  whether it uses a dedicated descriptor kind or an explicit learner capability on function
  descriptors. Merely implementing the Java interface does not register a k.IM function.
* Execute learning once per attempt over the training context. Define treatment of temporal
  schedules and sharding before invoking a learner through ordinary contextualizer loops.
* Add a resolver-side learned-resource submission contract, transport DTO/client/server support,
  and payload staging/transfer. Derive identity from resolver service name, source namespace and
  preserved learned ID. Validate adapter availability and schema, and reject identity overrides.
* Reuse Resource versions/history and scope registration. Define the standard version increment
  policy and atomic allocation under concurrent attempts; do not infer that `putResource` alone
  creates a new version. Commit must preserve immutable references to previous payloads.
* Specify an attempt token, idempotent retry behavior, cancellation cleanup and compensation when
  observation output completion and resource registration span services. Choose the observable
  success boundary explicitly; claiming distributed atomicity without a protocol is insufficient.
* Connect IDE listing/editing/publishing/deletion to the same local resource lifecycle. Verify
  scope isolation, restart persistence, payload portability and authorization.

Acceptance: an engine-independent fake learner creates a usable scoped Resource, ordinary outputs
complete, repeated successful attempts create distinct versions, retries do not, and every tested
failure preserves the previous committed version and releases staged payloads.

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

Initial validation on 2026-11-10:

* `git diff --check`: passed.
* Offline Maven reactor production compilation: `klab.core.api` passed, including the new SPI
  and source-model API. `klab.core.common` failed to resolve existing core API packages during
  compilation; the reactor did not reach `klab.core.services`.
* Focused `LearningModelContractTest` reactor attempt: blocked during upstream API test
  compilation by unresolved existing k.IM classes, before the new test could run.
* A separate offline test-classpath resolution attempt was blocked by uncached artifacts:
  Guice 5.0.1, Spring Boot starter mail 3.4.4 and Spring test 6.2.5.

The new regression test has **not been executed successfully**. It covers adaptation through
executable projection, source and executable JSON round trips, ordinary-model defaults and input
parameter snapshots. Re-run after restoring a complete build environment:

```powershell
mvn -o -pl klab.core.services -am test '-Dtest=LearningModelContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DfailIfNoTests=false'
```

Production acceptance requires the integration tests described at each stage, beyond these
Stage 1 regressions. No ML engine or service integration has been exercised by this change.
